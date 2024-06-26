package com.x9ware.examples;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */

public class X9ShellToUtil {

	private static final String X9UTIL_JAR_PATH = "C:/Program Files/X9Ware LLC/X9Utilities R4.12/x9util.jar";
	private static final String JAVA_EXE_PATH = "C:/Program Files/Zulu/zulu-11/bin/java.exe";
	private static final String WORKING_DIRECTORY = "/x9utilWork";

	public static void main(final String[] args) {
		/*
		 * Check if the required arguments are provided.
		 */
		if (args.length < 2) {
			System.out.println("Usage: X9ShellToUtil <input file> <output file> [switches]");
			System.exit(1);
		}

		/*
		 * Get the input file, output file, and a variable number of command line switches.
		 */
		final File inputFile = new File(args[0]);
		final File outputFile = new File(args[1]);
		final List<String> switches = new ArrayList<>(Arrays.asList(args).subList(2, args.length));

		/*
		 * Invoke x9utilities and then wait for the process to finish executing.
		 */
		int exitStatus = -1;
		try {
			final List<String> command = new ArrayList<>();
			command.add(JAVA_EXE_PATH);
			command.add("-jar");
			command.add(X9UTIL_JAR_PATH);
			command.addAll(switches);

			final ProcessBuilder processBuilder = new ProcessBuilder(command);
			processBuilder.directory(new File(WORKING_DIRECTORY));
			processBuilder.redirectInput(inputFile);
			processBuilder.redirectOutput(outputFile);

			final Process process = processBuilder.start();
			exitStatus = process.waitFor();

			final InputStream inputStream = process.getInputStream();
			try (Scanner scanner = new Scanner(inputStream)) {
				if (scanner.hasNextLine()) {
					final String output = scanner.nextLine();
					System.out.println("Output: " + output);
				}
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		/*
		 * Exit program with x9utilities exit status.
		 */
		System.out.println("Exit status: " + exitStatus);
		System.exit(exitStatus);
	}

}
