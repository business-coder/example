package com.x9ware.utilities;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.beans.X9HeaderAttr937;
import com.x9ware.core.X9;
import com.x9ware.core.X9HeaderXml937;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.core.X9Writer;
import com.x9ware.error.X9Error;
import com.x9ware.records.X9Credit;
import com.x9ware.tools.X9CsvLine;
import com.x9ware.tools.X9CsvReader;
import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9String;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager;

/**
 * X9UtilWriter is part of our utilities package which builds an x9 output file on a field by field
 * basis from a user provided csv file. Our design is twofold. First is to simplify this process as
 * much as possible (eg, we will automatically create the headers and trailers, and we also control
 * all of the bundling). Second is to provide as much flexibility as possible (eg, by still
 * providing the ability to support both ICL and ICLR and to optionally create addendum records).
 * The image records (type 50 and 52) are easily created using an image record that explicitly
 * defines the fully qualified file name for the front and back image. These images files can be
 * TIFF (where they must be a compliant x9.100-187 tiff image and will be written exactly as
 * provided) or can be some other image format (PNG, JPG, BMP, or GIF) and will be converted to an
 * x9 compliant tiff. X9UtilWriter faithfully creates the output file using the fields as provided
 * on the user input csv file. We very purposefully do not validate those fields for content. We do
 * incorporate all logic needed to justify and pad fields appropriately, and will truncate to the
 * maximum field length if the data length is excessive. But we otherwise always attempt to pass the
 * data through exactly as present from the csv file. This is the same result if the user was using
 * our SDK to directly create the x9 file. In that situation, it would be their requirement to
 * ensure that appropriate field values are being populated. You should use X9Assist during your
 * initial testing process to ensure that you are setting appropriate field values and that your
 * images are fully compliant.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilWriter {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/**
	 * X9HeaderXml937 instance.
	 */
	private final X9HeaderXml937 x9headerXml937 = new X9HeaderXml937();

	/*
	 * Private.
	 */
	private final boolean isLoggingEnabled;
	private X9Sdk sdk;
	private File csvInputFile;
	private File x9outputFile;
	private List<X9Error> processingErrorList;

	/*
	 * Constants.
	 */
	private static final char COMMA = ',';
	private static final int TYPE25_AMOUNT_INDEX = 6;
	private static final int TYPE25_SEQUENCE_NUMBER_INDEX = 7;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilWriter.class);

	/*
	 * X9UtilWriter Constructor.
	 *
	 * @param work_Unit current work unit
	 */
	public X9UtilWriter(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
	}

	/**
	 * Write an x937 file. We have a csv file as input and an x9 file as output.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Get work unit files.
		 */
		csvInputFile = workUnit.inputFile;
		final X9TempFile outputTempFile = workUnit.getOutputTempFileUsingDtsDnrSwitches();
		x9outputFile = outputTempFile.getTemp();

		/*
		 * Create our sdk instance.
		 */
		sdk = X9SdkFactory.getSdk(sdkBase);

		/*
		 * Write a new x9 file.
		 */
		X9TrailerManager trailerManager = null;
		boolean isAborted = false;
		final X9TotalsXml x9totalsXml = new X9TotalsXml();
		try (final X9SdkIO sdkIO = sdk.getSdkIO();
				final X9CsvReader csvReader = sdkIO.openCsvInputFile(csvInputFile)) {
			/*
			 * Writer processing.
			 */
			trailerManager = processWriter(sdkIO);
		} catch (final Exception ex) {
			/*
			 * Set message when aborted.
			 */
			isAborted = true;
			x9totalsXml.setAbortMessage(ex.toString());
			throw X9Exception.abort(ex);
		} finally {
			try {
				/*
				 * Rename on completion (when the write operation was not aborted).
				 */
				if (!isAborted) {
					outputTempFile.renameTemp();
				}
			} catch (final Exception ex) {
				/*
				 * Set message when aborted.
				 */
				x9totalsXml.setAbortMessage(ex.toString());
				throw X9Exception.abort(ex);
			} finally {
				/*
				 * Write totals when available and requested by command line switches.
				 */
				if (trailerManager != null) {
					x9totalsXml.setTotals(workUnit.outputFile, trailerManager);
					workUnit.writeSummaryTotals(x9totalsXml);
					LOGGER.info("writer {}", x9totalsXml.getTotalsString());
				}
			}
		}

		/*
		 * Return final exit status.
		 */
		return processingErrorList != null && processingErrorList.size() > 0
				? X9UtilBatch.EXIT_STATUS_ERROR
				: X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * Get the processing error list.
	 * 
	 * @return list of processing errors (never null)
	 */
	public List<X9Error> getProcessingErrorList() {
		return processingErrorList;
	}

	/**
	 * File writer processing with exception thrown on any errors.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 * @return x9trailermanager instance for our totals
	 * @throws Exception
	 */
	private X9TrailerManager processWriter(final X9SdkIO sdkIO) throws Exception {
		/*
		 * Set sdk options. We turn off image i/e since we will be loading our own images and
		 * attaching them to the created items; we do not want the SDK to provide any of those
		 * services. We must turn on repair trailers, since we want the accumulated totals to be
		 * automatically populated into the bundle, cash letter, and file trailer records.
		 */
		sdkBase.setImageFolder(null, X9Sdk.IMAGE_IE_DISABLED);
		sdkBase.setRepairTrailers(true);

		/*
		 * Allocate our writer and then write all items.
		 */
		boolean isFileHeaderWritten = false;
		boolean isCashLetterHeaderWritten = false;
		try (final X9Writer x9writer = new X9Writer(sdkBase, workUnit.isImageRepairEnabled(),
				workUnit.isImageResizeEnabled())) {
			/*
			 * Read the headerXml and load the csv into an internal map.
			 */
			final boolean isAbortIfEndMissing = !workUnit
					.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_END_NOT_PROVIDED);
			final Map<String, X9UtilWriterCsvLines> itemMap = X9UtilWriterList.loadCsv(sdkIO,
					x9writer, workUnit, x9headerXml937, isAbortIfEndMissing);

			/*
			 * Set options from headerXml and then also from the command line. This must be
			 * performed after the csv has been loaded (since headerXml is loaded there).
			 */
			setProcessingOptions(sdkIO);

			/*
			 * Get header attributes.
			 */
			final X9HeaderAttr937 headerAttr = x9headerXml937.getAttr();

			/*
			 * Open the output file.
			 */
			x9writer.bindAndOpenToFile(x9outputFile, x9headerXml937);

			/*
			 * Write items by batch profile.
			 */
			for (final Entry<String, X9UtilWriterCsvLines> entrySet : itemMap.entrySet()) {
				/*
				 * Get the next group of csv lines. Typically, everything is grouped under a single
				 * profile name of "". However, when creating multiple credits, items are grouped by
				 * profile and we accumulate the total amount for this group and insert the credit
				 * for these specific items (using that total amount). In that situation, any csv
				 * lines that are not associated with credit groups will be forced to the front
				 * since they have a mapKey of "". This would specifically apply to headerXml, but
				 * could also be used for other purposes such as an imageFolder reference. It is
				 * important to understand that the use of credit grouping puts some restrictions on
				 * how the csv is constructed. Specifically, items must be on a single line, and
				 * other functions like interspersed imageFolder commands cannot be used since they
				 * will be reordered to the front.
				 */
				final String mapKey = entrySet.getKey();
				final X9UtilWriterCsvLines csvLines = entrySet.getValue();
				LOGGER.info("csv lines for mapKey({}) lineCount({}) creditInserted({})", mapKey,
						csvLines.size(), headerAttr.creditInsertedAutomatically);

				/*
				 * Automatically insert a credit when directed by the headerXml definition.
				 */
				if (headerAttr.creditInsertedAutomatically) {
					/*
					 * Accumulate debit amounts which will be used to insert the offsetting credit.
					 */
					int itemCount = 0;
					String firstItemSequenceNumber = "";
					BigDecimal totalAmount = BigDecimal.ZERO;
					for (final X9CsvLine csvLine : csvLines) {
						/*
						 * Get the csv array and line number.
						 */
						final int lineNumber = csvLine.getLineNumber();
						final String[] record = csvLine.getCsvArray();

						/*
						 * Since we have explicit direction to automatically insert a credit, we
						 * should not see a credit on the csv file; abort if one is encountered.
						 */
						if (StringUtils.equalsAny(record[0], X9Writer.CSV_LINE_TYPE_CREDIT,
								X9Writer.CSV_LINE_TYPE_61, X9Writer.CSV_LINE_TYPE_62)) {
							throw X9Exception.abort(
									"user provided credit encountered when auto insert is "
											+ "enabled at lineNumber({}) content({})",
									lineNumber, X9String.joinWithLimits(record));
						}

						/*
						 * Get the index of various csv fields.
						 */
						final int csvAmountIndex;
						final int csvSequenceNumberIndex;
						if (StringUtils.equals(record[0], X9Writer.CSV_LINE_TYPE_T25)) {
							csvAmountIndex = X9Writer.ITEM_AMOUNT;
							csvSequenceNumberIndex = X9Writer.ITEM_SEQUENCE_NUMBER;
						} else if (StringUtils.equals(record[0], X9Writer.CSV_LINE_TYPE_25)) {
							csvAmountIndex = TYPE25_AMOUNT_INDEX;
							csvSequenceNumberIndex = TYPE25_SEQUENCE_NUMBER_INDEX;
						} else {
							csvAmountIndex = csvSequenceNumberIndex = 9999;
						}

						/*
						 * Accumulate debit amounts within this deposit which will be needed when we
						 * automatically insert an offsetting credit.
						 */
						final int csvLength = record.length;
						if (csvLength > csvAmountIndex) {
							itemCount++;
							totalAmount = totalAmount
									.add(X9Decimal.getAsAmount(record[csvAmountIndex]));
						}

						/*
						 * Save the first item sequence number (either from a 25 or a t25 record),
						 * which can be used as the serial number (AuxOnUs) on an inserted credit.
						 * If this isn is used for the credit serial number, then users should
						 * restrict their isn assignments to at most ten (10) digits. If the isn
						 * exceeds that, then we will use the rightmost 10 digits and ignore the
						 * high order digits. This facility works best when using single item
						 * deposits. If used with multi-item deposits, then the isn of the first
						 * debit in the deposit will be used as the credit item serial number (which
						 * still provides a good relational trace back facility for the credit).
						 */
						if (StringUtils.isBlank(firstItemSequenceNumber)
								&& csvLength > csvSequenceNumberIndex) {
							firstItemSequenceNumber = record[csvSequenceNumberIndex];
						}

						/*
						 * When a cash letter is active and then if credits are actually represented
						 * by the type 10 cash letter itself as an alternative to the more typical
						 * t25/t61/t62 record (which is used by Wells Fargo), we must close out the
						 * currently active cash letter. This will force a new cash letter header to
						 * be written with the information for the current deposit.
						 */
						if (isCashLetterHeaderWritten
								&& StringUtils.equals(headerAttr.creditFormat, X9Credit.T10)) {
							isCashLetterHeaderWritten = false;
							x9writer.writeBundleAndCashLetterTrailers();
						}
					}

					/*
					 * Build and attach the next credit which will be automatically inserted by
					 * x9writer at the designated insertion point per the headerXml definition.
					 */
					if (itemCount > 0) {
						final X9Credit x9credit = x9writer.createCredit();
						final String profileName = csvLines.getProfileName();
						setBatchProfileName(x9writer, headerAttr, profileName);
						x9credit.itemCount = itemCount;
						x9credit.amount = X9Decimal.getStringValue(totalAmount);
						x9credit.payorBankRouting = x9writer
								.getDirectedValue(headerAttr.creditPayorBankRouting);
						x9credit.micrOnUs = x9writer.getDirectedValue(headerAttr.creditMicrOnUs);
						final String creditAuxOnUs = x9writer
								.getDirectedValue(headerAttr.creditMicrAuxOnUs);
						x9credit.auxiliaryOnUs = StringUtils.equals(creditAuxOnUs,
								X9Writer.CREDIT_SERIAL_IS_DEBIT_ISN)
										? StringUtils.right(firstItemSequenceNumber, 10)
										: x9writer.assignCreditSerialNumber(creditAuxOnUs);
						x9credit.itemSequenceNumber = x9writer.assignCreditItemSequenceNumber(
								x9writer.getDirectedValue(headerAttr.creditItemSequenceNumber));
						LOGGER.info(
								"created batch profileName({}) amount({}) routing({}) OnUs({}) "
										+ "AuxOnUs({}) isn({}) creditFormat({}) creditLocation({})",
								profileName, x9credit.amount, x9credit.payorBankRouting,
								x9credit.micrOnUs, x9credit.auxiliaryOnUs,
								x9credit.itemSequenceNumber, headerAttr.creditFormat,
								headerAttr.creditRecordLocation);
					}
				}

				/*
				 * Write the file header when not yet written. A pending credit may be optionally
				 * attached here (but this would be unusual, since credits are typically inserted
				 * after the bundle header). We cannot write the cash letter header (yet), since it
				 * is dependent on deposit level information when creditFormat=t10. Because of that,
				 * writing the type 10 cash letter header is deferred into the upcoming loop, when
				 * we are writing items and have access to the batch profile.
				 */
				if (!isFileHeaderWritten) {
					isFileHeaderWritten = true;
					x9writer.writeFileHeaderAndOptionallyAttachCredit();
				}

				/*
				 * Most typically, items for each profile group begin in a new bundle. However, this
				 * is not mandatory, and is instead controlled by the credits-begin-in-new-bundle
				 * setting. When that has been selected, we now close an active bundle when we have
				 * created and attached a credit, to be inserted at the beginning of a new bundle.
				 */
				if (headerAttr.creditInsertedAutomatically && headerAttr.creditBeginsNewBundle) {
					x9writer.closeBundleWhenCurrentlyActive();
				}

				/*
				 * Bundle and write all items.
				 */
				for (final X9CsvLine csvLine : csvLines) {
					final int lineNumber = csvLine.getLineNumber();
					final String[] record = csvLine.getCsvArray();
					if (record != null && record.length > 0) {
						/*
						 * Log when command line enabled.
						 */
						if (isLoggingEnabled) {
							LOGGER.info("processing csv lineNumber({}) profileName({}) content({})",
									lineNumber, csvLines.getProfileName(),
									StringUtils.join(record, COMMA));
						}

						/*
						 * If this is an actual item, then set the batch profile and write the cash
						 * letter header (when needed). This skips over other leading csv line types
						 * such as "imageFolder, "batchProfile", etc, where the batch profile name
						 * has not yet been set. We need to delay writing the cash letter header
						 * until we get encounter the first actual item. This is important since
						 * items may be batches within the cash letter header, with deposit account
						 * proxy information taken from the batch profile.
						 */
						if (isCsvItemLineType(record[0])) {
							/*
							 * Set the profile name for this group of items (all items within this
							 * group have the same profile name, which may also be blanks).
							 */
							setBatchProfileName(x9writer, headerAttr, csvLines.getProfileName());

							/*
							 * Write the cash letter header when not yet written. A pending credit
							 * may be optionally attached here (but this would be unusual, since
							 * credits are typically inserted after the bundle header).
							 */
							if (!isCashLetterHeaderWritten) {
								isCashLetterHeaderWritten = true;
								x9writer.writeCashLetterHeaderAndOptionallyAttachCredit();
							}
						}

						/*
						 * Write the user provided csv record. This is most probably a type 25
						 * record, but can also be addenda, credits, and other record types.
						 */
						x9writer.writeX9FromCsvArray(lineNumber, record);
					}
				}
			}

			/*
			 * Get the processing errors, either by x9writer or x9field.
			 */
			processingErrorList = x9writer.getProcessingErrorList();

			/*
			 * Return our trailer manager instance.
			 */
			return x9writer.getTrailerManager();
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * Set the batch profile name for our profile facility, which allows items to be batched by
	 * depositor (including an automated calculation of the deposit amount). It also allows certain
	 * attributes to be redirected to an external properties file, making them independent of the
	 * headerXml file. Both of these are very powerful functions. Automated batching by customer
	 * allows a single x9 file to include deposits for a series of customers, where each credit is
	 * automatically constructed from the debits and attributes (routing, account number, etc) that
	 * is present in the properties file. Batch profiles can also be useful even when the x9 file
	 * contains items for a single customer. This is because the assigned profile name can be used
	 * to define certain attributes on an external basis from the headerXml file. For example,
	 * suppose these are corporate deposits and there is a need to use a specific credit ticket
	 * AuxOnUs where that serial number is predefined to be used for deposit reconciliation. This
	 * can be accomplished by pointing headerXml to a generic batch profile and then using that to
	 * dynamically assign credit AuxOnUs.
	 *
	 * @param x9writer
	 *            x9writer instance
	 * @param headerAttr
	 *            headerAttr options
	 * @param profileName
	 *            current profile name
	 */
	private void setBatchProfileName(final X9Writer x9writer, final X9HeaderAttr937 headerAttr,
			final String profileName) {
		/*
		 * Set the provided batch profile name when it is non-blank, and otherwise allow it to
		 * default to the batch profile name that is provided in headerXml attributes.
		 */
		final String batchProfile = StringUtils.isBlank(profileName) ? headerAttr.batchProfile
				: profileName;
		x9writer.setProfileName(batchProfile);

		/*
		 * Log when debugging.
		 */
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("setting batchProfile({})", batchProfile);
		}
	}

	/**
	 * Set processing options from the X9HeaderXml definition.
	 *
	 * @param sdkIO
	 *            current sdkIO
	 */
	private void setProcessingOptions(final X9SdkIO sdkIO) {
		/*
		 * Bind to the x9 file specification. We will take the configuration name first from our
		 * headers file, then from the command line, and finally will default to x9.37.
		 */
		final X9HeaderAttr937 headerAttr = x9headerXml937.getAttr();
		String configName = headerAttr.x9fileSpecification;
		if (StringUtils.isNotBlank(configName)) {
			LOGGER.info("configuration set from headers({})", configName);
		}

		if (StringUtils.isBlank(configName)
				&& workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_CONFIG)) {
			configName = workUnit.getCommandSwitchValue(X9UtilWorkUnit.SWITCH_CONFIG);
			LOGGER.info("configuration set from commandLine({})", configName);
		}

		if (StringUtils.isBlank(configName)) {
			configName = X9.X9_37_CONFIG;
		}

		if (!sdkBase.bindConfiguration(configName)) {
			throw X9Exception.abort("bind unsuccessful({})", configName);
		}

		/*
		 * Validate and set items per bundle.
		 */
		final String bundleItemCount = headerAttr.bundleItemCount;
		if (StringUtils.isNotBlank(bundleItemCount)) {
			final int itemsPerBundle = X9Numeric.toInt(bundleItemCount);
			if (itemsPerBundle < 0) {
				LOGGER.warn("bundleItemCount not numeric({}); assigning system default",
						bundleItemCount);
			} else {
				sdkIO.setItemsPerBundle(itemsPerBundle);
				LOGGER.info("setting itemsPerBundle({})", itemsPerBundle);
			}
		}
	}

	/**
	 * Determine if this is a csv item line type.
	 * 
	 * @param csvLineType
	 *            current csv line type
	 * @return true if this is an item line type otherwise false F
	 */
	public static boolean isCsvItemLineType(final String csvLineType) {
		return StringUtils.equalsAny(csvLineType, X9Writer.CSV_LINE_TYPE_T25,
				X9Writer.CSV_LINE_TYPE_CREDIT, X9Writer.CSV_LINE_TYPE_25, X9Writer.CSV_LINE_TYPE_31,
				X9Writer.CSV_LINE_TYPE_61, X9Writer.CSV_LINE_TYPE_62);
	}

}
