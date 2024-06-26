package com.x9ware.utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Endorsement;
import com.x9ware.base.X9FileReader;
import com.x9ware.base.X9Item937;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.core.X9;
import com.x9ware.core.X9Reader;
import com.x9ware.tools.X9Decimal;
import com.x9ware.types.X9Type25;
import com.x9ware.types.X9Type26;
import com.x9ware.types.X9Type28;
import com.x9ware.types.X9Type31;
import com.x9ware.types.X9Type32;
import com.x9ware.types.X9Type33;
import com.x9ware.types.X9Type35;
import com.x9ware.types.X9Type50;
import com.x9ware.types.X9Type52;
import com.x9ware.types.X9Type61;
import com.x9ware.types.X9Type62;

/**
 * X9UtilLoadItems is a static class that loads all items from an input x9.37 file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilLoadItems {

	/*
	 * Constants.
	 */
	private static final int ITEM_LIST_INITIAL_CAPACITY = 1000;

	/*
	 * Public.
	 */
	public static final boolean LOAD_937_CREDITS_ENABLED = true;
	public static final boolean LOAD_937_CREDITS_DISABLED = false;

	public static final boolean LOAD_937_IMAGES_ENABLED = true;
	public static final boolean LOAD_937_IMAGES_DISABLED = false;

	/**
	 * X9UtilLoadItems Constructor is private (static class).
	 */
	private X9UtilLoadItems() {
	}

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilLoadItems.class);

	/**
	 * Read an x9.37 file to build an item list.
	 *
	 * @param sdkBase
	 *            sdkBase for this environment
	 * @param inputFile
	 *            x9.37 input file
	 * @return constructed item list
	 */
	@SuppressWarnings("resource") // will be closed by lower level read937-sdkIO
	public static List<X9Item937> read937(final X9SdkBase sdkBase, final File inputFile) {
		return read937(sdkBase, X9FileReader.getNewChannelReader(inputFile),
				LOAD_937_CREDITS_DISABLED, LOAD_937_IMAGES_ENABLED);
	}

	/**
	 * Read an x9.37 file to build an item list.
	 *
	 * @param sdkBase
	 *            sdkBase for this environment
	 * @param inputFile
	 *            x9.37 input file
	 * @param isLoadCredits
	 *            true if credits are to be loaded otherwise false
	 * @param isLoadImages
	 *            true if images are to be loaded otherwise false
	 * @return constructed item list
	 */
	@SuppressWarnings("resource") // will be closed by lower level read937-sdkIO
	public static List<X9Item937> read937(final X9SdkBase sdkBase, final File inputFile,
			final boolean isLoadCredits, final boolean isLoadImages) {
		return read937(sdkBase, X9FileReader.getNewChannelReader(inputFile), isLoadCredits,
				isLoadImages);
	}

	/**
	 * Read an x9.37 file to build an item list for either a forward presentment or returns file.
	 * The created items will include their endorsements (addenda record types 26/28/32/35). The
	 * physical images can be optionally attached to the items when needed. Credits (types 61/62)
	 * can be optionally included when reading forward presentment files.
	 *
	 * @param sdkBase
	 *            sdkBase for this environment
	 * @param fileReader
	 *            file reader for the current input source
	 * @param isLoadCredits
	 *            true if credits are to be loaded otherwise false
	 * @param isLoadImages
	 *            true if images are to be loaded otherwise false
	 * @return constructed item list
	 */
	public static List<X9Item937> read937(final X9SdkBase sdkBase, final X9FileReader fileReader,
			final boolean isLoadCredits, final boolean isLoadImages) {
		/*
		 * Bind to the x9.37 configuration.
		 */
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}

		/*
		 * Read the x9.37 input reader and construct the item list.
		 */
		final List<X9Item937> itemList = new ArrayList<>(ITEM_LIST_INITIAL_CAPACITY);
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputReader(fileReader)) {
			/*
			 * Read until end of file.
			 */
			X9Item937 x9item937 = null;
			X9SdkObject sdkObject = null;
			int recordNumber = 0;
			while ((sdkObject = sdkIO.readNext()) != null) {
				/*
				 * Process by record type.
				 */
				recordNumber++;
				final int recordType = sdkObject.getRecordType();
				final byte[] dataRecord = sdkObject.getDataByteArray();
				switch (recordType) {

					case X9.CHECK_DETAIL: {
						final X9Type25 t25 = new X9Type25(sdkBase, dataRecord);
						x9item937 = new X9Item937(recordNumber, recordType, t25.payorBankRouting,
								t25.micrOnUs, t25.auxiliaryOnUs, t25.epc,
								X9Decimal.getAsAmount(t25.amount), t25.itemSequenceNumber);
						x9item937.setDocumentationTypeIndicator(t25.documentationTypeIndicator);
						x9item937.setReturnAcceptanceIndicator(t25.returnAcceptanceIndicator);
						x9item937.setMicrValidIndicator(t25.micrValidIndicator);
						x9item937.setBofdIndicator(t25.bofdIndicator);
						x9item937.setCorrectionIndicator(t25.correctionIndicator);
						x9item937.setArchiveTypeIndicator(t25.archiveTypeIndicator);
						itemList.add(x9item937);
						break;
					}

					case X9.RETURN_DETAIL: {
						final X9Type31 t31 = new X9Type31(sdkBase, dataRecord);
						x9item937 = new X9Item937(recordNumber, recordType, t31.payorBankRouting,
								t31.micrOnUs, t31.auxiliaryOnUs, t31.epc,
								X9Decimal.getAsAmount(t31.amount), t31.itemSequenceNumber);
						x9item937.setReturnReason(t31.returnReason);
						x9item937.setReturnNotificationIndicator(t31.returnNotificationIndicator);
						x9item937.setDocumentationTypeIndicator(
								t31.returnDocumentationTypeIndicator);
						x9item937.setArchiveTypeIndicator(t31.returnArchiveTypeIndicator);
						itemList.add(x9item937);
						break;
					}

					case X9.CREDIT_RECONCILIATION: {
						if (isLoadCredits) {
							final X9Type61 t61 = new X9Type61(sdkBase, dataRecord);
							x9item937 = new X9Item937(recordNumber, recordType,
									t61.payorBankRouting, t61.micrOnUs, t61.auxiliaryOnUs, t61.epc,
									X9Decimal.getAsAmount(t61.amount), t61.itemSequenceNumber);
							x9item937.setDocumentationTypeIndicator(t61.documentationTypeIndicator);
							itemList.add(x9item937);
						}
						break;
					}

					case X9.CREDIT: {
						if (isLoadCredits) {
							final X9Type62 t62 = new X9Type62(sdkBase, dataRecord);
							x9item937 = new X9Item937(recordNumber, recordType,
									t62.postingBankRouting, t62.micrOnUs, t62.auxiliaryOnUs,
									t62.epc, X9Decimal.getAsAmount(t62.amount),
									t62.itemSequenceNumber);
							x9item937.setDocumentationTypeIndicator(t62.documentationTypeIndicator);
							itemList.add(x9item937);
						}
						break;
					}

					case X9.CHECK_ADDENDUM_A: {
						if (x9item937 == null) {
							throw X9Exception.abort(
									"type 26 without previous item at recordNumber({})",
									recordNumber);
						}

						final X9Type26 t26 = new X9Type26(sdkBase, dataRecord);
						final X9Endorsement x9endorsement = new X9Endorsement(recordType,
								t26.addendumNumber, t26.routingNumber, t26.endorsementDate,
								t26.itemSequenceNumber, t26.truncationIndicator,
								t26.conversionIndicator, t26.correctionIndicator, "", "");
						x9endorsement.setDepositAccountNumber(t26.depositAccountNumber);
						x9endorsement.setDepositBranch(t26.depositBranch);
						x9endorsement.setUserField(t26.userField);
						x9item937.addEndorsement(x9endorsement);
						break;
					}

					case X9.CHECK_ADDENDUM_C: {
						if (x9item937 == null) {
							throw X9Exception.abort(
									"type 28 without previous item at recordNumber({})",
									recordNumber);
						}
						final X9Type28 t28 = new X9Type28(sdkBase, dataRecord);
						final X9Endorsement x9endorsement = new X9Endorsement(recordType,
								t28.addendumNumber, t28.routingNumber, t28.endorsementDate,
								t28.itemSequenceNumber, t28.truncationIndicator,
								t28.conversionIndicator, t28.correctionIndicator, t28.returnReason,
								t28.endorsingBankIdentifier);
						x9endorsement.setUserField(t28.userField);
						x9item937.addEndorsement(x9endorsement);
						break;
					}

					case X9.RETURN_ADDENDUM_A: {
						if (x9item937 == null) {
							throw X9Exception.abort(
									"type 32 without previous item at recordNumber({})",
									recordNumber);
						}
						final X9Type32 t32 = new X9Type32(sdkBase, dataRecord);
						final X9Endorsement x9endorsement = new X9Endorsement(recordType,
								t32.addendumNumber, t32.routingNumber, t32.endorsementDate,
								t32.itemSequenceNumber, t32.truncationIndicator,
								t32.conversionIndicator, t32.correctionIndicator, "", "");
						x9endorsement.setDepositAccountNumber(t32.depositAccountNumber);
						x9endorsement.setDepositBranch(t32.depositBranch);
						x9endorsement.setUserField(t32.userField);
						x9item937.addEndorsement(x9endorsement);
						break;
					}

					case X9.RETURN_ADDENDUM_B: {
						if (x9item937 == null) {
							throw X9Exception.abort(
									"type 33 without previous item at recordNumber({})",
									recordNumber);
						}
						final X9Type33 t33 = new X9Type33(sdkBase, dataRecord);
						x9item937.setPayorBankName(t33.bankName);
						x9item937.setPayorAccountName(t33.accountName);
						break;
					}

					case X9.RETURN_ADDENDUM_D: {
						if (x9item937 == null) {
							throw X9Exception.abort(
									"type 35 without previous item at recordNumber({})",
									recordNumber);
						}
						final X9Type35 t35 = new X9Type35(sdkBase, dataRecord);
						final X9Endorsement x9endorsement = new X9Endorsement(recordType,
								t35.addendumNumber, t35.routingNumber, t35.endorsementDate,
								t35.itemSequenceNumber, t35.truncationIndicator,
								t35.conversionIndicator, t35.correctionIndicator, t35.returnReason,
								t35.endorsingBankIdentifier);
						x9endorsement.setUserField(t35.userField);
						x9item937.addEndorsement(x9endorsement);
						break;
					}

					case X9.IMAGE_VIEW_DETAIL: {
						if (x9item937 == null) {
							throw X9Exception.abort(
									"type 50 without previous item at recordNumber({})",
									recordNumber);
						}
						final X9Type50 x9type50 = new X9Type50(sdkBase, dataRecord);
						x9item937.setImageCreatorDate(x9type50.creatorDate);
						x9item937.setImageCreatorRouting(x9type50.routingNumber);
						break;
					}

					case X9.IMAGE_VIEW_DATA: {
						final boolean isFrontImage = sdkObject.isFrontImage();
						final byte[] tiffArray = sdkObject.getCheckImage();
						if (x9item937 == null) {
							throw X9Exception.abort(
									"type 52 without previous item at recordNumber({})",
									recordNumber);
						}
						if (isFrontImage) {
							x9item937.setFrontImage(tiffArray);
							final X9Type52 x9type52 = new X9Type52(sdkBase, dataRecord);
							x9item937.setImageReferenceKey(x9type52.imageReferenceKey);
						} else {
							x9item937.setBackImage(tiffArray);
						}
						break;
					}

					case X9.FILE_HEADER:
					case X9.CASH_LETTER_HEADER:
					case X9.BUNDLE_HEADER:
					case X9.BUNDLE_TRAILER:
					case X9.CASH_LETTER_TRAILER:
					case X9.FILE_CONTROL_TRAILER: {
						x9item937 = null;
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
		 * Log the number of items that have been loaded.
		 */
		LOGGER.info("input itemsLoaded({})", itemList.size());

		/*
		 * Return the constructed list of all items.
		 */
		return itemList;
	}

	/**
	 * Get the total debit amount for all items within an x9.37 item list.
	 *
	 * @param itemList
	 *            current item list
	 * @return total debit amount
	 */
	public static long getTotalDebitAmount937(final List<X9Item937> itemList) {
		long totalDebitAmount = 0;
		for (final X9Item937 x9item937 : itemList) {
			if (x9item937.getRecordType() == X9.CHECK_DETAIL) {
				totalDebitAmount += x9item937.getAmountAsLong();
			}
		}
		return totalDebitAmount;
	}

}
