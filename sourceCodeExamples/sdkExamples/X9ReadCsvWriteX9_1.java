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
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.imaging.X9ImageMode;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tools.X9CsvReader;

/**
 * X9ReadCsvWriteX9 reads a csv file with corresponding images for each item stored in an associated
 * image folder. The data and images are then used to create records which are written to an output
 * x9file. The csv can be examined or modified as the x9.37 file is created and written. Generally,
 * our suggestion is to base application programs on our x9writer technology, as implemented in the
 * X9DemoWriter sample. That approach works on a logical item basis and allows all x9.37 formatting
 * requirements to be deferred to x9writer, which allows the application program to be independent
 * of the x9.37 specification being written.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ReadCsvWriteX9 {

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
	private static final String X9READCSV_WRITEX9 = "X9ReadCsvWriteX9";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ReadCsvWriteX9.class);

	/*
	 * X9ReadCsvWriteX9 Constructor.
	 */
	public X9ReadCsvWriteX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9READCSV_WRITEX9);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdk = X9SdkFactory.getSdk(sdkBase);
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
	}

	/**
	 * Read a csv file (line by line) and create an output x9.37 file.
	 */
	private void process() {
		/*
		 * Define files.
		 */
		final File csvInputFile = new File(baseFolder, "importFile.csv");
		final File imageImportFolder = new File(baseFolder, "importImageFolder");
		final File outputFile = new File(baseFolder, "x9readCsvWriteX9.x9");

		/*
		 * Read the csv file and create an output x9.37 file. This example opens the csv as an input
		 * file, but you can also use sdkIO.openCsvReader() to assign your own input stream.
		 */
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9CsvReader csvReader = sdkIO.openCsvInputFile(csvInputFile)) {
			/*
			 * Open the output file. This example opens the x9.37 as an output file, but you can
			 * also use sdkIO.openOutputStream() to write to an output stream.
			 */
			sdkIO.openOutputFile(outputFile);
			sdkBase.setImageFolder(imageImportFolder, X9Sdk.IMAGE_IE_ENABLED);

			/*
			 * Read and process all csv input lines until end of file.
			 */
			X9SdkObject sdkObject = null;
			while ((sdkObject = sdkIO
					.getNextCsvInputRecord(X9ImageMode.IMPORT_IMAGE_FROM_EXTERNAL_FILE)) != null) {
				/*
				 * Get the csv array.
				 */
				final String[] record = sdkObject.getCsvArray();

				/*
				 * List all fields within the record.
				 */
				final int recordNumber = sdkObject.getRecordNumber();
				final int recordType = sdkObject.getRecordType();
				for (int i = 0, n = record.length; i < n; i++) {
					LOGGER.info("x9 recordNumber({}) recordType({}) field({}) content({})",
							recordNumber, recordType, i, record[i]);
				}

				/*
				 * Get the image if type 52 image view data record and log the image size.
				 */
				if (recordType == X9.IMAGE_VIEW_DATA) {
					final byte[] image = sdkObject.getCheckImage();
					LOGGER.info("isFrontImage({}) check imageSize({})", sdkObject.isFrontImage(),
							image.length);
				}

				/*
				 * Create the x9.37 record from this sdkObject.
				 */
				sdkIO.makeOutputRecordFromCsv(sdkObject);

				/*
				 * Write the x9.
				 */
				sdkIO.writeOutputFile(sdkObject);
			}
			/*
			 * Log our statistics.
			 */
			LOGGER.info(sdkIO.getSdkStatisticsMessage(outputFile));
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
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
		LOGGER.info(X9READCSV_WRITEX9 + " started");
		try {
			final X9ReadCsvWriteX9 example = new X9ReadCsvWriteX9();
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
