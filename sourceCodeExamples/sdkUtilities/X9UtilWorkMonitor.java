package com.x9ware.utilities;

import java.util.List;

import com.x9ware.tools.X9TaskMonitor;
import com.x9ware.tools.X9TaskWorker;

/**
 * X9UtilWorkMonitor directs activities against a utilities work list. The exact number of started
 * threads is dependent on the number of available processors and system property settings. Overall
 * performance is maximized by splitting incoming work across internally created lists which are
 * then processed independently by concurrent threads. Statistics are accumulated within each worker
 * task and aggregated and reported on completion.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilWorkMonitor extends X9TaskMonitor<X9UtilWorkUnit> {

	/**
	 * X9UtilWorkMonitor Constructor.
	 *
	 * @param maximumThreadCount
	 *            maximum thread count
	 */
	public X9UtilWorkMonitor(final int maximumThreadCount) {
		super(maximumThreadCount);
	}

	@Override
	public X9TaskWorker<X9UtilWorkUnit> allocateNewWorkerInstance(
			final List<X9UtilWorkUnit> workerList) {
		return new X9UtilWorker(this, workerList);
	}

}
