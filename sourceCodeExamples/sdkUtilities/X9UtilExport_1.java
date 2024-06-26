package com.x9ware.utilities;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.core.X9;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.elements.X9C;
import com.x9ware.error.X9Error;
import com.x9ware.export.X9ExportFile;
import com.x9ware.export.X9ExportImages;
import com.x9ware.export.X9ExportInterface;
import com.x9ware.export.X9ExportMulti;
import com.x9ware.export.X9ExportToXml;
import com.x9ware.export.X9ExportTotals;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9D;
import com.x9ware.tools.X9File;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9TempFile;

/**
 * X9UtilExport is part of our utilities package which reads an x9 file and reformats and writes to
 * either a csv or an xml output file with optionally exported or embedded images.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilExport implements X9ExportInterface {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/*
	 * Private.
	 */
	private final String inputExtensions;
	private final String selectedRecordTypes;
	private final boolean[] isRecordTypeSelected = new boolean[X9.HIGHEST_RECORD_TYPE + 1];
	private final boolean isLoggingEnabled;
	private final boolean isAbortWhenEmpty;
	private final boolean isXmlExport;
	private final boolean isXmlFlat;
	private final boolean isXmlHierarchical;
	private final boolean isMultiFile;
	private final boolean isCsvExport;
	private final boolean isItemExport;
	private final boolean isItemExportWithColumnHeaders;
	private final boolean isGroupExport;
	private final boolean isTiffTagExport;
	private final boolean isImageExport;
	private final boolean isImageExportTif;
	private final boolean isImageExportPng;
	private final boolean isImageExportJpg;
	private final boolean isImageExportGif;
	private final boolean isImageExportRelative;
	private final boolean isImageBase64Basic;
	private final boolean isImageBase64Mime;
	private final boolean isImageExportIrdFormat;
	private final boolean isMultiPageTiffExport;
	private final boolean isMultiPageIrdExport;
	private final int imagesPerFolderCount;
	private final boolean isXmlIncludeEmptyFields;
	private final boolean isDecimalPointsInAmounts;
	private final boolean isAsIs;

	private X9Sdk sdk;
	private File inputFile;
	private File outputFile;
	private File baseImageFolder;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilExport.class);

	/*
	 * X9UtilExport Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilExport(final X9UtilWorkUnit work_Unit) {
		/*
		 * Get switch values.
		 */
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		inputExtensions = workUnit.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_EXTENSION_INPUT);
		selectedRecordTypes = workUnit.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_RECORD_TYPES);
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
		isAbortWhenEmpty = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_ABORT_WHEN_EMPTY);
		isItemExport = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_ITEM_EXPORT) || workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_ITEM_EXPORT_WITH_COLUMN_HEADINGS);
		isItemExportWithColumnHeaders = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_ITEM_EXPORT_WITH_COLUMN_HEADINGS);
		isGroupExport = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_GROUP_EXPORT);
		isTiffTagExport = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_TIFF_TAG_EXPORT);
		isXmlFlat = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_XML_EXPORT_FLAT);
		isXmlHierarchical = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_XML_EXPORT_HIERARCHICAL);
		isXmlExport = isXmlFlat || isXmlHierarchical;
		isMultiFile = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MULTI_FILE);
		isCsvExport = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_CSV_EXPORT)
				|| (!isItemExport && !isGroupExport && !isTiffTagExport);
		isImageExportRelative = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_RELATIVE);
		isImageBase64Basic = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_BASE64_BASIC)
				|| workUnit.isCommandSwitchSet("ei"); // compatibility for earlier xml switch value
		isImageBase64Mime = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_BASE64_MIME);
		isImageExport = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_EXPORT)
				|| isImageExportRelative || isImageBase64Basic || isImageBase64Mime;
		isImageExportTif = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_EXPORT_TIF);
		isImageExportPng = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_EXPORT_PNG);
		isImageExportJpg = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_EXPORT_JPG);
		isImageExportGif = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_EXPORT_GIF);
		isImageExportIrdFormat = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_IMAGE_EXPORT_IRD_FORMAT);
		isMultiPageTiffExport = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MULTIPAGE_TIFF_EXPORT);
		isMultiPageIrdExport = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MULTIPAGE_IRD_EXPORT);
		isXmlIncludeEmptyFields = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_XML_INCLUDE_EMPTY_FIELDS);
		isDecimalPointsInAmounts = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_DECIMAL_POINTS);
		isAsIs = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_AS_IS);

		final String imageCount = workUnit
				.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_IMAGES_PER_OUTPUT_FOLDER);
		if (StringUtils.isBlank(imageCount)) {
			imagesPerFolderCount = 0;
		} else {
			imagesPerFolderCount = X9Numeric.toInt(imageCount);
			if (imagesPerFolderCount < 0) {
				throw X9Exception.abort("imagesPerFolderCount({}) not numeric", imageCount);
			}
		}

		/*
		 * Ensure that only one export function was selected.
		 */
		int xcount = 0;
		if (isMultiFile) {
			xcount++;
		}
		if (isXmlExport) {
			xcount++;
		}
		if ((!isMultiFile && !isXmlExport)) {
			xcount++;
		}
		if (xcount > 1) {
			throw X9Exception.abort("multiple export formats");
		}

		/*
		 * Ensure that we have input file extensions for multi-export.
		 */
		if (isMultiFile) {
			if (StringUtils.isBlank(inputExtensions)) {
				throw X9Exception.abort("file extensions required for multi-export (xm)");
			}
		} else {
			if (StringUtils.isNotBlank(inputExtensions)) {
				throw X9Exception.abort("file extensions valid only for multi-export (xm)");
			}
		}
	}

	/**
	 * Export an x937 file. We have an x9 file as input and csv or xml with images as output.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Get work unit files.
		 */
		inputFile = workUnit.inputFile;
		final X9TempFile outputTempFile = workUnit.getOutputTempFileUsingDtsDnrSwitches();
		outputFile = outputTempFile.getTemp(); // either csv or xml based on switch setting
		baseImageFolder = workUnit.imageFolder;

		/*
		 * Get the record types which are user selected and otherwise default to all.
		 */
		if (StringUtils.isBlank(selectedRecordTypes)
				|| StringUtils.equals(selectedRecordTypes, "all")) {
			/*
			 * Select all record types.
			 */
			Arrays.fill(isRecordTypeSelected, true);
		} else if (StringUtils.equals(selectedRecordTypes, "items")) {
			/*
			 * Select only item records (as a convenience) when generically identified. This can be
			 * helpful when users are exporting against a very large number of files. In that case,
			 * dropping the header and trailer records can reduce the size of the output file.
			 */
			for (int rt = 0, n = isRecordTypeSelected.length; rt < n; rt++) {
				final boolean isItemRecord = (rt >= X9.CHECK_DETAIL && rt <= X9.IMAGE_VIEW_DATA)
						|| rt == X9.CREDIT || rt == X9.CREDIT_RECONCILIATION;
				isRecordTypeSelected[rt] = isItemRecord;
			}
		} else {
			/*
			 * Set selected record types.
			 */
			final String[] recordTypes = StringUtils.split(selectedRecordTypes, '|');
			for (final String rt : recordTypes) {
				if (StringUtils.isNumeric(rt)) {
					final int type = X9Numeric.toInt(rt);
					if (type >= 1 && type <= X9.HIGHEST_RECORD_TYPE) {
						isRecordTypeSelected[type] = true;
					} else {
						throw X9Exception.abort("recordType({}) out of bounds", type);
					}
				} else {
					throw X9Exception.abort("recordType({}) not numeric", rt);
				}
			}
		}

		/*
		 * Create an sdk instance and then bind to an x9 configuration.
		 */
		sdk = X9SdkFactory.getSdk(sdkBase);
		workUnit.bindToCommandLineConfiguration(sdkBase);

		/*
		 * Run the export.
		 */
		X9ExportTotals x9exportTotals = null;
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		try {
			if (isMultiFile) {
				/*
				 * Export from multiple x9 files to a single csv with optional image folder.
				 */
				final String[] inputFileExtensions = workUnit
						.validateInputFileExtensions(inputExtensions);
				final List<X9File> fileList = X9FileUtils.createInputFileList(workUnit.inputFile,
						X9FileUtils.SUBFOLDERS_INCLUDED, inputFileExtensions,
						workUnit.getFileSkipInterval(), X9FileUtils.LOG_SELECTED_FILES_ENABLED);

				/*
				 * Calculate total bytes across all files.
				 */
				final long totalByteCount = X9FileUtils.calculateTotalBytes(fileList);

				/*
				 * Run the multi-export to csv with optional images.
				 */
				final X9ExportMulti x9exportMulti = new X9ExportMulti(sdkBase, this);
				x9exportMulti.setLoggingEnabled(isLoggingEnabled);
				x9exportTotals = x9exportMulti.export(fileList, outputFile, baseImageFolder,
						totalByteCount);
			} else if (isXmlExport) {
				/*
				 * Export from x9 to xml with optional images.
				 */
				final X9ExportToXml x9exportToXml = new X9ExportToXml(sdkBase, this);
				x9exportToXml.setLoggingEnabled(isLoggingEnabled);
				x9exportTotals = x9exportToXml.exportFromFile(inputFile, outputFile,
						baseImageFolder);
			} else {
				/*
				 * Export from a single x9 to csv with optional images.
				 */
				try (final X9CsvWriter csvWriter = new X9CsvWriter(outputFile);
						final X9ExportFile x9exportFile = new X9ExportFile(sdk, csvWriter, this)) {
					x9exportFile.setLoggingEnabled(isLoggingEnabled);
					x9exportTotals = x9exportFile.exportFromFile(inputFile, baseImageFolder);
				} catch (final Exception ex) {
					throw X9Exception.abort(ex);
				}
			}

			/*
			 * Abort when there was no input and awe (abort when empty) was enabled.
			 */
			if (x9exportTotals == null || x9exportTotals.inputCount == 0) {
				/*
				 * Optionally abort on an empty input file.
				 */
				final long inputFileLength = inputFile.length();
				if (isAbortWhenEmpty && inputFileLength == 0) {
					workUnit.abortOnEmptyFile();
				}

				/*
				 * Always abort on a structurally flawed file.
				 */
				if (inputFileLength > 0) {
					workUnit.abortOnStructurallyFlawedFile();
				}
			}
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Rename on completion.
				 */
				outputTempFile.renameTemp();
			} catch (final Exception ex) {
				/*
				 * Set message when aborted.
				 */
				x9totalsXml.setAbortMessage(ex.toString());
				throw X9Exception.abort(ex);
			} finally {
				/*
				 * Populate our file (input) totals when they are available.
				 */
				if (x9exportTotals != null) {
					x9totalsXml.setTotals(inputFile, x9exportTotals);
				}

				/*
				 * Write high level summary totals when requested by command line switches.
				 */
				workUnit.writeSummaryTotals(x9totalsXml);

				if (x9exportTotals == null) {
					LOGGER.error("export aborted!");
				} else {
					String exportSummary = "export finished; inputFile(" + inputFile
							+ ") inputRecords(" + X9D.formatLong(x9exportTotals.inputCount)
							+ ") outputCount(" + X9D.formatLong(x9exportTotals.outputCount)
							+ ") debits(" + X9D.formatLong(x9exportTotals.debitCount) + ") amount("
							+ X9D.formatDollarAmount(x9exportTotals.debitAmount) + ")";
					if (x9exportTotals.creditCount > 0) {
						exportSummary += " credits(" + X9D.formatLong(x9exportTotals.creditCount)
								+ ") amount(" + X9D.formatDollarAmount(x9exportTotals.creditAmount)
								+ ")";
					}
					if (x9exportTotals.imageCount > 0) {
						exportSummary += " images(" + X9D.formatLong(x9exportTotals.imageCount)
								+ ")";
					}
					LOGGER.info(exportSummary);
				}
			}
		}

		/*
		 * Return exit status zero.
		 */
		return X9UtilBatch.EXIT_STATUS_ZERO;
	}

	@Override
	public boolean isConfigAuto() {
		return workUnit.isBindConfigurationAuto();
	}

	@Override
	public boolean isRecordSelected(final X9Object x9o) {
		final int recordType = x9o.x9ObjType;
		return recordType < isRecordTypeSelected.length ? isRecordTypeSelected[recordType] : false;
	}

	@Override
	public boolean isExportCsv() {
		return isCsvExport;
	}

	@Override
	public boolean isExportAsItems() {
		return isItemExport;
	}

	@Override
	public boolean isExportAsGroups() {
		return isGroupExport;
	}

	@Override
	public boolean isExportTiffTags() {
		return isTiffTagExport;
	}

	@Override
	public boolean isImageExport() {
		return isImageExport;
	}

	@Override
	public char getImageExportDirective() {
		final char imageExportDirective;
		if (isImageExportRelative) {
			imageExportDirective = X9ExportImages.IMAGE_EXPORT_RELATIVE;
		} else if (isImageBase64Basic) {
			imageExportDirective = X9ExportImages.IMAGE_BASE64_BASIC;
		} else if (isImageBase64Mime) {
			imageExportDirective = X9ExportImages.IMAGE_BASE64_MIME;
		} else {
			imageExportDirective = X9ExportImages.IMAGE_EXPORT_ABSOLUTE;
		}
		return imageExportDirective;
	}

	@Override
	public String getImageExportFormat() {
		final String imageFormat;
		if (isImageExportTif) {
			imageFormat = X9C.TIF;
		} else if (isImageExportPng) {
			imageFormat = X9C.PNG;
		} else if (isImageExportJpg) {
			imageFormat = X9C.JPG;
		} else if (isImageExportGif) {
			imageFormat = X9C.GIF;
		} else {
			imageFormat = X9C.TIF; // default to TIFF when nothing else specified
		}
		return imageFormat;
	}

	@Override
	public boolean isClearImageFolders() {
		return false;
	}

	@Override
	public boolean isExportImagesIrdFormat() {
		return isImageExportIrdFormat;
	}

	@Override
	public boolean isExportMultiPageTiffs() {
		return isMultiPageTiffExport;
	}

	@Override
	public boolean isExportMultiPageIRDs() {
		return isMultiPageIrdExport;
	}

	@Override
	public int getImagesPerFolder() {
		return imagesPerFolderCount;
	}

	@Override
	public boolean isCsvFormatQuoted() {
		return false;
	}

	@Override
	public boolean isCsvInsertColumnHeadersAsFirstRow() {
		return isItemExportWithColumnHeaders;
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
		return isXmlFlat;
	}

	@Override
	public boolean isXmlFormatHierarchical() {
		return isXmlHierarchical;
	}

	@Override
	public boolean isXmlIncludeEmptyFields() {
		return isXmlIncludeEmptyFields;
	}

	@Override
	public boolean isDecimalPointInAmounts() {
		return isDecimalPointsInAmounts;
	}

	@Override
	public boolean isAsIs() {
		return isAsIs;
	}

}
