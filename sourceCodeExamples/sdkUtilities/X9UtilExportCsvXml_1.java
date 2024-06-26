package com.x9ware.utilities;

import java.io.File;

import org.apache.commons.lang3.StringUtils;

import com.x9ware.beans.X9UtilExportCsvBean;
import com.x9ware.jaxb.X9Jaxb;
import com.x9ware.jaxb.X9JaxbXmlFileInterface;

/**
 * X9UtilExportCsvXml reads an export-csv xml definition as persisted to xml.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are explicitly restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public final class X9UtilExportCsvXml implements X9JaxbXmlFileInterface<X9UtilExportCsvBean> {

	/*
	 * Private.
	 */
	private X9UtilExportCsvBean exportCsvBean = new X9UtilExportCsvBean();

	/**
	 * X9UtilExportCsvXml Constructor.
	 */
	public X9UtilExportCsvXml() {
	}

	/**
	 * Get an export format by name.
	 *
	 * @param exportName
	 *            export name
	 * @return export format or null when not found
	 */
	public X9UtilExportCsvBean.Format getExportFormat(final String exportName) {
		for (final X9UtilExportCsvBean.Format exportFormat : exportCsvBean.formats.formatList) {
			if (StringUtils.equals(exportFormat.exportName, exportName)) {
				return exportFormat;
			}
		}
		return null;
	}

	@Override
	public X9UtilExportCsvBean readExternalXmlFile(final File xmlFile) {
		return exportCsvBean = X9Jaxb.getXmlFromFile(new X9UtilExportCsvBean(), xmlFile);
	}

	@Override
	public void writeExternalXmlFile(final File xmlFile) {
		exportCsvBean.updateReleaseAndBuild();
		X9Jaxb.putXml(exportCsvBean, xmlFile);
	}

}
