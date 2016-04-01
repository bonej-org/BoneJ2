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

public class DistributionAnalysis {

	// image array pointers
	public double[] originalROI;
	public double[] peeledROI;

	// constants
	public double sectorWidth;
	public double divisions;
	public double minimum;
	public double maximum;
	public double pixelSpacing;
	public int height;
	public int width;
	public double threshold;

	// Vectors for bone and marrow bone pixel coordinates
	Vector<Integer> marrowI;
	Vector<Integer> marrowJ;
	Vector<Integer> cortexI;
	Vector<Integer> cortexJ;
	double maxRadius;
	double maxRadiusY;
	public double[] marrowCenter;
	double[] cortexCenter;

	// Variables for radii calculations
	public double[] Theta;
	public double[] R;
	public double[] R2;
	double[] Rs;
	double[] Ru;
	Vector<double[]> BMDj;

	// Variables for moment calculations
	public Vector<Integer> pind;
	public Vector<Integer> pindColor;

	// Density distribution variables
	public double[] pRad;
	public double[] eRad;
	public double[] pPRad;
	public double[] pERad;
	public double[] endocorticalRadii;
	public double[] pericorticalRadii;
	public double[] peeledEndocorticalRadii;
	public double[] peeledPericorticalRadii;
	public double[] endoCorticalBMDs;
	public double[] midCorticalBMDs;
	public double[] periCorticalBMDs;
	public double[] radialDistribution;
	public double[] polarDistribution;
	public boolean preventPeeling;

	public double peeledBMD;

	public DistributionAnalysis(final SelectROI roi, final ImageAndAnalysisDetails details,
			final DetermineAlfa determineAlfa) {
		this.pind = determineAlfa.pind;
		this.pindColor = determineAlfa.pindColor;
		sectorWidth = details.sectorWidth;
		divisions = details.divisions;
		preventPeeling = details.preventPeeling;
		threshold = details.BMDthreshold;
		minimum = roi.minimum;
		maximum = roi.maximum;
		marrowI = roi.boneMarrowRoiI;
		marrowJ = roi.boneMarrowRoiJ;
		height = roi.height;
		width = roi.width;
		pixelSpacing = roi.pixelSpacing;
		originalROI = new double[width * height];
		peeledROI = new double[width * height];
		originalROI = roi.cortexROI.clone();
		peeledROI = roi.cortexROI.clone();
		erode(peeledROI);
		marrowCenter = new double[2];
		for (int i = 0; i < marrowI.size(); i++) {
			marrowCenter[0] += (double) marrowI.get(i);
			marrowCenter[1] += (double) marrowJ.get(i);
		}
		marrowCenter[0] /= marrowI.size();
		marrowCenter[1] /= marrowJ.size();
		// IJ.log("C0 "+marrowCenter[0]+" C1 "+marrowCenter[1]);
		maxRadius = 0;
		cortexI = new Vector<Integer>();
		cortexJ = new Vector<Integer>();
		peeledBMD = 0;
		int tempCounter = 0;
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++) {
				if (peeledROI[i + j * width] >= threshold) {
					if (Math.sqrt((i - marrowCenter[0]) * (i - marrowCenter[0])
							+ (j - marrowCenter[1]) * (j - marrowCenter[1])) > maxRadius) {
						maxRadius = Math.sqrt((i - marrowCenter[0]) * (i - marrowCenter[0])
								+ (j - marrowCenter[1]) * (j - marrowCenter[1]));
					}
					++tempCounter;
					peeledBMD += peeledROI[i + j * width];
				}
				if (originalROI[i + j * width] >= threshold) {
					cortexI.add(i);
					cortexJ.add(j);
				}
			}
		}
		peeledBMD /= (tempCounter);

		cortexCenter = new double[2];
		for (int i = 0; i < cortexI.size(); i++) {
			cortexCenter[0] += (double) cortexI.get(i);
			cortexCenter[1] += (double) cortexJ.get(i);

		}
		cortexCenter[0] /= cortexI.size();
		cortexCenter[1] /= cortexJ.size();
		maxRadiusY = 0; // y for cortical pixels. used for BSI calculations,
						// i.e. density weighted section modulus
		for (int i = 0; i < cortexI.size(); i++) {
			if (Math.sqrt(((double) cortexI.get(i) - cortexCenter[0]) * ((double) cortexI.get(i) - cortexCenter[0])
					+ ((double) cortexJ.get(i) - cortexCenter[1])
							* ((double) cortexJ.get(i) - cortexCenter[1])) > maxRadiusY) {
				maxRadiusY = Math.sqrt(((double) cortexI.get(i) - cortexCenter[0])
						* ((double) cortexI.get(i) - cortexCenter[0])
						+ ((double) cortexJ.get(i) - cortexCenter[1]) * ((double) cortexJ.get(i) - cortexCenter[1]));
			}
		}
		if (preventPeeling) {
			calculateRadiiNoPeeling();
		} else {
			calculateRadii();
		}
		rotateResults(details, roi);
	}

	void rotateResults(final ImageAndAnalysisDetails details, final SelectROI roi) {
		// Bone marrow cortexCenter[0] and cortexCenter[1]

		pRad = new double[360];
		eRad = new double[360];
		pPRad = new double[360];
		pERad = new double[360];

		// Calculate the endocortical and pericortical radii along with the
		// corresponding radii after peeling one layer of pixels
		for (int inde = 0; inde < 360; inde++) {
			pRad[inde] = Ru[inde] * pixelSpacing;
			eRad[inde] = Rs[inde] * pixelSpacing;
			pPRad[inde] = R[inde] * pixelSpacing;
			pERad[inde] = R2[inde] * pixelSpacing;

		}

		endocorticalRadii = new double[(int) (360 / sectorWidth)];
		pericorticalRadii = new double[(int) (360 / sectorWidth)];
		peeledEndocorticalRadii = new double[(int) (360 / sectorWidth)];
		peeledPericorticalRadii = new double[(int) (360 / sectorWidth)];
		endoCorticalBMDs = new double[(int) (360 / sectorWidth)];
		midCorticalBMDs = new double[(int) (360 / sectorWidth)];
		periCorticalBMDs = new double[(int) (360 / sectorWidth)];
		int pp;
		int dd;

		final double[][] corticalDensity = new double[(int) divisions][(int) (360 / sectorWidth)];
		// Calculate the division and sector values of vBMD
		for (pp = 0; pp < (int) (360 / sectorWidth); ++pp) {
			for (dd = 0; dd < (int) sectorWidth; ++dd) {
				endocorticalRadii[pp] += eRad[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
				pericorticalRadii[pp] += pRad[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
				peeledEndocorticalRadii[pp] += pERad[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
				peeledPericorticalRadii[pp] += pPRad[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
				// Cortex
				endoCorticalBMDs[pp] += BMDj.get(0)[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
				midCorticalBMDs[pp] += BMDj.get(1)[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
				periCorticalBMDs[pp] += BMDj.get(2)[pind.get((int) (pp * sectorWidth + dd))] / sectorWidth;
			}
			corticalDensity[0][pp] = endoCorticalBMDs[pp];
			corticalDensity[1][pp] = midCorticalBMDs[pp];
			corticalDensity[2][pp] = periCorticalBMDs[pp];
		}

		// Radial distribution
		radialDistribution = new double[(int) divisions];
		for (int i = 0; i < divisions; ++i) {
			radialDistribution[i] = 0;
			for (int j = 0; j < (int) (360 / sectorWidth); ++j) {
				radialDistribution[i] += corticalDensity[i][j];
			}
			radialDistribution[i] /= ((int) (360 / sectorWidth));
		}

		// Polar distribution
		polarDistribution = new double[(int) (360 / sectorWidth)];
		for (int j = 0; j < (int) (360 / sectorWidth); ++j) {
			polarDistribution[j] = 0;
			for (int i = 0; i < divisions; ++i) {
				polarDistribution[j] += corticalDensity[i][j];
			}
			polarDistribution[j] /= divisions;
		}

	}

	void calculateRadiiNoPeeling() {
		// Calculate radii in polar coordinate system originating from bone
		// marrow center of mass
		Theta = new double[360];
		R = new double[360];
		R2 = new double[360];
		Rs = new double[360];
		Ru = new double[360];
		BMDj = new Vector<double[]>();
		for (int i = 0; i < divisions; ++i) {
			BMDj.add(new double[360]);
		}
		final Vector<Double> BMD_temp = new Vector<Double>();
		int et;
		for (et = 0; et < 360; ++et) { // Finding endocortical and pericortical
										// borders uMath.sing polar coordinates
			Theta[et] = Math.PI / 180.0 * et;
			BMD_temp.clear();
			// Anatomical endosteal border
			while (originalROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
					+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)] < threshold
					&& R[et] < maxRadius / pixelSpacing) {
				R[et] = R[et] + 0.1;
			}
			R2[et] = R[et];
			Rs[et] = R[et];
			// Anatomical periosteal border
			while (originalROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
					+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)] > 0
					|| originalROI[(int) (marrowCenter[0] + (R[et] + 0.5) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 0.5) * Math.sin(Theta[et]))) * width)] > 0
					|| originalROI[(int) (marrowCenter[0] + (R[et] + 1) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 1) * Math.sin(Theta[et]))) * width)] > 0
					|| originalROI[(int) (marrowCenter[0] + (R[et] + 2) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 2) * Math.sin(Theta[et]))) * width)] > 0
					|| originalROI[(int) (marrowCenter[0] + (R[et] + 3) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 3) * Math.sin(Theta[et]))) * width)] > 0
					|| originalROI[(int) (marrowCenter[0] + (R[et] + 4) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 4) * Math.sin(Theta[et]))) * width)] > 0
					|| originalROI[(int) (marrowCenter[0] + (R[et] + 6) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 6) * Math.sin(Theta[et]))) * width)] > 0) {
				R[et] = R[et] + 0.1;
				// increments++;
				if (originalROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
						+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)] > 0) {
					BMD_temp.add(originalROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)]);
				}
			}
			Ru[et] = R[et];
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

	void calculateRadii() {
		// Calculate radii in polar coordinate system originating from bone
		// marrow center of mass
		Theta = new double[360];
		R = new double[360];
		R2 = new double[360];
		Rs = new double[360];
		Ru = new double[360];
		BMDj = new Vector<double[]>();
		for (int i = 0; i < divisions; ++i) {
			BMDj.add(new double[360]);
		}
		final Vector<Double> BMD_temp = new Vector<Double>();
		int et;
		for (et = 0; et < 360; ++et) { // Finding endocortical and pericortical
										// borders uMath.sing polar coordinates
			Theta[et] = Math.PI / 180.0 * et;
			BMD_temp.clear();
			// Anatomical endosteal border
			while (originalROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
					+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)] < threshold
					&& R[et] < maxRadius / pixelSpacing) {
				R[et] = R[et] + 0.1;
			}
			Rs[et] = R[et];
			// Peeled endosteal border
			while (peeledROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
					+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)] < 1
					&& R[et] < maxRadius / pixelSpacing) {
				R[et] = R[et] + 0.1;
			}
			R2[et] = R[et];
			R[et] = R[et] + 0.1;
			// Peeled periosteal border
			while (peeledROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
					+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)] > 0
					|| peeledROI[(int) (marrowCenter[0] + (R[et] + 0.5) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 0.5) * Math.sin(Theta[et]))) * width)] > 0
					|| peeledROI[(int) (marrowCenter[0] + (R[et] + 1) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 1) * Math.sin(Theta[et]))) * width)] > 0
					|| peeledROI[(int) (marrowCenter[0] + (R[et] + 2) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 2) * Math.sin(Theta[et]))) * width)] > 0
					|| peeledROI[(int) (marrowCenter[0] + (R[et] + 3) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 3) * Math.sin(Theta[et]))) * width)] > 0
					|| peeledROI[(int) (marrowCenter[0] + (R[et] + 4) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 4) * Math.sin(Theta[et]))) * width)] > 0
					|| peeledROI[(int) (marrowCenter[0] + (R[et] + 6) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (R[et] + 6) * Math.sin(Theta[et]))) * width)] > 0) {
				R[et] = R[et] + 0.1;
				// increments++;
				if (peeledROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
						+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)] > 0) {
					BMD_temp.add(originalROI[(int) (marrowCenter[0] + R[et] * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + R[et] * Math.sin(Theta[et]))) * width)]);
				}
			}
			// Anatomical periosteal border
			Ru[et] = R[et];
			while (originalROI[(int) (marrowCenter[0] + Ru[et] * Math.cos(Theta[et]))
					+ ((int) ((marrowCenter[1] + Ru[et] * Math.sin(Theta[et]))) * width)] > threshold
					|| originalROI[(int) (marrowCenter[0] + (Ru[et] + 0.5) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (Ru[et] + 0.5) * Math.sin(Theta[et]))) * width)] > threshold
					|| originalROI[(int) (marrowCenter[0] + (Ru[et] + 1) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (Ru[et] + 1) * Math.sin(Theta[et]))) * width)] > threshold
					|| originalROI[(int) (marrowCenter[0] + (Ru[et] + 2) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (Ru[et] + 2) * Math.sin(Theta[et]))) * width)] > threshold
					|| originalROI[(int) (marrowCenter[0] + (Ru[et] + 3) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (Ru[et] + 3) * Math.sin(Theta[et]))) * width)] > threshold
					|| originalROI[(int) (marrowCenter[0] + (Ru[et] + 4) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (Ru[et] + 4) * Math.sin(Theta[et]))) * width)] > threshold
					|| originalROI[(int) (marrowCenter[0] + (Ru[et] + 6) * Math.cos(Theta[et]))
							+ ((int) ((marrowCenter[1] + (Ru[et] + 6) * Math.sin(Theta[et]))) * width)] > threshold) {
				Ru[et] = Ru[et] + 0.1;
			}

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

	void erode(final double[] data) {
		// Erode algorithm
		// Modified from the best dilate by one solution taken from
		// http://ostermiller.org/dilate_and_erode.html
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				if (data[i * width + j] > minimum) {
					if (i > 0 && data[(i - 1) * width + j] == minimum || j > 0 && data[(i) * width + j - 1] == minimum
							|| i + 1 < height && data[(i + 1) * width + j] == minimum
							|| j + 1 < width && data[(i) * width + j + 1] == minimum) {
						data[i * width + j] = minimum - 1;
					} // Erode the pixel if any of the neighborhood pixels is
						// background
				}
			}
		}
		for (int i = 0; i < width * height; i++) {
			if (data[i] < minimum) {
				data[i] = minimum;
			}
		}
	}
}
