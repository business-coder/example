package com.x9ware.utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.compare.X9CompareFiles;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.elements.X9C;
import com.x9ware.fields.X9Field;
import com.x9ware.fields.X9FieldManager;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9TallyMap;
import com.x9ware.tools.X9TempFile;
import com.x9ware.tools.X9TextWriter;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilCompare is part of our utilities package which compares the contents of two x9.37 files. An
 * output differences file and results csv are created which lists the differences found.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilCompare {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/*
	 * Private.
	 */
	private X9Sdk sdk;
	private File inputFile1;
	private File inputFile2;
	private File outputFile;
	private File resultsFile;
	private X9TrailerManager x9trailerManager;

	/*
	 * Constants.
	 */
	private static final int EXIT_STATUS_DIFFERENCES = 1;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilCompare.class);

	/*
	 * X9UtilCompare Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilCompare(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
	}

	/**
	 * Compare two files and summarize the differences found.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Get work unit files.
		 */
		inputFile1 = workUnit.inputFile;
		inputFile2 = workUnit.secondaryFile;
		outputFile = workUnit.outputFile;
		resultsFile = workUnit.resultsFile;

		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		sdk = X9SdkFactory.getSdk(sdkBase);
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Allocate helper instances.
		 */
		x9trailerManager = new X9TrailerManager937(sdkBase);

		/*
		 * Compare files.
		 */
		final X9TempFile txtTempFile = X9UtilWorkUnit.getTempFileInstance(outputFile);
		int exitStatus = X9UtilBatch.EXIT_STATUS_ABORTED;
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Compare processing.
			 */
			exitStatus = runCompare(txtTempFile.getTemp());
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Rename the differences text file on completion.
				 */
				txtTempFile.renameTemp();
			} catch (final Exception ex) {
				/*
				 * Set message when aborted.
				 */
				x9totalsXml.setAbortMessage(ex.toString());
				throw X9Exception.abort(ex);
			} finally {
				/*
				 * Populate our file totals.
				 */
				x9totalsXml.setTotals(inputFile1, x9trailerManager);

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);
				LOGGER.info("compare {}", x9totalsXml.getTotalsString());
			}
		}

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

	/**
	 * File scrub processing with exception thrown on any errors.
	 *
	 * @param diffFile
	 *            differences text file
	 * @return exit status
	 * @throws Exception
	 */
	private int runCompare(final File diffFile) throws Exception {
		/*
		 * Build a list of the fields to be excluded from the compare.
		 */
		final List<X9Field> exclusionList;
		final String[] excludedFields = StringUtils
				.split(workUnit.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_EXCLUDE), '|');
		if (excludedFields == null || excludedFields.length == 0) {
			/*
			 * Set to null for the common case when there are no fields excluded.
			 */
			exclusionList = null;
		} else {
			/*
			 * Build a field list using the identified fields for this x9 specification.
			 */
			exclusionList = new ArrayList<>();
			final X9FieldManager x9fieldManager = sdkBase.getFieldManager();
			for (final String excludedField : excludedFields) {
				final X9Field x9field = x9fieldManager.getFieldObject(excludedField);
				if (x9field != null) {
					exclusionList.add(x9field);
					LOGGER.info(
							"field excluded recordType({}) fieldIndex({}) name({}) "
									+ "position({}) length({})",
							x9field.getRecordType(), x9field.getFieldIndex(),
							x9field.getDefinedPosition(), x9field.getDefinedLength());
				} else {
					throw X9Exception.abort("field not found({})", excludedField);
				}
			}
		}

		/*
		 * Allocate our text writer and accumulate differences as they are encountered.
		 */
		final List<String> differencesList = new ArrayList<>(1000);

		/*
		 * Allocate ancillary files.
		 */
		final String folder = X9FileUtils.getFolderName(diffFile);
		final String baseName = FilenameUtils.getBaseName(diffFile.toString());
		final File records1File = new File(folder, baseName + "_records1." + X9C.TXT);
		final File records2File = new File(folder, baseName + "_records2." + X9C.TXT);

		/*
		 * Text capture call back.
		 */
		final Consumer<String> textCaptureCallBack = new Consumer<String>() {

			@Override
			public void accept(final String text) {
				differencesList.add(text);

			}

		};

		/*
		 * Run the comparison.
		 */
		int diffCount = 0;
		X9CompareFiles x9compareFiles = null;
		try (final X9TextWriter diffWriter = new X9TextWriter(diffFile, textCaptureCallBack)) {
			/*
			 * Invoke our standard file comparison process.
			 */
			x9compareFiles = new X9CompareFiles(sdkBase, diffWriter, exclusionList,
					workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_VERBOSE),
					workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MASK));
			diffCount = x9compareFiles.runCompare(inputFile1, inputFile2, records1File,
					records2File);

			/*
			 * Delete the records1 and records2 text files when enabled.
			 */
			if (workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_DELETE)) {
				if (X9FileUtils.delete(records1File) && X9FileUtils.delete(records2File)) {
					LOGGER.info("text files deleted");
				}
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		} finally {
			/*
			 * We must abort when the compare itself encountered an exception.
			 */
			if (x9compareFiles == null || x9compareFiles.isCompareAborted()) {
				throw X9Exception.abort("compare aborted");
			}
		}

		/*
		 * Write the results file.
		 */
		final X9TempFile csvTempFile = X9UtilWorkUnit.getTempFileInstance(resultsFile);
		final X9TallyMap x9tallyMap = x9compareFiles.getFieldTallyMap();
		try (final X9CsvWriter csvWriter = new X9CsvWriter(csvTempFile.getTemp())) {
			/*
			 * Write actual field differences from the tally map.
			 */
			for (final Entry<String, AtomicInteger> entry : x9tallyMap.entrySet()) {
				csvWriter.startNewLine();
				final String[] diff = StringUtils.split(entry.getKey(),
						X9CompareFiles.TALLYMAP_SEPARATOR);
				csvWriter.addField(diff.length >= 1 ? diff[0] : "");
				csvWriter.addField(diff.length >= 2 ? diff[1] : "");
				csvWriter.addField(Integer.toString(entry.getValue().get()));
				csvWriter.write();
			}

			/*
			 * Include the output text file so everything is included in the results.
			 */
			for (final String text : differencesList) {
				if (StringUtils.isNotBlank(text)) {
					csvWriter.startNewLine();
					csvWriter.addField("textLine");
					csvWriter.addField(text);
					csvWriter.write();
				}
			}

			/*
			 * Write an end line.
			 */
			csvWriter.startNewLine();
			csvWriter.addField("end");
			csvWriter.write();
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		} finally {
			try {
				csvTempFile.renameTemp();
			} catch (final Exception ex) {
				throw X9Exception.abort(ex);
			}
		}

		/*
		 * Return our exit status.
		 */
		return diffCount == 0 ? X9UtilBatch.EXIT_STATUS_ZERO : EXIT_STATUS_DIFFERENCES;
	}

}
