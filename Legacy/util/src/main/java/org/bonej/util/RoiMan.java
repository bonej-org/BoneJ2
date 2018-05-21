/*
 * #%L
 * BoneJ utility classes.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers.
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

package org.bonej.util;

import java.awt.Rectangle;
import java.util.ArrayList;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

/**
 * Do useful things with ImageJ's ROI Manager
 *
 * @author Michael Doube
 */
public final class RoiMan {

	private static final int NO_SLICE_NUMBER = -1;

	private RoiMan() {}

	/**
	 * Crop a stack to the limits of the ROIs in the ROI Manager and optionally
	 * fill the background with a single pixel value.
	 *
	 * @param roiMan ROI Manager containing ROIs
	 * @param sourceStack input stack
	 * @param fillBackground if true, background will be set to value. NB Will not
	 *          set the background of the areas copied from sourceStack
	 * @param fillColor value to set background to
	 * @param padding empty pixels to pad faces of cropped stack with
	 * @return cropped copy of input stack
	 */
	public static ImageStack cropStack(final RoiManager roiMan,
		final ImageStack sourceStack, final boolean fillBackground,
		final int fillColor, final int padding)
	{
		final int[] limits = getLimits(roiMan, sourceStack);

		if (limits == null) {
			return null;
		}

		final int xMin = limits[0];
		final int xMax = limits[1];
		final int yMin = limits[2];
		final int yMax = limits[3];
		final int zMin = limits[4];
		final int zMax = limits[5];

		final int croppedWidth = xMax - xMin + 2 * padding;
		final int croppedHeight = yMax - yMin + 2 * padding;

		final ImageStack targetStack = new ImageStack(croppedWidth, croppedHeight);

		// copy
		ImageProcessor sourceProcessor;
		ImageProcessor targetProcessor;
		ArrayList<Roi> sliceRois;

		for (int sourceZ = zMin; sourceZ <= zMax; sourceZ++) {
			sliceRois = getSliceRoi(roiMan, sourceStack, sourceZ);
			if (sliceRois.isEmpty()) {
				continue;
			}

			sourceProcessor = sourceStack.getProcessor(sourceZ);
			targetProcessor = sourceProcessor.createProcessor(croppedWidth,
				croppedHeight);

			if (fillBackground) {
				targetProcessor.setColor(fillColor);
				targetProcessor.fill();
			}

			copySlice(sourceProcessor, targetProcessor, sliceRois, padding);
			targetStack.addSlice("", targetProcessor);
		}

		// z padding
		targetProcessor = targetStack.getProcessor(1).createProcessor(croppedWidth,
			croppedHeight);
		if (fillBackground) {
			targetProcessor.setColor(fillColor);
			targetProcessor.fill();
		}
		for (int i = 0; i < padding; i++) {
			targetStack.addSlice("", targetProcessor.duplicate(), 0);
			targetStack.addSlice(targetProcessor.duplicate());
		}

		return targetStack;
	}

	/**
	 * Remove all ROIs from the ROI manager
	 *
	 * @param roiMan an instance of {@link RoiManager}.
	 */
	// TODO Move to SphereFitter
	public static void deleteAll(final RoiManager roiMan) {
		final Roi[] rois = roiMan.getRoisAsArray();
		for (int i = 0; i < rois.length; i++) {
			if (roiMan.getCount() == 0) break;
			roiMan.select(i);
			roiMan.runCommand("delete");
		}
	}

	/**
	 * Get the calibrated 3D coordinates of point ROIs from the ROI manager
	 *
	 * @param imp an image.
	 * @param roiMan an instance of {@link RoiManager}
	 * @return double[n][3] containing n (x, y, z) coordinates or null if there
	 *         are no points
	 */
	// TODO Move to SphereFitter
	public static double[][] getRoiManPoints(final ImagePlus imp,
		final RoiManager roiMan)
	{
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		int nPoints = 0;
		final Roi[] roiList = roiMan.getRoisAsArray();
		for (int i = 0; i < roiMan.getCount(); i++) {
			final Roi roi = roiList[i];
			if (roi.getType() == 10) {
				nPoints++;
			}
		}
		if (nPoints == 0) return null;
		final double[][] dataPoints = new double[nPoints][3];
		int j = 0;
		for (int i = 0; i < roiMan.getCount(); i++) {
			final Roi roi = roiList[i];
			if (roi.getType() == 10) {
				final Rectangle xy = roi.getBounds();
				dataPoints[j][0] = xy.getX() * vW;
				dataPoints[j][1] = xy.getY() * vH;
				dataPoints[j][2] = roi.getPosition() * vD;
				j++;
			}
		}
		return dataPoints;
	}

	private static int clampToNonNegative(final int value, final int max) {
		if (value < 0) {
			return 0;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/**
	 * Copies the pixels in the given ROI from the source image to the target
	 * image.
	 *
	 * @param sourceProcessor Copy source
	 * @param targetProcessor Copy target
	 * @param minX Horizontal start of the copy area 0 &le; minX &lt; width
	 * @param minY Vertical start of the copy area 0 &le; minY &lt; height
	 * @param maxX Horizontal end of the copy area 0 &le; maxX &le; width
	 * @param maxY Vertical end of the copy area 0 &le; maxY &le; height
	 * @param padding Number pixels added to each side of the copy target
	 */
	private static void copyRoi(final ImageProcessor sourceProcessor,
		final ImageProcessor targetProcessor, final int minX, final int minY,
		final int maxX, final int maxY, final int padding)
	{
		int targetY = padding;
		for (int sourceY = minY; sourceY < maxY; sourceY++) {
			int targetX = padding;
			for (int sourceX = minX; sourceX < maxX; sourceX++) {
				final int sourceColor = sourceProcessor.get(sourceX, sourceY);
				targetProcessor.set(targetX, targetY, sourceColor);
				targetX++;
			}
			targetY++;
		}
	}

	/**
	 * Copies pixels under all the ROIs on a slide
	 *
	 * @param sourceProcessor The source image slide
	 * @param targetProcessor The target slide
	 * @param sliceRois List of all the ROIs on the source slide
	 * @param padding Number of pixels added on each side of the target slide
	 */
	private static void copySlice(final ImageProcessor sourceProcessor,
		final ImageProcessor targetProcessor, final Iterable<Roi> sliceRois,
		final int padding)
	{
		for (final Roi sliceRoi : sliceRois) {
			final Rectangle rectangle = sliceRoi.getBounds();
			final boolean invalid = !getSafeRoiBounds(rectangle, sourceProcessor
				.getWidth(), sourceProcessor.getHeight());

			if (invalid) {
				continue;
			}

			final int minY = rectangle.y;
			final int minX = rectangle.x;
			final int maxY = rectangle.y + rectangle.height;
			final int maxX = rectangle.x + rectangle.width;

			final ImageProcessor mask = sourceProcessor.getMask();
			if (mask == null) {
				copyRoi(sourceProcessor, targetProcessor, minX, minY, maxX, maxY,
					padding);
			}
			else {
				copyRoiWithMask(sourceProcessor, targetProcessor, minX, minY, maxX,
					maxY, padding);
			}
		}
	}

	/**
	 * Crops the given rectangle to the area [0, 0, width, height]
	 *
	 * @param bounds The rectangle to be fitted
	 * @param width Maximum width of the rectangle
	 * @param height Maximum height of the rectangle
	 * @return false if the height or width of the fitted rectangle is 0 (Couldn't
	 *         be cropped inside the area).
	 */
	private static boolean getSafeRoiBounds(final Rectangle bounds,
		final int width, final int height)
	{
		final int xMin = clampToNonNegative(bounds.x, width);
		final int xMax = clampToNonNegative(bounds.x + bounds.width, width);
		final int yMin = clampToNonNegative(bounds.y, height);
		final int yMax = clampToNonNegative(bounds.y + bounds.height, height);
		final int newWidth = xMax - xMin;
		final int newHeight = yMax - yMin;

		bounds.setBounds(xMin, yMin, newWidth, newHeight);

		return newWidth > 0 && newHeight > 0;
	}

	private static boolean isActiveOnAllSlices(final int sliceNumber) {
		return sliceNumber == NO_SLICE_NUMBER;
	}

	/**
	 * Return a list of ROIs that are active in the given slice, sliceNumber. ROIs
	 * without a slice number are assumed to be active in all slices.
	 *
	 * @param roiMan an instance of {@link RoiManager}
	 * @param stack a image stack
	 * @param sliceNumber the number of slice in the stack.
	 * @return A list of active ROIs on the slice. Returns an empty list if roiMan
	 *         == null or stack == null Returns an empty list if slice number is
	 *         out of bounds
	 */
	static ArrayList<Roi> getSliceRoi(final RoiManager roiMan,
		final ImageStack stack, final int sliceNumber)
	{
		final ArrayList<Roi> roiList = new ArrayList<>();

		if (roiMan == null || stack == null) {
			return roiList;
		}

		if (sliceNumber < 1 || sliceNumber > stack.getSize()) {
			return roiList;
		}

		final Roi[] rois = roiMan.getRoisAsArray();
		for (final Roi roi : rois) {
			final String roiName = roi.getName();
			if (roiName == null) {
				continue;
			}
			final int roiSliceNumber = roiMan.getSliceNumber(roiName);
			final int roiPosition = roi.getPosition();
			if (roiSliceNumber == sliceNumber || roiSliceNumber == NO_SLICE_NUMBER ||
				roiPosition == sliceNumber)
			{
				roiList.add(roi);
			}
		}
		return roiList;
	}

	/**
	 * Find the x, y and z limits of the ROIs in the ROI Manager
	 *
	 * @param roiMan A collection of ROIs
	 * @param stack The stack inside which the ROIs must fit (max limits).
	 * @return int[] containing x min, x max, y min, y max, z min and z max.
	 *         Returns null if roiMan == null or if roiMan.getCount() == 0 Returns
	 *         null if stack == null If any of the ROIs contains no slice
	 *         information, z min is set to 1 and z max is set to stack.getSize()
	 */
	static int[] getLimits(final RoiManager roiMan, final ImageStack stack) {
		if (roiMan == null || roiMan.getCount() == 0) {
			return null;
		}

		if (stack == null) {
			return null;
		}

		final int LAST_SLIDE = stack.getSize();
		final int DEFAULT_Z_MIN = 1;

		int xMin = Integer.MAX_VALUE;
		int xMax = 0;
		int yMin = Integer.MAX_VALUE;
		int yMax = 0;
		int zMin = LAST_SLIDE;
		int zMax = DEFAULT_Z_MIN;
		boolean noZRoi = false;
		boolean noValidRois = true;
		final Roi[] rois = roiMan.getRoisAsArray();

		for (final Roi roi : rois) {
			final Rectangle r = roi.getBounds();
			final boolean invalid = !getSafeRoiBounds(r, stack.getWidth(), stack
				.getHeight());

			if (invalid) {
				continue;
			}

			xMin = Math.min(r.x, xMin);
			xMax = Math.max(r.x + r.width, xMax);
			yMin = Math.min(r.y, yMin);
			yMax = Math.max(r.y + r.height, yMax);

			final int slice = roiMan.getSliceNumber(roi.getName());
			if (slice >= 1 && slice <= LAST_SLIDE) {
				zMin = Math.min(slice, zMin);
				zMax = Math.max(slice, zMax);
				noValidRois = false;
			}
			else if (isActiveOnAllSlices(slice)) {
				noZRoi = true; // found a ROI with no Z info
				noValidRois = false;
			}
		}

		if (noValidRois) {
			return null;
		}

		if (noZRoi) {
			return new int[] { xMin, xMax, yMin, yMax, DEFAULT_Z_MIN, LAST_SLIDE };
		}

		return new int[] { xMin, xMax, yMin, yMax, zMin, zMax };
	}

	/**
	 * Copies the pixels in the given ROI from the source image to the target
	 * image. Copies only those pixels where the color of the given mask &gt; 0.
	 *
	 * @param sourceProcessor Copy source
	 * @param targetProcessor Copy target
	 * @param minX Horizontal start of the copy area 0 &le; minX &lt; width
	 * @param minY Vertical start of the copy area 0 &le; minY &lt; height
	 * @param maxX Horizontal end of the copy area 0 &le; maxX &le; width
	 * @param maxY Vertical end of the copy area 0 &le; maxY &le; height
	 * @param padding Number pixels added to each side of the copy target
	 */
	static void copyRoiWithMask(final ImageProcessor sourceProcessor,
		final ImageProcessor targetProcessor, final int minX, final int minY,
		final int maxX, final int maxY, final int padding)
	{
		final ImageProcessor mask = sourceProcessor.getMask();
		if (mask == null) {
			copyRoi(sourceProcessor, targetProcessor, minX, minY, maxX, maxY,
				padding);
			return;
		}

		int targetY = padding;
		for (int sourceY = minY; sourceY < maxY; sourceY++) {
			int targetX = padding;
			for (int sourceX = minX; sourceX < maxX; sourceX++) {
				final int maskColor = mask.get(sourceX, sourceY);
				if (maskColor > 0) {
					final int sourceColor = sourceProcessor.get(sourceX, sourceY);
					targetProcessor.set(targetX, targetY, sourceColor);
				}
				targetX++;
			}
			targetY++;
		}
	}
}
