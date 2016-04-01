/*
 * #%L
 * PQCT: ImageJ density distribution analysis plugin.
 * %%
 * Copyright (C) 2007 - 2016 Timo Rantalainen, Michael Doube, BoneJ developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package org.doube.bonej.pqct.io;

//Vector, Collections
import java.util.Arrays;

public class ScaledImageData {
	public double[] scaledImage;
	public double[] softScaledImage;
	public double[] justROI;
	public double minimum;
	public double maximum;
	public int width;
	public int height;
	public double pixelSpacing;
	int filterSize;

	// Constructor
	public ScaledImageData(final int[] data, final int widthIn, final int heightIn, final double VoxelSize,
			final double scalingFactor, final double constant, int filterSize, final boolean flipHorizontal,
			final boolean flipVertical, final boolean noFiltering) {
		height = heightIn;
		width = widthIn;
		pixelSpacing = VoxelSize;
		filterSize = 3; // filterSize x filterSize median filter will be used
		final double[] unFiltered = new double[width * height];

		for (int t = 0; t < width * height; t++) { // Scale the image
			unFiltered[t] = (data[t]) * scalingFactor + constant;
		}
		/* Get the min and max values */
		final double[] tempSort = unFiltered.clone();
		Arrays.sort(tempSort);
		minimum = tempSort[0];
		maximum = tempSort[tempSort.length - 1];
		softScaledImage = medianFilter(unFiltered, width, height, 7); // Median
																		// filter
																		// data
		if (noFiltering) {
			scaledImage = unFiltered.clone();
		} else {
			scaledImage = medianFilter(unFiltered, width, height, filterSize); // Median
																				// filter
																				// data
		}

		if (flipHorizontal) {// Flip the image around the horizontal axis...
			flipHorizontally();
		}
		if (flipVertical) {// Flip the image around the horizontal axis...
			flipVertically();
		}
	}

	public void flipHorizontally() {
		final double[] temp = scaledImage.clone();
		final double[] temp2 = softScaledImage.clone();
		for (int j = 0; j < height; ++j) {
			for (int i = 0; i < width; ++i) {
				scaledImage[i + (height - 1 - j) * width] = temp[i + j * width];
				softScaledImage[i + (height - 1 - j) * width] = temp2[i + j * width];
			}
		}
	}

	public void flipVertically() {
		final double[] temp = scaledImage.clone();
		final double[] temp2 = softScaledImage.clone();
		for (int j = 0; j < height; ++j) {
			for (int i = 0; i < width; ++i) {
				scaledImage[(width - 1 - i) + j * width] = temp[i + j * width];
				softScaledImage[(width - 1 - i) + j * width] = temp2[i + j * width];
			}
		}
	}

	// Meadian filter
	public double[] medianFilter(final double[] data, final int width, final int height, final int filterSize) {
		final double[] filtered = new double[width * height];
		final double[] toMedian = new double[filterSize * filterSize];
		for (int i = 0; i < filtered.length; ++i) {
			filtered[i] = minimum;
		}
		/*
		 * Fill filtered with min value to get the frame from messing up with
		 * edge detection
		 */

		final int noGo = (int) Math.floor((filterSize) / 2.0);
		final int median = (int) Math.floor((filterSize * filterSize) / 2.0); // would
																				// be
																				// ceil,
																				// but
																				// indexing
																				// begins
																				// from
																				// 0!
		int rowTotal, colTotal;
		for (int row = noGo; row < height - noGo; row++) {
			for (int col = noGo; col < width - noGo; col++) {
				int newPixel = 0;
				for (int rowOffset = -noGo; rowOffset <= noGo; rowOffset++) {
					for (int colOffset = -noGo; colOffset <= noGo; colOffset++) {
						rowTotal = row + rowOffset;
						colTotal = col + colOffset;
						toMedian[newPixel] = data[rowTotal * width + colTotal];
						newPixel++;
					}
				}
				Arrays.sort(toMedian);
				filtered[row * width + col] = toMedian[median];
			}
		}

		return filtered;
	}

}
