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
import com.x9ware.generate.X9Generate937;
import com.x9ware.generate.X9GenerateCols937;
import com.x9ware.generate.X9GenerateXml937;
import com.x9ware.imaging.X9ImageMode;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tools.X9CsvReader;

/**
 * X9GenerateX9 reads a csv file and then creates an x9.37 file by leveraging the SDK generate
 * facility. An X9GenerateXml instance is used to define the generate related parameters, which are
 * loaded from an external xml file but could also be dynamically populated.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9GenerateX9 {

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
	private static final String X9GENERATEX9 = "X9GenerateX9";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9GenerateX9.class);

	/*
	 * X9GenerateX9 Constructor.
	 */
	public X9GenerateX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9GENERATEX9);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdk = X9SdkFactory.getSdk(sdkBase);
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
	}

	/**
	 * Read a csv file (line by line) and use generate to create an output x9.37 file
	 */
	private void process() {
		/*
		 * Log csv columns as required by generate.
		 */
		LOGGER.info(
				"Generate input columns may change from release to release and are as follows:");
		for (int i = 0, n = X9GenerateCols937.NAMES.length; i < n; i++) {
			LOGGER.info("generate csv column({}) {}", (i + 1), X9GenerateCols937.NAMES[i]);
		}

		/*
		 * Define files.
		 */
		final File generateXmlFile = new File(baseFolder, "default.Generator.xml");
		final File csvInputFile = new File(baseFolder, "generateX9.csv");
		final File generateIntermediateFile = new File(baseFolder, "generateIntermediateCsv.csv");
		final File outputFile = new File(baseFolder, "x9generateX9.x9");

		/*
		 * Read the csv input file and create a csv intermediate file.
		 */
		X9Generate937 x9generate937 = null;
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Load our generator definition.
			 */
			final X9GenerateXml937 generateXml = new X9GenerateXml937();
			generateXml.loadGenerateConfiguration(generateXmlFile);

			/*
			 * Run generate to create the intermediate csv file.
			 */
			x9generate937 = new X9Generate937(sdkBase, generateXml, csvInputFile,
					generateIntermediateFile);
			final String generateErrorMessage = x9generate937.generateFile();

			/*
			 * Read the csv file and create an output x9.37 file.
			 */
			if (StringUtils.isNotBlank(generateErrorMessage)) {
				/*
				 * Log generate error.
				 */
				LOGGER.error(generateErrorMessage, new Throwable());
			} else {
				/*
				 * Log generate results.
				 */
				LOGGER.info(x9generate937.getOutputStatisticsMessage());

				/*
				 * Set repair trailers which will populate all trailer counts and amounts.
				 */
				sdkBase.setRepairTrailers(true);

				/*
				 * Open our input csv file.
				 */
				try (final X9CsvReader csvReader = sdkIO
						.openCsvInputFile(generateIntermediateFile)) {
					/*
					 * Open our output file.
					 */
					sdkIO.openOutputFile(outputFile);

					/*
					 * Read and write all csv input lines until end of file.
					 */
					X9SdkObject sdkObject = null;
					while ((sdkObject = sdkIO.getNextCsvInputRecord(
							X9ImageMode.IMPORT_IMAGE_FROM_EXTERNAL_FILE)) != null) {
						sdkIO.makeOutputRecordFromCsv(sdkObject);
						sdkIO.writeOutputFile(sdkObject);
					}
				}
			}

			/*
			 * Log our statistics.
			 */
			LOGGER.info(sdkIO.getSdkStatisticsMessage("import", outputFile));
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Log as completed.
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
		LOGGER.info(X9GENERATEX9 + " started");
		try {
			final X9GenerateX9 example = new X9GenerateX9();
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
