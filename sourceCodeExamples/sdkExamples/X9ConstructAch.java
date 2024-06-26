package com.x9ware.examples;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.ach.X9Ach;
import com.x9ware.ach.X9AchWriter;
import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Item937;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.beans.X9HeaderAttrAch;
import com.x9ware.core.X9;
import com.x9ware.core.X9HeaderXmlAch;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.micr.X9MicrOnUs;
import com.x9ware.tools.X9CsvLine;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9String;
import com.x9ware.tools.X9TempFile;
import com.x9ware.utilities.X9UtilLoadItems;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManagerAch;

/**
 * X9ConstructAch reads an input x9.37 file, extracts all items that are present, and then creates
 * an ACH output file containing those items. This example is provided as a demonstration of our
 * achWriter capabilities, where the input source would be modified based on your specific
 * requirements. The created ACH file can be any Standard Entry Class( SEC). Output ACH files can
 * include debits and/or credits and a variable number of addenda records subject to the chosen SEC.
 * Batch size can be automatic or manually assigned by the application program. This specific
 * example loads an input x9.37 file to an internal list and then uses those items to create an ARC
 * file. However, this is obviously just an example, in terms of the input source, the chosen SEC,
 * and the number of attached addenda records.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ConstructAch {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");

	/*
	 * Constants.
	 */
	private static final String X9CONSTRUCTACH = "X9ConstructAch";
	private static final String TEMP_FILE_SUFFIX = "temp";
	private static final int MAXIMUM_SEQUENCE_NUMBER = 9999999;

	private static final int CSV_TRANSACTION_CODE = 0;
	private static final int CSV_ROUTING = 1;
	private static final int CSV_ACCOUNT = 2;
	private static final int CSV_AMOUNT = 3;
	private static final int CSV_IDENTIFICATION = 4;
	private static final int CSV_NAME_OR_NUMBER = 5;
	private static final int CSV_DISCRETIONARY_DATA = 6;
	private static final int CSV_TRACE_NUMBER = 7;
	private static final int CSV_ADDENDA = 8;
	private static final int CSV_COUNT = 9;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ConstructAch.class);

	/*
	 * X9ConstructAch Constructor.
	 */
	public X9ConstructAch() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment.
		 */
		X9SdkRoot.logStartupEnvironment(X9CONSTRUCTACH);
		X9SdkRoot.loadXmlConfigurationFiles();

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
			 * Load the input x9.37 file to an item list.
			 */
			final File inputFile = new File(baseFolder, "Test file with 25 checks.x9");
			if (!X9FileUtils.existsWithPathTracing(inputFile)) {
				throw X9Exception.abort("x9.37 input file notFound({})", inputFile);
			}

			final List<X9Item937> itemList = X9UtilLoadItems.read937(sdkBase, inputFile);

			/*
			 * Write the loaded items to the output ACH file.
			 */
			createAchFile(itemList);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		LOGGER.info("finished");
	}

	/**
	 * Create the ACH output file from logical items.
	 *
	 * @param itemList
	 *            list of items to be written
	 */
	private X9TrailerManager createAchFile(final List<X9Item937> itemList) {
		/*
		 * Bind to the ach configuration.
		 */
		if (!sdkBase.bindConfiguration(X9.ACH_NACHA_2013_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}

		/*
		 * Set ach file attributes. Content of the ach attributes bean can be defined externally and
		 * just unmarshalled, explicitly set through assignments, or assigned via a combination of
		 * both unmarshall and assignment. Field values within the bean can be absolute (eg, "200"),
		 * an indirect reference to a value in the provided string array (eg, [1] which refers to
		 * the first value within the string array), or an indirect reference to a value within an
		 * external property instance (eg, //propertyName). The batch profile name (this is where
		 * the individual properties will be stored) is defined in headerAttr.batchProfile which can
		 * either be an absolute assignment or a redirection into the string array. Batch profiles
		 * can also be constructed by an application program and stored programmatically into the
		 * X9ValueDirector profile map. In this example, all headerAttr values are being assigned
		 * explicitly into an allocated X9HeaderXmlAch instance. All file/batch header values are
		 * directly assigned. GetCsvLineEntry is then used to build a bracketed name that redirect
		 * transaction specific values into the provided csv array.
		 */
		final LocalDate currentDate = X9LocalDate.getCurrentDate();
		final X9HeaderXmlAch headerXml = new X9HeaderXmlAch();
		final X9HeaderAttrAch headerAttr = headerXml.getAttr();
		headerAttr.fileImmediateDestination = "123456780";
		headerAttr.fileImmediateOrigin = "123456780";
		headerAttr.fileImmediateDestinationName = "TestName";
		headerAttr.fileImmediateOriginName = "OriginName";
		headerAttr.fileCreationDate = X9LocalDate.formatDate(currentDate, "yyMMdd");
		headerAttr.fileCreationTime = X9LocalDate.formatDate(currentDate, "HHss");

		headerAttr.batchStandardEntryClassCode = "ARC";
		headerAttr.batchServiceClassCode = "200";
		headerAttr.batchCompanyName = "Test Company";
		headerAttr.batchCompanyIdentification = "Test";
		headerAttr.batchCompanyDiscretionaryData = "Collections";
		headerAttr.batchCompanyEntryDescription = "Checks";
		headerAttr.batchOriginatingDfiIdentification = "55555555";

		headerAttr.itemTransactionCode = getCsvLineEntryReference(CSV_TRANSACTION_CODE);
		headerAttr.itemDfiRouting = getCsvLineEntryReference(CSV_ROUTING);
		headerAttr.itemDfiAccountNumber = getCsvLineEntryReference(CSV_ACCOUNT);
		headerAttr.itemAmount = getCsvLineEntryReference(CSV_AMOUNT);
		headerAttr.itemIdentification = getCsvLineEntryReference(CSV_IDENTIFICATION);
		headerAttr.itemNameOrNumber = getCsvLineEntryReference(CSV_NAME_OR_NUMBER);
		headerAttr.itemDiscretionaryData = getCsvLineEntryReference(CSV_DISCRETIONARY_DATA);
		headerAttr.itemTraceNumber = getCsvLineEntryReference(CSV_TRACE_NUMBER);
		headerAttr.itemAddendaPaymentInformation = getCsvLineEntryReference(CSV_ADDENDA);

		/*
		 * Create the ACH transaction list.
		 */
		final int debitTranCode = 27;
		final int creditTranCode = 22;
		final List<X9CsvLine> transactionList = createAchTransactionList(itemList, headerAttr,
				debitTranCode, creditTranCode);

		/*
		 * Allocate the output file which will be renamed on completion.
		 */
		X9TrailerManagerAch trailerManager = null;
		final File outputFile = new File(baseFolder, "x9constructAch.ach");
		final X9TempFile achTempFile = new X9TempFile(outputFile, TEMP_FILE_SUFFIX);
		try (final X9AchWriter achWriter = new X9AchWriter(sdkBase, X9AchWriter.LOGGING_DISABLED)) {
			/*
			 * Create the ach output file from the input csv. Ach writer will re-sort items by entry
			 * class and date which allows the items to be appropriately grouped for batching.
			 */
			trailerManager = achWriter.createAchFile(transactionList, achTempFile.getTemp(),
					headerXml);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Rename on completion.
		 */
		achTempFile.renameTemp();

		/*
		 * All completed.
		 */
		LOGGER.info("ach writer completed({})", outputFile);

		/*
		 * Return the trailer manager which contains file totals.
		 */
		return trailerManager;
	}

	/**
	 * Create an ach transaction list.
	 *
	 * @param itemList
	 *            list of items to be written
	 * @param headerAttr
	 *            ach header attributes
	 * @param debitTranCode
	 *            debit transaction code
	 * @param creditTranCode
	 *            credit transaction code
	 * @return ach transaction list
	 */
	public List<X9CsvLine> createAchTransactionList(final List<X9Item937> itemList,
			final X9HeaderAttrAch headerAttr, final int debitTranCode, final int creditTranCode) {
		/*
		 * Accumulate debit/credit counts and amounts.
		 */
		int debitCount = 0;
		int creditCount = 0;
		BigDecimal debitAmount = BigDecimal.ZERO;
		BigDecimal creditAmount = BigDecimal.ZERO;
		for (final X9Item937 x9item937 : itemList) {
			final int recordType = x9item937.getRecordType();
			if (itemIsDebit(recordType)) {
				debitCount++;
				debitAmount = debitAmount.add(x9item937.getAmount());
			} else {
				creditCount++;
				creditAmount = creditAmount.add(x9item937.getAmount());
			}
		}

		/*
		 * Generate all items.
		 */
		int itemNumber = 0;
		int itemSequenceNumber = 210000; // starting sequence number suffix for items
		final List<X9CsvLine> transactionList = new ArrayList<>(itemList.size());
		for (final X9Item937 x9item937 : itemList) {
			/*
			 * Get item level information.
			 */
			itemNumber++;
			final int recordType = x9item937.getRecordType();
			final boolean isDebit = itemIsDebit(recordType);
			final String trancode = Integer.toString(isDebit ? debitTranCode : creditTranCode);
			final String routing = X9String.leftPadZeroes(x9item937.getRouting(), 9);
			final X9MicrOnUs x9micrOnUs = new X9MicrOnUs(x9item937.getOnus());
			final String account = getNumericAccountNumber(x9micrOnUs.getAccount());
			final String processControl = x9micrOnUs.getProcessControl();
			final String auxOnUs = x9item937.getAuxOnus();
			final String checkNumber = StringUtils.isBlank(auxOnUs) ? processControl : auxOnUs;
			final String sequenceNumber = x9item937.getItemSequenceNumber();
			final String trcItemInformation = X9String.rightPadTrimmed(processControl, 6)
					+ X9String.rightPadTrimmed(sequenceNumber, 16);
			final String originatingDfi = X9String
					.rightPadTrimmed(headerAttr.batchOriginatingDfiIdentification, 8);

			/*
			 * Initial identification/receiver assignment.
			 */
			String itemIdentification = headerAttr.batchCompanyIdentification;
			String itemInformation = "";

			/*
			 * Assign identification/receiver based on record content requirements.
			 */
			final String entryClass = headerAttr.batchStandardEntryClassCode;
			if (StringUtils.equalsAny(entryClass, X9Ach.ENTRY_CLASS_TRC, X9Ach.ENTRY_CLASS_XCK)) {
				itemInformation = trcItemInformation;
			} else if (StringUtils.equalsAny(entryClass, X9Ach.ENTRY_CLASS_ARC,
					X9Ach.ENTRY_CLASS_BOC, X9Ach.ENTRY_CLASS_POP, X9Ach.ENTRY_CLASS_RCK)) {
				itemIdentification = checkNumber;
			}

			/*
			 * Assign defaults when no identification/receiver has been set.
			 */
			itemIdentification = StringUtils.isBlank(itemIdentification) ? checkNumber
					: itemIdentification;
			itemInformation = StringUtils.isBlank(itemInformation) ? trcItemInformation
					: itemInformation;

			/*
			 * Truncate to maximum lengths.
			 */
			itemIdentification = StringUtils.rightPad(itemIdentification, 15);
			itemInformation = StringUtils.rightPad(itemInformation, 22);

			/*
			 * Increment item sequence number and wrap on rollover.
			 */
			if (itemSequenceNumber > MAXIMUM_SEQUENCE_NUMBER) {
				itemSequenceNumber = 0;
			}

			/*
			 * Build the csv array required by generate. Note that this is a variable length array,
			 * and can be allocated larger when multiple addenda records are needed.
			 */
			final String[] csvLine = new String[CSV_COUNT];
			csvLine[CSV_TRANSACTION_CODE] = trancode;
			csvLine[CSV_ROUTING] = routing;
			csvLine[CSV_ACCOUNT] = account;
			csvLine[CSV_AMOUNT] = Long.toString(x9item937.getAmountAsLong());
			csvLine[CSV_IDENTIFICATION] = itemIdentification;
			csvLine[CSV_NAME_OR_NUMBER] = itemInformation;
			csvLine[CSV_DISCRETIONARY_DATA] = "";
			csvLine[CSV_TRACE_NUMBER] = originatingDfi
					+ X9Numeric.getAsString(itemSequenceNumber++, 7);

			/*
			 * One or more addendas are attached here. These are each fully formed 94 byte ACH
			 * records, which can be created using static methods provided by X9AchType7.
			 */
			csvLine[CSV_ADDENDA] = "";

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(
						"outputItemNumber({}) recordType({}) "
								+ "entryClass({}) isDebit({}) transaction({})",
						itemNumber, recordType, recordType, isDebit,
						StringUtils.join(csvLine, '|'));
			}

			/*
			 * Add this item to the accumulated transaction list.
			 */
			transactionList.add(new X9CsvLine(csvLine, itemNumber));
		}

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug(
					"ach transactions generated; debitCount({}) debitAmount({}) "
							+ "creditCount({}) creditAmount({})",
					debitCount, debitAmount, creditCount, creditAmount);
		}

		/*
		 * Return the constructed transaction list.
		 */
		return transactionList;
	}

	/**
	 * Determine if a record type is debit or credit.
	 *
	 * @param recordType
	 *            current record type
	 * @return true or false
	 */
	private static boolean itemIsDebit(final int recordType) {
		return recordType == X9.CHECK_DETAIL;
	}

	/**
	 * Get a csv line entry reference for a specific column. The index is provided relative to zero,
	 * and we must return the column reference relative to one. The reference is bracketed to
	 * indicate that it points to a value within the csv line (and is not an explicit value).
	 *
	 * @param index
	 *            field index
	 * @return csv line reference
	 */
	private static String getCsvLineEntryReference(final int index) {
		return "[" + (index + 1) + "]";
	}

	/**
	 * Get the numeric account number (skip dashes, etc) but retain digit errors. The formatting of
	 * the account number field is critical since it will be used for transaction posting. Digit
	 * errors must be retained since they clearly indicate the uncertainty of the data and will
	 * surely result in a posting problem by the receiver, which is our intent. However, we remove
	 * other characters (such as embedded dashes and blanks) since they are not material to the
	 * account number and should be removed as part of this reformatting effort.
	 *
	 * @param text
	 *            text string
	 * @return string of contained numeric characters only
	 */
	private static String getNumericAccountNumber(final String text) {
		String number = "";
		for (int i = 0, n = text.length(); i < n; i++) {
			final char textDigit = text.charAt(i);
			if ((textDigit >= '0' && textDigit <= '9') || textDigit == '*') {
				number += textDigit;
			}
		}
		return number;
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
		LOGGER.info(X9CONSTRUCTACH + " started");
		try {
			final X9ConstructAch example = new X9ConstructAch();
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
