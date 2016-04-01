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
import java.util.Collections;
import java.util.Vector;

//image data
import org.doube.bonej.pqct.io.ImageAndAnalysisDetails;
import org.doube.bonej.pqct.io.ScaledImageData;

//ImagePlus
import ij.ImagePlus;
//ImagePlus ROI
import ij.gui.Roi;

@SuppressWarnings(value = { "serial", "unchecked" }) // Unchecked for obtaining
														// Vector<Object> as a
														// returnvalue

public class SelectSoftROI extends RoiSelector {
	// ImageJ constructor
	public SelectSoftROI(final ScaledImageData dataIn, final ImageAndAnalysisDetails detailsIn, final ImagePlus imp,
			final double boneThreshold, final boolean setRoi) {
		super(dataIn, detailsIn, imp, boneThreshold, setRoi);
		// Soft tissue analysis
		softSieve = null;
		byte[] softResult = null;
		if (details.stOn) {

			/* Get rid of measurement tube used at the UKK institute */
			byte[] sleeve = null;
			if (details.sleeveOn) {
				sleeve = removeSleeve(softScaledImage, sleeve, 25.0);
				int removed = 0;
				for (int ii = 0; ii < width * height; ii++) {
					if (sleeve[ii] == 1) {
						softScaledImage[ii] = minimum;
						++removed;
					}
				}
			}

			/**
			 * Ignore data outside manually selected ROI, if manualRoi has been
			 * selected
			 */
			final Roi ijROI = imp.getRoi();
			final double[] tempScaledImage = softScaledImage.clone();
			if (ijROI != null
					&& details.manualRoi) { /*
											 * Set pixels outside the manually
											 * selected ROI to zero
											 */
				/*
				 * Check whether pixel is within ROI, mark with bone threshold
				 */
				for (int j = 0; j < height; j++) {
					for (int i = 0; i < width; i++) {
						if (ijROI.contains(i, j)) {
						} else {
							softScaledImage[i + j * width] = minimum;
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
						softScaledImage[polygon.xpoints[j]
								+ polygon.ypoints[j] * width] = tempScaledImage[polygon.xpoints[j]
										+ polygon.ypoints[j] * width];
					}
				}
			}

			final Vector<Object> masks = getSieve(softScaledImage, airThreshold, details.roiChoiceSt,
					details.guessStacked, details.stacked, false, true);
			softSieve = (byte[]) masks.get(0);
			softResult = (byte[]) masks.get(1);
			final Vector<DetectedEdge> stEdges = (Vector<DetectedEdge>) masks.get(2);

			/*
			 * Erode three layers of pixels from the fat sieve to get rid of
			 * higher density layer (i.e. skin) on top of fat to enable finding
			 * muscle border
			 */
			byte[] muscleSieve = softSieve.clone();
			final double[] muscleImage = softScaledImage.clone();
			byte[] subCutaneousFat = null;
			for (int i = 0; i < 3; ++i) {
				muscleSieve = erode(muscleSieve);
				if (i == 0) {
					/*
					 * Add subcut fat sieve... Skin has already been removed by
					 * eroding one layer of pixels-> remove muscle later on
					 */
					subCutaneousFat = muscleSieve.clone();
				}
			}

			/* Remove everything other than the selected limb from the image */
			for (int i = 0; i < muscleSieve.length; ++i) {
				if (muscleSieve[i] < 1) {
					muscleImage[i] = minimum;
				}
			}
			/* Look for muscle outline */
			final Vector<Object> muscleMasks = getSieve(muscleImage, details.muscleThreshold, "Bigger",
					details.guessStacked, details.stacked, false, false);
			// muscleSieve = (byte[]) muscleMasks.get(0);
			final Vector<DetectedEdge> muscleEdges = (Vector<DetectedEdge>) muscleMasks.get(2);
			Collections.sort(muscleEdges, Collections.reverseOrder());
			int tempMuscleArea = 0;
			muscleSieve = new byte[softSieve.length];
			int areaToAdd = 0;
			/*
			 * Include areas that contribute more than 1% on top of what is
			 * already included
			 */
			while (areaToAdd < muscleEdges.size() && tempMuscleArea * 0.01 < muscleEdges.get(areaToAdd).area) {
				final byte[] tempMuscleSieve = fillSieve(muscleEdges.get(areaToAdd).iit,
						muscleEdges.get(areaToAdd).jiit, width, height, muscleImage, details.muscleThreshold);
				for (int i = 0; i < tempMuscleSieve.length; ++i) {
					if (tempMuscleSieve[i] > 0) {
						muscleSieve[i] = tempMuscleSieve[i];
					}
				}
				tempMuscleArea += muscleEdges.get(areaToAdd).area;
				areaToAdd++;
			}

			// muscleSieve = (byte[]) muscleMasks.get(1); /*Use all areas
			// encircled as muscle (needed if there's fat between muscles)!!*/

			/* Wipe muscle area +1 layer of pixels away from subcut. */
			final byte[] tempMuscleSieve = muscleSieve.clone();
			dilate(tempMuscleSieve, (byte) 1, (byte) 0, (byte) 2);
			for (int i = 0; i < tempMuscleSieve.length; ++i) {
				if (tempMuscleSieve[i] == 1) {
					subCutaneousFat[i] = 0;
				}
			}
			/* create temp boneResult to wipe out bone and marrow */
			final Vector<Object> masks2 = getSieve(softScaledImage, softThreshold, details.roiChoiceSt,
					details.guessStacked, details.stacked, false, false);
			final byte[] boneResult = (byte[]) masks2.get(1);
			for (int i = 0; i < softSieve.length; ++i) {
				if (softSieve[i] == 1 && softScaledImage[i] >= airThreshold && softScaledImage[i] < fatThreshold) {
					softSieve[i] = 2; // Fat
				}
				if (muscleSieve[i] == 1 && softScaledImage[i] >= muscleThreshold
						&& softScaledImage[i] < softThreshold) {
					softSieve[i] = 3; // Muscle
				}
				if (muscleSieve[i] == 1 && softScaledImage[i] >= airThreshold && softScaledImage[i] < muscleThreshold) {
					softSieve[i] = 4; // Intra/Intermuscular fat
				}
				if (subCutaneousFat[i] == 1) {
					softSieve[i] = 5; // Subcut fat
				}
				if (boneResult[i] == 1) {
					softSieve[i] = 6; // Bone & marrow
				}
			}
		}
	}
}
