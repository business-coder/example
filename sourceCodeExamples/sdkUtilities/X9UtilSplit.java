package com.x9ware.utilities;

import java.io.File;

import com.x9ware.base.X9SdkBase;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.create.X9Split;

/**
 * X9UtilSplit is part of our utilities package which reads an x9 file and splits it into multiple
 * x9 output files. This is accomplished by constructing a split key using the fields defined within
 * a user supplied XML document. There will be a variable number of output files (segments), where
 * logical split keys are mapped to these segments. A segment can be defined as non-writable, which
 * means that the those items are consumed but not physically written. An optional default output
 * segment can be defined as a catch-all for items that are otherwise not addressed by the split
 * criteria. Finally, the fields participating in the split can be defined with a replacement value,
 * allowing those fields to be updated as part of the split process. For example, you can split on
 * destination routing and then modify that field to an alternative value as part of the split.
 * Replacement values are logged and then applied as each output split file is created. On
 * completion, totals for all output segments are written to the log.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilSplit {

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
	 * X9UtilSplit Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilSplit(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
	}

	/**
	 * Split an existing x9 file. We have an x9 input file which will be split to multiple x9 output
	 * files, per an xml definition which defines the field values that participate in the split.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Allocate a split instance and invoke split. All of the work is done within the sdk.
		 */
		final File inputFile = workUnit.inputFile;
		final File outputFolder = workUnit.outputFile;
		final File splitXmlFile = workUnit.secondaryFile;
		final File resultsFile = workUnit.resultsFile;
		final X9Split x9split = new X9Split(sdkBase, splitXmlFile, resultsFile, isLoggingEnabled);
		final int exitStatus = x9split.splitProcessor(inputFile, outputFolder);

		/*
		 * Write summary totals when requested by command line switches.
		 */
		for (final X9TotalsXml x9totalsXml : x9split.getXmlTotalList()) {
			workUnit.writeSummaryTotals(x9totalsXml);
		}

		/*
		 * Return our exit status.
		 */
		return exitStatus;
	}

}
