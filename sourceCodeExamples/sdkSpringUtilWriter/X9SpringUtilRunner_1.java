package com.x9ware.springUtilWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.x9ware.tools.X9FileUtils;
import com.x9ware.utilities.X9UtilMain;
import com.x9ware.utilities.X9UtilWorkResults;

@Component
public class X9SpringUtilRunner implements CommandLineRunner {

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9SpringUtilRunner.class);

	@Override
	public void run(final String... args) throws Exception {
		/*
		 * Validate that we have three parameters on the command line.
		 */
		if (args == null || args.length == 0) {
			LOGGER.info("command line has NO arguments");
		} else if (args.length != 3) {
			LOGGER.info("usage requires three parameters: headerXmlFile, "
					+ "inputCsvFile, outputFile");
		} else {
			/*
			 * Run x9utilities -write with provided files.
			 */
			runX9UtilitiesWrite(buildWriteArgs(args));
		}
	}

	/**
	 * Build x9utilities write arguments.
	 * 
	 * @param args
	 *            arguments array for x9utilities invocation
	 * @return arguments array
	 */
	private String[] buildWriteArgs(final String... args) throws FileNotFoundException {
		/*
		 * Log our arguments (headerXmlFile, inputCsvFile, and outputFile).
		 */
		LOGGER.info("command line args:");
		for (int i = 0, n = args.length; i < n; i++) {
			LOGGER.info("  arg[{}] {}", i, args[i]);
		}

		final File headerXmlFile = new File(args[0]);
		final File inputCsvFile = new File(args[1]);
		final File outputFile = new File(args[2]);

		if (!X9FileUtils.existsWithPathTracing(headerXmlFile)) {
			throw new FileNotFoundException(headerXmlFile.toString());
		}

		if (!X9FileUtils.existsWithPathTracing(inputCsvFile)) {
			throw new FileNotFoundException(inputCsvFile.toString());
		}

		/*
		 * Construct a set of x9utilities arguments to invoke x9util -write.
		 */
		return new String[] { "-write", "-j", "-l", "-xml:" + headerXmlFile.toString(),
				inputCsvFile.toString(), outputFile.toString() };
	}

	/**
	 * Run the x9utilities write function.
	 * 
	 * @param writeArgs
	 *            x9utilities write args as artificial command line
	 * @return exit status final exit status to be posted
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private int runX9UtilitiesWrite(final String[] writeArgs)
			throws IOException, InterruptedException {
		/*
		 * Invoke x9utilities -write executed using classes within the SDK-jar.
		 */
		LOGGER.info("runX9UtilitiesWrite writeArgs({})", StringUtils.join(writeArgs, '|'));
		int exitStatus = X9UtilMain.EXIT_STATUS_ABORTED;
		try (final X9UtilMain x9utilMain = new X9UtilMain()) {
			final X9UtilWorkResults exitResult = x9utilMain.launch(writeArgs);
			exitStatus = exitResult.getExitStatus();
		} catch (final Throwable t) {
			LOGGER.error("aborted " + t.toString());
		} finally {
			LOGGER.info("runner exit(" + exitStatus + ")");
		}
		return exitStatus;
	}

}
