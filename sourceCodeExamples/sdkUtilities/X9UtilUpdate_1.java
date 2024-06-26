package com.x9ware.utilities;

import java.io.File;

import com.x9ware.base.X9SdkBase;
import com.x9ware.create.X9Update;

/**
 * X9UtilUpdate is part of our utilities package which reads an x9 file and updates fields per a
 * user supplied XML document. For each input field, the xml definition allows one or more
 * match-replace values to be applied, which allows different value assignments depending on the
 * current content. The match-replace strategy can be based on a single value, a list of possible
 * values, regex search, lookback to an earlier record, or a formulated lookup key applied against
 * an external properties file. Replacement values are logged as they are applied to the output.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilUpdate {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/*
	 * Private.
	 */
	private final boolean isLoggingEnabled;

	/*
	 * X9UtilUpdate Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilUpdate(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
	}

	/**
	 * Update an existing x9 file. We have an x9 input file, an x9 output file, a parameters xml
	 * file, and a results csv file that describes the field level swaps that were applied.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Allocate an update instance and invoke update. All of the work is done within the sdk.
		 */
		final File inputFile = workUnit.inputFile;
		final File outputFile = workUnit.outputFile;
		final File updateXmlFile = workUnit.secondaryFile;
		final File resultsFile = workUnit.resultsFile;
		final X9Update x9update = new X9Update(sdkBase, updateXmlFile, resultsFile,
				isLoggingEnabled);
		final int exitStatus = x9update.updateProcessor(inputFile, outputFile);

		/*
		 * Write summary totals when requested by command line switches.
		 */
		workUnit.writeSummaryTotals(x9update.getXmlTotals());

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

}
