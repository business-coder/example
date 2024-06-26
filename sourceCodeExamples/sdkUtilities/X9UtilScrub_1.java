package com.x9ware.utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.beans.X9ScrubBean;
import com.x9ware.core.X9Reader;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.create.X9Scrub;
import com.x9ware.create.X9Scrub937;
import com.x9ware.create.X9ScrubXml;
import com.x9ware.options.X9Options;
import com.x9ware.toolbox.X9RandomizedList;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9TallyMap;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilScrub is part of our utilities package which scrubs the contents of an x9 file (both x9 and
 * image components) to remove proprietary and confidential information. A summary of scrubbed field
 * actions is written to an output text file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilScrub {

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/*
	 * Private.
	 */
	private final boolean isLoggingEnabled;
	private File x9inputFile;
	private File x9outputFile;
	private X9TrailerManager x9trailerManager;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilScrub.class);

	/*
	 * X9UtilScrub Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilScrub(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
	}

	/**
	 * Scrub an x937 file and remove confidential information. We have an input x9 file with a
	 * corresponding x9 and results (actions) csv file as output.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Get work unit files.
		 */
		x9inputFile = workUnit.inputFile;
		final X9TempFile x9tempFile = X9UtilWorkUnit.getTempFileInstance(workUnit.outputFile);
		x9outputFile = x9tempFile.getTemp();

		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		final X9SdkBase sdkBase = workUnit.getNewSdkBase();
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Allocate helper instances.
		 */
		x9trailerManager = new X9TrailerManager937(sdkBase);

		/*
		 * Scrub this x9 file.
		 */
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Scrub processing and update our csv results.
			 */
			final X9Scrub x9scrub = runScrub(sdkBase, sdkIO);
			constructResultsList(x9scrub.getTallyMap());
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Rename on completion. If this fails, it is perhaps a timing issue with the
				 * X9ThreadedWriter finally block; the output file may not yet be closed.
				 */
				x9tempFile.renameTemp();
			} catch (final Exception ex) {
				/*
				 * Set message when aborted.
				 */
				x9totalsXml.setAbortMessage(ex.toString());
				throw X9Exception.abort(ex);
			} finally {
				/*
				 * Release all stored x9objects.
				 */
				sdkBase.getObjectManager().reset();

				/*
				 * Populate our file totals.
				 */
				x9totalsXml.setTotals(x9inputFile, x9trailerManager);

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);
				LOGGER.info("scrub {}", x9totalsXml.getTotalsString());
			}
		}

		/*
		 * Return exit status zero.
		 */
		return X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * File scrub processing with exception thrown on any errors.
	 * 
	 * @param sdkBase
	 *            current sdkBase
	 * @param sdkIO
	 *            current sdkIO
	 * @return x9scrub instance
	 */
	private X9Scrub runScrub(final X9SdkBase sdkBase, final X9SdkIO sdkIO) {
		/*
		 * Get the scrub parameters from the command line.
		 */
		final X9ScrubXml x9scrubXml = new X9ScrubXml();
		final X9ScrubBean scrubBean = x9scrubXml.loadScrubConfiguration(workUnit.secondaryFile);

		/*
		 * Log input xml when requested.
		 */
		if (isLoggingEnabled) {
			LOGGER.info(scrubBean.toString());
		}

		/*
		 * Get the use case file and allocate a random list. We allow the file name defined in the
		 * xml definition to be relative or absolute.
		 */
		final X9ScrubBean.ScrubAttr scrubAttr = x9scrubXml.getAttr();
		final String useCaseName = scrubAttr.useCaseFileName;
		final File useCaseFile = X9FileUtils.isFileNameAbsolute(useCaseName) ? new File(useCaseName)
				: new File(X9Options.getuseCaseFolder(), useCaseName);

		if (!X9FileUtils.existsWithPathTracing(useCaseFile)) {
			throw X9Exception.abort("useCaseFile notFound({})", useCaseFile);
		}

		final X9RandomizedList randomUseCaseList = new X9RandomizedList(sdkBase, useCaseFile,
				X9Scrub.SCRUB_VALUES_PER_LINE, X9Scrub.NUMBER_OF_LONGS_PER_LINE_IS_ZERO);

		if (!randomUseCaseList.isValidList()) {
			throw X9Exception.abort("useCase file format is invalid at lineNumber({}) content({})",
					randomUseCaseList.getInvalidLineNumber(),
					randomUseCaseList.getInvalidContent());
		}

		if (randomUseCaseList.getNumberOfEntries() == 0) {
			throw X9Exception.abort("useCase file is empty");
		}

		/*
		 * Set the list for sequential retrieval.
		 */
		randomUseCaseList.setSequentialRetrieval();

		/*
		 * Open and read the x9 file to populate x9objects.
		 */
		try (final X9Reader x9reader = sdkIO.openInputFile(x9inputFile)) {
			/*
			 * Get first x9 record.
			 */
			X9SdkObject sdkObject = sdkIO.readNext();

			/*
			 * Read until end of file.
			 */
			while (sdkObject != null) {
				/*
				 * Create and store a new x9object for this x9 record.
				 */
				final X9Object x9o = sdkIO.createAndStoreX9Object();

				/*
				 * Log when enabled via a command line switch.
				 */
				if (isLoggingEnabled) {
					LOGGER.info("x9 recordNumber({}) data({})", x9o.x9ObjIdx,
							new String(x9o.x9ObjData));
				}

				/*
				 * Accumulate and roll totals.
				 */
				x9trailerManager.accumulateAndRollTotals(x9o);

				/*
				 * Get next record.
				 */
				sdkObject = sdkIO.readNext();
			}
		}

		/*
		 * Assign x9header indexes.
		 */
		sdkBase.getObjectManager().assignHeaderObjectIndexReferences();

		/*
		 * Open the image reader.
		 */
		sdkIO.openImageReader(x9inputFile);

		/*
		 * Scrub the x9 file.
		 */
		final X9Scrub x9scrub = new X9Scrub937(sdkBase, x9scrubXml);
		x9scrub.scrubToFile(x9outputFile);
		return x9scrub;
	}

	/**
	 * Construct the results list for this work unit.
	 * 
	 * @param x9tallyMap
	 *            tally map for this scrub operation
	 */
	private void constructResultsList(final X9TallyMap x9tallyMap) {
		for (final Entry<String, AtomicInteger> entry : x9tallyMap.entrySet()) {
			final List<String> csvList = new ArrayList<>();
			csvList.add(x9inputFile.toString());
			csvList.add(x9outputFile.toString());
			csvList.add(entry.getKey());
			csvList.add(Integer.toString(entry.getValue().get()));
			workUnit.addAnotherCsvLine(csvList);
		}
	}

}
