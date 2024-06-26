package com.x9ware.examples;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.base.X9SdkBase;
import com.x9ware.base.X9SdkRoot;
import com.x9ware.create.X9Coordinate;
import com.x9ware.draw.X9DrawItemBack;
import com.x9ware.draw.X9DrawItemFront;
import com.x9ware.draw.X9DrawTools;
import com.x9ware.draw.X9FrontFields;
import com.x9ware.draw.X9TextLine;
import com.x9ware.draw.X9TextList;
import com.x9ware.elements.X9C;
import com.x9ware.fontTools.X9FontManager;
import com.x9ware.imageio.X9BitonalImage;
import com.x9ware.imageio.X9ImageIO;
import com.x9ware.imageio.X9ImageInfo;
import com.x9ware.imageio.X9ImageScaler;
import com.x9ware.imaging.X9CheckFormat;
import com.x9ware.imaging.X9FormatManager;
import com.x9ware.imaging.X9ImageIE;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.micr.X9MicrLine;
import com.x9ware.tiffTools.X9TiffWriter;
import com.x9ware.toolbox.X9AmountToWords;
import com.x9ware.toolbox.X9LineSplitter;
import com.x9ware.tools.X9D;
import com.x9ware.tools.X9Decimal;
import com.x9ware.tools.X9EventQueue;
import com.x9ware.tools.X9FileIO;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9ImageUtilities;
import com.x9ware.tools.X9LocalDate;

/**
 * X9DrawImage provides examples of dynamically drawing check images using one of several
 * techniques. First is the ability to draw from an SDK internally defined template, which might be
 * typically used for an RCC. These templates are defined as resources within the SDK JAR and it is
 * also possible to do this drawing using externally defined resources. Second is the ability to
 * draw directly onto an image canvas which can begin either as blank or initialized from an
 * externally defined image (PNG format is recommended).
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9DrawImage {

	/*
	 * Private.
	 */
	private final X9SdkBase sdkBase = new X9SdkBase();
	private final File baseFolder = new File(
			"c:/users/x9ware5/documents/x9_assist/files_SdkExamples");
	private final JFrame frame = new JFrame();
	private X9BitonalImage frontImage;
	private X9BitonalImage backImage;
	private byte[] frontTiffImage;
	private byte[] backTiffImage;

	/*
	 * Constants.
	 */
	private static final String X9DRAWIMAGE = "X9DrawImage";
	private static final String ARIAL = X9FontManager.ARIAL; // loaded from system font folder
	private static final String SERIF = Font.SERIF;
	private static final int DRAW_DPI = X9C.CHECK_IMAGE_DPI_240;

	/*
	 * Front font definitions.
	 */
	/*
	 * Load fonts and size based on our current drawing dpi.
	 */
	private static final Font FONT_12 = X9FontManager.loadFont(ARIAL, Font.PLAIN, 26);
	private static final Font FONT_13 = X9FontManager.loadFont(SERIF, Font.PLAIN, 40);
	private static final Font FONT_15_ITALIC = X9FontManager.loadFont(SERIF,
			Font.ITALIC + Font.BOLD, 46);
	private static final Font FONT_18 = X9FontManager.loadFont(SERIF, Font.PLAIN, 50);
	private static final Font FONT_18_BOLD = X9FontManager.loadFont(SERIF, Font.BOLD, 60);

	/*
	 * Back font definitions.
	 */
	private static final Font FONT_A08 = new Font(X9FontManager.ARIAL, Font.PLAIN, 8);
	private static final Font FONT_A10 = new Font(X9FontManager.ARIAL, Font.PLAIN, 10);

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9DrawImage.class);

	/*
	 * X9DrawImage Constructor.
	 */
	public X9DrawImage() {
		/*
		 * Set the runtime license key (this class is part of sdk-examples). X9RuntimeLicenseKey
		 * must be updated with the license key text provided for your evaluation.
		 */
		X9RuntimeLicenseKey.setLicenseKey();

		/*
		 * Initialize the environment.
		 */
		X9SdkRoot.logStartupEnvironment(X9DRAWIMAGE);
		X9SdkRoot.loadXmlConfigurationFiles();
	}

	/**
	 * Draw and display.
	 */
	public void process() {
		/*
		 * Draw images using from our predefined RCC templates. The advantage of this approach is
		 * that fields are externally defined in an XML document and not hard wired here.
		 */
		// drawFromRccTemplate();

		/*
		 * Draw images using a custom designed template. The advantage of this approach is that you
		 * have full control of the canvas and can thus draw anything anywhere, including more
		 * advanced functions such as inserting secondary images, draw rotated text, etc.
		 */
		drawFromCustomTemplate();

		/*
		 * Log tiff image sizes.
		 */
		LOGGER.info("frontTiffImage length({}) backTiffImage length({})", frontTiffImage.length,
				backTiffImage.length);

		/*
		 * Set flow layout.
		 */
		frame.getContentPane().setLayout(new FlowLayout());

		/*
		 * Add images to the panel.
		 */
		frame.add(new JLabel(X9ImageScaler.getScaledIcon(frontImage, 550, 400)));
		frame.add(new JLabel(X9ImageScaler.getScaledIcon(backImage, 550, 400)));

		/*
		 * Pack and display.
		 */
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	/**
	 * Draw using our rcc template.
	 */
	public void drawFromRccTemplate() {
		/*
		 * Assign the check format template. All field level attributes (coordinates, lengths,
		 * fonts, font sizes, etc) will be assigned from that format definition.
		 */
		final String templateName = "rcc1";
		final X9FormatManager x9formatManager = sdkBase.getFormatManager();
		final X9CheckFormat x9checkFormat = x9formatManager.getFormat(templateName);

		if (x9checkFormat == null) {
			throw X9Exception.abort("format notFound({})", templateName);
		}

		/*
		 * Allocate front image drawing tools.
		 */
		final X9DrawItemFront x9drawItemFront = new X9DrawItemFront(sdkBase);
		x9drawItemFront.setFormat(x9checkFormat);

		/*
		 * Assign front image fields.
		 */
		final LocalDate checkDate = X9LocalDate.getCurrentDate();
		final long amount = 123456;
		final String[] nameAddress = new String[5];
		nameAddress[0] = "Heading line 1";
		nameAddress[1] = "Heading line 2";
		nameAddress[2] = "Heading line 3";
		final String[] payeeName = new String[3];
		payeeName[0] = "Payee name line 1";
		payeeName[1] = "Payee name line 2";
		payeeName[2] = "Payee name line 3";
		final String memoLine = "Memo line text here";
		final String bankName = "bank name text here";
		final String signatureLine = "Signature line text here";
		final String routing = "123456780";
		final String onus = "123456789012";
		final String auxOnus = "123456";
		final String checkNumber = "";
		final String epc = "";

		/*
		 * Format a micr line with the amount excluded.
		 */
		final X9MicrLine x9micrLine = new X9MicrLine(sdkBase);
		final String micrLine = x9micrLine.formatMicrLine(auxOnus, epc, routing,
				X9MicrLine.NO_AMOUNT, onus);

		/*
		 * Set the front image fields.
		 */
		final X9FrontFields frontFields = new X9FrontFields();
		frontFields.setItemAmount(X9Decimal.getAsAmount(amount));
		frontFields.setCheckDate(checkDate);
		frontFields.setBankName(bankName);
		frontFields.setNameAddress(nameAddress);
		frontFields.setPayeeName(payeeName);
		frontFields.setMemoLine(memoLine);
		frontFields.setSignatureLine(signatureLine);
		frontFields.setSerialNumber(StringUtils.isNotBlank(auxOnus) ? auxOnus : checkNumber);
		frontFields.setMicrLine(micrLine);

		/*
		 * Draw the front image.
		 */
		frontImage = x9drawItemFront.drawFront(frontFields, DRAW_DPI);

		/*
		 * Allocate back image drawing tools.
		 */
		final X9DrawItemBack x9drawItemBack = new X9DrawItemBack(sdkBase);
		x9drawItemBack.setFormat(x9checkFormat);

		/*
		 * Create the back image (often a constant and then used repetitively).
		 */
		backImage = x9drawItemBack.drawImage("endorsement line 1", "endorsement line 2", DRAW_DPI);

		/*
		 * Convert the front and back images to tiff byte arrays.
		 */
		final int dpi = sdkBase.getDrawOptions().getDrawDpi();
		frontTiffImage = X9TiffWriter.createTiff(frontImage, dpi);
		backTiffImage = X9TiffWriter.createTiff(backImage, dpi);
	}

	/**
	 * Draw using a custom template.
	 */
	public void drawFromCustomTemplate() {
		/*
		 * Load the front image template (done only once for optimization). Recommendation is that
		 * templates be stored in PNG format, bitonal (not grayscale), and at 240 DPI.
		 */
		final File templateFile = new File(baseFolder, "/images/drawImagetemplate.png");

		if (!X9FileUtils.existsWithPathTracing(templateFile)) {
			throw X9Exception.abort("template notFound({})", templateFile);
		}

		final byte[] templateByteArray = X9FileIO.readFile(templateFile);
		final BufferedImage templateImage = X9ImageIO.readImage(templateByteArray);
		final X9ImageInfo x9imageInfo = new X9ImageInfo();
		final int dpi = x9imageInfo.interrogateImage(templateByteArray)
				? Math.round(x9imageInfo.getXdpi())
				: X9C.CHECK_IMAGE_DPI_240;

		/*
		 * Create a blank back image (this should be drawn only once for optimization).
		 */
		backImage = X9BitonalImage.createBlankImage(templateImage.getWidth(),
				templateImage.getHeight());

		/*
		 * Make a new copy of the front side template image for each item that is drawn.
		 */
		frontImage = X9BitonalImage.createFromImage(templateImage);
		Graphics2D g2d = X9ImageUtilities.createG2d(frontImage, X9ImageUtilities.QUALITY_ENABLED);

		/*
		 * Assign item related field values.
		 */
		final BigDecimal itemAmount = new BigDecimal("1888.88");
		final String checkNumber = "9622";
		final LocalDate checkDate = X9LocalDate.getCurrentDate();

		/*
		 * Format the payee line and extend with asterisks on the right.
		 */
		final float payeeX = 0.75F;
		final int payeeLength = Math.round((5.70F - payeeX) * dpi);
		final Font payeeFont = FONT_18;
		final FontMetrics fontMetrics = g2d.getFontMetrics(payeeFont);
		String payeeName = "Your Payee Name";
		while (fontMetrics.stringWidth(payeeName) < payeeLength) {
			payeeName += " *";
		}

		/*
		 * Create a list of text fields with specific fonts and coordinates (in inches).
		 */
		final List<X9TextList> textList = new ArrayList<>();
		textList.add(new X9TextList(FONT_15_ITALIC, new X9Coordinate(0.30F, 1.00F),
				"First National Bank, N.A."));
		textList.add(new X9TextList(FONT_18_BOLD, new X9Coordinate(6.75F, 0.45F), checkNumber));
		textList.add(new X9TextList(FONT_12, new X9Coordinate(0.20F, 0.20F), "John Smith",
				"12345 AnyWhere St", "Springfield, Usa 12345-1111"));
		textList.add(new X9TextList(FONT_13, new X9Coordinate(6.45F, 0.90F),
				X9LocalDate.formatDate(checkDate, "MMMM dd,yyyy")));
		textList.add(new X9TextList(FONT_18, new X9Coordinate(6.15F, 1.45F),
				"$ " + X9D.formatBigDecimal(itemAmount)));
		textList.add(new X9TextList(payeeFont, new X9Coordinate(payeeX, 1.45F), payeeName));

		/*
		 * Format and add the legal amount. X9AmountToWords translates to a word string and can then
		 * optionally split into two lines for exceptionally large amounts. Remember that the image
		 * template must be designed to accept two lines when using this optional capability.
		 */
		final Font amountFont = FONT_18;
		g2d.setFont(amountFont);
		final float amountX = 0.20F;
		final int fieldSize = Math.round((6.80F - amountX) * dpi);
		final String amountString = X9AmountToWords.translateToWords(itemAmount);
		final List<String> amountList = X9LineSplitter.split(g2d, amountString, fieldSize,
				X9DrawItemFront.MINIMUM_WORDS_ON_FIRST_AMOUNT_LINE);
		textList.add(new X9TextList(FONT_18, new X9Coordinate(amountX, 1.90F), amountList));

		/*
		 * Draw fields onto the cloned image template.
		 */
		X9DrawTools.drawTextLines(g2d, dpi, textList);

		/*
		 * Format and draw a micr line; exclude the amount by setting it to zero.
		 */
		final String routing = "123456780";
		final String onus = "123456789012" + "/" + checkNumber;
		final String auxOnus = "";
		final String epc = "";
		final X9MicrLine x9micrLine = new X9MicrLine(sdkBase);
		final String micrLine = x9micrLine.formatMicrLine(auxOnus, epc, routing,
				X9MicrLine.NO_AMOUNT, onus);
		x9micrLine.drawMicrLine(g2d, frontImage, micrLine, dpi);

		/*
		 * Release front side image graphics.
		 */
		g2d.dispose();

		/*
		 * Allocate back side image graphics.
		 */
		g2d = X9ImageUtilities.createG2d(backImage, X9ImageUtilities.QUALITY_ENABLED);

		/*
		 * Create a list which contains the paid stamp text using various fonts.
		 */
		final List<X9TextLine> stamperList = new ArrayList<>();
		stamperList.add(new X9TextLine("For Deposit Only", FONT_A10));
		stamperList.add(new X9TextLine("Additional Comments", FONT_A10));
		stamperList.add(new X9TextLine("Account 1234567890", FONT_A10));
		stamperList.add(new X9TextLine("", FONT_A10));
		stamperList.add(new X9TextLine("Store 187", FONT_A10));
		stamperList.add(new X9TextLine("", FONT_A10));
		stamperList.add(new X9TextLine("Trn-Id 144929", FONT_A10));
		stamperList.add(new X9TextLine("", FONT_A10));

		/*
		 * Draw the paid endorsement.
		 */
		final X9TextLine heading = new X9TextLine("Company Name", FONT_A08);
		X9DrawTools.drawPaidEndorsementStamp(backImage, heading, stamperList, 1.20F,
				X9DrawTools.BOX_ENABLED, dpi);

		/*
		 * Release back side image graphics.
		 */
		g2d.dispose();

		/*
		 * Convert the front and back images to tiff byte arrays.
		 */
		frontTiffImage = X9TiffWriter.createTiff(frontImage, dpi);
		backTiffImage = X9TiffWriter.createTiff(backImage, dpi);

		/*
		 * Write the constructed images.
		 */
		final File outputFolder = new File("c:/users/x9ware5/downloads");
		X9ImageIE.putImage(new File(outputFolder, "frontImage.tif"), frontImage, frontTiffImage);
		X9ImageIE.putImage(new File(outputFolder, "back.tif"), backImage, backTiffImage);
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
			LOGGER.info(X9DRAWIMAGE + " started");
			try {
				final X9DrawImage example = new X9DrawImage();
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
