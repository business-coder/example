package com.x9ware.examples;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.elements.X9C;
import com.x9ware.error.X9Error;
import com.x9ware.export.X9ExportFile;
import com.x9ware.export.X9ExportImages;
import com.x9ware.export.X9ExportInterface;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tools.X9CsvWriter;

/**
 * X9ReadX9ExportCsv reads an x9.37 file and exports to an output csv file (and optionally with
 * associated images) to the same output csv formats as provided by X9Utilities and X9Assist. This
 * is accomplished by implementing X9ExportInterface, which is used to control the various export
 * options that would be instead provided by either the X9Utilities command line or through the
 * X9Assist export UI panel. By default, this example will export in fixed-format with images, but
 * that can be changed based on your specific requirements. Note the method overrides that provide
 * these settings. Also pay close attention to where the output images are written and to the
 * programmatic option to clear the output image folder. These interface parameters must be closely
 * reviewed, especially as to how they might relate to your specific application.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9ReadX9ExportCsv implements X9ExportInterface {

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
	private static final String X9READX9_EXPORTCSV = "X9ReadX9ExportCsv";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9ReadX9ExportCsv.class);

	/*
	 * X9ReadX9ExportCsv Constructor.
	 */
	public X9ReadX9ExportCsv() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9READX9_EXPORTCSV);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdk = X9SdkFactory.getSdk(sdkBase);
		if (!sdkBase.bindConfiguration(X9.X9_37_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
	}

	/**
	 * Read an x9.37 file (record by record) and export to an output csv file in various formats.
	 */
	private void process() {
		/*
		 * Define files.
		 */
		final File x9InputFile = new File(baseFolder, "Test file with 25 checks.x9");
		final File csvOutputFile = new File(baseFolder, "X9ReadX9ExportCsv.csv");
		final File outputImageFolder = new File(baseFolder, "images");

		/*
		 * Read the x9.37 file and create a fixed-format output csv file with optional images. This
		 * example opens the x9.37 as an input file, but you can also use x9exportFile
		 * exportFromReader() to read from an input stream. Similarly, this example writes to an
		 * output csv file, but you can also use X9CsvWriter to write to a BufferedWriter which can
		 * be allocated to an output stream.
		 */
		try (final X9CsvWriter csvWriter = new X9CsvWriter(csvOutputFile);
				final X9ExportFile x9exportFile = new X9ExportFile(sdk, csvWriter, this)) {
			x9exportFile.setLoggingEnabled(false);
			x9exportFile.exportFromFile(x9InputFile, outputImageFolder);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Log our completion.
		 */
		LOGGER.info("finished");
	}

	@Override
	public boolean isConfigAuto() {
		return false;
	}

	@Override
	public boolean isRecordSelected(final X9Object x9o) {
		return x9o.x9ObjType >= X9.CHECK_DETAIL && x9o.x9ObjType <= X9.IMAGE_VIEW_DATA;
	}

	@Override
	public boolean isExportCsv() {
		return false;
	}

	@Override
	public boolean isExportAsItems() {
		return true;
	}

	@Override
	public boolean isExportAsGroups() {
		return false;
	}

	@Override
	public boolean isExportTiffTags() {
		return false;
	}

	@Override
	public boolean isImageExport() {
		return true;
	}

	@Override
	public char getImageExportDirective() {
		return X9ExportImages.IMAGE_EXPORT_ABSOLUTE;
	}

	@Override
	public String getImageExportFormat() {
		return X9C.TIF;
	}

	@Override
	public boolean isClearImageFolders() {
		return true;
	}

	@Override
	public boolean isExportImagesIrdFormat() {
		return false;
	}

	@Override
	public boolean isExportMultiPageTiffs() {
		return false;
	}

	@Override
	public boolean isExportMultiPageIRDs() {
		return false;
	}

	@Override
	public int getImagesPerFolder() {
		return 0;
	}

	@Override
	public boolean isCsvFormatQuoted() {
		return false;
	}

	@Override
	public boolean isCsvInsertColumnHeadersAsFirstRow() {
		return false;
	}

	@Override
	public boolean isCsvInsertFileNameIntoFirstColumn() {
		return false;
	}

	@Override
	public boolean isWriteType68RecordsGenerically() {
		return false;
	}

	@Override
	public boolean isFormatWithPrefix() {
		return false;
	}

	@Override
	public boolean isFormatWithSuffix() {
		return false;
	}

	@Override
	public boolean isErrorSelected(final X9Error x9error) {
		return false;
	}

	@Override
	public boolean isXmlFormatFlat() {
		return false;
	}

	@Override
	public boolean isXmlFormatHierarchical() {
		return false;
	}

	@Override
	public boolean isXmlIncludeEmptyFields() {
		return false;
	}

	@Override
	public boolean isDecimalPointInAmounts() {
		return true;
	}

	@Override
	public boolean isAsIs() {
		return false;
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
		LOGGER.info(X9READX9_EXPORTCSV + " started");
		try {
			final X9ReadX9ExportCsv example = new X9ReadX9ExportCsv();
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
