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

package org.doube.bonej.pqct.selectroi;

//Polygon, Rectangle
import java.awt.Polygon;
//Vector, Collections
import java.util.Vector;

//image data
import org.doube.bonej.pqct.io.ImageAndAnalysisDetails;
import org.doube.bonej.pqct.io.ScaledImageData;

//ImagePlus
import ij.IJ;
import ij.ImagePlus;
//ImagePlus ROI
import ij.gui.PolygonRoi;
import ij.gui.Roi;

@SuppressWarnings(value = { "serial", "unchecked" }) // Unchecked for obtaining
														// Vector<Object> as a
														// returnvalue

public class SelectROI extends RoiSelector {
	public Vector<DetectedEdge> edges;

	// ImageJ constructor
	public SelectROI(final ScaledImageData dataIn, final ImageAndAnalysisDetails detailsIn, final ImagePlus imp,
			final double boneThreshold, final boolean setRoi) {
		super(dataIn, detailsIn, imp, boneThreshold, setRoi);
		// Select ROI

		/* Select ROI and set everything else than the roi to minimum */
		cortexROI = new double[width * height]; // Make a new copy of the image
												// with only the ROI remaining
		cortexRoiI = new Vector<Integer>();
		cortexRoiJ = new Vector<Integer>();
		cortexAreaRoiI = new Vector<Integer>();
		cortexAreaRoiJ = new Vector<Integer>();
		boneMarrowRoiI = new Vector<Integer>();
		boneMarrowRoiJ = new Vector<Integer>();
		Roi ijROI = imp.getRoi();
		final double[] tempScaledImage = scaledImage.clone();
		if (ijROI != null
				&& details.manualRoi) { /*
										 * Set pixels outside the manually
										 * selected ROI to zero
										 */
			/* Check whether pixel is within ROI, mark with bone threshold */
			for (int j = 0; j < height; j++) {
				for (int i = 0; i < width; i++) {
					if (ijROI.contains(i, j)) {
					} else {
						tempScaledImage[i + j * width] = minimum;
					}
				}
			}
			/*
			 * Check whether a polygon can be acquired and include polygon
			 * points too
			 */
			final Polygon polygon = ijROI.getPolygon();
			if (polygon != null) {
				for (int j = 0; j < polygon.npoints; j++) {
					tempScaledImage[polygon.xpoints[j] + polygon.ypoints[j] * width] = scaledImage[polygon.xpoints[j]
							+ polygon.ypoints[j] * width];
				}
			}
		}

		final Vector<Object> boneMasks = getSieve(tempScaledImage, boneThreshold, details.roiChoice,
				details.guessStacked, details.stacked, details.guessFlip, details.allowCleaving);
		sieve = (byte[]) boneMasks.get(0);
		result = (byte[]) boneMasks.get(1);
		final Vector<DetectedEdge> boneEdges = (Vector<DetectedEdge>) boneMasks.get(2);
		selection = (Integer) boneMasks.get(3);
		/* Add the roi to the image */
		if (setRoi) {
			final int[] xcoordinates = new int[boneEdges.get(selection).iit.size()];
			final int[] ycoordinates = new int[boneEdges.get(selection).iit.size()];
			for (int i = 0; i < boneEdges.get(selection).iit.size(); ++i) {
				xcoordinates[i] = boneEdges.get(selection).iit.get(i);
				ycoordinates[i] = boneEdges.get(selection).jiit.get(i);
			}
			/*
			 * Flip the original image prior to adding the ROI, if scaled image
			 * is flipped
			 */
			if ((details.flipHorizontal || details.flipVertical) && imp.getRoi() != null) {
				IJ.run(imp, "Select None", ""); // Remove existing ROIs in order
												// to flip the whole image...
			}
			if (details.flipHorizontal) {
				imp.getProcessor().flipVertical();
				imp.updateAndDraw();
			}
			if (details.flipVertical) {
				imp.getProcessor().flipHorizontal();
				imp.updateAndDraw();
			}
			ijROI = new PolygonRoi(xcoordinates, ycoordinates, boneEdges.get(selection).iit.size(), Roi.POLYGON);
			imp.setRoi(ijROI);
		}

		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				if (scaledImage[i + j * width] < areaThreshold && sieve[i + j * width] > 0) {
					boneMarrowRoiI.add(i);
					boneMarrowRoiJ.add(j);
				}
				if (scaledImage[i + j * width] >= areaThreshold && sieve[i + j * width] > 0) {
					cortexAreaRoiI.add(i);
					cortexAreaRoiJ.add(j);
				}
				if (scaledImage[i + j * width] >= BMDthreshold && sieve[i + j * width] > 0) {
					cortexROI[i + j * width] = scaledImage[i + j * width];
					cortexRoiI.add(i);
					cortexRoiJ.add(j);
				} else {
					cortexROI[i + j * width] = minimum;
				}
			}
		}
		edges = boneEdges;
	}
}
