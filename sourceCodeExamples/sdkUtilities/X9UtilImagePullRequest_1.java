package com.x9ware.utilities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

/**
 * X9UtilImagePullRequest defines a single image pull request.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilImagePullRequest {

	/*
	 * Private.
	 */
	private final int csvLineNumber;
	private final String itemSequenceNumber;
	private final BigDecimal itemAmount;
	private final LocalDate itemDate;
	private final String itemRouting;
	private final String itemAccount;
	private final String itemSerial;
	private int errorCondition = ERROR_NONE;
	private final String[] csvOutputArray = new String[FIELD_COUNT];

	/*
	 * Csv output fields.
	 */
	public static int outputFieldNumber = 0;
	public static final int X9_FILE_NAME = outputFieldNumber++;
	public static final int ITEM_SEQUENCE_NUMBER = outputFieldNumber++;
	public static final int FRONT_IMAGE = outputFieldNumber++;
	public static final int BACK_IMAGE = outputFieldNumber++;
	public static final int RECORD_TYPE = outputFieldNumber++;
	public static final int RECORD_NUMBER = outputFieldNumber++;
	public static final int AUX_ONUS = outputFieldNumber++;
	public static final int EPC = outputFieldNumber++;
	public static final int PAYOR_ROUTING = outputFieldNumber++;
	public static final int PAYOR_ROUTING_CHECK_DIGIT = outputFieldNumber++;
	public static final int ONUS = outputFieldNumber++;
	public static final int AMOUNT = outputFieldNumber++;
	public static final int BOFD_INDICATOR = outputFieldNumber++;
	public static final int RETURN_LOCATION_ROUTING = outputFieldNumber++;
	public static final int BOFD_DATE = outputFieldNumber++;
	public static final int BOFD_ROUTING = outputFieldNumber++;
	public static final int IMAGE_CREATOR_ROUTING = outputFieldNumber++;
	public static final int IMAGE_CREATOR_DATE = outputFieldNumber++;
	public static final int RETURN_REASON = outputFieldNumber++;
	public static final int FIELD_COUNT = outputFieldNumber;

	/*
	 * Constants.
	 */
	public static final int ERROR_NONE = -1;
	public static final int ERROR_FILE_NOT_FOUND = 0;
	public static final int ERROR_ITEM_NOT_FOUND = 1;
	public static final int ERROR_ITEM_NO_IMAGE = 2;
	public static final int ERROR_FILE_ABORTED = 3;
	private static final String[] ERROR_CONDITION_NAMES = { "file-not-found", "item-not-found",
			"item-no-image", "file-aborted" };

	/**
	 * X9UtilImagePullRequest Constructor.
	 *
	 * @param csv_LineNumber
	 *            csv line number
	 * @param fileName
	 *            x9 file name
	 * @param item_SequenceNumber
	 *            item sequence number
	 * @param item_Amount
	 *            item amount
	 * @param item_Date
	 *            item date
	 * @param item_Routing
	 *            item routing
	 * @param item_Account
	 *            item account number
	 * @param item_Serial
	 *            item serial number
	 */
	public X9UtilImagePullRequest(final int csv_LineNumber, final String fileName,
			final String item_SequenceNumber, final BigDecimal item_Amount,
			final LocalDate item_Date, final String item_Routing, final String item_Account,
			final String item_Serial) {
		csvLineNumber = csv_LineNumber;
		itemSequenceNumber = item_SequenceNumber;
		itemAmount = item_Amount;
		itemDate = item_Date;
		itemRouting = item_Routing;
		itemAccount = item_Account;
		itemSerial = item_Serial;
		Arrays.fill(csvOutputArray, "");
		setOutputEntry(X9_FILE_NAME, fileName);
		setOutputEntry(ITEM_SEQUENCE_NUMBER, itemSequenceNumber);
	}

	/**
	 * Get the csv line number.
	 *
	 * @return csv line number
	 */
	public int getCsvLineNumber() {
		return csvLineNumber;
	}

	/**
	 * Get the item sequence number.
	 *
	 * @return item sequence number
	 */
	public String getItemSequenceNumber() {
		return itemSequenceNumber;
	}

	/**
	 * Get the item amount.
	 *
	 * @return item amount
	 */
	public BigDecimal getItemAmount() {
		return itemAmount;
	}

	/**
	 * Get the item date.
	 *
	 * @return item date
	 */
	public LocalDate getItemDate() {
		return itemDate;
	}

	/**
	 * Get the item routing.
	 *
	 * @return item routing
	 */
	public String getItemRouting() {
		return itemRouting;
	}

	/**
	 * Get the item account.
	 *
	 * @return item account
	 */
	public String getItemAccount() {
		return itemAccount;
	}

	/**
	 * Get the item serial.
	 *
	 * @return item serial
	 */
	public String getItemSerial() {
		return itemSerial;
	}

	/**
	 * Get the csv output array.
	 *
	 * @return csv output array
	 */
	public String[] getOutputArray() {
		return csvOutputArray;
	}

	/**
	 * Determine if a value is empty within the csv output array by index.
	 *
	 * @param index
	 *            array index
	 * @return true or false
	 */
	public boolean isOutputEmpty(final int index) {
		return StringUtils.isBlank(csvOutputArray[index]);
	}

	/**
	 * Get a value within the csv output array by index.
	 *
	 * @param index
	 *            array index
	 * @return array value at the specific index
	 */
	public String getOutputEntry(final int index) {
		return csvOutputArray[index];
	}

	/**
	 * Set a value within the csv output array by index.
	 *
	 * @param index
	 *            array index
	 * @param newValue
	 *            new value to be assigned
	 */
	public void setOutputEntry(final int index, final String newValue) {
		final String value = newValue == null ? "" : newValue.trim();
		csvOutputArray[index] = value;
	}

	/**
	 * Determine if this request is marked as successful.
	 *
	 * @return true or false
	 */
	public boolean isMarkedAsSuccessful() {
		return errorCondition < 0;
	}

	/**
	 * Get error condition.
	 *
	 * @return error condition
	 */
	public int getErrorCondition() {
		return errorCondition;
	}

	/**
	 * Get the error condition name for this request.
	 *
	 * @return error condition name
	 */
	public String getErrorConditionName() {
		return errorCondition >= 0 ? ERROR_CONDITION_NAMES[errorCondition] : "";
	}

	/**
	 * Set error condition and update the image names in the output array when not yet set.
	 *
	 * @param error_Condition
	 *            error condition
	 */
	public void setErrorCondition(final int error_Condition) {
		errorCondition = error_Condition;
		if (StringUtils.isBlank(csvOutputArray[FRONT_IMAGE])) {
			csvOutputArray[FRONT_IMAGE] = ERROR_CONDITION_NAMES[errorCondition];
		}
		if (StringUtils.isBlank(csvOutputArray[BACK_IMAGE])) {
			csvOutputArray[BACK_IMAGE] = ERROR_CONDITION_NAMES[errorCondition];
		}
	}

}
