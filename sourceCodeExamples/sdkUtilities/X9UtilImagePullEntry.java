package com.x9ware.utilities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.tools.X9String;

/**
 * X9UtilImagePullEntry defines all image pull requests for a single file as processed by a worker
 * task. There may be just one pull request for a given file, or there may be a very large number of
 * requests. This approach allows the image pull requests to be grouped within a single entry,
 * allowing them to be processed in a single pass of the associated file. We also store the input
 * file name here since it is common to all requests which are stored in the entry map.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilImagePullEntry {

	/*
	 * Private.
	 */
	private final Map<String, X9UtilImagePullRequest> imagePullMap = new HashMap<>();
	private final String x9fileName;
	private boolean hasSpecificRequestEntries;
	private int itemsPulled;
	private int itemsNotFound;
	private int filesNotFound;
	private int filesAborted;

	/*
	 * Constants.
	 */
	private static final char PIPE = '|';

	/**
	 * X9UtilImagePullEntry Constructor.
	 *
	 * @param x9_fileName
	 *            current x9 file name
	 */
	public X9UtilImagePullEntry(final String x9_fileName) {
		x9fileName = x9_fileName;
	}

	/**
	 * Get the entry map using the provided parameters.
	 *
	 * @param itemSequenceNumber
	 *            current item sequence number
	 * @param itemAmount
	 *            current item amount
	 * @param itemDate
	 *            current item date
	 * @param itemRouting
	 *            current item routing
	 * @param itemAccount
	 *            current item account
	 * @param itemSerial
	 *            current item check serial number
	 * @return map entry or null when not found
	 */
	public X9UtilImagePullRequest getEntryMap(final String itemSequenceNumber,
			final BigDecimal itemAmount, final LocalDate itemDate, final String itemRouting,
			final String itemAccount, final String itemSerial) {
		String mapKey;
		final X9UtilImagePullRequest pullRequest;
		final String lookupSequenceNumber = X9String.removeLeadingZeroes(itemSequenceNumber);
		if (imagePullMap.containsKey(
				mapKey = buildMapKey(lookupSequenceNumber, itemAmount, null, null, null, null))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap.containsKey(
				mapKey = buildMapKey(lookupSequenceNumber, null, itemDate, null, null, null))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap.containsKey(
				mapKey = buildMapKey(lookupSequenceNumber, null, null, null, null, null))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap.containsKey(mapKey = buildMapKey(null, itemAmount, itemDate,
				itemRouting, itemAccount, itemSerial))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap.containsKey(mapKey = buildMapKey(null, itemAmount, null,
				itemRouting, itemAccount, itemSerial))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap.containsKey(
				mapKey = buildMapKey(null, null, null, itemRouting, itemAccount, itemSerial))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap.containsKey(
				mapKey = buildMapKey(null, null, null, itemRouting, itemAccount, null))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap
				.containsKey(mapKey = buildMapKey(null, null, null, itemRouting, null, null))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap.containsKey(
				mapKey = buildMapKey(null, null, null, null, itemAccount, itemSerial))) {
			pullRequest = imagePullMap.get(mapKey);
		} else if (imagePullMap
				.containsKey(mapKey = buildMapKey(null, null, null, null, itemAccount, null))) {
			pullRequest = imagePullMap.get(mapKey);
		} else {
			pullRequest = null;
		}
		return pullRequest;
	}

	/**
	 * Get the image pull map.
	 *
	 * @return image pull map
	 */
	public Map<String, X9UtilImagePullRequest> getPullMap() {
		return imagePullMap;
	}

	/**
	 * Get the image pull map size.
	 *
	 * @return map size
	 */
	public int getMapSize() {
		return imagePullMap.size();
	}

	/**
	 * Put the entry to the map using the provided parameters.
	 *
	 * @param pullRequest
	 *            pull request entry be added to the map
	 * @return true when added otherwise false
	 */
	public boolean putMapEntry(final X9UtilImagePullRequest pullRequest) {
		/*
		 * Add this request to the map.
		 */
		final String lookupSequenceNumber = X9String
				.removeLeadingZeroes(pullRequest.getItemSequenceNumber());
		final String mapKey = buildMapKey(lookupSequenceNumber, pullRequest.getItemAmount(),
				pullRequest.getItemDate(), pullRequest.getItemRouting(),
				pullRequest.getItemAccount(), pullRequest.getItemSerial());
		final boolean isAddedToMap;
		if (imagePullMap.containsKey(mapKey)) {
			isAddedToMap = false;
		} else {
			isAddedToMap = true;
			imagePullMap.put(mapKey, pullRequest);
		}

		/*
		 * Turn on a running flag which indicates the map has specific item entries, which means
		 * that one or more pull requests include the item sequence number.
		 */
		hasSpecificRequestEntries = hasSpecificRequestEntries
				|| StringUtils.isNotBlank(lookupSequenceNumber);

		/*
		 * Return true when this request was added and false when it was a duplicate.
		 */
		return isAddedToMap;
	}

	/**
	 * Get the x9 file name.
	 *
	 * @return x9 file name
	 */
	public String getX9fileName() {
		return x9fileName;
	}

	/**
	 * Determine if the map contains item specific entries.
	 *
	 * @return true or false
	 */
	public boolean isMapSpecific() {
		return hasSpecificRequestEntries;
	}

	/**
	 * Determine if the map contains all generic item entries .
	 *
	 * @return true or false
	 */
	public boolean isMapGeneric() {
		return !hasSpecificRequestEntries;
	}

	/**
	 * Get the items pulled count.
	 *
	 * @return items pulled
	 */
	public int getItemsPulled() {
		return itemsPulled;
	}

	/**
	 * Increment the items pulled count.
	 */
	public void incrementItemsPulled() {
		itemsPulled++;
	}

	/**
	 * Get the items not found count.
	 *
	 * @return items not found
	 */
	public int getItemsNotFound() {
		return itemsNotFound;
	}

	/**
	 * Increment the items not found count.
	 */
	public void incrementItemsNotFound() {
		itemsNotFound++;
	}

	/**
	 * Get the files not found count.
	 *
	 * @return files not found
	 */
	public int getFilesNotFound() {
		return filesNotFound;
	}

	/**
	 * Increment the files not found count.
	 */
	public void incrementFilesNotFound() {
		filesNotFound++;
	}

	/**
	 * Get the files aborted count.
	 *
	 * @return files aborted
	 */
	public int getFilesAborted() {
		return filesAborted;
	}

	/**
	 * Increment the files aborted count.
	 */
	public void incrementFilesAborted() {
		filesAborted++;
	}

	/**
	 * Build the map key for a given batch from a string of values.
	 *
	 * @param itemSequence
	 *            current item sequence number
	 * @param itemAmount
	 *            current item amount
	 * @param itemDate
	 *            current item date
	 * @param itemRouting
	 *            current item routing
	 * @param itemAccount
	 *            current item account
	 * @param itemSerial
	 *            current item check serial number
	 * @return map key
	 */
	private String buildMapKey(final String itemSequence, final BigDecimal itemAmount,
			final LocalDate itemDate, final String itemRouting, final String itemAccount,
			final String itemSerial) {
		final String sequenceStr = StringUtils.isNotBlank(itemSequence) ? itemSequence.trim() : "";
		final String amountStr = itemAmount == null ? "" : X9Decimal.getStringValue(itemAmount);
		final String dateStr = itemDate == null ? "" : X9LocalDate.formatDateAsYYYYMMDD(itemDate);
		final String routingStr = StringUtils.isNotBlank(itemRouting) ? itemRouting.trim() : "";
		final String accountStr = StringUtils.isNotBlank(itemAccount) ? itemAccount.trim() : "";
		final String serialStr = StringUtils.isNotBlank(itemSerial) ? itemSerial.trim() : "";
		return sequenceStr + PIPE + amountStr + PIPE + dateStr + PIPE + routingStr + PIPE
				+ accountStr + PIPE + serialStr;
	}

}
