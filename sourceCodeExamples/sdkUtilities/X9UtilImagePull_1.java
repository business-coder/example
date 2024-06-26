
package com.x9ware.utilities;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.elements.X9C;
import com.x9ware.elements.X9Logging;
import com.x9ware.tools.X9ClearFolder;
import com.x9ware.tools.X9CsvLine;
import com.x9ware.tools.X9CsvReader;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.tools.X9TempFile;

/**
 * X9UtilImagePull is part of our utilities package which extracts a list of individual item images
 * by item sequence number and amount from a list of x9 files. We are designed as multi-threaded to
 * support a large number of requests to be pulled and processed concurrently.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilImagePull {

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/**
	 * Map of image pull requests which are stored by file name.
	 */
	private final Map<String, X9UtilImagePullEntry> fileMap = new TreeMap<>();

	/*
	 * Private.
	 */
	private final int maximumThreadCount;
	private final int maximumFilesPerIteration;
	private File csvInputFile;
	private File csvOutputFile;
	private File csvErrorFile;
	private int pullRequestCount;
	private int duplicateRequestCount;
	private int totalFileCount;
	private int totalRequestCount;

	/*
	 * Constants.
	 */
	private static final char COMMA = ',';
	private static final int MAXIMUM_FILES_PER_THREAD = 20;
	private static final String[] TIFF_EXTENSIONS = { X9C.TIF, X9C.TIFF };

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilImagePull.class);

	/**
	 * X9UtilImagePull Constructor.
	 *
	 * @param work_Unit
	 *            current work unit
	 */
	public X9UtilImagePull(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		maximumThreadCount = workUnit.getThreadCount();
		maximumFilesPerIteration = MAXIMUM_FILES_PER_THREAD * maximumThreadCount;
	}

	/**
	 * Read an image pull request file and extract item images to an output image folder.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Define input and output files.
		 */
		LOGGER.info("imagePull started; maximumThreadCount({})", maximumThreadCount);
		csvInputFile = workUnit.inputFile;
		final X9TempFile csvOutputTempFile = X9UtilWorkUnit
				.getTempFileInstance(workUnit.resultsFile);
		csvOutputFile = csvOutputTempFile.getTemp();

		final String folder = X9FileUtils.getFolderName(workUnit.resultsFile);
		final String baseName = FilenameUtils.getBaseName(workUnit.resultsFile.toString());
		final X9TempFile csvErrorTempFile = X9UtilWorkUnit
				.getTempFileInstance(new File(folder, baseName + "_ERRORS." + X9C.CSV));
		csvErrorFile = csvErrorTempFile.getTemp();

		/*
		 * Get the optional output xml parameter file.
		 */
		final File parmsXmlFile = workUnit.secondaryFile;
		if (parmsXmlFile == null) {
			LOGGER.info("parms:xmlFile omitted; default csv output fields assigned");
		}

		/*
		 * Set our sdk logging mode as quiet to keep down the noise level.
		 */
		X9Logging.setLoggingMode(X9Logging.LOGGING_QUIET);

		/*
		 * Create an xml instance which defined the csv output format.
		 */
		final X9UtilImagePullXml imagePullXml = new X9UtilImagePullXml(parmsXmlFile);

		/*
		 * Load the pull requests to a map by file name.
		 */
		loadPullRequests();

		/*
		 * Attach work threads and wait for them to complete.
		 */
		allocateMonitorAndRunThreads();

		/*
		 * Log overall pull statistics.
		 */
		logPullStatistics();

		/*
		 * Write the csv output files.
		 */
		writeCsvOutputFiles(imagePullXml);

		/*
		 * Rename our csv output files.
		 */
		csvOutputTempFile.renameTemp();
		csvErrorTempFile.renameTemp();

		/*
		 * Return exit status zero.
		 */
		return X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * Load image pull requests into a map that will reorder them by x9 file.
	 */
	private void loadPullRequests() {
		/*
		 * Examine the csv import file to ensure that it can be processed successfully.
		 */
		duplicateRequestCount = 0;
		X9CsvLine csvLine = null;
		try (final X9CsvReader csvReader = new X9CsvReader(csvInputFile)) {
			/*
			 * Continue until end of csv file or an error is encountered.
			 */
			while ((csvLine = csvReader.getNextCsvLine()) != null) {
				/*
				 * Requests can be in one of several formats. Columns 1 and 2 are required and
				 * contain file name and item sequence number. Column 3 is optional and contains the
				 * item amount. Column 4 is also optional and contains the item date. Our background
				 * logic behind this is that for some organizations the item sequence number will be
				 * unique, hence the other fields are somewhat redundant and are not needed to
				 * perform the selection. However, this is more typically not the case. Item amount
				 * is provided for further identification. Item date is needed when the sequence
				 * number is unique by business day but where the date itself is not embedded within
				 * the item sequence number value.
				 */
				totalRequestCount++;
				csvLine.logWhenDebugging();
				final String[] record = csvLine.getCsvArray();
				final int lineNumber = csvLine.getLineNumber();
				if (record.length < 2 || record.length > 7) {
					throw X9Exception.abort(
							"invalid pull request lineNumber({}); format is two to seven columns "
									+ "as fileName,itemSequenceNumber,[amount],[date],[routing],"
									+ "[account],[serial]; csvLine({})",
							lineNumber, StringUtils.join(record, COMMA));
				}

				/*
				 * Get the file name (which is mandatory).
				 */
				final String fileName = record[0];
				if (StringUtils.isBlank(fileName)) {
					throw X9Exception.abort("fileName missing lineNumber({}) value({}) csvLine({})",
							lineNumber, record[0], StringUtils.join(record, COMMA));
				}

				/*
				 * Get the item sequence number (which is optional).
				 */
				final String itemSequenceNumber;
				if (record.length > 1 && StringUtils.isNotBlank(record[1])) {
					itemSequenceNumber = record[1];
					if (!StringUtils.isNumeric(itemSequenceNumber)) {
						throw X9Exception.abort(
								"itemSequenceNumber not numeric lineNumber({}) value({}) csvLine({})",
								lineNumber, record[1], StringUtils.join(record, COMMA));
					}
				} else {
					itemSequenceNumber = null;
				}

				/*
				 * Get the item amount (which is optional).
				 */
				final BigDecimal itemAmount;
				if (record.length > 2 && StringUtils.isNotBlank(record[2])) {
					if (StringUtils.isNumeric(record[2])) {
						itemAmount = X9Decimal.getAsAmount(record[2]);
					} else {
						throw X9Exception.abort(
								"itemAmount not numeric lineNumber({}) value({}) csvLine({})",
								lineNumber, record[2], StringUtils.join(record, COMMA));
					}
				} else {
					itemAmount = null;
				}

				/*
				 * Get the item date (which is optional).
				 */
				final LocalDate itemDate;
				if (record.length > 3 && StringUtils.isNotBlank(record[3])) {
					itemDate = X9LocalDate.getDateFromString(record[3]);
					if (itemDate == null) {
						throw X9Exception.abort(
								"itemDate is invalid lineNumber({}) content({}) csvLine({})",
								lineNumber, record[3], StringUtils.join(record, COMMA));
					}
				} else {
					itemDate = null;
				}

				/*
				 * Get the item routing (which is optional).
				 */
				final String itemRouting;
				if (record.length > 4 && StringUtils.isNotBlank(record[4])) {
					itemRouting = record[4];
					if (!StringUtils.isNumeric(itemRouting)) {
						throw X9Exception.abort(
								"itemRouting is invalid lineNumber({}) content({}) csvLine({})",
								lineNumber, record[4], StringUtils.join(record, COMMA));
					}
				} else {
					itemRouting = null;
				}

				/*
				 * Get the item account (which is optional).
				 */
				final String itemAccount;
				if (record.length > 5 && StringUtils.isNotBlank(record[5])) {
					itemAccount = record[5];
					if (!StringUtils.isNumeric(itemAccount)) {
						throw X9Exception.abort(
								"itemAccount is invalid lineNumber({}) content({}) csvLine({})",
								lineNumber, record[5], StringUtils.join(record, COMMA));
					}
				} else {
					itemAccount = null;
				}

				/*
				 * Get the item serial (which is optional).
				 */
				final String itemSerial;
				if (record.length > 6 && StringUtils.isNotBlank(record[6])) {
					itemSerial = record[6];
					if (!StringUtils.isNumeric(itemSerial)) {
						throw X9Exception.abort(
								"itemSerial is invalid lineNumber({}) content({}) csvLine({})",
								lineNumber, record[6], StringUtils.join(record, COMMA));
					}
				} else {
					itemSerial = null;
				}

				/*
				 * Get this file from our map and create when we encounter the first.
				 */
				X9UtilImagePullEntry entryMap = fileMap.get(fileName);
				if (entryMap == null) {
					entryMap = new X9UtilImagePullEntry(fileName);
					fileMap.put(fileName, entryMap);
				}

				/*
				 * Add this new request to our map and log when determined to be a duplicate.
				 */
				final X9UtilImagePullRequest pullRequest = new X9UtilImagePullRequest(lineNumber,
						fileName, itemSequenceNumber, itemAmount, itemDate, itemRouting,
						itemAccount, itemSerial);
				if (entryMap.putMapEntry(pullRequest)) {
					/*
					 * Increment our request count.
					 */
					pullRequestCount++;
				} else {
					/*
					 * Log as a duplicate.
					 */
					duplicateRequestCount++;
					LOGGER.info("duplicate entry lineNumber({}) csvLine({})", lineNumber,
							StringUtils.join(record, COMMA));
				}
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Set and log the request counts.
		 */
		totalFileCount = fileMap.size();
		logRequestCounts();
	}

	/**
	 * Log the request counters.
	 */
	private void logRequestCounts() {
		LOGGER.info(
				"totalRequestCount({}) acceptedRequestCount({}) "
						+ "duplicateRequestCount({}) totalFileCount({})",
				totalRequestCount, pullRequestCount, duplicateRequestCount, totalFileCount);
	}

	/**
	 * Allocate our task monitor and run background threads.
	 */
	private void allocateMonitorAndRunThreads() {
		/*
		 * Assign our base image folder and append a timestamp suffix when directed.
		 */
		String baseImageFolderName = workUnit.imageFolder.toString();
		if (workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_APPEND_TIMESTAMP_TO_IMAGE_FOLDER_NAME)) {
			baseImageFolderName += "_" + X9LocalDate
					.formatDateTime(X9LocalDate.getCurrentDateTime(), "yyyyMMdd_HHmmss");
		} else {
			/*
			 * Clear the base image folder when directed.
			 */
			final File folder = new File(baseImageFolderName);
			if (workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_CLEAR_IMAGE_FOLDER)) {
				final X9ClearFolder x9clearFolder = new X9ClearFolder(folder, TIFF_EXTENSIONS);
				if (x9clearFolder.examineContent()) {
					final boolean isSuccessful = x9clearFolder.clearAll();
					LOGGER.info("imageFolder clear successful({}) folderNowExists({})",
							isSuccessful, folder.exists());
				}
			}
		}

		/*
		 * Abort if the assigned base image folder exists and is not empty. The base image folder
		 * originates from one of several sources. It may have been defaulted on the command line
		 * from the input csv file name, or it may alternatively have been explicitly user assigned.
		 * We may then have appended a timestamp to make the folder name unique, or we could have
		 * cleared a statically assigned folder name at user direction. Regardless of which of these
		 * actions have been taken, we now have our target image base folder which will be used for
		 * image extraction. This is our core mission and a potentially time consuming process so we
		 * proceed with caution.
		 */
		final File baseImageFolder = new File(baseImageFolderName);
		LOGGER.info("baseImageFolder({})", baseImageFolder);
		if (!workUnit.isCommandSwitchSet(
				X9UtilWorkUnit.SWITCH_DO_NOT_ABORT_WHEN_IMAGE_FOLDER_NOT_EMPTY)) {
			if (baseImageFolder.exists()) {
				final int fileCount = baseImageFolder.list().length;
				if (fileCount > 0) {
					throw X9Exception.abort(
							"baseImageFolder not empty fileCount({}) baseImageFolder({})",
							fileCount, baseImageFolder);
				}
			}
		}

		/*
		 * Build a list of all files to be pulled.
		 */
		int totalEntries = 0;
		final List<X9UtilImagePullEntry> fileWorkingList = new ArrayList<>();
		for (final X9UtilImagePullEntry pullEntry : fileMap.values()) {
			totalEntries++;
			fileWorkingList.add(pullEntry);
		}

		/*
		 * Pull all files.
		 */
		int iterationCount = 0;
		while (fileWorkingList.size() > 0) {
			try {
				/*
				 * Build a list of all files to be pulled during this iteration. The list size is
				 * limited for several reasons. First to ensure thread cpu time does not become
				 * excessive, which could trigger the monitor to falsely interrupt the thread.
				 * Second is that if any thread unexpectedly aborts, then as least we minimize the
				 * number of files that will not be processed due to the exception.
				 */
				iterationCount++;
				int count = 0;
				final List<X9UtilImagePullEntry> workerList = new ArrayList<>();
				while (count < maximumFilesPerIteration && fileWorkingList.size() > 0) {
					count++; // count and pull from the end to improve efficiency
					workerList.add(fileWorkingList.remove(fileWorkingList.size() - 1));
				}

				/*
				 * Allocate our task monitor and run background threads.
				 */
				final X9UtilImagePullMonitor taskMonitor = new X9UtilImagePullMonitor(
						maximumThreadCount, workUnit, baseImageFolderName);
				taskMonitor.runWaitLog(workerList);
			} catch (final Exception ex) {
				throw X9Exception.abort(ex);
			}
		}

		/*
		 * Log iteration statistics.
		 */
		LOGGER.info("all tasks completed; iterationCount({}) totalEntries({})", iterationCount,
				totalEntries);
	}

	/**
	 * Log overall pull statistics.
	 */
	private void logPullStatistics() {
		int itemsPulled = 0;
		int itemsNotFound = 0;
		int filesNotFound = 0;
		int filesAborted = 0;
		for (final X9UtilImagePullEntry pullEntry : fileMap.values()) {
			itemsPulled += pullEntry.getItemsPulled();
			itemsNotFound += pullEntry.getItemsNotFound();
			filesNotFound += pullEntry.getFilesNotFound();
			filesAborted += pullEntry.getFilesAborted();
		}
		LOGGER.info(
				"image pull finished; itemsPulled({}) itemsNotFound({}) filesNotFound({}) "
						+ "filesAborted({})",
				itemsPulled, itemsNotFound, filesNotFound, filesAborted);
	}

	/**
	 * Write the csv output files in the same order as the original pull request file.
	 *
	 * @param imagePullXml
	 *            csv output file xml instance
	 */
	private void writeCsvOutputFiles(final X9UtilImagePullXml imagePullXml) {
		/*
		 * Reorder the pull requests into their original sequence.
		 */
		LOGGER.info("resequencing");
		final Map<Integer, X9UtilImagePullRequest> sequencedMap = new TreeMap<>();
		for (final Entry<String, X9UtilImagePullEntry> l1 : fileMap.entrySet()) {
			final Map<String, X9UtilImagePullRequest> pullMap = l1.getValue().getPullMap();
			for (final Entry<String, X9UtilImagePullRequest> l2 : pullMap.entrySet()) {
				final X9UtilImagePullRequest pullRequest = l2.getValue();
				sequencedMap.put(pullRequest.getCsvLineNumber(), pullRequest);
			}
		}

		/*
		 * Write the csv output file for all successful requests using a user supplied xml
		 * definition. This design allows both the fields and their logical sequence to be
		 * customized per user requirements, and also insulates users from new fields that may be
		 * added from our enhancements.
		 */
		try (final X9CsvWriter csvWriter = new X9CsvWriter(csvOutputFile)) {
			int entryCount = 0;
			for (final X9UtilImagePullRequest pullRequest : sequencedMap.values()) {
				if (pullRequest.isMarkedAsSuccessful()) {
					entryCount++;
					csvWriter.putFromArray(
							imagePullXml.createCsvOutputArray(pullRequest.getOutputArray()));
				}
			}
			LOGGER.info("csvOutputFile written entryCount({})", entryCount);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Write the csv error file in our standard format which theoretically could be used again
		 * as input to the image pull process after identified error situations are corrected.
		 */
		try (final X9CsvWriter csvWriter = new X9CsvWriter(csvErrorFile)) {
			int entryCount = 0;
			for (final X9UtilImagePullRequest pullRequest : sequencedMap.values()) {
				if (!pullRequest.isMarkedAsSuccessful()) {
					entryCount++;
					final String[] errorArray = new String[5];
					errorArray[0] = pullRequest.getOutputEntry(X9UtilImagePullRequest.X9_FILE_NAME);
					errorArray[1] = pullRequest.getItemSequenceNumber();
					final BigDecimal itemAmount = pullRequest.getItemAmount();
					errorArray[2] = itemAmount == null ? "" : X9Decimal.getStringValue(itemAmount);
					errorArray[3] = Integer.toString(pullRequest.getCsvLineNumber());
					errorArray[4] = pullRequest.getErrorConditionName();
					csvWriter.putFromArray(errorArray);
				}
			}
			LOGGER.info("csvErrorFile written entryCount({})", entryCount);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

}
