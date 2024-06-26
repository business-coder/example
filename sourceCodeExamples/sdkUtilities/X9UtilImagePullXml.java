package com.x9ware.utilities;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.x9ware.actions.X9Exception;
import com.x9ware.beans.X9UtilImagePullBean;
import com.x9ware.jaxb.X9Jaxb;
import com.x9ware.jaxb.X9JaxbXmlFileInterface;

/**
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilImagePullXml implements X9JaxbXmlFileInterface<X9UtilImagePullBean> {

	/**
	 * Private.
	 */
	private final int[] outputFieldsByNumber;
	private X9UtilImagePullBean imagePullBean = new X9UtilImagePullBean();

	/*
	 * Lookup map to obtain standard field numbers by name.
	 */
	private static final Map<String, Integer> FIELD_LOOKUP_MAP = new HashMap<>();

	/*
	 * Static initializer which assigns all available fields by name. Users then select from these
	 * available fields and reorder them as required for their specific application requirements.
	 */
	static {
		FIELD_LOOKUP_MAP.put("x9fileName", X9UtilImagePullRequest.X9_FILE_NAME);
		FIELD_LOOKUP_MAP.put("itemSequenceNumber", X9UtilImagePullRequest.ITEM_SEQUENCE_NUMBER);
		FIELD_LOOKUP_MAP.put("frontImage", X9UtilImagePullRequest.FRONT_IMAGE);
		FIELD_LOOKUP_MAP.put("backImage", X9UtilImagePullRequest.BACK_IMAGE);
		FIELD_LOOKUP_MAP.put("recordType", X9UtilImagePullRequest.RECORD_TYPE);
		FIELD_LOOKUP_MAP.put("recordNumber", X9UtilImagePullRequest.RECORD_NUMBER);
		FIELD_LOOKUP_MAP.put("auxOnUs", X9UtilImagePullRequest.AUX_ONUS);
		FIELD_LOOKUP_MAP.put("epc", X9UtilImagePullRequest.EPC);
		FIELD_LOOKUP_MAP.put("payorRouting", X9UtilImagePullRequest.PAYOR_ROUTING);
		FIELD_LOOKUP_MAP.put("payorRoutingCheckDigit",
				X9UtilImagePullRequest.PAYOR_ROUTING_CHECK_DIGIT);
		FIELD_LOOKUP_MAP.put("onus", X9UtilImagePullRequest.ONUS);
		FIELD_LOOKUP_MAP.put("amount", X9UtilImagePullRequest.AMOUNT);
		FIELD_LOOKUP_MAP.put("bofdIndicator", X9UtilImagePullRequest.BOFD_INDICATOR);
		FIELD_LOOKUP_MAP.put("returnLocationRouting",
				X9UtilImagePullRequest.RETURN_LOCATION_ROUTING);
		FIELD_LOOKUP_MAP.put("bofdDate", X9UtilImagePullRequest.BOFD_DATE);
		FIELD_LOOKUP_MAP.put("bofdRouting", X9UtilImagePullRequest.BOFD_ROUTING);
		FIELD_LOOKUP_MAP.put("imageCreatorRouting", X9UtilImagePullRequest.IMAGE_CREATOR_ROUTING);
		FIELD_LOOKUP_MAP.put("imageCreatorDate", X9UtilImagePullRequest.IMAGE_CREATOR_DATE);
		FIELD_LOOKUP_MAP.put("returnReason", X9UtilImagePullRequest.RETURN_REASON);
	}

	/*
	 * Constants.
	 */
	private static final int FIELD_NOT_DEFINED = -1;

	/**
	 * Logger instance.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(X9UtilImagePullXml.class);

	/**
	 * X9UtilImagePullXml Constructor.
	 *
	 * @param xmlFile
	 *            xml file to be loaded or null when a default list should be created
	 */
	public X9UtilImagePullXml(final File xmlFile) {
		if (xmlFile == null) {
			/*
			 * Default to selecting all possible fields in their default order.
			 */
			outputFieldsByNumber = new int[X9UtilImagePullRequest.FIELD_COUNT];
			for (int i = 0; i < X9UtilImagePullRequest.FIELD_COUNT; i++) {
				outputFieldsByNumber[i] = i;
			}
		} else {
			/*
			 * Load the xml document.
			 */
			imagePullBean = readExternalXmlFile(xmlFile);
			if (imagePullBean == null) {
				throw X9Exception.abort("unable to load xml document({})", xmlFile);
			}

			/*
			 * Get a list of all output fields to be created.
			 */
			final List<X9UtilImagePullBean.ImagePullField> fieldList = imagePullBean.fieldList;

			if (fieldList == null || fieldList.size() == 0) {
				throw X9Exception.abort("invalid image pull field list({})", xmlFile);
			}

			/*
			 * Get each output field entry.
			 */
			int index = 0;
			final int nameCount = fieldList.size();
			outputFieldsByNumber = new int[nameCount];
			for (final X9UtilImagePullBean.ImagePullField field : fieldList) {
				/*
				 * Get the swap definition.
				 */
				final String fieldName = field.name;
				final int imagePullFieldNumber = getStandardFieldNumberByName(fieldName);

				if (imagePullFieldNumber < 0) {
					throw X9Exception.abort("invalid fieldName({})", fieldName);
				}

				/*
				 * Add this field number to the output field array.
				 */
				outputFieldsByNumber[index] = imagePullFieldNumber;

				/*
				 * Log the fields to be created.
				 */
				LOGGER.info("output fieldName({}) added at index({})", fieldName, (++index));
			}
		}
	}

	/**
	 * Create the user defined csv output array which contains the specifically requested fields and
	 * in their user defined order. This approach has several benefits. First is that users can get
	 * the list of fields that they need for this application. Second is that they are insulated
	 * from our possible future addition of fields since their implementation of this process will
	 * not be impacted unless they would decide to explicitly select those new fields.
	 *
	 * @param csvArray
	 *            standard csv output array
	 * @return user defined csv output array based on their selected fields
	 */
	public String[] createCsvOutputArray(final String[] csvArray) {
		final int fieldCount = outputFieldsByNumber.length;
		final String[] csvOutputArray = new String[fieldCount];
		for (int i = 0; i < fieldCount; i++) {
			csvOutputArray[i] = csvArray[outputFieldsByNumber[i]];
		}
		return csvOutputArray;
	}

	/**
	 * Get our standard field number by field name.
	 *
	 * @param fieldName
	 *            field name
	 * @return standard field number
	 */
	private int getStandardFieldNumberByName(final String fieldName) {
		final Integer standardFieldNumber = FIELD_LOOKUP_MAP.get(fieldName);
		return standardFieldNumber == null ? FIELD_NOT_DEFINED : standardFieldNumber;
	}

	@Override
	public X9UtilImagePullBean readExternalXmlFile(final File xmlFile) {
		return imagePullBean = X9Jaxb.getXmlFromFile(new X9UtilImagePullBean(), xmlFile);
	}

	@Override
	public void writeExternalXmlFile(final File xmlFile) {
		imagePullBean.updateReleaseAndBuild();
		X9Jaxb.putXml(imagePullBean, xmlFile);
	}

}
