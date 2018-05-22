/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.plugins;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Rectangle;
import java.awt.TextField;
import java.util.List;

import ij.gui.Roi;
import org.bonej.geometry.FitSphere;
import org.bonej.util.DialogModifier;
import org.bonej.util.ImageCheck;
import org.bonej.util.ResultInserter;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

/**
 * <p>
 * Takes point selections from ROI manager and returns the centroid and radius
 * of a best fit sphere Ported from Angelo Tardugno's C++
 * </p>
 *
 * @author Michael Doube and Angelo Tardugno
 */
public class SphereFitter implements PlugIn, DialogListener {

	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
		if (DialogModifier.hasInvalidNumber(gd.getNumericFields())) return false;
		final List<?> checkboxes = gd.getCheckboxes();
		final List<?> numbers = gd.getNumericFields();
		final Checkbox box = (Checkbox) checkboxes.get(0);
		final TextField num = (TextField) numbers.get(0);
		if (box.getState()) {
			num.setEnabled(true);
		}
		else {
			num.setEnabled(false);
		}
		DialogModifier.registerMacroValues(gd, gd.getComponents());
		return true;
	}

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment()) return;
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
			return;
		}
		if (ImageCheck.isSingleSlice(imp)) {
			IJ.error("Stack required");
			return;
		}
		final RoiManager roiMan = RoiManager.getInstance();
		if (roiMan == null) {
			IJ.error("Please populate ROI Manager with point ROIs");
			IJ.run("ROI Manager...");
			return;
		}

		final GenericDialog gd = new GenericDialog("Setup");
		gd.addCheckbox("Copy Sphere", true);
		gd.addNumericField("Padding", 2, 0, 2, "voxels");
		gd.addCheckbox("Inner Cube", true);
		gd.addCheckbox("Outer Cube", true);
		gd.addNumericField("Crop Factor", 1.0, 2, 4, "");
		gd.addCheckbox("Add_to_ROI_Manager", false);
		gd.addCheckbox("Clear_ROI_Manager", false);
		gd.addHelp("http://bonej.org/sphere");
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}
		final boolean doCopy = gd.getNextBoolean();
		final int padding = (int) gd.getNextNumber();
		final boolean doInnerCube = gd.getNextBoolean();
		final boolean doOuterCube = gd.getNextBoolean();
		final double cropFactor = gd.getNextNumber();
		final boolean doRoiMan = gd.getNextBoolean();
		final boolean clearRois = gd.getNextBoolean();

		final double[][] points = getRoiManPoints(imp, roiMan);
		if (points == null) {
			IJ.showMessage("Can't fit sphere to points.\n" +
				"No usable points in the ROI Manager.");
			return;
		}
		final double i = points[0][2];
		double j = i;
		for (final double[] p : points)
			if (p[2] != i) j = p[2];
		if (j == i) {
			IJ.showMessage("Can't fit sphere to points.\n" +
				"All points are on the same slice.\n" +
				"Check the ROI Manager option More >> Options... >\n" +
				"Associate ROIs with stack positions.\n");
			return;
		}

		final double[] sphereDim;
		try {
			sphereDim = FitSphere.fitSphere(points);
		}
		catch (final IllegalArgumentException ia) {
			IJ.showMessage(ia.getMessage());
			return;
		}
		catch (final RuntimeException re) {
			IJ.showMessage("Can't fit sphere to points.\n" +
				"Add more point ROI's to the ROI Manager and try again.\n");
			return;
		}

		final String units = imp.getCalibration().getUnits();
		final ResultInserter ri = ResultInserter.getInstance();
		ri.setResultInRow(imp, "X centroid (" + units + ")", sphereDim[0]);
		ri.setResultInRow(imp, "Y centroid (" + units + ")", sphereDim[1]);
		ri.setResultInRow(imp, "Z centroid (" + units + ")", sphereDim[2]);
		ri.setResultInRow(imp, "Radius (" + units + ")", sphereDim[3]);
		ri.updateTable();

		if (doCopy) copySphere(imp, padding, cropFactor, sphereDim).show();
		if (doInnerCube) copyInnerCube(imp, cropFactor, sphereDim).show();
		if (doOuterCube) copyOuterCube(imp, cropFactor, sphereDim).show();
		if (doRoiMan) {
			addToRoiManager(imp, roiMan, sphereDim, clearRois);
		}
		UsageReporter.reportEvent(this).send();
	}

	/**
	 * Remove all ROIs from the ROI manager
	 *
	 * @param roiMan an instance of {@link RoiManager}.
	 */
	private static void deleteAll(final RoiManager roiMan) {
		final Roi[] rois = roiMan.getRoisAsArray();
		for (int i = 0; i < rois.length; i++) {
			if (roiMan.getCount() == 0) break;
			roiMan.select(i);
			roiMan.runCommand("delete");
		}
	}

	/**
	 * Get the calibrated 3D coordinates of point ROIs from the ROI manager
	 *
	 * @param imp an image.
	 * @param roiMan an instance of {@link RoiManager}
	 * @return double[n][3] containing n (x, y, z) coordinates or null if there
	 *         are no points
	 */
	private static double[][] getRoiManPoints(final ImagePlus imp,
											  final RoiManager roiMan)
	{
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		int nPoints = 0;
		final Roi[] roiList = roiMan.getRoisAsArray();
		for (int i = 0; i < roiMan.getCount(); i++) {
			final Roi roi = roiList[i];
			if (roi.getType() == 10) {
				nPoints++;
			}
		}
		if (nPoints == 0) return null;
		final double[][] dataPoints = new double[nPoints][3];
		int j = 0;
		for (int i = 0; i < roiMan.getCount(); i++) {
			final Roi roi = roiList[i];
			if (roi.getType() == 10) {
				final Rectangle xy = roi.getBounds();
				dataPoints[j][0] = xy.getX() * vW;
				dataPoints[j][1] = xy.getY() * vH;
				dataPoints[j][2] = roi.getPosition() * vD;
				j++;
			}
		}
		return dataPoints;
	}

	/**
	 * Add series of circular ROIs to the ROI Manager based on the centre and
	 * radius of a sphere
	 *
	 * @param imp Needed for decalibration of calibrated (x,y,z) r values
	 * @param roiMan Instance of the ROI Manager i.e. RoiManager.getInstance().
	 * @param sphereDim calibrated centroid (x, y, z) and radius
	 * @param clearRois empty ROI manager before adding the ROIs.
	 * @throws IllegalArgumentException if roiMan is null, rather than
	 *           instantiating RoiManager.
	 */
	private static void addToRoiManager(final ImagePlus imp,
		final RoiManager roiMan, final double[] sphereDim, final boolean clearRois)
		throws IllegalArgumentException
	{
		if (roiMan == null) throw new IllegalArgumentException(
			"ROI Manager has not been instantiated");
		if (clearRois) {
			deleteAll(roiMan);
		}
		final Calibration cal = imp.getCalibration();
		final double xs = sphereDim[0];
		final int xi = (int) (xs / cal.pixelWidth);
		final double ys = sphereDim[1];
		final int yi = (int) (ys / cal.pixelHeight);
		final double r = sphereDim[3];
		final int zc = (int) Math.round(sphereDim[2] / cal.pixelDepth);
		final int rz = (int) Math.round(r / cal.pixelDepth);
		final int zStart = Math.max(zc - rz, 1);
		final int zEnd = Math.min(zc + rz, imp.getImageStackSize());
		for (int z = zStart; z <= zEnd; z++) { // iterate through z slices
			final double zd = (zc - z) * cal.pixelDepth;
			final double rc = Math.sqrt(r * r - zd * zd);
			final int wi = (int) (rc / cal.pixelWidth);
			final int hi = (int) (rc / cal.pixelHeight);
			final OvalRoi ellipse = new OvalRoi(xi - wi, yi - hi, wi * 2, hi * 2);
			ellipse.setPosition(z);
			roiMan.addRoi(ellipse);
		}
	}

	private ImagePlus copyInnerCube(final ImagePlus imp, final double cropFactor,
		final double[] sphereDim)
	{
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final double xC = sphereDim[0];
		final double yC = sphereDim[1];
		final double zC = sphereDim[2];
		final double r = sphereDim[3];
		final double h = r * cropFactor / Math.sqrt(3);
		int startX = (int) Math.round((xC - h) / vW);
		int startY = (int) Math.round((yC - h) / vH);
		int startZ = (int) Math.round((zC - h) / vD);
		int roiWidth = (int) Math.round(2 * h / vW);
		int roiHeight = (int) Math.round(2 * h / vH);
		int roiDepth = (int) Math.round(2 * h / vD);
		// bounds checking
		if (startX < 0) startX = 0;
		if (startY < 0) startY = 0;
		if (startZ < 1) startZ = 1;
		if (startX + roiWidth > imp.getWidth()) roiWidth = imp.getWidth() - startX;
		if (startY + roiHeight > imp.getHeight()) roiHeight = imp.getHeight() -
			startY;
		if (startZ + roiDepth > imp.getStackSize()) roiDepth = imp.getStackSize() -
			startZ;
		final ImageStack sourceStack = imp.getStack();
		final ImageStack targetStack = new ImageStack(roiWidth, roiHeight,
			roiDepth);
		final int endZ = startZ + roiDepth;
		final int endY = startY + roiHeight;
		final int endX = startX + roiWidth;
		for (int z = startZ; z < endZ; z++) {
			IJ.showProgress(z - startZ, roiDepth);
			IJ.showStatus("Copying largest enclosed cube");
			final int tZ = z - startZ + 1;
			targetStack.setPixels(Moments.getEmptyPixels(roiWidth, roiHeight, imp
				.getBitDepth()), tZ);
			targetStack.setSliceLabel(sourceStack.getShortSliceLabel(z), tZ);
			final ImageProcessor ip = sourceStack.getProcessor(z);
			final ImageProcessor targetIP = targetStack.getProcessor(tZ);
			for (int y = startY; y < endY; y++) {
				final int tY = y - startY;
				for (int x = startX; x < endX; x++) {
					targetIP.set(x - startX, tY, ip.get(x, y));
				}
			}
		}
		final ImagePlus target = new ImagePlus("Inner Cube", targetStack);
		target.setCalibration(cal);
		target.setDisplayRange(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());
		return target;
	}

	private ImagePlus copyOuterCube(final ImagePlus imp, final double cropFactor,
		final double[] sphereDim)
	{
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final double xC = sphereDim[0];
		final double yC = sphereDim[1];
		final double zC = sphereDim[2];
		final double r = sphereDim[3];
		final double h = r * cropFactor;
		int startX = (int) Math.round((xC - h) / vW);
		int startY = (int) Math.round((yC - h) / vH);
		int startZ = (int) Math.round((zC - h) / vD);
		int roiWidth = (int) Math.round(2 * h / vW);
		int roiHeight = (int) Math.round(2 * h / vH);
		int roiDepth = (int) Math.round(2 * h / vD);
		// bounds checking
		if (startX < 0) startX = 0;
		if (startY < 0) startY = 0;
		if (startZ < 1) startZ = 1;
		if (startX + roiWidth > imp.getWidth()) roiWidth = imp.getWidth() - startX;
		if (startY + roiHeight > imp.getHeight()) roiHeight = imp.getHeight() -
			startY;
		if (startZ + roiDepth > imp.getStackSize()) roiDepth = imp.getStackSize() -
			startZ;
		final ImageStack sourceStack = imp.getImageStack();
		final ImageStack targetStack = new ImageStack(roiWidth, roiHeight,
			roiDepth);
		final int endZ = startZ + roiDepth;
		final int endY = startY + roiHeight;
		final int endX = startX + roiWidth;
		for (int z = startZ; z < endZ; z++) {
			final int tZ = z - startZ + 1;
			IJ.showProgress(z - startZ, roiDepth);
			IJ.showStatus("Copying smallest enclosing cube");
			targetStack.setPixels(Moments.getEmptyPixels(roiWidth, roiHeight, imp
				.getBitDepth()), tZ);
			targetStack.setSliceLabel(sourceStack.getShortSliceLabel(z), tZ);
			final ImageProcessor ip = sourceStack.getProcessor(z);
			final ImageProcessor targetIP = targetStack.getProcessor(tZ);
			for (int y = startY; y < endY; y++) {
				final int tY = y - startY;
				for (int x = startX; x < endX; x++) {
					targetIP.set(x - startX, tY, ip.get(x, y));
				}
			}
		}
		final ImagePlus target = new ImagePlus("Outer Cube", targetStack);
		target.setCalibration(cal);
		target.setDisplayRange(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());
		return target;
	}

	private ImagePlus copySphere(final ImagePlus imp, final int padding,
		final double cropFactor, final double[] sphereDim)
	{
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final double xC = sphereDim[0];
		final double yC = sphereDim[1];
		final double zC = sphereDim[2];
		final double r = sphereDim[3];
		final double maxRad = r * cropFactor;
		int startX = (int) Math.round((xC - maxRad) / vW) - padding;
		int startY = (int) Math.round((yC - maxRad) / vH) - padding;
		int startZ = (int) Math.round((zC - maxRad) / vD) - padding;
		int roiWidth = (int) Math.round(2 * maxRad / vW) + 2 * padding;
		int roiHeight = (int) Math.round(2 * maxRad / vH) + 2 * padding;
		int roiDepth = (int) Math.round(2 * maxRad / vD) + 2 * padding;
		// bounds checking
		if (startX < 0) startX = 0;
		if (startY < 0) startY = 0;
		if (startZ < 1) startZ = 1;
		if (startX + roiWidth > imp.getWidth()) roiWidth = imp.getWidth() - startX;
		if (startY + roiHeight > imp.getHeight()) roiHeight = imp.getHeight() -
			startY;
		if (startZ + roiDepth > imp.getStackSize()) roiDepth = imp.getStackSize() -
			startZ;
		final ImageStack sourceStack = imp.getImageStack();
		final ImageStack targetStack = new ImageStack(roiWidth, roiHeight,
			roiDepth);
		final int endZ = startZ + roiDepth;
		final int endY = startY + roiHeight;
		final int endX = startX + roiWidth;
		for (int z = startZ; z < endZ; z++) {
			IJ.showProgress(z - startZ, roiDepth);
			IJ.showStatus("Copying sphere to new stack");
			final int tZ = z - startZ + 1;
			final double dZ = z * vD - zC;
			final double dZ2 = dZ * dZ;
			targetStack.setPixels(Moments.getEmptyPixels(roiWidth, roiHeight, imp
				.getBitDepth()), tZ);
			targetStack.setSliceLabel(sourceStack.getShortSliceLabel(z), tZ);
			final ImageProcessor ip = sourceStack.getProcessor(z);
			final ImageProcessor targetIP = targetStack.getProcessor(tZ);
			for (int y = startY; y < endY; y++) {
				final int tY = y - startY;
				final double dY = y * vH - yC;
				final double dY2 = dY * dY;
				for (int x = startX; x < endX; x++) {
					final double dX = x * vW - xC;
					final double distance = Math.sqrt(dX * dX + dY2 + dZ2);
					if (distance > maxRad) continue;
					targetIP.set(x - startX, tY, ip.get(x, y));
				}
			}
		}
		final ImagePlus target = new ImagePlus("Sphere", targetStack);
		target.setCalibration(cal);
		target.setDisplayRange(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());
		return target;
	}
}
