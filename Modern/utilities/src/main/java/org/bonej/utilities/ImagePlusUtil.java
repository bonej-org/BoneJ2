
package org.bonej.utilities;

import java.util.Arrays;

import ij.ImagePlus;
import ij.measure.Calibration;

/**
 * Utility methods for checking ImagePlus properties
 *
 * @author Richard Domander
 */
public final class ImagePlusUtil {

	private ImagePlusUtil() {}

	/**
	 * Calculates the degree of anisotropy in the image, i.e. the maximum
	 * difference in the calibrations of the dimensions.
	 *
	 * @param image an ImageJ1 style {@link ImagePlus}.
	 * @return Anisotropy fraction [0.0, Double.MAX_VALUE], an isotropic image
	 *         returns 0.0 Returns Double.NaN if image == null.
	 */
	public static double anisotropy(final ImagePlus image) {
		if (image == null) {
			return Double.NaN;
		}

		final Calibration cal = image.getCalibration();
		final double w = cal.pixelWidth;
		final double h = cal.pixelHeight;
		final double widthHeightRatio = w > h ? w / h : h / w;
		final double widthHeightAnisotropy = Math.abs(1.0 - widthHeightRatio);

		if (!is3D(image)) {
			return widthHeightAnisotropy;
		}

		final double d = cal.pixelDepth;
		final double widthDepthRatio = w > d ? w / d : d / w;
		final double heightDepthRatio = h > d ? h / d : d / h;
		final double widthDepthAnisotropy = Math.abs(1.0 - widthDepthRatio);
		final double heightDepthAnisotropy = Math.abs(1.0 - heightDepthRatio);

		return Math.max(widthHeightAnisotropy, Math.max(widthDepthAnisotropy,
			heightDepthAnisotropy));
	}

	/**
	 * Duplicates the image without changing the title of the copy, or cropping it
	 * to the ROI.
	 * <p>
	 * Circumvents the default behaviour of {@link ImagePlus#duplicate()}.
	 * </p>
	 *
	 * @param image an ImageJ1 style ImagePlus.
	 * @return an unchanged copy of the image.
	 */
	public static ImagePlus cleanDuplicate(final ImagePlus image) {
		image.killRoi();
		final ImagePlus copy = image.duplicate();
		image.restoreRoi();
		copy.setTitle(image.getTitle());
		return copy;
	}

	/**
	 * Checks if the image is 3D.
	 *
	 * @param image an ImageJ1 style {@link ImagePlus}.
	 * @return true if the image has more than one slice, false if not, or image
	 *         is null.
	 */
	public static boolean is3D(final ImagePlus image) {
		return image != null && image.getNSlices() > 1;
	}

	/**
	 * Checks if the image has only two different colours.
	 *
	 * @param image an ImageJ1 style {@link ImagePlus}.
	 * @return true if there are only two distinct pixel values present in the
	 *         image, false if more, or the image is null.
	 */
	public static boolean isBinaryColour(final ImagePlus image) {
		return image != null && Arrays.stream(image.getStatistics().histogram)
			.filter(p -> p > 0).count() <= 2;
	}
}
