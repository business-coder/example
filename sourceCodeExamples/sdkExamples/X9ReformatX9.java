package com.x9ware.examples;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Endorsement;
import com.x9ware.base.X9Item937;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.beans.X9HeaderAttr937;
import com.x9ware.core.X9;
import com.x9ware.core.X9HeaderXml937;
import com.x9ware.core.X9Writer;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.records.X9Credit;
import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.utilities.X9UtilLoadItems;

/**
 * X9ReformatX9 reads an input x9.37 file, extracts the individual items, and then invokes x9writer
 * to create an output x9.37 file. X9Writer utilizes a headerXml file to define all attributes of
 * the constructed x9.37 file. Item and image data is extracted from the input x9.37 file and will
 * carry over into the output file. Addenda records (type 26 and type 28s) are also copied over from
 * the input x9.37 file to the output file, or can be easily changed to be assigned statically from
 * the headerXml definition. This approach is very flexible, since it allows the x9.37 specification
 * of the output file to be different from the input file. It also allows the output file to contain
 * an automatically created credit which is calculated from the debits, even when the input file is
 * debits only. The format and positioning of the inserted credit is defined in headerXml.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ReformatX9 {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");

	/*
	 * Constants.
	 */
	private static final String X9REFORMATX9 = "X9ReformatX9";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ReformatX9.class);

	/*
	 * X9ReformatX9 Constructor.
	 */
	public X9ReformatX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment.
		 */
		X9SdkRoot.logStartupEnvironment(X9REFORMATX9);
		X9SdkRoot.loadXmlConfigurationFiles();
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}

		/*
		 * Set sdk options.
		 */
		sdkBase.setImageFolder(null, X9Sdk.IMAGE_IE_DISABLED);
		sdkBase.setRepairTrailers(true);
	}

	/**
	 * Read a csv input file and create an x9.37 output file.
	 */
	private void process() {
		try {
			/*
			 * Load headerXml which guides construction of the output x9.37 file.
			 */
			final File headersFile = new File(baseFolder, "x9headers3.xml");
			final X9HeaderXml937 x9headerXml937 = new X9HeaderXml937();
			x9headerXml937.readHeaderDefinition(headersFile);

			/*
			 * Load the input x9.37 file to an item list.
			 */
			final File inputFile = new File(baseFolder, "Test file with 25 checks.x9");
			if (!X9FileUtils.existsWithPathTracing(inputFile)) {
				throw X9Exception.abort("x9.37 input file notFound({})", inputFile);
			}
			final List<X9Item937> itemList = X9UtilLoadItems.read937(sdkBase, inputFile);

			/*
			 * Write the items to the output x9.37 file.
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
		 * Allocate our writer and then write all items from the provided list.
		 */
		boolean isHeaderWritten = false;
		try (final X9Writer x9writer = new X9Writer(sdkBase)) {
			/*
			 * Open the output file. This example opens the x9.37 as an output file, but you can
			 * also use similarly use bindAndOpenToStream() to write to an output stream.
			 */
			final File outputFile = new File(baseFolder, "x9reformatX9.x9");
			x9writer.bindAndOpenToFile(outputFile, x9headerXml937);

			/*
			 * Automatically insert a credit when directed by the headerXml definition.
			 */
			final X9HeaderAttr937 headerAttr = x9headerXml937.getAttr();
			if (headerAttr.creditInsertedAutomatically) {
				/*
				 * Accumulate debit amounts which will be used to optionally insert the offsetting
				 * credit (when that is needed by the financial institution).
				 */
				int itemCount = 0;
				BigDecimal totalAmount = BigDecimal.ZERO;
				for (final X9Item937 x9item937 : itemList) {
					itemCount++;
					totalAmount = totalAmount.add(x9item937.getAmount());
				}

				/*
				 * Build the credit which will be automatically inserted into the cash letter at the
				 * designated insertion point per the headerXml definition.
				 */
				if (itemCount > 0) {
					final X9Credit x9credit = x9writer.createCredit();
					x9credit.itemCount = itemCount;
					x9credit.amount = X9Decimal.getStringValue(totalAmount);
					x9credit.payorBankRouting = x9writer
							.getDirectedValue(headerAttr.creditPayorBankRouting);
					x9credit.micrOnUs = x9writer.getDirectedValue(headerAttr.creditMicrOnUs);
					x9credit.auxiliaryOnUs = x9writer.assignCreditSerialNumber(
							x9writer.getDirectedValue(headerAttr.creditMicrAuxOnUs));
					x9credit.itemSequenceNumber = x9writer.assignCreditItemSequenceNumber(
							x9writer.getDirectedValue(headerAttr.creditItemSequenceNumber));
					LOGGER.info(
							"created amount({}) routing({}) OnUs({}) AuxOnUs({}) isn({}) "
									+ "creditFormat({}) creditLocation({})",
							x9credit.amount, x9credit.payorBankRouting, x9credit.micrOnUs,
							x9credit.auxiliaryOnUs, x9credit.itemSequenceNumber,
							headerAttr.creditFormat, headerAttr.creditRecordLocation);
				}
			}

			/*
			 * Write the file and cash letter headers when not yet written.
			 */
			if (!isHeaderWritten) {
				isHeaderWritten = true;
				x9writer.writeHeaders();
			}

			/*
			 * Bundle and write all items using the type 26/28 endorsements from the input file. The
			 * assumption here is that headerXml does not define any primary (type 26) or secondary
			 * (type 28) endorsement information. This can be easily modified to not attach the
			 * endorsements from the input x9.37 file, but to instead define them in headerXml. The
			 * approach is dependent on the required solution. If the item level endorsements are to
			 * be retained, then this code can remain as is. If instead the requirement is to assign
			 * statically defined endorsements, then they should not be added here and should
			 * instead be defined in headerXml.
			 */
			int itemNumber = 0;
			for (final X9Item937 x9item937 : itemList) {
				/*
				 * Add the type 26/28 endorsements from the input x9.37 file.
				 */
				final List<X9Endorsement> endorsementList = x9item937.getEndorsementList();
				x9writer.attachExternalEndorsementsToItem(x9item937, endorsementList);

				/*
				 * Write the item group (item, endorsements, and attached images).
				 */
				itemNumber++;
				x9writer.addItem(x9item937, x9item937.getFrontImage(), x9item937.getBackImage());

				/*
				 * Log when debugging.
				 */
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("itemNumber({}) recordType({}) routing({}) onUs({}) "
							+ "epc({}) amount({}) itemSequenceNumber({}) endorsementCount({})",
							itemNumber, x9item937.getRecordType(), x9item937.getRouting(),
							x9item937.getOnus(), x9item937.getEpc(), x9item937.getAmount(),
							x9item937.getItemSequenceNumber(), endorsementList.size());
				}
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
	 * Main().
	 *
	 * @param args
	 *            command line arguments
	 */
	public static void main(final String[] args) {
		int status = 0;
		X9JdkLogger.initializeLoggingEnvironment();
		LOGGER.info(X9REFORMATX9 + " started");
		try {
			final X9ReformatX9 x9reformatX9 = new X9ReformatX9();
			x9reformatX9.process();
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
