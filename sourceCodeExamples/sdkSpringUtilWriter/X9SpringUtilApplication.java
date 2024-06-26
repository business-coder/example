package com.x9ware.springUtilWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.x9ware.logging.X9JdkLogger;

@SpringBootApplication
public class X9SpringUtilApplication {

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9SpringUtilApplication.class);

	/**
	 * Main invokes the spring boot application.
	 * 
	 * @param args
	 *            command line arguments
	 */
	public static void main(final String[] args) {
		int exitStatus = -1;
		try {
			X9JdkLogger.setLoggingEnabled(false); // turn off default SDK JDK Logger
			exitStatus = SpringApplication
					.exit(SpringApplication.run(X9SpringUtilApplication.class, args));
		} catch (final Exception ex) {
			throw (ex);
		} finally {
			LOGGER.info("completed");
			System.exit(exitStatus);
		}
	}

}
