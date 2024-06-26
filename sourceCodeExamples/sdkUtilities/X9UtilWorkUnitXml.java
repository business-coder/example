package com.x9ware.utilities;

import java.io.File;

import com.x9ware.beans.X9UtilWorkUnitAttr;
import com.x9ware.beans.X9UtilWorkUnitBean;
import com.x9ware.core.X9;
import com.x9ware.elements.X9C;
import com.x9ware.jaxb.X9Jaxb;
import com.x9ware.jaxb.X9JaxbXmlFileInterface;

/**
 * X9UtilWorkUnitXml describes a single utilities function to be performed. This class includes
 * methods to read/write the work unit definition to a saved external xml file.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilWorkUnitXml implements X9JaxbXmlFileInterface<X9UtilWorkUnitBean> {

	/*
	 * Private.
	 */
	private X9UtilWorkUnitBean workUnitBean = new X9UtilWorkUnitBean();

	/*
	 * Constants.
	 */
	public static final String DEFAULT_FUNCTION = "Export";
	public static final String DEFAULT_X9_CONFIGURATION = X9.X9_100_187_UCD_2008_CONFIG;
	public static final String DEFAULT_XML_WORKUNIT = "default.X9Utilities.xml";

	/**
	 * X9UtilWorkUnitXml Constructor.
	 */
	public X9UtilWorkUnitXml() {
	}

	/**
	 * Get our attributes bean.
	 *
	 * @return attributes bean
	 */
	public X9UtilWorkUnitAttr getAttr() {
		return workUnitBean.attributes;
	}

	@Override
	public X9UtilWorkUnitBean readExternalXmlFile(final File xmlFile) {
		return workUnitBean = X9Jaxb.getXmlFromFile(new X9UtilWorkUnitBean(),
				X9C.XML_CONTENT_WARN_DISABLED, xmlFile, X9C.XML_REWRITE_ON_ERROR_DISABLED,
				X9C.XML_CONTENT_LOGGING_DISABLED);
	}

	@Override
	public void writeExternalXmlFile(final File xmlFile) {
		workUnitBean.updateReleaseAndBuild();
		X9Jaxb.putXml(workUnitBean, xmlFile);
	}

}
