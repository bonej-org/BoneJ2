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

//import ij.text.*;		//Debugging text window
public class ConcentricRingAnalysis {

	// constants
	public double sectorWidth;
	public double divisions;
	public double minimum;
	public double maximum;
	public double pixelSpacing;
	public int height;
	public int width;
	public double threshold;

	public double[] boneCenter;

	// Variables for radii calculations
	public double[] Theta;
	public double[] Ru;
	Vector<double[]> BMDj;

	// Variables for moment calculations
	public Vector<Integer> pind;
	public Vector<Integer> pindColor;

	// Density distribution variables
	public double[] pRad;
	public double[] pericorticalRadii;
	public Vector<double[]> BMDs;
	SelectROI roi;

	public ConcentricRingAnalysis(final SelectROI roi, final ImageAndAnalysisDetails details,
			final DetermineAlfa determineAlfa) {
		this.pind = determineAlfa.pind;
		this.pindColor = determineAlfa.pindColor;
		this.roi = roi;
		sectorWidth = details.concentricSector;
		divisions = details.concentricDivisions;
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
		calculateRadii();
		rotateResults(details, roi);
	}

	void rotateResults(final ImageAndAnalysisDetails details, final SelectROI roi) {
		// Bone marrow cortexCenter[0] and cortexCenter[1]

		pRad = new double[360];

		// Calculate the endocortical and pericortical radii along with the
		// corresponding radii after peeling one layer of pixels
		for (int inde = 0; inde < 360; inde++) {
			pRad[inde] = Ru[inde] * pixelSpacing;
		}

		pericorticalRadii = new double[(int) (360 / sectorWidth)];
		BMDs = new Vector<double[]>();
		for (int div = 0; div < divisions; ++div) {
			BMDs.add(new double[(int) (360 / sectorWidth)]);
		}
		int pp;
		int dd;
		// Calculate the division and sector values of vBMD
		for (pp = 0; pp < (int) (360 / sectorWidth); pp++) {
			for (dd = 0; dd < (int) sectorWidth; dd++) {
				pericorticalRadii[pp] += pRad[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
				for (int div = 0; div < divisions; ++div) {
					BMDs.get(div)[pp] += BMDj.get(div)[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
				}
			}
		}

	}

	void calculateRadii() {
		// Calculate radii in polar coordinate system originating from bone
		// marrow center of mass
		Theta = new double[360];
		Ru = new double[360];
		BMDj = new Vector<double[]>();
		for (int i = 0; i < divisions; ++i) {
			BMDj.add(new double[360]);
		}
		final Vector<Double> BMD_temp = new Vector<Double>();
		final double rIncrement = 0.1;
		for (int et = 0; et < 360; ++et) { // Finding endocortical and
											// pericortical borders uMath.sing
											// polar coordinates
			Theta[et] = Math.PI / 180.0 * et;
			BMD_temp.clear();
			double R = 0;
			while (roi.sieve[(int) (boneCenter[0] + R * Math.cos(Theta[et]))
					+ ((int) ((boneCenter[1] + R * Math.sin(Theta[et]))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 0.5) * Math.cos(Theta[et]))
							+ ((int) ((boneCenter[1] + (R + 0.5) * Math.sin(Theta[et]))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 1) * Math.cos(Theta[et]))
							+ ((int) ((boneCenter[1] + (R + 1) * Math.sin(Theta[et]))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 2) * Math.cos(Theta[et]))
							+ ((int) ((boneCenter[1] + (R + 2) * Math.sin(Theta[et]))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 3) * Math.cos(Theta[et]))
							+ ((int) ((boneCenter[1] + (R + 3) * Math.sin(Theta[et]))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 4) * Math.cos(Theta[et]))
							+ ((int) ((boneCenter[1] + (R + 4) * Math.sin(Theta[et]))) * width)] > 0
					|| roi.sieve[(int) (boneCenter[0] + (R + 6) * Math.cos(Theta[et]))
							+ ((int) ((boneCenter[1] + (R + 6) * Math.sin(Theta[et]))) * width)] > 0) {
				// Calculate BMC rho*dV, dV=dA*slice_thickness
				// dA=pi*((R(et)*resolution)^2-((R(et)-0.1)*resolution)^2),
				// slice_thickness = 1 mm
				// (could be set to actual slice thickness, but makes no
				// difference for comparisons -> 1 mm is used BMD divided by
				// 1000, because unit is mg/cm3 and area is mm2
				BMD_temp.add(roi.scaledImage[(int) (boneCenter[0] + R * Math.cos(Theta[et]))
						+ ((int) ((boneCenter[1] + R * Math.sin(Theta[et]))) * width)]);
				R += rIncrement;
			}
			Ru[et] = R;
			final int analysisThickness = BMD_temp.size();
			// Dividing the cortex to three divisions -> save the mean vBMD for
			// each division
			if (analysisThickness < divisions) {
				break;
			} else {
				// cortex
				for (int div = 0; div < divisions; ++div) {
					int mo = 0;
					for (int ka = (int) ((double) analysisThickness * (double) div
							/ divisions); ka < (int) (analysisThickness * (div + 1.0) / divisions); ka++) {
						BMDj.get(div)[et] += BMD_temp.get(ka);
						mo++;
					}
					BMDj.get(div)[et] /= mo;
				}
			}

		}
	}

}
