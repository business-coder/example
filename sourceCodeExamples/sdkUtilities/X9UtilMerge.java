package com.x9ware.utilities;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9Fids;
import com.x9ware.base.X9RandomReader;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkObjectFactory;
import com.x9ware.core.X9;
import com.x9ware.core.X9FileIdModifierXml;
import com.x9ware.core.X9HashCodeBuilder;
import com.x9ware.core.X9Reader;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.elements.X9C;
import com.x9ware.records.X9RecordFields;
import com.x9ware.tools.X9Ascii;
import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9File;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9Pattern;
import com.x9ware.tools.X9TempFile;
import com.x9ware.tools.X9TextFile;
import com.x9ware.types.X9Type25;
import com.x9ware.types.X9Type31;
import com.x9ware.types.X9Type61;
import com.x9ware.types.X9Type62;
import com.x9ware.types.X9Type99;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilMerge will merge one or more x9 input files into a single output file. Our design is to be
 * as robust as possible since many things can go wrong during this process. which could be data or
 * structural errors on individual files, or file access when reading files across networks. Our
 * input is a folder that consists of the files to be merged (either individual files, folders of
 * files, or some combination of that) where the files are identified by their file extension(s).
 * The output x9 file must be written to another folder to ensure it is excluded from the list of
 * files being merged. Processed files will be renamed on completion so they are not included in a
 * subsequent merge operation that is based on the same input folder. This implementation is
 * designed to be run against a landing zone where files are dropped for accumulation. We build a
 * list of merged files with all of those files renamed on our successful completion. Error files
 * are renamed with a failed extension to keep them from being processed by our next iteration.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilMerge {

	/**
	 * X9SdkBase references for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;
	private final X9RecordFields x9recordFields;

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/**
	 * X9HashCodeBuilder instance as assigned by our constructor.
	 */
	private final X9HashCodeBuilder x9hashCodeBuilder;

	/**
	 * X9TrailerManager instance as assigned by our constructor.
	 */
	private final X9TrailerManager x9trailerManager;

	/*
	 * Private.
	 */
	private final String inputExtensions;
	private final String renameExtension;
	private final String failedExtension;
	private final boolean isSelectWhenT99Missing;
	private final boolean isMergeByBundle;
	private final boolean isModifyBundles;
	private final boolean isIncludeSubFolders;
	private final boolean isSortDescending;
	private final boolean isGroupByItemCount;
	private final boolean isDoNotRename;
	private final boolean isUpdateTimestampFile;
	private final boolean isLoggingEnabled;
	private final long fileSizeLimit;
	private final String runDate;
	private final String runTime;
	private final List<File> successfulFileList = new ArrayList<>();
	private final List<X9UtilMergeFailed> failedFileList = new ArrayList<>();
	private int outputFileCounter;
	private int mergeCount;
	private int renameCount;
	private int successfulFileCount;
	private int failedFileCount;

	/*
	 * Private.
	 */
	private int recordCount;
	private int cashLetterCount;
	private int bundleCount;
	private int debitCount;
	private int creditCount;
	private boolean isAttributesSet;
	private BigDecimal debitAmount = BigDecimal.ZERO;
	private BigDecimal creditAmount = BigDecimal.ZERO;
	private boolean isOutput100180;
	private String originationRT;
	private String destinationRT;
	private byte[] cashLetterTrailer;
	private byte[] fileTrailer;

	/*
	 * Constants.
	 */
	private static final String KB = "kb";
	private static final String MB = "mb";
	private static final int ONE_KB = 1024;
	private static final int ONE_MB = ONE_KB * ONE_KB;
	private static final int DEFAULT_MAXIMUM_SIZE_IS_800MB = 800 * ONE_MB;
	private static final int EXIT_STATUS_MULTIPLE_FILES = 1;
	private static final int EXIT_STATUS_FAILED_FILES = 2;
	private static final int DIVIDER_LENGTH = 140;
	private static final String DIVIDER_LEADER = "<";
	private static final String MERGE_TIMESTAMP_FILE = "mergeTimeStamp.csv";
	private static final String COMMA = ",";
	private static final String QUOTE = "\"";
	private static final String NO_FAILED_FILES = "!!none!!";
	private static final String NO_TYPE99_TRAILER = "file does not end with type 99 trailer";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilMerge.class);

	/*
	 * X9UtilMerge Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilMerge(final X9UtilWorkUnit work_Unit) {
		/*
		 * Initialize.
		 */
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		x9recordFields = sdkBase.getRecordFields();
		x9hashCodeBuilder = new X9HashCodeBuilder(sdkBase);
		x9trailerManager = workUnit.allocate937TrailerManagerApplyOptions(sdkBase);

		/*
		 * Get the current run date and time, which will be assigned to all file headers.
		 */
		final LocalDateTime currentDateTime = X9LocalDate.getCurrentDateTime();
		runDate = X9LocalDate.formatDateAsYYYYMMDD(currentDateTime);
		runTime = X9LocalDate.formatDateTime(currentDateTime, "HHmm");

		/*
		 * Get command line switch settings.
		 */
		inputExtensions = workUnit.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_EXTENSION_INPUT);
		renameExtension = workUnit.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_EXTENSION_RENAME);
		failedExtension = workUnit.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_EXTENSION_FAILED);
		isSelectWhenT99Missing = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_T99_MISSING);
		isMergeByBundle = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MERGE_BY_BUNDLE);
		isModifyBundles = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MODIFY_BUNDLES);
		isIncludeSubFolders = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_INCLUDE_SUBFOLDERS);
		isSortDescending = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_SORT_DESCENDING);
		isGroupByItemCount = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_GROUP_BY_ITEM_COUNT);
		isDoNotRename = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_DO_NOT_RENAME);
		isUpdateTimestampFile = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_UPDATE_TIMESTAMP);
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);

		if (isDoNotRename) {
			LOGGER.warn("dnr switch should be used only for testing!");
		}

		/*
		 * Get the maximum output file size, which can be either maximum bytes or maximum items.
		 * This is an important setting since most applications have a maximum file size limit,
		 * which is commonly no more that 1400MB. Because of this, we provide a facility to assign
		 * this limit from the command line and default when not specified.
		 */
		final String maximumSize = workUnit.x9commandLine
				.getSwitchValue(X9UtilWorkUnit.SWITCH_MAXIMUM_FILE_SIZE);
		if (StringUtils.isNotBlank(maximumSize)) {
			if (StringUtils.endsWith(maximumSize, KB)) {
				fileSizeLimit = X9Numeric.toInt(StringUtils.removeEnd(maximumSize, KB)) * ONE_KB;
			} else if (StringUtils.endsWith(maximumSize, MB)) {
				fileSizeLimit = X9Numeric.toInt(StringUtils.removeEnd(maximumSize, MB)) * ONE_MB;
			} else {
				/*
				 * Maximum file size without "kb" or "mb" would most typically be used when the
				 * maximum limit is based on items and not bytes. For example, this facility might
				 * be used to limit output files at "10000" or "20000" items.
				 */
				fileSizeLimit = X9Numeric.toInt(maximumSize);
			}
			if (fileSizeLimit < 0) {
				throw X9Exception.abort("maximumSize({}) not numeric", maximumSize);
			}
		} else {
			fileSizeLimit = DEFAULT_MAXIMUM_SIZE_IS_800MB;
		}

		/*
		 * Log our parameters.
		 */
		LOGGER.info(
				"inputExtensions({}) renameExtension({}) isMergeByBundle({}) "
						+ "isModifyBundles({}) isDoNotRename({}) isUpdateTimestampFile({}) "
						+ "maximumFileSize({})",
				inputExtensions, (renameExtension == null ? "" : renameExtension), isMergeByBundle,
				isModifyBundles, isDoNotRename, isUpdateTimestampFile, fileSizeLimit);

	}

	/**
	 * Merge files from the input folder to the selected output file.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Validate that the rename extension is different from all input extensions. This is core
		 * to our design since we cannot allow a renamed file to match back to our input extensions,
		 * which would allow files to be extracted again in subsequent runs.
		 */
		final String[] inputFileExtensions = workUnit.validateInputFileExtensions(inputExtensions);
		if (StringUtils.isNotBlank(renameExtension)) {
			for (final String inputFileExtension : inputFileExtensions) {
				if (StringUtils.equalsIgnoreCase(inputFileExtension, renameExtension)) {
					throw X9Exception.abort(
							"rename inputExtension({}) must be different from renameExtension({})",
							inputFileExtension, renameExtension);
				}
			}
		}

		/*
		 * Set the configuration name when provided; we otherwise default to x9.37.
		 */
		workUnit.bindToCommandLineConfiguration(sdkBase);

		/*
		 * Get a list of the files to be processed, as contained within the provided input folder.
		 * This can be either from the input folder itself, or cascading into lower subfolders.
		 */
		final File inputFolder = workUnit.inputFile;
		final List<X9File> workList = X9FileUtils.createInputFileList(inputFolder,
				isIncludeSubFolders, inputFileExtensions, workUnit.getFileSkipInterval(),
				X9FileUtils.LOG_SELECTED_FILES_ENABLED);

		/*
		 * Examine the selected list of files to determine which files end with type 99 trailer
		 * records. Obtain the record and item counts from the trailer records. We need the item
		 * count since that is one of the attributes that we can use to limit packaging.
		 */
		final List<X9File> selectionList = new ArrayList<>(workList.size());
		for (final X9File inputFile : workList) {
			if (setInputFileRecordCount(inputFile)) {
				/*
				 * Add this file to our selection list, which represents all files to be processed.
				 */
				selectionList.add(inputFile);
			} else {
				/*
				 * Our default is to only merge files that include a type 99 file trailer; the lack
				 * of a type 99 trailer means that the file is structurally flawed. If we add a
				 * flawed file to our processing list, then we will encounter an exception while
				 * reading, and our output file will most probably be flawed as well.
				 */
				if (isSelectWhenT99Missing) {
					/*
					 * Enable with caution; although this option is provided, is not recommended.
					 */
					selectionList.add(inputFile);
				} else {
					/*
					 * Mark list file as failed, since we could not read the type99 trailer.
					 */
					markFileAsFailed(inputFile, NO_TYPE99_TRAILER);
				}
			}
		}

		/*
		 * Sort descending on size when enabled. This sort can be by item count, but is more
		 * typically going to be by file size. Sorting the list descending can improve our ability
		 * to pack more input files into a single output file, subject to limits.
		 */
		final List<X9File> inputFileList = isSortDescending
				? X9FileUtils.sortFileListDecending(selectionList, isGroupByItemCount)
				: selectionList;

		/*
		 * Create our processing lists which spreads the input files across one or more output
		 * files, subject to either maximum output file size or maximum item count.
		 */
		final List<X9UtilMergeOutput> mergeOutputList = createProcessingList(inputFileList);

		/*
		 * Log the files as they have been resequenced and grouped for output. This is a two level
		 * list, where the higher level represents an output file and the lower level represents all
		 * of the files that will be included within that group.
		 */
		int outputFileNumber = 0;
		final String leader = ">> Output file grouping ";
		LOGGER.info(leader + StringUtils.repeat(DIVIDER_LEADER, DIVIDER_LENGTH - leader.length()));
		for (final X9UtilMergeOutput mergeOutput : mergeOutputList) {
			outputFileNumber++;
			for (final X9File file : mergeOutput.getInputFileList()) {
				LOGGER.info(
						StringUtils.repeat(DIVIDER_LEADER, 2) + " "
								+ "group({}) file({}) length({}) itemCount({})",
						outputFileNumber, file, file.getFileLength(), file.getItemCount());
			}
		}
		LOGGER.info(StringUtils.repeat(DIVIDER_LEADER, DIVIDER_LENGTH));

		/*
		 * Initiate when there is at least one selected file in the input folder.
		 */
		if (inputFileList.size() > 0) {
			try {
				/*
				 * Process all files across all inputs and outputs.
				 */
				processAllFiles(mergeOutputList);
			} catch (final Exception ex) {
				/*
				 * Throw this exception since it would be totally unexpected. In this situation, the
				 * time stamp file is not updated since the run is unsuccessful.
				 */
				throw X9Exception.abort(ex);
			} finally {
				/*
				 * Populate overall output totals, which represent all input files.
				 */
				final X9TotalsXml x9totalsXml = new X9TotalsXml();
				x9totalsXml.setTotals("all merged output files", x9trailerManager);

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);
				LOGGER.info("merge {}", x9totalsXml.getTotalsString());
			}
		} else {
			LOGGER.info("no work found");
		}

		/*
		 * Rename our output file(s) on completion and after the input files were renamed.
		 */
		for (final X9UtilMergeOutput mergeOutput : mergeOutputList) {
			mergeOutput.getOutputFile().renameTemp();
		}

		/*
		 * Finalize our exit status.
		 */
		final int exitStatus;
		if (failedFileList.size() > 0) {
			exitStatus = EXIT_STATUS_FAILED_FILES;
		} else if (mergeOutputList.size() > 1) {
			exitStatus = EXIT_STATUS_MULTIPLE_FILES;
		} else {
			exitStatus = 0;
		}

		/*
		 * Log statistics on final completion.
		 */
		final String exitMessage = X9Pattern.format(
				"exitStatus({}) mergeCount({}) renameCount({}) successfulFileCount({}) "
						+ "failedFileCount({}) debitCount({}) debitAmount({}) creditCount({}) "
						+ "creditAmount({}) cashLetterCount({}) bundleCount({})",
				exitStatus, mergeCount, renameCount, successfulFileCount, failedFileCount,
				debitCount, debitAmount, creditCount, creditAmount, cashLetterCount, bundleCount);
		LOGGER.info(exitMessage);

		/*
		 * Update a time stamp file when enabled. This file is located in our output folder and can
		 * be monitored by an external watcher to signal an alarm if the scheduler does not
		 * periodically trigger us as expected. For example, we could be scheduled to run every
		 * thirty minutes and the watcher could be separately scheduled to run every two hours. The
		 * watcher could then send an email if we have not run within the last hour as an alert.
		 */
		if (isUpdateTimestampFile) {
			/*
			 * Write our statistics.
			 */
			final String timeStamp = X9LocalDate.formatTimeStamp();
			final StringBuffer sb = new StringBuffer(
					"timeStamp" + COMMA + timeStamp + X9C.LINE_SEPARATOR);
			sb.append("exitMessage" + COMMA + exitMessage + X9C.LINE_SEPARATOR);
			sb.append("statistics" + COMMA + exitStatus + COMMA + mergeCount + COMMA + renameCount
					+ COMMA + successfulFileCount + COMMA + failedFileCount + COMMA + debitCount
					+ COMMA + X9Decimal.getLongValue(debitAmount) + COMMA + creditCount + COMMA
					+ X9Decimal.getLongValue(creditAmount) + COMMA + cashLetterCount + COMMA
					+ bundleCount + X9C.LINE_SEPARATOR);

			/*
			 * Append information for those files which were failed during this run.
			 */
			if (failedFileList.size() > 0) {
				for (final X9UtilMergeFailed failedFile : failedFileList) {
					sb.append("failed" + COMMA + QUOTE + failedFile.getFailedFile().toString()
							+ QUOTE + COMMA + QUOTE + failedFile.getFailedReason() + QUOTE
							+ X9C.LINE_SEPARATOR);
				}
			} else {
				sb.append("failed" + COMMA + NO_FAILED_FILES + X9C.LINE_SEPARATOR);
			}

			/*
			 * Append information which describes output files and their content.
			 */
			outputFileNumber = 0;
			for (final X9UtilMergeOutput mergeOutput : mergeOutputList) {
				final File outputFile = mergeOutput.getOutputFile().getFinal();
				final String outputFileName = outputFile.toString();
				outputFileNumber++;
				for (final X9File file : mergeOutput.getInputFileList()) {
					sb.append("output" + COMMA + outputFileNumber + COMMA + QUOTE + outputFileName
							+ QUOTE + COMMA + QUOTE + file.toString() + QUOTE + COMMA
							+ file.getFileLength() + COMMA
							+ file.getFileTotals().getTotalsAsCsvString() + X9C.LINE_SEPARATOR);
				}
			}

			/*
			 * The time stamp switch can be optionally used to provide the time stamp file name,
			 * which can be either a base name or fully qualified. If it is just a base name, then
			 * we will store the file in our output folder. First, this location is clearly is the
			 * most appropriate, given it is one of our outputs. Second, we allow our input file
			 * extension to be a wildcard ("*"), so we do not want to add this output text file to
			 * the input folder, which would only create confusion.
			 */
			final File timeStampFile;
			final String timeStampSwitchValue = workUnit.x9commandLine
					.getSwitchValue(X9UtilWorkUnit.SWITCH_UPDATE_TIMESTAMP);
			final String outputFolder = FilenameUtils.getFullPath(workUnit.outputFile.toString());
			if (StringUtils.isBlank(timeStampSwitchValue)) {
				/*
				 * Use our default time stamp file name when not explicitly provided.
				 */
				timeStampFile = new File(outputFolder, MERGE_TIMESTAMP_FILE);
			} else {
				/*
				 * We have been provided a time stamp file name via the command line switch. We
				 * allow this switch value to be a base name or fully qualified. This can be very
				 * useful, since it allows the time stamp file to be located anywhere.
				 */
				timeStampFile = X9FileUtils.isFileNameAbsolute(timeStampSwitchValue)
						? new File(timeStampSwitchValue)
						: new File(outputFolder, timeStampSwitchValue);
			}

			/*
			 * Write the accumulated text to the time stamp file.
			 */
			final X9TextFile x9textFile = new X9TextFile();
			x9textFile.put(timeStampFile, sb.toString());
			LOGGER.info("timestamp updated({})", timeStampFile);
		}

		/*
		 * All completed.
		 */
		LOGGER.info("merge finished");

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

	/**
	 * Process all input files which may require that multiple output files are created.
	 * 
	 * @param mergeOutputList
	 *            list of the output files to be created
	 */
	private void processAllFiles(final List<X9UtilMergeOutput> mergeOutputList) {
		/*
		 * Run the merge for each of the output files to be created.
		 */
		for (final X9UtilMergeOutput mergeOutput : mergeOutputList) {
			/*
			 * Create a unique output file name for this iteration when the output file size has a
			 * defined maximum limit.
			 */
			outputFileCounter++;
			final File nextOutputFile;
			if (fileSizeLimit == 0) {
				/*
				 * The output file size is unlimited, so there is no need to assign a suffix.
				 */
				nextOutputFile = workUnit.outputFile;
				if (outputFileCounter > 1) {
					throw X9Exception.abort("unexpected outputFileCounter({})", outputFileCounter);
				}
			} else {
				/*
				 * The output file size is constrained, so we always assign the file suffix.
				 */
				String fileName = workUnit.outputFile.toString();
				final String extension = FilenameUtils.getExtension(fileName);
				fileName = StringUtils.removeEnd(fileName, "." + extension);
				nextOutputFile = new File(fileName + "_" + outputFileCounter + "." + extension);
			}

			/*
			 * Ensure that the output file is not included within the list of its inputs. This
			 * double check must be done after the next output file name has been assigned with its
			 * optional suffix. This is an important verification, since X9TempFIle will delete the
			 * output file if it already exists, and we cannot allow this when there is some type of
			 * discrepancy between our inputs and outputs.
			 */
			for (final X9File inputFile : mergeOutput.getInputFileList()) {
				if (inputFile.equals(nextOutputFile)) {
					throw X9Exception
							.abort("output file encountered within merge list; outputFile({}) "
									+ "size({})", nextOutputFile, nextOutputFile.length());
				}
			}

			/*
			 * Create as a temp file which will be renamed on our overall completion.
			 */
			final X9TempFile tempFile = X9UtilWorkUnit.getTempFileInstance(nextOutputFile);
			mergeOutput.setOutputFile(tempFile);

			/*
			 * Run this merge.
			 */
			mergeFiles(mergeOutput);
		}

		/*
		 * Rename all input files to either processed or failed.
		 */
		if (!isDoNotRename) {
			renameMergedFilesOnCompletion();
		}
	}

	/**
	 * Create our merge output list, which has one entry for each output file that is being created.
	 * Each merge output entry contains the output merge file to be written with a list of all input
	 * files that will be copied to this output file. Input files have been distributed such that
	 * the aggregate size (or item count) does not exceed the defined maximum limits.
	 *
	 * @param inputList
	 *            input file list which has been sorted descending on item count or size
	 * @return list which represents one or more output files as they are to be created
	 */
	private List<X9UtilMergeOutput> createProcessingList(final List<X9File> inputList) {
		/*
		 * Set the number of files being processed, which is used for subsequent confirmation.
		 */
		final int fileCount = inputList.size();

		/*
		 * Verify that the file list is descending when that option has been enabled.
		 */
		if (isSortDescending) {
			long lastSortValue = Long.MAX_VALUE;
			for (final X9File currentFile : inputList) {
				final long currentSortValue = currentFile.getSortValue(isGroupByItemCount);
				if (currentSortValue > lastSortValue) {
					LOGGER.error(
							"files not sorted descending; lastSortValue({}) "
									+ "currentSortValue({}) currentFile({})",
							lastSortValue, currentSortValue, currentFile);
					for (final X9File file : inputList) {
						LOGGER.error("length({}) itemCount({}) file({})", file.getFileLength(),
								file.getItemCount(), file);
					}
					throw X9Exception.abort("list not properly sorted");
				}
				lastSortValue = currentSortValue;
			}
		}

		/*
		 * Clone the provided file list (since we will manipulate it as we process).
		 */
		final List<X9File> remainingList = new ArrayList<>(inputList);

		/*
		 * Build our merge output list, which has one entry for each output file being created.
		 */
		final List<X9UtilMergeOutput> mergeOutputList = new ArrayList<>();
		final long maximumSize = fileSizeLimit > 0 ? fileSizeLimit : Long.MAX_VALUE;
		createAnotherList: while (remainingList.size() > 0) {
			/*
			 * Allocate a new list which will accumulate specific files to be merged. We then
			 * immediately add the first file (which is the largest remaining file) and remove that
			 * file from the list. This is important, since it ensures that this file will be
			 * written even in that situation where it exceeds what has been provided as the maximum
			 * size (it must always be written, regardless of size).
			 */
			final ArrayList<X9File> mergeList = new ArrayList<>();
			mergeOutputList.add(new X9UtilMergeOutput(mergeList));
			final X9File firstFile = remainingList.remove(0);
			mergeList.add(firstFile);
			long accumulatedSize = firstFile.getSortValue(isGroupByItemCount);

			/*
			 * Continue adding files while files remain and we have room to add more.
			 */
			addFilesToRunningList: while (remainingList.size() > 0
					&& accumulatedSize < maximumSize) {
				/*
				 * Walk all remaining files and find the next with the largest sort value such that
				 * it can be added and still fit within the maximum limit. This code takes advantage
				 * of the fact that the list we are provided is sorted in descending sequence.
				 */
				for (int i = 0, n = remainingList.size(); i < n; i++) {
					final X9File anotherFile = remainingList.get(i);
					final long currentSortValue = anotherFile.getSortValue(isGroupByItemCount);
					if (accumulatedSize + currentSortValue < maximumSize) {
						mergeList.add(anotherFile);
						accumulatedSize += currentSortValue;
						remainingList.remove(i);
						continue addFilesToRunningList;
					}
				}

				/*
				 * We could not find another file that would fit; exit the inner loop and continue
				 * by creating another file which begins with the largest remaining file.
				 */
				continue createAnotherList;
			}
		}

		/*
		 * Count the number of files as they were distributed across the lists.
		 */
		int numberOfFiles = 0;
		for (final X9UtilMergeOutput mergeOutput : mergeOutputList) {
			numberOfFiles += mergeOutput.getInputFileCount();
		}

		/*
		 * Ensure that we have properly distributed the files and otherwise abort.
		 */
		if (numberOfFiles != fileCount) {
			throw X9Exception.abort("numberOfFiles({}) unexpectedly not equal to fileCount({})",
					numberOfFiles, fileCount);
		}

		/*
		 * Return the file processing list.
		 */
		return mergeOutputList;
	}

	/**
	 * Merge a list of one or more files into a new output file. We have earlier validations that
	 * ensures the output file is not in the list of files, since it would be overwritten by the
	 * merge. However, we purposefully do not validate that any given file appears in the input list
	 * only once. This approach does mean that you can logically double all of the items within a
	 * given file by running a merge with that file in the input list two times.
	 *
	 * @param mergeOutput
	 *            merge output, which defines the output file and all associated input files
	 */
	private void mergeFiles(final X9UtilMergeOutput mergeOutput) {
		/*
		 * Error if the list of files to be merged is empty.
		 */
		if (mergeOutput == null || mergeOutput.getInputFileCount() == 0) {
			throw X9Exception.abort("no output");
		}

		/*
		 * Error if the output (temp) file is included in the list of files to be merged.
		 */
		final File outputFile = mergeOutput.getOutputFile().getTemp();
		for (final X9File file : mergeOutput.getInputFileList()) {
			if (file.equals(outputFile)) {
				throw X9Exception.abort(
						"output temp file found in merge list; outputFile({}) size({})", outputFile,
						outputFile.length());
			}
		}

		/*
		 * Copy all files.
		 */
		int fileCount = 0;
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase, sdkBase.getDialect());
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Open our output file.
			 */
			sdkIO.openOutputFile(outputFile);

			/*
			 * Copy records from all input files to the output file.
			 */
			for (final X9File inputFile : mergeOutput.getInputFileList()) {
				try {
					/*
					 * Merge the next file.
					 */
					fileCount++;
					final boolean isFirstFile = fileCount == 1;
					final boolean isLastFile = fileCount == mergeOutput.getInputFileCount();
					LOGGER.info("merging fileCount({}) inputFile({})", fileCount, inputFile);
					mergeAnotherFile(sdkIO, inputFile, isFirstFile, isLastFile);
				} catch (final Exception ex) {
					/*
					 * Log but do not abort so that all input files can be processed.
					 */
					LOGGER.error("error when processing file({})", inputFile, ex);
				}
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * Merge contents of the next file input to our output.
	 *
	 * @param sdkIO
	 *            current sdkIO instance
	 * @param inputFile
	 *            next file to be copied
	 * @param isFirstFile
	 *            true if first file
	 * @param isLastFile
	 *            true if last file
	 */
	private void mergeAnotherFile(final X9SdkIO sdkIO, final X9File inputFile,
			final boolean isFirstFile, final boolean isLastFile) {
		/*
		 * Open the input file and copy all to our current output file.
		 */
		try (final X9Reader inputReader = sdkIO.openInputFile(inputFile)) {
			/*
			 * Indicate that mailbox records should be accepted.
			 */
			inputReader.setMailBoxRecordAccepted(true);

			/*
			 * Get the first record so we can determine file attributes.
			 */
			X9SdkObject sdkObject = sdkIO.readNext();

			/*
			 * Always abort when the first record is not a file header.
			 */
			if (sdkObject.getRecordType() != X9.FILE_HEADER) {
				throw X9Exception.abort("first record not file header for inputFile({})",
						inputFile);
			}

			/*
			 * Log when enabled.
			 */
			if (isLoggingEnabled) {
				final byte[] dataRecord = sdkObject.getDataByteArray();
				LOGGER.info("fileHeader({})",
						new String(dataRecord, 0, Math.min(dataRecord.length, 100)));
			}

			/*
			 * Get the x9 standard level.
			 */
			final String standardLevel = sdkObject.getFieldValue(X9Fids.R01_STANDARD_LEVEL);

			/*
			 * Increment file count and set writer options from the first file that is opened.
			 */
			if (!isAttributesSet) {
				isAttributesSet = true;
				final X9SdkObjectFactory x9sdkObjectFactory = sdkBase.getSdkObjectFactory();
				final boolean isOutputEbcdic = inputReader.isEbcdicEncoding();
				final boolean isFieldZeroPrefixes = inputReader.isFieldZeroPrefixes();
				x9sdkObjectFactory.setIsOutputEbcdic(isOutputEbcdic);
				x9sdkObjectFactory.setFieldZeroInserted(isFieldZeroPrefixes);
				isOutput100180 = StringUtils.equals(standardLevel,
						X9.STANDARD_LEVEL_100_180_AS_STRING);
			} else {
				/*
				 * Error if we attempt to merge x9.37 and x9.100-187 files.
				 */
				if (isOutput100180
						^ StringUtils.equals(standardLevel, X9.STANDARD_LEVEL_100_180_AS_STRING)) {
					throw X9Exception.abort("cannot merge x9.37 and x9.100-180 files");
				}
			}

			/*
			 * Copy this file input to output. We allocate our own local trailer manager here, so we
			 * can accumulate the totals for just this one specific file.
			 */
			final X9TrailerManager937 trailerTotals = workUnit
					.allocate937TrailerManagerApplyOptions(sdkBase);
			while (sdkObject != null) {
				writeOutput(sdkIO, sdkObject, isFirstFile, isLastFile);
				sdkObject = sdkIO.readNext();
			}

			/*
			 * Increment our merge count and save the accumulated file totals.
			 */
			mergeCount++;
			markFileAsSuccessful(inputFile);
			inputFile.setFileTotals(trailerTotals.getOverallTotals());
		} catch (final Exception ex) {
			/*
			 * Mark the file as failed and log the error.
			 */
			markFileAsFailed(inputFile, ex.getMessage());
			LOGGER.error("error on file({})", inputFile, ex);
		}
	}

	/**
	 * Add the current file to our successful list and increment the associated counter.
	 *
	 * @param inputFile
	 */
	private void markFileAsSuccessful(final File inputFile) {
		successfulFileCount++;
		successfulFileList.add(inputFile);
	}

	/**
	 * Add the current file to our failed list and increment the associated counter.
	 *
	 * @param failedFile
	 *            failed file
	 * @param failedReason
	 *            failed reason
	 */
	private void markFileAsFailed(final File failedFile, final String failedReason) {
		failedFileCount++;
		failedFileList.add(new X9UtilMergeFailed(failedFile, failedReason));
	}

	/**
	 * Write the current sdkObject to the output file.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param sdkObject
	 *            current sdkObject
	 * @param isFirstFile
	 *            true if first file
	 * @param isLastFile
	 *            true if last file
	 * @return true if record was written otherwise false
	 */
	private boolean writeOutput(final X9SdkIO sdkIO, final X9SdkObject sdkObject,
			final boolean isFirstFile, final boolean isLastFile) {
		/*
		 * Create the output record.
		 */
		sdkObject.setUpdateType52ImageLengths(X9SdkIO.UPDATE_TYPE52_IMAGE_LENGTHS_DISABLED);
		sdkIO.makeOutputRecord(sdkObject);

		/*
		 * Get record type and record data.
		 */
		final int recordType = sdkObject.getRecordType();
		final String recordFormat = sdkObject.getRecordFormat();
		byte[] dataRecord = sdkObject.getDataByteArray();

		if (isLastFile) {
			/*
			 * If we are merging at the bundle level, then write the saved cash letter trailer from
			 * the first file so our t90 and t99 are taken from the same physical input file.
			 */
			if (recordType == X9.CASH_LETTER_TRAILER && isMergeByBundle
					&& cashLetterTrailer != null) {
				dataRecord = cashLetterTrailer;
			}

			/*
			 * Copy the saved file trailer from the first file so our file header and file trailer
			 * are taken from the same physical input file.
			 */
			if (recordType == X9.FILE_CONTROL_TRAILER && fileTrailer != null) {
				dataRecord = fileTrailer;
			}
		}

		/*
		 * Process based on record type; we copy EVERY record unless explicitly overridden.
		 */
		boolean isRecordToBeWritten = true;
		switch (recordType) {

			case X9.FILE_HEADER: {
				/*
				 * The file header is only written for the first file being included in this merge.
				 */
				isRecordToBeWritten = isFirstFile;

				/*
				 * Set file creation date, file creation time, and the file ID modifier.
				 */
				sdkObject.setFieldValue(x9recordFields.r01FileCreationDate, runDate);
				sdkObject.setFieldValue(x9recordFields.r01FileCreationTime, runTime);
				final int outputIndex = outputFileCounter - 1;
				final int fileIdIndex = outputIndex < X9FileIdModifierXml.MODIFIER_ALPHA_NUMERIC
						.length() ? outputIndex : 0;
				final String fileIdModifier = StringUtils
						.mid(X9FileIdModifierXml.MODIFIER_ALPHA_NUMERIC, fileIdIndex, 1);
				sdkObject.setFieldValue(x9recordFields.r01FileIdModifier, fileIdModifier);
				LOGGER.info(
						"mergeCount({}) outputIndex({}) fileIdModifier({}) "
								+ "runDate({}) runTime({})",
						mergeCount, outputIndex, fileIdModifier, runDate, runTime);
				break;
			}

			case X9.CASH_LETTER_HEADER: {
				/*
				 * Determine if the first file is being copied.
				 */
				if (isFirstFile) {
					/*
					 * The cash letter header from the first file will always be written. We now
					 * save the origination and destination RT from the cash letter header record,
					 * which may be subsequently populated into the bundle records.
					 */
					originationRT = sdkObject
							.getFieldValue(x9recordFields.r10EceInstitutionRouting);
					destinationRT = sdkObject.getFieldValue(x9recordFields.r10DestinationRouting);
				} else {
					/*
					 * When not the first file, only write when merging at the cash letter level.
					 */
					isRecordToBeWritten = !isMergeByBundle;
				}

				/*
				 * Increment the cash letter count when written.
				 */
				if (isRecordToBeWritten) {
					cashLetterCount++;
				}
				break;
			}

			case X9.BUNDLE_HEADER: {
				/*
				 * Get the x9field object for the bundle identifier. We will assign the next bundle
				 * identifier to ensure it is unique within the output file being created.
				 */
				bundleCount++;
				sdkObject.setFieldValue(x9recordFields.r20BundleIdentifier,
						X9Numeric.getAsString(bundleCount, 5));

				/*
				 * Modify the origination/destination RT in the bundle header record per UCD
				 * requirements when merging at the bundle level and directed to do so.
				 */
				if (isModifyBundles) {
					sdkObject.setFieldValue(x9recordFields.r20EceInstitutionRouting, originationRT);
					sdkObject.setFieldValue(x9recordFields.r20DestinationRouting, destinationRT);
				}
				break;
			}

			case X9.CHECK_DETAIL: {
				debitCount++;
				final X9Type25 t25 = new X9Type25(sdkBase, dataRecord);
				debitAmount = debitAmount.add(X9Decimal.getAsAmount(t25.amount));
				break;
			}

			case X9.RETURN_DETAIL: {
				debitCount++;
				final X9Type31 t31 = new X9Type31(sdkBase, dataRecord);
				debitAmount = debitAmount.add(X9Decimal.getAsAmount(t31.amount));
				break;
			}

			case X9.CREDIT_RECONCILIATION: {
				creditCount++;
				final X9Type61 t61 = new X9Type61(sdkBase, recordFormat, dataRecord);
				creditAmount = creditAmount.add(X9Decimal.getAsAmount(t61.amount));
				break;
			}

			case X9.CREDIT: {
				creditCount++;
				final X9Type62 t62 = new X9Type62(sdkBase, dataRecord);
				creditAmount = creditAmount.add(X9Decimal.getAsAmount(t62.amount));
				break;
			}

			case X9.CASH_LETTER_TRAILER: {
				/*
				 * Save the first cash letter trailer (should be on the first file).
				 */
				if (cashLetterTrailer == null) {
					cashLetterTrailer = cloneRecord(dataRecord);
				}

				/*
				 * Write the cash letter trailer on the last file and then always when merging at
				 * the cash letter (not bundle) level.
				 */
				isRecordToBeWritten = isLastFile || !isMergeByBundle;
				break;
			}

			case X9.FILE_CONTROL_TRAILER: {
				/*
				 * Save the first file trailer (should be on the first file).
				 */
				if (fileTrailer == null) {
					fileTrailer = cloneRecord(dataRecord);
				}

				/*
				 * Only write the file trailer when copying the last file.
				 */
				isRecordToBeWritten = isLastFile;
				break;
			}

			default: {
				break;
			}
		}

		/*
		 * Write when selected.
		 */
		if (isRecordToBeWritten) {
			/*
			 * Fatal error if the first record being written is not a file header.
			 */
			if (recordCount == 0 && recordType != X9.FILE_HEADER) {
				throw X9Exception.abort("first recordType({}) not file header", recordType);
			}

			/*
			 * Increment current record count. Remember that this is critical since the current
			 * record number is included in the accumulated hash code.
			 */
			recordCount++;

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("merge writing recordNumber({}) recordType({}) dataRecord({})",
						recordCount, recordType, new String(dataRecord));
			}

			/*
			 * Accumulate and populate totals within the trailer records.
			 */
			x9trailerManager.accumulateAndPopulate(recordType, recordFormat, dataRecord);

			/*
			 * Update image lengths and write from the possibly modified data.
			 */
			sdkObject.setUpdateType52ImageLengths(X9SdkIO.UPDATE_TYPE52_IMAGE_LENGTHS_DISABLED);
			sdkIO.writeOutputFileFromData(sdkObject, dataRecord);

			/*
			 * Accumulate into our running hash code for the new file being created, after trailer
			 * totals have been populated and all possible data modifications have been applied.
			 * This hash code will be validated by X9Driver when this file is subsequently loaded.
			 */
			x9hashCodeBuilder.appendHashCodeForSdkObject(recordCount, sdkObject);

			/*
			 * Log when enabled.
			 */
			if (isLoggingEnabled) {
				LOGGER.info("merge recordNumber({}) recordType({}) dataRecord({}) hashCode({})",
						recordCount, recordType, new String(dataRecord),
						x9hashCodeBuilder.getComputedHashCode());
			}
		}

		/*
		 * Return true if this record was written.
		 */
		return isRecordToBeWritten;
	}

	/**
	 * Set the record and item count for a specific input file. This is done by random reading the
	 * file trailer record which appears at the end of each input file.
	 * 
	 * @param inputFile
	 *            current input file
	 * @return true if the record count has been determined otherwise false
	 */
	private boolean setInputFileRecordCount(final X9File inputFile) {
		boolean isSuccessful = false;
		final long fileLength = inputFile.getFileLength();
		if (fileLength < X9.RECORD_LENGTH) {
			LOGGER.error("unable to obtain last record due to fileLength({})", fileLength);
		} else {
			/*
			 * Try with finally to ensure that we always close.
			 */
			final X9RandomReader x9randomReader = new X9RandomReader();
			try {
				/*
				 * Read the file header record and translate EBDCIC to ASCII when needed.
				 */
				x9randomReader.open(inputFile);
				final long lastRecordPosition = Math.max(fileLength - X9.RECORD_LENGTH, 0);
				final int lastRecordLength = (int) Math.min(fileLength - lastRecordPosition + 1,
						X9.RECORD_LENGTH);
				final byte[] lastRecord = x9randomReader.readFromFile(lastRecordPosition,
						lastRecordLength);
				if ((lastRecord[0] & 0xFF) == 0xF9) {
					X9Ascii.translateEbcdicToAscii(lastRecord, 0, X9.RECORD_LENGTH);
				}

				/*
				 * Set the record count and item count from the file trailer record.
				 */
				if ((lastRecord[0] & 0xFF) == 0x39 && (lastRecord[1] & 0xFF) == 0x39) {
					final X9Type99 t99 = new X9Type99(sdkBase, lastRecord);
					final int totalRecordCount = X9Numeric.toInt(t99.totalRecordCount);
					final int totalItemCount = X9Numeric.toInt(t99.totalItemCount);
					if (totalRecordCount >= 0) {
						inputFile.setRecordCount(totalRecordCount);
					} else {
						LOGGER.error("totalRecordCount not numeric({})", t99.totalRecordCount);
					}
					if (totalItemCount >= 0) {
						inputFile.setItemCount(totalItemCount);
					} else {
						LOGGER.error("totalItemCount not numeric({})", t99.totalItemCount);
					}
					if (totalRecordCount >= 0 && totalItemCount >= 0) {
						isSuccessful = true;
					}
				}

				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("fileName({}) recordCount({}) itemCount({})", inputFile,
							inputFile.getRecordCount(), inputFile.getItemCount());
				}
			} finally {
				/*
				 * Always close.
				 */
				x9randomReader.close();
			}
		}

		/*
		 * Always log when a type 99 trailer has not been found.
		 */
		if (!isSuccessful) {
			LOGGER.error(NO_TYPE99_TRAILER + "({})", inputFile);
		}
		return isSuccessful;
	}

	/**
	 * Rename input files to either their successful or failed extensions. This rename ensures that
	 * a file that has been merged will not be selected again by the next iterative run. Our design
	 * is that these renamed files should be externally moved to an archive folder immediately on
	 * our completion, but this rename also ensures that we will not process the files again even if
	 * that does not happen. Use of the do not rename switch is only provided to facilitate testing.
	 */
	private void renameMergedFilesOnCompletion() {
		/*
		 * Rename files that were successful.
		 */
		if (StringUtils.isNotBlank(renameExtension)) {
			for (final File inputFile : successfulFileList) {
				final File renamedFile = new File(
						FilenameUtils.removeExtension(inputFile.toString()) + "."
								+ renameExtension);
				renameFile(inputFile, renamedFile);
			}
		}

		/*
		 * Rename files that were failed.
		 */
		if (StringUtils.isNotBlank(failedExtension)) {
			for (final X9UtilMergeFailed failedFile : failedFileList) {
				final File inputFile = failedFile.getFailedFile();
				final File renamedFile = new File(
						FilenameUtils.removeExtension(inputFile.toString()) + "."
								+ failedExtension);
				renameFile(inputFile, renamedFile);
			}
		}
	}

	/**
	 * Rename an input file with logging.
	 *
	 * @param oldFile
	 *            old file
	 * @param newFile
	 *            new file
	 */
	private void renameFile(final File oldFile, final File newFile) {
		try {
			if (X9FileUtils.rename(oldFile, newFile)) {
				renameCount++;
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * Clone a data record byte array to a new target byte array.
	 *
	 * @param dataRecord
	 *            input data as byte array
	 * @return cloned record
	 */
	private byte[] cloneRecord(final byte[] dataRecord) {
		final int len = dataRecord.length;
		final byte[] byteArray = new byte[len];
		System.arraycopy(dataRecord, 0, byteArray, 0, len);
		return byteArray;
	}

}
