package com.x9ware.utilities;

import java.io.File;
import java.util.List;

import com.x9ware.tools.X9TaskMonitor;

/**
 * X9UtilImagePullMonitor directs image pull activities against a series of image pull entry maps,
 * which in aggregate represent the images to be pulled. The exact number of started threads is
 * dependent on the number of available processors and system property settings. Performance is
 * maximized by splitting input across internally created lists which are then processed
 * independently by concurrent threads. Statistics are accumulated within each worker task and
 * aggregated and reported on completion.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilImagePullMonitor extends X9TaskMonitor<X9UtilImagePullEntry> {

	/*
	 * Private.
	 */
	private final X9UtilWorkUnit workUnit;
	private final File baseImageFolder;

	/**
	 * X9UtilImagePullMonitor Constructor.
	 *
	 * @param maximumThreadCount
	 *            maximum thread count
	 * @param work_Unit
	 *            current work unit
	 * @param base_ImageFolderName
	 *            base image folder name (it might be time-stamped)
	 */
	public X9UtilImagePullMonitor(final int maximumThreadCount, final X9UtilWorkUnit work_Unit,
			final String base_ImageFolderName) {
		/*
		 * Allocate our thread monitor with specified maximum thread count.
		 */
		super(maximumThreadCount);

		/*
		 * Assign our work unit and base image folder.
		 */
		workUnit = work_Unit;
		final String baseImageFolderName = base_ImageFolderName;
		baseImageFolder = new File(baseImageFolderName);
	}

	@Override
	public X9UtilImagePullWorker allocateNewWorkerInstance(
			final List<X9UtilImagePullEntry> workerList) {
		return new X9UtilImagePullWorker(this, workUnit, workerList, baseImageFolder);
	}

}
