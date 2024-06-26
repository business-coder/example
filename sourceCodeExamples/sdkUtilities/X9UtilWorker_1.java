package com.x9ware.utilities;

import java.util.List;

import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.elements.X9Products;
import com.x9ware.options.X9PreferencesXml;
import com.x9ware.swing.X9StartUp;
import com.x9ware.tools.X9Task;
import com.x9ware.tools.X9TaskMonitor;
import com.x9ware.tools.X9TaskWorker;

/**
 * X9UtilWorker invokes a single work unit within a worker task.
 * 
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilWorker extends X9TaskWorker<X9UtilWorkUnit> {

	/*
	 * Private.
	 */
	private X9UtilWorkUnit workUnit;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilWorker.class);

	/**
	 * X9UtilWorker Constructor.
	 *
	 * @param monitor
	 *            associated task monitor for call backs
	 * @param workList
	 *            our work unit list to be processed
	 */
	public X9UtilWorker(final X9TaskMonitor<X9UtilWorkUnit> monitor,
			final List<X9UtilWorkUnit> workList) {
		super(monitor, workList);
	}

	@Override
	public boolean processOneEntry(final X9UtilWorkUnit work_Unit) {
		/*
		 * Execute the work unit and return true when meaningful work was performed.
		 */
		workUnit = work_Unit;
		executeWorkUnit(workUnit);
		return workUnit.status >= 0;
	}

	/**
	 * Execute a single work unit.
	 * 
	 * @param workUnit
	 *            work unit to be executed
	 * 
	 * @return exit status for the executed work unit
	 */
	public static int executeWorkUnit(final X9UtilWorkUnit workUnit) {
		/*
		 * Determine if the work unit is to be executed subject to abort limits.
		 */
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("executeWorkUnit commandLine({})",
					workUnit.x9commandLine.getCommandLineAsString());
		}

		workUnit.status = X9UtilBatch.EXIT_STATUS_ABORTED;
		final X9UtilWorkUnitList workUnitList = workUnit.workUnitList;
		final int abortCount = workUnitList.getAbortCount();
		final boolean isAbortLimitDisabled = workUnitList.getAbortLimit() < 0;
		final boolean isWorkUnitToBeExecuted = isAbortLimitDisabled
				|| abortCount < workUnitList.getAbortLimit();

		/*
		 * Execute the work unit when abort limits have not been exceeded.
		 */
		if (isWorkUnitToBeExecuted) {
			try {
				/*
				 * Parse the command line to determine if all required files are present.
				 */
				final boolean isParseSuccessful = workUnit.isParseAndAssignSuccessful();
				if (isParseSuccessful) {
					/*
					 * Assign the product name to be utilized by this environment. When we are
					 * invoked by a user written SDK application, then the program name should be
					 * provided as X9SDK. When we are invoked by X9Utilities, then the program name
					 * will be assigned as the product name from the user license, which will be
					 * either X9Utilities or X9Export. This allows us to provide all x9utilities
					 * functionality to SDK and X9Utilities users, while limiting the functionality
					 * extended to X9Export users. This approach is secure, since we assign
					 * X9Utilities and X9Export from within our own code, while the credentials for
					 * SDK users are already confirmed elsewhere.
					 */
					final String productName = StringUtils.equals(X9Products.X9SDK,
							workUnitList.getProgramName()) ? X9Products.X9SDK
									: workUnitList.getUserLicense().getProductName();

					/*
					 * Run the requested batch run unit subject to the current client license.
					 */
					workUnit.markTaskStartTime();
					if (workUnit.getCommandLine().isSwitchSet(X9UtilBatch.HELP_SWITCH)) {
						workUnit.logCommandUsage(); // log command usage (help) when requested.
						workUnit.status = X9UtilBatch.EXIT_STATUS_ZERO;
					} else if (StringUtils.equalsAny(productName, X9Products.X9UTILITIES,
							X9Products.X9SDK)) {
						/*
						 * Run commands that are valid for an X9Utilities license.
						 */
						workUnit.status = executeUtilitiesCommand(workUnit);
					} else if (StringUtils.equals(productName, X9Products.X9EXPORT)) {
						/*
						 * Run commands that are valid for an X9Export license.
						 */
						workUnit.status = executeExportCommand(workUnit);
					} else {
						/*
						 * Otherwise unknown product.
						 */
						workUnit.status = X9UtilBatch.EXIT_STATUS_ABORTED;
						throw X9Exception
								.abort("unable to launch this batch product using the located "
										+ "license with productName({})", productName);
					}

					/*
					 * Log the completion, elapsed time, and highest exit status encountered.
					 */
					workUnit.markTaskEndTime();
					LOGGER.info("function({}) completed elapsed({}) status({})",
							workUnit.utilFunctionName.toLowerCase(),
							X9Task.formatElapsedSeconds(workUnit.getTaskStartTime(),
									workUnit.getTaskEndTime()),
							workUnit.status);

					/*
					 * Post work unit as completed back to the utilities console.
					 */
					workUnitList.postCompletedWorkUnit(workUnit);
				} else {
					LOGGER.info("function({}) command line parse unsuccessful({}))",
							workUnit.utilFunctionName.toLowerCase(), workUnit.getCommandLine());
					workUnit.status = X9UtilBatch.EXIT_STATUS_INVALID_FUNCTION;
				}
			} catch (final Throwable t) {
				LOGGER.error("exception", t);
				workUnit.status = X9UtilBatch.EXIT_STATUS_FILE_NOT_FOUND;
			}
		} else {
			LOGGER.info("work unit skipped due to abort limit execeeded({})", abortCount);
		}

		/*
		 * Return our exit status.
		 */
		return workUnit.status;
	}

	/**
	 * Execute an X9Utilities command.
	 * 
	 * @param workUnit
	 *            work unit to be executed
	 * @return exit status from work unit
	 */
	private static int executeUtilitiesCommand(final X9UtilWorkUnit workUnit) {
		/*
		 * Check the JVM level.
		 */
		final String javaVersion = System.getProperty("java.version");
		LOGGER.info("javaVersion({})", javaVersion);
		final String functionName = workUnit.utilFunctionName;
		if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_CONSOLE)) {
			/*
			 * Console mode requires at least java 11 based on miglayout requirements.
			 */
			if (!SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_11)) {
				throw X9Exception.abort("java-11 required");
			}

			/*
			 * Load preferences, which is needed to support console ui operations.
			 */
			X9StartUp.loadPreferences(X9PreferencesXml.PREFERENCES_READ_WRITE_DISABLED);
		} else {
			/*
			 * Command line functions are run as headless and requires at least java 1.8.
			 */
			if (!SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_8)) {
				throw X9Exception.abort("java-1.8 required");
			}
			X9SdkRoot.setHeadless();
			LOGGER.info("running in headless mode");
		}

		/*
		 * Write user defined text directly to the log as provided on the command line. This is
		 * helpful when the user has automation that matches our log against their own event log.
		 */
		if (workUnit.isCommandSwitchSet(X9UtilBatch.USER_LOGGER_LINE)) {
			workUnit.logUserProvidedTextLine(
					workUnit.getCommandSwitchValue(X9UtilBatch.USER_LOGGER_LINE));
		}

		/*
		 * Run the requested batch run unit subject to the current client license.
		 */
		final int exitStatus;
		if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_CONSOLE)) {
			final X9UtilConsole x9utilConsole = new X9UtilConsole(workUnit);
			exitStatus = x9utilConsole.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_TRANSLATE)) {
			final X9UtilTranslate x9utilTranslate = new X9UtilTranslate(workUnit);
			exitStatus = x9utilTranslate.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_WRITE)) {
			final X9UtilWriter x9utilWriter = new X9UtilWriter(workUnit);
			exitStatus = x9utilWriter.process();
			workUnit.addProcessingErrors(x9utilWriter.getProcessingErrorList());
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_DRAW)) {
			final X9UtilDraw x9utilDraw = new X9UtilDraw(workUnit);
			exitStatus = x9utilDraw.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_IMPORT)) {
			final X9UtilImport x9utilImport = new X9UtilImport(workUnit);
			exitStatus = x9utilImport.process();
			workUnit.addProcessingErrors(x9utilImport.getProcessingErrorList());
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_EXPORT)) {
			final X9UtilExport x9utilExport = new X9UtilExport(workUnit);
			exitStatus = x9utilExport.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_EXPORT_CSV)) {
			final X9UtilExportCsv x9utilExportCsv = new X9UtilExportCsv(workUnit);
			exitStatus = x9utilExportCsv.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_VALIDATE)) {
			final X9UtilValidate x9utilValidate = new X9UtilValidate(workUnit);
			exitStatus = x9utilValidate.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_SCRUB)) {
			final X9UtilScrub x9utilScrub = new X9UtilScrub(workUnit);
			exitStatus = x9utilScrub.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_MAKE)) {
			final X9UtilMake x9utilMake = new X9UtilMake(workUnit);
			exitStatus = x9utilMake.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_MERGE)) {
			final X9UtilMerge x9utilMerge = new X9UtilMerge(workUnit);
			exitStatus = x9utilMerge.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_UPDATE)) {
			final X9UtilUpdate x9utilUpdate = new X9UtilUpdate(workUnit);
			exitStatus = x9utilUpdate.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_SPLIT)) {
			final X9UtilSplit x9utilSplit = new X9UtilSplit(workUnit);
			exitStatus = x9utilSplit.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_COMPARE)) {
			final X9UtilCompare x9utilCompare = new X9UtilCompare(workUnit);
			exitStatus = x9utilCompare.process();
		} else if (StringUtils.equals(functionName, X9UtilWorkUnit.FUNCTION_IMAGE_PULL)) {
			final X9UtilImagePull x9utilImagePull = new X9UtilImagePull(workUnit);
			exitStatus = x9utilImagePull.process();
		} else {
			exitStatus = X9UtilBatch.EXIT_STATUS_INVALID_FUNCTION;
			throw X9Exception.abort("invalid functionName({})", functionName);
		}

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

	/**
	 * Execute an X9Export command.
	 * 
	 * @param workUnit
	 *            work unit to be executed
	 * @return exit status from work unit
	 */
	private static int executeExportCommand(final X9UtilWorkUnit workUnit) {
		/*
		 * Run the requested batch run unit subject to the current client license.
		 */
		final int exitStatus;
		if (StringUtils.equals(workUnit.utilFunctionName, X9UtilWorkUnit.FUNCTION_TRANSLATE)) {
			final X9UtilTranslate x9utilTranslate = new X9UtilTranslate(workUnit);
			exitStatus = x9utilTranslate.process();
		} else if (StringUtils.equals(workUnit.utilFunctionName, X9UtilWorkUnit.FUNCTION_EXPORT)) {
			final X9UtilExport x9utilExport = new X9UtilExport(workUnit);
			exitStatus = x9utilExport.process();
		} else if (StringUtils.equals(workUnit.utilFunctionName,
				X9UtilWorkUnit.FUNCTION_EXPORT_CSV)) {
			final X9UtilExportCsv x9utilExportCsv = new X9UtilExportCsv(workUnit);
			exitStatus = x9utilExportCsv.process();
		} else {
			exitStatus = X9UtilBatch.EXIT_STATUS_INVALID_FUNCTION;
			throw X9Exception.abort("invalid work unit({})", workUnit.utilFunctionName);
		}

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

}
