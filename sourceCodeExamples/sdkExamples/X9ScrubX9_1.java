package com.x9ware.examples;

import java.io.File;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.core.X9Reader;
import com.x9ware.create.X9Scrub;
import com.x9ware.create.X9Scrub937;
import com.x9ware.create.X9ScrubXml;
import com.x9ware.logging.X9JdkLogger;

/**
 * X9ScrubX9 reads and sanitizes an x9.37 file by leveraging the SDK scrub facility. An X9ScrubXml
 * instance is used to define the scrub related parameters, which we load externally but could also
 * be dynamically populated.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ScrubX9 {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final X9Sdk sdk;
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");

	/*
	 * Constants.
	 */
	private static final String X9SCRUBX9 = "X9ScrubX9";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ScrubX9.class);

	/*
	 * X9ScrubX9 Constructor.
	 */
	public X9ScrubX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9SCRUBX9);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdk = X9SdkFactory.getSdk(sdkBase);
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
	}

	/**
	 * Read a csv file (line by line) and use generate to create an output x9.37 file
	 */
	private void process() {
		/*
		 * Define files.
		 */
		final File scrubXml = new File(baseFolder, "default.Scrubs.xml");
		final File inputFile = new File(baseFolder, "x9buildX9.x9");
		final File outputFile = new File(baseFolder, "x9scrubX9.x9");

		/*
		 * Load the input x9.37 file. This example opens the x9.37 as an input file, but you can
		 * also use sdkIO.openInputReader() to read from an input stream.
		 */
		int recordCount = 0;
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputFile(inputFile)) {
			/*
			 * Get first x9.37 record.
			 */
			X9SdkObject sdkObject = sdkIO.readNext();

			/*
			 * Read and store records until end of file.
			 */
			while (sdkObject != null) {
				recordCount++;
				sdkIO.createAndStoreX9Object();
				sdkObject = sdkIO.readNext();
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Assign x9header indexes.
		 */
		sdkBase.getObjectManager().assignHeaderObjectIndexReferences();

		/*
		 * Scrub the loaded x9.37 file and create the scrubbed x9.37 output file.
		 */
		if (recordCount > 0) {
			try {
				/*
				 * Load our reformatter definition.
				 */
				final X9ScrubXml x9scrubXml = new X9ScrubXml();
				x9scrubXml.loadScrubConfiguration(scrubXml);

				/*
				 * Run scrub to create the sanitized x9.37 file. This example scrubs to an output
				 * file, but you can also use x9scrub.scrubToSteram() to write to an output stream.
				 */
				final X9Scrub x9scrub = new X9Scrub937(sdkBase, x9scrubXml);
				x9scrub.scrubToFile(outputFile);

				/*
				 * Log scrub completion message and individual fields that were scrubbed.
				 */
				final StringBuilder sb = new StringBuilder(x9scrub.getCompletionMessage());
				sb.append("\nIndividual scrubbed fields were as follows:\n");
				for (final Entry<String, AtomicInteger> entry : x9scrub.getTallyMap().entrySet()) {
					sb.append("     description(").append(entry.getKey());
					sb.append(") count(").append(entry.getValue().get()).append(")\n");
				}
				LOGGER.info(sb.toString());

			} catch (final Exception ex) {
				throw X9Exception.abort(ex);

			}
		}

		/*
		 * Release all sdkBase storage (since we loaded the file to the heap).
		 */
		sdkBase.systemReset();

		/*
		 * Log our completion.
		 */
		LOGGER.info("finished");
	}

	/**
	 * Main().
	 *
	 * @param args
	 *            command line arguments
	 */
	public static void main(final String[] args) {
		int status = 0;
		X9JdkLogger.initializeLoggingEnvironment();
		LOGGER.info(X9SCRUBX9 + " started");
		try {
			final X9ScrubX9 example = new X9ScrubX9();
			example.process();
		} catch (final Throwable t) { // catch both errors and exceptions
			status = 1;
			LOGGER.error("main exception", t);
		} finally {
			X9SdkRoot.shutdown();
			X9JdkLogger.closeLog();
			System.exit(status);
		}
	}

}
