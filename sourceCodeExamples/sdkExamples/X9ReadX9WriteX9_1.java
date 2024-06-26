package com.x9ware.examples;

import java.io.File;
import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Item937;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.core.X9Reader;
import com.x9ware.fields.X9Field;
import com.x9ware.fields.X9Walk;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.types.X9Type01;

/**
 * X9ReadX9WriteX9 reads an x9.37 file and creates an output x9.37 file with associated images. This
 * is a good example of reading an x9.37 file which is then loaded to resident x9objects. All fields
 * within each record are examined using walk. This example could be easily extended to allow
 * individual fields to be modified as needed. The possibly modified x9.37 file is written from the
 * resident x9objects array.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ReadX9WriteX9 {

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
	private static final String X9READX9_WRITEX9 = "X9ReadX9WriteX9";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ReadX9WriteX9.class);

	/*
	 * X9ReadX9WriteX9 Constructor.
	 */
	public X9ReadX9WriteX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9READX9_WRITEX9);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdk = X9SdkFactory.getSdk(sdkBase);
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
	}

	/**
	 * Read an x9.37 file (record by record) which is stored in an internal list and then used to
	 * subsequently create an output x9.37 file.
	 */
	private void process() {
		/**
		 * Get a field walker instance.
		 */
		final X9Walk x9walk = new X9Walk(sdkBase);

		/*
		 * Define files.
		 */
		final File inputFile = new File(baseFolder, "Test file with 25 checks.x9");
		final File outputFile = new File(baseFolder, "X9ReadX9WriteX9.x9");

		/*
		 * Read the x9.37 file and populate x9objects. This is provided as an example of storing the
		 * input x9.37 file into x9objects that can be internally processed. This process to store
		 * the records onto the heap is optional. You could certainly instead simply read x9.37 and
		 * write x9.37 with the data modified as necessary, as a single process. This example opens
		 * the x9.37 as an input file, but you can also use sdkIO.openInputReader() to read from an
		 * input stream.
		 */
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
				/*
				 * Create a new x9object and have X9ObjectManager add to the list.
				 */
				final X9Object x9o = sdkIO.createAndStoreX9Object();
				LOGGER.info("added to the heap recordNumber({}) recordType({})", x9o.x9ObjIdx,
						x9o.x9ObjType);

				/*
				 * Get the next x9.37 record.
				 */
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
		 * Walk the record list and demonstrate some common actions.
		 */
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			X9Object x9o = sdkBase.getFirstObject();
			while (x9o != null) {
				/*
				 * Modify the file header as an example of how x9objects can be updated. The data
				 * change is applied to the byte array that is stored within this x9object.
				 */
				final int recordType = x9o.x9ObjType;
				if (recordType == X9.FILE_HEADER) {
					final X9Type01 t01 = new X9Type01(sdkBase, x9o.x9ObjData);
					t01.immediateDestinationName = "writex9";
					t01.modify();
					LOGGER.info("file header has been modified({})", new String(x9o.x9ObjData));
				}

				/*
				 * Obtain and log item related fields.
				 */
				if (recordType == X9.CHECK_DETAIL) {
					final X9Item937 x9item = new X9Item937(x9o);
					final BigDecimal amount = x9item.getAmount();
					final String itemSequenceNumber = x9item.getItemSequenceNumber();
					final String routing = x9item.getRouting();
					final String onus = x9item.getOnus();
					final String auxOnus = x9item.getAuxOnus();
					final String epc = x9item.getEpc();
					final String bofdIndicator = x9item.getBofdIndicator();
					final String bofdEndorsementDate = x9item.getBofdEndorsementDate();
					final String bofdRouting = x9item.getBofdRouting();
					final String imageCreatorDate = x9item.getImageCreatorDate();
					final String imageCreatorRouting = x9item.getImageCreatorRouting();
					LOGGER.info("amount({}) itemSequenceNumber({}) routing({}) onus({}) "
							+ "auxOnus({}) epc({}) bofdIndicator({}) bofdEndorsementDate({}) "
							+ "bofdRouting({}) imageCreatorDate({}) imageCreatorRouting({})",
							amount, itemSequenceNumber, routing, onus, auxOnus, epc, bofdIndicator,
							bofdEndorsementDate, bofdRouting, imageCreatorDate,
							imageCreatorRouting);
				}

				/*
				 * Get the next x9.37 record.
				 */
				x9o = x9o.getNext();
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Create a new x9.37 file from the internal x9.37 list that was previously created.
		 */
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Open files. This example will write the data from a stored list of x9objects. We did
			 * not attach the images to the type 52 records (we could have done that...) but will
			 * instead randomly read the images from that input file using the file offset and
			 * length of each each. Just accept this as an example. It would be much more
			 * advantageous to write the output file as part of the original process (one-step), or
			 * to attach the images to the type 52 x9objects (so they would not have to be re-read
			 * here). Also, this example opens the x9.37 as an output file, but you can also use
			 * sdkIO.openOutputStream() to instead write to an output stream.
			 */
			sdkIO.openImageReader(inputFile);
			sdkIO.openOutputFile(outputFile);

			/*
			 * Walk the list and write each x9.
			 */
			X9Object x9o = sdkBase.getFirstObject();
			while (x9o != null) {
				/*
				 * Create the sdkObject.
				 */
				final X9SdkObject sdkObject = sdkIO.makeOutputRecord(x9o);

				/*
				 * List x9.37 record fields.
				 */
				final X9Field[] fieldArray = x9walk.getFieldArray(x9o);
				for (final X9Field x9field : fieldArray) {
					if (!x9field.isBinaryField()) {
						LOGGER.info("recordType({}) fieldIndex({}) fieldName({}) value({})",
								x9o.x9ObjType, x9field.getFieldIndex(), x9field.getName(),
								x9field.getValueTrimmedToUpper(x9o));
					}
				}

				/*
				 * Write this x9.37 record from the sdkObject.
				 */
				sdkIO.writeOutputFile(sdkObject);

				/*
				 * Get the next x9.37 record.
				 */
				x9o = x9o.getNext();
			}

			/*
			 * Log our statistics.
			 */
			LOGGER.info(sdkIO.getSdkStatisticsMessage(outputFile));

		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Release all sdkBase storage (since we loaded the file to the heap).
		 */
		sdkBase.systemReset();

		/*
		 * Log as completed.
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
		LOGGER.info(X9READX9_WRITEX9 + " started");
		try {
			final X9ReadX9WriteX9 example = new X9ReadX9WriteX9();
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
