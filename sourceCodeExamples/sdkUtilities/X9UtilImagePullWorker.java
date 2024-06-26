package com.x9ware.utilities;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9FileReader;
import com.x9ware.base.X9Item937;
import com.x9ware.base.X9Object;
import com.x9ware.base.X9SdkBase;
import com.x9ware.core.X9;
import com.x9ware.core.X9Reader937;
import com.x9ware.elements.X9C;
import com.x9ware.micr.X9MicrOnUs;
import com.x9ware.tools.X9FileIO;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9Folder;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.tools.X9TaskMonitor;
import com.x9ware.tools.X9TaskWorker;
import com.x9ware.types.X9Type20;

/**
 * X9UtilImagePullWorker performs all image pull work for a single x9.37 file, where we are provided
 * a map of all items that are to be pulled within this single file. Our task is to populate the csv
 * request fields and write the associated front and back images.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilImagePullWorker extends X9TaskWorker<X9UtilImagePullEntry> {

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/*
	 * Private.
	 */
	private final X9UtilWorkUnit workUnit;
	private final boolean isLoggingEnabled;
	private final boolean isPullCredits;
	private final boolean isPullBackSideImages;
	private final File imageFolder;
	private int recordNumber;
	private String returnLocationRouting = "";

	/*
	 * Public.
	 */
	public int filesCompleted;

	/*
	 * Constants.
	 */
	private static final String IMAGE_FOLDER_NAME = "folder";
	private static final String NOT_PULLED = "notPulled";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilImagePullWorker.class);

	/**
	 * X9UtilImagePullWorker Constructor.
	 *
	 * @param task_Monitor
	 *            associated task monitor for call backs
	 * @param work_Unit
	 *            current work unit
	 * @param pullList
	 *            list of entries to be pulled (each of these represents a single file)
	 * @param baseImageFolder
	 *            base image folder
	 */
	public X9UtilImagePullWorker(final X9TaskMonitor<X9UtilImagePullEntry> task_Monitor,
			final X9UtilWorkUnit work_Unit, final List<X9UtilImagePullEntry> pullList,
			final File baseImageFolder) {
		/*
		 * Initialization.
		 */
		super(task_Monitor, pullList);
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		isLoggingEnabled = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_LOGGING);
		isPullCredits = workUnit.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_INCLUDE_61_62_CREDITS);
		isPullBackSideImages = workUnit
				.isCommandSwitchSet(X9UtilWorkUnit.SWITCH_PULL_BACK_SIDE_IMAGES);

		/*
		 * Assign our image folder name sequentially using the worker thread number.
		 */
		imageFolder = new File(baseImageFolder, IMAGE_FOLDER_NAME + getWorkerThreadNumber());

		/*
		 * Set the configuration name when provided; we otherwise default to file header.
		 */
		workUnit.autoBindToCommandLineConfiguration(sdkBase);

		/*
		 * Create the image folder when needed.
		 */
		X9Folder.createFolderWhenNeeded(imageFolder);

		/*
		 * Set the image folder in sdkBase for this environment.
		 */
		sdkBase.setImageFolder(imageFolder, true);
	}

	@Override
	public boolean processOneEntry(final X9UtilImagePullEntry pullEntry) {
		/*
		 * Process one image pull worker map entry, which is a list of items for a single file.
		 * Files are read only once, looking for those specific items to be pulled. Each file is
		 * processed within a try so we can mark unprocessed entries as aborted.
		 */
		final File x9file = new File(pullEntry.getX9fileName());
		final int requestCount = pullEntry.getMapSize();
		final boolean isFileFound = X9FileUtils.existsWithPathTracing(x9file);
		LOGGER.info("file({}) isFileFound({}) requestCount({}) threadName({})", x9file, isFileFound,
				requestCount, getWorkerThreadName());
		try {
			if (isFileFound) {
				/*
				 * Pull all requested images for the current x9 file.
				 */
				pullImagesForX9File(pullEntry, x9file);
			} else {
				/*
				 * Mark all pull requests for this file as not found.
				 */
				pullEntry.incrementFilesNotFound();
				markAllUnmarkedEntries(pullEntry, X9UtilImagePullRequest.ERROR_FILE_NOT_FOUND);
			}
		} catch (final Exception ex) {
			/*
			 * Increment files aborted and then consume the error so the thread continues.
			 */
			pullEntry.incrementFilesAborted();
			LOGGER.error("file exception", ex);
		} finally {
			/*
			 * At this point all entries should have an image file name assigned as either pull,
			 * item not found, or file not found. Obviously they are not marked if we have taken the
			 * above exception which will be included in the log. We now forcibly mark anything that
			 * is yet not marked as aborted which will guarantee that all items are marked.
			 */
			markAllUnmarkedEntries(pullEntry, X9UtilImagePullRequest.ERROR_FILE_ABORTED);
		}

		/*
		 * Return true if work was performed.
		 */
		return isFileFound && requestCount > 0;
	}

	/**
	 * Pull items and images for a single x9 file using the supplied entry map.
	 *
	 * @param pullEntry
	 *            map of items to be pulled
	 * @param x9file
	 *            current x9 file
	 */
	private void pullImagesForX9File(final X9UtilImagePullEntry pullEntry, final File x9file) {
		/*
		 * Get the base name that will be prefixed to the front of each exported image.
		 */
		final String imageNamePrefix = FilenameUtils.getBaseName(x9file.toString());

		/*
		 * Process all requests for the current file.
		 */
		final int numberOfItemsToBePulled = pullEntry.getMapSize();
		try (final X9FileReader x9fileReader = X9FileReader.getNewChannelReader(x9file);
				final X9Reader937 x9reader937 = new X9Reader937(sdkBase, x9fileReader)) {
			/*
			 * Get the next item group. As part of optimization, our processing will be exited as
			 * soon as all pull requests for the current file have been found.
			 */
			X9Object x9o = getNextIncomingRecord(x9reader937);
			pullAllItems: while (x9o != null) {
				/*
				 * Advance to the next item group when not currently positioned on an item.
				 */
				while (x9o != null && !x9o.isItem()) {
					x9o = getNextIncomingRecord(x9reader937);
				}

				/*
				 * Exit when we reach end of file.
				 */
				if (x9o == null) {
					break pullAllItems;
				}

				/*
				 * Allocate an x9item and populate fields from the item record (25/31/61/62).
				 */
				final X9Object currentItem = x9o;
				final X9Item937 x9item937 = new X9Item937(x9o, X9Item937.DO_NOT_ATTACH_FROM_HEAP);

				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("item recordNumber({}) data({})", x9o.x9ObjIdx,
							new String(x9o.x9ObjData));
				}

				/*
				 * Read through the addenda records and attach them to this item record. This
				 * populate process will normally read and then stop on the second image record. In
				 * that situation, the logic at the top of this loop will then continue and will
				 * read to the next item, which would typically be the very next record on the file.
				 * However, other situations will happen as well. If the item does not have two
				 * images, then we will unexpectedly hit the next item here while reading for the
				 * attached images. In that situation, the logic at the top of this loop will
				 * recognize and accept this item positioning. The item may also have more than two
				 * attached images. In that case, the logic at the top of this loop similarly
				 * continues to read through the secondary images as needed to get to the next item.
				 */
				int imageCount = 0;
				byte[] frontImage = null;
				byte[] backImage = null;
				populateItemAddenda: while ((x9o = getNextIncomingRecord(x9reader937)) != null
						&& !x9o.isItem()) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("addenda recordNumber({}) data({})", x9o.x9ObjIdx,
								new String(x9o.x9ObjData));
					}

					/*
					 * Populate fields for all record types within the current item group.
					 */
					x9item937.populateFieldsByRecordType(x9o);

					/*
					 * Save the images as they are encountered.
					 */
					if (x9o.isRecordType(X9.IMAGE_VIEW_DATA)) {
						imageCount++;
						if (imageCount == 1) {
							frontImage = x9reader937.getImageBuffer();
						} else if (imageCount == 2) {
							backImage = x9reader937.getImageBuffer();
							break populateItemAddenda;
						}
					}
				}

				/*
				 * Determine if this item has been selected.
				 */
				if (currentItem.isDebit() || (isPullCredits && currentItem.isCredit())) {
					/*
					 * Get item related fields.
					 */
					final String itemSequenceNumber = x9item937.getItemSequenceNumber();
					final BigDecimal itemAmount = x9item937.getAmount();
					final LocalDate itemDate = X9LocalDate
							.getDateFromString(x9item937.getImageCreatorDate());
					final String itemRouting = x9item937.getRouting();
					final X9MicrOnUs x9micrOnUs = new X9MicrOnUs(x9item937.getOnus());
					final String itemAccount = getMicrLineField(x9micrOnUs.getAccount());
					final String itemSerial = getMicrLineField(
							x9micrOnUs.getSerialNumberAsString(x9item937.getAuxOnus()));

					/*
					 * Use the current item details as a lookup against the request map.
					 */
					final X9UtilImagePullRequest pullRequest = pullEntry.getEntryMap(
							itemSequenceNumber, itemAmount, itemDate, itemRouting, itemAccount,
							itemSerial);
					if (pullRequest != null) {
						/*
						 * The item has been selected based on pull criteria.
						 */
						pullEntry.incrementItemsPulled();

						/*
						 * Log records when enabled via a command line switch. Note we only log
						 * items due to the overwhelming volume that is most probably involved. This
						 * logging is a support aid to allow us to identify the last record
						 * processed within the current file.
						 */
						if (isLoggingEnabled) {
							LOGGER.info("pulling recordNumber({}) data({})", currentItem.x9ObjIdx,
									new String(currentItem.x9ObjData));
						}

						/*
						 * Set primary fields.
						 */
						pullRequest.setOutputEntry(X9UtilImagePullRequest.RECORD_TYPE,
								Integer.toString(x9item937.getRecordType()));
						pullRequest.setOutputEntry(X9UtilImagePullRequest.RECORD_NUMBER,
								Integer.toString(x9item937.getItemNumber()));
						pullRequest.setOutputEntry(X9UtilImagePullRequest.RETURN_LOCATION_ROUTING,
								returnLocationRouting);

						/*
						 * Always write the front image.
						 */
						if (frontImage != null) {
							final String imageFileName = exportImage(imageNamePrefix, "front",
									x9item937, frontImage);
							pullRequest.setOutputEntry(X9UtilImagePullRequest.FRONT_IMAGE,
									imageFileName);
						} else {
							pullRequest
									.setErrorCondition(X9UtilImagePullRequest.ERROR_ITEM_NO_IMAGE);
						}

						/*
						 * Export the back image when directed.
						 */
						if (isPullBackSideImages) {
							if (backImage != null) {
								final String imageFileName = exportImage(imageNamePrefix, "back",
										x9item937, backImage);
								pullRequest.setOutputEntry(X9UtilImagePullRequest.BACK_IMAGE,
										imageFileName);
							} else {
								pullRequest.setErrorCondition(
										X9UtilImagePullRequest.ERROR_ITEM_NO_IMAGE);
							}
						} else {
							pullRequest.setOutputEntry(X9UtilImagePullRequest.BACK_IMAGE,
									NOT_PULLED);
						}

						/*
						 * Populate remaining fields for this pull request.
						 */
						final String routing = x9item937.getRouting();
						pullRequest.setOutputEntry(X9UtilImagePullRequest.AUX_ONUS,
								x9item937.getAuxOnus());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.EPC, x9item937.getEpc());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.PAYOR_ROUTING,
								StringUtils.substring(routing, 0, 8));
						pullRequest.setOutputEntry(X9UtilImagePullRequest.PAYOR_ROUTING_CHECK_DIGIT,
								StringUtils.substring(routing, 8));
						pullRequest.setOutputEntry(X9UtilImagePullRequest.ONUS,
								x9item937.getOnus());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.AMOUNT,
								x9item937.getAmountAsString());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.BOFD_INDICATOR,
								x9item937.getBofdIndicator());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.BOFD_DATE,
								x9item937.getBofdEndorsementDate());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.BOFD_ROUTING,
								x9item937.getBofdRouting());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.IMAGE_CREATOR_ROUTING,
								x9item937.getImageCreatorRouting());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.IMAGE_CREATOR_DATE,
								x9item937.getImageCreatorDate());
						pullRequest.setOutputEntry(X9UtilImagePullRequest.RETURN_REASON,
								x9item937.getReturnReason());

						/*
						 * Exit when all items have been pulled for this file.
						 */
						if (pullEntry.isMapSpecific()
								&& pullEntry.getItemsPulled() >= numberOfItemsToBePulled) {
							break pullAllItems;
						}
					}
				}
			}

			/*
			 * Mark all unmarked pull requests for this file as item not found.
			 */
			markAllUnmarkedEntries(pullEntry, X9UtilImagePullRequest.ERROR_ITEM_NOT_FOUND);
		} catch (final Exception ex) {
			/*
			 * Catch and consume the error so the thread continues.
			 */
			LOGGER.error("pull exception", ex);
		} finally {
			filesCompleted++;
		}
	}

	/**
	 * Get a MICR line value as a string with any embedded special characters removed.
	 *
	 * @param micrValue
	 *            micr line value with possible special characters ("-", " ", etc)
	 * @return micr line value as a string
	 */
	private String getMicrLineField(final String micrValue) {
		final long value = X9MicrOnUs.getMicrFieldAsLong(micrValue);
		return value > 0 ? Long.toString(value) : "";
	}

	/**
	 * Export the current image and write an empty file when the tiff array is null.
	 *
	 * @param imageNamePrefix
	 *            string that is prefixed to the front of each image name
	 * @param frontOrBack
	 *            front or back
	 * @param x9item937
	 *            current item
	 * @param tiffArray
	 *            current image byte array
	 * @return created image file name
	 */
	private String exportImage(final String imageNamePrefix, final String frontOrBack,
			final X9Item937 x9item937, final byte[] tiffArray) {
		try {
			final String amount = x9item937.getAmountAsString();
			final String imageFileName = imageNamePrefix + "_isn_"
					+ x9item937.getItemSequenceNumber() + "_amount_" + amount + "_" + frontOrBack
					+ "." + X9C.TIF;
			final File imageFile = new File(imageFolder, imageFileName);
			X9FileIO.writeFile(tiffArray, imageFile);
			return imageFileName;
		} catch (final Exception ex) {
			/*
			 * Catch and consume the error so the thread continues.
			 */
			LOGGER.error("image export exception", ex);
			return "";
		}
	}

	/**
	 * Mark all pull request entries for the current x9 file with a specifically provided error
	 * condition when images have not been previously set for this pull request.
	 *
	 * @param pullEntry
	 *            list of items be pulled
	 * @param errorCondition
	 *            error condition value
	 */
	private void markAllUnmarkedEntries(final X9UtilImagePullEntry pullEntry,
			final int errorCondition) {
		final Map<String, X9UtilImagePullRequest> pullMap = pullEntry.getPullMap();
		for (final X9UtilImagePullRequest pullRequest : pullMap.values()) {
			if (pullRequest.isOutputEmpty(X9UtilImagePullRequest.FRONT_IMAGE)
					|| pullRequest.isOutputEmpty(X9UtilImagePullRequest.BACK_IMAGE)) {
				pullEntry.incrementItemsNotFound();
				pullRequest.setErrorCondition(errorCondition);
			}
		}
	}

	/**
	 * Get an x9object for the next incoming x9 record. This method will extract any fields that are
	 * needed from x9 record records.
	 *
	 * @param x9reader937
	 *            x9reader937 instance
	 * @return next incoming x9object
	 */
	private X9Object getNextIncomingRecord(final X9Reader937 x9reader937) {
		final X9Object x9o;
		if (x9reader937.getNext() == null) {
			x9o = null;
		} else {
			recordNumber++;
			x9o = x9reader937.createNewX9Object(recordNumber);
			if (x9o.isBundleHeader()) {
				final X9Type20 t20 = new X9Type20(x9o);
				returnLocationRouting = t20.returnLocationRoutingNumber;
			}
		}
		return x9o;
	}

}
