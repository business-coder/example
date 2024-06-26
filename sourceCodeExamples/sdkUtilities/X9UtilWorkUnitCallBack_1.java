package com.x9ware.utilities;

/**
 * X9UtilWorkUnitCallBack is a callback mechanism that allows feedback for completed work units to
 * be posted back to the utilities console.
 * 
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public interface X9UtilWorkUnitCallBack {

	/**
	 * Callback when a specific x9utilities work unit has been completed.
	 * 
	 * @param workUnit
	 *            work unit just completed
	 * @param totalSizeOfAllFiles
	 *            total (aggregate) size of all files being processed
	 */
	void workUnitCompleted(final X9UtilWorkUnit workUnit, final long totalSizeOfAllFiles);
}
