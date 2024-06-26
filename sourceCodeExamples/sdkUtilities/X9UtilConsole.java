package com.x9ware.utilities;

import java.awt.Dimension;

import javax.swing.WindowConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.base.X9SdkBase;
import com.x9ware.elements.X9Products;
import com.x9ware.swing.X9Frame;
import com.x9ware.swing.X9LookAndFeelWindows;
import com.x9ware.swing.X9StartUp;
import com.x9ware.tools.X9CountDownLatch;
import com.x9ware.tools.X9EventQueue;
import com.x9ware.tools.X9Task;
import com.x9ware.ux.X9UtilConsoleUx;

/**
 * X9UtilConsole is invoked within x9utilities using the "-console" command line switch. We are a UI
 * dialog that runs X9Utilities as a desktop application. X9Assist invokes X9UtilConsoleUx directly
 * from X9GuiToolBar and does not use this bridge, which is needed only by X9UtilWorker. This is the
 * mechanism to get the utilities console launched within x9utilities using the "-console" switch.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilConsole {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilConsole.class);

	/*
	 * X9UtilConsole Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilConsole(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
	}

	/**
	 * Processing for the "-console" command switch when running in x9utilities.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * The utilities console must be run on the event thread.
		 */
		final X9CountDownLatch waitLatch = new X9CountDownLatch(1);
		X9EventQueue.runLater(() -> {
			try {
				runX9UtilitiesConsole(waitLatch);
			} catch (final Throwable t) {
				X9StartUp.startupFailure("main failed", t);
			}
		});

		/*
		 * Wait for the utilities console to signal completion.
		 */
		X9Task.waitOnLatch("x9utilities console", waitLatch, X9Task.NO_EXPIRATION_JUST_RUN_FOREVER,
				null, Integer.MAX_VALUE);
		LOGGER.info("exit console");

		/*
		 * Return exit status zero.
		 */
		return X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * Run the utilities console. This runs in X9Utilities only and is not used by X9Assist.
	 * 
	 * @param waitLatch
	 *            wait latch to be posted on completion
	 */
	private void runX9UtilitiesConsole(final X9CountDownLatch waitLatch) {
		/*
		 * Look and Feel must be initialized immediately after the log is opened. Although I really
		 * prefer the flat look and feel, there are several things that make this impractical. First
		 * is that the console consists of a single panel, so L&F is not a big issue either way.
		 * Second is that flatLaf requires adding their jar to our runtime (500k); it makes little
		 * sense to do that for all of our customers, when it will be used by a small group. Third,
		 * and most importantly, flatLaf uses reflection with a message that can only be eliminated
		 * by passing an "--add-opens" argument into the JVM, which gets into our Gradle build. Much
		 * easier to just avoid all of that.
		 */
		X9LookAndFeelWindows.setWindowsLookAndFeel();

		/*
		 * Define our frame, which is needed since X9UtilConsoleUx is a dialog, so we must be the
		 * parent frame. This title appears on the Windows task bar.
		 */
		final X9Frame x9frame = new X9Frame(WindowConstants.EXIT_ON_CLOSE);
		x9frame.setTitle(X9Products.X9UTILITIES);
		x9frame.packAllComponents(new Dimension(10, 10));
		x9frame.makeVisible();

		/*
		 * Run the x9utilities console.
		 */
		final X9UtilConsoleUx utilConsoleUx = new X9UtilConsoleUx(x9frame, sdkBase, waitLatch);
		utilConsoleUx.dialog();
	}

}
