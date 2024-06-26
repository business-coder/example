
package com.x9ware.utilities;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9SdkBase;
import com.x9ware.core.X9Writer;
import com.x9ware.core.X9WriterImages;
import com.x9ware.imageio.X9BitonalImage;
import com.x9ware.imaging.X9ImageMerge;
import com.x9ware.sunTiff.TIFFEncodeParam;
import com.x9ware.tiffTools.X9TiffWriter;
import com.x9ware.tools.X9CsvLine;
import com.x9ware.tools.X9CsvReader;
import com.x9ware.tools.X9CsvWriter;
import com.x9ware.tools.X9FileIO;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9String;

/**
 * X9UtilDraw is part of our utilities package which uses an input csv file to draw images, in the
 * same manner as provided by the writer. This includes the ability to draw front images, back
 * images, and the associated back-side paid stamps. Output can be single or multi-page tiff images.
 * This process is helpful for applications that need to create images as an independent activity,
 * perhaps to be loaded to their internal image archive. In those situations, we can be invoked
 * first to draw the physical images to external files, which are then provided to the writer.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilDraw {

	/**
	 * X9UtilWorkUnit instance which describes the unit of work assigned to us.
	 */
	private final X9UtilWorkUnit workUnit;

	/**
	 * X9SdkBase instance for this environment as assigned by our constructor.
	 */
	private final X9SdkBase sdkBase;

	/*
	 * Private.
	 */
	private final X9WriterImages x9writerImages;
	private File csvInputFile;
	private File csvOutputFile;
	private String imageFolder;
	private String outputFolder;
	private int drawCount;
	private int singleTiffCount;
	private int multiTiffCount;

	/*
	 * Constants.
	 */
	private static final int IMAGE_FOLDER_ID_LENGTH = 2; // base folder for image templates
	private static final int OUTPUT_FOLDER_ID_LENGTH = 2; // base folder for output tiff images
	private static final char PERIOD = '.';
	private static final char COMMA = ',';
	private static final String OMIT = "omit";
	private static final String MULTI = "multi";
	private static final String BLANK = "blank";
	private static final String CSV_LINE_TYPE_OUTPUT_FOLDER = "outputFolder";
	private static final String CSV_LINE_TYPE_ITEM = "item";

	private static final int COL_LINE_TYPE = 0;
	private static final int COL_AUX_ONUS = 1;
	private static final int COL_EPC = 2;
	private static final int COL_ROUTING = 3;
	private static final int COL_ONUS = 4;
	private static final int COL_AMOUNT = 5;
	private static final int COL_IDENTIFIER = 6;
	private static final int COL_FRONT_IMAGE = 7;
	private static final int COL_BACK_IMAGE = 8;
	private static final int COL_FRONT_IMAGE_LENGTH = 9;
	private static final int COL_BACK_IMAGE_LENGTH = 10;
	private static final int COL_COUNT = 11;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilDraw.class);

	/**
	 * X9UtilDraw Constructor.
	 *
	 * @param work_Unit
	 *            current work unit
	 */
	public X9UtilDraw(final X9UtilWorkUnit work_Unit) {
		workUnit = work_Unit;
		sdkBase = workUnit.getNewSdkBase();
		x9writerImages = new X9WriterImages(sdkBase);
	}

	/**
	 * Read a image draw csv file and write an output csv which details the images created. Note
	 * that creation of the output csv file is optional, and is essentially an echo of the input csv
	 * with the finalized output image file names, and their associated byte length.
	 *
	 * @return exit status
	 */
	public int process() {
		/*
		 * Define input and output files.
		 */
		csvInputFile = workUnit.inputFile;
		csvOutputFile = workUnit.resultsFile; // null if not being created

		/*
		 * Read the csv file and draw the requested images.
		 */
		drawImages();

		/*
		 * Return exit status zero.
		 */
		return X9UtilBatch.EXIT_STATUS_ZERO;
	}

	/**
	 * Draw images, where out input csv has two lines for each logical item. The first is an "item"
	 * line, which defines the the micr line for the item to be drawn. The second is the
	 * "drawImages" line, which contains parameters as to how the front and back images are drawn.
	 */
	private void drawImages() {
		/*
		 * Examine the csv import file to ensure that it can be processed successfully.
		 */
		drawCount = 0;
		X9CsvLine csvLine = null;
		try (final X9CsvReader csvReader = new X9CsvReader(csvInputFile);
				final X9CsvWriter csvWriter = new X9CsvWriter(csvOutputFile)) {
			/*
			 * Continue until end of csv file.
			 */
			while ((csvLine = csvReader.getNextCsvLine()) != null) {
				/*
				 * Invoke appropriate function based on csv line type.
				 */
				csvLine.logWhenDebugging();
				final String[] csvArray = csvLine.getCsvArray();
				final int recordNumber = csvLine.getLineNumber();
				final String csvLineType = csvArray[0];
				if (StringUtils.equals(csvLineType, X9Writer.CSV_LINE_TYPE_IMAGE_FOLDER)) {
					setImageFolder(recordNumber, csvArray);
				} else if (StringUtils.equals(csvLineType, CSV_LINE_TYPE_OUTPUT_FOLDER)) {
					setOutputFolder(recordNumber, csvArray);
				} else if (StringUtils.equals(csvLineType, X9Writer.CSV_LINE_TYPE_PAID_STAMP)) {
					x9writerImages.storePaidStampLines(csvArray);
				} else if (StringUtils.equalsAny(csvLineType, CSV_LINE_TYPE_ITEM, "25", "31", "61",
						"62")) {
					drawCount++;
					drawAndWriteItemImages(csvReader, csvWriter, recordNumber, csvArray);
				} else {
					throw X9Exception.abort("invalid csvLineType({}) recordNumber({}) content({})",
							csvLineType, recordNumber, StringUtils.join(csvArray, COMMA));
				}
			}
		} catch (final Exception ex) {
			throw X9Exception.abort(ex);
		}

		/*
		 * List and abort when there were missing template images.
		 */
		x9writerImages.abortWhenMissingTemplates();

		/*
		 * Log our statistics.
		 */
		LOGGER.info("utilDraw completed({}) drawCount({}) singleTiffCount({}) multiTiffCount({})",
				drawCount, singleTiffCount, multiTiffCount);
	}

	/**
	 * Draw and write the images for a single item.
	 * 
	 * @param csvReader
	 *            csv reader since we will need to read next
	 * @param csvWriter
	 *            csv writer which is used to write the action line for this item
	 * @param recordNumber
	 *            current record number
	 * @param csvItemArray
	 *            item csv array
	 * @throws IOException
	 */
	private void drawAndWriteItemImages(final X9CsvReader csvReader, final X9CsvWriter csvWriter,
			final int recordNumber, final String[] csvItemArray) throws IOException {
		/*
		 * Assign the item fields from the csv array.
		 */
		final String lineType = getFieldValue(csvItemArray, COL_LINE_TYPE);
		final String auxOnus = getFieldValue(csvItemArray, COL_AUX_ONUS);
		final String epc = getFieldValue(csvItemArray, COL_EPC);
		final String routing = getFieldValue(csvItemArray, COL_ROUTING);
		final String onus = getFieldValue(csvItemArray, COL_ONUS);
		final String amountString = getFieldValue(csvItemArray, COL_AMOUNT);
		final String identifier = getFieldValue(csvItemArray, COL_IDENTIFIER);
		final String frontImageName = getFieldValue(csvItemArray, COL_FRONT_IMAGE);
		final String backImageName = getFieldValue(csvItemArray, COL_BACK_IMAGE);

		final long amount = X9Numeric.toLong(StringUtils.remove(amountString, PERIOD));
		if (amount < 0) {
			throw X9Exception.abort("non-numeric amount({}) recordNumber({}) content({})",
					amountString, recordNumber, StringUtils.join(csvItemArray, COMMA));
		}

		if (StringUtils.isBlank(frontImageName)) {
			throw X9Exception.abort("frontImage name missing recordNumber({}) content({})",
					recordNumber, X9String.joinWithLimits(csvItemArray));
		}

		/*
		 * Assign front and back image file names. Absence of the back image file name implies that
		 * we should create a multi-page tiff that contains the front-back images. The image file
		 * names can be absolute (fully qualified) or they can be stored into a user supplied
		 * output-folder named, which can be changed as often as needed. The image file names can be
		 * just the base name or can also be a sub-folder plus the base name.
		 */
		final boolean isMultiPageTiff = StringUtils.equalsIgnoreCase(backImageName, MULTI);
		final File frontImageFile = X9FileUtils.isFileNameAbsolute(frontImageName)
				? new File(frontImageName)
				: new File(outputFolder, frontImageName);
		final File backImageFile = isMultiPageTiff ? null
				: (X9FileUtils.isFileNameAbsolute(backImageName) ? new File(backImageName)
						: new File(outputFolder, backImageName));

		/*
		 * Allocate the output csv line for this item activity. The item identifier could be
		 * provided as the item sequence number, but could also be any other value that would assist
		 * the application in matching/processing this csv line.
		 */
		final String[] csvOutputArray = new String[COL_COUNT];
		csvOutputArray[COL_LINE_TYPE] = lineType;
		csvOutputArray[COL_AUX_ONUS] = auxOnus;
		csvOutputArray[COL_EPC] = epc;
		csvOutputArray[COL_ROUTING] = routing;
		csvOutputArray[COL_ONUS] = onus;
		csvOutputArray[COL_AMOUNT] = amountString;
		csvOutputArray[COL_IDENTIFIER] = identifier;
		csvOutputArray[COL_FRONT_IMAGE] = frontImageFile.toString();
		csvOutputArray[COL_BACK_IMAGE] = backImageFile == null ? "" : backImageFile.toString();

		/*
		 * Now get the drawImages line, since it is mandatory and must follow the item line.
		 */
		final X9CsvLine csvLine = csvReader.getNextCsvLine();
		if (csvLine == null) {
			throw X9Exception.abort("unexpected end of file at recordNumber({})", recordNumber);
		}

		/*
		 * Draw the front and back images using the provided "drawImages" csvArray.
		 */
		final String[] csvDrawArray = csvLine.getCsvArray();
		final X9BitonalImage[] images = x9writerImages.drawBitonalImages(imageFolder, amount,
				routing, onus, auxOnus, epc, recordNumber, csvDrawArray);
		if (images == null || images.length != 2) {
			throw X9Exception.abort("invalid x9writerImages response");
		}

		/*
		 * Get the image byte arrays and write to external tiff image files.
		 */
		final X9BitonalImage frontImage = images[X9WriterImages.FRONT_INDEX];
		final X9BitonalImage backImage = images[X9WriterImages.BACK_INDEX];

		if (frontImage == null) {
			throw X9Exception.abort("frontImage null");
		}
		if (backImage == null) {
			throw X9Exception.abort("backImage null");
		}

		/*
		 * Write the bitonal buffered images either as single tiff or multi-page tiff.
		 */
		final int drawDpi = sdkBase.getDrawOptions().getDrawDpi();
		if (isMultiPageTiff) {
			/*
			 * Encode and write the multi-page tiff file.
			 */
			multiTiffCount++;
			final X9ImageMerge x9imageMerge = new X9ImageMerge(BufferedImage.TYPE_BYTE_BINARY,
					TIFFEncodeParam.COMPRESSION_GROUP4);
			x9imageMerge.addAnotherImage(frontImage, drawDpi);
			x9imageMerge.addAnotherImage(backImage, drawDpi);
			final byte[] multiTiffArray = x9imageMerge.encodeMultiPageImage();
			X9FileIO.writeFile(multiTiffArray, frontImageFile);
			csvOutputArray[COL_FRONT_IMAGE_LENGTH] = Integer.toString(multiTiffArray.length);
			csvOutputArray[COL_BACK_IMAGE_LENGTH] = "0";
		} else {
			/*
			 * Create and write the front-side image.
			 */
			singleTiffCount++;
			final byte[] frontTiffImage = X9TiffWriter.createTiff(frontImage, drawDpi);
			X9FileIO.writeFile(frontTiffImage, frontImageFile);
			csvOutputArray[COL_FRONT_IMAGE_LENGTH] = Integer.toString(frontTiffImage.length);

			/*
			 * Create and write the back-side image when not omitted.
			 */
			if (StringUtils.equalsIgnoreCase(backImageName, OMIT)) {
				csvOutputArray[COL_BACK_IMAGE_LENGTH] = "0";
			} else {
				singleTiffCount++;
				final byte[] backTiffImage = X9TiffWriter.createTiff(backImage, drawDpi);
				X9FileIO.writeFile(backTiffImage, backImageFile);
				csvOutputArray[COL_BACK_IMAGE_LENGTH] = Integer.toString(backTiffImage.length);
			}
		}

		/*
		 * Write the action line for this item when it is being created.
		 */
		if (csvOutputFile != null) {
			csvWriter.putFromArray(csvOutputArray);
		}
	}

	/**
	 * Set the image template folder; this is typically only done once in a given run.
	 *
	 * @param recordNumber
	 *            current csv record number
	 * @param csvArray
	 *            csvArray which represents the image to be written
	 */
	private void setImageFolder(final int recordNumber, final String[] csvArray) {
		/*
		 * Ensure we have been provided a folder name.
		 */
		if (csvArray.length != IMAGE_FOLDER_ID_LENGTH) {
			throw X9Exception.abort(
					"image folder csvLength({}) must be({}) recordNumber({}) content({})",
					csvArray.length, IMAGE_FOLDER_ID_LENGTH, recordNumber,
					X9String.joinWithLimits(csvArray));
		}

		/*
		 * Assign the image folder and ensure that it exists.
		 */
		imageFolder = csvArray[1].trim();
		if (X9FileUtils.existsWithPathTracing(new File(imageFolder))) {
			LOGGER.info("imageFolder({})", imageFolder);
		} else {
			throw X9Exception.abort("imageFolder notFound({}) recordNumber({}) content({})",
					imageFolder, recordNumber, X9String.joinWithLimits(csvArray));
		}
	}

	/**
	 * Set the output images folder; this is optional and can be done multiple times in a given run.
	 *
	 * @param recordNumber
	 *            current csv record number
	 * @param csvArray
	 *            csvArray which represents the image to be written
	 */
	private void setOutputFolder(final int recordNumber, final String[] csvArray) {
		if (csvArray.length != OUTPUT_FOLDER_ID_LENGTH) {
			throw X9Exception.abort(
					"output folder csvLength({}) must be({}) recordNumber({}) " + "content({})",
					csvArray.length, OUTPUT_FOLDER_ID_LENGTH, recordNumber,
					X9String.joinWithLimits(csvArray));
		}
		outputFolder = csvArray[1].trim();
		if (StringUtils.isBlank(outputFolder)) {
			throw X9Exception.abort("output folder missing recordNumber({})", recordNumber);
		}
	}

	/**
	 * Get a field value when provided but return an empty string when "blank".
	 *
	 * @param csvArray
	 *            current csv array
	 * @param index
	 *            current csv index
	 * @return assigned field value
	 */
	private static String getFieldValue(final String[] csvArray, final int index) {
		return index < csvArray.length ? getFieldValue(csvArray[index]) : "";
	}

	/**
	 * Get a field value when provided but also return an empty string when "blank".
	 *
	 * @param value
	 *            xml field value
	 * @return assigned field value
	 */
	private static String getFieldValue(final String value) {
		return StringUtils.equals(value, BLANK) ? "" : value;
	}

}
