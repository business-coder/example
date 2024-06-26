package com.x9ware.utilities;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import com.x9ware.tools.X9CsvLine;

/**
 * X9UtilWriterCsvLines defines an individual list of csv lines stored by X9UtilWriter. Each such
 * list can be optionally associated with a batch profile name. These lists allow the csv input to
 * be grouped and reordered. For example, a given batch profile name can be associated with multiple
 * lists, where each list represents an individual deposit. This allows deposits to be constructed
 * per more complex rules. Remember that these csv lines can be more then just items, since they can
 * be other line types (headerXml, imageFolder, paidStamp, image, etc).
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilWriterCsvLines extends ArrayList<X9CsvLine> {

	/*
	 * Private.
	 */
	private final String profileName;

	/*
	 * Constants.
	 */
	private static final int INITIAL_ROW_COUNT = 1000;

	/**
	 * X9UtilWriterCsvLines Constructor.
	 *
	 * @param profile_Name
	 *            profile name
	 */
	public X9UtilWriterCsvLines(final String profile_Name) {
		super(INITIAL_ROW_COUNT);
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

}
