package com.x9ware.utilities;

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
import com.x9ware.beans.X9GenerateBean937;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.create.X9DateCalc;
import com.x9ware.elements.X9C;
import com.x9ware.elements.X9UserHome;
import com.x9ware.generate.X9Generate937;
import com.x9ware.generate.X9GenerateXml937;
import com.x9ware.generate.X9MakeFile937;
import com.x9ware.generate.X9MakeReader;
import com.x9ware.generate.X9MakeReader937;
import com.x9ware.generate.X9MakeXml;
import com.x9ware.imaging.X9ImageMode;
import com.x9ware.options.X9Options;
import com.x9ware.options.X9WorkFolder;
import com.x9ware.poi.X9PoiToCsv;
import com.x9ware.tools.X9CsvReader;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;

/**
 * X9UtilMake is part of our utilities package which runs make/generate on a batch basis. Input is
 * the CSV use case file, while output will be the x9.37 output file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilMake {

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
	private File inputFile;
	private File reformatterXmlFile;
	private File generatorXmlFile;
	private File routingListFile;
	private File outputFile;
	private X9TempFile outputTempFile;

	/*
	 * Constants.
	 */
	private static final String MAKE_INTERMEDIATE_FILE = "makeIntermediateFile.csv";
	private static final String GENERATE_INTERMEDIATE_FILE = "generateIntermediateFile.csv";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilMake.class);

	/*
	 * X9UtilMake Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilMake(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
	}

	/**
	 * Run make/generate.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Get work unit files.
		 */
		inputFile = workUnit.inputFile;
		outputFile = workUnit.outputFile;

		/*
		 * Get the reformatter file name and ensure it exists.
		 */
		final String reformatterFileName = workUnit
				.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_MAKE_REFORMATTER);
		if (StringUtils.isBlank(reformatterFileName)) {
			throw X9Exception.abort("reformatter name required");
		}
		reformatterXmlFile = new File(reformatterFileName);
		if (!X9FileUtils.existsWithPathTracing(reformatterXmlFile)) {
			throw X9Exception.abort("reformatterXmlFile not found({})", reformatterXmlFile);
		}

		/*
		 * Get the generator file name and ensure it exists.
		 */
		final String generatorFileName = workUnit
				.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_MAKE_GENERATOR);
		if (StringUtils.isBlank(generatorFileName)) {
			throw X9Exception.abort("generator name required");
		}
		generatorXmlFile = new File(generatorFileName);
		if (!X9FileUtils.existsWithPathTracing(generatorXmlFile)) {
			throw X9Exception.abort("generatorXmlFile not found({})", generatorXmlFile);
		}

		/*
		 * Log the reformatter and generator names.
		 */
		LOGGER.info("make reformatter({})", reformatterXmlFile);
		LOGGER.info("make generator({})", generatorXmlFile);

		/**
		 * Load the reformatter and generator from their xml definitions.
		 */
		final X9MakeXml makeXml = new X9MakeXml();
		final X9GenerateXml937 generateXml = new X9GenerateXml937();
		try {
			makeXml.loadReformatterConfiguration(reformatterXmlFile);
			generateXml.loadGenerateConfiguration(generatorXmlFile);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Get the routing list file, which can either be provided via the command line or populated
		 * within generator xml, where it can be either absolute or relative.
		 */
		final X9GenerateBean937.GenerateAttr937 generateAttr = generateXml.getAttr();
		if (StringUtils.isBlank(generateAttr.endorsementRoutingList)
				|| StringUtils.equalsIgnoreCase(generateAttr.endorsementRoutingList, X9C.NONE)) {
			routingListFile = workUnit.secondaryFile;
			if (routingListFile == null) {
				throw X9Exception.abort(
						"routing list not provided by either generator xml or the command line");
			}
			LOGGER.info("routingList set from commandLine({})", routingListFile);
		} else {
			routingListFile = X9FileUtils.isFileNameAbsolute(generateAttr.endorsementRoutingList)
					? new File(generateAttr.endorsementRoutingList)
					: new File(X9UserHome.getRoutingListsFolder(),
							generateAttr.endorsementRoutingList);
			LOGGER.info("routingList set from generateXml({})", routingListFile);
		}

		/*
		 * Set the routing list file and ensure it exists.
		 */
		generateAttr.endorsementRoutingList = routingListFile.toString();
		if (!X9FileUtils.existsWithPathTracing(routingListFile)) {
			throw X9Exception.abort("routingListFile not found({})", routingListFile);
		}

		/*
		 * Assign configuration from generator xml and otherwise default to the command line. We
		 * assign from the generator first, since that is the behavior provided by X9Assist.
		 */
		if (StringUtils.isBlank(generateAttr.fileConfig)) {
			final String configName = workUnit.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_CONFIG);
			generateAttr.fileConfig = configName;
			LOGGER.info("configuration set from commandLine({})", configName);
		} else {
			LOGGER.info("configuration set from generateXml({})", generateAttr.fileConfig);
		}

		/*
		 * Run the make/generate/import process.
		 */
		sdk = X9SdkFactory.getSdk(sdkBase);
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		outputTempFile = X9UtilWorkUnit.getTempFileInstance(outputFile);
		final int exitStatus = X9UtilBatch.EXIT_STATUS_ABORTED;
		X9TrailerManager x9trailerManager = null;
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Assign defaults and then run make.
			 */
			assignMakeDefaults(generateAttr);
			x9trailerManager = runMake(sdkIO, makeXml, generateXml);
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Rename the generate output file on completion.
				 */
				outputTempFile.renameTemp();
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
				x9totalsXml.setTotals(outputFile, x9trailerManager);

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);
				LOGGER.info("make{}", x9totalsXml.getTotalsString());
			}
		}

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

	/**
	 * Assign make default values.
	 */
	private void assignMakeDefaults(final X9GenerateBean937.GenerateAttr937 generateAttr) {
		/*
		 * Assign dates when they are to be refreshed based on the current date.
		 */
		if (generateAttr.dateRefresh) {
			final LocalDate today = X9LocalDate.getCurrentDate();
			generateAttr.businessDate = X9DateCalc.getDefaultBusinessDate(today);
			generateAttr.createDate = X9DateCalc.getDefaultFileCreationDate(today);
			generateAttr.itemStartDate = X9DateCalc.getDefaultItemStartDate(today);
			generateAttr.itemEndDate = X9DateCalc.getDefaultItemEndDate(today);
			generateAttr.primaryAddendumDate = X9DateCalc.getDefaultPrimaryAddendumDate(today);
			generateAttr.secondaryAddendumDate = X9DateCalc.getDefaultSecondaryAddendumDate(today);
		}

		/**
		 * Assign default values to the draft check image that is used by make. These fields can be
		 * populated via the the use file. However, when a very basic use case file is provided (eg,
		 * MICR fields only), then these values will not exist and the check images drawn by make
		 * will be sparse. We assign these as a convenience for that specific situation, but would
		 * also expect actual usage to populate these via reformatter assignments.
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
	}

	/**
	 * Run make processing with an exception thrown on any errors.
	 * 
	 * @param sdkIO
	 *            current sdkIO
	 * @param makeXml
	 *            reformatter xml
	 * @param generateXml
	 *            generator xml
	 * @return trailer manager totals for our output file
	 */
	private X9TrailerManager runMake(final X9SdkIO sdkIO, final X9MakeXml makeXml,
			final X9GenerateXml937 generateXml) throws Exception {
		/*
		 * Translate from excel to csv when needed.
		 */
		final File csvFile;
		final X9PoiToCsv poiToCsv = new X9PoiToCsv(sdkBase, X9PoiToCsv.WORKSHEET_PROMPT_DISABLED);
		if (X9PoiToCsv.isNativeExcelFileExtension(inputFile)) {
			try {
				csvFile = poiToCsv.translateExcelToCsvWhenNeeded(inputFile);
			} catch (final Exception ex) {
				throw X9Exception.abort(ex);
			}
		} else {
			csvFile = inputFile;
		}

		/*
		 * Allocate a csv reader which will be used to load and scan the use case file. Note that we
		 * do not accept Excel as input for several reasons. Most basic is that we do not have POI
		 * as part of our SDK footprint. Second is that it would require additional decisions as to
		 * which sheet should be processed when there are multiple sheets.
		 */
		X9TrailerManager x9trailerManager = null;
		final X9MakeReader x9makeReader = new X9MakeReader937(sdkBase);
		try (final X9CsvReader csvReader = new X9CsvReader(csvFile)) {
			/*
			 * Make is running as batch and not interactive; our assumption is that invalid data
			 * should not be accepted.
			 */
			x9makeReader.setAcceptInvalidData(false);

			/*
			 * Scan the use case file for possible errors and log those results.
			 */
			final String readerMessage = x9makeReader.scanUseCaseFile(csvReader, csvFile.toString(),
					makeXml);
			final int itemCount = x9makeReader.getUseCaseRowCount();
			LOGGER.info(
					"useCase itemCount({}) columns({}) readerMessage({}) seriousErrors({}) "
							+ "reformatterXmlFile({}) ",
					itemCount, x9makeReader.getUseCaseColumnCount(), readerMessage,
					x9makeReader.isRedStatus(), reformatterXmlFile);

			/*
			 * Run make to create the items.
			 */
			final File makeIntermediateFile = new File(X9WorkFolder.getTempFolder(),
					MAKE_INTERMEDIATE_FILE);
			final X9MakeFile937 x9makeFile = new X9MakeFile937(sdkBase, x9makeReader, makeXml);
			final List<String> fieldsWhichExceedMaxSize = x9makeFile.makeFile(makeIntermediateFile,
					itemCount);

			/*
			 * Log fields which exceed their maximum size.
			 */
			if (fieldsWhichExceedMaxSize != null && fieldsWhichExceedMaxSize.size() > 0) {
				for (final String fieldExceedsMaxSize : fieldsWhichExceedMaxSize) {
					LOGGER.warn("fieldExceedsMaxSize({})", fieldExceedsMaxSize);
				}
			}

			/*
			 * Run generate to create the intermediate csv file.
			 */
			final File generateIntermediateFile = new File(X9WorkFolder.getTempFolder(),
					GENERATE_INTERMEDIATE_FILE);
			final X9Generate937 x9generate937 = new X9Generate937(sdkBase, generateXml,
					makeIntermediateFile, generateIntermediateFile);
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
				x9trailerManager = writeOutputFile(sdkIO, generateIntermediateFile);
			}

			/*
			 * Log our statistics.
			 */
			LOGGER.info(sdkIO.getSdkStatisticsMessage("import", outputFile));
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Return our trailer manager totals or null when the output file was not created.
		 */
		return x9trailerManager;
	}

	/**
	 * Write the output file.
	 *
	 * @param sdkIO
	 *            current sdkIO instance
	 * @param generateIntermediateFile
	 *            csv file that was created by generate
	 * @return trailer manager totals for our output file
	 */
	private X9TrailerManager writeOutputFile(final X9SdkIO sdkIO,
			final File generateIntermediateFile) {
		/*
		 * Open the csv file that was created by generate.
		 */
		try (final X9CsvReader csvReader = sdkIO.openCsvInputFile(generateIntermediateFile)) {
			/*
			 * Open our output file.
			 */
			sdkIO.openOutputFile(outputTempFile.getTemp());

			/*
			 * Read and write all csv input lines until end of file.
			 */
			X9SdkObject sdkObject = null;
			while ((sdkObject = sdkIO
					.getNextCsvInputRecord(X9ImageMode.IMPORT_IMAGE_FROM_EXTERNAL_FILE)) != null) {
				sdkIO.makeOutputRecordFromCsv(sdkObject);
				sdkIO.writeOutputFile(sdkObject);
			}

			/*
			 * Return our trailer manager totals.
			 */
			return sdkIO.getTrailerManager();
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		} finally {
			sdkIO.closeOutputFile();
		}
	}

}
