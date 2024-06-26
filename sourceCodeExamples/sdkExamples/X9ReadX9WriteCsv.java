package com.x9ware.examples;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
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
import com.x9ware.core.X9Reader;
import com.x9ware.logging.X9JdkLogger;

/**
 * X9ReadX9WriteCsv reads an x9.37 file and creates an output csv file with associated images. Sdk
 * methods are used which allow fields within the x9.37 input records to be examined or modified as
 * the csv output file is created.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ReadX9WriteCsv {

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
	private static final String X9READX9_WRITECSV = "X9ReadX9WriteCsv";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ReadX9WriteCsv.class);

	/*
	 * X9ReadX9WriteCsv Constructor.
	 */
	public X9ReadX9WriteCsv() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9READX9_WRITECSV);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdk = X9SdkFactory.getSdk(sdkBase);
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
	}

	/**
	 * Read an x9.37 file (record by record) and create an output csv file.
	 */
	private void process() {
		/*
		 * Define files.
		 */
		final File x9InputFile = new File(baseFolder, "Test file with 25 checks.x9");
		final File csvOutputFile = new File(baseFolder, "x9readX9WriteCsv.csv");

		/*
		 * Read the x9.37 file and create an output csv file with images. This example opens the
		 * x9.37 as an input file, but you can also use sdkIO.openInputReader() to read from an
		 * input stream. It is also is writing to an output csv file, but you can alternatively use
		 * sdkIO.openCsvWriter() to write to an output csv stream.
		 */
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputFile(x9InputFile)) {
			/*
			 * Open our output csv file.
			 */
			sdkIO.openCsvOutputFile(csvOutputFile);

			/*
			 * Set to export using absolute image file names.
			 */
			sdkIO.setExportedFileNamesRelative(false);

			/*
			 * Read until end of file.
			 */
			X9SdkObject sdkObject = null;
			while ((sdkObject = sdkIO.readNext()) != null) {
				/*
				 * Create csv from the x9.
				 */
				final String[] record = sdkIO.makeCsvFromInputRecord(sdkObject);

				/*
				 * List all fields in the record.
				 */
				final int recordNumber = sdkObject.getRecordNumber();
				final int recordType = sdkObject.getRecordType();
				for (int i = 0, n = record.length; i < n; i++) {
					final String value = record[i];
					if (StringUtils.isNotBlank(value)) {
						LOGGER.info("x9 recordNumber({}) recordType({}) field({}) value({})",
								recordNumber, recordType, i, value);
					}
				}

				/*
				 * Get the image when this is a type 52 image view data record.
				 */
				if (recordType == X9.IMAGE_VIEW_DATA) {
					final byte[] byteArray = sdkObject.getCheckImage();
					if (byteArray != null) {
						LOGGER.info("check imageSize({})", byteArray.length);
					}
				}

				/*
				 * Write the csv.
				 */
				sdkIO.writeCsv(sdkObject);
			}

			/*
			 * Log our statistics.
			 */
			LOGGER.info(sdkIO.getSdkStatisticsMessage(csvOutputFile));
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
		LOGGER.info(X9READX9_WRITECSV + " started");
		try {
			final X9ReadX9WriteCsv example = new X9ReadX9WriteCsv();
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
