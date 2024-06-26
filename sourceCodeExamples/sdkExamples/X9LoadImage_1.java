package com.x9ware.examples;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.imageio.X9BitonalImage;
import com.x9ware.imageio.X9ImageInfo;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tiffTools.X9TiffImage;
import com.x9ware.tiffTools.X9TiffWriter;
import com.x9ware.tools.X9EventQueue;
import com.x9ware.tools.X9FileIO;
import com.x9ware.tools.X9FileUtils;

/**
 * X9LoadImage is an example of loading an external image which could be in a variety of formats
 * (PNG, JPG, GIF, or TIFF). The image will be converted to TIFF, with the ability to accept the
 * existing DPI or override it to a specific value. The resulting image could be inserted into an
 * x9.37 file being constructed with x9writer, or could be written to an external file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9LoadImage {

	/*
	 * Private.
	 */
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");

	/*
	 * Constants.
	 */
	private static final String X9LOADIMAGE = "X9LoadImage";
	private static final int DPI_200 = 200;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9LoadImage.class);

	/*
	 * X9DrawImage Constructor.
	 */
	public X9LoadImage() {
	}

	/**
	 * Load image and convert.
	 */
	public void process() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Get the image from an external file into a byte array.
		 */
		final File inputFile = new File(baseFolder, "testImage.png");

		if (!X9FileUtils.existsWithPathTracing(inputFile)) {
			throw X9Exception.abort("inputFile not found(" + inputFile + ")");
		}

		final byte[] imageByteArray = X9FileIO.readFile(inputFile);

		/*
		 * Get image attributes when they can be determined by inspection.
		 */
		final X9ImageInfo x9imageInfo = new X9ImageInfo();
		if (x9imageInfo.interrogateImage(imageByteArray)) {
			final String formatName = X9ImageInfo.getImageFormatName(x9imageInfo.getFormat());
			LOGGER.info("image format({}) formatName({}) width({}) height({}) xDpi({}) yDpi({}) "
					+ "widthInInches({}) heightInInches({}) compressionName({}) colorType({})",
					x9imageInfo.getFormat(), formatName, x9imageInfo.getWidth(),
					x9imageInfo.getHeight(), x9imageInfo.getXdpi(), x9imageInfo.getYdpi(),
					x9imageInfo.getWidthInInches(), x9imageInfo.getHeightInInches(),
					x9imageInfo.getCompressionName(), x9imageInfo.getColorType());
		} else {
			LOGGER.error("image format undetermined");
		}

		/*
		 * Use Java ImageIO to convert the image byte array to a Java BufferedImage. ImageIO
		 * supports common formats such as PNG, JPG, and GIF. Java 9 and higher also includes basic
		 * TIFF support. If the image that is being loaded is color (eg, RGB) then it will go
		 * through an intermediate gray scale conversion.
		 */
		BufferedImage bi = null;
		try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(imageByteArray)) {
			bi = ImageIO.read(inputStream);
		} catch (final Exception ex) {
			LOGGER.error("read exception", ex);
		}

		/*
		 * Convert the image from its original format to black-white.
		 */
		final X9BitonalImage bw = X9BitonalImage.createFromImage(bi);

		/*
		 * Create a tiff image and force the DPI to 200. Note that a BufferedImage is essentially a
		 * pixel array and it does not have a DPI associated with it. Hence the DPI has to be
		 * assigned when the TIFF image is encoded. In many situations, you will want to use the DPI
		 * as extracted from your input image, as obtained (above) using X9ImageInfo. In other
		 * situations, you may need to ignore the input DPI, when you know that it does not
		 * represent the actual pixels (width, height). In this example, we assign the DPI as 200.
		 */
		final X9TiffImage tiffImage = X9TiffWriter.makeDirectory(bw, DPI_200);
		final byte[] tiffImageArray = X9TiffWriter.encodeTiffImage(tiffImage);

		/*
		 * Write the resulting tiff output byte array to an external file so it can be inspected
		 * using a utility such as MS-Paint, GIMP, or IrfanView.
		 */
		try {
			final File outputFile = new File(baseFolder, "convertedImage.tif");
			X9FileIO.writeFile(tiffImageArray, outputFile);
			LOGGER.info("outputFile written({}) byteLength({})", outputFile, tiffImageArray.length);
		} catch (final Exception ex) {
			LOGGER.error("write exception", ex);
		}
	}

	/**
	 * Main().
	 *
	 * @param args
	 *            command line arguments
	 */
	public static void main(final String[] args) {
		X9EventQueue.runLater(() -> {
			X9JdkLogger.initializeLoggingEnvironment();
			LOGGER.info(X9LOADIMAGE + " started");
			try {
				final X9LoadImage example = new X9LoadImage();
				example.process();
			} catch (final Throwable t) { // catch both errors and exceptions
				LOGGER.error("main exception", t);
			} finally {
				X9SdkRoot.shutdown();
				X9JdkLogger.closeLog();
			}
		});
	}

}
