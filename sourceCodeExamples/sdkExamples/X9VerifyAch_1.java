package com.x9ware.examples;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.ach.X9Ach;
import com.x9ware.ach.X9AchType1;
import com.x9ware.ach.X9AchType5;
import com.x9ware.ach.X9AchType6;
import com.x9ware.ach.X9AchType8;
import com.x9ware.ach.X9AchType9;
import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9Dialect;
import com.x9ware.base.X9DialectFactory;
import com.x9ware.base.X9FileReader;
import com.x9ware.base.X9ItemAch;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9ObjectManager;
import com.x9ware.base.X9Sdk;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkFactory;
import com.x9ware.base.X9SdkIO;
import com.x9ware.base.X9SdkObject;
import com.x9ware.base.X9SdkObjectFactory;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.core.X9;
import com.x9ware.core.X9FileAttributes;
import com.x9ware.core.X9Reader;
import com.x9ware.elements.X9C;
import com.x9ware.error.X9Error;
import com.x9ware.error.X9ErrorManager;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.options.X9Options;
import com.x9ware.tools.X9D;
import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9FileIO;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManagerAch;
import com.x9ware.validate.X9ValidateAch;
import com.x9ware.validate.X9Validator;

/**
 * X9VerifyAch verifies an ach by applying the same rule based validations that are used by our
 * X9Assist desktop application. This example program demonstrates a variety of functions such as
 * loading an ach file, inspecting various record types, running validation, modifying records,
 * deleting records, recalculating trailer hash totals, adding pad records, and writing to both an
 * output stream or an output file. Errors identified by validation are written to the system log.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9VerifyAch {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase;
	private final X9ObjectManager x9objectManager;
	private X9Reader x9reader;
	private X9AchType1 achType1;
	private X9AchType9 achType9;

	/*
	 * Computed totals (these are based on the actual items and not the trailer totals).
	 */
	private int fileBatchCount = 0;
	private int fileItemCount = 0;
	private int fileDebitCount = 0;
	private int fileCreditCount = 0;
	private int fileDebitPrenoteCount = 0;
	private int fileCreditPrenoteCount = 0;
	private int fileDebitZeroDollarCount = 0;
	private int fileCreditZeroDollarCount = 0;
	private BigDecimal fileDebitAmount = BigDecimal.ZERO;
	private BigDecimal fileCreditAmount = BigDecimal.ZERO;

	/*
	 * Actual trailer totals.
	 */
	private int trailerBatchCount;
	private BigDecimal trailerDebitAmount;
	private BigDecimal trailerCreditAmount;

	/*
	 * Constants.
	 */
	private static final String X9VERIFYACH = "X9VerifyAch";
	private static final int BLOCKING_FACTOR = 10;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9VerifyAch.class);

	/**
	 * X9VerifyAch Constructor.
	 */
	public X9VerifyAch() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment and bind to an x9.37 configuration.
		 */
		X9SdkRoot.logStartupEnvironment(X9VERIFYACH);
		X9SdkRoot.loadXmlConfigurationFiles();
		sdkBase = new X9SdkBase();
		if (!sdkBase.bindConfiguration(X9.ACH_CORE_VALIDATIONS_CONFIG)) {
			throw X9Exception.abort("bind unsuccessful");
		}
		x9objectManager = sdkBase.getObjectManager();
	}

	/**
	 * Load the ach file, run the validator, and list all errors.
	 */
	private void process() {

		/*
		 * Define our test file and ensure it exists.
		 */
		final File folder = new File("c:/Users/X9Ware5/Documents/x9_assist/files");
		final File inputAchFile = new File(folder, "achTestFileCtx.ach");
		if (X9FileUtils.existsWithPathTracing(inputAchFile)) {
			LOGGER.info("processing achFile({})", inputAchFile);
		} else {
			throw X9Exception.abort("achFile not found({})", inputAchFile);
		}

		/*
		 * Process the input file using try-with-resources for automatic close.
		 */
		try (final InputStream inputStream = new BufferedInputStream(
				new FileInputStream(inputAchFile))) {
			/*
			 * Validate the input file using the provided data stream.
			 */
			final boolean hasRecords = validateInputStream(inputStream);

			/*
			 * Provide summary information when the file is not structurally flawed.
			 */
			if (hasRecords) {
				/*
				 * Gather file content.
				 */
				gatherFileContent();

				LOGGER.info(
						"file totals: batchCount({}) itemCount({}) debitCount({}) "
								+ "creditCount({}) debitAmount({}) creditAmount({}) "
								+ "dbitPrenoteCount({}) creditPrenoteCount({}) "
								+ "debitZeroDollarCount({}) creditZeroDollarCount({})",
						fileBatchCount, fileItemCount, fileDebitCount, fileCreditCount,
						fileDebitAmount, fileCreditAmount, fileDebitPrenoteCount,
						fileCreditPrenoteCount, fileDebitZeroDollarCount,
						fileCreditZeroDollarCount);
				LOGGER.info("trailerBatchCount({}) trailerDebitAmount({}) trailerCreditAmount({})",
						trailerBatchCount, trailerDebitAmount, trailerCreditAmount);

				/*
				 * List some records in their native 94 byte format.
				 */
				int loggingCount = 0;
				X9Object x9o = x9objectManager.getFirst();
				while (x9o != null && loggingCount < 100) {
					loggingCount++;
					LOGGER.info("recordType({}) recordNumber({}) content({})", x9o.x9ObjType,
							x9o.x9ObjIdx, new String(x9o.x9ObjData));
					x9o = x9o.getNext();
				}

				/*
				 * List validation errors.
				 */
				listErrorsToLog();
			} else {
				LOGGER.info("The uploaded file cannot be validated as it is not in the "
						+ "required format");
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * Modify one record and write to another output file.
		 */
		try {
			modifyRecordAndWrite(new File(folder, "test.ach"));
			LOGGER.info("finished");
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		} finally {
			x9objectManager.reset(); // release storage
		}
	}

	/*
	 * Validate an ach file contains within an input stream.
	 *
	 * @param input_Stream input stream to be validated
	 *
	 * @return true if the file contains records otherwise false
	 */
	private boolean validateInputStream(final InputStream input_Stream) {
		/*
		 * Allocate a file reader to process the input stream.
		 */
		try (final X9FileReader x9fileReader = X9FileReader.getNewStreamReader(input_Stream)) {
			/*
			 * Allocate an ach reader to read the incoming ach file and store for validation.
			 */
			try (final X9Reader allocated_Reader = X9DialectFactory.getNewReader(sdkBase,
					X9Dialect.ACH, x9fileReader, X9Reader.MAILBOX_NOT_ACCEPTED)) {
				/*
				 * Read and store x9objects until end of file. This will load all ach records to the
				 * heap using the object manager. The memory requirement is small, since ach files
				 * are data only (no images) with concise record types.
				 */
				x9reader = allocated_Reader;
				while (x9reader.getNext() != null) {
					x9objectManager.store(
							x9reader.createNewX9Object(x9objectManager.getNextRecordIndex()));
				}

				/*
				 * Validate the file when the reader was successful.
				 */
				if (x9objectManager.getNumberOfRecords() > 0) {
					/*
					 * Turn off ABA district validation.
					 */
					X9Options.applyAbaFrbDistrictValidation = false;

					/*
					 * Create a validator instance and verify the ach file from the x9objects.
					 */
					final X9Validator x9validator = new X9ValidateAch(sdkBase, x9reader);
					x9validator.verifyFile();

					/*
					 * Verify ach pad record count.
					 */
					final X9FileAttributes x9fileAttributes = x9reader.getFileAttributes();
					final int readerCount = x9fileAttributes.getRecordCount();
					if (readerCount > 0) {
						x9validator.verifyPadRecordCount(x9fileAttributes.getNinesRecordCount());
					}

					/*
					 * Log validation statistics.
					 */
					final X9ErrorManager x9errorManager = sdkBase.getErrorManager();
					LOGGER.info(
							"validation completed recordCount({}) errorCount({}) highestSeverity({})",
							x9validator.getX9RecordCount(), x9errorManager.getTotalRunErrors(),
							x9errorManager.getRunSeverity());
				}
			} catch (final Exception ex) {
				LOGGER.error("validation exception", ex);
			}
		} catch (final Exception ex) {
			LOGGER.error("file reader exception", ex);
		}

		/*
		 * Return true when the file has one or more records.
		 */
		return x9objectManager.getNumberOfRecords() > 0;
	}

	/**
	 * Gather file content.
	 */
	private void gatherFileContent() {
		/*
		 * Get the first and last records within the file, which obtains the first and last physical
		 * records regardless of actual record type. If there is only one record on the file, then
		 * test two references would actually point to the same record.
		 */
		final X9Object fileHeader = x9objectManager.getFirst(); // file header is expected
		final X9Object x9last = x9objectManager.getLast(); // file trailer is expected

		/*
		 * Abort if record instances were not found. These can only be null when the file did not
		 * contain at least one record. If the caller does not want this abort, then they should
		 * check the record count before they invoke us.
		 */
		if (fileHeader == null) {
			throw X9Exception.abort("first record not found");
		}

		if (x9last == null) {
			throw X9Exception.abort("last record not found");
		}

		/*
		 * The file header is absolutely needed; we cannot continue without it.
		 */
		if (fileHeader.x9ObjType != X9Ach.FILE_HEADER) {
			throw X9Exception.abort("first record not fileHeader({})", fileHeader.x9ObjType);
		}

		/*
		 * List file header.
		 */
		achType1 = new X9AchType1(fileHeader);
		LOGGER.info(
				"file header: priorityCode({}) immediateDestination({}) "
						+ "immediateDestinationName({}) immediateOrigin({}) immediateOriginName({})"
						+ "fileCreationDate({}) fileCreationTime({}) fileIdModifier({})",
				achType1.priorityCode, achType1.immediateDestination,
				achType1.immediateDestinationName, achType1.immediateOrigin,
				achType1.immediateOriginName, achType1.fileCreationDate, achType1.fileCreationTime,
				achType1.fileIdModifier);

		/*
		 * Create the file trailer type 9 instance when the last record is of proper type.
		 */
		final X9Object fileTrailer;
		if (x9last.x9ObjType == X9Ach.FILE_CONTROL) {
			fileTrailer = x9last;
			achType9 = new X9AchType9(fileTrailer);
		} else {
			fileTrailer = null;
			achType9 = null;
			LOGGER.warn("last record not fileControl({})", x9last.x9ObjType);
		}

		/*
		 * List file trailer.
		 */
		if (achType9 != null) {
			LOGGER.info(
					"file trailer: batchCount({}) entryAddendaCount({}) entryHash({}) "
							+ "debitAmount({}) creditAmount({})",
					achType9.batchCount, achType9.entryAddendaCount, achType9.entryHash,
					achType9.debitAmount, achType9.creditAmount);

		}

		/*
		 * List batches.
		 */
		X9Object x9o = x9objectManager.getFirst();
		while (x9o != null) {
			if (x9o.isBundleHeader()) {
				final X9AchType5 achType5 = new X9AchType5(x9o);
				LOGGER.info(
						"batch header: serviceClassCode({}) companyName({}) "
								+ "companyIdentification({}) standardEntryClassCode({}) "
								+ "companyEntryDescription({}) companyDescriptiveDate({}) "
								+ "effectiveEntryDate({}) settlementDateJulian({})",
						achType5.serviceClassCode, achType5.companyName,
						achType5.companyIdentification, achType5.standardEntryClassCode,
						achType5.companyEntryDescription, achType5.companyDescriptiveDate,
						achType5.effectiveEntryDate, achType5.settlementDateJulian);
				final List<X9ItemAch> itemList = listBatchContent(x9o);
				LOGGER.info("batch item Count({})", itemList.size());
			}
			x9o = x9o.getNext();
		}

		/*
		 * Set trailer record counts and amounts when present.
		 */
		if (achType9 == null) {
			trailerBatchCount = 0;
			trailerDebitAmount = BigDecimal.ZERO;
			trailerCreditAmount = BigDecimal.ZERO;
		} else {
			trailerBatchCount = X9Numeric.toInt(achType9.batchCount);
			trailerDebitAmount = X9Decimal.getAsAmount(achType9.debitAmount);
			trailerCreditAmount = X9Decimal.getAsAmount(achType9.creditAmount);
		}
	}

	/**
	 * List batch content.
	 * 
	 * @param batchHeader
	 *            current batch header
	 * @return item list for this batch
	 */
	private List<X9ItemAch> listBatchContent(final X9Object batchHeader) {
		/*
		 * Abort if the batch header is not as expected.
		 */
		if (batchHeader == null) {
			throw X9Exception.abort("currentBatch unexpectedly null");
		}

		if (!batchHeader.isBundleHeader()) {
			throw X9Exception.abort("currentBatch is incorrect recordType({})",
					batchHeader.x9ObjType);
		}

		/*
		 * Allocate and populate an ach type 5 for the batch header.
		 */
		final X9AchType5 achType5 = new X9AchType5(batchHeader);
		final String entryClass = achType5.standardEntryClassCode;
		final String companyName = achType5.companyName;

		/*
		 * Accumulate all items within the provided batch.
		 */
		final List<X9ItemAch> itemList = new ArrayList<>();
		int itemCount = 0;
		int debitCount = 0;
		int creditCount = 0;
		int debitPrenotes = 0;
		int creditPrenotes = 0;
		int debitZeroDollar = 0;
		int creditZeroDollar = 0;
		BigDecimal debitAmount = BigDecimal.ZERO;
		BigDecimal creditAmount = BigDecimal.ZERO;
		X9Object x9o = batchHeader.getNext();

		/*
		 * Count by transaction type.
		 */
		while (x9o != null && (x9o.isItem() || x9o.isAddendum())) {
			if (x9o.isItem()) {
				itemCount++;
				final X9AchType6 achType6 = new X9AchType6(entryClass, companyName, x9o);
				if (StringUtils.equalsAny(achType6.transactionCode, X9Ach.TC_CHECKING_DEBIT_PRENOTE,
						X9Ach.TC_SAVINGS_DEBIT_PRENOTE, X9Ach.TC_GL_DEBIT_PRENOTE)) {
					debitPrenotes++;
				} else if (StringUtils.equalsAny(achType6.transactionCode,
						X9Ach.TC_CHECKING_CREDIT_PRENOTE, X9Ach.TC_SAVINGS_CREDIT_PRENOTE,
						X9Ach.TC_GL_CREDIT_PRENOTE, X9Ach.TC_LOAN_CREDIT_PRENOTE)) {
					creditPrenotes++;
				} else if (StringUtils.equalsAny(achType6.transactionCode,
						X9Ach.TC_CHECKING_DEBIT_ZERO_DOLLAR, X9Ach.TC_SAVINGS_DEBIT_ZERO_DOLLAR,
						X9Ach.TC_GL_DEBIT_ZERO_DOLLAR)) {
					debitZeroDollar++;
				} else if (StringUtils.equalsAny(achType6.transactionCode,
						X9Ach.TC_CHECKING_CREDIT_ZERO_DOLLAR, X9Ach.TC_SAVINGS_CREDIT_ZERO_DOLLAR,
						X9Ach.TC_GL_CREDIT_ZERO_DOLLAR, X9Ach.TC_LOAN_CREDIT_ZERO_DOLLAR)) {
					creditZeroDollar++;
				} else if (x9o.isDebit()) {
					debitCount++;
					debitAmount = debitAmount.add(x9o.getItemAmount());
				} else if (x9o.isCredit()) {
					creditCount++;
					creditAmount = creditAmount.add(x9o.getItemAmount());
				}

				/*
				 * Add to item list for this batch.
				 */
				itemList.add(new X9ItemAch(achType6));
			}

			/*
			 * Get next record.
			 */
			x9o = x9o.getNext();
		}

		/*
		 * List actual totals for items within this batch.
		 */
		LOGGER.info("batch content: itemCount({}) debitCount({}) creditCount({}) debitAmount({}) "
				+ "creditAmount({}) debitPrenotes({}) creditPrenotes({}) debitZeroDollar({}) "
				+ "creditZeroDollar({})", itemCount, debitCount, creditCount, debitAmount,
				creditAmount, debitPrenotes, creditPrenotes, debitZeroDollar, creditZeroDollar);

		/*
		 * Accumulate actual data totals, which can be contrasted to the trailer records.
		 */
		fileBatchCount++;
		fileItemCount += itemCount;
		fileDebitCount += debitCount;
		fileCreditCount += creditCount;
		fileDebitPrenoteCount += debitPrenotes;
		fileCreditPrenoteCount += creditPrenotes;
		fileDebitZeroDollarCount += debitZeroDollar;
		fileCreditZeroDollarCount += creditZeroDollar;
		fileDebitAmount = fileDebitAmount.add(debitAmount);
		fileCreditAmount = fileCreditAmount.add(creditAmount);

		/*
		 * List the batch trailer when it exists.
		 */
		final X9Object batchTrailer = x9o != null && x9o.isBundleTrailer() ? x9o : null;
		final X9AchType8 achType8 = batchTrailer != null
				? new X9AchType8(entryClass, companyName, x9o)
				: null;
		if (achType8 != null) {
			LOGGER.info(
					"batch trailer: serviceClassCode({}) entryAddendaCount({}) entryHash({}) "
							+ "totalDebitDollarAmount({}) totalCreditDollarAmount({}) "
							+ "companyIdentification({}) achOperatorData({}) "
							+ "originatingDfiIdentification({}) batchNumber({})",
					achType8.serviceClassCode, achType8.entryAddendaCount, achType8.entryHash,
					achType8.totalDebitDollarAmount, achType8.totalCreditDollarAmount,
					achType8.companyIdentification, achType8.achOperatorData,
					achType8.originatingDfiIdentification, achType8.batchNumber);
		}

		/*
		 * Return the list of items for this batch.
		 */
		return itemList;
	}

	/**
	 * List all ach errors to the system log.
	 */
	private void listErrorsToLog() {
		final X9ErrorManager x9errorManager = sdkBase.getErrorManager();
		final Map<String, X9Error> errorMap = x9errorManager.getResequencedMap();
		if (errorMap.size() == 0) {
			LOGGER.info("no validation errors");
		} else {
			for (final X9Error x9error : errorMap.values()) {
				String batchNumber = "";
				String entryNumber = "";
				final X9Object x9o = x9error.getX9object();
				if (x9o != null) {
					entryNumber = x9o.getItemSequenceNumber();
					final X9Object x9oBatch = x9o.getBundleObject();
					if (x9oBatch != null) {
						final X9AchType5 achType5 = new X9AchType5(x9oBatch);
						batchNumber = achType5.batchNumber;
					}
				}
				LOGGER.info("error: batch serial number({}) entry number({}) error ===> {}",
						batchNumber, entryNumber, x9error.getFormulatedErrorString());
			}
		}
	}

	/*
	 * Modify a record and write to an output file. New hash totals will be computed automatically.
	 */
	private void modifyRecordAndWrite(final File outputFile) {
		/*
		 * Change the amount on the first item and completely remove the second item.
		 */
		int modificationCount = 0;
		String entryClass = "";
		String companyName = "";
		X9Object x9o = sdkBase.getFirstObject();
		while (x9o != null && modificationCount < 2) {
			if (x9o.isBundleHeader()) {
				/*
				 * Save entry class and company name from the batch header.
				 */
				final X9AchType5 achType5 = new X9AchType5(x9o);
				entryClass = achType5.standardEntryClassCode;
				companyName = achType5.companyName;
			} else if (x9o.isItem()) {
				/*
				 * Modify the amount on this item. If this file contains an offset, then that total
				 * will not be changed by this logic, and the file will now become out of balance.
				 */
				if (modificationCount == 0) {
					modificationCount++;
					LOGGER.info("modify recordNumber({}) recordType({}) dataBfore({})",
							x9o.x9ObjIdx, x9o.x9ObjType, new String(x9o.x9ObjData));
					final X9AchType6 achType6 = new X9AchType6(entryClass, companyName, x9o);
					achType6.amount = "8888";
					achType6.modify(x9o);
					LOGGER.info("modify recordNumber({}) recordType({}) dataAfter({})",
							x9o.x9ObjIdx, x9o.x9ObjType, new String(x9o.x9ObjData));
				} else if (modificationCount == 1) {
					/*
					 * Remove this type 6 record, which similarly puts the file out of balance when
					 * it has an offsetting debit or credit.
					 */
					modificationCount++;
					LOGGER.info("delete recordNumber({}) recordType({}) dataBfore({})",
							x9o.x9ObjIdx, x9o.x9ObjType, new String(x9o.x9ObjData));
					x9o.setDeleteStatus(true);

					/*
					 * Now remove the type 7 addenda records that are attached to this type 6.
					 */
					x9o = x9o.getNext();
					while (x9o != null && x9o.isAddendum()) {
						LOGGER.info("delete recordNumber({}) recordType({}) addenda({})",
								x9o.x9ObjIdx, x9o.x9ObjType, new String(x9o.x9ObjData));
						x9o.setDeleteStatus(true);
						x9o = x9o.getNext();
					}
				}
			}
			x9o = x9o.getNext();
		}

		/*
		 * Update hash counts in the batch trailer and file trailer records.
		 */
		final X9TrailerManagerAch achTrailerManager = new X9TrailerManagerAch(sdkBase);
		x9o = sdkBase.getFirstObject();
		while (x9o != null) {
			achTrailerManager.accumulateAndPopulate(x9o.x9ObjData);
			x9o = x9o.getNext();
		}

		/*
		 * Ensure we modified a record as expected.
		 */
		if (modificationCount == 2) {
			/*
			 * Create a temporary file which will be renamed on completion.
			 */
			final X9TempFile x9tempFile = new X9TempFile(outputFile);

			/*
			 * Write to the temporary output file.
			 */
			writeToFile(x9tempFile.getTemp());

			/*
			 * Rename the output file to final.
			 */
			x9tempFile.renameTemp();
		} else {
			/*
			 * Records not modified as expected.
			 */
			throw X9Exception.abort("unexpected modificationCount({})", modificationCount);
		}
	}

	/**
	 * Write records to an output file.
	 * 
	 * @param outputFile
	 *            output file to be written
	 */
	private void writeToFile(final File outputFile) {
		/*
		 * Initialize counters.
		 */
		int debitCount = 0;
		int creditCount = 0;
		int lineCount = 0;
		BigDecimal debitTotal = BigDecimal.ZERO;
		BigDecimal creditTotal = BigDecimal.ZERO;

		/*
		 * Set sdk writer options.
		 */
		final boolean isEbcdic = false;
		final X9SdkObjectFactory x9sdkObjectFactory = sdkBase.getSdkObjectFactory();
		x9sdkObjectFactory.setIsOutputEbcdic(isEbcdic);
		x9sdkObjectFactory.setFieldZeroInserted(false);

		/*
		 * Write to an output ach file; pad records will be added automatically as needed.
		 */
		final X9Sdk sdk = X9SdkFactory.getSdk(sdkBase, X9Dialect.ACH);
		try (final X9SdkIO sdkIO = sdk.getSdkIO()) {
			/*
			 * Open output as a file. Note that sdkIO also has method "openOutputStream" that can be
			 * used to write to an output stream instead of an output file. For example, you could
			 * write to a ByteArrayOutputStream.
			 */
			final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			sdkIO.openOutputStream(outputStream, "outputStream"); // open as an output stream
			// sdkIO.openOutputFile(outputFile); // open as an output file

			/*
			 * Walk through the list and write all x9objects.
			 */
			X9Object x9o = sdkBase.getFirstObject();
			while (x9o != null) {
				/*
				 * Create an sdkObject from this x9object.
				 */
				final X9SdkObject sdkObject = sdkIO.makeOutputRecord(x9o, false);

				/*
				 * Increment counts and amounts.
				 */
				if (x9o.isDebit()) {
					debitCount++;
					debitTotal = debitTotal.add(x9o.getRecordAmount());
				} else if (x9o.isCredit()) {
					creditCount++;
					creditTotal = creditTotal.add(x9o.getRecordAmount());
				}

				/*
				 * Write record from the sdkObject.
				 */
				lineCount++;
				sdkIO.writeOutputFile(sdkObject);

				/*
				 * Get the next record.
				 */
				x9o = x9o.getNext();
			}

			/*
			 * The output stream does not contain pad (nines) records, so we now add them and then
			 * write the stream to an output file. The pad records are optional because some
			 * applications may not want the pad records added. However, if you use X9SdkIO (above)
			 * to directly write to the output file, then the pad records are added as part of that
			 * write operation. Pad records must be added only when you first write to a stream and
			 * then copy that stream to an output file.
			 */
			final int recordsWritten = appendPadRecordsToStream(outputStream, lineCount);
			writeOutputStreamToFile(outputStream, outputFile);

			/*
			 * Write summary message to the log.
			 */
			final String characterSet = isEbcdic ? X9C.EBCDIC : X9C.ASCII;
			LOGGER.info(
					"outputFile({}) characterSet({}) debitCount({}) debitTotal({}) "
							+ "creditCount({}) creditTotal({}) recordsWritten({})",
					outputFile.toString(), characterSet, X9D.formatLong(debitCount),
					X9D.formatBigDecimal(debitTotal), X9D.formatLong(creditCount),
					X9D.formatBigDecimal(creditTotal), X9D.formatLong(recordsWritten));
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * Append pad records to the output stream
	 * 
	 * @param outputStream
	 *            constructed byte array output stream
	 * @param lineCount
	 *            current line count
	 * @return total number of records written
	 */
	private int appendPadRecordsToStream(final ByteArrayOutputStream outputStream,
			final int lineCount) {
		try {
			int recordsWritten = lineCount;
			final String padRecord = StringUtils.rightPad("", X9Ach.ACH_RECORD_LENGTH_94, '9');
			final byte[] padBytes = padRecord.getBytes();
			while (recordsWritten % BLOCKING_FACTOR != 0) {
				recordsWritten++;
				outputStream.write(padBytes);
			}
			return recordsWritten;
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}
	}

	/**
	 * Write the byte array output stream to an output file.
	 * 
	 * @param outputStream
	 *            constructed byte array output stream
	 * @param outputFile
	 *            output file to be written
	 * @param isAppendPadRecords
	 *            true if pad records are to be appended
	 */
	private void writeOutputStreamToFile(final ByteArrayOutputStream outputStream,
			final File outputFile) {
		try {
			outputStream.flush();
			outputStream.close();
			X9FileIO.writeFile(outputStream.toByteArray(), outputFile);
			LOGGER.info("output written({})", outputFile);
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
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
		LOGGER.info(X9VERIFYACH + " started");
		try {
			final X9VerifyAch x9verifyAch = new X9VerifyAch();
			x9verifyAch.process();
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
