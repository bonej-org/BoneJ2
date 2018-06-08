
package org.bonej.utilities;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.scijava.vecmath.Vector3d;

import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

/**
 * A class containing utility methods for the ImageJ RoiManager
 *
 * @author Michael Doube
 * @author Richard Domander
 */
public final class RoiManagerUtil {

	private static final int FIRST_SLICE_NUMBER = 1;
	private static final int NO_SLICE_NUMBER = -1;

	private RoiManagerUtil() {}

	// region -- Utility methods --

	/**
	 * Cropping the stack without any padding.
	 *
	 * @see #cropToRois(RoiManager, ImageStack, boolean, int, int)
	 * @param roiMan the manager containing the ROIs.
	 * @param sourceStack the image to be cropped.
	 * @param fillBackground if true, fill the background of the cropped image.
	 * @param fillColor color of the background of the cropped image.
	 * @return an Optional with the cropped stack of the given image. The Optional
	 *         is empty if roiMan == null, or sourceStack == null, or roiMan is
	 *         empty.
	 */
	public static Optional<ImageStack> cropToRois(final RoiManager roiMan,
		final ImageStack sourceStack, final boolean fillBackground,
		final int fillColor)
	{
		return cropToRois(roiMan, sourceStack, fillBackground, fillColor, 0);
	}

	/**
	 * Checks if a ROI is active on all slices.
	 *
	 * @param sliceNumber the slice number or z-position of the ROI.
	 * @return true if the ROI is not associated with a particular slide.
	 */
	public static boolean isActiveOnAllSlices(final int sliceNumber) {
		return sliceNumber <= 0;
	}

	/**
	 * Gets the coordinates of all point ROIs in the manager.
	 * <p>
	 * NB z-coordinates start from 1. If a ROI is not associated with a slice,
	 * it's z = 0.
	 * </p>
	 *
	 * @param manager an instance of {@link RoiManager}.
	 * @return point ROI coordinates.
	 */
	public static List<Vector3d> pointROICoordinates(final RoiManager manager) {
		if (manager == null) {
			return Collections.emptyList();
		}
		final Roi[] rois = manager.getRoisAsArray();
		// To be completely accurate, we'd have to calculate the centers of the ROIs
		// bounding boxes, but since we're only interested in point ROIs that won't
		// make a huge difference.
		return Arrays.stream(rois).filter(roi -> roi.getType() == Roi.POINT).map(
			roi -> {
				final double x = roi.getXBase();
				final double y = roi.getYBase();
				final int z = roi.getZPosition();
				return new Vector3d(x, y, z);
			}).collect(Collectors.toList());
	}

	// endregion

	// region -- Helper methods --

	private static int clamp(final int value, final int min, final int max) {
		if (Integer.compare(value, min) < 0) {
			return min;
		}
		if (Integer.compare(value, max) > 0) {
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
	 * @param minX Horizontal start of the copy area 0 &lt;= minX &lt; width
	 * @param minY Vertical start of the copy area 0 &lt;= minY &lt; height
	 * @param maxX Horizontal end of the copy area 0 &lt;= maxX &lt;= width
	 * @param maxY Vertical end of the copy area 0 &lt;= maxY &lt;= height
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
	 * Copies the pixels in the given ROI from the source image to the target
	 * image. Copies only those pixels where the color of the given mask &gt; 0.
	 * <p>
	 * NB Calls copyRoi with the given parameters if sourceProcessor.getMask() ==
	 * null.
	 * </p>
	 *
	 * @param sourceProcessor Copy source
	 * @param targetProcessor Copy target
	 * @param minX Horizontal start of the copy area 0 &lt;= minX &lt; width
	 * @param minY Vertical start of the copy area 0 &lt;= minY &lt; height
	 * @param maxX Horizontal end of the copy area 0 &lt;= maxX &lt;= width
	 * @param maxY Vertical end of the copy area 0 &lt;= maxY &lt;= height
	 * @param padding Number pixels added to each side of the copy target
	 */
	private static void copyRoiWithMask(final ImageProcessor sourceProcessor,
		final ImageProcessor targetProcessor, final int minX, final int minY,
		final int maxX, final int maxY, final int padding)
	{
		final ImageProcessor mask = sourceProcessor.getMask();

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

	/**
	 * Copies pixels under all the ROIs on a slide.
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
	 * Returns a list of ROIs that are active in the given slice.
	 *
	 * @param roiMan the collection of all the current ROIs.
	 * @param stack the 3D stack where the ROIs are.
	 * @param sliceNumber number of the slice to be searched.
	 * @return in addition to the active ROIs, returns all the ROIs without a
	 *         slice number (assumed to be active in all slices). Returns an empty
	 *         list if sliceNumber is out of bounds, or roiMan == null, or stack
	 *         == null
	 */
	static List<Roi> getSliceRoi(final RoiManager roiMan, final ImageStack stack,
		final int sliceNumber)
	{
		final List<Roi> roiList = new ArrayList<>();

		if (roiMan == null || stack == null || sliceNumber < FIRST_SLICE_NUMBER ||
			sliceNumber > stack.getSize())
		{
			return roiList;
		}

		final Roi[] rois = roiMan.getRoisAsArray();
		for (final Roi roi : rois) {
			final String roiName = roi.getName();
			if (roiName == null) {
				continue;
			}
			final int roiSliceNumber = roiMan.getSliceNumber(roiName);
			if (roiSliceNumber == sliceNumber || roiSliceNumber == NO_SLICE_NUMBER) {
				roiList.add(roi);
			}
		}
		return roiList;
	}

	/**
	 * Find the x, y and z limits of the stack defined by the ROIs in the ROI
	 * Manager.
	 * <p>
	 * NB If for any ROI isActiveOnAllSlices == true, then z0 == 1 and z1 ==
	 * stack.getSize().
	 * </p>
	 *
	 * @param roiMan the collection of all the current ROIs.
	 * @param stack the stack inside which the ROIs must fit (max limits).
	 * @return returns an Optional with the limits in an int array {x0, x1, y0,
	 *         y1, z0, z1}. Returns an empty Optional if roiMan == null or stack
	 *         == null or roiMan is empty.
	 */
	static Optional<int[]> getLimits(final RoiManager roiMan,
		final ImageStack stack)
	{
		if (roiMan == null || roiMan.getCount() == 0 || stack == null) {
			return Optional.empty();
		}

		final int DEFAULT_Z_MIN = 1;
		final int DEFAULT_Z_MAX = stack.getSize();

		int xMin = stack.getWidth();
		int xMax = 0;
		int yMin = stack.getHeight();
		int yMax = 0;
		int zMin = DEFAULT_Z_MAX;
		int zMax = DEFAULT_Z_MIN;

		final Roi[] rois = roiMan.getRoisAsArray();
		boolean allSlices = false;
		boolean noValidRois = true;

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

			final int sliceNumber = roiMan.getSliceNumber(roi.getName());
			if (sliceNumber >= FIRST_SLICE_NUMBER && sliceNumber <= stack.getSize()) {
				zMin = Math.min(sliceNumber, zMin);
				zMax = Math.max(sliceNumber, zMax);
				noValidRois = false;
			}
			else if (isActiveOnAllSlices(sliceNumber)) {
				allSlices = true;
				noValidRois = false;
			}
		}

		if (noValidRois) {
			return Optional.empty();
		}

		final int[] limits = { xMin, xMax, yMin, yMax, zMin, zMax };

		if (allSlices) {
			limits[4] = DEFAULT_Z_MIN;
			limits[5] = DEFAULT_Z_MAX;
		}

		return Optional.of(limits);
	}

	/**
	 * Crops the given rectangle to the area [0, 0, width, height].
	 *
	 * @param bounds the rectangle to be fitted.
	 * @param width maximum width of the rectangle.
	 * @param height maximum height of the rectangle.
	 * @return false if the height or width of the fitted rectangle is 0 (couldn't
	 *         be cropped inside the area).
	 */
	static boolean getSafeRoiBounds(final Rectangle bounds, final int width,
		final int height)
	{
		final int xMin = clamp(bounds.x, 0, width);
		final int xMax = clamp(bounds.x + bounds.width, 0, width);
		final int yMin = clamp(bounds.y, 0, height);
		final int yMax = clamp(bounds.y + bounds.height, 0, height);
		final int newWidth = xMax - xMin;
		final int newHeight = yMax - yMin;

		bounds.setBounds(xMin, yMin, newWidth, newHeight);

		return newWidth > 0 && newHeight > 0;
	}

	/**
	 * Crop a stack to the limits defined by the ROIs in the ROI Manager and
	 * optionally fill the background with a single pixel value.
	 *
	 * @param roiMan the manager containing the ROIs.
	 * @param sourceStack the image to be cropped.
	 * @param fillBackground if true, fill the background of the cropped image.
	 * @param fillColor color of the background of the cropped image.
	 * @param padding number of pixels added to the each side of the resulting
	 *          image.
	 * @return an Optional with the cropped stack of the given image. The Optional
	 *         is empty if roiMan == null, or sourceStack == null, or roiMan is
	 *         empty.
	 */
	static Optional<ImageStack> cropToRois(final RoiManager roiMan,
		final ImageStack sourceStack, final boolean fillBackground,
		final int fillColor, final int padding)
	{
		if (roiMan == null || sourceStack == null) {
			return Optional.empty();
		}

		final Optional<int[]> optionalLimits = getLimits(roiMan, sourceStack);
		if (!optionalLimits.isPresent()) {
			return Optional.empty();
		}

		final int[] limits = optionalLimits.get();

		final int xMin = limits[0];
		final int xMax = limits[1];
		final int yMin = limits[2];
		final int yMax = limits[3];
		final int zMin = limits[4];
		final int zMax = limits[5];

		final int croppedWidth = xMax - xMin + 2 * padding;
		final int croppedHeight = yMax - yMin + 2 * padding;
		final int croppedDepth = zMax - zMin + 2 * padding + 1;

		final ImageStack targetStack = ImageStack.create(croppedWidth,
			croppedHeight, croppedDepth, sourceStack.getBitDepth());
		int targetZ = padding + 1;

		for (int sourceZ = zMin; sourceZ <= zMax; sourceZ++) {
			final List<Roi> sliceRois = getSliceRoi(roiMan, sourceStack, sourceZ);
			if (sliceRois.isEmpty()) {
				continue;
			}

			final ImageProcessor sourceProcessor = sourceStack.getProcessor(sourceZ);
			final ImageProcessor targetProcessor = targetStack.getProcessor(targetZ);

			if (fillBackground) {
				targetProcessor.setColor(fillColor);
				targetProcessor.fill();
			}

			copySlice(sourceProcessor, targetProcessor, sliceRois, padding);

			targetZ++;
		}

		return Optional.of(targetStack);
	}
	// endregion
}
