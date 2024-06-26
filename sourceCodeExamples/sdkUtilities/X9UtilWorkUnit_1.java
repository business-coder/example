package com.x9ware.utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.apacheIO.FilenameUtils;
import com.x9ware.base.X9SdkBase;
import com.x9ware.beans.X9UtilWorkUnitAttr;
import com.x9ware.config.X9ConfigSelector;
import com.x9ware.core.X9;
import com.x9ware.core.X9TotalsXml;
import com.x9ware.draw.X9DrawOptions;
import com.x9ware.elements.X9C;
import com.x9ware.error.X9Error;
import com.x9ware.records.X9CreditAttributes;
import com.x9ware.tools.X9CommandLine;
import com.x9ware.tools.X9File;
import com.x9ware.tools.X9FileUtils;
import com.x9ware.tools.X9LocalDate;
import com.x9ware.tools.X9Numeric;
import com.x9ware.tools.X9Task;
import com.x9ware.tools.X9TempFile;
import com.x9ware.validate.X9TrailerManager937;

/**
 * X9UtilWorkUnit defines the files that are associated with a single X9Util work unit which are
 * subsequently passed to various X9Util worker tasks. We include methods to create and populate a
 * new X9UtilWorkUnit object from provided inputs and to perform various other common functions for
 * those work efforts. Our design is to allow worker tasks to be created from command line arguments
 * or alternatively from other more complex sources such as line items within a command file
 * (allowing a large number of tasks to be performed in a single X9Util batch operation) or against
 * a directory (where work units are created from the file names themselves). These features take
 * advantage of the multi-threaded processing capabilities of the SDK where each work unit can have
 * its own X9SdkBase and function independently of all other concurrent tasks.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilWorkUnit {

	/*
	 * Public final.
	 */
	public final X9CommandLine x9commandLine;
	public final X9UtilWorkUnitList workUnitList;
	private final boolean isParseLoggingEnabled;

	/*
	 * Public.
	 */
	public String utilFunctionName;
	public File inputFile;
	public File imageFolder;
	public File secondaryFile;
	public File outputFile;
	public File resultsFile;
	public int status;

	/*
	 * Private.
	 */
	private long startTime;
	private long endTime;
	private boolean isValidWorkUnit;
	private boolean isImageRepairEnabled;
	private boolean isImageResizeEnabled;
	private final List<X9Error> processingErrorList = new ArrayList<>();
	private final List<String[]> csvResultsList = new ArrayList<String[]>();

	/*
	 * Credit actions are non-null only when they are assigned on the command line.
	 */
	private Boolean isCreditsAddToItemCount;
	private Boolean isCreditsAddToItemAmount;
	private Boolean isCreditsAddToImageCount;

	/*
	 * All function names. REMEMBER that when adding a new function, we must also take a look at
	 * X9UtilConsoleUx, because some minor changes will be needed there as well. Look specifically
	 * at method createFileSelectorTable().
	 */
	public static final String FUNCTION_CONSOLE = "Console";
	public static final String FUNCTION_TRANSLATE = "Translate";
	public static final String FUNCTION_WRITE = "Write";
	public static final String FUNCTION_DRAW = "Draw";
	public static final String FUNCTION_IMPORT = "Import";
	public static final String FUNCTION_EXPORT = "Export";
	public static final String FUNCTION_EXPORT_CSV = "ExportCsv";
	public static final String FUNCTION_VALIDATE = "Validate";
	public static final String FUNCTION_SCRUB = "Scrub";
	public static final String FUNCTION_MAKE = "Make";
	public static final String FUNCTION_MERGE = "Merge";
	public static final String FUNCTION_UPDATE = "Update";
	public static final String FUNCTION_SPLIT = "Split";
	public static final String FUNCTION_COMPARE = "Compare";
	public static final String FUNCTION_IMAGE_PULL = "ImagePull";

	public static final String[] FUNCTION_NAMES = new String[] { FUNCTION_CONSOLE,
			FUNCTION_TRANSLATE, FUNCTION_WRITE, FUNCTION_DRAW, FUNCTION_IMPORT, FUNCTION_EXPORT,
			FUNCTION_EXPORT_CSV, FUNCTION_VALIDATE, FUNCTION_SCRUB, FUNCTION_MAKE, FUNCTION_MERGE,
			FUNCTION_UPDATE, FUNCTION_SPLIT, FUNCTION_COMPARE, FUNCTION_IMAGE_PULL };

	public static final String[] AUTO_BIND_FUNCTION_NAMES = new String[] { FUNCTION_TRANSLATE,
			FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_EXPORT_CSV, FUNCTION_VALIDATE,
			FUNCTION_SCRUB, FUNCTION_UPDATE, FUNCTION_SPLIT, FUNCTION_COMPARE };

	/*
	 * Constants.
	 */
	public static final int MAXIMUM_ALLOWED_THREAD_COUNT = 12;
	public static final boolean PARSE_LOGGING_ENABLED = true; // parse logging
	public static final boolean PARSE_LOGGING_DISABLED = false; // versus quiet mode
	private static final int SKIP_INTERVAL_DEFAULT = 60;
	private static final String SUMMARY_SUFFIX = "_summary";
	private static final String HELP_BREAK_LINE = StringUtils.rightPad("", 80, "-");

	/*
	 * Global switches.
	 */
	public static final String SWITCH_CONFIG = "config";
	public static final String SWITCH_IMAGE_EXPORT = "i";
	public static final String SWITCH_EXCLUDE = "exclude";
	public static final String SWITCH_MULTI_FILE = "xm";
	public static final String SWITCH_DO_NOT_REWRITE = "dnr";
	public static final String SWITCH_DATE_TIME_STAMP = "dts";
	public static final String SWITCH_EXTENSION_INPUT = "exti";
	public static final String SWITCH_EXTENSION_OUTPUT = "exto";
	public static final String SWITCH_SKIP_INTERVAL = "skpi";
	public static final String SWITCH_AS_IS = "asis";
	public static final String SWITCH_DPI = "dpi";
	public static final String SWITCH_LOGGING = "l";
	public static final String SWITCH_WRITE_JSON_TOTALS = "j";
	public static final String SWITCH_WRITE_XML_TOTALS = "x";
	public static final String SWITCH_WRITE_TEXT_TOTALS = "t";
	public static final String SWITCH_WORK_UNIT = "workUnit";
	public static final String SWITCH_IMAGE_REPAIR_ENABLED = "imageRepairEnabled";
	public static final String SWITCH_IMAGE_RESIZE_ENABLED = "imageResizeEnabled";

	/*
	 * Our batch and script switches are specialized features that facilitate batch operations.
	 * Batch enables the execution of a model command against all files in a specified folder, by
	 * scanning through all content and generating a list of work units to be executed. Script, on
	 * the other hand, processes a text file of commands. In this case, the initial command line
	 * refers to the script, which is then loaded and used to create the work unit list. These
	 * functions are powerful and support threading, allowing the execution of groups of work units
	 * concurrently on background threads. Our primary objective is to enhance the performance while
	 * eliminating the need to implement threading support within each of our functional processors.
	 */
	public static final String SWITCH_BATCH = "batch";
	public static final String SWITCH_SCRIPT = "script";
	public static final String SWITCH_THREADS = "threads";
	public static final String SWITCH_ABORT_ON_EXCEPTION = "aoe";

	/*
	 * Switches which define the impact of credits on the trailer records. Each of these switches
	 * can be set to true or false (eg, "-creditsAddToItemCount:true"). This is an important
	 * facility, since we are not using an XML file to define a multiple of options (similar to the
	 * HeaderXml file used by -write), and we are using our trailer manager to recalculate and
	 * populate the trailer records, hence the absolute need for this. A future enhancement might be
	 * to always write the batch trailer records exactly as they are, and then to use that data to
	 * modify the cash letter and file control trailer records. That would be possible, but it would
	 * be difficult given the various record formats and x9 specifications.
	 */
	public static final String SWITCH_CREDITS_ADD_TO_ITEM_COUNT = "creditsAddToItemCount";
	public static final String SWITCH_CREDITS_ADD_TO_ITEM_AMOUNT = "creditsAddToItemAmount";
	public static final String SWITCH_CREDITS_ADD_TO_IMAGE_COUNT = "creditsAddToImageCount";

	/*
	 * Console switches.
	 */
	public static final String[] CONSOLE_SWITCHES = { SWITCH_LOGGING };

	/*
	 * Translate switches.
	 */
	public static final String SWITCH_INCLUDE_ADDENDA = "a";
	public static final String SWITCH_EXCLUDE_HEADERXML = "noHeaderXml";
	public static final String SWITCH_EXCLUDE_CREDITS = "noCredits";
	public static final String[] TRANSLATE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_INCLUDE_ADDENDA, SWITCH_EXCLUDE_HEADERXML, SWITCH_EXCLUDE_CREDITS,
			SWITCH_IMAGE_EXPORT };

	/*
	 * Write switches.
	 */
	public static final String SWITCH_HEADERS_XML = "xml";
	public static final String SWITCH_END_NOT_PROVIDED = "enp";
	public static final String[] WRITE_SWITCHES = { SWITCH_CONFIG, SWITCH_DPI, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_HEADERS_XML, SWITCH_DO_NOT_REWRITE, SWITCH_DATE_TIME_STAMP,
			SWITCH_IMAGE_REPAIR_ENABLED, SWITCH_IMAGE_RESIZE_ENABLED, SWITCH_END_NOT_PROVIDED };

	/*
	 * Draw switches.
	 */
	public static final String[] DRAW_SWITCHES = { SWITCH_CONFIG, SWITCH_DPI, SWITCH_LOGGING };

	/*
	 * Import switches.
	 */
	public static final String SWITCH_REPLACE_TRAILER_TOTALS = "r";
	public static final String[] IMPORT_SWITCHES = { SWITCH_CONFIG, SWITCH_DPI, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_REPLACE_TRAILER_TOTALS };

	/*
	 * Export switches.
	 */
	public static final String SWITCH_RECORD_TYPES = "rectypes";
	public static final String SWITCH_ABORT_WHEN_EMPTY = "awe";
	public static final String SWITCH_ITEM_EXPORT = "xf";
	public static final String SWITCH_ITEM_EXPORT_WITH_COLUMN_HEADINGS = "xfc";
	public static final String SWITCH_GROUP_EXPORT = "xg";
	public static final String SWITCH_TIFF_TAG_EXPORT = "xt";
	public static final String SWITCH_XML_EXPORT_FLAT = "xmlf";
	public static final String SWITCH_XML_EXPORT_HIERARCHICAL = "xmlh";
	public static final String SWITCH_CSV_EXPORT = "xc";
	public static final String SWITCH_IMAGE_RELATIVE = "ir";
	public static final String SWITCH_IMAGE_EXPORT_NONE = "none";
	public static final String SWITCH_IMAGE_EXPORT_TIF = "tif";
	public static final String SWITCH_IMAGE_EXPORT_JPG = "jpg";
	public static final String SWITCH_IMAGE_EXPORT_PNG = "png";
	public static final String SWITCH_IMAGE_EXPORT_GIF = "gif";
	public static final String SWITCH_IMAGE_BASE64_BASIC = "i64";
	public static final String SWITCH_IMAGE_BASE64_MIME = "i64mime";
	public static final String SWITCH_IMAGE_EXPORT_IRD_FORMAT = "ird";
	public static final String SWITCH_MULTIPAGE_TIFF_EXPORT = "mptiff";
	public static final String SWITCH_MULTIPAGE_IRD_EXPORT = "mpird";
	public static final String SWITCH_IMAGES_PER_OUTPUT_FOLDER = "ipof";
	public static final String SWITCH_XML_INCLUDE_EMPTY_FIELDS = "ef";
	public static final String SWITCH_DECIMAL_POINTS = "dp";
	public static final String[] EXPORT_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXTENSION_INPUT, SWITCH_SKIP_INTERVAL, SWITCH_RECORD_TYPES,
			SWITCH_ABORT_WHEN_EMPTY, SWITCH_ITEM_EXPORT, SWITCH_ITEM_EXPORT_WITH_COLUMN_HEADINGS,
			SWITCH_GROUP_EXPORT, SWITCH_TIFF_TAG_EXPORT, SWITCH_XML_EXPORT_FLAT,
			SWITCH_XML_EXPORT_HIERARCHICAL, SWITCH_MULTI_FILE, SWITCH_CSV_EXPORT,
			SWITCH_IMAGE_EXPORT, SWITCH_IMAGE_RELATIVE, SWITCH_IMAGE_EXPORT_TIF,
			SWITCH_IMAGE_EXPORT_JPG, SWITCH_IMAGE_EXPORT_PNG, SWITCH_IMAGE_EXPORT_GIF,
			SWITCH_IMAGE_BASE64_BASIC, SWITCH_IMAGE_BASE64_MIME, SWITCH_IMAGE_EXPORT_IRD_FORMAT,
			SWITCH_MULTIPAGE_TIFF_EXPORT, SWITCH_MULTIPAGE_IRD_EXPORT,
			SWITCH_IMAGES_PER_OUTPUT_FOLDER, SWITCH_XML_INCLUDE_EMPTY_FIELDS, SWITCH_DECIMAL_POINTS,
			SWITCH_AS_IS };

	/*
	 * ExportCsv switches. In support of automated operations, our design is to embed the majority
	 * of ExportCsv options within this xml definition. This allows export-csv to be invoked with
	 * just the format reference, with all parameters then applied appropriately. This approach is
	 * very different from the standard export command, but it is also a major differentiation.
	 */
	public static final String SWITCH_EXPORT_CONTROLS = "xctl";
	public static final String SWITCH_EXPORT_FORMAT = "xfmt";
	public static final String[] EXPORT_CSV_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXPORT_CONTROLS, SWITCH_EXPORT_FORMAT };

	/*
	 * Validate switches.
	 */
	public static final String[] VALIDATE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS };

	/*
	 * Scrub switches.
	 */
	public static final String[] SCRUB_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXTENSION_OUTPUT };

	/*
	 * Make switches.
	 */
	public static final String SWITCH_MAKE_REFORMATTER = "reformatter";
	public static final String SWITCH_MAKE_GENERATOR = "generator";
	public static final String[] MAKE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_MAKE_REFORMATTER, SWITCH_MAKE_GENERATOR, SWITCH_WRITE_JSON_TOTALS,
			SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS };

	/*
	 * Merge switches.
	 */
	public static final String SWITCH_T99_MISSING = "t99m";
	public static final String SWITCH_MERGE_BY_BUNDLE = "mrgb";
	public static final String SWITCH_MODIFY_BUNDLES = "modb";
	public static final String SWITCH_INCLUDE_SUBFOLDERS = "sf";
	public static final String SWITCH_SORT_DESCENDING = "sd";
	public static final String SWITCH_GROUP_BY_ITEM_COUNT = "gbic";
	public static final String SWITCH_DO_NOT_RENAME = "dnr";
	public static final String SWITCH_UPDATE_TIMESTAMP = "utsf";
	public static final String SWITCH_MAXIMUM_FILE_SIZE = "max";
	public static final String SWITCH_EXTENSION_RENAME = "extr";
	public static final String SWITCH_EXTENSION_FAILED = "extf";
	public static final String[] MERGE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXTENSION_INPUT, SWITCH_EXTENSION_RENAME, SWITCH_EXTENSION_FAILED,
			SWITCH_T99_MISSING, SWITCH_MERGE_BY_BUNDLE, SWITCH_MODIFY_BUNDLES,
			SWITCH_INCLUDE_SUBFOLDERS, SWITCH_SORT_DESCENDING, SWITCH_GROUP_BY_ITEM_COUNT,
			SWITCH_SKIP_INTERVAL, SWITCH_DO_NOT_RENAME, SWITCH_UPDATE_TIMESTAMP,
			SWITCH_MAXIMUM_FILE_SIZE, SWITCH_CREDITS_ADD_TO_ITEM_COUNT,
			SWITCH_CREDITS_ADD_TO_ITEM_AMOUNT, SWITCH_CREDITS_ADD_TO_IMAGE_COUNT };

	/*
	 * Update switches.
	 */
	public static final String[] UPDATE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXTENSION_OUTPUT };

	/*
	 * Split switches.
	 */
	public static final String[] SPLIT_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS, };

	/*
	 * Compare switches.
	 */
	public static final String SWITCH_VERBOSE = "v";
	public static final String SWITCH_MASK = "mask";
	public static final String SWITCH_DELETE = "delete";
	public static final String[] COMPARE_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_WRITE_JSON_TOTALS, SWITCH_WRITE_XML_TOTALS, SWITCH_WRITE_TEXT_TOTALS,
			SWITCH_EXCLUDE, SWITCH_DELETE, SWITCH_VERBOSE, SWITCH_MASK };

	/*
	 * Image pull switches.
	 */
	public static final String SWITCH_APPEND_TIMESTAMP_TO_IMAGE_FOLDER_NAME = "ix";
	public static final String SWITCH_CLEAR_IMAGE_FOLDER = "ic";
	public static final String SWITCH_DO_NOT_ABORT_WHEN_IMAGE_FOLDER_NOT_EMPTY = "ia";
	public static final String SWITCH_INCLUDE_61_62_CREDITS = "cr";
	public static final String SWITCH_PULL_BACK_SIDE_IMAGES = "ib";
	public static final String[] IMAGE_PULL_SWITCHES = { SWITCH_CONFIG, SWITCH_LOGGING,
			SWITCH_APPEND_TIMESTAMP_TO_IMAGE_FOLDER_NAME, SWITCH_CLEAR_IMAGE_FOLDER,
			SWITCH_DO_NOT_ABORT_WHEN_IMAGE_FOLDER_NOT_EMPTY, SWITCH_INCLUDE_61_62_CREDITS,
			SWITCH_PULL_BACK_SIDE_IMAGES, SWITCH_THREADS };

	/*
	 * Functional switches by command.
	 */
	private static final Map<String, String[]> FUNCTIONAL_SWITCHES = new HashMap<>();
	static {
		FUNCTIONAL_SWITCHES.put(FUNCTION_WRITE, WRITE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_DRAW, DRAW_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_TRANSLATE, TRANSLATE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_IMPORT, IMPORT_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_EXPORT, EXPORT_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_EXPORT_CSV, EXPORT_CSV_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_VALIDATE, VALIDATE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_SCRUB, SCRUB_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_MAKE, MAKE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_MERGE, MERGE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_UPDATE, UPDATE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_SPLIT, SPLIT_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_COMPARE, COMPARE_SWITCHES);
		FUNCTIONAL_SWITCHES.put(FUNCTION_IMAGE_PULL, IMAGE_PULL_SWITCHES);
	}

	/*
	 * Required files by function.
	 */
	public static final String[] INPUT_FILE_REQUIRED = new String[] { FUNCTION_WRITE, FUNCTION_DRAW,
			FUNCTION_TRANSLATE, FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_EXPORT_CSV,
			FUNCTION_VALIDATE, FUNCTION_SCRUB, FUNCTION_MAKE, FUNCTION_MERGE, FUNCTION_UPDATE,
			FUNCTION_SPLIT, FUNCTION_COMPARE, FUNCTION_IMAGE_PULL };

	public static final String[] SECONDARY_FILE_REQUIRED = new String[] { FUNCTION_SCRUB,
			FUNCTION_UPDATE, FUNCTION_SPLIT, FUNCTION_COMPARE };

	public static final String[] OUTPUT_FILE_REQUIRED = new String[] { FUNCTION_MERGE };

	public static final String[] RESULTS_FILE_REQUIRED = new String[] {};

	public static final String[] IMAGE_FOLDER_REQUIRED = new String[] {};

	/*
	 * Allowed files by function.
	 */
	public static final String[] INPUT_FILE_ALLOWED = new String[] { FUNCTION_WRITE, FUNCTION_DRAW,
			FUNCTION_TRANSLATE, FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_EXPORT_CSV,
			FUNCTION_VALIDATE, FUNCTION_SCRUB, FUNCTION_MAKE, FUNCTION_MERGE, FUNCTION_UPDATE,
			FUNCTION_SPLIT, FUNCTION_COMPARE, FUNCTION_IMAGE_PULL };

	public static final String[] SECONDARY_FILE_ALLOWED = new String[] { FUNCTION_WRITE,
			FUNCTION_SCRUB, FUNCTION_MAKE, FUNCTION_UPDATE, FUNCTION_EXPORT_CSV, FUNCTION_SPLIT,
			FUNCTION_COMPARE, FUNCTION_IMAGE_PULL };

	public static final String[] OUTPUT_FILE_ALLOWED = new String[] { FUNCTION_WRITE,
			FUNCTION_TRANSLATE, FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_EXPORT_CSV,
			FUNCTION_VALIDATE, FUNCTION_SCRUB, FUNCTION_MAKE, FUNCTION_MERGE, FUNCTION_UPDATE,
			FUNCTION_SPLIT, FUNCTION_COMPARE };

	public static final String[] RESULTS_FILE_ALLOWED = new String[] { FUNCTION_DRAW,
			FUNCTION_SCRUB, FUNCTION_COMPARE, FUNCTION_UPDATE, FUNCTION_SPLIT,
			FUNCTION_IMAGE_PULL };

	public static final String[] IMAGE_FOLDER_ALLOWED = new String[] { FUNCTION_TRANSLATE,
			FUNCTION_IMPORT, FUNCTION_EXPORT, FUNCTION_IMAGE_PULL };

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilWorkUnit.class);

	/**
	 * X9UtilWorkUnit Constructor. Our default is to run with standard logging, where all command
	 * line errors are included in the log. This can be instead be disabled which is essentially a
	 * quiet mode. This limited logging is used by X9UtilConsoleUx to validate command line
	 * parameters, where this logging is inappropriate.
	 * 
	 * @param work_UnitList
	 *            work unit this for this runtime execution
	 * @param command_Line
	 *            command line for this work unit
	 * @throws FileNotFoundException
	 */
	public X9UtilWorkUnit(final X9UtilWorkUnitList work_UnitList, final X9CommandLine command_Line)
			throws FileNotFoundException {
		this(work_UnitList, command_Line, PARSE_LOGGING_ENABLED);
	}

	/**
	 * X9UtilWorkUnit Constructor.
	 * 
	 * @param work_UnitList
	 *            work unit this for this runtime execution
	 * @param command_Line
	 *            command line for this work unit
	 * @param is_ParseLoggingEnabled
	 *            true if parse logging is enabled (we are not in quite mode)
	 * @throws FileNotFoundException
	 */
	public X9UtilWorkUnit(final X9UtilWorkUnitList work_UnitList, final X9CommandLine command_Line,
			final boolean is_ParseLoggingEnabled) throws FileNotFoundException {
		/*
		 * Set attributes which are all final.
		 */
		workUnitList = work_UnitList;
		x9commandLine = command_Line;
		isParseLoggingEnabled = is_ParseLoggingEnabled;

		/*
		 * Determine if the command line references a saved xml work unit that was previously
		 * constructed by the utilities console that runs within X9Assist.
		 */
		final File[] files;
		if (isCommandSwitchSet(SWITCH_WORK_UNIT)) {
			/*
			 * When that work unit switch is set, it points us to an xml file that has the full
			 * command line as it was originally executed. Our task here is then relatively easy,
			 * since we only need to load that xml file and parse the command line as it was
			 * previously saved for us. Note that the command line arguments are separated by the
			 * pipe character since there are most probably embedded blanks within the various input
			 * and output file names.
			 */
			final X9UtilWorkUnitXml workUnitXml = new X9UtilWorkUnitXml();
			workUnitXml.readExternalXmlFile(getCommandLineFile(SWITCH_WORK_UNIT));
			final X9UtilWorkUnitAttr workUnitAttr = workUnitXml.getAttr();
			files = x9commandLine.parse(workUnitAttr.commandLineAsExecuted);
		} else {
			/*
			 * Otherwise get the files as assigned on this command line.
			 */
			files = x9commandLine.getCommandFiles();
		}

		/*
		 * Parse and assign work unit files which are associated with this work unit.
		 */
		parseAndAssign(files);
	}

	/**
	 * Determine if command line parse was successful.
	 * 
	 * @return true or false
	 */
	public boolean isParseAndAssignSuccessful() {
		return isValidWorkUnit;
	}

	/**
	 * Parse and assign work unit files from listed inputs on the command line. This assignment is
	 * based on each function subject to the number of files that are present, and will assign
	 * defaults when possible. We return true when all necessary files have been provided.
	 * 
	 * @param files
	 *            command line files
	 * @return true when the assignments represent a valid unit of work
	 * @throws FileNotFoundException
	 */
	public boolean parseAndAssign(final File... files) throws FileNotFoundException {
		/*
		 * Get the default output file extension and default to "new".
		 */
		final String ext = getCommandSwitchValue(SWITCH_EXTENSION_OUTPUT);
		final String outExtension = StringUtils.isNotBlank(ext) ? ext : "new";

		/*
		 * Ensure that a valid function switch is present and set the function name.
		 */
		if (!isValidFunctionSwitchPresent()) {
			markInvalidAndLogAllAvailableFunctions("invalid switches present");
			return false;
		}

		/*
		 * Assign files based on the function being performed.
		 */
		inputFile = null;
		isValidWorkUnit = true;
		if (StringUtils.equals(utilFunctionName, FUNCTION_CONSOLE)) {
			if (files.length > 0) {
				markInvalidAndLogAllAvailableFunctions("files not allowed");
			}
		} else if (files.length == 0) {
			markInvalidAndLogAllAvailableFunctions("files are required");
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_TRANSLATE)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -translate <inputFile>
					 */
					final String folder = X9FileUtils.getFolderName(inputFile);
					final String baseName = FilenameUtils.getBaseName(inputFile.toString());
					outputFile = new File(folder, baseName + "." + X9C.CSV);
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 2: {
					/*
					 * -translate <inputFile> <outputFile>
					 */
					outputFile = files[1];
					final String folder = X9FileUtils.getFolderName(outputFile);
					final String baseName = FilenameUtils.getBaseName(outputFile.toString());
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 3: {
					/*
					 * -translate <inputFile> <outputFile> <imageFolder>
					 */
					outputFile = files[1];
					imageFolder = files[2];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-translate inputFile csvOutputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_WRITE)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -write <inputFile>
					 */
					outputFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.X937);
					break;
				}
				case 2: {
					/*
					 * -write <inputFile> <outputFile>
					 */
					outputFile = files[1];
					break;
				}
				case 3: {
					/*
					 * -write <inputFile> <headerXml> <outputFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-write csvInputFile headerXmlFile outputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_DRAW)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -draw <inputFile>
					 */
					resultsFile = null;
					break;
				}
				case 2: {
					/*
					 * -draw <inputFile> <resultsFile>
					 */
					resultsFile = files[1];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-draw inputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_IMPORT)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -import <inputFile>
					 */
					outputFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.X937);
					break;
				}
				case 2: {
					/*
					 * -import <inputFile> <outputFile>
					 */
					outputFile = files[1];
					break;
				}
				case 3: {
					/*
					 * -import <inputFile> <outputFile> <imageFolder>
					 */
					outputFile = files[1];
					imageFolder = files[2];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-import csvInputFile outputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_EXPORT)) {
			inputFile = files[0];
			final boolean isExportToXml = isCommandSwitchSet(SWITCH_XML_EXPORT_FLAT)
					|| isCommandSwitchSet(SWITCH_XML_EXPORT_HIERARCHICAL);
			final String extension = isExportToXml ? X9C.XML : X9C.CSV;
			switch (files.length) {
				case 1: {
					/*
					 * -export <inputFile>
					 */
					final String folder = X9FileUtils.getFolderName(inputFile);
					final String baseName = FilenameUtils.getBaseName(inputFile.toString());
					outputFile = new File(folder, baseName + "." + extension);
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 2: {
					/*
					 * -export <inputFile> <outputFile>
					 */
					outputFile = files[1];
					final String folder = X9FileUtils.getFolderName(outputFile);
					final String baseName = FilenameUtils.getBaseName(outputFile.toString());
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 3: {
					/*
					 * -export <inputFile> <outputFile> <imageFolder>
					 */
					outputFile = files[1];
					imageFolder = files[2];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-export inputFile outputFile");
				}
			}

			int imageFormatCount = 0;
			if (isCommandSwitchSet(SWITCH_IMAGE_EXPORT_TIF)) {
				imageFormatCount++;
			}
			if (isCommandSwitchSet(SWITCH_IMAGE_EXPORT_JPG)) {
				imageFormatCount++;
			}
			if (isCommandSwitchSet(SWITCH_IMAGE_EXPORT_PNG)) {
				imageFormatCount++;
			}
			if (isCommandSwitchSet(SWITCH_IMAGE_EXPORT_GIF)) {
				imageFormatCount++;
			}
			if (imageFormatCount > 1) {
				markInvalidCommandLine("multiple image formats selected");
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_EXPORT_CSV)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -exportCsv <inputFile> [ file names are in the xml definition ]
					 */
					break;
				}
				case 2: {
					/*
					 * -export <inputFile> <outputFile> [ optional, since the output file name can
					 * be provided by the export format xml definition ]
					 */
					outputFile = files[1];
					final String folder = X9FileUtils.getFolderName(outputFile);
					final String baseName = FilenameUtils.getBaseName(outputFile.toString());
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 3: {
					/*
					 * -export <inputFile> <outputFile> <imageFolder> [ optional, since the output
					 * image folder name can be provided by the export format xml definition ]
					 */
					outputFile = files[1];
					imageFolder = files[2];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-exportCsv inputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_VALIDATE)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -validate <inputFile>
					 */
					resultsFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.CSV);
					break;
				}
				case 2: {
					/*
					 * -validate <inputFile> <resultsFile>
					 */
					resultsFile = files[1];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-validate inputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_SCRUB)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -scrub <inputFile> <parmFile> [ no results file will be created ]
					 */
					secondaryFile = files[1];
					final String baseName = FilenameUtils.removeExtension(inputFile.toString());
					outputFile = new File(baseName + "." + outExtension);
					break;
				}
				case 3: {
					/*
					 * -scrub <inputFile> <parmFile> <outputFile> [ no results file ]
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					break;
				}
				case 4: {
					/*
					 * -scrub <inputFile> <parmFile> <outputFile> <resultsFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = files[3];
					break;
				}
				default: {
					markInvalidAndLogSyntax(
							"-scrub inputFile parametersFile outputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_MAKE)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -make <inputFile> <outputFile>
					 */
					outputFile = files[1];
					break;
				}
				case 3: {
					/*
					 * -make <inputFile> <routingListFile> <outputFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-make inputFile routingListFile outputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_MERGE)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -merge <inputFolder> <outputFolder>
					 */
					outputFile = files[1];
					break;
				}
				default: {
					markInvalidAndLogSyntax("-merge inputFolder outputFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_UPDATE)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -update <inputFile> <xmlParameters>
					 */
					secondaryFile = files[1];
					final String baseName = FilenameUtils.removeExtension(inputFile.toString());
					outputFile = new File(baseName + "." + outExtension);
					resultsFile = new File(baseName + "." + X9C.CSV);
					break;
				}
				case 3: {
					/*
					 * -update <inputFile> <xmlParameters> <outputFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					final String baseName = FilenameUtils.removeExtension(outputFile.toString());
					resultsFile = new File(baseName + "." + X9C.CSV);
					break;
				}
				case 4: {
					/*
					 * -update <inputFile> <xmlParameters> <outputFile> <resultsFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = files[3];
					break;
				}
				default: {
					markInvalidAndLogSyntax(
							"-update inputFile parametersFile outputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_SPLIT)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -split <inputFile> <xmlParameters>
					 */
					secondaryFile = files[1];
					outputFile = new File(X9FileUtils.getFolderName(inputFile));
					resultsFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.CSV);
					break;
				}
				case 3: {
					/*
					 * -update <inputFile> <xmlParameters> <outputFolder>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = new File(
							FilenameUtils.removeExtension(inputFile.toString()) + "." + X9C.CSV);
					break;
				}
				case 4: {
					/*
					 * -update <inputFile> <xmlParameters> <outputFolder> <resultsFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = files[3];
					break;
				}
				default: {
					markInvalidAndLogSyntax(
							"-split inputFile parametersFile x9OutputFolder resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_COMPARE)) {
			inputFile = files[0];
			switch (files.length) {
				case 2: {
					/*
					 * -compare <inputFile1> <inputFile2>
					 */
					secondaryFile = files[1];
					final String folder = X9FileUtils.getFolderName(inputFile);
					final String baseName = FilenameUtils.getBaseName(inputFile.toString());
					outputFile = new File(folder, baseName + "_output." + X9C.TXT);
					resultsFile = new File(folder, baseName + "_output." + X9C.CSV);
					break;
				}
				case 3: {
					/*
					 * -compare <inputFile1> <inputFile2> <resultsFile>
					 */
					secondaryFile = files[1];
					resultsFile = files[2];
					final String folder = X9FileUtils.getFolderName(resultsFile);
					final String baseName = FilenameUtils.getBaseName(resultsFile.toString());
					outputFile = new File(folder, baseName + "." + X9C.CSV);
					break;
				}
				case 4: {
					/*
					 * -compare <inputFile1> <inputFile2> <outputFile> <resultsFile>
					 */
					secondaryFile = files[1];
					outputFile = files[2];
					resultsFile = files[3];
					break;
				}
				default: {
					markInvalidAndLogSyntax(
							"-compare inputFile1 inputFile2 outputFile resultsFile");
				}
			}
		} else if (StringUtils.equals(utilFunctionName, FUNCTION_IMAGE_PULL)) {
			inputFile = files[0];
			switch (files.length) {
				case 1: {
					/*
					 * -imagePull <inputFile>
					 */
					final String folder = X9FileUtils.getFolderName(inputFile);
					final String baseName = FilenameUtils.getBaseName(inputFile.toString());
					resultsFile = new File(folder, baseName + "_RESULTS." + X9C.CSV);
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 2: {
					/*
					 * -imagePull <inputFile> <resultsFile>
					 */
					resultsFile = files[1];
					final String folder = X9FileUtils.getFolderName(resultsFile);
					final String baseName = FilenameUtils.getBaseName(resultsFile.toString());
					imageFolder = new File(folder, baseName + "_IMAGES");
					break;
				}
				case 3: {
					/*
					 * -imagePull <inputFile> <resultsFile> <imageFolder>
					 */
					resultsFile = files[1];
					imageFolder = files[2];
					break;
				}
				case 4: {
					/*
					 * -imagePull <inputFile> <csvParameters> <resultsFile> <imageFolder>
					 */
					secondaryFile = files[1];
					resultsFile = files[2];
					imageFolder = files[3];
					break;
				}
				default: {
					markInvalidAndLogSyntax(
							"-imagePull inputFile parametersFile resultsFile imageFolder");
				}
			}
		} else {
			markInvalidAndLogAllAvailableFunctions("unknown function");
		}

		/*
		 * Get functional switches for the current command.
		 */
		final String[] functionalSwitches = getFunctionalSwitches();

		/*
		 * Log any invalid command line switches that were identified.
		 */
		final String invalidSwitches = checkForValidSwitches(functionalSwitches);
		if (StringUtils.isNotBlank(invalidSwitches)) {
			if (isParseLoggingEnabled) {
				LOGGER.error("switch(es) are invalid({}) for selected function({})",
						invalidSwitches, utilFunctionName);
			}
		}

		/*
		 * Log all command lines switches which are actually present with their values.
		 */
		if (isParseLoggingEnabled) {
			logEnabledSwitches(functionalSwitches);
		}

		/*
		 * Log all assigned input and output files.
		 */
		if (isCommandSwitchSet(SWITCH_LOGGING) && isParseLoggingEnabled) {
			if (inputFile != null) LOGGER.info("input file({})", inputFile);
			if (secondaryFile != null) LOGGER.info("secondary file({})", secondaryFile);
			if (outputFile != null) LOGGER.info("output file({})", outputFile);
			if (resultsFile != null) LOGGER.info("results file({})", resultsFile);
			if (imageFolder != null) LOGGER.info("image folder({})", imageFolder);
		}

		/*
		 * Ensure that our input file/folder exists which is mandatory for all functions. This is a
		 * generic existence check; we are not checking specifically for file or directory here.
		 * This is important for several reasons. First is that we have functions that accept one or
		 * the other. Second is that when using batch mode, a function that would normally take an
		 * input file will actually be specified as a wild card pattern.
		 */
		if (isValidWorkUnit && inputFile != null) {
			if (isCommandSwitchSet(SWITCH_BATCH)) {
				/*
				 * If this is batch, then our input is a directory and not a file. We must get just
				 * the path while ignoring the attached wild card pattern.
				 */
				final String filePath = FilenameUtils.getFullPath(inputFile.toString());
				final File directory = new File(filePath);
				if (!X9FileUtils.existsWithPathTracing(directory)) {
					throw new FileNotFoundException("input path notFound(" + filePath + ")");
				}
				if (!directory.isDirectory()) {
					throw new FileNotFoundException("input not a directory(" + filePath + ")");
				}
			} else {
				if (!X9FileUtils.existsWithPathTracing(inputFile)) {
					throw new FileNotFoundException("input file notFound(" + inputFile + ")");
				}
			}
		}

		/*
		 * Returns true when the assignments represent a valid unit of work.
		 */
		return isValidWorkUnit;
	}

	/**
	 * Check if switches present on the command line are valid for the current function.
	 * 
	 * @param validFunctionalSwitches
	 *            array of valid switch settings for the active function
	 * @return null when all switches are valid otherwise string of invalid switch names
	 */
	public String checkForValidSwitches(final String[] validFunctionalSwitches) {
		/*
		 * Validate actual switches which are present on the command line.
		 */
		String invalidSwitches = "";
		final String[] commandSwitches = x9commandLine.getCommandSwitches();
		if (commandSwitches != null && commandSwitches.length > 0) {
			/*
			 * Build an array of actual command line switch values.
			 */
			final int switchCount = commandSwitches.length;
			final String[] switchesPresent = new String[switchCount];
			for (int i = 0; i < switchCount; i++) {
				/*
				 * Remove the leading dash (it will always be present). We then also extract the
				 * switch name for those switches that have attached values. In that case, the
				 * format is -switch:value so we just extract the actual switch name that appears
				 * before the ":". The remainder of the switch text (the value) can be ignored.
				 */
				switchesPresent[i] = StringUtils
						.substringBefore(StringUtils.removeStart(commandSwitches[i], "-"), ":");
			}

			/*
			 * Determine if command line switches that are physically present on the command line
			 * are contextually valid based on the actual command being executed.
			 */
			for (final String switchName : switchesPresent) {
				if (StringUtils.equalsIgnoreCase(switchName, utilFunctionName)) {
					/*
					 * Identify and accept the actual function name itself. For example, UTIL writer
					 * will always have the "-write" switch present. We accept the batch and threads
					 * parameter for all commands, in support of batch operations.
					 */
				} else if (StringUtils.equalsAnyIgnoreCase(switchName, X9UtilBatch.DEBUG_SWITCH,
						X9UtilBatch.CONSOLE_ON_SWITCH, X9UtilBatch.CONSOLE_OFF_SWITCH,
						X9UtilBatch.SWITCH_LOG_FOLDER, X9UtilBatch.USER_LOGGER_LINE, SWITCH_BATCH,
						SWITCH_THREADS)) {
					/*
					 * Accept certain global switches which apply to all commands (debug, logging
					 * On/Off, console On/Off, etc); just accept since these are handled elsewhere.
					 */
				} else {
					/*
					 * Determine if this switch is valid for the current function.
					 */
					boolean isFound = false;
					for (final String functionalSwitch : validFunctionalSwitches) {
						if (StringUtils.equalsAnyIgnoreCase(switchName, functionalSwitch)) {
							isFound = true;
							break;
						}
					}

					/*
					 * Build a string of invalid switches for this command.
					 */
					if (!isFound) {
						invalidSwitches = invalidSwitches
								+ (StringUtils.isNotBlank(invalidSwitches) ? ";" : "") + switchName;
					}
				}
			}

		}

		/*
		 * Log additional research information when we have identified invalid switches.
		 */
		if (StringUtils.isNotBlank(invalidSwitches) && isParseLoggingEnabled) {
			LOGGER.info("invalidSwitches({}) commandSwitches({}) validFunctionalSwitches({})",
					invalidSwitches, StringUtils.join(commandSwitches, ";"),
					StringUtils.join(validFunctionalSwitches, ";"));
		}

		/*
		 * Return invalid switch names and null when all switches are valid.
		 */
		return StringUtils.isBlank(invalidSwitches) ? null : invalidSwitches;
	}

	/**
	 * Log all functional switches which are currently enabled.
	 *
	 * @param functionalSwitches
	 *            array of valid switch settings for the active function
	 */
	public void logEnabledSwitches(final String[] functionalSwitches) {
		for (final String switchId : functionalSwitches) {
			if (isCommandSwitchSet(switchId)) {
				final String switchValue = getCommandSwitchValue(switchId);
				if (StringUtils.isBlank(switchValue)) {
					LOGGER.info("switch({}) enabled", switchId);
				} else {
					LOGGER.info("switch({}) enabled value({})", switchId, switchValue);
				}
			}
		}
	}

	/**
	 * Get the functional switches for this function.
	 *
	 * @return array of functional switches
	 */
	public String[] getFunctionalSwitches() {
		final String[] functionalSwitches = FUNCTIONAL_SWITCHES.get(utilFunctionName);
		return functionalSwitches == null ? new String[] {} : functionalSwitches;
	}

	/**
	 * Log command usage as a user help facility.
	 */
	public void logCommandUsage() {
		/*
		 * Format command line options by function.
		 */
		LOGGER.info(HELP_BREAK_LINE);
		LOGGER.info("command usage:");
		if (isCommandSwitchSet(SWITCH_BATCH)) {
			LOGGER.info("x9util -batch [ model command line to be invoked for each file ]");
			LOGGER.info("[-threads:n] [-aoe:n]");
			LOGGER.info("-threads        number of background threads to be initiated");
			LOGGER.info("-aoe            number of aborted tasks before work units are flushed");
		} else if (isCommandSwitchSet(SWITCH_SCRIPT)) {
			LOGGER.info("x9util -script scriptFile");
			LOGGER.info("scriptFile      script file that contains command lines to be executed");
		} else if (isCommandSwitchSet(FUNCTION_WRITE)) {
			LOGGER.info("x9util -write inputFile.csv [headerXml] [outputFile.x9] ");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("writes a new x9 output file from the provided input csv file");
			LOGGER.info("all image filenames must be provided in absolute format");
			LOGGER.info("headerXml       headerXml file which defines output x9 parameters; "
					+ "must be first csv row when omitted");
			LOGGER.info("outputFile      defaults to inputFile.x9 when not specified");
			LOGGER.info("-enp            overrides the need for the end statement on the items "
					+ "csv file (which is otherwise mandatory");
			LOGGER.info("-dnr            do not rewrite output file when it already exists");
			LOGGER.info("-dts            append date-time stamp to output file");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_DRAW)) {
			LOGGER.info("x9util -draw inputFile.csv [results.csv] [-dpi:] [-l]");
			LOGGER.info("draws images and writes them to either single or multi-page tiff files");
			LOGGER.info("results.csv contains an optional output csv with finalized file names");
			LOGGER.info("-dpi:nnn        sets the drawing dpi as either 200 or 240");
			LOGGER.info("-l              lists all records to the log");
		} else if (isCommandSwitchSet(FUNCTION_TRANSLATE)) {
			LOGGER.info("x9util -translate inputFile.x9 [outputFile.csv] [imageFolder]");
			LOGGER.info("[-config:] [-a] [-i] [-l] [-j] [-x] [-t]");
			LOGGER.info("reads an x9 input file to create an output csv and optional image folder");
			LOGGER.info("outputFile      defaults to inputFile.csv when not specified");
			LOGGER.info("imageFolder     defaults to outputFile_IMAGES when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-a              includes addenda in the output csv file");
			LOGGER.info("-noHeaderXml    excludes the headerXml line from output csv file");
			LOGGER.info("-noCredits      excludes the various credit types from output csv file");
			LOGGER.info("-i              indicates that images should be exported to the "
					+ "imageFolder");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_IMPORT)) {
			LOGGER.info("x9util -import inputFile.csv [outputFile.x9] [imageFolder]");
			LOGGER.info("[-config:] [-dpi:] [-l] [-j] [-x] [-t]");
			LOGGER.info("imports an input csv file and optional imageFolder to create an "
					+ "output x9 file");
			LOGGER.info("all image filenames must be provided in absolute format");
			LOGGER.info("outputFile      defaults to inputFile.x9 when not specified");
			LOGGER.info("imageFolder     defaults to outputFile_IMAGES when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-dpi:nnn        sets the drawing dpi as either 200 or 240");
			LOGGER.info("-r              indicates that trailer record totals should be "
					+ "automatically repaired");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_EXPORT)) {
			LOGGER.info("x9util -export inputFile.x9 [outputFile.csv] [imageFolder]");
			LOGGER.info("[-config:] [-xc] [-xf] [-xg] [-xm] [-i] [-ir] [-tif] [-jpg] [-png] "
					+ "[-gif] [-i64] [-i64mime] [-mp] [-xt] [-xml] [-ef] [-l] [-j] [-x] [-t]");
			LOGGER.info("exports an x9 input file to create output csv/xml with optional images");
			LOGGER.info("outputFile      defaults to inputFile.csv when not specified");
			LOGGER.info("imageFolder     defaults to outputFile_IMAGES when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-xc             exports to csv as native x9 record/field format");
			LOGGER.info("-xf             exports to csv as parsed items in fixed column format");
			LOGGER.info("-xfc            exports to csv in fixed column format with headings");
			LOGGER.info("-xg             exports to csv as record groups in variable columns");
			LOGGER.info("-xm             folder level (multiple inputs written to one output");
			LOGGER.info("-i              exports images to the image folder with absolute "
					+ "file names inserted into the image data field");
			LOGGER.info("-ir             exports images to the image folder with relative "
					+ "file names inserted into the image data field");
			LOGGER.info("-tif            exports images in tif format");
			LOGGER.info("-jpg            exports images in jpg format");
			LOGGER.info("-png            exports images in png format");
			LOGGER.info("-gif            exports images in gif format");
			LOGGER.info("-i64            inserts images directly into the image data field as "
					+ "base64-basic strings during csv/xml export");
			LOGGER.info("-i64mime        inserts images directly into the image data field as "
					+ "base64-mime strings during csv/xml export");
			LOGGER.info("-mp             creates and exports a multi-page tiff to the image "
					+ "folder from the front+back tiff images");
			LOGGER.info("-xt             exports image tiff tags to csv");
			LOGGER.info("-xml            exports to xml (instead of our default which otherwise "
					+ "exports to csv)");
			LOGGER.info("-ef             includes fields which contain blanks data during "
					+ "xml export");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_EXPORT_CSV)) {
			LOGGER.info(
					"x9util -exportCsv inputFile.x9 [outputFile.csv] [imageFolder] -xctl: -xfmt: -l");
			LOGGER.info("exports an x9 input file to a specific csv format with optional images");
			LOGGER.info("-xctl:          defines the export control xml file to be referenced");
			LOGGER.info("-xfmt:          defines the export format definition to be utilized");
		} else if (isCommandSwitchSet(FUNCTION_VALIDATE)) {
			LOGGER.info("x9util -validate inputFile.x9 [outputFile.csv]");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("validates an x9 input file and creates an output csv error file");
			LOGGER.info("final exitStatus is set based on the types of errors which were "
					+ "identified during validation");
			LOGGER.info("exitStatus which is negative implies the validation was aborted");
			LOGGER.info("exitStatus = 0  (no errors found)");
			LOGGER.info("exitStatus = 1  (informational error messages issued)");
			LOGGER.info("exitStatus = 2  (warning error messages issued)");
			LOGGER.info("exitStatus = 3  (error error messages issued)");
			LOGGER.info("exitStatus = 4  (severe error messages issued)");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_SCRUB)) {
			LOGGER.info("x9util -scrub inputFile.x9 parameters.xml [outputFile.x9] [results.csv]");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("scrubs an x9 input file and creates a sanitized output x9 file");
			LOGGER.info("results.csv contains a summary of the fields which have been scrubbed");
			LOGGER.info("parameters      mandatory and defines the scrub actions to be applied");
			LOGGER.info("outputFile      defaults to inputFile.new when not specified");
			LOGGER.info("results         defaults to inputFile.csv when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-exto			 output file extension which otherwise defaults to 'new'");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_MAKE)) {
			LOGGER.info("x9util -make inputFile.x9 [routingList.csv] outputFile.x9");
			LOGGER.info("[-config:] [-dpi:] [-l] [-j] [-x] [-t]");
			LOGGER.info("make/generate an x9 file using a provided reformatter and generator");
			LOGGER.info("routingList      optional and defines the routing list to be utilized");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-dpi:nnn        sets the drawing dpi as either 200 or 240");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_MERGE)) {
			LOGGER.info("x9util -merge inputFolder outputFile.x9");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("merges the contents of the specified folder to a single x9 output file");
			LOGGER.info("inputFolder     required and contains input files to be merged");
			LOGGER.info("outputFile      required and will contain all items from all input files");
			LOGGER.info("-exti:x1|x2|... list of one or more input file extensions");
			LOGGER.info("-extr:merged    extension used to rename merged files on completion");
			LOGGER.info("-extf:failed    extension used to rename failed files on completion");
			LOGGER.info("-max:nnnn       maximum output size as either mb/kb or item count");
			LOGGER.info("-creditsAddToCount         add credits to trailer item counts");
			LOGGER.info("-creditsAddToAmount        add credits to trailer item amounts");
			LOGGER.info("-creditsExcludeFromImages  exclude credits from trailer image counts");
			LOGGER.info("-sf             include subfolders within the input folder");
			LOGGER.info("-sd             sort selected files descending by their attributes");
			LOGGER.info("-gbic           group files by item count for packaging");
			LOGGER.info("-t99            t99 trailers must be present to select an input file");
			LOGGER.info("-mrgb           indicates that merge is at the bundle level");
			LOGGER.info("-modb           bundle RTs should be modified from cash letter headers");
			LOGGER.info("-skpi           skip internal in seconds for transmissions in progress");
			LOGGER.info("-utsf:file.csv  indicates the time stamp file should be created");
			LOGGER.info("-dnr            do not rename merged files (ONLY used for testing");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_UPDATE)) {
			LOGGER.info("x9util -update inputFile.x9 parameters.xml [outputFile.x9] [results.csv]");
			LOGGER.info("[-config:] [-exto:] [-l] [-j] [-x] [-t]");
			LOGGER.info("updates an existing x9 input file by searching for field values and then "
					+ "creating an output x9 file with replacement values per the parameters file");
			LOGGER.info("results.csv contains a list of the before and after values for each field "
					+ " updated by this operation");
			LOGGER.info("parameters      mandatory and defines values for find/replace actions");
			LOGGER.info("outputFile      defaults to inputFile.new when not specified");
			LOGGER.info("results         defaults to inputFile.csv when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-exto			 output file extension which otherwise defaults to 'new'");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_SPLIT)) {
			LOGGER.info("x9util -split inputFile.x9 parameters.xml [outputFolder] [results.csv]");
			LOGGER.info("[-config:] [-l] [-j] [-x] [-t]");
			LOGGER.info("splits an existing x9 input file by into segments on one or more input "
					+ "fields and then creating one or more output files per the parameters file");
			LOGGER.info("results.csv contains a list of the output files which have been created "
					+ "with record and item counts");
			LOGGER.info("parameters      mandatory and defines values for find/replace actions");
			LOGGER.info("outputFolder    defaults to the folder for inputFile.x9");
			LOGGER.info("results         defaults to inputFile.csv when not specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-l              lists all records to the log");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_COMPARE)) {
			LOGGER.info(
					"x9util -compare inputFile1.x9 inputFile2.x9 [outputFile.txt] [results.csv]");
			LOGGER.info("[-config:] [-j] [-x] [-t] [ex:xx.xx|xx.xx|xx.xx|...");
			LOGGER.info(
					"compares two x9 files and creates a results file of any differences found");
			LOGGER.info("-exclude:xx.xx|xx.xx|xx.xx|... field(s) to be excluded from the compare");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-j              creates json totals file in the output folder");
			LOGGER.info("-x              creates xml totals file in the output folder");
			LOGGER.info("-t              creates text totals file in the output folder");
		} else if (isCommandSwitchSet(FUNCTION_IMAGE_PULL)) {
			LOGGER.info(
					"x9util -imagePull inputFile.csv [parameters.xml] [resultsFile.csv] [imageFolder]");
			LOGGER.info("[-config:] [-cr] [-ib] [-ix] [-ic] [-ia] [-l]");
			LOGGER.info("extracts images from a provided series of x9 files based on item "
					+ "number and optional amount");
			LOGGER.info("parameters      optional and defines the fields to be written to "
					+ "results.csv; default is to write our standard list; requires all four (4) "
					+ "command line files to prevent ambiguity");
			LOGGER.info("resultsFile     defaults to inputFile_RESULTS.csv when not specified");
			LOGGER.info("imageFolder     defaults to resultsFile_IMAGES when not specified and "
					+ "can only be specified when results.csv is also specified");
			LOGGER.info("-config:        specifies the x9 configuration to be loaded");
			LOGGER.info("-cr             include credit record types 61 and 62");
			LOGGER.info("-ib             pull back side images");
			LOGGER.info("-ix             append a timestamp to the assigned image folder name");
			LOGGER.info("-ic             clear the assigned image folder");
			LOGGER.info("-ia             do not abort if the output image folder is not empty");
			LOGGER.info("-l              list record types 25/31 to the log");
		} else {
			logAllAvailableUtilityFunctions();
		}
		LOGGER.info(HELP_BREAK_LINE);
	}

	/**
	 * Get a newly allocated sdkBase for this work unit.
	 *
	 * @return newly allocated sdkBase
	 */
	public X9SdkBase getNewSdkBase() {
		/*
		 * Create an sdk instance and set image repair/resize actions. Image repair is a single
		 * action while image resize triggers both resize and repair to be set,
		 */
		final X9SdkBase sdkBase = new X9SdkBase();
		isImageResizeEnabled = isCommandSwitchSet(SWITCH_IMAGE_RESIZE_ENABLED);
		isImageRepairEnabled = isImageResizeEnabled
				|| isCommandSwitchSet(SWITCH_IMAGE_REPAIR_ENABLED);

		/*
		 * Set credit trailer action switches. These switches can be externally assigned a value of
		 * true or false, or can be allowed to default (null) to an appropriate setting for the
		 * current x9rules definition or the content of the x9 file itself (eg, x9.100-187-2016).
		 */
		isCreditsAddToItemCount = getBooleanCommandSwitchValue(SWITCH_CREDITS_ADD_TO_ITEM_COUNT);
		isCreditsAddToItemAmount = getBooleanCommandSwitchValue(SWITCH_CREDITS_ADD_TO_ITEM_AMOUNT);
		isCreditsAddToImageCount = getBooleanCommandSwitchValue(SWITCH_CREDITS_ADD_TO_IMAGE_COUNT);

		/*
		 * Set the dpi when provided on the command line. We do not need to be concerned as to
		 * whether this switch is appropriate for the current command, since we will have already
		 * verified that the switch is defined as acceptable for this function.
		 */
		if (isCommandSwitchSet(SWITCH_DPI)) {
			final String dpiString = getCommandSwitchValue(SWITCH_DPI);
			final int dpi = X9Numeric.toInt(dpiString);
			if (dpi < 0) {
				throw X9Exception.abort("dpi({}) not numeric", dpiString);
			}
			if (dpi == X9C.CHECK_IMAGE_DPI_200 || dpi == X9C.CHECK_IMAGE_DPI_240) {
				sdkBase.getDrawOptions().setDrawDpi(dpi, X9DrawOptions.DPI_PRESERVE_DISABLED);
			} else {
				throw X9Exception.abort("dpi({}) must be 200 or 240", dpiString);
			}
		}

		/*
		 * Return the created sdk base.
		 */
		return sdkBase;
	}

	/**
	 * Write user defined text directly to the log from the command line or incoming csv. This is
	 * helpful when the user has automation that matches our log against their own event log.
	 * 
	 * @param userText
	 *            user text line
	 */
	public void logUserProvidedTextLine(final String userText) {
		LOGGER.info("{} {} {}", X9UtilBatch.USER_LOGGER_PREFIX_SUFFIX, userText,
				X9UtilBatch.USER_LOGGER_PREFIX_SUFFIX);
	}

	/**
	 * Bind to the configuration identified on the command line and default to file header.
	 *
	 * @param sdkBase
	 *            sdkBase for this environment
	 */
	public void autoBindToCommandLineConfiguration(final X9SdkBase sdkBase) {
		if (bindToCommandLineConfiguration(sdkBase)) {
			X9ConfigSelector.autoBindToConfiguration(sdkBase, inputFile);
		}
	}

	/**
	 * Bind to the configuration identified on the command line with default to x937. This bind also
	 * supports an automated determination of the configuration (using X9ConfigSelector) which is
	 * based on the content of the type 01 file header. This automated bind is indicated by simply
	 * omitting the configuration name on the command line (which tells us to do the inspection).
	 *
	 * @param sdkBase
	 *            sdkBase for this environment
	 * @return true if auto bind is active
	 */
	public boolean bindToCommandLineConfiguration(final X9SdkBase sdkBase) {
		/*
		 * Set the configuration name when provided and otherwise default to x9.37. A big exception
		 * here is auto-bind, which initially defaults to x937 (in order to read the file header),
		 * and will then will be subsequently changed to a possibly more targeted configuration.
		 * This is a bit of a catch22, since the file header must be read, so we need to initially
		 * bind to something in order to read that record.
		 */
		String configName = X9.X9_37_CONFIG;
		final boolean isBindAuto = isBindConfigurationAuto();
		if (isBindAuto) {
			LOGGER.info("autoBind active");
		} else if (isCommandSwitchSet(SWITCH_CONFIG)) {
			configName = getCommandSwitchValue(SWITCH_CONFIG);
			LOGGER.info("configuration set from commandLine({})", configName);
		}

		/*
		 * Bind to the selected configuration.
		 */
		if (!sdkBase.bindConfiguration(configName)) {
			throw X9Exception.abort("bind unsuccessful({})", configName);
		}

		/*
		 * Return true when auto bind is active.
		 */
		return isBindAuto;
	}

	/**
	 * Determine if bind configuration is defaulting to auto, which directs that the configuration
	 * be automatically determined from the type 01 file header. The auto bind process can be
	 * generally applied to any of our utility functions that are file readers.
	 *
	 * @return true or false
	 */
	public boolean isBindConfigurationAuto() {
		return StringUtils.equalsAny(utilFunctionName, AUTO_BIND_FUNCTION_NAMES)
				&& !isCommandSwitchSet(SWITCH_CONFIG);
	}

	/**
	 * Get the maximum number of threads for this system.
	 *
	 * @return assigned thread count
	 */
	public int getThreadCount() {
		return getTaskThreadCount(getCommandSwitchValue(SWITCH_THREADS));
	}

	/**
	 * Get a list of files to be processed for this function. We interrogate the multi-file command
	 * line switch and return either a single file or a list of files subject to that setting. This
	 * is a convenience method since it can be used to simplify overall file list processing.
	 *
	 * @return list of files
	 */
	public List<X9File> getInputFileList() {
		final List<X9File> fileList;
		if (isCommandSwitchSet(X9UtilWorkUnit.SWITCH_MULTI_FILE)) {
			verifyInputIsFolder();
			final String inputExtensions = getCommandSwitchValue(SWITCH_EXTENSION_INPUT);
			final String[] inputFileExtensions = validateInputFileExtensions(inputExtensions);
			fileList = X9FileUtils.createInputFileList(inputFile, X9FileUtils.SUBFOLDERS_INCLUDED,
					inputFileExtensions, getFileSkipInterval(),
					X9FileUtils.LOG_SELECTED_FILES_ENABLED);
		} else {
			fileList = new ArrayList<>(1);
			fileList.add(new X9File(inputFile));
			if (!inputFile.exists()) {
				throw X9Exception.abort("inputFile not found({})", inputFile);
			}
			if (inputFile.isDirectory()) {
				throw X9Exception.abort("inputFile is directory({})", inputFile);
			}
		}
		return fileList;
	}

	/**
	 * Verify that our input and output folders exist and that they are different.
	 */
	public void verifyInputAndOutputAsFolders() {
		verifyInputIsFolder();
		verifyOutputIsFolder();
		if (inputFile.equals(outputFile)) {
			throw X9Exception.abort("inputFolder and outputFolder cannot be the same");
		}
	}

	/**
	 * Verify that our input file exists and is a directory (folder).
	 */
	public void verifyInputIsFolder() {
		if (inputFile.isDirectory()) {
			LOGGER.info("inputFolder({}) located", inputFile);
		} else {
			throw X9Exception.abort("inputFolder not a directory({})", inputFile);
		}
	}

	/**
	 * Verify that our output file exists and is a directory (folder).
	 */
	public void verifyOutputIsFolder() {
		if (outputFile.isDirectory()) {
			LOGGER.info("outputFolder({}) located", outputFile);
		} else {
			throw X9Exception.abort("outputFolder not a directory({})", outputFile);
		}
	}

	/**
	 * Validate input file extensions.
	 *
	 * @param inputExtensions
	 *            input extensions separated by pipe character
	 * @return input file extensions
	 */
	public String[] validateInputFileExtensions(final String inputExtensions) {
		/*
		 * Validate that we have at least one input file extension.
		 */
		final String[] inputFileExtensions = StringUtils.split(inputExtensions, '|');
		if (inputFileExtensions == null || inputFileExtensions.length == 0) {
			throw X9Exception.abort("inputFileExtensions({}) missing", SWITCH_EXTENSION_INPUT);
		}

		/*
		 * Validate that none of the input file extensions are blank.
		 */
		for (final String inputFileExtension : inputFileExtensions) {
			if (StringUtils.isBlank(inputFileExtension)) {
				throw X9Exception.abort("inputFileExtensions({}) has blank entry",
						SWITCH_EXTENSION_INPUT);
			}
		}

		/*
		 * Return the input file extensions.
		 */
		return inputFileExtensions;
	}

	/**
	 * Get the file modify delay window and default when not specified.
	 *
	 * @return file skip interval
	 */
	public int getFileSkipInterval() {
		final int fileSkipInterval;
		final String skipInterval = x9commandLine.getSwitchValue(SWITCH_SKIP_INTERVAL);
		if (StringUtils.isNotBlank(skipInterval)) {
			fileSkipInterval = X9Numeric.toInt(skipInterval);
			if (fileSkipInterval < 0) {
				throw X9Exception.abort("delayInterval({}) not numeric", skipInterval);
			}
		} else {
			fileSkipInterval = SKIP_INTERVAL_DEFAULT;
			LOGGER.info("skipInterval defaulted({})", fileSkipInterval);
		}
		return fileSkipInterval;
	}

	/**
	 * Write summary totals as requested by command line switches.
	 *
	 * @param x9totalsXml
	 *            accumulated summary totals
	 */
	public void writeSummaryTotals(final X9TotalsXml x9totalsXml) {
		writeSummaryTotals(x9totalsXml, isCommandSwitchSet(SWITCH_WRITE_XML_TOTALS),
				isCommandSwitchSet(SWITCH_WRITE_TEXT_TOTALS),
				isCommandSwitchSet(SWITCH_WRITE_JSON_TOTALS));
	}

	/**
	 * Allocate an x9.37 trailer manager and apply command line options that are present.
	 * 
	 * @param sdkBase
	 *            sdkBase for this environment
	 * @return allocated trailer manager
	 */
	public X9TrailerManager937 allocate937TrailerManagerApplyOptions(final X9SdkBase sdkBase) {
		/*
		 * Allocate a new trailer manager.
		 */
		final X9TrailerManager937 x9trailerManager = new X9TrailerManager937(sdkBase);
		final X9CreditAttributes creditAttributes = sdkBase.getCreditAttributes();

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug(
					"setting trailerManagerOptions isCreditsAddToItemCount({}) "
							+ "isCreditsAddToItemAmount({}) isCreditsAddToImageCount({})",
					isCreditsAddToItemCount, isCreditsAddToItemAmount, isCreditsAddToImageCount);
		}

		if (isCreditsAddToItemCount != null) {
			creditAttributes.setAddToTrailerItemCount(isCreditsAddToItemCount);
		}
		if (isCreditsAddToItemAmount != null) {
			creditAttributes.setAddToTrailerItemAmount(isCreditsAddToItemAmount);
		}
		if (isCreditsAddToImageCount != null) {
			creditAttributes.setAddToTrailerImageCount(isCreditsAddToImageCount);
		}
		return x9trailerManager;
	}

	/**
	 * Write summary totals per provided switches.
	 *
	 * @param x9totalsXml
	 *            accumulated summary totals
	 * @param isWriteXmlTotals
	 *            true if xml totals to be written
	 * @param isWriteTextTotals
	 *            true if text totals to be written
	 * @param isWriteJsonTotals
	 *            true if text totals to be written
	 */
	public void writeSummaryTotals(final X9TotalsXml x9totalsXml, final boolean isWriteXmlTotals,
			final boolean isWriteTextTotals, final boolean isWriteJsonTotals) {
		/*
		 * Write file statistics to xml when requested via command line switch.
		 */
		if (isWriteXmlTotals) {
			final File summaryFile = makeSummaryFile(X9C.XML);
			if (summaryFile != null) {
				x9totalsXml.writeExternalXmlFile(summaryFile);
			}
		}

		/*
		 * Write file statistics to text when requested via command line switch.
		 */
		if (isWriteTextTotals) {
			x9totalsXml.writeTextFile(makeSummaryFile(X9C.TXT));
		}

		/*
		 * Write file statistics to json when requested via command line switch.
		 */
		if (isWriteJsonTotals) {
			x9totalsXml.writeJsonFile(makeSummaryFile(X9C.JSON));
		}
	}

	/**
	 * Make a summary file to be used to write statistics. It is important that the summary files
	 * have unique names since we can export an x9 file to xml and hence the summary file must be
	 * assigned a name that is different that the exported xml file itself.
	 *
	 * @param extension
	 *            desired summary file extension (eg, txt or xml)
	 * @return summary file
	 */
	public File makeSummaryFile(final String extension) {
		final File file = outputFile != null ? outputFile : inputFile;
		return file != null
				? new File(FilenameUtils.removeExtension(file.toString()) + SUMMARY_SUFFIX + "."
						+ extension)
				: null;
	}

	/**
	 * Abort when the input file is empty.
	 */
	public void abortOnEmptyFile() {
		throw X9Exception.abort("input file is empty({})", inputFile);
	}

	/**
	 * Abort when the input file is structurally flawed.
	 */
	public void abortOnStructurallyFlawedFile() {
		throw X9Exception.abort("input file is structurally flawed({})", inputFile);
	}

	/**
	 * Get task duration (end time minus start time).
	 * 
	 * @return task duration
	 */
	public long getTaskDuration() {
		return wasTaskStarted() ? getTaskEndTime() - getTaskStartTime() : 0;
	}

	/**
	 * Determine if this task was started.
	 * 
	 * @return true or false
	 */
	public boolean wasTaskStarted() {
		return startTime > 0;
	}

	/**
	 * Determine if this task was aborted.
	 * 
	 * @return true or false
	 */
	public boolean wasTaskAborted() {
		return startTime > 0 && (endTime == 0 || status < X9UtilBatch.EXIT_STATUS_ZERO);
	}

	/**
	 * Get the start time; this time must be set and will never be defaulted.
	 * 
	 * @return start time
	 */
	public long getTaskStartTime() {
		return startTime;
	}

	/**
	 * Get the end time and default to current time if not yet assigned. The end time is defaulted
	 * to accommodate situations where there was an abort, where the end time was not recorded.
	 * 
	 * @return end time
	 */
	public long getTaskEndTime() {
		if (endTime == 0) {
			markTaskEndTime();
		}
		return endTime;
	}

	/**
	 * Mark the start time from the system clock.
	 */
	public void markTaskStartTime() {
		startTime = System.currentTimeMillis();
	}

	/**
	 * Mark the end time from the system clock.
	 */
	public void markTaskEndTime() {
		endTime = System.currentTimeMillis();
	}

	/**
	 * Get the command line.
	 * 
	 * @return command line
	 */
	public X9CommandLine getCommandLine() {
		return x9commandLine;
	}

	/**
	 * Determine if a given switch is set on the command line for this work unit.
	 *
	 * @param switchId
	 *            switch identifier
	 * @return true or false
	 */
	public boolean isCommandSwitchSet(final String switchId) {
		return x9commandLine.isSwitchSet(switchId);
	}

	/**
	 * Get a switch value from the command line for this work unit.
	 *
	 * @param switchId
	 *            switch identifier
	 * @return switch value
	 */
	public String getCommandSwitchValue(final String switchId) {
		return x9commandLine.getSwitchValue(switchId);
	}

	/**
	 * Determine if image repair is enabled.
	 *
	 * @return true or false
	 */
	public boolean isImageRepairEnabled() {
		return isImageRepairEnabled;
	}

	/**
	 * Determine if image repair automated resize is enabled.
	 *
	 * @return true or false
	 */
	public boolean isImageResizeEnabled() {
		return isImageResizeEnabled;
	}

	/**
	 * Determine if a valid function switch is present on the command line.
	 *
	 * @return true or false
	 */
	public boolean isValidFunctionSwitchPresent() {
		/*
		 * Examine the command line for possible command line switches.
		 */
		int switchCount = 0;
		for (final String functionName : FUNCTION_NAMES) {
			if (isCommandSwitchSet(functionName)) {
				switchCount++;
				utilFunctionName = functionName;
			}
		}

		/*
		 * Error if there are multiple function switches present (very unexpected). This will catch
		 * the situation where there are different command line switches present, but it obviously
		 * does not identify the alternative situation when the same switch is present more than
		 * once. This latter case would be unusual but is not considered an error.
		 */
		if (switchCount > 1) {
			utilFunctionName = "";
			LOGGER.info("multiple function switches found on the command line");
			return false;
		}

		/*
		 * Error if no function switch found on the command line.
		 */
		if (switchCount == 0) {
			LOGGER.info("no function found on the command line");
			return false;
		}

		/*
		 * Otherwise we found exactly one function switch so all is well.
		 */
		return true;
	}

	/**
	 * Determine if multiple function switches are present on the command line.
	 *
	 * @return true or false
	 */
	public boolean hasMultipleFunctionSwitchesPresent() {
		int switchCount = 0;
		for (final String functionName : FUNCTION_NAMES) {
			if (isCommandSwitchSet(functionName)) {
				switchCount++;
				utilFunctionName = functionName;
			}
		}
		return (switchCount > 1);
	}

	/**
	 * Get an output temp file instance based on time stamp and do not rewrite switches.
	 * 
	 * @return temp file instance for the new output file
	 */
	public X9TempFile getOutputTempFileUsingDtsDnrSwitches() {
		final String timeStamp = isCommandSwitchSet(SWITCH_DATE_TIME_STAMP) ? "yyyyMMdd_HHmmss"
				: "";
		return getTempFileWithOptionalTimestamp(outputFile.toString(), timeStamp,
				isCommandSwitchSet(SWITCH_DO_NOT_REWRITE));
	}

	/**
	 * Get the csv results list.
	 * 
	 * @return csv results list
	 */
	public List<String[]> getCsvResultsList() {
		return csvResultsList;
	}

	/**
	 * Add another line to the csv results accumulation.
	 * 
	 * @param csvLine
	 *            csv line as string list
	 */
	public void addAnotherCsvLine(final List<String> csvLine) {
		csvResultsList.add(csvLine.toArray(new String[0]));
	}

	/**
	 * Get a boolean command switch value subject to how (if) it is defined on the command line.
	 * 
	 * @param switchId
	 *            switch identifier
	 * @return true (when set true), false (when set false), or null when not present
	 */
	private Boolean getBooleanCommandSwitchValue(final String switchId) {
		/*
		 * Return null when the switch is not present on the command line.
		 */
		if (!isCommandSwitchSet(switchId)) {
			return null;
		}

		/*
		 * Since the switch is present on the command line, we expect the value to be true/false.
		 */
		final String setting = getCommandSwitchValue(switchId);
		if (StringUtils.equalsAnyIgnoreCase(setting, "on", "true")) {
			return true;
		}
		if (StringUtils.equalsAnyIgnoreCase(setting, "off", "false")) {
			return false;
		}

		/*
		 * Abort when the command line switch setting is invalid.
		 */
		throw X9Exception.abort("switchId({}) setting({}) not true or false", switchId, setting);
	}

	/**
	 * Get the use case file as set on the command line and confirm existence.
	 *
	 * @param switchId
	 *            command line switch identifier
	 * @return use case file
	 */
	private File getCommandLineFile(final String switchId) {
		if (!isCommandSwitchSet(switchId)) {
			throw X9Exception.abort("{} command line switch required", switchId);
		}

		final File file = new File(x9commandLine.getSwitchValue(switchId));
		LOGGER.info("{} file set from commandLine({})", switchId, file);

		if (!X9FileUtils.existsWithPathTracing(file)) {
			throw X9Exception.abort("{} file notFound({})", switchId, file);
		}

		return file;
	}

	/**
	 * Mark the work unit as invalid and log expected syntax along with the input command line.
	 * 
	 * @param properSyntax
	 *            command line proper syntax
	 */
	private void markInvalidAndLogSyntax(final String properSyntax) {
		markInvalidCommandLine("expected syntax: x9util " + properSyntax + "; inputCommandLine("
				+ x9commandLine.getCommandLineAsString() + ")");
	}

	/**
	 * Mark the work unit as invalid and log proper usage followed by all available functions.
	 * 
	 * @param invalidReason
	 *            reason behind this command line error condition
	 */
	private void markInvalidAndLogAllAvailableFunctions(final String invalidReason) {
		if (markInvalidCommandLine(invalidReason)) {
			LOGGER.error("Usage: the x9utilities function must be one of these: "
					+ StringUtils.lowerCase(StringUtils.join(FUNCTION_NAMES, ", ")));
			LOGGER.error("Usage: for our X9Export product license, the only available functions "
					+ "are -export, -exportCsv, and -translate");
			logAllAvailableUtilityFunctions();
		}
	}

	/**
	 * Mark the work unit as invalid and log the reason.
	 * 
	 * @param invalidReason
	 *            reason behind this command line error condition
	 * @return true if standard logging is enabled otherwise false
	 */
	private boolean markInvalidCommandLine(final String invalidReason) {
		isValidWorkUnit = false;
		if (isParseLoggingEnabled) {
			LOGGER.error(invalidReason);
		}
		return isParseLoggingEnabled;
	}

	/**
	 * Log all available functions.
	 */
	private void logAllAvailableUtilityFunctions() {
		LOGGER.info("all available functions");
		LOGGER.info("x9util -console -h");
		LOGGER.info("x9util -batch -h");
		LOGGER.info("x9util -script -h");
		LOGGER.info("x9util -write -h");
		LOGGER.info("x9util -draw -h");
		LOGGER.info("x9util -translate -h");
		LOGGER.info("x9util -import -h");
		LOGGER.info("x9util -export -h");
		LOGGER.info("x9util -exportCsv -h");
		LOGGER.info("x9util -validate -h");
		LOGGER.info("x9util -scrub -h");
		LOGGER.info("x9util -make -h");
		LOGGER.info("x9util -merge -h");
		LOGGER.info("x9util -update -h");
		LOGGER.info("x9util -split -h");
		LOGGER.info("x9util -compare -h");
		LOGGER.info("x9util -imagePull -h");
		LOGGER.info("-h provides more detailed information when entered along with each "
				+ "of the above functions (for example, -write -h)");
	}

	/**
	 * Get an output temp file instance with an optional time stamp suffix. We also provide the
	 * option to abort if the constructed file already exits (do not rewrite).
	 * 
	 * @param fileName
	 *            output file name
	 * @param timeStampPattern
	 *            time stamp date pattern or empty string when not needed
	 * @param isDoNotRewrite
	 *            true if the file should not be written otherwise false
	 * @return temp file instance for the new output file
	 */
	public static X9TempFile getTempFileWithOptionalTimestamp(final String fileName,
			final String timeStampPattern, final boolean isDoNotRewrite) {
		/*
		 * Ensure a file name has been provided.
		 */
		final String trimmedFileName = StringUtils.trim(fileName);
		if (StringUtils.isBlank(trimmedFileName)) {
			throw X9Exception.abort("output fileName missing");
		}

		/*
		 * Add the current timestamp when directed.
		 */
		final String outputFileName = StringUtils.isBlank(timeStampPattern) ? trimmedFileName
				: FilenameUtils.removeExtension(trimmedFileName) + "."
						+ X9LocalDate.formatDateTime(X9LocalDate.getCurrentDateTime(),
								timeStampPattern)
						+ "." + FilenameUtils.getExtension(trimmedFileName);

		/*
		 * Abort if the output file already exists and we are instructed to not rewrite.
		 */
		final File timeStampedFile = new File(outputFileName);
		if (isDoNotRewrite && timeStampedFile.exists()) {
			throw X9Exception.abort("doNotRewrite=true and outputFile exists({})", timeStampedFile);
		}

		/*
		 * Return the constructed time stamped file.
		 */
		return getTempFileInstance(timeStampedFile);
	}

	/**
	 * Get an X9TempFile instance for an output file being created.
	 *
	 * @param file
	 *            output file
	 * @return temp file instance for the new output file
	 */
	public static X9TempFile getTempFileInstance(final File file) {
		return new X9TempFile(file);
	}

	/**
	 * Get the processing error list from all sdkObjects.
	 * 
	 * @return list of processing errors (never null)
	 */
	public List<X9Error> getProcessingErrorList() {
		return processingErrorList;
	}

	/**
	 * Add a new processing error to our accumulated list.
	 * 
	 * @param x9error
	 *            new processing error to be added
	 */
	public void addProcessingError(final X9Error x9error) {
		if (x9error != null) {
			processingErrorList.add(x9error);
		}
	}

	/**
	 * Add a list of additional processing errors to our accumulated list.
	 * 
	 * @param errorList
	 *            list of new errors to be added
	 */
	public void addProcessingErrors(final List<X9Error> errorList) {
		if (errorList != null) {
			processingErrorList.addAll(errorList);
		}
	}

	/**
	 * Get the maximum number of threads for this system. This number can be defined externally
	 * (from the command line parameter "-threads:" and will otherwise default to either 2 (for 32
	 * bit systems) and 4 (for 64 bit systems). Our design is to support larger numbers as possible,
	 * but we need to be careful here since each of these threads will be doing substantial work
	 * with a large read buffer. Also remember that our ability to process files concurrently is
	 * directly related to how large the Java heap is set either when X9Utilities is compiled or the
	 * command line assignment when running under a JVM.
	 * 
	 * @param threads
	 *            number of desired threads as a string
	 * @return assigned thread count
	 */
	public static int getTaskThreadCount(final String threads) {
		final int threadCount;
		if (StringUtils.isBlank(threads)) {
			threadCount = X9Task.getSuggestedConcurrentThreads();
		} else {
			threadCount = X9Numeric.toInt(threads);
			if (threadCount < 0) {
				throw X9Exception.abort("threads({}) not numeric", threads);
			}
			if (threadCount > MAXIMUM_ALLOWED_THREAD_COUNT) {
				throw X9Exception.abort("threads({}) excessive", threads);
			}
		}
		return threadCount;
	}

}
