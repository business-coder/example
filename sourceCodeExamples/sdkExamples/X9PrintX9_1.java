package com.x9ware.examples;

import java.awt.print.PrinterException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.core.X9FileAttributes;
import com.x9ware.core.X9Reader;
import com.x9ware.create.X9Coordinate;
import com.x9ware.create.X9PageDef;
import com.x9ware.create.X9PrintImageXml;
import com.x9ware.create.X9PrintLayout;
import com.x9ware.create.X9PrintPages;
import com.x9ware.create.X9PrintUtility;
import com.x9ware.logging.X9JdkLogger;

/**
 * X9PrintX9 reads an x9.37 file and directs printing of images to a selected printer. Code is
 * provided for either interactive or silent print. An X9PrintImagesXml instance defines print
 * related parameters, which are loaded from an external xml file but could also be dynamically
 * populated.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9PrintX9 {

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
	private static final String X9PRINTX9 = "X9PrintX9";
	private static final String DEFAULT_PRINTER = "CutePDF Writer";
	private static final String PRINT = "Print";
	private static final int INITIAL_ITEM_LIST_SIZE = 1000;
	private static final boolean IS_PRINT_USER_INTERACTIVE = true;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9PrintX9.class);

	/*
	 * X9PrintX9 Constructor.
	 */
	public X9PrintX9() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9PRINTX9);
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
		final File printXml = new File(baseFolder, "Letter 3 x 1 MICR Print.xml");
		final File x9InputFile = new File(baseFolder, "x9generateX9.x9");

		/*
		 * Load the input x9.37 file. This example opens the x9.37 as an input file, but you can
		 * also use sdkIO.openInputReader() to read from an input stream.
		 */
		int recordCount = 0;
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9Reader x9reader = sdkIO.openInputFile(x9InputFile)) {
			/*
			 * Open x9.37 image reader for this input file.
			 */
			sdkIO.openImageReader(x9InputFile);

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

			/*
			 * Assign x9header indexes.
			 */
			sdkBase.getObjectManager().assignHeaderObjectIndexReferences();

			/*
			 * Print the loaded x9.37 file either to a user selected printer (when interactive
			 * print) or to a specific printer (when silent print).
			 */
			if (recordCount > 0) {
				printFile(printXml, x9InputFile, x9reader);
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
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
	 * Print the x9.37 file.
	 *
	 * @param printXml
	 *            print xml file
	 * @param x9InputFile
	 *            x9.37 input file
	 * @param x9fileAttributes
	 *            x9.37 input file attributes
	 * @throws PrinterException
	 */
	/**
	 * @param printXml
	 * @param x9InputFile
	 * @param x9fileAttributes
	 * @throws PrinterException
	 */
	private void printFile(final File printXml, final File x9InputFile,
			final X9FileAttributes x9fileAttributes) throws PrinterException {
		/*
		 * Load our print definition.
		 */
		final X9PrintImageXml x9printImageXml = new X9PrintImageXml();
		x9printImageXml.readXmlFile(printXml);

		/*
		 * Determine paper format.
		 */
		final boolean isPortrait = x9printImageXml.isPortrait();

		/*
		 * Get page attributes.
		 */
		final X9Coordinate coordinate = x9printImageXml
				.getFieldCoordinate(X9PrintImageXml.PAGE_SIZE);
		final float pageWidth = coordinate.getX();
		final float pageHeight = coordinate.getY();

		/**
		 * Create an intermediate list to hold the formatted print items.
		 */
		final List<X9Object> itemList = new ArrayList<>(INITIAL_ITEM_LIST_SIZE);

		/*
		 * Allocate a print layout instance.
		 */
		final X9PrintLayout x9printLayout = new X9PrintLayout(sdkBase, x9printImageXml);

		/*
		 * Add all items to the print list.
		 */
		x9printLayout.addAllLoadedItems(itemList);

		/*
		 * Determine the default printer.
		 */
		final PrintService defaultPrinterService = PrintServiceLookup.lookupDefaultPrintService();
		final String defaultPrinter = defaultPrinterService == null ? DEFAULT_PRINTER
				: defaultPrinterService.getName();

		/*
		 * Allocate our print utility.
		 */
		final X9PrintUtility x9printUtility = new X9PrintUtility(pageWidth, pageHeight);

		/*
		 * Allocate our pageable interface that will create our print pages.
		 */
		final String footerText = FilenameUtils.getName(x9InputFile.toString());
		final List<X9PageDef> pageList = x9printLayout.createPages(itemList);
		final X9PrintPages x9printPages = new X9PrintPages(sdkBase, x9fileAttributes, PRINT,
				x9printImageXml, pageList, footerText);

		/*
		 * Get a new printer job and assign the default printer.
		 */
		x9printUtility.createNewPrinterJob(x9printPages, defaultPrinter, pageList.size());

		/*
		 * Set our paper size.
		 */
		x9printUtility.setPaperSize();

		/*
		 * Build the printer request attribute set.
		 */
		x9printUtility.buildAttributes(isPortrait);
		x9printUtility.addDpiPrintAttribute(X9PrintUtility.DPI_1200);

		/*
		 * Now print as either interactive or silent.
		 */
		if (IS_PRINT_USER_INTERACTIVE) {
			/*
			 * Invoke user interactive print dialog and then print when user directed (and not
			 * cancelled) using the selected printer and a possibly modified page count.
			 */
			if (x9printUtility.invokePrintDialog()) {
				final int pagesToBePrinted = x9printUtility.getNumberOfPagesToBePrinted();
				x9printPages.setNumberOfPagesToBePrinted(pagesToBePrinted);
				x9printUtility.initiatePrint();
			}
		} else {
			/*
			 * Invoke silent print of all pages to the indicated printer.
			 */
			final int pagesToBePrinted = pageList.size();
			x9printPages.setNumberOfPagesToBePrinted(pagesToBePrinted);
			x9printUtility.initiatePrint();
		}
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
		LOGGER.info(X9PRINTX9 + " started");
		try {
			final X9PrintX9 example = new X9PrintX9();
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
