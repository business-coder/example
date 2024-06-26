package com.x9ware.examples;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.core.X9FileAttributes;
import com.x9ware.core.X9Reader;
import com.x9ware.error.X9Error;
import com.x9ware.error.X9ErrorManager;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.validate.X9Validate937;
import com.x9ware.validate.X9ValidateTiff;
import com.x9ware.validate.X9Validator;

/**
 * X9VerifyX9 verifies an x9.37 file by applying the same rule based validations that are used by
 * our X9Assist desktop application. Errors will be written to the system log.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9VerifyX9 {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final X9Sdk sdk;
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");

	/*
	 * Constants.
	 */
	private static final String X9VERIFYX9 = "X9VerifyX9";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9VerifyX9.class);

	/*
	 * X9VerifyX9 Constructor.
	 */
	public X9VerifyX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9VERIFYX9);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdk = X9SdkFactory.getSdk(sdkBase);
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
	}

	/**
	 * Load the x9.37 file, run the validator, and list all errors.
	 */
	private void process() {
		/*
		 * Read the x9.37 file, populate x9objects, and validate the tiff images. This example opens
		 * the x9.37 as an input file, but you can also use sdkIO.openInputReader() to read from an
		 * input stream.
		 */
		final File x9InputFile = new File(baseFolder, "Test file with errors.x9");
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputFile(x9InputFile)) {
			/*
			 * X9ValidateTiff instance.
			 */
			final X9ValidateTiff x9validateTiff = new X9ValidateTiff(sdkBase);

			/*
			 * Get first x9.37 record and file attributes.
			 */
			X9SdkObject sdkObject = sdkIO.readNext();
			final X9FileAttributes x9fileAttributes = sdkIO.getInputFileAttributes();

			/*
			 * Read and store records until end of file.
			 */
			while (sdkObject != null) {
				/*
				 * Create and store a new x9object for this x9.37 record.
				 */
				final X9Object x9o = sdkIO.createAndStoreX9Object();

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
			 * Create a validator instance and then verify the x9.37 file from the x9objects.
			 */
			final X9Validator x9validator = new X9Validate937(sdkBase, x9fileAttributes);
			x9validator.verifyFile();

			/*
			 * Get the error manager.
			 */
			final X9ErrorManager x9errorManager = sdkBase.getErrorManager();

			/*
			 * Log validation statistics.
			 */
			LOGGER.info("validation completed recordCount({}) errorCount({}) highestSeverity({})",
					x9validator.getX9RecordCount(), x9errorManager.getTotalRunErrors(),
					x9errorManager.getRunSeverity());

			/*
			 * Optionally list all errors to the log.
			 */
			if (LOGGER.isDebugEnabled()) {
				listErrorsToLog(x9errorManager.getErrorMap());
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * List all x9.37 errors to the system log.
	 *
	 * @param errorMap
	 *            error map
	 */
	private void listErrorsToLog(final Map<String, ArrayList<X9Error>> errorMap) {
		final StringBuilder sb = new StringBuilder();
		for (final Entry<String, ArrayList<X9Error>> entry : errorMap.entrySet()) {
			final ArrayList<X9Error> x9errors = entry.getValue();
			for (final X9Error x9error : x9errors) {
				sb.setLength(0);
				sb.append("errorKey(").append(entry.getKey());
				sb.append(") recordNumber(").append(x9error.getRecordNumber());
				sb.append(") recordType(").append(x9error.getRecordType());
				sb.append(") errorName(").append(x9error.getErrorName());
				sb.append(") severity(").append(x9error.getSeverity());
				sb.append(") errorFieldName(").append(x9error.getFieldName());
				sb.append(") errorMessage(").append(x9error.getCollectiveText());
				sb.append(") errorComments(").append(x9error.getComments());
				sb.append(")");
				LOGGER.debug(sb.toString());
			}
		}
	}

	/**
	 * Main().
	 *
	 * @param args
	 *            command line arguments
	 */
	public static void main(final String[] args) {
		int status = 0;
		X9JdkLogger.initializeLoggingEnvironment();
		LOGGER.info(X9VERIFYX9 + " started");
		try {
			final X9VerifyX9 x9verify = new X9VerifyX9();
			x9verify.process();
		} catch (final Throwable t) { // catch both errors and exceptions
			status = 1;
			LOGGER.error("main exception", t);
		} finally {
			X9SdkRoot.shutdown();
			X9JdkLogger.closeLog();
			System.exit(status);
		}
	}

}
