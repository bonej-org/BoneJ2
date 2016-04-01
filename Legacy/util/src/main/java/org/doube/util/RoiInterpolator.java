/*
 * #%L
 * BoneJ: open source tools for trabecular geometry and whole bone shape analysis.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers. See also individual class @authors.
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
package org.doube.util;

import java.awt.Rectangle;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import vib.BinaryInterpolator;

public class RoiInterpolator implements PlugIn {
	int[][] idt;
	int w, h;

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment())
			return;
		final RoiManager roiman = RoiManager.getInstance();
		if (roiman == null || roiman.getCount() < 2) {
			IJ.error("Please populate the ROI Manager with multiple ROIs");
			return;
		}
		final Roi[] rois = roiman.getRoisAsArray();
		int xmax = 0;
		int xmin = Integer.MAX_VALUE;
		int ymax = 0;
		int ymin = Integer.MAX_VALUE;
		int zmax = 1;
		int zmin = Integer.MAX_VALUE;
		final ArrayList<Integer> templateSlices = new ArrayList<Integer>();
		for (final Roi roi : rois) {
			final int slice = roiman.getSliceNumber(roi.getName());
			if (!templateSlices.contains(new Integer(slice)))
				templateSlices.add(new Integer(slice));
			if (slice == 0) // ignore non-slice associated ROIs
				continue;
			zmin = Math.min(slice, zmin);
			zmax = Math.max(slice, zmax);
			final Rectangle bounds = roi.getBounds();
			xmin = Math.min(xmin, bounds.x);
			ymin = Math.min(ymin, bounds.y);
			xmax = Math.max(xmax, bounds.x + bounds.width);
			ymax = Math.max(ymax, bounds.y + bounds.height);
		}
		if (templateSlices.size() < 2) {
			IJ.error("ROIs are all on the same slice, nothing to interpolate");
			return;
		}
		// create the binary stack
		final int stackW = xmax - xmin + 1;
		final int stackH = ymax - ymin + 1;
		final int nSlices = zmax - zmin + 1;
		final ImageStack stack = new ImageStack(stackW, stackH);
		for (int s = 0; s < nSlices; s++) {
			final ByteProcessor bp = new ByteProcessor(stackW, stackH);
			bp.setColor(255);
			for (final Roi roi : rois) {
				final int slice = roiman.getSliceNumber(roi.getName());
				if (slice == zmin + s) {
					final Rectangle bounds = roi.getBounds();
					roi.setLocation(bounds.x - xmin, bounds.y - ymin);
					bp.setRoi(roi);
					if (roi.getType() == Roi.RECTANGLE)
						bp.fill();
					else
						bp.fill(roi);
				}
			}
			stack.addSlice("" + s, bp);
		}
		// do the binary interpolation
		final BinaryInterpolator bi = new BinaryInterpolator();
		bi.run(stack);
		final ImagePlus binary = new ImagePlus("interpolated", stack);

		// get the ROIs
		final ThresholdToSelection ts = new ThresholdToSelection();
		ts.setup("", binary);
		for (int s = 0; s < nSlices; s++) {
			if (templateSlices.contains(new Integer(s + zmin)))
				continue;
			final ImageProcessor bp = stack.getProcessor(s + 1);
			final int threshold = 255;
			bp.setThreshold(threshold, threshold, ImageProcessor.NO_LUT_UPDATE);
			final Roi roi = ts.convert(bp);
			roi.setPosition(s + zmin);
			final Rectangle bounds = roi.getBounds();
			roi.setLocation(bounds.x + xmin, bounds.y + ymin);
			roiman.addRoi(roi);
		}
		for (final Roi roi : rois) {
			final Rectangle bounds = roi.getBounds();
			roi.setLocation(bounds.x + xmin, bounds.y + ymin);
		}
		IJ.showStatus("ROIs interpolated");
		UsageReporter.reportEvent(this).send();
	}
}
