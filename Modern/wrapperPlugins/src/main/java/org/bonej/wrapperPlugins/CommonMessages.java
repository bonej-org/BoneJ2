
package org.bonej.wrapperPlugins;

/**
 * Common messages displayed for the user
 *
 * @author Richard Domander
 */
public class CommonMessages {

	public static final String WEIRD_SPATIAL = "Need a 2D or 3D image";
	public static final String NOT_3D_IMAGE = "Need a 3D image";
	public static final String HAS_TIME_DIMENSIONS =
		"Image cannot have time axis";
	public static final String HAS_CHANNEL_DIMENSIONS =
		"Image cannot be composite";
	public static final String NOT_BINARY = "Need a binary image";
	public static final String NOT_8_BIT_BINARY_IMAGE =
		"Need an 8-bit binary image";
	public static final String NO_IMAGE_OPEN = "No image open";
	public static final String NO_SKELETONS = "Image contains no skeletons";
	public static final String GOT_SKELETONISED = "The image was skeletonised";
	public static final String BAD_CALIBRATION =
		"Calibration cannot be determined";

	private CommonMessages() {}
}
