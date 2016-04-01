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
import java.util.Vector;

import org.doube.bonej.pqct.io.ImageAndAnalysisDetails;
//ROI selection..
import org.doube.bonej.pqct.selectroi.SelectROI;

public class MassDistribution {

	// constants
	public double sectorWidth;
	public double minimum;
	public double maximum;
	public double pixelSpacing;
	public int height;
	public int width;
	public double[] boneCenter;
	double[] BMC;

	// Variables for moment calculations
	public double rotationCorrection;
	public double alfa;
	public Vector<Integer> pind;

	// Mass distribution variables
	public double[] BMCs;
	SelectROI roi;
	ImageAndAnalysisDetails details;

	public MassDistribution(final SelectROI roi, final ImageAndAnalysisDetails details,
			final DetermineAlfa determineAlfa) {
		this.pind = determineAlfa.pind;
		this.roi = roi;
		this.details = details;
		alfa = determineAlfa.alfa;
		rotationCorrection = determineAlfa.rotationCorrection;
		sectorWidth = details.sectorWidth;
		minimum = roi.minimum;
		maximum = roi.maximum;
		height = roi.height;
		width = roi.width;
		pixelSpacing = roi.pixelSpacing;
		boneCenter = new double[2];
		int points = 0;
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				if (roi.sieve[i + j * width] > 0) {
					boneCenter[0] += i;
					boneCenter[1] += j;
					++points;
				}
			}
		}
		boneCenter[0] /= points;
		boneCenter[1] /= points;
		calculateDistribution();
		rotateResults(details, roi);
	}

	void calculateDistribution() {
		// Calculate radii in polar coordinate system originating from bone
		// marrow center of mass
		BMC = new double[360];
		int et;
		final double rIncrement = 0.1;
		for (et = 0; et < 360; et++) { // Finding endocortical and pericortical
										// borders uMath.sing polar coordinates
			final double Theta = Math.PI / 180.0 * et;
			BMC[et] = 0;
			double R = 0;
			while (roi.sieve[(int) (boneCenter[0] + R * Math.cos(Theta))
					+ ((int) ((boneCenter[1] + R * Math.sin(Theta))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 0.5) * Math.cos(Theta))
							+ ((int) ((boneCenter[1] + (R + 0.5) * Math.sin(Theta))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 1) * Math.cos(Theta))
							+ ((int) ((boneCenter[1] + (R + 1) * Math.sin(Theta))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 2) * Math.cos(Theta))
							+ ((int) ((boneCenter[1] + (R + 2) * Math.sin(Theta))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 3) * Math.cos(Theta))
							+ ((int) ((boneCenter[1] + (R + 3) * Math.sin(Theta))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 4) * Math.cos(Theta))
							+ ((int) ((boneCenter[1] + (R + 4) * Math.sin(Theta))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 6) * Math.cos(Theta))
							+ ((int) ((boneCenter[1] + (R + 6) * Math.sin(Theta))) * width)] > 0) {
				// Calculate BMC rho*dV, dV=dA*slice_thickness
				// dA=pi*((R(et)*resolution)^2-((R(et)-0.1)*resolution)^2),
				// slice_thickness = 1 mm
				// (could be set to actual slice thickness, but makes no
				// difference for comparisons -> 1 mm is used BMD divided by
				// 1000, because unit is mg/cm3 and area is mm2
				final double tempBMD = roi.scaledImage[(int) (boneCenter[0] + R * Math.cos(Theta))
						+ ((int) ((boneCenter[1] + R * Math.sin(Theta))) * width)];
				BMC[et] += tempBMD / 1000.0 * Math.PI / 360.0 * ((R * roi.pixelSpacing) * (R * roi.pixelSpacing)
						- ((R - rIncrement) * roi.pixelSpacing) * ((R - rIncrement) * roi.pixelSpacing));
				R += rIncrement;
			}
		}
	}

	void rotateResults(final ImageAndAnalysisDetails details, final SelectROI roi) {
		BMCs = new double[(int) (360 / sectorWidth)];
		// Calculate the division and sector values of vBMD
		for (int pp = 0; pp < (int) (360 / sectorWidth); pp++) {
			for (int dd = 0; dd < (int) sectorWidth; dd++) {
				BMCs[pp] += BMC[pind.get((int) (pp * sectorWidth + dd))];
			}
		}

	}
}
