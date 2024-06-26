package com.x9ware.utilities;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.core.X9;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.error.X9Error;
import com.x9ware.imaging.X9ImageMode;
import com.x9ware.tools.X9CsvReader;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;

/**
 * X9UtilImport is part of our utilities package which reads a csv and writes an x9 file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilImport {

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
	private final boolean isLoggingEnabled;
	private final boolean isRepairTrailers;
	private List<X9Error> processingErrorList;
	private X9Sdk sdk;
	private File csvInputFile;
	private File x9outputFile;
	private File imageFolder;
	private int inputCount;
	private int checkCount;
	private int creditCount;
	private int imageCount;

	/*
	 * Constants.
	 */
	private static final char COMMA = ',';

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilImport.class);

	/*
	 * X9UtilImport Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilImport(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
		isRepairTrailers = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_REPLACE_TRAILER_TOTALS);
	}

	/**
	 * Import an x937 file. We have a csv file as input and an x9 file as output.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Get work unit files.
		 */
		csvInputFile = workUnit.inputFile;
		final X9TempFile x9tempFile = X9UtilWorkUnit.getTempFileInstance(workUnit.outputFile);
		x9outputFile = x9tempFile.getTemp();
		imageFolder = workUnit.imageFolder;

		if (imageFolder != null && !X9FileUtils.existsWithPathTracing(imageFolder)) {
			throw X9Exception.abort("imageFolder notFound({})", imageFolder);
		}

		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		sdk = X9SdkFactory.getSdk(sdkBase);
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Set image enabled to true which allows image import functions to be supported.
		 */
		sdkBase.setImageIeEnabled(true);

		/*
		 * Set repair trailers based on our command line switch which defaults to false.
		 */
		sdkBase.setRepairTrailers(isRepairTrailers);

		/*
		 * Import to x9.
		 */
		X9TrailerManager x9trailerManager = null;
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Import processing.
			 */
			processImport(sdkIO);
			processingErrorList = sdkIO.getProcessingErrorList();

			/*
			 * Get our trailer totals.
			 */
			x9trailerManager = sdkIO.getTrailerManager();
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Rename on completion.
				 */
				x9tempFile.renameTemp();
			} catch (final Exception ex) {
				/*
				 * Set message when aborted.
				 */
				x9totalsXml.setAbortMessage(ex.toString());
				LOGGER.error("close exception", ex);
			} finally {
				/*
				 * Populate our file totals.
				 */
				if (x9trailerManager != null) {
					x9totalsXml.setTotals(workUnit.outputFile, x9trailerManager);
				}

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);
				LOGGER.info(
						"import finished; input records({}) checks({}) credits({}) imageCount({})",
						inputCount, checkCount, creditCount, imageCount);
			}
		}

		/*
		 * Return final exit status.
		 */
		return processingErrorList != null && processingErrorList.size() > 0
				? X9UtilBatch.EXIT_STATUS_ERROR
				: X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * Get the processing error list.
	 * 
	 * @return list of processing errors (never null)
	 */
	public List<X9Error> getProcessingErrorList() {
		return processingErrorList;
	}

	/**
	 * File import processing with exception thrown on any errors.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @throws Exception
	 */
	private void processImport(final X9SdkIO sdkIO) throws Exception {
		/*
		 * Read our input file.
		 */
		try (X9CsvReader csvReader = sdkIO.openCsvInputFile(csvInputFile)) {
			/*
			 * Open our output file.
			 */
			sdkIO.openOutputFile(x9outputFile);

			/*
			 * Set the image folder when provided on the command line.
			 */
			if (imageFolder != null) {
				sdkBase.setImageFolder(imageFolder, X9Sdk.IMAGE_IE_ENABLED);
			}

			/*
			 * Read all csv lines until end of file.
			 */
			X9SdkObject sdkObject = null;
			while ((sdkObject = sdkIO
					.getNextCsvInputRecord(X9ImageMode.IMPORT_IMAGE_FROM_EXTERNAL_FILE)) != null) {
				/*
				 * Get information for this sdkObject.
				 */
				inputCount++;
				final String[] record = sdkObject.getCsvArray();
				final int recordNumber = sdkObject.getRecordNumber();
				final int recordType = sdkObject.getRecordType();

				/*
				 * Log when enabled via a command line switch.
				 */
				if (isLoggingEnabled) {
					LOGGER.info("csv lineNumber({}) content({})", recordNumber,
							StringUtils.join(record, COMMA));
				}

				/*
				 * Process by record type.
				 */
				switch (recordType) {

					case X9.CHECK_DETAIL:
					case X9.RETURN_DETAIL: {
						checkCount++;
						break;
					}

					case X9.CREDIT:
					case X9.CREDIT_RECONCILIATION: {
						creditCount++;
						break;
					}

					case X9.IMAGE_VIEW_DATA: {
						imageCount++;
						break;
					}

				}

				/*
				 * Create the output record from this sdkObject.
				 */
				sdkIO.makeOutputRecordFromCsv(sdkObject);

				/*
				 * Write the x9.
				 */
				sdkIO.writeOutputFile(sdkObject);
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

}
