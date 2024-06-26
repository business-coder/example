package com.x9ware.utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.error.X9Error;
import com.x9ware.licensing.X9License;
import com.x9ware.tools.X9CommandLine;

/**
 * X9UtilWorkUnitList represents a list of one or more batch work units to be executed by
 * x9utilities. We contain the list itself, along with various associated attributes. The work unit
 * list can be created in one of several ways. It might be the contents of a text file of commands
 * to be executed, the expansion of a single command applies to all files located within a folder
 * structure, or a list that is dynamically created by an sdk application.
 * 
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilWorkUnitList {

	/*
	 * Private.
	 */
	private final String programName;
	private final X9License x9license;
	private final List<X9UtilWorkUnit> workUnitList = new ArrayList<>();
	private final X9CommandLine x9commandLine;
	private final AtomicInteger abortCount = new AtomicInteger(0);
	private final int abortLimit;
	private X9UtilWorkUnitCallBack workUnitCallBack;
	private long totalSizeOfAllFiles;

	/*
	 * Exit status is initialized as zero. It can become negative when any of the invoked work units
	 * abort. We cannot initialize as aborted since that assignment will never be overcome.
	 */
	private int exitStatus = X9UtilBatch.EXIT_STATUS_ZERO;

	/*
	 * Constants.
	 */
	public static final int ALLOWED_EXCEPTIONS_ARE_UNLIMITED = -1;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilWorkUnitList.class);

	/**
	 * X9UtilWorkUnitList Constructor.
	 * 
	 * @param x9command_Line
	 *            command line instance
	 * @param program_Name
	 *            program name (X9SDK or X9Utilities) for this runtime environment
	 * @param x9_license
	 *            license for this runtime environment
	 */
	public X9UtilWorkUnitList(final X9CommandLine x9command_Line, final String program_Name,
			final X9License x9_license) {
		this(x9command_Line, program_Name, x9_license, ALLOWED_EXCEPTIONS_ARE_UNLIMITED);
	}

	/**
	 * X9UtilWorkUnitList Constructor.
	 * 
	 * @param x9command_Line
	 *            command line instance
	 * @param program_Name
	 *            program name (X9SDK or X9Utilities) for this runtime environment
	 * @param x9_license
	 *            license for this runtime environment
	 * @param abort_Limit
	 *            abort limit which is minus one when unlimited
	 */
	public X9UtilWorkUnitList(final X9CommandLine x9command_Line, final String program_Name,
			final X9License x9_license, final int abort_Limit) {
		/*
		 * The work unit list contains the high level (invoking) command line that was used to
		 * initiate batch processing. This will be the command line that contains the "-batch" or
		 * "-script" switch. Each work unit will then contain the command line that was formulated
		 * against the model, using substitution, using the input file for that specific work unit.
		 */
		x9commandLine = x9command_Line;
		programName = program_Name;
		x9license = x9_license;
		abortLimit = abort_Limit;
	}

	/**
	 * Get the invoking command line.
	 * 
	 * @return invoking command line
	 */
	public X9CommandLine getCommandLine() {
		return x9commandLine;
	}

	/**
	 * Set our optional work unit completion callback, which is used to interactively monitor the
	 * execution of the underlying work units via a progress bar. This is used by the utilities
	 * console when running in batch mode, to provide the console user with high level feedback as
	 * work is being processed. There is no perfect way to monitor the progress. It would seem that
	 * using the aggregate size of all input files is better than just tracking the number of work
	 * units completed, since it would give more weight to larger files versus smaller files. This
	 * is especially important for the more intensive operations such as export and scrub. The
	 * caller should use try-catch-finally to ensure that the progress bar is always closed.
	 * 
	 * @param work_UnitCallBack
	 *            work unit completion call back
	 */
	public void setWorkUnitCompletionCallBack(final X9UtilWorkUnitCallBack work_UnitCallBack) {
		workUnitCallBack = work_UnitCallBack;
		totalSizeOfAllFiles = 0;
		for (final X9UtilWorkUnit workUnit : workUnitList) {
			final File inputFile = workUnit.inputFile;
			if (inputFile != null) {
				totalSizeOfAllFiles += inputFile.length();
			}
		}
	}

	/**
	 * Post work unit as completed back to the utilities console.
	 * 
	 * @param workUnit
	 *            work unit just completed
	 */
	public void postCompletedWorkUnit(final X9UtilWorkUnit workUnit) {
		if (workUnitCallBack != null) {
			final File inputFile = workUnit.inputFile;
			if (inputFile != null) {
				/*
				 * Post this work unit as completed.
				 */
				workUnitCallBack.workUnitCompleted(workUnit, totalSizeOfAllFiles);
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug(
							"postCompletedWorkUnit sizeOfThisFile({}) "
									+ "totalSizeOfAllFiles({}) inputFile({})",
							inputFile.length(), totalSizeOfAllFiles, inputFile);
				}
			}
		}
	}

	/**
	 * Get the program name.
	 * 
	 * @return program name
	 */
	public String getProgramName() {
		return programName;
	}

	/**
	 * Get the user license.
	 * 
	 * @return user license
	 */
	public X9License getUserLicense() {
		return x9license;
	}

	/**
	 * Get the abort limit.
	 * 
	 * @return abort limit
	 */
	public int getAbortLimit() {
		return abortLimit;
	}

	/**
	 * Get the abort count.
	 * 
	 * @return abort count
	 */
	public int getAbortCount() {
		return abortCount.get();
	}

	/**
	 * Increment the abort count.
	 * 
	 * @return incremented abort count
	 */
	public synchronized int incrementAbortCount() {
		return abortCount.incrementAndGet();
	}

	/**
	 * Get the work unit size.
	 * 
	 * @return work unit size
	 */
	public int getWorkUnitCount() {
		return workUnitList.size();
	}

	/**
	 * Get the first work unit or null when the list is empty.
	 * 
	 * @return first work unit
	 */
	public X9UtilWorkUnit getFirstWorkUnit() {
		return workUnitList.size() == 0 ? null : workUnitList.get(0);
	}

	/**
	 * Get the work unit list.
	 * 
	 * @return work units as a list
	 */
	public List<X9UtilWorkUnit> getWorkUnits() {
		return workUnitList;
	}

	/**
	 * Add another work unit to be executed.
	 * 
	 * @param workUnit
	 *            new work unit to be executed
	 */
	public void addWorkUnit(final X9UtilWorkUnit workUnit) {
		workUnitList.add(workUnit);
	}

	/**
	 * Get the processing error list from all work units.
	 * 
	 * @return list of processing errors (never null)
	 */
	public List<X9Error> getProcessingErrorList() {
		final List<X9Error> processingErrorList = new ArrayList<>();
		for (final X9UtilWorkUnit workUnit : workUnitList) {
			final List<X9Error> errorList = workUnit.getProcessingErrorList();
			for (final X9Error x9error : errorList) {
				if (x9error != null) {
					processingErrorList.add(x9error);
				}
			}
		}
		return processingErrorList;
	}

	/**
	 * Get highest exit status or negative if there has been an abort. The individual exit status
	 * for each work unit can be obtained from the work list.
	 *
	 * @return highest exit status
	 */
	public int getExitStatus() {
		return exitStatus;
	}

	/**
	 * Update the exit status as the highest set by each function as they are completed, but do not
	 * allow a new exit status to replace any negative exit status values that have been set
	 * earlier. The exit status can only be pushed lower once it is negative or we are posting a
	 * negative status from a newly completed task. Synchronization is needed since we can be
	 * invoked from concurrently running threads.
	 *
	 * @param taskStatus
	 *            status from task just completed
	 * @return updated exit status
	 */
	public synchronized int updateExitStatus(final int taskStatus) {
		exitStatus = (exitStatus < 0 || taskStatus < 0) ? Math.min(exitStatus, taskStatus)
				: Math.max(exitStatus, taskStatus);
		return exitStatus;
	}

}
