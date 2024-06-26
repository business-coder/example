package com.x9ware.utilities;

import java.util.TreeMap;

import com.x9ware.tools.X9CsvLine;

/**
 * X9UtilWriterProfileMap defines a map which reorders the incoming csv lines used by X9UtilWriter.
 * The map key can be the profile name (when reordered) or blank (when not reordered).
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilWriterProfileMap extends TreeMap<String, X9UtilWriterCsvLines> {

	/**
	 * X9UtilWriterProfileMap Constructor.
	 */
	public X9UtilWriterProfileMap() {
	}

	/**
	 * Add a new line to the profile map.
	 *
	 * @param mapKey
	 *            map key
	 * @param profileName
	 *            profile name
	 * @param csvLine
	 *            csv line to be added
	 */
	public void addNewLine(final String mapKey, final String profileName, final X9CsvLine csvLine) {
		X9UtilWriterCsvLines csvLines = this.get(mapKey);
		if (csvLines == null) {
			csvLines = new X9UtilWriterCsvLines(profileName);
			this.put(mapKey, csvLines);
		}
		csvLines.add(csvLine);
	}

}
