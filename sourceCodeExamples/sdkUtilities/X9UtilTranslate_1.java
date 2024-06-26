package com.x9ware.utilities;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkImageBundle;
import com.x9ware.base.X9SdkObject;
import com.x9ware.beans.X9HeaderAttr937;
import com.x9ware.core.X9;
import com.x9ware.core.X9HeaderXml937;
import com.x9ware.core.X9Reader;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.core.X9Writer;
import com.x9ware.elements.X9C;
import com.x9ware.records.X9CreditInspector;
import com.x9ware.records.X9RecordFields;
import com.x9ware.tools.X9FileIO;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9Folder;
import com.x9ware.tools.X9TempFile;
import com.x9ware.types.X9Type01;
import com.x9ware.types.X9Type10;
import com.x9ware.types.X9Type20;
import com.x9ware.types.X9Type25;
import com.x9ware.types.X9Type26;
import com.x9ware.types.X9Type28;
import com.x9ware.types.X9Type50;
import com.x9ware.types.X9Type52;
import com.x9ware.types.X9Type61;
import com.x9ware.types.X9Type62;
import com.x9ware.types.X9Type70;
import com.x9ware.types.X9Type90;
import com.x9ware.types.X9Type99;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilTranslate is part of our utilities package which extracts data and images from an x9 input
 * file that is parsed with components then written to our output files. Our output can be used as
 * input to X9UtilWriter. Bottom line is that this function will be seldom used. It was developed as
 * a companion tool for for writer, since it shares a common csv format and can be used as a test
 * data generator. We provide command line options to optionally include images and addendas, which
 * thus creates a good representation of the input file. We also populate and write headerXml937
 * with this specific x9 content, which provides helpful insight into the file. Although this tool
 * can be useful in certain situations, please realize that a file that is translated (by us) and
 * then written will not be 100% the same as the original file, given that certain fields (batch
 * headers, etc) will be potentially different. Because of that, we always recommend that export
 * should be more commonly used, since it provides an exact representation of the file data.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilTranslate {

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
	private final X9HeaderXml937 x9headerXml937 = new X9HeaderXml937();
	private final X9SdkImageBundle sdkImageBundle = new X9SdkImageBundle();
	private final X9HeaderAttr937 headerAttr;
	private final boolean isLoggingEnabled;
	private final boolean isIncludeAddenda;
	private final boolean isExcludeHdrXml;
	private final boolean isExcludeCredits;
	private final boolean isImageExportEnabled;
	private X9Sdk sdk;
	private File x9inputFile;
	private File csvOutputFile;
	private File imageFolder;
	private String lastBundleFolderWritten;
	private boolean isReturnsFile;
	private String imageFrontName;
	private String imageBackName;
	private int inputCount;
	private int writeCount;
	private int checkCount;
	private int creditCount;
	private int addendumCount;
	private int imageCount;
	private X9TrailerManager x9trailerManager;

	private boolean isFileHeaderAddedToXml = false;
	private boolean isXmlCashLetterHeaderFieldsPopulated = false;
	private boolean isXmlBundleHeaderFieldsPopulated = false;
	private boolean isXmlItemFieldsPopulated = false;
	private boolean isXmlImageDetailFieldsPopulated = false;
	private boolean isXmlImageDataFieldsPopulated = false;
	private boolean isXmlCreditFieldsPopulated = false;
	private boolean isXmlBofdEndorsementPopulated = false;
	private boolean isXmlSecdEndorsementPopulated = false;
	private boolean isXmlBundleTrailerFieldsPopulated = false;
	private boolean isXmlCashLetterTrailerFieldsPopulated = false;
	private boolean isXmlFileTrailerFieldsPopulated = false;
	private int bundleRecordNumber = 0;
	private int imageSet = 0;
	private BigDecimal itemAmount = BigDecimal.ZERO;
	private String imageViewCreatorRouting = "";
	private String imageViewCreatorDate = "";

	/*
	 * Constants.
	 */
	private static final char COMMA = ',';

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilTranslate.class);

	/*
	 * X9UtilTranslate Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilTranslate(final X9UtilWorkUnit work_Unit) {
		/*
		 * Get command line settings.
		 */
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
		isIncludeAddenda = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_INCLUDE_ADDENDA);
		isExcludeHdrXml = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_EXCLUDE_HEADERXML);
		isExcludeCredits = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_EXCLUDE_CREDITS);
		isImageExportEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_EXPORT);

		/*
		 * Allocate our headerXml bean.
		 */
		x9headerXml937.allocateNewBean();
		headerAttr = x9headerXml937.getAttr();
	}

	/**
	 * Translate an x937 file. We have an x937 file as input and a csv file as output.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Define input and output files.
		 */
		x9inputFile = workUnit.inputFile;
		final X9TempFile x9tempFile = X9UtilWorkUnit.getTempFileInstance(workUnit.outputFile);
		csvOutputFile = x9tempFile.getTemp();
		imageFolder = workUnit.imageFolder;

		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		sdk = X9SdkFactory.getSdk(sdkBase);
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Allocate helper instances.
		 */
		x9trailerManager = new X9TrailerManager937(sdkBase);

		/*
		 * Allocate the image folder when needed.
		 */
		if (isImageExportEnabled) {
			X9Folder.createFolderWhenNeeded(imageFolder);
		}

		/*
		 * Read x9 and write csv.
		 */
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Translate processing.
			 */
			sdkIO.setExportedFileNamesRelative(true);
			processTranslate(sdkIO);

			/*
			 * Write end as the very last row.
			 */
			writeCsv(sdkIO, new String[] { "end" });
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Rename on completion.
				 */
				x9tempFile.renameTemp();
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
				x9totalsXml.setTotals(x9inputFile, x9trailerManager);

				/*
				 * Write summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);
				LOGGER.info(
						"translate finished; input records({}) output csv records({}) checks({}) "
								+ "credits({}) addenda({}) images({})",
						inputCount, writeCount, checkCount, creditCount, addendumCount, imageCount);
			}
		}

		/*
		 * Return exit status zero.
		 */
		return X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * Translation processing with exception thrown on any errors.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @throws Exception
	 */
	private void processTranslate(final X9SdkIO sdkIO) throws Exception {
		/*
		 * Open our input file.
		 */
		try (final X9Reader x9reader = sdkIO.openInputFile(x9inputFile)) {
			/*
			 * Open the csv output file.
			 */
			sdkIO.openCsvOutputFile(csvOutputFile);

			/*
			 * The headerXml line must be the first line on the output csv file when present, but
			 * can be excluded when not desired. We now write this line when not excluded. Overall,
			 * we should second guess the need for this functionality, since our recommended
			 * approach is to put this on the command line and not embed it within the csv. This
			 * action has been retained for compatibility with earlier releases.
			 */
			final String folder = X9FileUtils.getFolderName(workUnit.outputFile);
			final String baseName = FilenameUtils.getBaseName(workUnit.outputFile.toString());
			final File headersFile = new File(folder, baseName + "_headers." + X9C.XML);
			if (!isExcludeHdrXml) {
				final String[] headerXmlCsv = new String[] { "headerXml",
						X9FileUtils.normalize(headersFile.toString()) };
				writeCsv(sdkIO, headerXmlCsv);
			}

			/*
			 * Read until end of file.
			 */
			X9SdkObject sdkObject = null;
			while ((sdkObject = sdkIO.readNext()) != null) {
				/*
				 * Increment counter and get the record type and record number.
				 */
				inputCount++;
				int recordType = sdkObject.getRecordType();
				final String recordFormat = sdkObject.getRecordFormat();

				/*
				 * Log x9 input when enabled via a command line switch.
				 */
				if (isLoggingEnabled) {
					LOGGER.info("x9 recordNumber({}) data({})", sdkObject.getRecordNumber(),
							new String(sdkObject.getDataByteArray()));
				}

				/*
				 * Accumulate and roll totals that are associated with the input file.
				 */
				x9trailerManager.accumulateAndRollTotals(recordType, recordFormat,
						sdkObject.getDataByteArray());

				/*
				 * Exclude credits and their subsequent addenda records when directed. This is
				 * useful for situations where you only want the items, because you instead want to
				 * use our output file and the "-write" function to insert another credit format
				 * into a newly constructed x9 file.
				 */
				if (isExcludeCredits
						&& (recordType == X9.CREDIT_RECONCILIATION || recordType == X9.CREDIT)) {
					skipToNextItem: while ((sdkObject = sdkIO.readNext()) != null) {
						recordType = sdkObject.getRecordType();
						if (recordType == X9.CHECK_ADDENDUM_A // type 26
								|| recordType == X9.CHECK_ADDENDUM_B // type 27
								|| recordType == X9.CHECK_ADDENDUM_C // type 28
								|| recordType == X9.RETURN_ADDENDUM_A // type 32
								|| recordType == X9.RETURN_ADDENDUM_B // type 33
								|| recordType == X9.RETURN_ADDENDUM_C // type 34
								|| recordType == X9.RETURN_ADDENDUM_D // type 35
								|| recordType == X9.IMAGE_VIEW_DETAIL// type 50 and 52
								|| recordType == X9.IMAGE_VIEW_DATA) {
							continue skipToNextItem;
						}
						break skipToNextItem;
					}
				}

				/*
				 * Process this one sdk object.
				 */
				processOneRecord(x9reader, sdkIO, sdkObject);
			}

			/*
			 * Write the headers xml file when not excluded, which is helpful in several situations.
			 * First is to gain insight into the header values for an existing x9 file when you plan
			 * to use "-write" to create that file. The written headers XML file can be your
			 * reviewed as the basis for that work effort. Second that the xml file can be used
			 * after installing a new X9Utilities release to assist in upgrading an existing XML
			 * definition with the latest parameter definitions. You can create this xml file from
			 * the older release and then again with the new release. These xml files can then be
			 * compared using (for example using Meld) to determine what has changed and identify
			 * parameters that are either added or eliminated.
			 */
			if (!isExcludeHdrXml) {
				x9headerXml937.writeExternalXmlFile(headersFile);
				LOGGER.info("headersXml written({})", headersFile);
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * Process one record from the incoming x9 file.
	 * 
	 * @param x9reader
	 *            current x9reader instance
	 * @param sdkIO
	 *            current sdkIO instance
	 * @param sdkObject
	 *            current sdkObject
	 * @throws Exception
	 *             writer output error
	 */
	private void processOneRecord(final X9Reader x9reader, final X9SdkIO sdkIO,
			final X9SdkObject sdkObject) throws Exception {
		/*
		 * Start a new item and assign front/back image relative file names.
		 */
		final int recordNumber = sdkObject.getRecordNumber();
		final int recordType = sdkObject.getRecordType();
		if (sdkObject.isItemRecordType()) {
			imageSet = 0;
			itemAmount = sdkObject.getItemAmount();
			imageFrontName = sdkObject.assignRelativeImageName(sdkImageBundle, bundleRecordNumber,
					itemAmount, 1, X9C.TIF);
			imageBackName = sdkObject.assignRelativeImageName(sdkImageBundle, bundleRecordNumber,
					itemAmount, 2, X9C.TIF);
		}

		/*
		 * Create csv from the x9.
		 */
		sdkIO.makeCsvFromInputRecord(sdkObject);

		/*
		 * Select records based on record type. Much of the following logic is associated with
		 * building a HeaderXml definition that closely mimics the attributes of the input x9.37
		 * file. You can never do this completely, but considerable effort has been applied to
		 * populate the HeaderXml output as accurately as possible.
		 */
		switch (recordType) {
			case X9.FILE_HEADER: {
				if (!isFileHeaderAddedToXml) {
					isFileHeaderAddedToXml = true;
					final X9Type01 t01 = new X9Type01(sdkBase, sdkObject.getDataByteArray());
					headerAttr.x9fileSpecification = StringUtils.equals(t01.ucdIndicator, "1")
							? X9.X9_100_187_UCD_2018_CONFIG
							: X9.X9_37_CONFIG;
					headerAttr.ebcdicEnCoding = x9reader.isEbcdicEncoding();
					headerAttr.fieldZeroInserted = x9reader.isFieldZeroPrefixes();
					headerAttr.fileStandardLevel = t01.standardLevel;
					headerAttr.fileMode = t01.testFileIndicator;
					headerAttr.fileOriginationRouting = t01.immediateOriginRoutingNumber;
					headerAttr.fileOriginationName = t01.immediateOriginName;
					headerAttr.fileDestinationRouting = t01.immediateDestinationRoutingNumber;
					headerAttr.fileDestinationName = t01.immediateDestinationName;
					headerAttr.fileIdModifier = t01.fileIdModifier;
					headerAttr.fileResendIndicator = t01.resendIndicator;
					headerAttr.fileUcdIndicator = t01.ucdIndicator;
					headerAttr.fileCountryCode = t01.countryCode;
					headerAttr.fileUserField = t01.userField;
				}
				break;
			}

			case X9.CASH_LETTER_HEADER: {
				if (!isXmlCashLetterHeaderFieldsPopulated) {
					isXmlCashLetterHeaderFieldsPopulated = true;
					final X9Type10 t10 = new X9Type10(sdkBase, sdkObject.getDataByteArray());
					headerAttr.cashLetterEceInstitutionRouting = t10.eceInstitutionRoutingNumber;
					headerAttr.cashLetterDestinationRouting = t10.destinationRoutingNumber;
					headerAttr.cashLetterIdentifier = ""; // will be date time stamped
					headerAttr.cashLetterContactName = t10.originatorContactName;
					headerAttr.cashLetterContactPhone = t10.originatorContactPhone;
					headerAttr.cashLetterReturnsIndicator = t10.returnsIndicator;
					headerAttr.cashLetterRecordTypeIndicator = t10.cashLetterRecordTypeIndicator;
					headerAttr.cashLetterDocumentationTypeIndicator = t10.cashLetterDocumentationTypeIndicator;
					headerAttr.cashLetterCollectionTypeIndicator = t10.collectionTypeIndicator;
					headerAttr.cashLetterFedWorkType = t10.workType;
					headerAttr.cashLetterUserField = t10.userField;
				}
				break;
			}

			case X9.BUNDLE_HEADER: {
				bundleRecordNumber = recordNumber;
				if (!isXmlBundleHeaderFieldsPopulated) {
					isXmlBundleHeaderFieldsPopulated = true;
					final X9Type20 t20 = new X9Type20(sdkBase, sdkObject.getDataByteArray());
					headerAttr.bundleItemCount = "300";
					headerAttr.bundleIdentifier = ""; // will be date time stamped
					headerAttr.bundleEceInstitutionRouting = t20.eceInstitutionRoutingNumber;
					headerAttr.bundleDestinationRouting = t20.destinationRoutingNumber;
					headerAttr.bundleReturnsRouting = t20.returnLocationRoutingNumber;
					headerAttr.bundleUserField = t20.userField;
				}
				break;
			}

			case X9.CHECK_DETAIL: {
				/*
				 * Build the t25 array which is our preferred flavor of the type 25 record.
				 */
				checkCount++;
				final X9Type25 t25 = new X9Type25(sdkBase, sdkObject.getDataByteArray());
				final String[] a25 = new String[X9Writer.ITEM_IMAGE_BACK_NAME + 1];
				a25[0] = "t25";
				a25[X9Writer.ITEM_AMOUNT] = t25.amount;
				a25[X9Writer.ITEM_SEQUENCE_NUMBER] = t25.itemSequenceNumber;
				a25[X9Writer.ITEM_ROUTING] = t25.payorBankRouting;
				a25[X9Writer.ITEM_ONUS] = t25.micrOnUs;
				a25[X9Writer.ITEM_AUX_ONUS] = t25.auxiliaryOnUs;
				a25[X9Writer.ITEM_EPC] = t25.epc;
				a25[X9Writer.ITEM_IMAGE_ROUTING] = "";
				a25[X9Writer.ITEM_IMAGE_DATE] = "";
				a25[X9Writer.ITEM_IMAGE_FRONT_NAME] = isImageExportEnabled
						? new File(imageFolder, imageFrontName).toString()
						: "";
				a25[X9Writer.ITEM_IMAGE_BACK_NAME] = isImageExportEnabled
						? new File(imageFolder, imageBackName).toString()
						: "";
				writeCsv(sdkIO, a25);

				/*
				 * Populate header fields from the first item.
				 */
				if (!isXmlItemFieldsPopulated) {
					isXmlItemFieldsPopulated = true;
					headerAttr.itemDocumentationTypeIndicator = t25.documentationTypeIndicator;
					headerAttr.itemReturnAcceptanceIndicator = t25.returnAcceptanceIndicator;
					headerAttr.itemMicrValidIndicator = t25.micrValidIndicator;
					headerAttr.itemBofdIndicator = t25.bofdIndicator;
					headerAttr.itemArchiveIndicator = t25.archiveTypeIndicator;
					headerAttr.itemCorrectionIndicator = t25.correctionIndicator;
				}
				break;
			}

			case X9.RETURN_DETAIL: {
				/*
				 * Flag as returns file.
				 */
				checkCount++;
				isReturnsFile = true;

				/*
				 * Set the addendum count to zero when attached addenda are not written.
				 */
				if (!isIncludeAddenda) {
					final X9RecordFields x9recordFields = sdkBase.getRecordFields();
					setColumnInCsvArray(sdkObject, x9recordFields.r31ReturnAddendumCount, "00");
				}

				/*
				 * Write the item csv.
				 */
				writeItem(sdkIO, sdkObject);
				break;
			}

			case X9.CREDIT_RECONCILIATION: {
				/*
				 * Populate header fields.
				 */
				creditCount++;
				if (!isXmlCreditFieldsPopulated) {
					isXmlCreditFieldsPopulated = true;
					final X9CreditInspector creditInspector = new X9CreditInspector(sdkBase);
					final X9Type61 t61 = new X9Type61(sdkBase, sdkObject.getDataByteArray());
					headerAttr.creditStructure = X9HeaderXml937.DEPOSITS_MULTI_ITEM;
					headerAttr.creditFormat = X9CreditInspector
							.translateCreditRecordFormatToDescription(creditInspector
									.getFormatForType61Record(sdkObject.getDataByteArray()));
					headerAttr.creditRecordLocation = "b20";
					headerAttr.creditPayorBankRouting = t61.payorBankRouting;
					headerAttr.creditMicrOnUs = t61.micrOnUs;
					headerAttr.creditMicrAuxOnUs = t61.auxiliaryOnUs;
					headerAttr.creditItemSequenceNumber = "auto";
					headerAttr.creditDocumentationTypeIndicator = t61.documentationTypeIndicator;
					headerAttr.creditRecordUsageIndicator = t61.recordUsageIndicator;
					headerAttr.creditTypeOfAccount = t61.typeOfAccount;
					headerAttr.creditSourceOfWork = t61.sourceOfWork;
					headerAttr.creditWorkType = t61.workType;
					headerAttr.creditDebitCreditIndicator = t61.debitCreditIndicator;
					headerAttr.creditImageDrawCheckListCount = 15;
					headerAttr.creditInsertedAutomatically = true;
					headerAttr.creditImageDrawFront = true;
					headerAttr.creditImageDrawBack = true;
					headerAttr.creditImageDrawMicrLine = true;
					headerAttr.creditCreateBofd = false;
					headerAttr.creditCreateSecondaryEndorsement = false;
					headerAttr.creditAddToItemCount = true;
					headerAttr.creditAddToTotalAmount = false;
					headerAttr.creditAddToImageCount = true;
					headerAttr.creditBeginsNewBundle = true;
				}

				/*
				 * Write the item csv.
				 */
				writeItem(sdkIO, sdkObject);
				break;
			}

			case X9.CREDIT: {
				/*
				 * Populate header fields.
				 */
				creditCount++;
				if (!isXmlCreditFieldsPopulated) {
					isXmlCreditFieldsPopulated = true;
					final X9Type62 t62 = new X9Type62(sdkBase, sdkObject.getDataByteArray());
					headerAttr.creditStructure = X9HeaderXml937.DEPOSITS_MULTI_ITEM;
					headerAttr.creditFormat = "t62";
					headerAttr.creditRecordLocation = "b20";
					headerAttr.creditPayorBankRouting = t62.postingBankRouting;
					headerAttr.creditMicrOnUs = t62.micrOnUs;
					headerAttr.creditMicrAuxOnUs = t62.auxiliaryOnUs;
					headerAttr.creditItemSequenceNumber = "auto";
					headerAttr.creditDocumentationTypeIndicator = t62.documentationTypeIndicator;
					headerAttr.creditTypeOfAccount = t62.typeOfAccountCode;
					headerAttr.creditSourceOfWork = t62.sourceOfWorkCode;
					headerAttr.creditImageDrawCheckListCount = 15;
					headerAttr.creditInsertedAutomatically = true;
					headerAttr.creditImageDrawFront = true;
					headerAttr.creditImageDrawBack = true;
					headerAttr.creditImageDrawMicrLine = true;
					headerAttr.creditCreateBofd = false;
					headerAttr.creditCreateSecondaryEndorsement = false;
					headerAttr.creditAddToItemCount = true;
					headerAttr.creditAddToTotalAmount = false;
					headerAttr.creditBeginsNewBundle = true;
				}

				/*
				 * Write the item csv.
				 */
				writeItem(sdkIO, sdkObject);
				break;
			}

			case X9.CHECK_ADDENDUM_A:
			case X9.RETURN_ADDENDUM_A: {
				/*
				 * Populate header fields.
				 */
				if (!isXmlBofdEndorsementPopulated) {
					isXmlBofdEndorsementPopulated = true;
					final X9Type26 t26 = new X9Type26(sdkBase, sdkObject.getDataByteArray());
					headerAttr.bofdAddendumRouting = t26.routingNumber;
					headerAttr.bofdDepositBranch = t26.depositBranch;
					headerAttr.bofdPayeeName = t26.payeeName;
					headerAttr.bofdAddendumTruncationIndicator = t26.truncationIndicator;
					headerAttr.bofdAddendumConversionIndicator = t26.conversionIndicator;
					headerAttr.bofdAddendumCorrectionIndicator = t26.correctionIndicator;
					headerAttr.bofdAddendumUserField = t26.userField;
				}

				/*
				 * Write the addendum csv.
				 */
				writeAddendum(sdkIO, sdkObject);
				break;
			}

			case X9.CHECK_ADDENDUM_C:
			case X9.RETURN_ADDENDUM_D: {
				/*
				 * Populate header fields.
				 */
				if (!isXmlSecdEndorsementPopulated) {
					isXmlSecdEndorsementPopulated = true;
					final X9Type28 t28 = new X9Type28(sdkBase, sdkObject.getDataByteArray());
					headerAttr.secdAddendumRouting = t28.routingNumber;
					headerAttr.secdAddendumTruncationIndicator = t28.truncationIndicator;
					headerAttr.secdAddendumConversionIndicator = t28.conversionIndicator;
					headerAttr.secdAddendumCorrectionIndicator = t28.correctionIndicator;
					headerAttr.secdAddendumUserField = t28.userField;
				}

				/*
				 * Write the addendum csv.
				 */
				writeAddendum(sdkIO, sdkObject);
				break;

			}

			case X9.CHECK_ADDENDUM_B:
			case X9.RETURN_ADDENDUM_B:
			case X9.RETURN_ADDENDUM_C: {
				writeAddendum(sdkIO, sdkObject);
				break;
			}

			case X9.IMAGE_VIEW_DETAIL: {
				/*
				 * Populate header fields.
				 */
				final X9Type50 t50 = new X9Type50(sdkBase, sdkObject.getDataByteArray());
				imageViewCreatorRouting = t50.routingNumber;
				imageViewCreatorDate = t50.creatorDate;
				if (!isXmlImageDetailFieldsPopulated) {
					isXmlImageDetailFieldsPopulated = true;
					headerAttr.imageDetailImageIndicator = t50.imageIndicator;
					headerAttr.imageDetailFormatIndicator = t50.formatIndicator;
					headerAttr.imageDetailCompressionAlgorithm = t50.compressionIndicator;
					headerAttr.imageDetailDataSize = "actual";
					headerAttr.imageDetailViewDescriptor = t50.descriptor;
					headerAttr.imageDetailDigitalSignatureIndicator = t50.digitalSignatureIndicator;
					headerAttr.imageDetailDigitalSignatureMethod = t50.digitalSignatureMethod;
					headerAttr.imageDetailSecurityKeySize = t50.securityKeySize;
					headerAttr.imageDetailStartOfProtectedData = t50.protectedDataStart;
					headerAttr.imageDetailLengthOfProtectedData = t50.protectedDataLength;
					headerAttr.imageDetailImageRecreateIndicator = t50.imageRecreateIndicator;
					headerAttr.imageDetailUserField = t50.userField;
					headerAttr.imageDetailOverrideIndicator = t50.imageTestOverrideIndicator;
					headerAttr.imageDetailImageRecreateIndicator = t50.imageRecreateIndicator;
				}
				break;
			}

			case X9.IMAGE_VIEW_DATA: {
				/*
				 * Populate header fields.
				 */
				if (!isXmlImageDataFieldsPopulated) {
					final X9Type52 t52 = new X9Type52(sdkBase, sdkObject.getDataByteArray());
					isXmlImageDataFieldsPopulated = true;
					headerAttr.imageDataSecurityOriginatorName = t52.securityOriginatorName;
					headerAttr.imageDataSecurityAuthenticatorName = t52.securityAuthenticatorName;
					headerAttr.imageDataSecurityKeyName = t52.securityOriginatorName;
					headerAttr.imageDataClippingOrigin = t52.clippingOrigin;
					headerAttr.imageDataClippingCoordinateH1 = t52.clippingCoordinateH1;
					headerAttr.imageDataClippingCoordinateH2 = t52.clippingCoordinateH2;
					headerAttr.imageDataClippingCoordinateV1 = t52.clippingCoordinateV1;
					headerAttr.imageDataClippingCoordinateV2 = t52.clippingCoordinateV2;
					headerAttr.imageDataPopulateReferenceKey = false;
					headerAttr.imageDataPopulateDigitalSignature = false;
				}

				/*
				 * Export images when selected and only for imageSets 1 (front) and 2 (back).
				 */
				imageSet++;
				final byte[] tiffArray = sdkObject.getCheckImage();
				if (isImageExportEnabled && tiffArray != null && imageSet <= 2) {
					/*
					 * Use the relative image file name that was assigned from the item.
					 */
					final String imageFileName = imageSet == 1 ? imageFrontName : imageBackName;

					/*
					 * Allocate a new bundle folder when needed.
					 */
					lastBundleFolderWritten = X9SdkImageBundle.createNewBundleFolderWhenNeeded(
							imageFolder, imageFileName, lastBundleFolderWritten);

					/*
					 * Export this image to the external image file.
					 */
					imageCount++;
					final File imageFile = new File(imageFolder, imageFileName);
					X9FileIO.writeFile(tiffArray, imageFile);

					/*
					 * Write the image line for returns file. Note that this is not done for forward
					 * presentment since the image file names are included on the "t25" line.
					 */
					if (isReturnsFile) {
						final String[] a52 = new String[4];
						final String normalizedFileName = X9FileUtils
								.normalize(imageFile.toString());
						a52[0] = "image";
						a52[1] = normalizedFileName;
						a52[2] = imageViewCreatorRouting;
						a52[3] = imageViewCreatorDate;
						writeCsv(sdkIO, a52);
					}

					/*
					 * Clear the image file names as they are written.
					 */
					if (imageSet == 1) {
						imageFrontName = "";
					} else {
						imageBackName = "";
					}

					/*
					 * Clear the type 50 creator routing and date since it has been assigned.
					 */
					imageViewCreatorRouting = "";
					imageViewCreatorDate = "";
				}
				break;
			}

			case X9.BUNDLE_TRAILER: {
				if (!isXmlBundleTrailerFieldsPopulated) {
					isXmlBundleTrailerFieldsPopulated = true;
					final X9Type70 t70 = new X9Type70(sdkBase, sdkObject.getDataByteArray());
					headerAttr.trailerPopulateMicrValidAmount = StringUtils
							.isNotBlank(t70.micrValidAmount);
					headerAttr.trailerPopulateImageCount = StringUtils.isNotBlank(t70.imageCount);
				}
				break;
			}

			case X9.CASH_LETTER_TRAILER: {
				if (!isXmlCashLetterTrailerFieldsPopulated) {
					isXmlCashLetterTrailerFieldsPopulated = true;
					final X9Type90 t90 = new X9Type90(sdkBase, sdkObject.getDataByteArray());
					headerAttr.trailerInstitutionName = t90.eceInstitutionName;
					headerAttr.trailerSettlementDate = "";
				}
				break;
			}

			case X9.FILE_CONTROL_TRAILER: {
				if (!isXmlFileTrailerFieldsPopulated) {
					isXmlFileTrailerFieldsPopulated = true;
					final X9Type99 t99 = new X9Type99(sdkBase, sdkObject.getDataByteArray());
					headerAttr.trailerContactName = t99.immediateOriginContactName;
					headerAttr.trailerContactPhone = t99.immediateOriginContactPhoneNumber;
					headerAttr.trailerCreditTotalIndicator = t99.creditTotalIndicator;
				}
				break;
			}

			default: {
				break;
			}
		}
	}

	/**
	 * Write an item to the output csv file.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param sdkObject
	 *            current sdkObject
	 * @throws IOException
	 */
	private void writeItem(final X9SdkIO sdkIO, final X9SdkObject sdkObject) throws IOException {
		final byte[] x9data = sdkObject.getDataByteArray();
		final BigDecimal amount = sdkBase.getObjectManager().getAmount(x9data,
				sdkObject.getRecordFormat());
		sdkObject.setItemAmount(amount);
		writeCsv(sdkIO, sdkObject.getCsvArray());
	}

	/**
	 * Write an addendum to the output csv file.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param sdkObject
	 *            current sdkObject
	 * @throws IOException
	 */
	private void writeAddendum(final X9SdkIO sdkIO, final X9SdkObject sdkObject)
			throws IOException {
		if (isIncludeAddenda) {
			addendumCount++;
			writeCsv(sdkIO, sdkObject.getCsvArray());
		}
	}

	/**
	 * Write the current csv array to the csv output writer.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param csvArray
	 *            current csv array
	 * @throws IOException
	 */
	private void writeCsv(final X9SdkIO sdkIO, final String[] csvArray) throws IOException {
		/*
		 * Write the next csv line.
		 */
		writeCount++;
		sdkIO.putCsvFromArray(csvArray);

		/*
		 * Log when enabled.
		 */
		if (isLoggingEnabled) {
			LOGGER.info("csv lineNumber({}) content({})", writeCount,
					StringUtils.join(csvArray, COMMA));
		}
	}

	/**
	 * Set a value within the the csvArray.
	 *
	 * @param sdkObject
	 *            current sdkObject
	 * @param index
	 *            column index
	 * @param value
	 *            value to be assigned to the indicated column
	 */
	private void setColumnInCsvArray(final X9SdkObject sdkObject, final int index,
			final String value) {
		final String[] record = sdkObject.getCsvArray();
		if (index < record.length) {
			record[index] = value;
		}
	}

}
