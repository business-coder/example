package com.x9ware.utilities;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9Item937;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9ObjectManager;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkImageBundle;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkObjectFactory;
import com.x9ware.beans.X9UtilExportCsvBean;
import com.x9ware.config.X9ConfigSelector;
import com.x9ware.core.X9;
import com.x9ware.core.X9Reader;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.elements.X9C;
import com.x9ware.export.X9ExportFile;
import com.x9ware.export.X9ExportImages;
import com.x9ware.fields.X9Field;
import com.x9ware.fields.X9FieldManager;
import com.x9ware.imageio.X9ImageInfo;
import com.x9ware.micr.X9MicrOnUs;
import com.x9ware.records.X9RecordDotField;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilExportCsv is part of our utilities package which reads an x9 file and exports items into a
 * user defined csv format. One or more output formats can be defined within a centralized xml file,
 * which is especially suited for processors with multiple endpoints and varying csv layouts. The
 * ability to define the actual csv fields to be exported (and their relative sequence) is what
 * differentiates us from the more generic -export function. There are numerous other differences.
 * For example, -exportCsv can only be used to export a single file, while then more general -export
 * function can export multiple files in a single run. Finally, note that the xml format includes
 * all export parameters, making this a self-defined work unit. Images can be exported using the
 * same options as provided by the -export function.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilExportCsv {

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
	private final boolean isLoggingEnabled;
	private final X9SdkImageBundle sdkImageBundle = new X9SdkImageBundle();
	private final X9TrailerManager x9trailerManager;
	private final X9ExportImages x9exportImages;
	private X9UtilExportCsvBean.Format exportFormat;
	private String imageExportMethod;
	private String imageExportFormat;
	private File x9inputFile;
	private File csvOutputFile;
	private File imageFolder;
	private int outputItemCount;
	private int outputImageCount;

	/*
	 * Constants.
	 */
	private static final String AUTO = "auto";
	private static final String NONE = "none";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilExportCsv.class);

	/*
	 * X9UtilExportCsv Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilExportCsv(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		x9trailerManager = new X9TrailerManager937(sdkBase); // accumulate input file totals
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);

		/*
		 * Get the export format to be used based on command line options.
		 */
		getExportFormat(); // this must always be found since it drives everything

		/*
		 * Allocate our image export tool when needed.
		 */
		final boolean isImageExportDisabled = StringUtils.equals(imageExportMethod, NONE)
				|| StringUtils.equals(imageExportFormat, NONE);
		x9exportImages = isImageExportDisabled ? null : new X9ExportImages(sdkBase);
		LOGGER.info("isImageExportEnabled({}) imageExportMethod({}) imageExportFormat({})",
				!isImageExportDisabled, imageExportMethod, imageExportFormat);
	}

	/**
	 * Export an existing x9 file to a user defined csv output format.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Reset export image when images are actually being exported.
		 */
		if (x9exportImages != null) {
			x9exportImages.resetForNewFile();
		}

		/*
		 * Get the output csv file, which may be time-stamped per the output definition. We look to
		 * the export format for this assignment, and turn to the command line when not present.
		 */
		final String outputFile = StringUtils.isBlank(exportFormat.csvFileName)
				? workUnit.outputFile.toString()
				: exportFormat.csvFileName;
		if (StringUtils.isBlank(outputFile)) {
			throw X9Exception.abort("outputFile not assigned; this can be provided either "
					+ "by the export format or the command line");
		}
		final X9TempFile csvTempFile = X9UtilWorkUnit.getTempFileWithOptionalTimestamp(outputFile,
				exportFormat.dateTimeStamp, exportFormat.doNotRewrite);
		csvOutputFile = csvTempFile.getTemp();

		/*
		 * Bind to user selected configuration with auto-bind.
		 */
		x9inputFile = workUnit.inputFile;
		final String formatConfigName = exportFormat.configName;
		if (StringUtils.equals(formatConfigName, AUTO)) {
			/*
			 * Use auto-bind to select the most applicable configuration based on content.
			 */
			LOGGER.info("autoBind active");
			X9ConfigSelector.autoBindToConfiguration(sdkBase, x9inputFile);
		} else {
			/*
			 * Bind to the selected configuration and default to x9.37 dstu.
			 */
			final String configName = StringUtils.isBlank(formatConfigName) ? X9.X9_37_CONFIG
					: formatConfigName;
			if (!sdkBase.bindConfiguration(configName)) {
				throw X9Exception.abort("bind unsuccessful({})", configName);
			}
		}

		/*
		 * Load the x9 input file into the heap.
		 */
		final int itemCount = loadInputFile(x9inputFile);
		LOGGER.info("file loaded({}) itemCount({})", x9inputFile, itemCount);

		/*
		 * Locate the image export folder and clear it when directed.
		 */
		if (x9exportImages != null) {
			final String baseImageFolder = StringUtils.isBlank(exportFormat.imageFolder)
					? workUnit.imageFolder.toString()
					: exportFormat.imageFolder;
			if (StringUtils.isNotBlank(baseImageFolder)) {
				final String baseName = FilenameUtils.getBaseName(x9inputFile.toString());
				final File baseFolder = new File(baseImageFolder);
				imageFolder = x9exportImages.assignAndClearImageFolder(baseFolder, baseName, null,
						exportFormat.clearImageFolder);
			} else {
				imageFolder = null;
			}
		}

		/*
		 * Export to csv.
		 */
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9CsvWriter csvWriter = new X9CsvWriter(csvOutputFile)) {
			/*
			 * Update processing.
			 */
			exportProcessor(sdkIO, csvWriter);
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
				csvTempFile.renameTemp();
			} catch (final Exception ex) {
				/*
				 * Set message when aborted.
				 */
				x9totalsXml.setAbortMessage(ex.toString());
				throw X9Exception.abort(ex);
			} finally {
				/*
				 * Populate our file totals.
				 */
				x9totalsXml.setTotals(x9inputFile, x9trailerManager);

				/*
				 * Write summary totals when requested.
				 */
				workUnit.writeSummaryTotals(x9totalsXml, exportFormat.summaryXmlFile,
						exportFormat.summaryTxtFile, exportFormat.summaryJsonFile);
			}

			/*
			 * Write our output message.
			 */
			LOGGER.info("exportCsv {}", x9totalsXml.getTotalsString());
		}

		/*
		 * Release all sdkBase storage (since we loaded the file to the heap).
		 */
		sdkBase.systemReset();

		/*
		 * Return exit status zero.
		 */
		return X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * Read an x9 file (record by record) which is stored in an internal list and then used to
	 * subsequently create an output x9 file.
	 *
	 * @param csvWriter
	 *            csvWriter for the output csv file
	 * @throws Exception
	 */
	private void exportProcessor(final X9SdkIO sdkIO, final X9CsvWriter csvWriter)
			throws Exception {
		/*
		 * Check if we are exporting images.
		 */
		if (x9exportImages != null) {
			/*
			 * Set the image folder.
			 */
			sdkBase.setImageFolder(imageFolder, true);

			/*
			 * Set image export option as either relative or absolute.
			 */
			sdkIO.setExportedFileNamesRelative(StringUtils.equals(imageExportMethod,
					Character.toString(X9ExportImages.IMAGE_EXPORT_RELATIVE)));
		}

		/*
		 * Allocate csv column headers when directed.
		 */
		final List<X9UtilExportCsvBean.Output> exportFieldList = exportFormat.fields.outputList;
		if (exportFormat.includeColumnHeaders) {
			csvWriter.allocateColumnNameList(exportFieldList.size());
		}

		/*
		 * Export all items.
		 */
		X9Object x9oFileHeader = null;
		X9Object x9oCashLetterHeader = null;
		X9Object x9oBundleHeader = null;
		final X9ObjectManager x9objectManager = sdkBase.getObjectManager();
		X9Object x9o = x9objectManager.getFirst();
		while (x9o != null) {
			/*
			 * Accumulate and roll totals.
			 */
			x9trailerManager.accumulateAndRollTotals(x9o);

			/*
			 * Log when enabled via a command line switch.
			 */
			if (isLoggingEnabled) {
				LOGGER.info("x9 recordNumber({}) content({})", x9o.x9ObjIdx,
						new String(x9o.x9ObjData));
			}

			/*
			 * Process by record type.
			 */
			switch (x9o.x9ObjType) {
				/*
				 * Save the file header so we can do a look-back to those values.
				 */
				case X9.FILE_HEADER: {
					x9oFileHeader = x9o;
					break;
				}

				/*
				 * Save the cash letter header so we can do a look-back to those values.
				 */
				case X9.CASH_LETTER_HEADER: {
					x9oCashLetterHeader = x9o;
					break;
				}

				/*
				 * Save the bundle header so we can do a look-back to those values.
				 */
				case X9.BUNDLE_HEADER: {
					x9oBundleHeader = x9o;
					break;
				}

				case X9.CHECK_DETAIL:
				case X9.RETURN_DETAIL:
				case X9.CREDIT:
				case X9.CREDIT_RECONCILIATION: {
					/*
					 * Export images when needed and get a file name list, which can contain image
					 * file names or base64 image strings.
					 */
					List<String> imageFileNameList = null;
					if (x9exportImages != null) {
						/*
						 * Export the images per stated options.
						 */
						final int bundleRecordNumber = x9oBundleHeader == null ? 0
								: x9oBundleHeader.x9ObjIdx;
						imageFileNameList = exportImages(bundleRecordNumber, x9o);

						if (LOGGER.isDebugEnabled()) {
							for (final String imageFileName : imageFileNameList) {
								LOGGER.debug(
										"imageExportMethod({}) imageFormat({}) imageFileName({})",
										imageExportMethod, exportFormat.imageFormat, imageFileName);
							}
						}
					}

					/*
					 * Allocate an X9Item937 instance to provide access to several fields.
					 */
					final X9Item937 x9item937 = new X9Item937(x9o);

					/*
					 * Parse micr OnUs into subfields.
					 */
					final String micrOnUs = x9item937.getOnus();
					final X9MicrOnUs x9micrOnUs = new X9MicrOnUs(micrOnUs);

					/*
					 * Walk all csv output fields that are defined for this format.
					 */
					csvWriter.startNewLine();
					for (final X9UtilExportCsvBean.Output exportField : exportFieldList) {
						/*
						 * Assign the logical field name, which is only needed to allow the row one
						 * title line to be formatted and included in the output csv.
						 */
						final String fieldIdentifier = exportField.field;
						final String fieldValue;
						String fieldName = getColumnName(exportField, fieldIdentifier);
						if (StringUtils.equals(fieldIdentifier, "FrontImage")) {
							fieldValue = imageFileNameList != null && imageFileNameList.size() >= 1
									? imageFileNameList.get(0)
									: "";
						} else if (StringUtils.equals(fieldIdentifier, "BackImage")) {
							fieldValue = imageFileNameList != null && imageFileNameList.size() >= 2
									? imageFileNameList.get(1)
									: "";
						} else if (StringUtils.equals(fieldIdentifier, "MicrRouting")) {
							fieldValue = x9item937.getRouting();
						} else if (StringUtils.equals(fieldIdentifier, "AccountNumber")) {
							fieldValue = x9micrOnUs.getAccount();
						} else if (StringUtils.equals(fieldIdentifier, "CheckNumber")) {
							fieldValue = X9ExportFile.getCheckNumber(x9item937, x9micrOnUs);
						} else if (StringUtils.equals(fieldIdentifier, "ReturnReason")) {
							fieldValue = x9item937.getReturnReason();
						} else {
							final X9Field x9field = getReferencedField(fieldIdentifier);
							fieldName = getColumnName(exportField, x9field);
							fieldValue = getFieldValue(x9field, x9o, x9oFileHeader,
									x9oCashLetterHeader, x9oBundleHeader);
						}

						/*
						 * Add the field name and field value for the current user defined csv
						 * field. The field name is used to create the column headers line, which is
						 * optional and contains either the user defined name with a fallback to the
						 * rules specification field name.
						 */
						csvWriter.addColumnName(fieldName);
						csvWriter.addField(fieldValue);
					}

					/*
					 * Write the accumulated csv line for this item.
					 */
					csvWriter.write();
					break;
				}

				default: {
					break;
				}

			}

			/*
			 * Get the next record.
			 */
			x9o = x9o.getNext();
		}

		LOGGER.info("exportCsv finished itemCount({}) imageCount({})", outputItemCount,
				outputImageCount);
	}

	/**
	 * Get the referenced x9field for a given record dot field identifier.
	 *
	 * @param recordDotField
	 *            current record dot field
	 * @return referenced x9field
	 */
	private X9Field getReferencedField(final String recordDotField) {
		/*
		 * Validate record type and field number.
		 */
		final int recordType = X9RecordDotField.getRecordType(recordDotField);
		final int fieldNumber = X9RecordDotField.getFieldNumber(recordDotField);

		if (recordType <= 0) {
			throw X9Exception.abort("invalid record type{})", recordDotField);
		}

		if (fieldNumber <= 0) {
			throw X9Exception.abort("invalid field number{})", recordDotField);
		}

		/*
		 * Get the x9field definition from the record dot field identifier.
		 */
		final X9FieldManager x9fieldManager = sdkBase.getFieldManager();
		final X9Field x9field = x9fieldManager.getFieldObject(recordDotField);
		if (x9field == null) {
			throw X9Exception.abort("field not found recordDotField({})", recordDotField);
		}

		/*
		 * Return the referenced x9field.
		 */
		return x9field;
	}

	/**
	 * Get the value for a specific field which is identified using the record dot field format. The
	 * desired field reference can be within the preceding header records, from this item record, or
	 * from several addenda record types that are attached to this item (types 26, 32, 50, and 52)
	 * which are accessed via the object manager (heap).
	 *
	 * @param x9field
	 *            x9field for the field to be referenced
	 * @param x9oItem
	 *            current item
	 * @param x9oFileHeader
	 *            current file header
	 * @param x9oCashLetterHeader
	 *            current cash letter header
	 * @param x9oBundleHeader
	 *            current bundle header
	 * @return formulated split key for this item or null when the item does not match criteria
	 */
	private String getFieldValue(final X9Field x9field, final X9Object x9oItem,
			final X9Object x9oFileHeader, final X9Object x9oCashLetterHeader,
			final X9Object x9oBundleHeader) {
		/*
		 * Get the field value from the targeted record.
		 */
		final int recordType = x9field.getRecordType();
		final X9Object x9oTarget;
		switch (recordType) {
			case X9.FILE_HEADER: {
				x9oTarget = x9oFileHeader; // handled later when unexpectedly null
				break;
			}

			case X9.CASH_LETTER_HEADER: {
				x9oTarget = x9oCashLetterHeader; // handled later when unexpectedly null
				break;
			}

			case X9.BUNDLE_HEADER: {
				x9oTarget = x9oBundleHeader; // handled later when unexpectedly null
				break;
			}

			case X9.CHECK_DETAIL:
			case X9.RETURN_DETAIL:
			case X9.CREDIT:
			case X9.CREDIT_RECONCILIATION: {
				x9oTarget = x9oItem; // handled later when unexpectedly null
				break;
			}

			case X9.CHECK_ADDENDUM_A: // type 26
			case X9.CHECK_ADDENDUM_C: // type 28
			case X9.RETURN_ADDENDUM_A: // type 32
			case X9.RETURN_ADDENDUM_B: // type 33
			case X9.RETURN_ADDENDUM_D: // type 35
			case X9.IMAGE_VIEW_DETAIL: // type 50
			case X9.IMAGE_VIEW_DATA: { // type 52
				/*
				 * We have to walk forward to get these addenda records (the desired record must
				 * immediately follow the current item), as part of this record group. For example,
				 * a type 26 within a forward presentment file, or a type 33 for a returns file.
				 */
				X9Object x9o = x9oItem;
				int count = x9o.countRecordsInGroup();
				while (x9o != null && count > 0) {
					/*
					 * Search for the first addenda record of the required type.
					 */
					if (recordType == x9o.x9ObjType) {

						break;
					}

					/*
					 * Decrement and get next.
					 */
					count--;
					x9o = x9o.getNext();
				}

				/*
				 * Set the target record type when located and otherwise assign null.
				 */
				x9oTarget = x9o == null || recordType != x9o.x9ObjType ? null : x9o;
				break;
			}

			default: {
				throw X9Exception.abort("incorrect recordType({})", recordType);
			}

		}

		/*
		 * Return null when this field references a record type that does not exist. This might be a
		 * reference to a type 26 record that does not exist, or it could be a reference to a
		 * preceding type 01 or 10 record that does not exist due to a malformed file.
		 */
		if (x9oTarget == null) {
			return null;
		}

		/*
		 * Return null when the located record type does not match the identified target item. The
		 * most likely situation is criteria against a credit (61/62) record type when we are
		 * currently positioned on a type 25 record.
		 */
		if (recordType != x9oTarget.x9ObjType) {
			return null;
		}

		/*
		 * Get the field value and optionally insert a decimal point when it is an amount.
		 */
		return exportFormat.includeDecimalPoints
				? x9field.getValueWithDecimalPointWhenAmount(x9oTarget.x9ObjData)
				: x9field.getValueToUpper(x9oTarget);
	}

	/**
	 * Export the images for the current item based on user xml directives. The single multi-page
	 * image exports will have their results stored in the front image name, with the second image
	 * name then blank (omitted). Also, note that all of these image options can have their file
	 * names as absolute or relative, and can also be generated and returned as base64 strings.
	 *
	 * @param bundleRecordNumber
	 *            current bundle record number
	 * @param x9oItem
	 *            x9o of current item
	 * @return string list of exported image names or base64 strings
	 */
	private List<String> exportImages(final int bundleRecordNumber, final X9Object x9oItem) {
		/*
		 * Build an sdkObject list for all records attached to this item, which is a common list
		 * format that will be needed to export the images.
		 */
		X9Object x9o = x9oItem;
		int count = x9o.countRecordsInGroup();
		final BigDecimal itemAmount = x9oItem.getRecordAmount();
		final X9SdkObjectFactory sdkObjectFactory = sdkBase.getSdkObjectFactory();
		final List<X9SdkObject> sdkObjectList = new ArrayList<>(count);
		final List<String> imageFileNameList = new ArrayList<>();
		while (x9o != null && count > 0) {
			/*
			 * Allocate another sdkObject for this item and add it to the list.
			 */
			final X9SdkObject sdkObject = sdkObjectFactory.create(x9o);
			sdkObject.setCheckImage(x9o.getDirectlyAttachedImage());
			sdkObjectList.add(sdkObject);

			/*
			 * Assign the relative image file name.
			 */
			final byte[] imageByteArray = x9o.getDirectlyAttachedImage();
			final String imageRelativeFileName = imageByteArray == null ? ""
					: sdkObject.assignRelativeImageName(sdkImageBundle, bundleRecordNumber,
							itemAmount, x9o.getImageSet(),
							X9ImageInfo.getImageFormatExtension(imageByteArray));

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug(
						"adding sdkObject for recordType({}) hasDirectlyAttachedImage({}) "
								+ "imageRelativeFileName({})",
						x9o.x9ObjType, x9o.hasDirectlyAttachedImage(), imageRelativeFileName);
			}

			/*
			 * Decrement and get next.
			 */
			count--;
			x9o = x9o.getNext();
		}

		/*
		 * We now invoke X9ExportImages to do all of the hard work for us.
		 */
		if (x9exportImages != null) {
			final char imageExportDirective = imageExportMethod.charAt(0);
			if (StringUtils.equals(imageExportMethod,
					X9UtilWorkUnit.SWITCH_MULTIPAGE_TIFF_EXPORT)) {
				/*
				 * Write a single image which is a multi-page tiff of the front-back images.
				 */
				x9exportImages.writeComplexImageExports(X9ExportImages.ComplexImageExports.MPTIF,
						imageFolder, sdkObjectList, imageFileNameList, X9C.TIF,
						imageExportDirective);
			} else if (StringUtils.equals(imageExportMethod,
					X9UtilWorkUnit.SWITCH_MULTIPAGE_IRD_EXPORT)) {
				/*
				 * Write a single image which is a multi-page tiff of the front-back IRD images.
				 */
				x9exportImages.writeComplexImageExports(X9ExportImages.ComplexImageExports.MPIRD,
						imageFolder, sdkObjectList, imageFileNameList, X9C.TIF,
						imageExportDirective);
			} else if (imageExportDirective == X9ExportImages.IMAGE_EXPORT_ABSOLUTE
					|| imageExportDirective == X9ExportImages.IMAGE_EXPORT_RELATIVE
					|| imageExportDirective == X9ExportImages.IMAGE_BASE64_BASIC
					|| imageExportDirective == X9ExportImages.IMAGE_BASE64_MIME) {
				/*
				 * Write all attached images (could be front-back, but also supplemental gray
				 * scale).
				 */
				x9exportImages.writeItemImages(imageFolder, sdkObjectList, imageFileNameList,
						imageExportFormat, imageExportDirective);
			} else {
				/*
				 * Abort when we do not recognize the image export method.
				 */
				throw X9Exception.abort("unrecognized imageExportMethod({})", imageExportMethod);
			}

			/*
			 * Count the number of images that were actually written (up to two).
			 */
			for (int i = 0, n = Math.min(imageFileNameList.size(), 2); i < n; i++) {
				final String imageFileName = imageFileNameList.get(i);
				if (StringUtils.isNotBlank(imageFileName)) {
					outputImageCount++;
				}
			}
		}

		/*
		 * Return the constructed image file name list, which may contain absolute file names,
		 * relative file names, or actual images in base64 format.
		 */
		return imageFileNameList;
	}

	/**
	 * Get the name to be assigned to the current column while defaulting to x9field.
	 *
	 * @param exportField
	 *            current xml field being exported
	 * @param x9field
	 *            x9field for the field to be referenced
	 * @return column name
	 */
	private String getColumnName(final X9UtilExportCsvBean.Output exportField,
			final X9Field x9field) {
		return getColumnName(exportField, StringUtils.remove(x9field.getName(), ' '));
	}

	/**
	 * Get the name to be assigned to the current column with a default otherwise assigned.
	 *
	 * @param exportField
	 *            current xml field being exported
	 * @param defaultColumnName
	 *            default column name to be assigned
	 * @return column name
	 */
	private String getColumnName(final X9UtilExportCsvBean.Output exportField,
			final String defaultColumnName) {
		return StringUtils.isNotBlank(exportField.columnName) ? exportField.columnName
				: defaultColumnName;
	}

	/**
	 * Get the export to be utilized (invoked from our constructor).
	 */
	private void getExportFormat() {
		/*
		 * Get export controls that contains export format definitions.
		 */
		final String exportControls = workUnit.x9commandLine
				.getSwitchValue(X9UtilWorkUnit.SWITCH_EXPORT_CONTROLS);
		if (StringUtils.isBlank(exportControls)) {
			throw X9Exception.abort("export controls missing");
		}

		/*
		 * Get the specific format name to be used.
		 */
		final String exportName = workUnit.x9commandLine
				.getSwitchValue(X9UtilWorkUnit.SWITCH_EXPORT_FORMAT);
		if (StringUtils.isBlank(exportName)) {
			throw X9Exception.abort("export format name missing");
		}

		/*
		 * Read the xml document and get the export csv bean; abort if not found.
		 */
		final X9UtilExportCsvXml exportCsvXml = new X9UtilExportCsvXml();
		final X9UtilExportCsvBean exportCsvBean = exportCsvXml
				.readExternalXmlFile(new File(exportControls));
		if (exportCsvBean == null) {
			throw X9Exception.abort("exportControls({}) not found", exportControls);
		}

		/*
		 * Log input xml when requested.
		 */
		if (isLoggingEnabled) {
			LOGGER.info(exportCsvBean.toString());
		}

		/*
		 * Get the export format to be utilized; abort if not found.
		 */
		exportFormat = exportCsvXml.getExportFormat(exportName);
		if (exportFormat == null) {
			throw X9Exception.abort("exportName({}) not found", exportName);
		}

		/*
		 * Make sure the image export method is valid.
		 */
		imageExportMethod = exportFormat.imageMethod;
		imageExportMethod = imageExportMethod == null
				|| StringUtils.equalsAny(imageExportMethod, NONE, "n", "") ? NONE
						: imageExportMethod;

		final char imageMethod = imageExportMethod.charAt(0);
		final boolean isImageMethodValid = imageMethod == X9ExportImages.IMAGE_EXPORT_NONE
				|| imageMethod == X9ExportImages.IMAGE_EXPORT_ABSOLUTE
				|| imageMethod == X9ExportImages.IMAGE_EXPORT_RELATIVE
				|| imageMethod == X9ExportImages.IMAGE_BASE64_BASIC
				|| imageMethod == X9ExportImages.IMAGE_BASE64_MIME;
		if (!isImageMethodValid) {
			throw X9Exception.abort("exportName({}) invalid imageExportMethod({})", exportName,
					imageExportMethod);
		}

		/*
		 * Make sure the image export format is valid.
		 */
		imageExportFormat = exportFormat.imageFormat;
		imageExportFormat = imageExportFormat == null
				|| StringUtils.equalsAny(imageExportFormat, NONE, "n", "") ? NONE
						: imageExportFormat;
		if (!StringUtils.equalsAny(imageExportFormat, NONE, X9C.TIF, X9C.PNG, X9C.JPG, X9C.GIF)) {
			throw X9Exception.abort("exportName({}) invalid imageExportFormat({})", exportName,
					imageExportFormat);
		}
	}

	/**
	 * Load the input x9.37 input file to the heap using X9ObjectManager. We load the file since
	 * fields can be exported from addenda records that follow the item itself (type 26, 32, 33, 50,
	 * 52). Loading the file to the heap also allows us to use the X9Item937 heap walker.
	 *
	 * @param inputFile
	 *            input file to be loaded
	 * @return item count
	 */
	private int loadInputFile(final File inputFile) {
		outputItemCount = 0;
		int recordCount = 0;
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase);
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
				 * Store all x9objects on the heap.
				 */
				recordCount++;
				final X9Object x9o = sdkIO.createAndStoreX9Object();

				if (x9o.isItem()) {
					/*
					 * Count input items.
					 */
					outputItemCount++;
				} else if (x9o.isRecordType(X9.IMAGE_VIEW_DATA)) {
					/*
					 * Attach images directly to the type 52 records so we can write them later.
					 */
					x9o.setDirectlyAttachedImage(x9reader.getImageBuffer());
				}

				/*
				 * Continue reading the input file.
				 */
				sdkObject = sdkIO.readNext();
			}

			LOGGER.info("inputFile({}) loaded recordCount({})", inputFile, recordCount);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Return the item count.
		 */
		return outputItemCount;
	}

}
