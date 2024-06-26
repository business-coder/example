package com.x9ware.utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9AbortManager;
import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.apacheIO.WildcardFileFilter;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9ProductManager;
import com.x9ware.elements.X9HomeFolder;
import com.x9ware.elements.X9ProductFactory;
import com.x9ware.elements.X9ProductGroup;
import com.x9ware.licensing.X9Credentials;
import com.x9ware.licensing.X9License;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.options.X9OptionsManager;
import com.x9ware.toolbox.X9Purge;
import com.x9ware.tools.X9BuildAttr;
import com.x9ware.tools.X9CommandLine;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9D;
import com.x9ware.tools.X9File;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9Task;

/**
 * X9UtilBatch is a back-end class, leveraged by X9UtilMain and also made available to user written
 * SDK applications. We contain the core batch driver execution and multi-threading logic
 * implemented for x9utilities processing. X9UtilBatch can process a single utility command (eg,
 * from the command line) but can also process multiple utility commands as a group, resulting in an
 * exit status that is the maximum of all performed tasks. We implement closeable as part of
 * try-with-resources to easily ensure that our close method is always invoked by the caller.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilBatch implements AutoCloseable {

	/*
	 * Private.
	 */
	private final String programName;
	private final boolean isEnvironmentToBeOpened;
	private final boolean isEnvironmentToBeClosed;
	private final int maxWorkUnitsPerIteration;
	private String runMode = SINGLE_ENTRY;
	private X9UtilWorkUnitCallBack workUnitCallBack;
	private X9License x9license;
	private float batchThreadingBenefit;

	/*
	 * Constants.
	 */
	public static final String HELP_SWITCH = "h";
	public static final String DEBUG_SWITCH = "debug";
	public static final String SWITCH_LOG_FOLDER = "log";
	public static final String CONSOLE_ON_SWITCH = "consoleOn";
	public static final String CONSOLE_OFF_SWITCH = "consoleOff";

	private static final char ASTERISK = '*';
	private static final String SLF4J_NOT_ON_CLASS_PATH = "SLF4J not on classpath";
	private static final String SINGLE_ENTRY = "single-entry";

	/*
	 * The number of work units per thread is an arbitrarily small number. The purpose is to ensure
	 * that the cpu time consumed by any given thread is not so excessive that it gets cancelled by
	 * our monitor. The total reason behind cpu checking is to ensure that we have not gone into a
	 * forever loop, which quite honestly could only happen in our MICR line recognizer.
	 */
	private static final int MAXIMUM_WORK_UNITS_PER_THREAD = 6;

	/*
	 * We provide a generalized user facility to insert specific content lines into our system log,
	 * either from the command line or (for certain commands such as the writer) from the csv. This
	 * can be helpful when the user is scanning our output system log as part of their work flow. In
	 * this situation, they can match these logging lines to correlate back to their internal run
	 * number, or perhaps identify the logical deposits being written.
	 */
	public static final String USER_LOGGER_LINE = "logger";
	public static final String USER_LOGGER_PREFIX_SUFFIX = "#####";

	/*
	 * Sdk open and close flags.
	 */
	public static final boolean ENVIRONMENT_OPEN_ENABLED = true;
	public static final boolean ENVIRONMENT_OPEN_DISABLED = false;
	public static final boolean ENVIRONMENT_CLOSE_ENABLED = true;
	public static final boolean ENVIRONMENT_CLOSE_DISABLED = false;

	/*
	 * Public exit status.
	 */
	public static final int EXIT_STATUS_ZERO = 0;
	public static final int EXIT_STATUS_ERROR = 3;
	public static final int EXIT_STATUS_ABORTED = -1;
	public static final int EXIT_STATUS_INVALID_FUNCTION = -2;
	public static final int EXIT_STATUS_FILE_NOT_FOUND = -3;

	/**
	 * We now carefully define our Logger instance. This is the first class that attempts to use
	 * logging within x9utilities, and our concern is that the SLF4J jar may not be present. In that
	 * situation, the class initializer fails with a ExceptionInInitializerError. It unfortunately
	 * does not throw a ClassNotFoundException, which would be more expected. In an attempt to help
	 * with this environmental error, we catch the exception and issue a message which hopefully
	 * points to the setup problem. This also allows us to propagate the exception up to the caller
	 * when we are being invoked from an SDK application. Note that we cannot just log the error,
	 * since by very definition logging is not present.
	 */
	private static Logger LOGGER = null;
	static {
		try {
			LOGGER = LoggerFactory.getLogger(X9UtilBatch.class);
		} catch (final Throwable t) {
			System.out.println(SLF4J_NOT_ON_CLASS_PATH);
			throw new RuntimeException("SLF4J not on classpath", t);
		}
	}

	/**
	 * X9UtilBatch Constructor.
	 *
	 * @param program_Name
	 *            batch program name
	 */
	public X9UtilBatch(final String program_Name) {
		this(program_Name, ENVIRONMENT_OPEN_ENABLED, ENVIRONMENT_CLOSE_ENABLED);
	}

	/**
	 * X9UtilBatch Constructor.
	 *
	 * @param program_Name
	 *            batch program name
	 * @param is_EnvironmentToBeOpened
	 *            true or false
	 * @param is_EnvironmentToBeClosed
	 *            true or false
	 */
	public X9UtilBatch(final String program_Name, final boolean is_EnvironmentToBeOpened,
			final boolean is_EnvironmentToBeClosed) {
		/*
		 * Abort if SLF4J is not on our class path.
		 */
		if (LOGGER == null) {
			throw X9Exception.abort(SLF4J_NOT_ON_CLASS_PATH);
		}

		/*
		 * Initialization.
		 */
		programName = program_Name;
		isEnvironmentToBeOpened = is_EnvironmentToBeOpened;
		isEnvironmentToBeClosed = is_EnvironmentToBeClosed;
		maxWorkUnitsPerIteration = X9Task.getSuggestedConcurrentThreads()
				* MAXIMUM_WORK_UNITS_PER_THREAD;
	}

	/**
	 * Set our completion call back, to inform the utilities console of work units that are now
	 * finished and thus update the progress bar.
	 * 
	 * @param work_UnitCallBack
	 *            work unit just completed
	 */
	public void setCompletionCallBack(final X9UtilWorkUnitCallBack work_UnitCallBack) {
		workUnitCallBack = work_UnitCallBack;
	}

	/**
	 * Launch (prepare and execute) a new utility function using an arguments array.
	 *
	 * @param args
	 *            command line arguments
	 * @return x9utilities work list that has been populated for execution
	 */
	public X9UtilWorkUnitList launch(final String args[]) {
		X9UtilWorkUnitList workUnitList = null;
		try {
			workUnitList = prepare(new X9CommandLine(args));
			execute(workUnitList);
		} catch (final Throwable t) {
			LOGGER.error("exception", t);
			if (workUnitList != null) {
				workUnitList.updateExitStatus(EXIT_STATUS_ABORTED);
			}
		}
		return workUnitList;
	}

	/**
	 * Prepare a new utility function using a command line as a string.
	 *
	 * @param commandLineAsString
	 *            command line as a string
	 * @return x9utilities work list that has been populated for execution
	 */
	public X9UtilWorkUnitList prepare(final String commandLineAsString) {
		return prepare(new X9CommandLine( // trim leading and trailing blanks
				StringUtils.trim( // also remove x9utilities program name when it appears
						StringUtils.removeStartIgnoreCase(commandLineAsString, "x9util"))));
	}

	/**
	 * Prepare a new utility function using an arguments array.
	 *
	 * @param args
	 *            command line arguments
	 * @return x9utilities work list that has been populated for execution
	 */
	public X9UtilWorkUnitList prepare(final String args[]) {
		return prepare(new X9CommandLine(args));
	}

	/**
	 * Prepare a new utility function using our internal x9commandLine instance. This process will
	 * parse the command line and then allocate/populate the work unit list, which can then be
	 * subsequently executed. We have separated prepare versus execute into two parts, allowing the
	 * caller to evaluate the complexity of the overall effort before initiating.
	 * 
	 * @param x9commandLine
	 *            command line instance
	 * @return x9utilities work list that has been populated for execution
	 */
	public X9UtilWorkUnitList prepare(final X9CommandLine x9commandLine) {
		/*
		 * Open the environment when enabled.
		 */
		if (isEnvironmentToBeOpened) {
			open(x9commandLine);
		}

		/*
		 * Prepare using the provided command line arguments and catch any thrown errors. Errors can
		 * be thrown when they occur during the initial command line parse and setup process.
		 */
		final X9UtilWorkUnitList workUnitList = new X9UtilWorkUnitList(x9commandLine, programName,
				x9license, getAbortOnExceptionsCount(x9commandLine));
		try {
			/*
			 * Populate the work list.
			 */
			populateWorkList(workUnitList);
		} catch (final Throwable t) {
			/*
			 * Exceptions are caught and logged, but they are not re-thrown.
			 */
			LOGGER.error("exception", t);
			workUnitList.updateExitStatus(EXIT_STATUS_ABORTED);
		}

		/*
		 * Return the populated work list.
		 */
		return workUnitList;
	}

	/**
	 * Execute all x9utilities work units as provided within the work list. We extend the option to
	 * run the work unit list either sequentially or from a series of concurrent background threads,
	 * which are two very different alternatives. When they are run sequentially, then the order is
	 * preserved by the overall elapsed time will be much larger. Running from threads makes more
	 * sense when there are a large number of work units to be processed. When running from threads,
	 * the execution order is unpredictable and system resource usage will be much higher.
	 * 
	 * @param workUnitList
	 *            work unit list
	 * @throws FileNotFoundException
	 */
	public void execute(final X9UtilWorkUnitList workUnitList) throws FileNotFoundException {
		/*
		 * Determine thread count when running concurrently. Although we allow a single work unit to
		 * be run from a background thread, there is obviously no benefit obtained from doing so.
		 */
		final X9CommandLine x9commandLine = workUnitList.getCommandLine();
		final String threadString = x9commandLine.getSwitchValue(X9UtilWorkUnit.SWITCH_THREADS);
		final int threadCount = StringUtils.isBlank(threadString) ? 0
				: X9UtilWorkUnit.getTaskThreadCount(threadString);

		/*
		 * Run all work units.
		 */
		final boolean areAbortListenersEnabled = X9AbortManager.areListenersEnabled();
		try {
			/*
			 * Execute all work units within the work list.
			 */
			executeWorkList(workUnitList, threadCount);
		} catch (final Throwable t) {
			/*
			 * Exceptions are caught and logged, but they are not re-thrown.
			 */
			LOGGER.error("exception", t);
			workUnitList.updateExitStatus(EXIT_STATUS_ABORTED);
		} finally {
			/*
			 * Restore the abort listener status (typically always setting back to enabled).
			 */
			X9AbortManager.setListenersEnabled(areAbortListenersEnabled);
		}
	}

	/**
	 * Populate the x9utilities work list from a single (model) command line. For example, batch
	 * processing with wild cards can generate multiple work units, each formulated from the model
	 * command line. However, the more simplistic use case would be creation of a single work unit.
	 * 
	 * @param workUnitList
	 *            work unit list
	 * @throws FileNotFoundException
	 */
	private void populateWorkList(final X9UtilWorkUnitList workUnitList)
			throws FileNotFoundException {
		/*
		 * Log the command line.
		 */
		final X9CommandLine x9commandLine = workUnitList.getCommandLine();
		final String[] args = x9commandLine.getCommandArgs();
		LOGGER.info("command line: {}", StringUtils.join(args, ' '));
		LOGGER.info("command args({}) argsLength({})", StringUtils.join(args, '|'), args.length);

		/*
		 * Populate the work list based on one of several strategies.
		 */
		if (x9commandLine.isSwitchSet(X9UtilWorkUnit.SWITCH_BATCH)) {
			/*
			 * Batch operations, where work units are manufactured recursively using a model.
			 */
			runMode = X9UtilWorkUnit.SWITCH_BATCH;
			addBatchWorkUnits(workUnitList);
		} else if (x9commandLine.isSwitchSet(X9UtilWorkUnit.SWITCH_SCRIPT)) {
			/*
			 * Scripted operations, where a text file contains the commands to be processed.
			 */
			runMode = X9UtilWorkUnit.SWITCH_SCRIPT;
			addScriptWorkUnits(workUnitList);
		} else {
			/*
			 * Single-entry operations, where we run the input command line as provided.
			 */
			runMode = SINGLE_ENTRY;
			workUnitList.addWorkUnit(new X9UtilWorkUnit(workUnitList, x9commandLine));
		}

		/*
		 * Set exit status when no files were found (probably no match against the pattern).
		 */
		if (workUnitList.getWorkUnitCount() == 0) {
			LOGGER.info("no files found");
			workUnitList.updateExitStatus(EXIT_STATUS_FILE_NOT_FOUND);
		}
	}

	/**
	 * Execute all work units within the work list.
	 * 
	 * @param workUnitList
	 *            work unit list
	 * @param threadCount
	 *            thread count
	 * @throws FileNotFoundException
	 */
	private void executeWorkList(final X9UtilWorkUnitList workUnitList, final int threadCount)
			throws FileNotFoundException {
		/*
		 * Now that the work unit list has been populated, we can establish ourselves at the
		 * callback for completions. This will calculate the number of bytes across all input files
		 * and use that to track overall completion percentage. Note that positioning this here
		 * allows it to function when we process concurrently from threads or otherwise sequentially
		 * from a simple list.
		 */
		workUnitList.setWorkUnitCompletionCallBack(workUnitCallBack);

		/*
		 * Turn off abort listeners, which is only applicable when running within the x9assist
		 * utilities console, where an abort listener is set via the abort manager. In that
		 * situation, we do not want to generate popup messages, since they interrupt the flow but
		 * also ask the user if they want to report the error via our website. That type of error
		 * reporting seems totally inappropriate, since the exception will most probably be related
		 * to a referenced input file that does not exist.
		 */
		X9AbortManager.setListenersEnabled(false);

		/*
		 * Run all work units.
		 */
		final int workUnitCount = workUnitList.getWorkUnitCount();
		LOGGER.info("starting tasks runMode({}) workUnitCount({})", runMode, workUnitCount);
		final long startTime = System.currentTimeMillis();
		if (threadCount > 0) {
			processListFromThreads(workUnitList, threadCount);
		} else {
			processList(workUnitList);
		}

		/*
		 * List the work units that were actually executed.
		 */
		int idx = 0;
		for (final X9UtilWorkUnit workUnit : workUnitList.getWorkUnits()) {
			if (workUnit.wasTaskStarted()) {
				LOGGER.info("workUnit({}) status({}) duration({}ms) commandLine({})", ++idx,
						workUnit.status, workUnit.getTaskDuration(),
						workUnit.getCommandLine().getCommandLineAsString());
			}
		}

		/*
		 * Write a combined results file (this is currently only used by scrub).
		 */
		final File resultsCsvFile = getCombinedResultsCsvFile(workUnitList);
		if (resultsCsvFile != null) {
			writeResultsFile(workUnitList, resultsCsvFile);
		}

		/*
		 * Log our overall completion message and elapsed time.
		 */
		LOGGER.info("all tasks completed workUnitCount({}) threadCount({}) duration({}ms)",
				workUnitCount, threadCount, System.currentTimeMillis() - startTime);
		LOGGER.info("{} finished exitStatus({})", programName, workUnitList.getExitStatus());

		if (batchThreadingBenefit > 0) {
			LOGGER.info("batchThreadingBenefit({}x)",
					X9D.formatSimpleFloat(batchThreadingBenefit, 1));
		}
	}

	/**
	 * Run the x9utilities batch process against a list of work units. In the most common runtime
	 * situation, there will be single work unit. However, we support several more complicated
	 * scenarios. First, we allow this method to be sequentially invoked multiple times by the
	 * invoking application. This would be typically used by an SDK application that is
	 * encapsulating x9utilities within their work flow. Second is that the provided work unit list
	 * contains multiple entries which are processed sequentially, with the maximum exit status
	 * ultimately assigned. For either of these, the SDK environment (logging, etc) is opened by the
	 * caller and not closed until all has been completed, where the close would typically be issued
	 * automatically behind the scenes by try-with-resources. The return results list will contain
	 * the start time, end time, and exit status for every function that was executed.
	 *
	 * @param workUnitList
	 *            work unit list
	 */
	private void processList(final X9UtilWorkUnitList workUnitList) {
		for (final X9UtilWorkUnit workUnit : workUnitList.getWorkUnits()) {
			if (isOutputCreatedEarlier(workUnitList, workUnit)) {
				/*
				 * Error if this output file was created by an earlier work unit.
				 */
				workUnit.status = X9UtilBatch.EXIT_STATUS_ABORTED;
			} else {
				/*
				 * Execute this work unit and update exit status.
				 */
				final int status = X9UtilWorker.executeWorkUnit(workUnit);
				workUnitList.updateExitStatus(status);
			}
		}
	}

	/**
	 * Run the x9utilities batch process against a list of work units using our task monitor. The
	 * work units will be run concurrently from background threads using our task monitor.
	 *
	 * @param workUnitList
	 *            work unit list
	 * @param maximumThreadCount
	 *            maximum thread count
	 */
	private void processListFromThreads(final X9UtilWorkUnitList workUnitList,
			final int maximumThreadCount) {
		/*
		 * Build an intermediate list of all work units to be processed.
		 */
		int totalEntries = 0;
		final List<X9UtilWorkUnit> workUnits = workUnitList.getWorkUnits();
		final List<X9UtilWorkUnit> remainingWorkList = new ArrayList<>();
		for (final X9UtilWorkUnit workUnit : workUnits) {
			totalEntries++;
			remainingWorkList.add(workUnit);
		}

		int monitorStatus = 0;
		int iterationCount = 0;
		final long startTime = System.currentTimeMillis();
		while (remainingWorkList.size() > 0) {
			/*
			 * Build a list of work units to be processed by this iteration. The list size is
			 * limited for several reasons. First is that it keeps the aggregate thread cpu time
			 * from becoming too excessive, which could trigger the monitor to falsely attempt to
			 * interrupt the thread. Second is that if any thread unexpectedly aborts, then we
			 * minimize the number of files that will not be processed due to the exception.
			 */
			final List<X9UtilWorkUnit> workList = new ArrayList<>();
			while (workList.size() < maxWorkUnitsPerIteration && remainingWorkList.size() > 0) {
				final X9UtilWorkUnit workUnit = remainingWorkList.get(0);
				if (isOutputCreatedEarlier(workUnitList, workUnit)) {
					/*
					 * Error if this output file was created by an earlier work unit.
					 */
					workUnit.status = X9UtilBatch.EXIT_STATUS_ABORTED;
				} else {
					/*
					 * Otherwise add this work unit to the work-list being accumulated.
					 */
					workList.add(workUnit);
				}

				/*
				 * Remove this work unit from the list and abort when unable to be removed.
				 */
				if (!remainingWorkList.remove(workUnit)) {
					X9Exception.abort("remove workUnit from work-list unsuccessful({})", workUnit);
				}
			}

			/*
			 * Allocate our task monitor and run background threads.
			 */
			iterationCount++;
			LOGGER.info("iteration({}) processing({}) remaining({}) maximumThreadCount({})",
					iterationCount, workList.size(), remainingWorkList.size(), maximumThreadCount);
			final X9UtilWorkMonitor taskMonitor = new X9UtilWorkMonitor(maximumThreadCount);
			monitorStatus = Math.max(taskMonitor.runWaitLog(workList), monitorStatus);
		}
		final long elapsedTime = System.currentTimeMillis() - startTime;

		/*
		 * Get aggregate elapsed time across all work units.
		 */
		float totalElapsedTime = 0;
		for (final X9UtilWorkUnit workUnit : workUnits) {
			workUnitList.updateExitStatus(workUnit.status);
			totalElapsedTime += workUnit.getTaskDuration();
		}

		/*
		 * Calculate the batch threading benefit which has been achieved by splitting work and
		 * running on parallel background threads. For example, if we are running with 4 threads,
		 * the calculated benefit might be 3.1X. This is ultimately very dependent on the size of
		 * the files assigned to the various threads. When large files are assigned to the same
		 * thread, then our overall benefit will be reduced. Hopefully, we get a good distribution
		 * and the benefit will be worth all of this effort.
		 */
		batchThreadingBenefit = elapsedTime > 0 ? totalElapsedTime / elapsedTime : 0;

		/*
		 * Log iteration and thread monitor statistics.
		 */
		LOGGER.info("thread monitor completed; iterationCount({}) totalEntries({}) "
				+ "maximumThreadCount({}) elapsedTime({}) monitorStatus({}) " + "exitStatus({})",
				iterationCount, totalEntries, maximumThreadCount, elapsedTime, monitorStatus,
				workUnitList.getExitStatus());
	}

	/**
	 * Add batch work units from the input folder using the wild card pattern. We are provided a
	 * model command line which represents the actual command to be processed. The input file (first
	 * parameter) is the folder to be scanned, where this is ultimately replaced by actual file
	 * names, as we sequentially build the work unit list. The work unit list is constructed here;
	 * the decision will be made later as to run sequentially or threaded.
	 * 
	 * @param workUnitList
	 *            work unit list
	 * @throws FileNotFoundException
	 */
	private void addBatchWorkUnits(final X9UtilWorkUnitList workUnitList)
			throws FileNotFoundException {
		/*
		 * We are provided the input file name as a wild card pattern that will be used to build the
		 * file list. In its most basic form, this pattern just contains the file extension.
		 * However, it can also be more detailed with additional pattern matching, based on user
		 * requirements. We now construct the filter and obtain a list of files by recursively
		 * walking all files and folders. We do not log the filenames since they will be listed on
		 * completion, with their associated statistics (start-stop, status, etc).
		 */
		final X9CommandLine x9commandLine = workUnitList.getCommandLine();
		final X9UtilWorkUnit workUnit = new X9UtilWorkUnit(workUnitList, x9commandLine);
		final File inputFile = workUnit.inputFile;
		final List<X9File> inputFileList;
		if (workUnit.inputFile == null) {
			throw X9Exception.abort("input file null");
		}

		/*
		 * Our input can be either a folder, file, or wild card string.
		 */
		final String inputFileName = inputFile.toString();
		final boolean inputIsDirectory = inputFile.isDirectory();
		if (inputIsDirectory || StringUtils.contains(inputFile.toString(), ASTERISK)) {
			final String inputFolder = inputIsDirectory ? inputFileName
					: FilenameUtils.getFullPath(inputFileName);
			final String wildCardString = inputIsDirectory ? "*.*"
					: FilenameUtils.getBaseName(inputFileName) + "."
							+ FilenameUtils.getExtension(inputFileName);
			final WildcardFileFilter fileFilter = new WildcardFileFilter(wildCardString,
					X9FileUtils.getFileNameCaseSensitivitySubjectToOS());
			inputFileList = X9FileUtils.createInputFileList(new File(inputFolder),
					X9FileUtils.SUBFOLDERS_INCLUDED, fileFilter, workUnit.getFileSkipInterval(),
					X9FileUtils.LOG_SELECTED_FILES_DISABLED);
			LOGGER.info("inputFolder({}) wildCardString({}) fileCount({})", inputFolder,
					wildCardString, inputFileList.size());
		} else if (!X9FileUtils.existsWithPathTracing(inputFile)) {
			throw X9Exception.abort("input file/folder does not exist");
		} else if (inputFile.isFile()) {
			inputFileList = new ArrayList<>();
			inputFileList.add(new X9File(inputFile));
		} else {
			throw X9Exception.abort("logic error");
		}

		/*
		 * Generate command lines for each of the input files to be processed. The file name will
		 * become args[0] since we require it to be the input file name, replacing the folder name
		 * that was scanned. In order to customize the command line, we support a series of tilde
		 * "~" based parameters that can be replaced based on the current file name and current
		 * date/time. This substitution facility will likely be improved as we get more experience,
		 * and also based on customer feedback subject to what they actually need.
		 */
		int index = 0;
		for (final File nextInputFile : inputFileList) {
			/*
			 * Parse the file name into various component parts.
			 */
			final LocalDateTime currentDateTime = X9LocalDate.getCurrentDateTime();
			final String fileName = FilenameUtils.normalize(nextInputFile.toString());
			final String filePath = FilenameUtils.getFullPath(fileName);
			final String filePathNoSeparator = FilenameUtils
					.getFullPathNoEndSeparator(fileName.toString());
			final String fileNameNoExtension = FilenameUtils.removeExtension(fileName);
			final String fileNameBase = FilenameUtils.getBaseName(fileName);
			final String fileNameExtension = FilenameUtils.getExtension(fileName);
			final String fileNameBaseWithExtension = fileNameBase + "." + fileNameExtension;

			/*
			 * Replace our supported parameters to build a revised command line. Note that our first
			 * action is to replace the input file name, which will be the wild card string, with
			 * the actual file name to be assigned to this work unit. We have to remove the batch,
			 * threads, and abort switches since they are our directives and are not not be passed
			 * on to the work unit that is being executed.
			 */
			final String cl = x9commandLine.getCommandLineAsString() //
					.replace(inputFileName, fileName) // replace wild card with actual file name
					.replaceAll("#fn#", fileName) //
					.replaceAll("#fp#", filePath) //
					.replaceAll("#fpns#", filePathNoSeparator) //
					.replaceAll("#fnnx#", fileNameNoExtension) //
					.replaceAll("#fnb#", fileNameBase) //
					.replaceAll("#fnx#", fileNameExtension) //
					.replaceAll("#fnbx#", fileNameBaseWithExtension) //
					.replaceAll("#i#", Integer.toString(++index)) //
					.replaceAll("#i2#", X9Numeric.getAsString(index, 2)) //
					.replaceAll("#i3#", X9Numeric.getAsString(index, 3)) //
					.replaceAll("#i4#", X9Numeric.getAsString(index, 4)) //
					.replaceAll("#i5#", X9Numeric.getAsString(index, 5)) //
					.replaceAll("#i6#", X9Numeric.getAsString(index, 6)) //
					.replaceAll("#yyyyMMdd#",
							X9LocalDate.formatDateTime(currentDateTime, "yyyyMMdd"))
					.replaceAll("#kkmmss#", X9LocalDate.formatDateTime(currentDateTime, "kkmmss"))
					.replaceAll("#HHmmss#", X9LocalDate.formatDateTime(currentDateTime, "HHmmss"))
					.replaceAll("#kkmmssSSS#",
							X9LocalDate.formatDateTime(currentDateTime, "kkmmssSSS"))
					.replaceAll("#HHmmssSSS#",
							X9LocalDate.formatDateTime(currentDateTime, "HHmmssSSS"))
					.replaceAll("#yy#", X9LocalDate.formatDateTime(currentDateTime, "yy"))
					.replaceAll("#yyyy#", X9LocalDate.formatDateTime(currentDateTime, "yyyy"))
					.replaceAll("#MM#", X9LocalDate.formatDateTime(currentDateTime, "MM"))
					.replaceAll("#dd#", X9LocalDate.formatDateTime(currentDateTime, "dd"))
					.replaceAll("#DDD#", X9LocalDate.formatDateTime(currentDateTime, "DDD"))
					.replaceAll("#HH#", X9LocalDate.formatDateTime(currentDateTime, "HH"))
					.replaceAll("#kk#", X9LocalDate.formatDateTime(currentDateTime, "kk"))
					.replaceAll("#mm#", X9LocalDate.formatDateTime(currentDateTime, "mm"))
					.replaceAll("#ss#", X9LocalDate.formatDateTime(currentDateTime, "ss"))
					.replaceAll("#SSS#", X9LocalDate.formatDateTime(currentDateTime, "SSS"))
					// remove the batch switch which will always be present
					.replaceAll("-" + X9UtilWorkUnit.SWITCH_BATCH, "")
					// remove the threads switch which is always followed by a numeric count
					.replaceAll("-" + X9UtilWorkUnit.SWITCH_THREADS + ":\\d+", "")
					// remove the abort on exception switch which is optionally followed by a count
					.replaceAll("-" + X9UtilWorkUnit.SWITCH_ABORT_ON_EXCEPTION + "(:\\d*)?", "");

			/*
			 * Use the finalized command line to create a new work unit and add to our list.
			 */
			workUnitList.addWorkUnit(new X9UtilWorkUnit(workUnitList, new X9CommandLine(cl)));
		}
	}

	/**
	 * Add script work units from an input text file that contains a series of command lines to be
	 * executed. For example, these could be a list of export commands to be executed, a list of
	 * write commands to build x9.37 files, or a combination of translate-write to convert an x9.37
	 * file from one format to another. The work unit list is constructed here; the decision will be
	 * made later as to run sequentially or threaded.
	 * 
	 * @param workUnitList
	 *            work unit list
	 * @throws FileNotFoundException
	 */
	private void addScriptWorkUnits(final X9UtilWorkUnitList workUnitList)
			throws FileNotFoundException {
		/*
		 * Get the script file reference and ensure it exists.
		 */
		int lineCount = 0;
		final X9CommandLine x9commandLine = workUnitList.getCommandLine();
		final File scriptFile = new File(
				x9commandLine.getSwitchValue(X9UtilWorkUnit.SWITCH_SCRIPT));
		if (X9FileUtils.existsWithPathTracing(scriptFile)) {
			/*
			 * Read the script file and create a work unit for each line that is present. Each of
			 * these are an independent command line that does not require any manipulation.
			 */
			LOGGER.info("reading script lines from file({})", scriptFile);
			final List<String> scriptLines = X9FileUtils.readTextFile(scriptFile);
			for (final String scriptLine : scriptLines) {
				LOGGER.info("lineNumber({}) scriptLine({})", ++lineCount, scriptLine);
				workUnitList.addWorkUnit(
						new X9UtilWorkUnit(workUnitList, new X9CommandLine(scriptLine)));
			}
			LOGGER.info("end of script file; lineCount({})", lineCount);
		} else {
			throw X9Exception.abort("scriptFile not found({})", scriptFile);
		}
	}

	/**
	 * Open this batch environment.
	 *
	 * @param x9commandLine
	 *            current command line
	 */
	public void open(final X9CommandLine x9commandLine) {
		/*
		 * Initialize.
		 */
		initSdkSettings();
		initLoggingEnvironment(x9commandLine);
		initClientLicense();
		initIssueCommentaries();

		/*
		 * Perform further initialization when not a functional help request.
		 */
		if (!x9commandLine.isSwitchSet(HELP_SWITCH)) {
			initLoadStartupFiles();
			initPurgeExpiredLogs();
		}
	}

	/**
	 * Initialize sdk settings. The confusion continues over the best user folder name. Basically,
	 * the user home folder is not significantly used by X9Utilities, so this decision should not be
	 * a bit issue. As of R4.11, we now have utilities console running in both x9assist and
	 * x9utilities. With that change, it makes the most sense for x9utilities to use the x9_assist
	 * home folder name, since this allows the file chooser to access the same bookmarks.
	 */
	public void initSdkSettings() {
		X9ProductManager.setProductName(programName, null);
		X9HomeFolder.setUserFolderName(X9HomeFolder.X9_ASSIST);
	}

	/**
	 * Initialize the logging environment.
	 *
	 * @param x9commandLine
	 *            current command line
	 */
	public void initLoggingEnvironment(final X9CommandLine x9commandLine) {
		/*
		 * Activate the console log when console logging is enabled from the command line. When
		 * running from an EXE created by JPackage, the --win-console option is always enabled which
		 * activates the console; this processing then determines if output is actually written.
		 * When running directly under a JVM, there are additional controls provided based on
		 * launching with either java.exe or javaw.exe. It would conceptually be nice to only
		 * activate when we are physically attached to a console, but that determination is
		 * difficult because System.console() is more about how the application is started than
		 * whether a console device physically exists on this system. For example, system console
		 * will be null when running under Eclipse. After considerable research, our decision is
		 * that we want to make the application behave very predictably and thus we faithfully
		 * follow our console setting from the command line. Hence bottom line is that the
		 * consoleOn/consoleOff will completely drive our decision to open the console log.
		 * According to the API, whether a virtual machine has a console is dependent upon the
		 * underlying platform and also upon the manner in which the virtual machine is invoked. If
		 * the virtual machine is started from an interactive command line without redirecting the
		 * standard input and output streams then its console will exist and will typically be
		 * connected to the keyboard and display from which the virtual machine was launched. If the
		 * virtual machine is started automatically, for example by a background job scheduler, then
		 * it will typically not have a console.
		 */
		final boolean isConsoleEnabled = !x9commandLine.isSwitchSet(CONSOLE_OFF_SWITCH);
		X9JdkLogger.setConsoleLogEnabled(isConsoleEnabled);

		/*
		 * Initialize the log using an optional log folder directive from the command line.
		 */
		final String logFolderName;
		if (x9commandLine.isSwitchSet(SWITCH_LOG_FOLDER)) {
			logFolderName = x9commandLine.getSwitchValue(SWITCH_LOG_FOLDER);
			X9JdkLogger.initialize(new File(logFolderName));
		} else {
			logFolderName = "defaulted";
			X9JdkLogger.initializeLoggingEnvironment();
		}

		/*
		 * Enable debug from the command line when directed.
		 */
		final boolean isDebug = x9commandLine.isSwitchSet(DEBUG_SWITCH);
		if (isDebug) {
			X9JdkLogger.setLogLevelAsDebug();
		}

		/*
		 * Log our startup message.
		 */
		LOGGER.info("{} started; logFolder[{}] isConsoleEnabled({}) isDebug({})", programName,
				logFolderName, isConsoleEnabled, isDebug);
		LOGGER.info("command line switches -consoleOn and -consoleOff can be used to "
				+ "enable/disable the console window");
	}

	/**
	 * Initialize the client license (must be done after logging has been opened).
	 */
	public void initClientLicense() {
		/*
		 * The encrypted license is typically only set by SDK applications. However, we also allow
		 * SDK customers to directly invoke X9Utilities functions from their SDK applications, and
		 * are aware of customers using this facility. Hence we accept an SDK license here.
		 */
		final String licenseXmlDocument = X9BuildAttr.getEncryptedLicenseXmlDocument();
		if (StringUtils.isNotBlank(licenseXmlDocument)) {
			/*
			 * Set a user provided hard-wired license.
			 */
			x9license = X9Credentials.setLicenseFromXmlDocumentString(licenseXmlDocument);
		} else {
			/*
			 * Get the best possible batch license (which includes SDK licenses).
			 */
			final X9ProductGroup x9productGroup = X9ProductFactory.getBatchProductGroup();
			x9license = X9Credentials.getRuntimeLicense(x9productGroup);

			/*
			 * Abort if no license was found or if it is expired.
			 */
			if (x9license == null) {
				throw X9Exception.abort("no license found");
			} else if (x9license.isLicenseExpired()) {
				throw X9Exception.abort("license is expired");
			} else if (!x9license.isMatchingProduct(x9productGroup)) {
				throw X9Exception.abort("license has non-matching product");
			}

			/*
			 * Set and log the client credentials for this runtime environment.
			 */
			X9Credentials.setAndLogCredentials(x9license);
		}
	}

	/**
	 * Issue various commentary messages (must be done after the client license is assigned).
	 */
	public void initIssueCommentaries() {
		X9BuildAttr.issueCandidateBuildMessageWhenNeeded(programName);
	}

	/**
	 * Load the required startup files.
	 */
	public void initLoadStartupFiles() {
		X9SdkRoot.logStartupEnvironment(programName);
		X9SdkRoot.loadXmlConfigurationFiles();
		X9OptionsManager.logStartupFolders();
	}

	/**
	 * Purge the expired log files.
	 */
	public void initPurgeExpiredLogs() {
		X9Purge.purgeLogFiles();
	}

	/**
	 * Close this batch environment.
	 */
	@Override
	public void close() {
		if (isEnvironmentToBeClosed) {
			X9SdkRoot.shutdown();
			LOGGER.info("{} closed", StringUtils.lowerCase(programName));
			X9JdkLogger.closeLog();
		}
	}

	/**
	 * The combined results csv file is optional. Typically we have a results file for every work
	 * unit, where those results written individually (not combined). However, when we are running
	 * in batch mode, we also support a combined results file with all results embedded within a
	 * single file. This is currently used by scrub, but could be used by other functions in the
	 * future. When results are being aggregated, those csv lines are stored in the workUnit. When
	 * those lines are populated, then we now recognize the condition that the combined results csv
	 * is to be written.
	 * 
	 * @param workUnitList
	 *            work unit list
	 * @return results csv file
	 */
	private File getCombinedResultsCsvFile(final X9UtilWorkUnitList workUnitList) {
		int lineCount = 0;
		File resultsCsvFile = null;
		for (final X9UtilWorkUnit workUnit : workUnitList.getWorkUnits()) {
			resultsCsvFile = resultsCsvFile != null ? resultsCsvFile : workUnit.resultsFile;
			for (final String[] csvArray : workUnit.getCsvResultsList()) {
				lineCount += csvArray.length;
			}
		}
		return lineCount == 0 ? null : resultsCsvFile;
	}

	/**
	 * Write the accumulated results csv file. These results are gathered and stored by each work
	 * unit, which eliminates the need for ordering or synchronization. When processing multiple
	 * files (eg, batched) we collect them by work unit, which allows the output csv lines to be
	 * ordered based on the input files and not the more random order of how they were processed.
	 * 
	 * @param workUnitList
	 *            work unit list
	 * @param resultsCsvFile
	 *            results csv file to be written
	 * @return line count written
	 */
	private int writeResultsFile(final X9UtilWorkUnitList workUnitList, final File resultsCsvFile) {
		int lineCount = 0;
		if (resultsCsvFile != null) {
			try (final X9CsvWriter csvWriter = new X9CsvWriter(resultsCsvFile)) {
				for (final X9UtilWorkUnit workUnit : workUnitList.getWorkUnits()) {
					for (final String[] csvArray : workUnit.getCsvResultsList()) {
						lineCount++;
						csvWriter.putFromArray(csvArray);
					}
				}
				lineCount++;
				csvWriter.putFromArray(new String[] { "end" });
			} catch (final Exception ex) {
				throw X9Exception.abort(ex);
			}
		}
		return lineCount;
	}

	/**
	 * Determine if the output file name from this work unit was also created by an earlier work
	 * unit. We cannot allow this to happen, since it would either attempt to overwrite a file that
	 * was just created, or just as bad, attempt to write to the same output file while it is also
	 * be written by another work unit. If this situation, we allow the first such work unit to run
	 * and we will identify and block any subsequent work units from executing.
	 * 
	 * @param workUnitList
	 *            work unit list
	 * @param workUnit
	 *            work unit that has been selected to run
	 * @return true if the output file was already created by an earlier work unit
	 */
	private boolean isOutputCreatedEarlier(final X9UtilWorkUnitList workUnitList,
			final X9UtilWorkUnit workUnit) {
		/*
		 * Get the index for the current work unit.
		 */
		final List<X9UtilWorkUnit> workUnits = workUnitList.getWorkUnits();
		final int workUnitIndex = workUnits.indexOf(workUnit);
		if (workUnitIndex < 0) {
			throw X9Exception.abort("workUnit not found({})", workUnit);
		}

		/*
		 * Return true when an earlier work unit references the same output file name. The error is
		 * logged with a stack trace, to make it very visible in the system log. This error is
		 * treated as an abort, which means the overall exit status will be minus one.
		 */
		if (workUnit.outputFile != null) {
			final String outputFileName = FilenameUtils.normalize(workUnit.outputFile.toString());
			for (int i = 0; i < workUnitIndex; i++) {
				final X9UtilWorkUnit earlierWorkUnit = workUnits.get(i);
				if (earlierWorkUnit.outputFile != null) {
					final String earlierFileName = FilenameUtils
							.normalize(earlierWorkUnit.outputFile.toString());
					if (StringUtils.equals(outputFileName, earlierFileName)) {
						LOGGER.error(
								"duplicated output for workUnit index ({}) already created "
										+ "by earlier index({}) as outputFileName({})",
								workUnitIndex, i, outputFileName, new Throwable());
						return true;
					}
				}
			}
		}

		/*
		 * Otherwise return false.
		 */
		return false;
	}

	/**
	 * Get abort on exceptions count from the command line. We return minus one when the switch is
	 * not present, which will disable this feature and allow unlimited exceptions.
	 * 
	 * @param x9commandLine
	 *            current command line
	 * @return abort on exception count
	 */
	private int getAbortOnExceptionsCount(final X9CommandLine x9commandLine) {
		final int allowedExceptions;
		if (x9commandLine.isSwitchSet(X9UtilWorkUnit.SWITCH_ABORT_ON_EXCEPTION)) {
			final String aoeValue = x9commandLine
					.getSwitchValue(X9UtilWorkUnit.SWITCH_ABORT_ON_EXCEPTION);
			if (StringUtils.isBlank(aoeValue)) {
				allowedExceptions = 1; // default to one when no value provided
			} else {
				allowedExceptions = X9Numeric.toInt(aoeValue);
				if (allowedExceptions < 0) {
					throw X9Exception.abort("aoeValue({}) not numeric", aoeValue);
				} else if (allowedExceptions == 0) {
					throw X9Exception.abort("aoeValue({}) cannot be zero", aoeValue);
				}
			}
		} else {
			allowedExceptions = X9UtilWorkUnitList.ALLOWED_EXCEPTIONS_ARE_UNLIMITED;
		}
		return allowedExceptions;
	}

}
