package com.x9ware.examples;

import java.io.File;
import java.time.LocalDate;
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
import com.x9ware.base.X9SdkRoot;
import com.x9ware.beans.X9GenerateBean937;
import com.x9ware.core.X9;
import com.x9ware.elements.X9C;
import com.x9ware.generate.X9Generate937;
import com.x9ware.generate.X9GenerateXml937;
import com.x9ware.generate.X9MakeFile937;
import com.x9ware.generate.X9MakeReader937;
import com.x9ware.generate.X9MakeXml;
import com.x9ware.imaging.X9ImageMode;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.options.X9Options;
import com.x9ware.tools.X9CsvReader;
import com.x9ware.tools.X9LocalDate;

/**
 * X9MakeX9 reads a csv file and then creates an x9.37 file by leveraging the SDK make facility. An
 * X9MakeXml instance is used to define the make related parameters, which are loaded from an
 * external xml file but could also be dynamically populated. Make is a two step process where we
 * first create an items file which is then used as input to generate which creates the x9.37 file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9MakeX9 {

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
	private static final String X9MAKEX9 = "X9MakeX9";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9MakeX9.class);

	/*
	 * X9MakeX9 Constructor.
	 */
	public X9MakeX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9MAKEX9);
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
		 * Define files.
		 */
		final File makeXmlFile = new File(baseFolder, X9C.DEFAULT_MAKE_937_REFORMATTER);
		final File generateXmlFile = new File(baseFolder, X9C.DEFAULT_937_GENERATOR);
		final File csvInputFile = new File(baseFolder, "useCase1000.csv");
		final File routingListFile = new File(baseFolder, "routingList.csv");
		final File makeIntermediateFile = new File(baseFolder, "makeIntermediateCsv.csv");
		final File generateIntermediateFile = new File(baseFolder, "generateIntermediateCsv.csv");
		final File outputFile = new File(baseFolder, "x9makeX9.x9");

		/*
		 * Load our reformatter definition.
		 */
		final X9MakeXml makeXml = new X9MakeXml();
		makeXml.loadReformatterConfiguration(makeXmlFile);

		/*
		 * Load our generator definition.
		 */
		final X9GenerateXml937 generateXml = new X9GenerateXml937();
		generateXml.loadGenerateConfiguration(generateXmlFile);

		/*
		 * Allocate our make reader.
		 */
		final X9MakeReader937 x9makeReader = new X9MakeReader937(sdkBase);

		/*
		 * Read the csv input file and create a csv intermediate file.
		 */
		X9Generate937 x9generate937 = null;
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9CsvReader csvReader = new X9CsvReader(csvInputFile)) {
			/*
			 * Load the items.
			 */
			final String message = x9makeReader.scanUseCaseFile(csvReader, csvInputFile.toString(),
					makeXml);
			LOGGER.info("x9makeReader message({})", message);

			/*
			 * Run make to create the items. This sample will create a file with 100 items but that
			 * can be easily changed to create all use cases.
			 */
			final X9MakeFile937 x9makeFile = new X9MakeFile937(sdkBase, x9makeReader, makeXml);
			final int itemCount = x9makeReader.getUseCaseRowCount();
			final List<String> fieldsWhichExceedMaxSize = x9makeFile.makeFile(makeIntermediateFile,
					itemCount);

			/*
			 * List any fields which exceeded the maximum allowed by generate.
			 */
			for (final String text : fieldsWhichExceedMaxSize) {
				LOGGER.error(text);
			}

			/*
			 * The draft-check parameters can be provided via the make reformatter. In this example,
			 * the csv is very basic (containing only the MICR line fields) and we instead
			 * explicitly set these parameters for check image drawing.
			 */
			X9Options.draftCheckNameAddress[0] = "James C. Morrison"; // front image fields
			X9Options.draftCheckNameAddress[1] = "12345 AnyWhere Circle";
			X9Options.draftCheckNameAddress[2] = "Your City, State 12345";
			X9Options.draftCheckPayeeName = "Cash";
			X9Options.draftCheckBankName = "Non-Negotiable";
			X9Options.draftCheckMemoLine = "Void";
			X9Options.draftCheckSignatureLine = "Jim Morrison";
			X9Options.draftCheckEndorsmentHeader = "Endorse Here"; // back image fields
			X9Options.draftCheckSignBelowLine = "Do not write, stamp, or sign below these lines.";

			/*
			 * Set generate attributes, and most importantly various date fields.
			 */

			final LocalDate today = X9LocalDate.getCurrentDate();
			final X9GenerateBean937.GenerateAttr937 generateAttr = generateXml.getAttr();
			generateAttr.fileConfig = X9GenerateXml937.DEFAULT_X9_CONFIGURATION;
			generateAttr.endorsementRoutingList = routingListFile.toString();
			generateAttr.businessDate = today;
			generateAttr.createDate = today;
			generateAttr.createTime = 801;
			generateAttr.itemStartDate = today;
			generateAttr.itemEndDate = today;
			generateAttr.primaryAddendumDate = today;
			generateAttr.secondaryAddendumDate = today;

			/*
			 * Run generate which returns a possible error message.
			 */
			x9generate937 = new X9Generate937(sdkBase, generateXml, makeIntermediateFile,
					generateIntermediateFile);
			final String generateErrorMessage = x9generate937.generateFile();

			/*
			 * Read the generate csv file and create an output x9.37 file.
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
				 * Finally write the output x9.37 file.
				 */
				writeOutputFile(sdkIO, generateIntermediateFile, outputFile);
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
	 * Write the output file.
	 *
	 * @param sdkIO
	 *            current sdkIO instance
	 * @param generateIntermediateFile
	 *            csv file that was created by generate
	 * @param outputFile
	 *            output x9.37 file to be written
	 */
	private void writeOutputFile(final X9SdkIO sdkIO, final File generateIntermediateFile,
			final File outputFile) {
		/*
		 * Open the csv file that was created by generate.
		 */
		try (final X9CsvReader csvReader = sdkIO.openCsvInputFile(generateIntermediateFile)) {
			/*
			 * Open our output file.
			 */
			sdkIO.openOutputFile(outputFile);

			/*
			 * Read and write all csv input lines until end of file.
			 */
			X9SdkObject sdkObject = null;
			while ((sdkObject = sdkIO
					.getNextCsvInputRecord(X9ImageMode.IMPORT_IMAGE_FROM_EXTERNAL_FILE)) != null) {
				sdkIO.makeOutputRecordFromCsv(sdkObject);
				sdkIO.writeOutputFile(sdkObject);
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		} finally {
			sdkIO.closeOutputFile();
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
		LOGGER.info(X9MAKEX9 + " started");
		try {
			final X9MakeX9 example = new X9MakeX9();
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
