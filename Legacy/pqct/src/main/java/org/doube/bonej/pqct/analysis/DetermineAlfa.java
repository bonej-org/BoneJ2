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

package org.doube.bonej.pqct.analysis;

//Vector, Collections
import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;

import org.doube.bonej.pqct.io.ImageAndAnalysisDetails;
//ROI selection..
import org.doube.bonej.pqct.selectroi.SelectROI;

public class DetermineAlfa {
	public int rotationIndex;
	public double alfa = 0;
	public double rotationCorrection = 0;
	public double distanceBetweenBones = 0;
	public Vector<Integer> pind;
	public Vector<Integer> pindColor;
	ImageAndAnalysisDetails details;

	public DetermineAlfa(final SelectROI roi, final ImageAndAnalysisDetails details) {
		this.details = details;
		// Calculate CSMIs and rotation angle to align maximal and minimal
		// bending axes with X and Y axes
		rotationCorrection = ((details.sectorWidth) / 2.0);
		/*
		 * Rotation according to Imax/Imin for bone of interest or according to
		 * all bones
		 */
		if (details.rotationChoice.equals(details.rotationLabels[0])
				|| details.rotationChoice.equals(details.rotationLabels[2])) {
			double[] csmiValues = new double[3];
			if (details.rotationChoice.equals(details.rotationLabels[0])) {
				csmiValues = csmi(roi.sieve, roi.width, roi.height);
			}
			if (details.rotationChoice.equals(details.rotationLabels[2])) {
				final byte[] tempCsmiSieve = new byte[roi.width * roi.height];
				for (int j = 0; j < roi.height; j++) {
					for (int i = 0; i < roi.width; i++) {
						if (roi.scaledImage[i + j * roi.width] >= details.rotationThreshold) {
							tempCsmiSieve[i + j * roi.width] = 1;
						} else {
							tempCsmiSieve[i + j * roi.width] = 0;
						}
					}
				}
				csmiValues = csmi(tempCsmiSieve, roi.width, roi.height);
			}
			final double xmax = csmiValues[0];
			final double ymax = csmiValues[1];
			final double moment = csmiValues[2];
			double vali1, vali2;
			// Calculate rotation required to align rotation axes
			if (ymax == xmax) {
				alfa = 0; // check that xmax does not equal ymax (can't divide
							// with 0...
			} else {
				alfa = Math.atan(2.0 * moment / (ymax - xmax)) / 2.0;
				// Calculate the maximal and minimial cross-sectional moments of
				// inertia
				vali1 = (ymax + xmax) / 2 + (ymax - xmax) / 2 * Math.cos(2 * (-alfa)) - moment * Math.sin(2 * (-alfa));
				vali2 = (ymax + xmax) / 2 - (ymax - xmax) / 2 * Math.cos(2 * (-alfa)) + moment * Math.sin(2 * (-alfa));
				// The according to Imax/Imin alfa may align rotation axis
				// corresponding to maximal CSMI with either horizontal
				// or vertical axis, whichever rotation is smaller...
				// Always rotate towards horizontal axis... maximal bending axis
				// will be aligned with horizontal axis
				// Note that e.g. tibial mid-shaft rotation is completely
				// different if only tibia or if both tibia and fibula
				// are consireder!!!
				if (vali1 > vali2) {
					if (alfa < 0) {
						alfa = Math.PI / 2.0 + alfa;
					} else {
						alfa = alfa - Math.PI / 2.0;
					}
				}

			}
		}

		/* Rotation according to the furthest point */
		if (details.rotationChoice.equals(details.rotationLabels[1])) {
			/* Calculate alfa from periosteal radii */
			final double[] marrowCenter = new double[2];
			for (int i = 0; i < roi.boneMarrowRoiI.size(); i++) {
				marrowCenter[0] += (double) roi.boneMarrowRoiI.get(i);
				marrowCenter[1] += (double) roi.boneMarrowRoiJ.get(i);
			}
			marrowCenter[0] /= roi.boneMarrowRoiI.size();
			marrowCenter[1] /= roi.boneMarrowRoiJ.size();
			final double[] radii = new double[roi.edges.get(roi.selection).length];
			for (int i = 0; i < roi.edges.get(roi.selection).length; ++i) {
				radii[i] = Math.sqrt(Math.pow(roi.edges.get(roi.selection).iit.get(i) - marrowCenter[0], 2)
						+ Math.pow(roi.edges.get(roi.selection).jiit.get(i) - marrowCenter[1], 2));
			}
			final double[] sumRadii = new double[radii.length];
			for (int i = 5; i < radii.length - 6; ++i) {
				for (int j = -5; j < 6; ++j) {
					sumRadii[i] += radii[i + j];
				}
			}
			final double[] sortRadii = sumRadii.clone();
			Arrays.sort(sortRadii);
			int largest = 0;
			while (sumRadii[largest] != sortRadii[sortRadii.length - 1]) {
				++largest;
			}
			double x, y;
			x = roi.edges.get(roi.selection).iit.get(largest) - marrowCenter[0];
			y = roi.edges.get(roi.selection).jiit.get(largest) - marrowCenter[1];
			alfa = Math.PI - Math.atan2(y, x);
		}

		/* Rotate unselected bone to right */
		if (details.rotationChoice.equals(details.rotationLabels[3])
				|| details.rotationChoice.equals(details.rotationLabels[4])) {
			/* Create temp roi for rotating using rotationThreshold.. */
			final SelectROI tempRoi = new SelectROI(roi.scaledImageData, roi.details, roi.imp,
					details.rotationThreshold, false);
			/*
			 * Find the second biggest bone (could be bigger than the selected
			 * roi...
			 */
			final int[] twoBones = tempRoi.twoLargestBonesDetectedEdges(tempRoi.edges);
			int otherBoneSelection = 0;
			if (tempRoi.selection == twoBones[0]) {
				otherBoneSelection = twoBones[1];
			} else {
				otherBoneSelection = twoBones[0];
			}
			/* Fill a sieve with a second bone and acquire coordinates... */
			final Vector<Integer> sRoiI = tempRoi.edges.get(otherBoneSelection).iit;
			final Vector<Integer> sRoiJ = tempRoi.edges.get(otherBoneSelection).jiit;

			final byte[] secondBoneSieve = tempRoi.fillSieve(sRoiI, sRoiJ, tempRoi.width, tempRoi.height,
					tempRoi.scaledImage, details.rotationThreshold);

			final double[] selectedBoneCenter = calculateCenter(tempRoi.sieve, tempRoi.width,
					tempRoi.height); /* Calculate selected bone centre */
			final double[] otherBoneCenter = calculateCenter(secondBoneSieve, tempRoi.width,
					tempRoi.height); /* Calculate other bone centre */
			double x = 0;
			double y = 0;
			// IJ.log(selectedBoneCenter[0]+" "+selectedBoneCenter[1]+"
			// "+otherBoneCenter[0]+" "+otherBoneCenter[1]);
			/* Rotate unselected bone to right */
			if (details.rotationChoice.equals(details.rotationLabels[3])) {
				x = otherBoneCenter[0] - selectedBoneCenter[0]; // Use the
																// selected bone
																// as origin for
																// rotation
				y = otherBoneCenter[1] - selectedBoneCenter[1]; // Use the
																// selected bone
																// as origin for
																// rotation
			}
			/* Rotate selected bone to right */
			if (details.rotationChoice.equals(details.rotationLabels[4])) {
				x = selectedBoneCenter[0] - otherBoneCenter[0]; // Use the other
																// bone as
																// origin for
																// rotation
				y = selectedBoneCenter[1] - otherBoneCenter[1]; // Use the other
																// bone as
																// origin for
																// rotation
			}
			alfa = -Math.atan2(y, x);
			distanceBetweenBones = Math.sqrt(Math.pow(x, 2.0) + Math.pow(y, 2.0)) * roi.pixelSpacing;
			// IJ.error(twoBones[0]+" "+twoBones[1]+" "+otherBoneSelection+"
			// "+tempRoi.selection+" "+alfa*180.0/Math.PI+"
			// "+tempRoi.length.size());
		}

		/* Manual rotation */
		if (details.manualRotation) {
			alfa = details.manualAlfa;
		}

		/* Flip distribution */
		if (details.flipDistribution) {
			rotationCorrection = -rotationCorrection;
		}

		rotationIndex = (int) (alfa / Math.PI * 180.0 + rotationCorrection);

		// Calculate CSMIs and rotation angle to align maximal and minimal
		// bending axes with X and Y axes
		pind = rotateIndex(rotationIndex);

		if (details.flipDistribution) {
			pindColor = rotateIndex((rotationIndex));
		} else {
			pindColor = rotateIndex(-rotationIndex);
		}

	}

	double[] calculateCenter(final byte[] sieve, final int width, final int height) {
		final double[] originBone = new double[3];
		for (int j = 0; j < height; ++j) {
			for (int i = 0; i < width; ++i) {
				if (sieve[i + j * width] > 0) {
					originBone[0] += i;
					originBone[1] += j;
					originBone[2] += 1;
				}
			}
		}
		originBone[0] /= originBone[2];
		originBone[1] /= originBone[2];
		return originBone;
	}

	Vector<Integer> rotateIndex(final int rotationAngle) {
		int initialIndex = 0;
		final Vector<Integer> rotateIndexVector = new Vector<Integer>();
		if (rotationAngle >= 0) {
			initialIndex = 360 - rotationAngle;
		} else {
			initialIndex = -rotationAngle;
		}
		int inde;
		inde = initialIndex;
		while (inde < 360) {
			rotateIndexVector.add(inde);
			++inde;
		}
		inde = 0;
		while (inde < initialIndex) {
			rotateIndexVector.add(inde);
			++inde;
		}

		/* Flip rotateIndexVector, for e.g. comparing left to right */
		if (details.flipDistribution) {
			Collections.reverse(rotateIndexVector);
		}
		return rotateIndexVector;
	}

	double[] csmi(final byte[] sieve, final int width, final int height) {
		final double[] cortexCenter = new double[2];
		int points = 0;
		final Vector<Integer> bmcI = new Vector<Integer>();
		final Vector<Integer> bmcJ = new Vector<Integer>();
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				if (sieve[i + j * width] > 0) {
					cortexCenter[0] += i;
					cortexCenter[1] += j;
					bmcI.add(i);
					bmcJ.add(j);
					++points;
				}
			}
		}
		cortexCenter[0] /= points;
		cortexCenter[1] /= points;

		final double[] returnValues = new double[3];
		for (int i = 0; i < returnValues.length; ++i) {
			returnValues[i] = 0;
		}
		// Calculating cross-sectional moment of inertia in the original image
		// orientation
		for (int i = 0; i < bmcI.size(); i++) {
			returnValues[0] += ((bmcI.get(i) - cortexCenter[0])) * ((bmcI.get(i) - cortexCenter[0]));
			returnValues[1] += ((bmcJ.get(i) - cortexCenter[1])) * ((bmcJ.get(i) - cortexCenter[1]));
			returnValues[2] += ((bmcI.get(i) - cortexCenter[0])) * ((bmcJ.get(i) - cortexCenter[1]));
		}
		return returnValues;
	}

}
