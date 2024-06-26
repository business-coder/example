package com.x9ware.utilities;

import java.io.File;

/**
 * X9UtilMergeFailed defines a failed input file that was not processed by merge. Each instance
 * includes the input file along with a reason as to why the file was rejected.
 * 
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilMergeFailed {

	/*
	 * Private.
	 */
	private final File failedFile;
	private final String failedReason;

	/*
	 * X9UtilMergeFailed Constructor.
	 *
	 * @param input_FileList list of files that are written to this output file
	 */
	public X9UtilMergeFailed(final File failed_File, final String failed_Reason) {
		failedFile = failed_File;
		failedReason = failed_Reason;
	}

	/**
	 * Get the failed file.
	 * 
	 * @return failed file
	 */
	public File getFailedFile() {
		return failedFile;
	}

	/**
	 * Get the failed reason.
	 * 
	 * @return failed reason
	 */
	public String getFailedReason() {
		return failedReason;
	}

}
