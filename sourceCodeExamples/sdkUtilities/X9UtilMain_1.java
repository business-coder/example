package com.x9ware.utilities;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.elements.X9Products;
import com.x9ware.error.X9Error;

/**
 * X9UtilMain is the static main class for X9Utilities, which is the command line interface for our
 * various batch products. The actual batch functions that are allowed will be determined based on
 * the current client license. Attempting to invoke a function that is not supported by the current
 * license will result in an abort. X9UtilMain is itself an extension of X9UtilBatch, where all
 * actually processing is performed. This design allows other SDK applications to extend X9UtilBatch
 * in a similar manner.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9UtilMain extends X9UtilBatch {

	/*
	 * Constants.
	 */
	public static final boolean ENVIRONMENT_OPEN_CLOSE_ENABLED = true;
	public static final boolean ENVIRONMENT_OPEN_CLOSE_DISABLED = false;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilMain.class);

	/**
	 * X9UtilMain Constructor.
	 */
	public X9UtilMain() {
		/*
		 * This constructor is always used when x9utilities is run from the command line. In this
		 * launch scenario, we use the X9UTILITIES product name, which forces an x9utilities license
		 * key to be located and applied to the runtime environment.
		 */
		super(X9Products.X9UTILITIES, ENVIRONMENT_OPEN_ENABLED, ENVIRONMENT_CLOSE_ENABLED);
	}

	/**
	 * X9UtilMain Constructor with explicitly defined environment open and close parameters.
	 *
	 * @param is_EnvironmentToBeOpenedAndClosed
	 *            true or false
	 */
	public X9UtilMain(final boolean is_EnvironmentToBeOpenedAndClosed) {
		this(is_EnvironmentToBeOpenedAndClosed, is_EnvironmentToBeOpenedAndClosed);
	}

	/**
	 * X9UtilMain Constructor with explicitly defined open and close parameters.
	 *
	 * @param is_EnvironmentToBeOpened
	 *            true or false
	 * @param is_EnvironmentToBeClosed
	 *            true or false
	 */
	public X9UtilMain(final boolean is_EnvironmentToBeOpened,
			final boolean is_EnvironmentToBeClosed) {
		/*
		 * This constructor can only be invoked from an sdk application (it is never used from
		 * x9utilities command line). This could be an sdk user application, but it could also be
		 * x9assist running the utilities console. Either way, we now open the batch environment
		 * with our sdk product name. We can logically do this since the invoking application has
		 * already had its license key validated, hence it is appropriate to allow x9utilities to
		 * launch without further license key validation. This is a core requirement, since an
		 * x9assist user running the utilities console does not have an x9utilities license.
		 */
		super(X9Products.X9SDK, is_EnvironmentToBeOpened, is_EnvironmentToBeClosed);
	}

	/**
	 * Main as invoked directly from the command line. The only thing unique here is that we include
	 * system exit which terminates the currently running JVM. Our launch method can otherwise be
	 * used for more control over the runtime environment.
	 *
	 * @param args
	 *            command line arguments
	 */
	public static void main(final String[] args) {
		/*
		 * Run using try-with-resources to ensure we always close and system exit.
		 */
		int exitStatus = EXIT_STATUS_ABORTED;
		try (final X9UtilMain x9utilMain = new X9UtilMain()) {
			/*
			 * Launch the x9utilities runtime.
			 */
			final X9UtilWorkUnitList workUnitList = x9utilMain.launch(args);
			exitStatus = workUnitList.getExitStatus();

			/*
			 * Generate list of all processing errors (writer, import, etc).
			 */
			final List<X9Error> processingErrorList = workUnitList.getProcessingErrorList();
			if (processingErrorList != null && processingErrorList.size() > 0) {
				LOGGER.error("summary of processing errors:");
				for (final X9Error x9error : processingErrorList) {
					LOGGER.error(x9error.getFormulatedErrorString());
				}
			}
		} catch (final Throwable t) {
			LOGGER.error("exception", t);
		} finally {
			System.exit(exitStatus);
		}
	}

}
