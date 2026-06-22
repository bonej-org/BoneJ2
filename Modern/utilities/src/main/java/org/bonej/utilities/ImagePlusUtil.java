/*-
 * #%L
 * Utility methods for BoneJ2
 * %%
 * Copyright (C) 2015 - 2026 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.utilities;

import java.util.Arrays;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;

/**
 * Utility methods for checking ImagePlus properties
 *
 * @author Richard Domander
 */
public final class ImagePlusUtil {

	private ImagePlusUtil() {}

	/**
	 * Calculates the degree of spatial calibration anisotropy.
	 * <p>
	 * Spatial calibration is anisotropic if pixel width, height and depth are not
	 * equal.
	 * </p>
	 *
	 * @param image an ImageJ1 style {@link ImagePlus}.
	 * @return Percentage of anisotropy. An isotropic image returns 0.0
	 */
	public static double anisotropy(final ImagePlus image) {
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
	 * @return true if the image has more than one slice.
	 */
	public static boolean is3D(final ImagePlus image) {
		return image.getNSlices() > 1;
	}

	/**
	 * Checks if the image has only two different colours.
	 *
	 * @param image an ImageJ1 style {@link ImagePlus}.
	 * @return true if there are only two distinct pixel values present in the
	 *         image, false if more.
	 */
	public static boolean isBinaryColour(final ImagePlus image) {
		return Arrays.stream(image.getStatistics().histogram).filter(p -> p > 0)
			.count() <= 2;
	}
	
	/**
	 * Checks if the ImageStack contains native arrays that can be accessed directly from the heap.
	 * Returns true if safe to access directly without duplication.
	 * Returns false if the stack uses wrappers, VirtualStacks, or other non-native representations.
	 * 
	 * @param imp ImagePlus to check
	 * @return true if safe to access imp directly without duplication or false if the stack
	 * uses wrappers, VirtualStacks, or other non-native representations, or if there was an exception.
	 */
	public static boolean isNativeStack(final ImagePlus imp) {
		try {
			ImageStack stack = imp.getStack();
			
			// Check if the stack itself is a VirtualStack (which implies lazy loading/wrapping)
			if (stack instanceof ij.VirtualStack) {
				return false;
			}

			// Get the pixels for the first slice (1-based index)
			Object pixels = stack.getPixels(1);
			
			// Verify it is a primitive byte, short, float or int array
			return (pixels instanceof byte[] ||
					pixels instanceof short[] ||
					pixels instanceof float[] ||
					pixels instanceof int[]);
		} catch (Exception e) {
			// If anything fails (e.g., empty stack, weird state), assume non-native to be safe
			return false;
		}
	}
}
