package com.x9ware.examples;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Item937;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkObjectFactory;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.elements.X9WareLLC;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.records.X9Factory;
import com.x9ware.records.X9RecordFields;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.utilities.X9UtilLoadItems;

/**
 * X9ConstructX9 builds an x9.37 output file on a record by record and field by field basis. This
 * example shows how automated bundling is implemented and how totals are automatically accumulated
 * and populated for the x9.37 trailer records. X9ConstructX9 is a low level implementation, where
 * you have detailed responsibility for creating all record types and fields. You should also take a
 * look at X9DemoWriter as an alternative. It is based on our x9writer technology, which builds an
 * x9.37 file at a higher level. Using x9writer is much more flexible, since a large number of
 * record fields are populated from the x9header xml file, which is a much more generic approach
 * which allows the application program to be more independent of the output x9.37 content.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ConstructX9 {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final X9Factory x9factory;
	private final X9RecordFields x9recordFields;

	private int cashLetterNumber;
	private int bundleNumber;
	private int bundleSequenceNumber;

	private final long destinationRouting = 123456780L;
	private final long originationRouting = 123456780L;
	private final long returnsRouting = 123456780L;

	private final LocalDate currentDate = X9LocalDate.getCurrentDate();
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");

	/*
	 * Constants.
	 */
	private static final String X9CONSTRUCTX9 = "X9ConstructX9";

	private static final String ORIGINATOR_NAME = "originator";
	private static final String COLLECTION_TYPE_INDICATOR = "01";
	private static final String RECORD_TYPE_INDICATOR = "I";
	private static final String FED_WORK_TYPE = "C";
	private static final String DOCUMENTATION_TYPE_INDICATOR = "G";

	private static final String IMAGE_FRONT = "0";
	private static final String IMAGE_BACK = "1";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ConstructX9.class);

	/*
	 * X9ConstructX9 Constructor.
	 */
	public X9ConstructX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9CONSTRUCTX9);
		X9SdkRoot.loadXmlConfigurationFiles();
		if (!sdkBase.bindConfiguration(X9.X9_100_187_UCD_2008_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}

		/*
		 * Allocate our x9.37 record factory.
		 */
		x9factory = new X9Factory(sdkBase);
		x9recordFields = sdkBase.getRecordFields();
	}

	/**
	 * Create an output x9.37 file with complete control over the record types written.
	 *
	 * @throws IOException
	 */
	private void process() throws IOException {
		/*
		 * Set sdk writer options for character set and field zero.
		 */
		final X9SdkObjectFactory x9sdkObjectFactory = sdkBase.getSdkObjectFactory();
		x9sdkObjectFactory.setIsOutputEbcdic(true); // this is the default
		x9sdkObjectFactory.setFieldZeroInserted(true); // this is the default

		/*
		 * Load an x9.37 file to be used as an item list.
		 */
		final File inputFile = new File(baseFolder, "Test file with 25 checks.x9");
		if (!X9FileUtils.existsWithPathTracing(inputFile)) {
			throw X9Exception.abort("x9.37 input file notFound({})", inputFile);
		}

		final List<X9Item937> itemList = X9UtilLoadItems.read937(sdkBase, inputFile);

		/*
		 * Define files.
		 */
		final File outputFile = new File(baseFolder, "x9constructX9.x9");

		/*
		 * Write x9 record by record and field by field.
		 */
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Open files.
			 */
			sdkIO.openOutputFile(outputFile);

			/*
			 * Allow all trailer totals to be calculated automatically.
			 */
			sdkBase.setRepairTrailers(true);

			/*
			 * Write the file header and cash letter header.
			 */
			writeFileHeader(sdkIO, X9.FILE_HEADER);
			writeCashLetterHeader(sdkIO, X9.CASH_LETTER_HEADER);

			/*
			 * Write the first bundle header.
			 */
			writeBundleHeader(sdkIO, X9.BUNDLE_HEADER);

			/*
			 * Write a type 61 credit reconciliation record. Please contact us if you need to draw a
			 * deposit ticket which includes the credit amount.
			 */
			writeCredit(sdkIO, X9.CREDIT_RECONCILIATION,
					X9UtilLoadItems.getTotalDebitAmount937(itemList));

			/*
			 * Create the checks.
			 */
			for (final X9Item937 x9item937 : itemList) {
				/*
				 * Write another bundle header when needed. Note that batch cutoff is based on the
				 * number of items and is automatically handled by the sdk.
				 */
				if (sdkIO.isBundleHeaderNeeded()) {
					writeBundleHeader(sdkIO, X9.BUNDLE_HEADER);
				}

				/*
				 * Write the check and associated records.
				 */
				writeCheckGroup(sdkIO, x9item937);
			}

			/*
			 * Write trailer records.
			 */
			sdkIO.writeBundleTrailer();
			sdkIO.writeCashLetterTrailer(ORIGINATOR_NAME, "");
			sdkIO.writeFileControlTrailer();

			/*
			 * Log our statistics.
			 */
			LOGGER.info(sdkIO.getSdkStatisticsMessage(outputFile));
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Log as completed.
		 */
		LOGGER.info("finished");
	}

	/**
	 * Create the file header record (type 01).
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9type
	 *            current record type
	 */
	private void writeFileHeader(final X9SdkIO sdkIO, final int x9type) {
		/*
		 * Populate record fields.
		 */
		final X9SdkObject sdkObject = sdkIO.startNewCsvRecord(x9type);
		final String[] record = x9factory.allocate(sdkObject);
		x9factory.set(record, x9recordFields.r01StandardLevel, "03");
		x9factory.set(record, x9recordFields.r01TestFileIndicator, "T");
		x9factory.set(record, x9recordFields.r01ImmediateDestinationRouting, destinationRouting);
		x9factory.set(record, x9recordFields.r01ImmediateOriginRouting, originationRouting);
		x9factory.set(record, x9recordFields.r01FileCreationDate, currentDate);
		x9factory.set(record, x9recordFields.r01FileCreationTime, "1200");
		x9factory.set(record, x9recordFields.r01ResendIndicator, "N");
		x9factory.set(record, x9recordFields.r01ImmediateDestinationName, "destinationBank");
		x9factory.set(record, x9recordFields.r01ImmediateOrigName, "originationBank");
		x9factory.set(record, x9recordFields.r01FileIdModifier, "A");
		x9factory.set(record, x9recordFields.r01CountryCode, "");
		x9factory.set(record, x9recordFields.r01UserField, "");
		x9factory.set(record, x9recordFields.r01UcdIndicator, "1");

		sdkIO.writeFromArray(sdkObject, record);
	}

	/**
	 * Create the cash letter header record (type 10).
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9type
	 *            current record type
	 */
	private void writeCashLetterHeader(final X9SdkIO sdkIO, final int x9type) {
		/*
		 * Populate record fields.
		 */
		final X9SdkObject sdkObject = sdkIO.startNewCsvRecord(x9type);
		final String[] record = x9factory.allocate(sdkObject);
		x9factory.set(record, x9recordFields.r10CollectionTypeIndicator, COLLECTION_TYPE_INDICATOR);
		x9factory.set(record, x9recordFields.r10DestinationRouting, destinationRouting);
		x9factory.set(record, x9recordFields.r10EceInstitutionRouting, originationRouting);
		x9factory.set(record, x9recordFields.r10CashLetterBusinessDate, currentDate);
		x9factory.set(record, x9recordFields.r10CashLetterCreationDate, currentDate);
		x9factory.set(record, x9recordFields.r10CashLetterCreationTime, "1200");
		x9factory.set(record, x9recordFields.r10CashLetterRecordTypeIndicator,
				RECORD_TYPE_INDICATOR);
		x9factory.set(record, x9recordFields.r10CashLetterDocumentationTypeIndicator,
				DOCUMENTATION_TYPE_INDICATOR);
		x9factory.set(record, x9recordFields.r10CashLetterIdentifier, ++cashLetterNumber);
		x9factory.set(record, x9recordFields.r10OriginatorContactName, X9WareLLC.X9WARE_LLC);
		x9factory.set(record, x9recordFields.r10OriginatorContactPhoneNumber, "");
		x9factory.set(record, x9recordFields.r10WorkType, FED_WORK_TYPE);

		sdkIO.writeFromArray(sdkObject, record);
	}

	/**
	 * Create the bundle header record (type 20).
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9type
	 *            current record type
	 */
	private void writeBundleHeader(final X9SdkIO sdkIO, final int x9type) {
		/*
		 * Populate record fields.
		 */
		final X9SdkObject sdkObject = sdkIO.startNewCsvRecord(x9type);
		final String[] record = x9factory.allocate(sdkObject);
		x9factory.set(record, x9recordFields.r20CollectionTypeIndicator, COLLECTION_TYPE_INDICATOR);
		x9factory.set(record, x9recordFields.r20DestinationRouting, destinationRouting);
		x9factory.set(record, x9recordFields.r20EceInstitutionRouting, originationRouting);
		x9factory.set(record, x9recordFields.r20BundleBusinessDate, currentDate);
		x9factory.set(record, x9recordFields.r20BundleCreationDate, currentDate);
		x9factory.set(record, x9recordFields.r20BundleIdentifier, ++bundleNumber);
		x9factory.set(record, x9recordFields.r20BundleSequenceNumber, ++bundleSequenceNumber);
		x9factory.set(record, x9recordFields.r20CycleNumber, "");
		x9factory.set(record, x9recordFields.r20ReturnLocationRouting, returnsRouting);

		/*
		 * Write the type 20 bundle header record.
		 */
		sdkIO.writeFromArray(sdkObject, record);
	}

	/**
	 * Create an optional credit record (type 61).
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9type
	 *            current record type
	 * @param creditAmount
	 *            current credit amount
	 */
	private void writeCredit(final X9SdkIO sdkIO, final int x9type, final long creditAmount) {
		/*
		 * Populate record fields.
		 */
		final String recordFormat = X9.CREDIT_METAVANTE_FORMAT_001;
		final X9SdkObject sdkObject = sdkIO.startNewCsvRecord(x9type, recordFormat);
		final String[] record = x9factory.allocate(sdkObject);

		/*
		 * Assign record fields for this credit format.
		 */
		x9recordFields.updateFieldsForSpecificationLevel(sdkBase.getRules(), x9type, recordFormat);

		/*
		 * Format the credit based on the specifically selected record type (61/62) and format using
		 * our generic credit support which allows us to populate all of our supported formats.
		 */
		x9factory.set(record, x9recordFields.t6xRecordUsageIndicator, "2");
		x9factory.set(record, x9recordFields.t6xAuxOnus, "");
		x9factory.set(record, x9recordFields.t6xExternalProcessingCode, "");
		x9factory.set(record, x9recordFields.t6xPostingBankRouting, "123456780");
		x9factory.set(record, x9recordFields.t6xOnus, "1234567891234/");
		x9factory.set(record, x9recordFields.t6xItemAmount, creditAmount);
		x9factory.set(record, x9recordFields.t6xCreditSequenceNumber, 88888888);
		x9factory.set(record, x9recordFields.t6xDocumentationTypeIndicator, "G");
		x9factory.set(record, x9recordFields.t6xTypeOfAccount, "");
		x9factory.set(record, x9recordFields.t6xSourceOfWork, "");
		x9factory.set(record, x9recordFields.t6xWorkType, "");
		x9factory.set(record, x9recordFields.t6xDebitCreditIndicator, "");
		x9factory.set(record, x9recordFields.t6xUserField, "");
		x9factory.set(record, x9recordFields.t6xReserved, "");

		/*
		 * Write the type 61 credit record.
		 */
		sdkIO.writeFromArray(sdkObject, record);
	}

	/**
	 * Write the group of records that follow a check detail record.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9item937
	 *            current item to be written
	 */
	private void writeCheckGroup(final X9SdkIO sdkIO, final X9Item937 x9item937) {
		/*
		 * Load a tiff image from an external file.
		 */
		// final byte[] frontImage = X9FileIO.readFile(checkImageFile);

		/*
		 * Write the check detail record.
		 */
		writeCheckDetail(sdkIO, x9item937, X9.CHECK_DETAIL);

		/*
		 * Write bofd endorsement.
		 */
		writeBofdEndorsement(sdkIO, x9item937, X9.CHECK_ADDENDUM_A);

		/*
		 * Write image records for the check front.
		 */
		final byte[] frontImage = x9item937.getFrontImage();
		writeImageViewDetail(sdkIO, x9item937, IMAGE_FRONT, frontImage.length);
		writeImageViewData(sdkIO, x9item937, frontImage);

		/*
		 * Write image records for the check back.
		 */
		final byte[] backImage = x9item937.getBackImage();
		writeImageViewDetail(sdkIO, x9item937, IMAGE_BACK, backImage.length);
		writeImageViewData(sdkIO, x9item937, backImage);
	}

	/**
	 * Create the check record (type 25).
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9item937
	 *            current item to be written
	 * @param x9type
	 *            current record type
	 */
	private void writeCheckDetail(final X9SdkIO sdkIO, final X9Item937 x9item937,
			final int x9type) {
		/*
		 * Populate record fields.
		 */
		final String routing = x9item937.getRouting();
		final X9SdkObject sdkObject = sdkIO.startNewCsvRecord(x9type);
		final String[] record = x9factory.allocate(sdkObject);
		x9factory.set(record, x9recordFields.r25AuxOnus, x9item937.getAuxOnus());
		x9factory.set(record, x9recordFields.r25ExternalProcessingCode, x9item937.getEpc());
		x9factory.set(record, x9recordFields.r25PayorBankRoutingNumber,
				StringUtils.mid(routing, 0, 8));
		x9factory.set(record, x9recordFields.r25PayorBankRoutingCheckDigit,
				StringUtils.mid(routing, 8, 1));
		x9factory.set(record, x9recordFields.r25Onus, x9item937.getOnus());
		x9factory.set(record, x9recordFields.r25ItemAmount, x9item937.getAmountAsLong());
		x9factory.set(record, x9recordFields.r25EceItemSequenceNumber,
				x9item937.getItemSequenceNumber());
		x9factory.set(record, x9recordFields.r25DocumentationTypeIndicator,
				x9item937.getDocumentationTypeIndicator());
		x9factory.set(record, x9recordFields.r25ReturnAcceptanceIndicator,
				x9item937.getReturnAcceptanceIndicator());
		x9factory.set(record, x9recordFields.r25MicrValidIndicator,
				x9item937.getMicrValidIndicator());
		x9factory.set(record, x9recordFields.r25BofdIndicator, x9item937.getBofdIndicator());
		x9factory.set(record, x9recordFields.r25CheckDetailAddendumCount, "01");
		x9factory.set(record, x9recordFields.r25CorrectionIndicator,
				x9item937.getCorrectionIndicator());
		x9factory.set(record, x9recordFields.r25ArchiveTypeIndicator,
				x9item937.getArchiveTypeIndicator());

		/*
		 * Write the type 25 check detail record.
		 */
		sdkIO.writeFromArray(sdkObject, record);
	}

	/**
	 * Create the Bofd endorsement record (type 26).
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9item937
	 *            current item to be written
	 * @param x9type
	 *            current record type
	 */
	private void writeBofdEndorsement(final X9SdkIO sdkIO, final X9Item937 x9item937,
			final int x9type) {
		/*
		 * Populate record fields.
		 */
		final X9SdkObject sdkObject = sdkIO.startNewCsvRecord(x9type);
		final String[] record = x9factory.allocate(sdkObject);
		x9factory.set(record, x9recordFields.r26AddendumNumber, "1");
		x9factory.set(record, x9recordFields.r26BofdRouting, 123456780);
		x9factory.set(record, x9recordFields.r26BofdBusinessDate, currentDate);
		x9factory.set(record, x9recordFields.r26BofdItemSequenceNumber,
				x9item937.getItemSequenceNumber());
		x9factory.set(record, x9recordFields.r26BofdDepositAccountNumber, "");
		x9factory.set(record, x9recordFields.r26BofdDepositBranch, "");
		x9factory.set(record, x9recordFields.r26PayeeName, "");
		x9factory.set(record, x9recordFields.r26BofdTruncationIndicator, "Y");
		x9factory.set(record, x9recordFields.r26BofdConversionIndicator, "2");
		x9factory.set(record, x9recordFields.r26BofdCorrectionIndicator, "");
		x9factory.set(record, x9recordFields.r26UserField, "");

		/*
		 * Write the type 26 primary endorsement record.
		 */
		sdkIO.writeFromArray(sdkObject, record);
	}

	/**
	 * Create the image view detail record (type 50).
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9item937
	 *            current item to be written
	 * @param side_Indicator
	 *            check image side indicator (front or back)
	 * @param imageSize
	 *            check image size
	 */
	private void writeImageViewDetail(final X9SdkIO sdkIO, final X9Item937 x9item937,
			final String side_Indicator, final int imageSize) {
		/*
		 * Populate record fields.
		 */
		final X9SdkObject sdkObject = sdkIO.startNewCsvRecord(X9.IMAGE_VIEW_DETAIL);
		final String[] record = x9factory.allocate(sdkObject);
		x9factory.set(record, x9recordFields.r50ImageIndicator, "1");
		x9factory.set(record, x9recordFields.r50ImageCreatorRoutingNumber, x9item937.getRouting());
		x9factory.set(record, x9recordFields.r50ImageCreatorDate, currentDate);
		x9factory.set(record, x9recordFields.r50ImageViewFormatIndicator, "00");
		x9factory.set(record, x9recordFields.r50ImageViewCompressionAlgorithmIdentifier, "00");
		x9factory.set(record, x9recordFields.r50ImageViewDataSize, imageSize);
		x9factory.set(record, x9recordFields.r50ViewSideIndicator, side_Indicator);
		x9factory.set(record, x9recordFields.r50ViewDescriptor, "0");
		x9factory.set(record, x9recordFields.r50DigitalSignatureIndicator, "0");
		x9factory.set(record, x9recordFields.r50DigitalSignatureMethod, "");
		x9factory.set(record, x9recordFields.r50SecurityKeySize, "");
		x9factory.set(record, x9recordFields.r50StartOfProtectedData, "");
		x9factory.set(record, x9recordFields.r50LengthOfProtectedData, "");
		x9factory.set(record, x9recordFields.r50ImageRecreateIndicator, "0");
		x9factory.set(record, x9recordFields.r50TiffVarianceIndicator, "");
		x9factory.set(record, x9recordFields.r50ImageTestOverrideIndicator, "");

		/*
		 * Write the type 50 image view detail record.
		 */
		sdkIO.writeFromArray(sdkObject, record);
	}

	/**
	 * Create the image view data record (type 52).
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @param x9item937
	 *            current item to be written
	 * @param tiffImage
	 *            tiff image as byte array
	 */
	private void writeImageViewData(final X9SdkIO sdkIO, final X9Item937 x9item937,
			final byte[] tiffImage) {
		/*
		 * Populate record fields.
		 */
		final X9SdkObject sdkObject = sdkIO.startNewCsvRecord(X9.IMAGE_VIEW_DATA);
		final String[] record = x9factory.allocate(sdkObject);
		x9factory.set(record, x9recordFields.r52EceInstitutionRouting, originationRouting);
		x9factory.set(record, x9recordFields.r52BundleBusinessDate, currentDate);
		x9factory.set(record, x9recordFields.r52CycleNumber, "");
		x9factory.set(record, x9recordFields.r52EceInstituionItemSequenceNumber,
				x9item937.getItemSequenceNumber());
		x9factory.set(record, x9recordFields.r52SecurityOriginatorName, "");
		x9factory.set(record, x9recordFields.r52SecurityAuthenticatorName, "");
		x9factory.set(record, x9recordFields.r52SecurityKeyName, "");
		x9factory.set(record, x9recordFields.r52ClippingOrigin, "0");
		x9factory.set(record, x9recordFields.r52ClippingCoordinateH1, "");
		x9factory.set(record, x9recordFields.r52ClippingCoordinateH2, "");
		x9factory.set(record, x9recordFields.r52ClippingCoordinateV1, "");
		x9factory.set(record, x9recordFields.r52ClippingCoordinateV2, "");
		x9factory.set(record, x9recordFields.r52LengthOfImageReferenceKey, "0000");
		x9factory.set(record, x9recordFields.r52ImageReferenceKey, "");
		x9factory.set(record, x9recordFields.r52LengthOfDigitalSignature, "0");
		x9factory.set(record, x9recordFields.r52DigitalSignature, "");
		x9factory.set(record, x9recordFields.r52LengthOfImageData, "0"); // provided elsewhere
		x9factory.set(record, x9recordFields.r52ImageData, ""); // provided elsewhere

		/*
		 * Set the tiff image.
		 */
		sdkObject.setCheckImage(tiffImage);

		/*
		 * Write the type 52 image view data record.
		 */
		sdkIO.writeFromArray(sdkObject, record);
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
		LOGGER.info(X9CONSTRUCTX9 + " started");
		try {
			final X9ConstructX9 example = new X9ConstructX9();
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
