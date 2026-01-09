/*-
 * #%L
 * Mavenized version of the BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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


package org.bonej.plugins;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.List;

import org.bonej.menuWrappers.ThicknessHelper;
import org.bonej.util.BoneList;
import org.bonej.util.DialogModifier;
import org.bonej.util.ImageCheck;
import org.bonej.util.ThresholdGuesser;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Point3f;

import customnode.CustomPointMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import ij3d.Content;
import ij3d.Image3DUniverse;

/**
 * <p>
 * Calculate 2D geometrical parameters
 * </p>
 *
 * @author Michael Doube
 */

public class SliceGeometry implements PlugIn, DialogListener {
	
	// Controls how per-pixel contributions to second moments are weighted
	private enum MomentWeightingMode {
		GEOMETRIC,
		PARTIAL_AREA,
		DENSITY
	}

	private Calibration cal;
	private int al;
	private int startSlice;
	private int endSlice;
	private double vW;
	private double vH;
	/** Show slice centroid */
	private boolean doCentroids;
	/** Show principal axes */
	private boolean doAxes;
	/** Number of thresholded pixels in each slice */
	private double[] cslice;
	/** Cross-sectional area */
	private double[] cortArea;
	/** Mean of 3D local thickness in slice */
	private double[] meanCortThick3D;
	/** Maximum 3D local thickness in slice */
	private double[] maxCortThick3D;
	/** Standard deviation of 3D local thickness in slice */
	private double[] stdevCortThick3D;
	/** Mean of 2D local thickness in slice */
	private double[] meanCortThick2D;
	/** Maximum 2D local thickness in slice */
	private double[] maxCortThick2D;
	/** Standard deviation of 2D local thickness in slice */
	private double[] stdevCortThick2D;
	/** Angle of principal axes */
	private double[] theta;
	/**
	 * 2nd moment of area around minimum principal axis (shorter axis, larger I)
	 */
	private double[] Imin;
	/**
	 * 2nd moment of area around maximum principal axis (longer axis, smaller I)
	 */
	private double[] Imax;
	/** product moment of area, should be 0 if theta calculated perfectly */
	private double[] Ipm;
	/** length of major axis */
	private double[] R1;
	/** length of minor axis */
	private double[] R2;
	/** maximum distance from minimum principal axis (longer) */
	private double[] maxRadMin;
	/** maximum distance from maximum principal axis (shorter) */
	private double[] maxRadMax;
	/** Section modulus around minimum principal axis */
	private double[] Zmin;
	/** Section modulus around maximum principal axis */
	private double[] Zmax;
	/** Maximum diameter */
	private double[] feretMax;
	/** Angle of maximum diameter */
	private double[] feretAngle;
	/** Minimum diameter */
	private double[] feretMin;
	/** List of empty slices. If true, slice contains 0 pixels to analyse */
	private boolean[] emptySlices;
	/** List of slice centroids */
	private double[][] sliceCentroids;
	private double[] meanDensity;
	private double m;
	private double c;
	private double[][] weightedCentroids;
	private boolean fieldUpdated;
	/** List of perimeter lengths */
	private double[] perimeter;
	/** List of polar section moduli */
	private double[] Zpol;
	private Orienteer orienteer;
	/** Flag to use anatomic orientation */
	private boolean doOriented;
	/** Second moment of area around primary axis */
	private double[] I1;
	/** Second moment of area around secondary axis */
	private double[] I2;
	/** Chord length from principal axis */
	private double[] maxRad2;
	/** Chord length from secondary axis */
	private double[] maxRad1;
	/** Section modulus around primary axis */
	private double[] Z1;
	/** Section modulus around secondary axis */
	private double[] Z2;
	private double[] principalDiameter;
	private double[] secondaryDiameter;
	/** Use the masked version of thickness, which trims the 1px overhang */
	private boolean doMask;
	private double background;
	private double foreground;
	private boolean doPartialVolume;
	private MomentWeightingMode weightingMode = MomentWeightingMode.GEOMETRIC;



	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
		if (DialogModifier.hasInvalidNumber(gd.getNumericFields())) return false;
		final List<?> checkboxes = gd.getCheckboxes();
		final List<?> nFields = gd.getNumericFields();
		final Checkbox calibration = (Checkbox) checkboxes.get(10);
		final boolean isHUCalibrated = calibration.getState();
		final TextField minT = (TextField) nFields.get(0);
		final TextField maxT = (TextField) nFields.get(1);

		final double min = Double.parseDouble(minT.getText());
		final double max = Double.parseDouble(maxT.getText());
		if (isHUCalibrated && !fieldUpdated) {
			minT.setText("" + cal.getCValue(min));
			maxT.setText("" + cal.getCValue(max));
			fieldUpdated = true;
		}
		if (!isHUCalibrated && fieldUpdated) {
			minT.setText("" + cal.getRawValue(min));
			maxT.setText("" + cal.getRawValue(max));
			fieldUpdated = false;
		}
		if (isHUCalibrated) DialogModifier.replaceUnitString(gd, "grey", "HU");
		else DialogModifier.replaceUnitString(gd, "HU", "grey");

		DialogModifier.registerMacroValues(gd, gd.getComponents());
		return true;
	}

	@Override
	public void run(final String arg) {
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
			return;
		}
		Roi roi = imp.getRoi();
		if (roi == null) {}
		else if (roi.getType() != Roi.RECTANGLE) {
			IJ.showMessage("ROI Error", "Slice Geometry expects only one rectangular ROI");
			return;
		}
		cal = imp.getCalibration();
		vW = cal.pixelWidth;
		vH = cal.pixelHeight;
		// Linear unit of measure
		final String units = cal.getUnits();
		al = imp.getStackSize() + 1;

		final String pixUnits;
		if (ImageCheck.huCalibrated(imp)) {
			pixUnits = "HU";
			fieldUpdated = true;
		}
		else pixUnits = "grey";

		final double[] thresholds = ThresholdGuesser.setDefaultThreshold(imp);
		orienteer = Orienteer.getInstance();

		final GenericDialog gd = new GenericDialog("Options");

		int boneID = BoneList.guessBone(imp.getTitle());
		final String[] bones = BoneList.get();
		gd.addChoice("Bone: ", bones, bones[boneID]);

		gd.addCheckbox("2D_Thickness", true);
		gd.addCheckbox("3D_Thickness", false);
		gd.addCheckbox("Mask thickness map", false);
		gd.addCheckbox("Draw_Axes", true);
		gd.addCheckbox("Draw_Centroids", true);
		gd.addCheckbox("Annotated_Copy_(2D)", true);
		gd.addCheckbox("3D_Annotation", false);
		gd.addCheckbox("Process_Stack", false);
		gd.addCheckbox("Clear_results", false);
		initOrientationCheckBox(gd);
		gd.addCheckbox("HU_Calibrated", ImageCheck.huCalibrated(imp));
		gd.addNumericField("Bone_Min:", thresholds[0], 1, 6, pixUnits + " ");
		gd.addNumericField("Bone_Max:", thresholds[1], 1, 6, pixUnits + " ");
		gd.addMessage("Only pixels >= bone min\n" + "and <= bone max are used.");
		gd.addMessage("Density calibration coefficients");
		gd.addNumericField("Slope", 0, 4, 6, "g.cm^-3 / " + pixUnits + " ");
		gd.addNumericField("Y_Intercept", 1.8, 4, 6, "g.cm^-3");
		
		final String[] weightingChoices = {
				"Geometric (binary)",
				"Partial area (filledFraction)",
				"Density-weighted (experimental)"
		};
		gd.addChoice("Moment weighting", weightingChoices, weightingChoices[0]);

		gd.addNumericField("Background", thresholds[0], 1, 6, pixUnits + " ");
		gd.addNumericField("Foreground", thresholds[1], 1, 6, pixUnits + " ");
		gd.addHelp("https://imagej.github.io/plugins/bonej#slice-geometry");
		gd.addDialogListener(this);
		gd.showDialog();
		final String bone = gd.getNextChoice();
		boneID = BoneList.guessBone(bone);
		final boolean doThickness2D = gd.getNextBoolean();
		final boolean doThickness3D = gd.getNextBoolean();
		doMask = gd.getNextBoolean();
		doAxes = gd.getNextBoolean();
		doCentroids = gd.getNextBoolean();
		// if true, show annotation in a new window
		final boolean doCopy = gd.getNextBoolean();
		final boolean do3DAnnotation = gd.getNextBoolean();
		// If true, process the whole stack
		final boolean doStack = gd.getNextBoolean();
		// Flag to clear the results table or concatenate
		final boolean clearResults = gd.getNextBoolean();
		doOriented = gd.getNextBoolean();
		if (doStack) {
			startSlice = 1;
			endSlice = imp.getImageStackSize();
		}
		else {
			startSlice = imp.getCurrentSlice();
			endSlice = imp.getCurrentSlice();
		}

		final boolean isHUCalibrated = gd.getNextBoolean();
		double min = gd.getNextNumber();
		double max = gd.getNextNumber();
		m = gd.getNextNumber();
		c = gd.getNextNumber();
		// Harvest dropdown into this.weightingMode
		final int weightingIdx = gd.getNextChoiceIndex();
		this.weightingMode = MomentWeightingMode.values()[weightingIdx];

		// Replaced meaning of doPartialVolume with the dropdown choice
		doPartialVolume = (this.weightingMode == MomentWeightingMode.PARTIAL_AREA);

		background = gd.getNextNumber();
		foreground = gd.getNextNumber();
		if (background >= foreground || min >= max) {
			IJ.showMessage("Slice Geometry", "Background value must be less than foreground value.");
			return;
		}
		if (isHUCalibrated) {
			min = cal.getRawValue(min);
			max = cal.getRawValue(max);
			background = cal.getRawValue(background);
			foreground = cal.getRawValue(foreground);

			// convert HU->density user input into raw->density coefficients
			// for use in later calculations
			c = m * cal.getCoefficients()[0] + c;
			m = m * cal.getCoefficients()[1];
		}
		if (gd.wasCanceled()) return;

		if (calculateCentroids(imp, min, max) == 0) {
			IJ.error("No pixels available to calculate.\n" +
					"Please check the threshold and ROI.");
			return;
		}

		calculateMoments(imp, min, max);
		if (doThickness3D) calculateThickness3D(imp, min, max);
		if (doThickness2D) calculateThickness2D(imp, min, max);

		roiMeasurements(imp, min, max);

		// TODO locate centroids of multiple sections in a single plane

		final ResultsTable rt = ResultsTable.getResultsTable();
		if (clearResults) rt.reset();

		final String title = imp.getTitle();
		for (int s = startSlice; s <= endSlice; s++) {
			rt.incrementCounter();
			rt.addLabel(title);
			rt.addValue("Bone Code", boneID);
			rt.addValue("Slice", s);
			rt.addValue("CSA (" + units + "²)", cortArea[s]);
			rt.addValue("X cent. (" + units + ")", sliceCentroids[0][s]);
			rt.addValue("Y cent. (" + units + ")", sliceCentroids[1][s]);
			rt.addValue("Density", meanDensity[s]);
			rt.addValue("wX cent. (" + units + ")", weightedCentroids[0][s]);
			rt.addValue("wY cent. (" + units + ")", weightedCentroids[1][s]);
			rt.addValue("Theta (rad)", theta[s]);
			rt.addValue("R1 (" + units + ")", maxRadMax[s]);
			rt.addValue("R2 (" + units + ")", maxRadMin[s]);
			rt.addValue("Imin (" + units + "^4)", Imin[s]);
			rt.addValue("Imax (" + units + "^4)", Imax[s]);
			rt.addValue("Ipm (" + units + "^4)", Ipm[s]);
			rt.addValue("Zmin (" + units + "³)", Zmin[s]);
			rt.addValue("Zmax (" + units + "³)", Zmax[s]);
			rt.addValue("Zpol (" + units + "³)", Zpol[s]);
			rt.addValue("Feret Min (" + units + ")", feretMin[s]);
			rt.addValue("Feret Max (" + units + ")", feretMax[s]);
			rt.addValue("Feret Angle (rad)", feretAngle[s]);
			rt.addValue("Perimeter (" + units + ")", perimeter[s]);
			if (doThickness3D) {
				rt.addValue("Max Thick 3D (" + units + ")", maxCortThick3D[s]);
				rt.addValue("Mean Thick 3D (" + units + ")", meanCortThick3D[s]);
				rt.addValue("SD Thick 3D (" + units + ")", stdevCortThick3D[s]);
			}
			if (doThickness2D) {
				rt.addValue("Max Thick 2D (" + units + ")", maxCortThick2D[s]);
				rt.addValue("Mean Thick 2D (" + units + ")", meanCortThick2D[s]);
				rt.addValue("SD Thick 2D (" + units + ")", stdevCortThick2D[s]);
			}
			if (!doOriented || orienteer == null) {
				continue;
			}
			final String[] dirs = orienteer.getAxisLabels(imp);
			if (dirs == null) {
				continue;
			}
			rt.addValue(dirs[0] + " (rad)", orienteer.getOrientation(imp, dirs[0]));
			rt.addValue(dirs[2] + " (rad)", orienteer.getOrientation(imp, dirs[2]));
			rt.addValue("I" + dirs[0] + dirs[1] + "(" + units + "^4)", I1[s]);
			rt.addValue("I" + dirs[2] + dirs[3] + "(" + units + "^4)", I2[s]);
			rt.addValue("Z" + dirs[0] + dirs[1] + "(" + units + "³)", Z1[s]);
			rt.addValue("Z" + dirs[2] + dirs[3] + "(" + units + "³)", Z2[s]);
			rt.addValue("R" + dirs[0] + dirs[1] + "(" + units + ")", maxRad2[s]);
			rt.addValue("R" + dirs[2] + dirs[3] + "(" + units + ")", maxRad1[s]);
			rt.addValue("D" + dirs[0] + dirs[1] + "(" + units + ")",
					principalDiameter[s]);
			rt.addValue("D" + dirs[2] + dirs[3] + "(" + units + ")",
					secondaryDiameter[s]);
		}
		rt.show("Results");

		if (doAxes || doCentroids) {
			if (!doCopy) {
				final ImagePlus annImp = annotateImage(imp);
				imp.setStack(null, annImp.getImageStack());
			}
			else {
				annotateImage(imp).show();
			}
		}
		if (do3DAnnotation) {
			show3DAxes(imp);
		}
	}

	private void initOrientationCheckBox(final GenericDialog gd) {
		gd.addCheckbox("Use_Orientation", (orienteer != null));
		final Checkbox checkBox = (Checkbox) gd.getCheckboxes().lastElement();
		checkBox.setState(orienteer != null);
		checkBox.setEnabled(orienteer != null);
	}

	/**
	 * Draw centroids and / or principal axes on a copy of the original image
	 *
	 * @param imp
	 * @return ImagePlus with centroid and / or principal axes drawn
	 */
	private ImagePlus annotateImage(final ImagePlus imp) {
		final ImageStack stack = imp.getImageStack();
		final int w = stack.getWidth();
		final int h = stack.getHeight();
		final ImageStack annStack = new ImageStack(w, h);
		for (int s = startSlice; s <= endSlice; s++) {
			final ImageProcessor annIP = stack.getProcessor(s).duplicate();
			annIP.setColor(Color.white);
			final double cX = sliceCentroids[0][s] / vW;
			final double cY = sliceCentroids[1][s] / vH;

			if (doCentroids && !emptySlices[s]) {
				annIP.drawOval((int) Math.floor(cX - 4), (int) Math.floor(cY - 4), 8,
						8);
			}

			if (doAxes && !emptySlices[s]) {
				final double th = theta[s];
				final double rMin = R1[s];
				final double rMax = R2[s];
				final double thPi = th + Math.PI / 2;

				int x1 = (int) Math.floor(cX - Math.cos(thPi) * 2 * rMin);
				int y1 = (int) Math.floor(cY - Math.sin(thPi) * 2 * rMin);
				int x2 = (int) Math.floor(cX + Math.cos(thPi) * 2 * rMin);
				int y2 = (int) Math.floor(cY + Math.sin(thPi) * 2 * rMin);
				annIP.drawLine(x1, y1, x2, y2);

				x1 = (int) Math.floor(cX - Math.cos(-th) * 2 * rMax);
				y1 = (int) Math.floor(cY + Math.sin(-th) * 2 * rMax);
				x2 = (int) Math.floor(cX + Math.cos(-th) * 2 * rMax);
				y2 = (int) Math.floor(cY - Math.sin(-th) * 2 * rMax);
				annIP.drawLine(x1, y1, x2, y2);
			}
			annStack.addSlice(stack.getSliceLabel(s), annIP);
		}
		final ImagePlus ann = new ImagePlus("Annotated_" + imp.getTitle(),
				annStack);
		ann.setCalibration(imp.getCalibration());
		if (ann.getImageStackSize() == 1) ann.setProperty("Info", stack
				.getSliceLabel(startSlice));
		return ann;
	}


	/**
	 * Calculate the centroid of each slice
	 *
	 * @param imp Input image
	 * @return double containing sum of pixel count
	 */
	private double calculateCentroids(final ImagePlus imp, final double min,
			final double max)
	{
		final ImageStack stack = imp.getImageStack();
		int rx = 0; int ry = 0; int rwidth = 0; int rheight = 0;
		if (imp.getRoi() == null) {
			rwidth = imp.getWidth();
			rheight = imp.getHeight();
		} else {
			rx = imp.getRoi().getBounds().x;
			ry = imp.getRoi().getBounds().y;
			rwidth = imp.getRoi().getBounds().width;
			rheight = imp.getRoi().getBounds().height;
		}
		// 2D centroids
		sliceCentroids = new double[2][al];
		// pixel counters
		double cstack = 0;
		emptySlices = new boolean[al];
		cslice = new double[al];
		cortArea = new double[al];
		meanDensity = new double[al];
		weightedCentroids = new double[2][al];
		final double pixelArea = vW * vH;
		final int roiXEnd = rx + rwidth;
		final int roiYEnd = ry + rheight;
		for (int s = startSlice; s <= endSlice; s++) {
			IJ.showStatus("Calculating centroids...");
			IJ.showProgress(s - startSlice, endSlice);
			double sumX = 0;
			double sumY = 0;
			int count = 0;
			double sumAreaFractions = 0;
			double sumD = 0;
			double wSumX = 0;
			double wSumY = 0;
			final ImageProcessor ip = stack.getProcessor(s);
			for (int y = ry; y < roiYEnd; y++) {
				for (int x = rx; x < roiXEnd; x++) {
					final double pixel = ip.get(x, y);
					if (pixel >= min && pixel <= max) {
						count++;
						final double aW = (this.weightingMode == MomentWeightingMode.PARTIAL_AREA)
						? filledFraction(pixel)
						: 1.0;
						sumAreaFractions += aW;
						sumX += aW * x;
						sumY += aW * y;
						final double wP = pixel * this.m + this.c;
						sumD += wP;
						wSumX += x * wP;
						wSumY += y * wP;
					}
				}
			}
			cslice[s] = count;
			if (count > 0) {
				
				final double areaCx = sumX * vW / sumAreaFractions;
				final double areaCy = sumY * vH / sumAreaFractions;

				// Density-weighted centroid: only defined when the total density weight is non-zero.
				// If sumD == 0 (e.g. empty slice or zero net weight), the centroid is undefined and
				// we propagate NaN rather than allowing a divide-by-zero or arbitrary fallback.
				final double densCx = (sumD != 0) ? (wSumX * vW / sumD) : Double.NaN;
				final double densCy = (sumD != 0) ? (wSumY * vH / sumD) : Double.NaN;

				weightedCentroids[0][s] = densCx;
				weightedCentroids[1][s] = densCy;

				if (this.weightingMode == MomentWeightingMode.DENSITY) {
					sliceCentroids[0][s] = densCx;
					sliceCentroids[1][s] = densCy;
				} else {
					sliceCentroids[0][s] = areaCx;
					sliceCentroids[1][s] = areaCy;
				}
		
				cortArea[s] = sumAreaFractions * pixelArea;
				meanDensity[s] = sumD / count;
				cstack += count;
				emptySlices[s] = false;
			}
			else {
				emptySlices[s] = true;
				cortArea[s] = Double.NaN;
				sliceCentroids[0][s] = Double.NaN;
				sliceCentroids[1][s] = Double.NaN;
				weightedCentroids[0][s] = Double.NaN;
				weightedCentroids[1][s] = Double.NaN;
				meanDensity[s] = Double.NaN;
				cslice[s] = Double.NaN;
			}
		}
		return cstack;
	}

	/**
	 * Calculate second moments of area, length and angle of principal axes
	 *
	 * @param imp
	 */
	private void calculateMoments(final ImagePlus imp, final double min,
			final double max)
	{
		final ImageStack stack = imp.getImageStack();
		int rx = 0; int ry = 0; int rwidth = 0; int rheight = 0;
		if (imp.getRoi() == null) {
			rwidth = imp.getWidth();
			rheight = imp.getHeight();
		} else {
			rx = imp.getRoi().getBounds().x;
			ry = imp.getRoi().getBounds().y;
			rwidth = imp.getRoi().getBounds().width;
			rheight = imp.getRoi().getBounds().height;
		}
		theta = new double[al];
		for (int s = startSlice; s <= endSlice; s++) {
			IJ.showStatus("Calculating Ix and Iy...");
			IJ.showProgress(s, endSlice);
			double sxs = 0;
			double sys = 0;
			double sxxs = 0;
			double syys = 0;
			double sxys = 0;
			final int roiXEnd = rx + rwidth;
			final int roiYEnd = ry + rheight;
			if (emptySlices[s]) {
				theta[s] = Double.NaN;
				continue;
			}
			final ImageProcessor ip = stack.getProcessor(s);
			double sumAreaFractions = 0;
			for (int y = ry; y < roiYEnd; y++) {
				for (int x = rx; x < roiXEnd; x++) {
					final double pixel = ip.get(x, y);
					if (pixel >= min && pixel <= max) {
						final double xVw = x * vW;
						final double yVh = y * vH;
						final double areaFraction = doPartialVolume ? filledFraction(pixel) : 1;
						sumAreaFractions += areaFraction;
						// sum of distances from axis
						sxs += xVw * areaFraction;
						sys += yVh * areaFraction;
						// sum of squares of distances from axis
						sxxs += xVw * xVw * areaFraction;
						syys += yVh * yVh * areaFraction;
						sxys += xVw * yVh * areaFraction;
					}
				}
			}
			// + /12 is for each pixel's own moment
			final double Myys = sxxs - (sxs * sxs / sumAreaFractions) + sumAreaFractions * vW * vW / 12;
			final double Mxxs = syys - (sys * sys / sumAreaFractions) + sumAreaFractions * vH * vH / 12;
			final double Mxys = sxys - (sxs * sys / sumAreaFractions) + sumAreaFractions * vH * vW / 12;
			if (Mxys == 0) {
				theta[s] = 0;
			}
			else {
				theta[s] = Math.atan((Mxxs - Myys + Math.sqrt((Mxxs - Myys) * (Mxxs -
						Myys) + 4 * Mxys * Mxys)) / (2 * Mxys));
			}
		}
		// Get I and Z around the principal axes
		final double[][] result = calculateAngleMoments(imp, min, max, theta);
		Imin = result[0];
		Imax = result[1];
		Ipm = result[2];
		R1 = result[3];
		R2 = result[4];
		maxRadMin = result[5];
		maxRadMax = result[6];
		Zmin = result[7];
		Zmax = result[8];
		Zpol = result[9];

		// optionally get I and Z around some user-defined axes
		if (doOriented && orienteer != null) {
			final double angle = orienteer.getOrientation();
			final double[] angles = new double[al];
			for (int i = 0; i < al; i++) {
				angles[i] = angle;
			}
			final double[][] result2 = calculateAngleMoments(imp, min, max, angles);
			I1 = result2[0];
			I2 = result2[1];
			maxRad2 = result2[5];
			maxRad1 = result2[6];
			Z1 = result2[7];
			Z2 = result2[8];
		}
	}

	private double[][] calculateAngleMoments(final ImagePlus imp,
			final double min, final double max, final double[] angles)
	{
		final ImageStack stack = imp.getImageStack();
		int rx = 0; int ry = 0; int rwidth = 0; int rheight = 0;
		if (imp.getRoi() == null) {
			rwidth = imp.getWidth();
			rheight = imp.getHeight();
		} else {
			rx = imp.getRoi().getBounds().x;
			ry = imp.getRoi().getBounds().y;
			rwidth = imp.getRoi().getBounds().width;
			rheight = imp.getRoi().getBounds().height;
		}
		final double[] I1 = new double[al];
		final double[] I2 = new double[al];
		final double[] Ip = new double[al];
		final double[] r1 = new double[al];
		final double[] r2 = new double[al];
		final double[] maxRad2 = new double[al];
		final double[] maxRad1 = new double[al];
		final double[] maxRadC = new double[al];
		final double[] Z1 = new double[al];
		final double[] Z2 = new double[al];
		final double[] Zp = new double[al];
		for (int s = startSlice; s <= endSlice; s++) {
			IJ.showStatus("Calculating Imin and Imax...");
			IJ.showProgress(s, endSlice);
			if (emptySlices[s]) {
				I1[s] = Double.NaN;
				I2[s] = Double.NaN;
				Ip[s] = Double.NaN;
				r1[s] = Double.NaN;
				r2[s] = Double.NaN;
				maxRad2[s] = Double.NaN;
				maxRad1[s] = Double.NaN;
				Z1[s] = Double.NaN;
				Z2[s] = Double.NaN;
				Zp[s] = Double.NaN;
			}
			else {
				final ImageProcessor ip = stack.getProcessor(s);
				double sxs = 0;
				double sys = 0;
				double sxxs = 0;
				double syys = 0;
				double sxys = 0;
				double maxRadMinS = 0;
				double maxRadMaxS = 0;
				double maxRadCentreS = 0;
				final double cosTheta = Math.cos(angles[s]);
				final double sinTheta = Math.sin(angles[s]);
				final int roiYEnd = ry + rheight;
				final int roiXEnd = rx + rwidth;
				final double xC = sliceCentroids[0][s];
				final double yC = sliceCentroids[1][s];
				final double cS = cslice[s];
				double sumAreaFractions = 0;
				for (int y = ry; y < roiYEnd; y++) {
					final double yYc = y * vH - yC;
					for (int x = rx; x < roiXEnd; x++) {
						final double pixel = ip.get(x, y);
						if (pixel >= min && pixel <= max) {
							final double areaFraction = doPartialVolume ? filledFraction(
									pixel)
									: 1;
									sumAreaFractions += areaFraction;
									final double xXc = x * vW - xC;
									final double xCosTheta = x * vW * cosTheta;
									final double yCosTheta = y * vH * cosTheta;
									final double xSinTheta = x * vW * sinTheta;
									final double ySinTheta = y * vH * sinTheta;
									sxs += areaFraction * (xCosTheta + ySinTheta);
									sys += areaFraction * (yCosTheta - xSinTheta);
									sxxs += areaFraction * (xCosTheta + ySinTheta) * (xCosTheta + ySinTheta);
									syys += areaFraction * (yCosTheta - xSinTheta) * (yCosTheta - xSinTheta);
									sxys += areaFraction * (yCosTheta - xSinTheta) * (xCosTheta + ySinTheta);
									maxRadMinS = Math.max(maxRadMinS, Math.abs(xXc * cosTheta + yYc *
											sinTheta));
									maxRadMaxS = Math.max(maxRadMaxS, Math.abs(yYc * cosTheta - xXc *
											sinTheta));
									maxRadCentreS = Math.max(maxRadCentreS, Math.sqrt(xXc * xXc +
											yYc * yYc));
						}
					}
				}
				maxRad2[s] = maxRadMinS;
				maxRad1[s] = maxRadMaxS;
				maxRadC[s] = maxRadCentreS;
				final double pixelMoments = sumAreaFractions * vW * vH
						* (cosTheta * cosTheta + sinTheta * sinTheta) / 12;
				I1[s] = vW * vH * (sxxs - (sxs * sxs / sumAreaFractions) + pixelMoments);
				I2[s] = vW * vH * (syys - (sys * sys / sumAreaFractions) + pixelMoments);
				Ip[s] = sxys - (sys * sxs / sumAreaFractions) + pixelMoments;
				r1[s] = Math.sqrt(I2[s] / (cS * vW * vH * vW * vH));
				r2[s] = Math.sqrt(I1[s] / (cS * vW * vH * vW * vH));
				Z1[s] = I1[s] / maxRad2[s];
				Z2[s] = I2[s] / maxRad1[s];
				Zp[s] = (I1[s] + I2[s]) / maxRadC[s];
			}
		}

		return new double[][] { I1, I2, Ip, r1, r2, maxRad2, maxRad1, Z1, Z2, Zp, };
	}



	/**
	 * Calculate thickness on individual slices using local thickness
	 *
	 * @param imp
	 */
	private void calculateThickness2D(final ImagePlus imp, final double min,
			final double max)
	{
		maxCortThick2D = new double[al];
		meanCortThick2D = new double[al];
		stdevCortThick2D = new double[al];

		final int nThreads = Runtime.getRuntime().availableProcessors();
		final SliceThread[] sliceThread = new SliceThread[nThreads];
		for (int thread = 0; thread < nThreads; thread++) {
			sliceThread[thread] = new SliceThread(thread, nThreads, imp, min, max,
					meanCortThick2D, maxCortThick2D, stdevCortThick2D, startSlice, endSlice,
					emptySlices);
			sliceThread[thread].start();
		}
		try {
			for (int thread = 0; thread < nThreads; thread++) {
				sliceThread[thread].join();
			}
		}
		catch (final InterruptedException ie) {
			IJ.error("A thread was interrupted.");
		}
	}

	/**
	 * Calculate 3D Local Thickness and determine thickness statistics for the
	 * slice
	 */
	private void calculateThickness3D(final ImagePlus imp, final double min,
			final double max)
	{
		maxCortThick3D = new double[al];
		meanCortThick3D = new double[al];
		stdevCortThick3D = new double[al];
		int rx = 0; int ry = 0; int rwidth = 0; int rheight = 0;
		if (imp.getRoi() == null) {
			rwidth = imp.getWidth();
			rheight = imp.getHeight();
		} else {
			rx = imp.getRoi().getBounds().x;
			ry = imp.getRoi().getBounds().y;
			rwidth = imp.getRoi().getBounds().width;
			rheight = imp.getRoi().getBounds().height;
		}

		// convert to binary
		final ImagePlus binaryImp = convertToBinary(imp, min, max);

		final ImagePlus thickImp = ThicknessHelper.getLocalThickness(binaryImp, false, doMask);

		for (int s = startSlice; s <= endSlice; s++) {
			if (emptySlices[s]) {
				maxCortThick3D[s] = Double.NaN;
				meanCortThick3D[s] = Double.NaN;
				stdevCortThick3D[s] = Double.NaN;
				continue;
			}
			final FloatProcessor ip = (FloatProcessor) thickImp.getStack()
					.getProcessor(s);
			double sumPix = 0;
			double sliceMax = 0;
			double pixCount = 0;
			final int roiXEnd = rx + rwidth;
			final int roiYEnd = ry + rheight;
			for (int y = ry; y < roiYEnd; y++) {
				for (int x = rx; x < roiXEnd; x++) {
					final float pixel = Float.intBitsToFloat(ip.get(x, y));
					if (pixel > 0) {
						pixCount++;
						sumPix += pixel;
						sliceMax = Math.max(sliceMax, pixel);
					}
				}
			}
			final double sliceMean = sumPix / pixCount;
			meanCortThick3D[s] = sliceMean;
			maxCortThick3D[s] = sliceMax;

			double sumSquares = 0;
			for (int y = ry; y < roiYEnd; y++) {
				for (int x = rx; x < roiXEnd; x++) {
					final float pixel = Float.intBitsToFloat(ip.get(x, y));
					if (pixel > 0) {
						final double d = sliceMean - pixel;
						sumSquares += d * d;
					}
				}
			}
			stdevCortThick3D[s] = Math.sqrt(sumSquares / pixCount);
		}
	}

	private static ImagePlus convertToBinary(final ImagePlus imp,
			final double min, final double max)
	{
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		final ImageStack sourceStack = imp.getImageStack();
		final ImageStack binaryStack = new ImageStack(w, h);
		for (int s = 1; s <= d; s++) {
			final ImageProcessor sliceIp = sourceStack.getProcessor(s);
			final ByteProcessor binaryIp = new ByteProcessor(w, h);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (sliceIp.get(x, y) >= min && sliceIp.get(x, y) <= max) {
						binaryIp.set(x, y, 255);
					}
				}
			}
			binaryStack.addSlice(sourceStack.getSliceLabel(s), binaryIp);
		}
		final ImagePlus binaryImp = new ImagePlus("binaryImp", binaryStack);
		binaryImp.setCalibration(imp.getCalibration());
		return binaryImp;
	}

	private void roiMeasurements(final ImagePlus imp, final double min,
			final double max)
	{
		final Roi initialRoi = imp.getRoi();
		final int xMin = imp.getImageStack().getRoi().x;
		double[] feretValues;
		feretAngle = new double[al];
		feretMax = new double[al];
		feretMin = new double[al];
		perimeter = new double[al];
		principalDiameter = new double[al];
		secondaryDiameter = new double[al];
		final int initialSlice = imp.getCurrentSlice();
		// for the required slices...
		for (int s = startSlice; s <= endSlice; s++) {
			final ImageProcessor ip = imp.getImageStack().getProcessor(s);
			final Wand w = new Wand(ip);
			w.autoOutline(xMin, (int) Math.round(sliceCentroids[1][s] / vH), min, max,
					Wand.EIGHT_CONNECTED);
			if (emptySlices[s] || w.npoints == 0) {
				feretMin[s] = Double.NaN;
				feretAngle[s] = Double.NaN;
				feretMax[s] = Double.NaN;
				perimeter[s] = Double.NaN;
				principalDiameter[s] = Double.NaN;
				secondaryDiameter[s] = Double.NaN;
				continue;
			}

			final int type = Wand.allPoints() ? Roi.FREEROI : Roi.TRACED_ROI;
			final PolygonRoi roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints,
					type);
			feretValues = roi.getFeretValues();
			feretMin[s] = feretValues[2] * vW;
			feretAngle[s] = feretValues[1] * Math.PI / 180;
			feretMax[s] = feretValues[0] * vW;
			perimeter[s] = roi.getLength() * vW;

			if (doOriented && orienteer != null) {
				final double[][] points = new double[w.npoints][2];
				for (int i = 0; i < w.npoints; i++) {
					points[i][0] = w.xpoints[i] * vW;
					points[i][1] = w.ypoints[i] * vH;
				}
				final double[] diameters = orienteer.getDiameters(points);
				principalDiameter[s] = diameters[0];
				secondaryDiameter[s] = diameters[1];
			}
		}
		IJ.setSlice(initialSlice);
		imp.setRoi(initialRoi);
	}

	/**
	 * Display principal axes on a 3D rendered version of the image
	 *
	 * @param imp Original image
	 */
	private void show3DAxes(final ImagePlus imp) {
		// copy the data from inside the ROI and convert it to 8-bit
		final Duplicator d = new Duplicator();
		final ImagePlus roiImp = d.run(imp, 1, imp.getImageStackSize());

		// initialise and show the 3D universe
		final Image3DUniverse univ = new Image3DUniverse();
		univ.show();

		double rX = 0;
		double rY = 0;
		if (imp.getRoi() != null) {
			final Rectangle roi = imp.getRoi().getBounds();
			rX = roi.getX() * cal.pixelWidth;
			rY = roi.getY() * cal.pixelHeight;
		}

		// list of centroids
		final List<Point3f> centroids = new ArrayList<>();
		// list of axes
		final List<Point3f> minAxes = new ArrayList<>();
		final List<Point3f> maxAxes = new ArrayList<>();
		for (int s = 1; s <= roiImp.getImageStackSize(); s++) {
			if (((Double) cortArea[s]).equals(Double.NaN) || cortArea[s] == 0)
				continue;

			final double cX = sliceCentroids[0][s] - rX;
			final double cY = sliceCentroids[1][s] - rY;
			final double cZ = (s - 0.5) * cal.pixelDepth;

			final Point3f cent = new Point3f();
			cent.x = (float) cX;
			cent.y = (float) cY;
			cent.z = (float) cZ;
			centroids.add(cent);

			// add the axes to the list
			final double th = theta[s];
			final double rMin = R1[s] * cal.pixelWidth;
			final double rMax = R2[s] * cal.pixelWidth;
			final double thPi = th + Math.PI / 2;

			final Point3f start1 = new Point3f();
			start1.x = (float) (cX - Math.cos(thPi) * 2 * rMin);
			start1.y = (float) (cY - Math.sin(thPi) * 2 * rMin);
			start1.z = (float) cZ;
			minAxes.add(start1);

			final Point3f end1 = new Point3f();
			end1.x = (float) (cX + Math.cos(thPi) * 2 * rMin);
			end1.y = (float) (cY + Math.sin(thPi) * 2 * rMin);
			end1.z = (float) cZ;
			minAxes.add(end1);

			final Point3f start2 = new Point3f();
			start2.x = (float) (cX - Math.cos(-th) * 2 * rMax);
			start2.y = (float) (cY + Math.sin(-th) * 2 * rMax);
			start2.z = (float) cZ;
			maxAxes.add(start2);

			final Point3f end2 = new Point3f();
			end2.x = (float) (cX + Math.cos(-th) * 2 * rMax);
			end2.y = (float) (cY - Math.sin(-th) * 2 * rMax);
			end2.z = (float) cZ;
			maxAxes.add(end2);
		}
		// show the centroids
		final CustomPointMesh mesh = new CustomPointMesh(centroids);
		mesh.setPointSize(5.0f);
		final Color3f cColour = new Color3f(0.0f, 0.5f, 1.0f);
		mesh.setColor(cColour);
		try {
			univ.addCustomMesh(mesh, "Centroid").setLocked(true);
		}
		catch (final NullPointerException npe) {
			IJ.log("3D Viewer was closed before rendering completed.");
			return;
		}

		// show the axes
		final Color3f minColour = new Color3f(1.0f, 0.0f, 0.0f);
		try {
			univ.addLineMesh(minAxes, minColour, "Minimum axis", false).setLocked(
					true);
		}
		catch (final NullPointerException npe) {
			IJ.log("3D Viewer was closed before rendering completed.");
			return;
		}
		final Color3f maxColour = new Color3f(0.0f, 0.0f, 1.0f);
		try {
			univ.addLineMesh(maxAxes, maxColour, "Maximum axis", false).setLocked(
					true);
		}
		catch (final NullPointerException npe) {
			IJ.log("3D Viewer was closed before rendering completed.");
			return;
		}

		// show the stack
		try {
			new StackConverter(roiImp).convertToGray8();
			final Content content = univ.addVoltex(roiImp);
			content.setLocked(true);
		}
		catch (final NullPointerException npe) {
			IJ.log("3D Viewer was closed before rendering completed.");
		}
	}

	private final class SliceThread extends Thread {

		private final int thread;
		private final int nThreads;
		private final int startSlice;
		private final int endSlice;
		private final double min;
		private final double max;
		private final double[] meanThick;
		private final double[] maxThick;
		private final double[] stdevThick;
		private final boolean[] emptySlices;
		private final ImagePlus impT;

		private SliceThread(final int thread, final int nThreads,
				final ImagePlus imp, final double min, final double max,
				final double[] meanThick, final double[] maxThick,
				final double[] stdevThick, final int startSlice, final int endSlice,
				final boolean[] emptySlices)
		{
			impT = imp;
			this.min = min;
			this.max = max;
			this.thread = thread;
			this.nThreads = nThreads;
			this.meanThick = meanThick;
			this.maxThick = maxThick;
			this.stdevThick = stdevThick;
			this.startSlice = startSlice;
			this.endSlice = endSlice;
			this.emptySlices = emptySlices;
		}

		@Override
		public void run() {
			for (int s = thread + startSlice; s <= endSlice; s += nThreads) {
				if (emptySlices[s]) {
					meanThick[s] = Double.NaN;
					maxThick[s] = Double.NaN;
					stdevThick[s] = Double.NaN;
					continue;
				}
				final ImageProcessor ip = impT.getImageStack().getProcessor(s);
				final ImagePlus sliceImp = new ImagePlus(" " + s, ip);
				int rx = 0; int ry = 0; int rwidth = 0; int rheight = 0;
				if (sliceImp.getRoi() == null) {
					rwidth = sliceImp.getWidth();
					rheight = sliceImp.getHeight();
				} else {
					rx = sliceImp.getRoi().getBounds().x;
					ry = sliceImp.getRoi().getBounds().y;
					rwidth = sliceImp.getRoi().getBounds().width;
					rheight = sliceImp.getRoi().getBounds().height;
				}
				// binarise
				final ImagePlus binaryImp = convertToBinary(sliceImp, min, max);
				final Calibration cal = impT.getCalibration();
				binaryImp.setCalibration(cal);
				// calculate thickness
				final ImagePlus thickImp = ThicknessHelper.getLocalThickness(binaryImp, false,
						doMask);
				final FloatProcessor thickIp = (FloatProcessor) thickImp.getProcessor();
				double sumPix = 0;
				double sliceMax = 0;
				double pixCount = 0;
				final double roiXEnd = rx + rwidth;
				final double roiYEnd = ry + rheight;
				for (int y = ry; y < roiYEnd; y++) {
					for (int x = rx; x < roiXEnd; x++) {
						final float pixel = Float.intBitsToFloat(thickIp.get(x, y));
						if (pixel > 0) {
							pixCount++;
							sumPix += pixel;
							sliceMax = Math.max(sliceMax, pixel);
						}
					}
				}
				final double sliceMean = sumPix / pixCount;
				meanThick[s] = sliceMean;
				maxThick[s] = sliceMax;

				double sumSquares = 0;
				for (int y = ry; y < roiYEnd; y++) {
					for (int x = rx; x < roiXEnd; x++) {
						final float pixel = Float.intBitsToFloat(thickIp.get(x, y));
						if (pixel > 0) {
							final double d = sliceMean - pixel;
							sumSquares += d * d;
						}
					}
				}

				stdevThick[s] = Math.sqrt(sumSquares / pixCount);
			}
		}
	}

	/**
	 * Calculate the proportion of a pixel that contains foreground, assuming a
	 * two-phase image (foreground and background) and linear relationship
	 * between pixel value and physical density. If the pixel value is greater
	 * than the foreground value, this method will return 1, and if lower than
	 * the background value, returns 0.
	 * 
	 * @param pixel
	 *            the input pixel value
	 * @return fraction of pixel 'size' occupied by foreground
	 */
	private double filledFraction(final double pixel) {
		if (pixel > foreground) {
			return 1;
		}
		if (pixel < background) {
			return 0;
		}
		return (pixel - background) / (foreground - background);
	}
}
