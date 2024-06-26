package com.x9ware.utilities;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9MessageManager;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.core.X9;
import com.x9ware.core.X9FileAttributes;
import com.x9ware.core.X9Reader;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.error.X9Error;
import com.x9ware.error.X9ErrorCounters;
import com.x9ware.error.X9ErrorManager;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;
import com.x9ware.validate.X9Validate937;
import com.x9ware.validate.X9ValidateTiff;
import com.x9ware.validate.X9Validator;

/**
 * X9UtilValidate is part of our utilities package which validates an x9 file (both x9 and image
 * components). The x9 specification can be selected via a command line switch and will be defaulted
 * to x9.37. Any identified errors are written to an output text file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilValidate {

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
	private X9Sdk sdk;
	private File x9inputFile;
	private File resultsOutputFile;
	private int inputCount;
	private int errorCount;
	private int errorSeverity;
	private int checkCount;
	private int creditCount;
	private X9TrailerManager x9trailerManager;

	/*
	 * Constants.
	 */
	private static final String NONE = "None";
	private static final int HIGHEST_FIELD_NUMBER = 999;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilValidate.class);

	/*
	 * X9UtilValidate Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilValidate(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
	}

	/**
	 * Validate an x937 file per the active configuration and rules. We have an x9 file as input and
	 * an results (errors) csv file as output.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Get work unit files.
		 */
		x9inputFile = workUnit.inputFile;
		final X9TempFile x9tempFile = X9UtilWorkUnit.getTempFileInstance(workUnit.resultsFile);
		resultsOutputFile = x9tempFile.getTemp();

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
		 * Allocate sdkIO and open the x9 file.
		 */
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputFile(x9inputFile)) {
			/*
			 * Run the validator.
			 */
			sdkIO.openCsvOutputFile(resultsOutputFile);
			runValidator(sdkIO, x9reader);
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Release all sdkBase storage (since we loaded the file to the heap).
				 */
				sdkBase.systemReset();

				/*
				 * Rename on completion.
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
				 * Populate our file totals.
				 */
				x9totalsXml.setTotals(x9inputFile, x9trailerManager);

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);
				LOGGER.info(
						"validate finished; input records({}) checks({}) credits({}) "
								+ "errorCount({}) errorSeverity({})",
						inputCount, checkCount, creditCount, errorCount,
						X9MessageManager.getSeverityAsString(errorSeverity));
			}
		}

		/*
		 * Return status based on identified errors.
		 */
		final X9ErrorCounters errorCounters = sdkBase.getErrorManager().getTotalErrors();
		final int exitStatus;
		if (errorCounters.getSevereCount() > 0) {
			exitStatus = 4;
		} else if (errorCounters.getErrorCount() > 0) {
			exitStatus = 3;
		} else if (errorCounters.getWarnCount() > 0) {
			exitStatus = 2;
		} else if (errorCounters.getInfoCount() > 0) {
			exitStatus = 1;
		} else {
			exitStatus = 0;
		}
		return exitStatus;
	}

	/**
	 * File validator processing with exception thrown on any errors.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9reader
	 *            x9 reader for the opened input file
	 * @throws Exception
	 */
	private void runValidator(final X9SdkIO sdkIO, final X9Reader x9reader) throws Exception {
		/*
		 * Get file attributes.
		 */
		final X9FileAttributes x9fileAttributes = sdkIO.getInputFileAttributes();

		/*
		 * Allocate a new tiff validator instance.
		 */
		final X9ValidateTiff x9validateTiff = new X9ValidateTiff(sdkBase);

		/*
		 * Read the x9 file, populate x9objects, and validate the tiff images.
		 */
		X9SdkObject sdkObject = sdkIO.readNext();
		while (sdkObject != null) {
			/*
			 * Create and store a new x9object for this x9 record.
			 */
			inputCount++;
			final X9Object x9o = sdkIO.createAndStoreX9Object();

			/*
			 * Increment our counters.
			 */
			if (x9o.isDebit()) {
				checkCount++;
			}

			if (x9o.isCredit()) {
				creditCount++;
			}

			/*
			 * Log when enabled via a command line switch.
			 */
			if (isLoggingEnabled) {
				LOGGER.info("x9 recordNumber({}) data({})", x9o.x9ObjIdx,
						new String(x9o.x9ObjData));
			}

			/*
			 * Validate tiff images.
			 */
			if (sdkObject.getRecordType() == X9.IMAGE_VIEW_DATA) {
				x9validateTiff.validateIncomingImage(x9o, x9reader.getImageBuffer());
			}

			/*
			 * Get next record.
			 */
			sdkObject = sdkIO.readNext();
		}

		/*
		 * Create a validator instance and verify the x9 file from the x9objects.
		 */
		final X9Validator x9validate937 = new X9Validate937(sdkBase, x9fileAttributes);
		x9validate937.verifyFile();

		/*
		 * Get the error manager and set the final error severity.
		 */
		final X9ErrorManager x9errorManager = sdkBase.getErrorManager();
		errorSeverity = x9errorManager.getRunSeverity();

		/*
		 * Walk through the list and write all validation errors in record number sequence.
		 */
		X9Object x9o = sdkBase.getFirstObject();
		while (x9o != null) {
			/*
			 * Get errors for this record.
			 */
			final List<X9Error> errorArray = sdkBase.getErrorManager().getErrorsForRecord(x9o);

			/*
			 * Write errors for this record.
			 */
			if (errorArray != null && errorArray.size() > 0) {
				for (final X9Error x9error : errorArray) {
					writeError(sdkIO, x9error);
				}
			}

			/*
			 * Accumulate and roll totals.
			 */
			x9trailerManager.accumulateAndRollTotals(x9o);

			/*
			 * Get the next x9 record.
			 */
			x9o = x9o.getNext();
		}

		/*
		 * Write a none string when there are no errors.
		 */
		if (errorCount == 0) {
			sdkIO.startCsvLine();
			sdkIO.addAnotherCsvField(NONE);
			sdkIO.writeCsvLine();
		}
	}

	/**
	 * Write error records to the text output file.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9error
	 *            x9error to be written
	 * @throws IOException
	 */
	private void writeError(final X9SdkIO sdkIO, final X9Error x9error) throws IOException {
		/*
		 * Increment the number of errors.
		 */
		errorCount++;

		/*
		 * Get the error field number and change to be relative to one (one zero) when this is an
		 * actual field and not a pseudo field (like the tiff image).
		 */
		int fieldNumber = x9error.getFieldIndex();
		if (fieldNumber <= HIGHEST_FIELD_NUMBER) {
			fieldNumber++;
		}

		/*
		 * Write this error to the output csv file.
		 */
		sdkIO.startCsvLine();
		sdkIO.addAnotherCsvField(Integer.toString(x9error.getRecordNumber()));
		sdkIO.addAnotherCsvField(Integer.toString(x9error.getRecordType()));
		sdkIO.addAnotherCsvField(Integer.toString(fieldNumber));
		sdkIO.addAnotherCsvField(x9error.getFieldName());
		sdkIO.addAnotherCsvField(x9error.getErrorName());
		sdkIO.addAnotherCsvField(X9MessageManager.getSeverityAsString(x9error.getSeverity()));

		final String errorMessage = x9error.getCollectiveText();
		sdkIO.addAnotherCsvField(errorMessage);

		final String errorComments = x9error.getComments();
		if (StringUtils.isNotBlank(errorComments)
				&& StringUtils.equals(errorMessage, errorComments)) {
			sdkIO.addAnotherCsvField(errorComments);
		}

		sdkIO.writeCsvLine();
	}

}
