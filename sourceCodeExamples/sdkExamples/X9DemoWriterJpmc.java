package com.x9ware.examples;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Item937;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.beans.X9HeaderAttr937;
import com.x9ware.core.X9;
import com.x9ware.core.X9HeaderXml937;
import com.x9ware.core.X9Writer;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tools.X9CsvLine;
import com.x9ware.tools.X9CsvReader;
import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9FileIO;
import com.x9ware.tools.X9FileUtils;

/**
 * X9DemoWriterJpmc creates a output x9.37 file from individual items using x9writer. Input items
 * would typically be extracted from an input source such as a database or xml file. X9Writer uses
 * an externally defined headerXml file to assign all attributes of the constructed x9.37 file. JPMC
 * is a bit unique, since each deposit must be isolated in its own cash letter header.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9DemoWriterJpmc {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_Utilities");

	/*
	 * Constants.
	 */
	private static final String X9DemoWriterJpmc = "X9DemoWriterJpmc";
	private static final char COMMA = ',';
	private static final int ITEM_LIST_INITIAL_CAPACITY = 1000;

	/*
	 * Item csv array fields (these indexes are zero relative; the first column contains "t25").
	 */
	private static final int ITEM_AMOUNT = 1;
	private static final int ITEM_SEQUENCE_NUMBER = 2;
	private static final int ITEM_ROUTING = 3;
	private static final int ITEM_ONUS = 4;
	private static final int ITEM_AUX_ONUS = 5;
	private static final int ITEM_EPC = 6;
	private static final int ITEM_IMAGE_ROUTING = 7;
	private static final int ITEM_IMAGE_DATE = 8;
	private static final int ITEM_IMAGE_FRONT_NAME = 9;
	private static final int ITEM_IMAGE_BACK_NAME = 10;
	private static final int ITEM_FIELD_COUNT = 11;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9DemoWriterJpmc.class);

	/*
	 * X9DemoWriterJpmc Constructor.
	 */
	public X9DemoWriterJpmc() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment. Note that we do not bind to an configuration here since that
		 * will be done by X9Writer subject to the configuration identified in X9HeaderXml.
		 */
		X9SdkRoot.logStartupEnvironment(X9DemoWriterJpmc);
		X9SdkRoot.loadXmlConfigurationFiles();
	}

	/**
	 * Read a csv input file and create an x9.37 output file.
	 */
	private void process() {
		try {
			/*
			 * Load headerXml which guides construction of the output x9.37 file.
			 */
			final File headersFile = new File(baseFolder, "x9headers2.xml");
			final X9HeaderXml937 x9headerXml937 = new X9HeaderXml937();
			x9headerXml937.readHeaderDefinition(headersFile);

			/*
			 * Load a csv file, where those items will be used for this demonstration.
			 */
			final File inputFile = new File(baseFolder,
					"Test file with 5,000 checks translate.csv");
			if (!X9FileUtils.existsWithPathTracing(inputFile)) {
				throw X9Exception.abort("csv input file notFound({})", inputFile);
			}
			final List<X9Item937> itemList = loadCsv(inputFile, x9headerXml937);

			/*
			 * Create the output file from the loaded items.
			 */
			createX937File(itemList, x9headerXml937);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		LOGGER.info("finished");
	}

	/**
	 * Create the x9.37 output file from logical items using the headerXml definition.
	 *
	 * @param itemList
	 *            list of items to be written
	 * @param x9headerXml937
	 *            x9headers xml file
	 */
	private void createX937File(final List<X9Item937> itemList,
			final X9HeaderXml937 x9headerXml937) {
		/*
		 * Set sdk options.
		 */
		sdkBase.setImageFolder(null, X9Sdk.IMAGE_IE_DISABLED);
		sdkBase.setRepairTrailers(true);

		/*
		 * Allocate our writer and then write all items from the provided list.
		 */
		try (final X9Writer x9writer = new X9Writer(sdkBase)) {
			/*
			 * Open the output file. This example opens the x9.37 as an output file, but you can
			 * also use similarly use bindAndOpenToStream() to write to an output stream.
			 */
			final File outputFile = new File(baseFolder, "x9demoWriterJpmc.x9");
			x9writer.bindAndOpenToFile(outputFile, x9headerXml937);

			/*
			 * Write the file header.
			 */
			x9writer.writeFileHeader(X9.FILE_HEADER);

			/*
			 * As a demonstration, write four deposits, where each is in their own cash letter.
			 */
			for (int i = 0; i < 4; i++) {
				/*
				 * Set the JPMC-ULID and write the cash letter header. Note that we are assigning a
				 * dummy ULID here, it would need to be modified for your deposits.
				 */
				x9headerXml937.getAttr().cashLetterContactPhone = "111" + i;
				x9writer.writeCashLetterHeader(X9.CASH_LETTER_HEADER);

				/*
				 * Bundle and write all items.
				 */
				int itemNumber = 0;
				for (final X9Item937 x9item937 : itemList) {
					/*
					 * Write the item group (item, endorsements, and attached images).
					 */
					itemNumber++;
					x9writer.addItem(x9item937, x9item937.getFrontImage(),
							x9item937.getBackImage());

					/*
					 * Log during development.
					 */
					LOGGER.info(
							"itemNumber({}) recordType({}) routing({}) onUs({}) "
									+ "epc({}) amount({}) itemSequenceNumber({})",
							itemNumber, x9item937.getRecordType(), x9item937.getRouting(),
							x9item937.getOnus(), x9item937.getEpc(), x9item937.getAmount(),
							x9item937.getItemSequenceNumber());
				}

				/*
				 * Write the bundle and cash letter trailers.
				 */
				x9writer.writeBundleAndCashLetterTrailers();
			}

			/*
			 * Log our statistics.
			 */
			LOGGER.info(x9writer.getWriterStatisticsMessage(outputFile));
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * Load the csv file into an array list.
	 *
	 * @param inputFile
	 *            csv input file
	 * @param x9headerXml937
	 *            x9headers xml file
	 * @return csv line map
	 */
	private List<X9Item937> loadCsv(final File inputFile, final X9HeaderXml937 x9headerXml937) {
		/*
		 * Read the input file file into an array list.
		 */
		final X9HeaderAttr937 headerAttr = x9headerXml937.getAttr();
		final List<X9Item937> itemList = new ArrayList<>(ITEM_LIST_INITIAL_CAPACITY);
		try (final X9CsvReader csvReader = new X9CsvReader(inputFile)) {
			/*
			 * Load all csv rows to the array list.
			 */
			boolean isEndEncountered = false;
			X9CsvLine csvLine = csvReader.getNextCsvLine();
			while (csvLine != null && csvLine.isPopulated() && !isEndEncountered) {
				/*
				 * Check if end encountered.
				 */
				final int lineNumber = csvLine.getLineNumber();
				final String[] record = csvLine.getCsvArray();
				final String csvLineType = record[0].trim();
				isEndEncountered = StringUtils.equals(csvLineType, "end");
				if (isEndEncountered) {
					/*
					 * Indicate end was encountered on the csv input file.
					 */
					LOGGER.info("end encountered at csv lineNumber({})", lineNumber);
				} else if (StringUtils.equals(csvLineType, "t25")) {
					/*
					 * Ensure the csv line contains the correct number of fields.
					 */
					final int csvLength = record.length;
					if (csvLength != ITEM_FIELD_COUNT) {
						throw X9Exception.abort(
								"incorrect csvLength({}) lineNumber({}) expected({}) csvArray({})",
								csvLength, lineNumber, ITEM_FIELD_COUNT,
								StringUtils.join(record, COMMA));
					}

					/*
					 * Assign item fields from the incoming csv line.
					 */
					final String csvAmount = record[ITEM_AMOUNT];
					final String csvSequenceNumber = record[ITEM_SEQUENCE_NUMBER];
					final String csvRouting = record[ITEM_ROUTING];
					final String csvOnUs = record[ITEM_ONUS];
					final String csvAuxOnUs = record[ITEM_AUX_ONUS];
					final String csvEpc = record[ITEM_EPC];
					final String imageCreatorRouting = StringUtils
							.isNotBlank(record[ITEM_IMAGE_ROUTING]) ? record[ITEM_IMAGE_ROUTING]
									: headerAttr.itemImageCreatorRouting;
					final String imageCreatorDate = StringUtils.isNotBlank(record[ITEM_IMAGE_DATE])
							? record[ITEM_IMAGE_DATE]
							: headerAttr.itemImageCreatorDate;
					final File frontImageFile = new File(record[ITEM_IMAGE_FRONT_NAME].trim());
					final File backImageFile = new File(record[ITEM_IMAGE_BACK_NAME].trim());

					/*
					 * Create an item instance for the type 25 record.
					 */
					final X9Item937 x9item937 = new X9Item937(lineNumber, X9.CHECK_DETAIL,
							csvRouting, csvOnUs, csvAuxOnUs, csvEpc,
							X9Decimal.getAsAmount(csvAmount), csvSequenceNumber);
					x9item937.setImageCreatorRouting(imageCreatorRouting);
					x9item937.setImageCreatorDate(imageCreatorDate);
					x9item937.setFrontImage(X9FileIO.readFile(frontImageFile));
					x9item937.setBackImage(X9FileIO.readFile(backImageFile));

					itemList.add(x9item937);
				} else {
					throw X9Exception.abort("invalid csvLineType({}) lineNumber({}) record({})",
							csvLineType, lineNumber, StringUtils.join(record, COMMA));
				}

				/*
				 * Get the next csv input line.
				 */
				csvLine = csvReader.getNextCsvLine();
			}

			/*
			 * Ensure that "end" was present and otherwise abort.
			 */
			if (!isEndEncountered) {
				throw X9Exception.abort("\"end\" not last csv input line");
			}
		} catch (final Exception ex) {
			/*
			 * Abort on exception.
			 */
			throw X9Exception.abort(ex);
		}

		/*
		 * Return the constructed list of all items.
		 */
		return itemList;
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
		LOGGER.info(X9DemoWriterJpmc + " started");
		try {
			final X9DemoWriterJpmc x9demoWriterJpmc = new X9DemoWriterJpmc();
			x9demoWriterJpmc.process();
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
