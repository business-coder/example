package com.x9ware.utilities;

import org.apache.commons.lang3.StringUtils;

/**
 * X9UtilWriterProfile anchors information regarding each unique batch profile name encountered.
 * 
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilWriterProfile {

	/*
	 * Private.
	 */
	private final String profileName;
	private int depositCount;
	private int itemCount;

	/**
	 * X9UtilWriterProfile Constructor.
	 * 
	 * @param profile_Name
	 *            profile name
	 */
	public X9UtilWriterProfile(final String profile_Name) {
		profileName = StringUtils.isNotBlank(profile_Name) ? profile_Name : "";
	}

	/**
	 * Get the profile name.
	 *
	 * @return profile name
	 */
	public String getProfileName() {
		return profileName;
	}

	public int getDepositCount() {
		return depositCount;
	}

	/**
	 * Start a new deposit.
	 * 
	 * @return number of deposits for this profile name.
	 */
	public int startNewDeposit() {
		itemCount = 1;
		return ++depositCount;
	}

	/**
	 * Get the current item count.
	 * 
	 * @return item count
	 */
	public int getItemCount() {
		return itemCount;
	}

	/**
	 * Increment the current item count.
	 * 
	 * @return incremented item count
	 */
	public int incrementItemCount() {
		return ++itemCount;
	}

}
