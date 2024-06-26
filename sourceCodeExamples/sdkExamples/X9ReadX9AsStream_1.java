package com.x9ware.examples;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Dialect;
import com.x9ware.base.X9DialectFactory;
import com.x9ware.base.X9FileReader;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9ObjectManager;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.core.X9Reader;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tools.X9Decimal;
import com.x9ware.types.X9Type01;
import com.x9ware.types.X9Type10;
import com.x9ware.types.X9Type20;
import com.x9ware.types.X9Type25;
import com.x9ware.types.X9Type70;
import com.x9ware.types.X9Type90;
import com.x9ware.types.X9Type99;

/**
 * X9ReadX9AsStream reads an x9.37 file as an input stream and retrieves the specific fields as
 * defined at the record type level. Note that the field level classes also support modify, so they
 * can be used to modify individual fields and create a modified file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9ReadX9AsStream {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");

	/*
	 * Constants.
	 */
	private static final String X9ReadX9AsStream = "X9ReadX9AsStream";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ReadX9AsStream.class);

	/*
	 * X9ReadX9AsStream Constructor.
	 */
	public X9ReadX9AsStream() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9ReadX9AsStream);
		X9SdkRoot.loadXmlConfigurationFiles();
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

		/*
		 * Read the x9.37 file and log basic fields by record type.
		 */
		int recordNumber = 0;
		final X9ObjectManager x9objectManager = sdkBase.getObjectManager();
		try (final InputStream inputStream = new BufferedInputStream(
				new FileInputStream(x9InputFile));
				final X9FileReader x9fileReader = X9FileReader.getNewStreamReader(inputStream);
				final X9Reader x9reader = X9DialectFactory.getNewReader(sdkBase, X9Dialect.X9,
						x9fileReader, X9Reader.MAILBOX_NOT_ACCEPTED)) {
			/*
			 * Read until end of file.
			 */
			BigDecimal itemAmount = BigDecimal.ZERO;
			String itemSequenceNumber = "";
			while (x9reader.getNext() != null) {
				final X9Object x9o = x9reader
						.createNewX9Object(x9objectManager.getNextRecordIndex());
				/*
				 * Log the content of various record types as examples of field access.
				 */
				recordNumber++;
				final int recordType = x9o.x9ObjType;
				switch (recordType) {

					case X9.FILE_HEADER: {
						final X9Type01 t01 = new X9Type01(x9o);
						LOGGER.info(
								"recordNumber({}) recordType({}) "
										+ "immediateDestinationRoutingNumber({}) "
										+ "immediateOriginRoutingNumber({}) "
										+ "immediateDestinationName({}) immediateOriginName({})",
								recordNumber, recordType, t01.immediateDestinationRoutingNumber,
								t01.immediateOriginRoutingNumber, t01.immediateDestinationName,
								t01.immediateOriginName);
						break;
					}

					case X9.CASH_LETTER_HEADER: {
						final X9Type10 t10 = new X9Type10(x9o);
						LOGGER.info("recordNumber({}) recordType({}) collectionTypeIndicator({})"
								+ "destinationRoutingNumber({}) eceInstitutionRoutingNumber({}) "
								+ "cashLetterIdentifier({}) cashLetterDocumentationTypeIndicator({})",
								recordNumber, recordType, t10.collectionTypeIndicator,
								t10.destinationRoutingNumber, t10.eceInstitutionRoutingNumber,
								t10.cashLetterIdentifier, t10.cashLetterDocumentationTypeIndicator);
						break;
					}

					case X9.BUNDLE_HEADER: {
						final X9Type20 t20 = new X9Type20(x9o);
						LOGGER.info("recordNumber({}) recordType({}) collectionTypeIndicator({})"
								+ "destinationRoutingNumber({}) eceInstitutionRoutingNumber({}) "
								+ "bundleIdentifier({}) bundleSequenceNumber({})", recordNumber,
								recordType, t20.collectionTypeIndicator,
								t20.destinationRoutingNumber, t20.eceInstitutionRoutingNumber,
								t20.bundleIdentifier, t20.bundleSequenceNumber);
						break;
					}

					case X9.CHECK_DETAIL: {
						final byte[] dataRecord = x9o.x9ObjData;
						final X9Type25 t25 = new X9Type25(sdkBase, dataRecord);
						itemAmount = X9Decimal.getAsAmount(t25.amount);
						itemSequenceNumber = t25.itemSequenceNumber;
						LOGGER.info("recordNumber({}) recordType({}) "
								+ "auxiliaryOnUs({}) payorBankRouting({}) micrOnUs({}) amount({}) "
								+ "itemSequenceNumber({}) data({})", recordNumber, recordType,
								t25.auxiliaryOnUs, t25.payorBankRouting, t25.micrOnUs, itemAmount,
								itemSequenceNumber, new String(dataRecord));
						break;
					}

					case X9.IMAGE_VIEW_DATA: {
						final byte[] tiffArray = x9reader.getImageBuffer();
						LOGGER.info("recordNumber({}) recordType({}) imageLength({})", recordNumber,
								recordType, tiffArray.length);
						break;
					}

					case X9.BUNDLE_TRAILER: {
						final X9Type70 t70 = new X9Type70(x9o);
						LOGGER.info(
								"recordNumber({}) recordType({}) itemCount({}) "
										+ "imageCount({}) itemAmount({}) micrValidAmount({})) ",
								recordNumber, recordType, t70.itemCount, t70.imageCount,
								t70.itemAmount, t70.micrValidAmount);
						break;
					}

					case X9.CASH_LETTER_TRAILER: {
						final X9Type90 t90 = new X9Type90(x9o);
						LOGGER.info(
								"recordNumber({}) recordType({}) bundleCount({}) "
										+ "itemCount({}) imageCount({})) itemAmount({})",
								recordNumber, recordType, t90.bundleCount, t90.itemCount,
								t90.imageCount, t90.itemAmount);
						break;
					}

					case X9.FILE_CONTROL_TRAILER: {
						final X9Type99 t99 = new X9Type99(x9o);
						LOGGER.info(
								"recordNumber({}) recordType({}) cashLetterCount({}) "
										+ "totalItemCount({}) totalRecordCount({})) "
										+ "fileTotalAmount({})",
								recordNumber, recordType, t99.cashLetterCount, t99.totalItemCount,
								t99.totalRecordCount, t99.fileTotalAmount);
						break;
					}

					default: {
						break;
					}
				}
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Log our completion.
		 */
		LOGGER.info("finished recordCount({})", recordNumber);
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
		LOGGER.info(X9ReadX9AsStream + " started");
		try {
			final X9ReadX9AsStream example = new X9ReadX9AsStream();
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
