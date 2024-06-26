package com.x9ware.examples;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkObjectFactory;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.core.X9Reader;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9TempFile;
import com.x9ware.types.X9Type01;
import com.x9ware.types.X9Type20;

/**
 * X9ModifyX9 reads an x9.37 file and copies it to an output x9.37 file while modifying certain
 * record types and fields. This sample can serve as a basis to meet any x9.37 file modification
 * requirement.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ModifyX9 {

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
	private static final String X9MODIFYX9 = "X9ModifyX9";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ScrubX9.class);

	/*
	 * X9ModifyX9 Constructor.
	 */
	public X9ModifyX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9MODIFYX9);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdk = X9SdkFactory.getSdk(sdkBase);
		if (!sdkBase.bindConfiguration(X9.X9_100_187_UCD_2018_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
	}

	/**
	 * Read an x9.37 file (record by record), modify selective records, and write a new x9.37 file.
	 */
	private void process() {
		/*
		 * Define files.
		 */
		final File inputFile = new File(baseFolder, "Test file with 25 checks.x9");
		final File outputFile = new File(baseFolder, "x9modifyX9.x9");

		/*
		 * Ensure the input file exists.
		 */
		if (!X9FileUtils.existsWithPathTracing(inputFile)) {
			throw X9Exception.abort("file not found({})", inputFile);
		}

		/*
		 * Create a new x9.37 file from the internal list when successfully loaded. This example
		 * opens the x9.37 as an input file, but you can also use sdkIO.openInputReader() to read
		 * from an input stream.
		 */
		X9TempFile x9tempFile = null;
		File outputTempFile = null;
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputFile(inputFile)) {
			/*
			 * Create the output file as temp which will be renamed on completion.
			 */
			x9tempFile = new X9TempFile(outputFile);
			outputTempFile = x9tempFile.getTemp();

			/*
			 * Ensure the input file exists.
			 */
			if (!X9FileUtils.existsWithPathTracing(inputFile)) {
				throw X9Exception.abort("file not found({})", inputFile);
			}

			/*
			 * Open the output file. This example opens the x9.37 as an output file, but you can
			 * also use sdkIO.openOutputStream() to write to an output stream.
			 */
			sdkIO.openOutputFile(outputTempFile);

			/*
			 * Get the first x9.37 record.
			 */
			X9SdkObject sdkObject = sdkIO.readNext();

			/*
			 * Set output file attributes based on the first record.
			 */
			final X9SdkObjectFactory x9sdkObjectFactory = sdkBase.getSdkObjectFactory();
			x9sdkObjectFactory.setIsOutputEbcdic(x9reader.isEbcdicEncoding());
			x9sdkObjectFactory.setFieldZeroInserted(x9reader.isFieldZeroPrefixes());

			/*
			 * Copy until end of file.
			 */
			while (sdkObject != null) {
				/*
				 * Display the UCD indicator from the file header record. Note that this access
				 * requires that a UCD configuration be referenced by the bind.
				 */
				final int recordType = sdkObject.getRecordType();
				final byte[] dataRecord = sdkObject.getDataByteArray();
				if (recordType == X9.FILE_HEADER) {
					final X9Type01 t01 = new X9Type01(sdkBase, dataRecord);
					LOGGER.info("ucd indicator({})", t01.ucdIndicator);
				}

				/*
				 * As an example, modify the return routing number in the type 20 bundle header.
				 */
				if (recordType == X9.BUNDLE_HEADER) {
					final X9Type20 t20 = new X9Type20(sdkBase, dataRecord);
					t20.returnLocationRoutingNumber = "123456789";
					t20.modify();
				}

				/*
				 * Rebuild and write the output record.
				 */
				sdkObject.buildOutputFromData(dataRecord);
				sdkIO.writeOutputFile(sdkObject);

				/*
				 * Get the next record.
				 */
				sdkObject = sdkIO.readNext();
			}
			/*
			 * Log our statistics.
			 */
			LOGGER.info(sdkIO.getSdkStatisticsMessage(outputFile));
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		} finally {
			/*
			 * Rename on completion.
			 */
			if (x9tempFile != null && outputTempFile != null) {
				x9tempFile.renameTemp();
			}
		}

		/*
		 * Log our completion.
		 */
		LOGGER.info("finished");
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
		LOGGER.info(X9MODIFYX9 + " started");
		try {
			final X9ModifyX9 example = new X9ModifyX9();
			example.process();
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
