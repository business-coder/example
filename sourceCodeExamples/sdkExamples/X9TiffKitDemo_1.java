package com.x9ware.examples;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.apacheIO.IOCase;
import com.x9ware.apacheIO.WildcardFileFilter;
import com.x9ware.elements.X9C;
import com.x9ware.elements.X9Root;
import com.x9ware.imageio.X9ImageRequirements;
import com.x9ware.imageio.X9ImageResults;
import com.x9ware.logging.X9JdkLogger;
import com.x9ware.tiffKit.X9TiffKit;
import com.x9ware.tiffTools.X9Tags;
import com.x9ware.tiffTools.X9TiffDirectory;
import com.x9ware.tiffTools.X9TiffField;
import com.x9ware.tiffTools.X9TiffRepair;
import com.x9ware.tools.X9D;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9Pattern;

/**
 * X9TiffKitDemo is a demonstration of our TIFF KIT capabilities.
 * 
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9TiffKitDemo {

	/*
	 * Private.
	 */
	private final X9TiffKit x9tiffKit;
	private final X9ImageRequirements x9imageRequirements = new X9ImageRequirements();
	private final List<String> repairActionList = new ArrayList<>();
	private String errorMessage;
	private boolean isImageWritten;
	private int imagesAnalyzed;
	private int imagesFlawed;
	private int imagesNoActionNeeded;
	private int imagesRepaired;
	private int imagesRepairedAndResized;
	private int imagesSharpened;
	private int imagesNotResizedDueToAspectRatio;
	private int imagesNotRepaired;
	private int imagesNotRepairable;
	private int imagesConvertedToTiff;
	private int imagesWrittenByFinally;
	private int imagesNotWritten;

	/*
	 * Constants.
	 */
	private static final int MAXIMUM_VALUE = Integer.MAX_VALUE;
	private static final int MINIMUM_TIFF_LENGTH = 20;
	private static final int LARGE_IMAGE_THRESHOLD_TO_TRIGGER_SHARPENING = 200 * 1024; // 200k

	/*
	 * Client name: tiffkit evaluation; Company name: X9Ware LLC; Product name: X9TiffKit; License
	 * key: ac60-d6dd-cbb1-fb8d; Entered date: 2022/02/16; Expiration date: 2022/04/22.
	 */
	private static String licenseXmlDocument = "19D8F9BE172AC761E330B3053A5121EF556BCC3D4BF3B0DD55"
			+ "A736F2C9CC84760C8D82284A2EE90D5070DA48E47025A2A56B"
			+ "D18A1ECDB6A58E298ABFD5143388E783DC9A3A9B0B62E7AB46"
			+ "4BF23D129045F542D49DE144C22681A8BBD279CADECD6E6B95"
			+ "3E0CCD55CB2A77CC02E0AF4FEA2BCDA01DFC58AF85F2456A18"
			+ "13097616A50E952019CDDB046EC6C8A15313FE7481C84FD617"
			+ "3040E4EC307FCF2A383400CC8715B8D56BE0A5CF8463E04841"
			+ "F367C0C84B3ED3ABAB31548E09E934E992A2BA084943E9B83F"
			+ "2663B5179E944A43125A692EB85F5F03E68549854A0D6B224A"
			+ "4518FA6AA0712B8CDC77B3949993DC80F4BEA41A452F757F35"
			+ "867CDAA760DF17B10A4EEE6563C87320A70FABFE058C56EA30"
			+ "37C1EBE65C680EA8C48655F5664B166C5F18BE4A44592E4B2B"
			+ "61FC0FCD129ACCAE97B5BD287460E8DB11E1FC49E4A53E7EE8"
			+ "D183E31D1CB49393049E84364697D7A7A0605F96EFD802F7CF"
			+ "4EF484F7016B602092B6ED269B131BBEE4A9112BB429F8842E"
			+ "D9AE017926BF800633C96C207A2D4FB01F058B1157D7E66192"
			+ "EFD10286A7B15AD33D8FE5F8EC69C33854DFB204261390FC62"
			+ "35B99EBF3E99FDFD6751DFB73F2BB13397FA24FF5F0BF7B2A3"
			+ "EF4AB94E9B52FBD5EC19572A238D131E2B38099AB37F6159CA"
			+ "E74693DB2C2CE055F005E20EAF5709498F3B818B8F8296FC34"
			+ "C095D98C8B876F9B60B900EF59E272147271CD6168009EB65A"
			+ "C2F94C164CFB85821CE0D971CC982546E33764EDC3397F94C3"
			+ "3CABA62D870EBF9C5406CA68D6BC5B8C8393470580C241EB13"
			+ "32027E0DCDE7D75B118724F8264E3B1E1556633918BA2AC9C0"
			+ "E4E23ECB1FCB5339763E3ED08FCCA337E0A3667251FEAFB943"
			+ "75315A9466AFF2D4336C918859D20DD87C020C43CDF450A65D"
			+ "3B62D00A7DF3B659A7B3B7F8A8291C7C2EAAB415DE35E4ECE2"
			+ "46D711832EF8290F495E6A175C6F92741ABE199473B074873C"
			+ "27D265E0F7B4D72F61F4DC4F0A5411C354F6AE71";

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9TiffKitDemo.class);

	/**
	 * X9TiffKitDemo constructor.
	 */
	public X9TiffKitDemo() {
		/*
		 * Set the license key (this is a one-time initialization activity).
		 */
		x9tiffKit = new X9TiffKit(licenseXmlDocument);

		/*
		 * Define exchange requirements for mandatory tiff tags.
		 */
		x9tiffKit.addMandatoryTag(X9Tags.IMAGE_WIDTH, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_ALLOWED, 1, MAXIMUM_VALUE);
		x9tiffKit.addMandatoryTag(X9Tags.IMAGE_LENGTH, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_ALLOWED, 1, MAXIMUM_VALUE);
		x9tiffKit.addMandatoryTag(X9Tags.COMPRESSION, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 4, 4);
		x9tiffKit.addMandatoryTag(X9Tags.PHOTOMETRIC_INTERPRETATION, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 0, 0);
		x9tiffKit.addMandatoryTag(X9Tags.STRIP_OFFSETS, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_ALLOWED, 1, MAXIMUM_VALUE);
		x9tiffKit.addMandatoryTag(X9Tags.ROWS_PER_STRIP, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_ALLOWED, 1, MAXIMUM_VALUE);
		x9tiffKit.addMandatoryTag(X9Tags.STRIP_BYTE_COUNTS, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_ALLOWED, 1, MAXIMUM_VALUE);

		/*
		 * Define exchange requirements for optional tiff tags.
		 */
		x9tiffKit.addOptionalTag(X9Tags.NEW_SUBFILE, X9TiffKit.SHORT_TYPE_NOT_ALLOWED,
				X9TiffKit.LONG_TYPE_ALLOWED, 0, 0);
		x9tiffKit.addOptionalTag(X9Tags.BITS_PER_SAMPLE, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 1, 1);
		x9tiffKit.addOptionalTag(X9Tags.THRESHOLDING, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 1, 1);
		x9tiffKit.addOptionalTag(X9Tags.FILL_ORDER, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 1, 1);
		x9tiffKit.addOptionalTag(X9Tags.ORIENTATION, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 1, 1);
		x9tiffKit.addOptionalTag(X9Tags.SAMPLES_PER_PIXEL, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 1, 1);
		x9tiffKit.addOptionalTag(X9Tags.PLANAR_CONFIGURATION, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 1, 1);
		x9tiffKit.addOptionalTag(X9Tags.T6_OPTIONS, X9TiffKit.SHORT_TYPE_NOT_ALLOWED,
				X9TiffKit.LONG_TYPE_ALLOWED, 0, 0);
		x9tiffKit.addOptionalTag(X9Tags.RESOLUTION_UNIT, X9TiffKit.SHORT_TYPE_ALLOWED,
				X9TiffKit.LONG_TYPE_NOT_ALLOWED, 2, 2);
	}

	/**
	 * Inspect and repair all images within an input folder and write to an output folder.
	 * 
	 * @param inputFolder
	 *            input image folder
	 * @param outputFolder
	 *            output image folder
	 * @param wildCardPatterns
	 *            wild card patterns to be selected from the input folder (eg, "*.tif")
	 * @return list of repair messages as a result of all repair actions
	 */
	public List<String> inspectAndRepairFromFolder(final File inputFolder, final File outputFolder,
			final String[] wildCardPatterns) {
		/*
		 * Get a list of all images in the provided input folder.
		 */
		final long startTime = System.currentTimeMillis();
		final List<File> imageFileList = new ArrayList<>();
		final WildcardFileFilter fileFilter = new WildcardFileFilter(wildCardPatterns,
				IOCase.INSENSITIVE);
		X9FileUtils.getFilteredListRecursively(inputFolder, imageFileList, fileFilter);

		/*
		 * Inspect and repair all images within the provided input folder.
		 */
		final int imageCount = imageFileList.size();
		final List<String> messageList = new ArrayList<>();
		if (imageCount > 0) {
			/*
			 * Use the tiff-kit to inspect and repair all images.
			 */
			for (final File inputImageFile : imageFileList) {
				try {
					final File outputImageFile = new File(outputFolder,
							FilenameUtils.getName(inputImageFile.toString()));
					final String repairMessage = inspectAndRepairOneImage(inputImageFile,
							outputImageFile);
					messageList.add(repairMessage);
				} catch (final Exception ex) {
					LOGGER.error("exception", ex);
				}
			}

			/*
			 * Log items per second.
			 */
			final long endTime = System.currentTimeMillis();
			final long elapsedTime = endTime - startTime;
			final float seconds = elapsedTime / 1000F;
			final float itemsPerSecond = seconds > 0 ? imageCount / seconds : 0;
			messageList.add(X9Pattern.format(">> imageCount({}) elapsedTime({}) itemsPerSecond({})",
					imageCount, elapsedTime, itemsPerSecond));
		}

		/*
		 * Return message list which represents all actions taken.
		 */
		return messageList;
	}

	/**
	 * Inspect and repair one image. This method returns a string which generally describes
	 * attributes of the input message and the actions that have been taken (when necessary). The
	 * 
	 * @param inputFile
	 *            input image file
	 * @param outputFile
	 *            output image file
	 * @return message string for image actions that have been taken
	 */
	public String inspectAndRepairOneImage(final File inputFile, final File outputFile) {
		/*
		 * Apply actions subject to image format and inspections.
		 */
		imagesAnalyzed++;
		errorMessage = "";
		isImageWritten = false;
		byte[] inputByteArray = null;
		try {
			/*
			 * Examine image byte array to determine the image format.
			 */
			inputByteArray = x9tiffKit.readImageByteArray(inputFile);
			final String imageFormat = x9tiffKit.getImageFormat(inputByteArray);
			if (StringUtils.isBlank(imageFormat)) {
				/*
				 * The image is fundamentally flawed since the format is unrecognizable.
				 */
				imagesFlawed++;
				errorMessage += X9Pattern.format("image flawed({})", inputFile);
				isImageWritten = x9tiffKit.writeImageByteArray(inputByteArray, outputFile);
			} else if (StringUtils.equals(imageFormat, X9C.TIF)) {
				/*
				 * Interrogate and repair an image identified as tiff from the byte array.
				 */
				interrogateAndRepairTiffImage(inputFile, outputFile, inputByteArray);
			} else {
				/*
				 * Attempt to update images when (unexpectedly) in some format other than tiff.
				 */
				imagesConvertedToTiff++;
				final X9ImageResults x9imageResults = x9tiffKit.repairOneImage(inputByteArray,
						x9imageRequirements, X9TiffRepair.RESIZE_ENABLED);
				errorMessage += X9Pattern.format("non-tiff image({}) {}", inputFile,
						x9imageResults.getRepairMessage());
				isImageWritten = x9tiffKit.writeImageByteArray(x9imageResults.getByteArray(),
						outputFile);
				if (isImageWritten) {
					addFileToRepairList("converted", inputFile);
				}
			}
		} catch (final Exception ex) {
			/*
			 * Catch and log any errors.
			 */
			LOGGER.error("exception", ex);
			errorMessage += X9Pattern.format(">> image ({}) exception({})", inputFile,
					ex.getCause().toString());
		} finally {
			/*
			 * Always write input images that were not written above.
			 */
			if (!isImageWritten) {
				try {
					if (inputByteArray != null
							&& x9tiffKit.writeImageByteArray(inputByteArray, outputFile)) {
						imagesWrittenByFinally++;
						errorMessage += X9Pattern.format(">> trapped and written({}))", inputFile);
					} else {
						imagesNotWritten++;
						errorMessage += X9Pattern.format(">> trapped NOT WRITTEN({}))", inputFile);
					}
				} catch (final Exception ex) {
					LOGGER.error("exception", ex);
				}
			}
		}

		/*
		 * Return the accumulated output message which summarizes status and actions taken.
		 */
		return errorMessage;
	}

	/**
	 * Interrogate and repair a tiff image.
	 * 
	 * @param inputFile
	 *            input image file
	 * @param outputFile
	 *            output image file
	 * @param inputByteArray
	 *            byte array loaded from the input image file
	 * @throws IOException
	 */
	private void interrogateAndRepairTiffImage(final File inputFile, final File outputFile,
			final byte[] inputByteArray) throws IOException {
		/*
		 * Interrogate the image when determined to be in tiff format.
		 */
		final X9TiffDirectory x9tiffDirectory = new X9TiffDirectory();
		final String dirMessage = x9tiffKit.determineIfImageIsExchangeable(x9tiffDirectory,
				inputByteArray, x9imageRequirements);
		final boolean isExchangeable = StringUtils.isBlank(dirMessage);
		if (isExchangeable) {
			/*
			 * Attempt sharpening for exchangeable images that are excessively large. This is done
			 * when the images may fail downstream IQA due to size. This sharpening process attempts
			 * to remove pixel noise that can be generated by less than optimal scanners.
			 */
			if (inputByteArray.length > LARGE_IMAGE_THRESHOLD_TO_TRIGGER_SHARPENING) {
				final byte[] outputByteArray = x9tiffKit.applyImageSharpening(x9tiffDirectory,
						inputByteArray);
				if (outputByteArray.length <= LARGE_IMAGE_THRESHOLD_TO_TRIGGER_SHARPENING) {
					imagesSharpened++;
					errorMessage += X9Pattern.format(
							"image sharpened width({}) height({}) dpi({}) widthInInches({}) "
									+ "heightInInches({}) inputLength({}) outputLength({}) "
									+ "file({})",
							x9tiffDirectory.getWidth(), x9tiffDirectory.getHeight(),
							x9tiffDirectory.calculateDpi(),
							X9D.formatFloat(x9tiffDirectory.getWidthInInches(), 2),
							X9D.formatFloat(x9tiffDirectory.getHeightInInches(), 2),
							inputByteArray.length, outputByteArray.length, inputFile);
					isImageWritten = x9tiffKit.writeImageByteArray(outputByteArray, outputFile);
				}
			}

			/*
			 * Copy the image as-is when determined to be exchangeable and image size was OK.
			 */
			if (!isImageWritten) {
				imagesNoActionNeeded++;
				errorMessage += X9Pattern.format(
						"image exchangeable width({}) height({}) dpi({}) widthInInches({}) "
								+ "heightInInches({}) file({})",
						x9tiffDirectory.getWidth(), x9tiffDirectory.getHeight(),
						x9tiffDirectory.calculateDpi(),
						X9D.formatFloat(x9tiffDirectory.getWidthInInches(), 2),
						X9D.formatFloat(x9tiffDirectory.getHeightInInches(), 2), inputFile);
				isImageWritten = x9tiffKit.writeImageByteArray(inputByteArray, outputFile);
			}
		} else {
			/*
			 * This image has not passed tiff tag inspection rules.
			 */
			errorMessage += X9Pattern.format("image({}) dirMessage({})", inputFile, dirMessage);

			/*
			 * Log the input tiff image directory.
			 */
			logTiffDirectory(x9tiffDirectory,
					"input:" + FilenameUtils.getBaseName(inputFile.toString()));

			/*
			 * Repair the image when possible.
			 */
			if (x9tiffKit.isTiffImageRepairable()) {
				/*
				 * Repair tiff images.
				 */
				final X9ImageResults x9imageResults = x9tiffKit.repairOneImage(inputByteArray,
						x9imageRequirements, X9TiffRepair.RESIZE_ENABLED);
				errorMessage += X9Pattern.format("results: {}", x9imageResults.getRepairMessage());

				/*
				 * Repair feedback tells us that the image has been repaired. We now double check
				 * that by inspecting the tiff byte array was created and ensuring that there are no
				 * errors, which means that all required tags are present.
				 */
				final byte[] outputByteArray = x9imageResults.getByteArray();
				if (x9imageResults.isRepaired() && outputByteArray.length > MINIMUM_TIFF_LENGTH
						&& StringUtils
								.isBlank(x9tiffDirectory.validateImageDirectory(outputByteArray))) {
					/*
					 * Images can be repaired and then optionally resized when required. The resize
					 * will be done when the image size (in inches) is either too small or too large
					 * per exchange standards. This is highly unusual, but will happen if the dpi is
					 * incredibly incorrect.
					 */
					final boolean isImageResized = x9imageResults.isResized();
					if (isImageResized) {
						imagesRepairedAndResized++;
					} else {
						imagesRepaired++;
					}

					/*
					 * Images are not resized when the aspect ration cannot be retained.
					 */
					if (x9imageResults.isNotResizedDueToAspectRatio()) {
						imagesNotResizedDueToAspectRatio++;
					}

					/*
					 * Write the repaired tiff image and add to our repair list.
					 */
					isImageWritten = x9tiffKit.writeImageByteArray(outputByteArray, outputFile);
					if (isImageWritten) {
						final String repairAction = isImageResized ? "repaired+resized"
								: "repaired";
						addFileToRepairList(repairAction, inputFile);
					}

					/*
					 * Log the output tiff image directory.
					 */
					// logTiffDirectory(x9tiffDirectory, "output");
				} else {
					/*
					 * Copy the image as-is when tiff image repair has failed.
					 */
					imagesNotRepaired++;
					errorMessage += X9Pattern.format("image not repairable({})", inputFile);
					isImageWritten = x9tiffKit.writeImageByteArray(inputByteArray, outputFile);
				}
			} else {
				/*
				 * Tiff image is not repairable.
				 */
				imagesNotRepairable++;
				errorMessage += X9Pattern.format("image({}) has invalid tiff directory({})",
						inputFile, dirMessage);
				isImageWritten = x9tiffKit.writeImageByteArray(inputByteArray, outputFile);
			}
		}
	}

	/**
	 * Log final runtime statistics.
	 */
	private void logStatistics() {
		/*
		 * Log all repaired images.
		 */
		Collections.sort(repairActionList);
		for (final String repairAction : repairActionList) {
			LOGGER.info(">> " + repairAction);
		}

		/*
		 * And log our final runtime counters.
		 */
		LOGGER.info(
				"finished; imagesAnalyzed({}) imagesFlawed({}) imagesNoActionNeeded({}) "
						+ "imagesRepaired({}) imagesRepairedAndResized({}) imagesSharpened({}) "
						+ "imagesNotResizedDueToAspectRatio({}) imagesNotRepaired({}) "
						+ "imagesNotRepairable({}) imagesConvertedToTiff({}) "
						+ "imagesWrittenByFinally({}) imagesNotWritten({})",
				imagesAnalyzed, imagesFlawed, imagesNoActionNeeded, imagesRepaired,
				imagesRepairedAndResized, imagesSharpened, imagesNotResizedDueToAspectRatio,
				imagesNotRepaired, imagesNotRepairable, imagesConvertedToTiff,
				imagesWrittenByFinally, imagesNotWritten);
	}

	/**
	 * Add another file to the repaired file actions list.
	 * 
	 * @param group
	 *            group name
	 * @param repairFile
	 *            file that has been repaired
	 */
	private void addFileToRepairList(final String group, final File repairFile) {
		repairActionList.add(group + " " + repairFile);
	}

	/**
	 * Log tiff fields as example of more detailed functionality.
	 * 
	 * @param x9tiffDirectory
	 *            current tiff directory
	 * @param identifier
	 *            tiff directory identifier
	 */
	private void logTiffDirectory(final X9TiffDirectory x9tiffDirectory, final String identifier) {
		final X9TiffField[] x9tiffFields = x9tiffDirectory.getIfd();
		for (final X9TiffField x9tiffField : x9tiffFields) {
			LOGGER.info("ifd({}) tiffTag({}) type({}) count({}) value({})", identifier,
					x9tiffField.getTag(), x9tiffField.getType(), x9tiffField.getCount(),
					x9tiffField.getTagValue());
		}
	}

	/**
	 * Demonstration of multi-repair of all images that exist within an input folder.
	 */
	private void demoRepairFromFolder() {
		/*
		 * Define input and output folders.
		 */
		final File inputFolder = new File("C:/Users/X9Ware5/Documents/x9_assist/imageKitTesting");
		final File outputFolder = new File("C:/Users/X9Ware5/Downloads/imageKitTesting");

		if (!X9FileUtils.existsWithPathTracing(inputFolder)) {
			throw X9Exception.abort("folder not found({})", inputFolder);
		}

		/*
		 * Inspect and repair all images within a test folder.
		 */
		final String[] patterns = new String[] { "*.tif", "*.png", "*.jpg" };
		final List<String> messageList = inspectAndRepairFromFolder(inputFolder, outputFolder,
				patterns);

		/*
		 * List all messages.
		 */
		for (final String message : messageList) {
			LOGGER.info(message);
		}
	}

	/**
	 * Demonstration of single image repair.
	 */
	private void demoRepairOneImage() {
		/*
		 * Define input and output images.
		 */
		final File inputFolder = new File("C:/Users/X9Ware5/Documents/x9_assist/imageKitTesting");
		final File inputFile = new File(inputFolder, "220214RP00060741_B.tif");
		// final File inputFile = new File(inputFolder, "thresholdtest7.tif");
		// final File inputFile = new File(inputFolder, "imageGray.tif");
		final File outputFile = new File(inputFolder, "workImage.tif");
		if (!X9FileUtils.existsWithPathTracing(inputFile)) {
			throw X9Exception.abort("image not found({})", inputFile);
		}

		/*
		 * Inspect and repair a single image.
		 */
		final String repairMessage = inspectAndRepairOneImage(inputFile, outputFile);
		LOGGER.info(repairMessage);
	}

	/**
	 * Main().
	 *
	 * @param args
	 *            command line arguments
	 */
	public static void main(final String[] args) {
		/*
		 * Initialize our TIFF KIT environment.
		 */
		X9JdkLogger.initializeLoggingEnvironment();
		X9Root.logStartupEnvironment("X9TiffKitDemo");

		/*
		 * Demonstrations.
		 */
		final X9TiffKitDemo tiffKitDemo = new X9TiffKitDemo();
		try {
			tiffKitDemo.demoRepairOneImage();
			// tiffKitDemo.demoRepairFromFolder();
		} catch (final Exception ex) {
			LOGGER.error("trapped", ex);
		} finally {
			/*
			 * Close the system log and exit.
			 */
			tiffKitDemo.logStatistics();
			X9Root.systemShutdown();
			X9JdkLogger.closeLog();
			System.exit(0);
		}
	}

}
