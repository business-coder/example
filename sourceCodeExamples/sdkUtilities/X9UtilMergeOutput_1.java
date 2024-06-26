package com.x9ware.utilities;

import java.util.List;

import com.x9ware.tools.X9File;
import com.x9ware.tools.X9TempFile;

/**
 * X9UtilMergeOutput defines a single output file that is created by merge. Each instance includes
 * the output file itself, along with an array of the input files that are written to this output.
 * 
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilMergeOutput {

	/*
	 * Private.
	 */
	private final List<X9File> inputFileList;
	private X9TempFile outputFile;

	/*
	 * X9UtilMergeOutput Constructor.
	 *
	 * @param input_FileList list of files that are written to this output file
	 */
	public X9UtilMergeOutput(final List<X9File> input_FileList) {
		inputFileList = input_FileList;
	}

	/**
	 * Get the input file list that is associated with this output file.
	 * 
	 * @return input file list
	 */
	public List<X9File> getInputFileList() {
		return inputFileList;
	}

	/**
	 * Get the number of input files that is associated with this output file.
	 * 
	 * @return number of input files
	 */
	public int getInputFileCount() {
		return inputFileList.size();
	}

	/**
	 * Get the output file that is associated with this output file..
	 * 
	 * @return output file
	 */
	public X9TempFile getOutputFile() {
		return outputFile;
	}

	/**
	 * Set the output file that is associated with this output file.
	 * 
	 * @param output_File
	 *            output file
	 */
	public void setOutputFile(final X9TempFile output_File) {
		outputFile = output_File;
	}

}
