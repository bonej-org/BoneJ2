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

import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.Views;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.joml.Vector3d;

/**
 * A class containing utility methods for the ImageJ RoiManager
 *
 * @author Michael Doube
 * @author Richard Domander
 */
public final class RoiManagerUtil {
	private RoiManagerUtil() {}

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
		final Roi[] rois = manager.getRoisAsArray();
		return Arrays.stream(rois).filter(roi -> roi.getType() == Roi.POINT).map(
				roi -> {
					final int z = roi.getZPosition();
					return Arrays.stream(roi.getContainedPoints()).distinct().map(
							p -> new Vector3d(p.x, p.y, z));
				}).flatMap(s -> s).distinct().collect(Collectors.toList());
	}

	/**
	 * Determine whether the ROI Manager is not active or has no entries
	 * 
	 * @return true if there are no ROIs to process
	 */
	public static boolean roiManagerIsEmpty() {
		final RoiManager rm = RoiManager.getInstance2();
		//if there are no ROIs or no ROI Manager
		return (rm == null || rm.getCount() == 0);
	}

	/**
	 * Build a 2D union mask (BitType) for the given XY view and (z,t,c) plane using ROIs from the
	 * (visible) IJ1 RoiManager. The returned mask is aligned to the view's interval.
	 *
	 * @param xyView 2D view (may have non-zero min).
	 * @param z1 1-based Z position (IJ1 convention)
	 * @param t1 1-based T position (IJ1 convention)
	 * @param c1 1-based C position (IJ1 convention)
	 * @return a BitType mask that represents all the ROIs active on this XY slice or null if the ROI
	 * Manager is null or empty, or if there are no ROIs active on this XY slice.
	 */
	public static RandomAccessibleInterval<BitType> unionMaskFromRoiManager(
			final RandomAccessibleInterval<?> xyView,
			final int z1,
			final int t1,
			final int c1
			) {
		
		//if there are no ROIs or no ROI Manager
		if (roiManagerIsEmpty()) {
			return null;
		}
		
		final RoiManager rm = RoiManager.getInstance2();

		final Roi[] all = rm.getRoisAsArray();
		final List<Roi> rois = new ArrayList<>();
		for (final Roi r : all) {
			if (matchesPlane(r, z1, t1, c1)) rois.add(r);
		}
		if (rois.isEmpty()) {
			return null;
		}

		// View geometry
		final long minX = xyView.min(0);
		final long minY = xyView.min(1);
		final int w = (int) xyView.dimension(0);
		final int h = (int) xyView.dimension(1);

		// Bit mask aligned to xyView interval
		final RandomAccessibleInterval<BitType> mask =
				ArrayImgs.bits(w, h); // min at (0,0) for now
		final RandomAccessibleInterval<BitType> alignedMask =
				Views.translate(mask, minX, minY); // now mask coords match xyView coords

		// Fill union by OR-ing each ROI's raster mask into alignedMask
		for (final Roi roi : rois) {
			orRoiIntoMask(roi, alignedMask);
		}

		return alignedMask;
	}

	private static boolean matchesPlane(final Roi roi, final int z1, final int t1, final int c1) {
		final int rz = roi.getZPosition();
		final int rt = roi.getTPosition();
		final int rc = roi.getCPosition();
		return (rz == 0 || rz == z1) && (rt == 0 || rt == t1) && (rc == 0 || rc == c1);
	}

	/**
	 * Create a mask that matches this view in size and position and which has all true (1) values
	 * 
	 * @param xyView
	 * @return an all-true mask that matches the given xyView
	 */
	private static RandomAccessibleInterval<BitType> fullMaskLike(final RandomAccessibleInterval<?> xyView) {
		final int w = (int) xyView.dimension(0);
		final int h = (int) xyView.dimension(1);
		final long minX = xyView.min(0);
		final long minY = xyView.min(1);
		final RandomAccessibleInterval<BitType> m = ArrayImgs.bits(w, h);
		final Cursor<BitType> cursor = Views.flatIterable(m).cursor();
		while (cursor.hasNext()) {
			cursor.next().set(true);
		}
		return Views.translate(m, minX, minY);	
	}

	private static RandomAccessibleInterval<BitType> emptyMaskLike(final RandomAccessibleInterval<?> xyView) {
		final int w = (int) xyView.dimension(0);
		final int h = (int) xyView.dimension(1);
		final long minX = xyView.min(0);
		final long minY = xyView.min(1);
		final RandomAccessibleInterval<BitType> m = ArrayImgs.bits(w, h);
		return Views.translate(m, minX, minY);	
	}

	/**
	 * OR a single IJ1 Roi into an ImgLib2 BitType mask.
	 * The mask must be in the same pixel coordinate system as the Roi (typically image coordinates).
	 */
	private static void orRoiIntoMask(final Roi roi, final RandomAccessibleInterval<BitType> mask) {
		final Rectangle b = roi.getBounds();         // ROI bounds in image pixel coords
		final ImageProcessor ipMask = roi.getMask(); // ROI-local mask (may be null for rectangle ROIs)

		// Intersect bounds with mask interval to stay safe
		final long x0 = Math.max(b.x, mask.min(0));
		final long y0 = Math.max(b.y, mask.min(1));
		final long x1 = Math.min(b.x + b.width  - 1L, mask.max(0));
		final long y1 = Math.min(b.y + b.height - 1L, mask.max(1));
		if (x1 < x0 || y1 < y0) return;

		if (ipMask == null) {
			// Rectangle ROI: set all pixels in intersected bounds
			final RandomAccessibleInterval<BitType> view = Views.interval(mask, new FinalInterval(new long[]{x0, y0}, new long[]{x1, y1}));
			for (final BitType bt : Views.flatIterable(view)) bt.setOne();
			return;
		}

		// Non-rectangular ROI: use ROI-local mask pixels and copy into global mask with OR semantics.
		// ipMask is in ROI-local coordinates: (0..b.width-1, 0..b.height-1)
		final ImageProcessor byteMask = ipMask.convertToByte(false);
		final byte[] mp = (byte[]) byteMask.getPixels();
		final int mw = byteMask.getWidth();

		for (long yy = y0; yy <= y1; yy++) {
			final int my = (int) (yy - b.y);
			final int mRow = my * mw;

			for (long xx = x0; xx <= x1; xx++) {
				final int mx = (int) (xx - b.x);
				if ((mp[mRow + mx] & 0xff) != 0) {
					mask.randomAccess().setPositionAndGet(xx, 0); // can't do two dims at once
					// Use a small RA helper for clarity/efficiency:
					final RandomAccess<BitType> ra = mask.randomAccess();
					ra.setPosition(xx, 0);
					ra.setPosition(yy, 1);
					ra.get().setOne();
				}
			}
		}
	}
}
