package com.x9ware.examples;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.draw.X9PaidStamp;
import com.x9ware.draw.X9TextLine;
import com.x9ware.elements.X9C;
import com.x9ware.fontTools.X9FontManager;
import com.x9ware.imageio.X9BitonalImage;
import com.x9ware.imageio.X9ImageIO;
import com.x9ware.imageio.X9ImageScaler;
import com.x9ware.imaging.X9BlankImage;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tiff.X9TiffInspector;
import com.x9ware.tiff.X9TiffRules;
import com.x9ware.tiffTools.X9TiffWriter;
import com.x9ware.tools.X9EventQueue;
import com.x9ware.tools.X9FileIO;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9LocalDate;

/**
 * X9PaidEndorsement is an example of applying a paid endorsement to a back side image.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9PaidEndorsement extends JFrame {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final JFrame frame = new JFrame();
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");
	private final String currentDateAsString = X9LocalDate.formatDate(X9LocalDate.getCurrentDate());

	/*
	 * Constants.
	 */
	private static final String X9PAID_ENDORSEMENT = "X9PaidEndorsement";

	/*
	 * Font definitions.
	 */
	private static final Font FONT_A06 = new Font(X9FontManager.ARIAL, Font.PLAIN, 6);
	private static final Font FONT_A08 = new Font(X9FontManager.ARIAL, Font.PLAIN, 8);
	private static final Font FONT_A10 = new Font(X9FontManager.ARIAL, Font.PLAIN, 10);

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9PaidEndorsement.class);

	/*
	 * X9PaidEndorsement Constructor.
	 */
	public X9PaidEndorsement() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment.
		 */
		X9SdkRoot.logStartupEnvironment(X9PAID_ENDORSEMENT);
		X9SdkRoot.loadXmlConfigurationFiles();
	}

	/**
	 * Draw and display.
	 */
	public void process() {
		/*
		 * Draw the endorsement stamp.
		 */
		final BufferedImage bi = drawEndorsementStamp();

		/*
		 * Set flow layout.
		 */
		frame.getContentPane().setLayout(new FlowLayout());

		/*
		 * Add images to the panel.
		 */
		frame.add(new JLabel("Image"));
		frame.add(new JLabel(X9ImageScaler.getScaledIcon(bi, 1100, 800)));

		/*
		 * Pack and display.
		 */
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	/**
	 * Draw the paid endorsement stamp.
	 *
	 * @return buffered image for the drawn endorsement stamp
	 */
	public BufferedImage drawEndorsementStamp() {
		/*
		 * Allocate helpers.
		 */
		final X9TiffInspector x9tiffInspector = new X9TiffInspector(sdkBase);

		/*
		 * Draw the paid endorsement stamp.
		 */
		int dpi = 0;
		File imageFile = null;
		byte[] tiffArray = null;
		BufferedImage bi = null;
		try {
			/*
			 * Get the input tiff image.
			 */
			imageFile = new File(baseFolder, "images/backImage.tif");

			if (X9FileUtils.existsWithPathTracing(imageFile)) {
				tiffArray = X9FileIO.readFile(imageFile);
			} else {
				LOGGER.error("tiff image notFound({})", imageFile, new Throwable());
			}

			/*
			 * Determine if we have a valid tiff image.
			 */
			final boolean isValidTiff = tiffArray != null
					&& x9tiffInspector.isValidImage(tiffArray, X9TiffRules.BITONAL_RULES);

			/*
			 * Convert from a tiff byte array to a buffered image.
			 */
			if (isValidTiff) {
				dpi = x9tiffInspector.getXdpiRounded();
				bi = X9ImageIO.readImage(tiffArray);
			} else {
				LOGGER.error("invalid tiff array({})", imageFile, new Throwable());
			}

			/*
			 * Substitute a blank image when we do have have a buffered image.
			 */
			if (bi == null) {
				LOGGER.error("creating blank buffered image");
				dpi = X9C.CHECK_IMAGE_DPI_240;
				final int w = Math.round(X9C.CHECK_WIDTH_IN_INCHES * dpi);
				final int h = Math.round(X9C.CHECK_HEIGHT_IN_INCHES * dpi);
				bi = new X9BitonalImage(w, h);
			}

			/*
			 * Create a list which contains the paid stamp text using specific fonts.
			 */
			final List<X9TextLine> textList = new ArrayList<>();
			textList.add(new X9TextLine("For Deposit Only", FONT_A10));
			textList.add(new X9TextLine("Additional Comments", FONT_A10));
			textList.add(new X9TextLine("Account 1234567890", FONT_A10));
			textList.add(new X9TextLine(currentDateAsString, FONT_A10));
			textList.add(new X9TextLine("Store 187", FONT_A10));
			textList.add(new X9TextLine("", FONT_A10));
			textList.add(new X9TextLine("Trn-ID 144929", FONT_A08));
			textList.add(new X9TextLine("", FONT_A10));

			/*
			 * Draw the paid stamp endorsement.
			 */
			final X9TextLine title = new X9TextLine("Company Name", FONT_A06);
			X9PaidStamp.draw(bi, title, textList, 1.20F, // title, text list, right margin
					X9PaidStamp.BOX_ENABLED, // draw a box around the paid stamp
					X9PaidStamp.FILL_DISABLED, // do not fill within the (retain those pixels)
					dpi); // dpi should be the same as the drawing image being provided

			X9PaidStamp.draw(bi, title, textList, 1.80F, // title, text list, right margin
					X9PaidStamp.BOX_ENABLED, // draw a box around the paid stamp
					X9PaidStamp.FILL_ENABLED, // do not fill within the (retain those pixels)
					dpi); // dpi should be the same as the drawing image being provided

			/*
			 * Convert the modified buffered image to a new tiff byte array.
			 */
			tiffArray = X9TiffWriter.createTiff(bi, dpi);

		} catch (final Exception ex) {
			LOGGER.error("draw exception", ex);
		} finally {
			/*
			 * Substitute a blank image as proxy if the process for some reason has failed.
			 */
			if (tiffArray == null) {
				LOGGER.error("blank image created as proxy for image({})", imageFile);
				tiffArray = X9BlankImage.getBlankImageTiffByteArray();
			}
		}

		/*
		 * Return the drawn image.
		 */
		return bi;
	}

	/**
	 * Main.
	 *
	 * @param args
	 *            arguments
	 */
	public static void main(final String[] args) {
		X9EventQueue.runLater(() -> {
			X9JdkLogger.initializeLoggingEnvironment();
			LOGGER.info(X9PAID_ENDORSEMENT + " started");
			try {
				final X9PaidEndorsement example = new X9PaidEndorsement();
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
