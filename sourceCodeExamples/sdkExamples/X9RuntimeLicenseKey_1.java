package com.x9ware.examples;

import com.x9ware.tools.X9BuildAttr;

/**
 * X9RuntimeLicenseKey is a static class which defines the license key to be assigned to this
 * runtime environment. This approach allows the license key to be embedded directly within the
 * application source code (it is not stored externally). This license key methodology is utilized
 * by the X9-SDK, X9Utilities, and E13B-OCR and supports perpetual, annualized, and evaluation
 * licenses. The license key is issued by X9Ware and is associated directly with a specific
 * organization. The license key is included in the system log for every execution of our products.
 *
 * @author X9Ware LLC. Copyright(c) 2012-2022 X9Ware LLC. All Rights Reserved. This is proprietary
 *         software as developed and licensed by X9Ware LLC under the exclusive legal right of the
 *         copyright holder. All licensees are provided the right to use the software only under
 *         certain conditions, and are specifically restricted from other specific uses including
 *         modification, sharing, reuse, redistribution, or reverse engineering.
 */
public class X9RuntimeLicenseKey {

	/**
	 * X9RuntimeLicenseKey Constructor is private (static class).
	 */
	private X9RuntimeLicenseKey() {
	}

	/*
	 * Client name: Evaluation; Company name: X9Ware LLC; Product name: X9Sdk; License key:
	 * f9e3-5818-963f-fede; Entered date: 2023/08/25; Expiration date: 2023/09/15.
	 */
	private static String licenseXmlDocument = "your key here";

	/**
	 * Set the runtime license key.
	 */
	public static void setLicenseKey() {
		X9BuildAttr.setSdkProductLicense(licenseXmlDocument);
	}

}
