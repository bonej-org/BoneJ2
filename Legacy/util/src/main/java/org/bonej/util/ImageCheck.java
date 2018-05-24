/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.util;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ImageStatistics;

/**
 * Check if an image conforms to the type defined by each method.
 *
 * @author Michael Doube
 */
public final class ImageCheck {

	private ImageCheck() {}

	/**
	 * Check that the voxel thickness is correct in the DICOM image metadata.
	 *
	 * @param imp an image.
	 */
	public static void dicomVoxelDepth(final ImagePlus imp) {
		if (imp == null) {
			IJ.error("Cannot check DICOM header of a null image");
			return;
		}
		final Calibration cal = imp.getCalibration();
		final double vD = cal.pixelDepth;
		final int stackSize = imp.getStackSize();
		final double first = getDicomSlicePosition(imp, 1);
		final double last = getDicomSlicePosition(imp, stackSize);
		if (first < 0 || last < 0) {
			IJ.error("No DICOM slice position data");
			return;
		}
		final double sliceSpacing = (Math.abs(last - first) + 1) / stackSize;
		final String units = cal.getUnits();
		final double error = Math.abs((sliceSpacing - vD) / sliceSpacing) * 100.0;
		if (Double.compare(vD, sliceSpacing) == 0) {
			IJ.log(imp.getTitle() + ": Voxel depth agrees with DICOM header.\n");
			return;
		}
		IJ.log(imp.getTitle() + ":\n" + "Current voxel depth disagrees by " +
			error + "% with DICOM header slice spacing.\n" + "Current voxel depth: " +
			IJ.d2s(vD, 6) + " " + units + "\n" + "DICOM slice spacing: " + IJ.d2s(
				sliceSpacing, 6) + " " + units + "\n" + "Updating image properties...");
		cal.pixelDepth = sliceSpacing;
		imp.setCalibration(cal);
	}

	/**
	 * Guess whether an image is Hounsfield unit calibrated
	 *
	 * @param imp an image.
	 * @return true if the image might be HU calibrated
	 */
	public static boolean huCalibrated(final ImagePlus imp) {
		final Calibration cal = imp.getCalibration();
		if (!cal.calibrated()) {
			return false;
		}
		final double[] coeff = cal.getCoefficients();
		final double value = cal.getCValue(0);
		return (value != 0 && value != Short.MIN_VALUE) || coeff[1] != 1;
	}

	/**
	 * Check if image is binary
	 *
	 * @param imp a GRAY8 type image.
	 * @return true if image is binary
	 */
	public static boolean isBinary(final ImagePlus imp) {
		if (imp == null) {
			IJ.error("Image is null");
			return false;
		}

		if (imp.getType() != ImagePlus.GRAY8) {
			return false;
		}

		final ImageStatistics stats = imp.getStatistics();
		return stats.histogram[0] + stats.histogram[255] == stats.pixelCount;
	}

	/**
	 * Checks if an image is a single slice image.
	 *
	 * @param imp an image.
	 * @return true if the image has only 1 slice.
	 */
	public static boolean isSingleSlice(final ImagePlus imp) {
		return imp.getStackSize() < 2;
	}

	/**
	 * Check if the image's voxels are isotropic in all 3 dimensions (i.e. are
	 * placed on a cubic grid)
	 *
	 * @param imp image to test
	 * @param tolerance tolerated fractional deviation from equal length
	 * @return true if voxel width == height == depth
	 */
	public static boolean isVoxelIsotropic(final ImagePlus imp,
		final double tolerance)
	{
		if (imp == null) {
			IJ.error("No image", "Image is null");
			return false;
		}
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double tLow = 1 - tolerance;
		final double tHigh = 1 + tolerance;
		final double widthHeightRatio = vW > vH ? vW / vH : vH / vW;

		if (widthHeightRatio < tLow || widthHeightRatio > tHigh) {
			return false;
		}

		if (isSingleSlice(imp)) {
			return true;
		}

		final double vD = cal.pixelDepth;
		final double widthDepthRatio = vW > vD ? vW / vD : vD / vW;

		return (widthDepthRatio >= tLow && widthDepthRatio <= tHigh);
	}

	/**
	 * Get the value associated with a DICOM tag from an ImagePlus header
	 *
	 * @param imp an image.
	 * @param slice number of slice in image.
	 * @return the value associated with the tag
	 */
	private static String getDicomAttribute(final ImagePlus imp,
		final int slice)
	{
		final ImageStack stack = imp.getImageStack();
		if (slice < 1 || slice > stack.getSize()) {
			return null;
		}
		final String header = stack.getSliceLabel(slice);
		if (header == null) {
			return null;
		}
		final String tag = "0020,0032";
		final int idx1 = header.indexOf(tag);
		final int idx2 = header.indexOf(":", idx1);
		final int idx3 = header.indexOf("\n", idx2);
		if (idx1 >= 0 && idx2 >= 0 && idx3 > idx2) {
			final String value = header.substring(idx2 + 1, idx3);
			return value.trim();
		}
		return " ";
	}

	private static double getDicomSlicePosition(final ImagePlus imp,
		final int slice)
	{
		final String position = getDicomAttribute(imp, slice);
		if (position == null) {
			return -1;
		}
		final String[] xyz = position.split("\\\\");
		if (xyz.length == 3) {
			return Double.parseDouble(xyz[2]);
		}
		return -1;
	}
}
