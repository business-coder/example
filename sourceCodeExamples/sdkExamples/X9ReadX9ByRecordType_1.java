package com.x9ware.examples;

import java.io.File;
import java.math.BigDecimal;

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
import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9FileIO;
import com.x9ware.types.X9Type01;
import com.x9ware.types.X9Type10;
import com.x9ware.types.X9Type20;
import com.x9ware.types.X9Type25;

/**
 * X9ReadX9ByRecordType reads an x9.37 file and use type objects to map and retrieves the specific
 * fields as defined at the record type level. Note that the field level classes also support
 * modify, so they can be used to modify individual fields and create a modified file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ReadX9ByRecordType {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final X9Sdk sdk;
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");
	private final File imageFolder = new File("c:/users/x9ware5/downloads/exportImageTest");

	/*
	 * Constants.
	 */
	private static final String X9READX9_BY_RECORD_TYPE = "X9ReadX9ByRecordType";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ReadX9ByRecordType.class);

	/*
	 * X9ReadX9ByRecordType Constructor.
	 */
	public X9ReadX9ByRecordType() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9READX9_BY_RECORD_TYPE);
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

		/*
		 * Read the x9.37 file and log basic fields by record type. This example opens the x9.37 as
		 * an input file, but you can also use sdkIO.openInputReader() to read from an input stream.
		 */
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputFile(x9InputFile)) {
			/*
			 * Read until end of file.
			 */
			BigDecimal itemAmount = BigDecimal.ZERO;
			String itemSequenceNumber = "";
			X9SdkObject sdkObject = null;
			while ((sdkObject = sdkIO.readNext()) != null) {
				/*
				 * Log the content of various record types as examples of field access.
				 */
				final int recordNumber = sdkObject.getRecordNumber();
				final int recordType = sdkObject.getRecordType();
				switch (recordType) {

					case X9.FILE_HEADER: {
						final X9Type01 t01 = new X9Type01(sdkBase, sdkObject.getAsciiRecord());
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
						final X9Type10 t10 = new X9Type10(sdkBase, sdkObject.getAsciiRecord());
						LOGGER.info("recordNumber({}) recordType({}) "
								+ "collectionTypeIndicator({})" + "destinationRoutingNumber({}) "
								+ "eceInstitutionRoutingNumber({}) "
								+ "cashLetterIdentifier({}) cashLetterDocumentationTypeIndicator({})",
								recordNumber, recordType, t10.collectionTypeIndicator,
								t10.destinationRoutingNumber, t10.eceInstitutionRoutingNumber,
								t10.cashLetterIdentifier, t10.cashLetterDocumentationTypeIndicator);
						break;
					}

					case X9.BUNDLE_HEADER: {
						final X9Type20 t20 = new X9Type20(sdkBase, sdkObject.getAsciiRecord());
						LOGGER.info(
								"recordNumber({}) recordType({}) " + "collectionTypeIndicator({})"
										+ "destinationRoutingNumber({}) "
										+ "eceInstitutionRoutingNumber({}) "
										+ "bundleIdentifier({}) bundleSequenceNumber({})",
								recordNumber, recordType, t20.collectionTypeIndicator,
								t20.destinationRoutingNumber, t20.eceInstitutionRoutingNumber,
								t20.bundleIdentifier, t20.bundleSequenceNumber);
						break;
					}

					case X9.CHECK_DETAIL: {
						final byte[] dataRecord = sdkObject.getAsciiRecord();
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
						final boolean isFrontImage = sdkObject.isFrontImage();
						final byte[] tiffArray = sdkObject.getCheckImage();
						LOGGER.info(
								"recordNumber({}) recordType({}) isFrontImage({}) imageLength({})",
								recordNumber, recordType, isFrontImage, tiffArray.length);
						final String relativeName = recordNumber + "_"
								+ X9Decimal.getStringValue(itemAmount) + "_" + itemSequenceNumber
								+ "_" + (isFrontImage ? "front" : "back") + ".tif";
						final File imageFile = new File(imageFolder, relativeName);
						X9FileIO.writeFile(tiffArray, imageFile);
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
		LOGGER.info(X9READX9_BY_RECORD_TYPE + " started");
		try {
			final X9ReadX9ByRecordType example = new X9ReadX9ByRecordType();
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
