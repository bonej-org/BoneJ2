
package org.bonej.wrapperPlugins;

/**
 * Common messages displayed for the user
 *
 * @author Richard Domander
 */
public final class CommonMessages {

	static final String WEIRD_SPATIAL = "Need a 2D or 3D image";
	static final String NOT_3D_IMAGE = "Need a 3D image";
	static final String HAS_TIME_DIMENSIONS = "Image cannot have time axis";
	static final String HAS_CHANNEL_DIMENSIONS = "Image cannot be composite";
	static final String NOT_BINARY = "Need a binary image";
	static final String NOT_8_BIT_BINARY_IMAGE = "Need an 8-bit binary image";
	static final String NO_IMAGE_OPEN = "No image open";
	static final String NO_SKELETONS = "Image contained no skeletons";
	static final String BAD_CALIBRATION = "Calibration cannot be determined";

	private CommonMessages() {}
}
