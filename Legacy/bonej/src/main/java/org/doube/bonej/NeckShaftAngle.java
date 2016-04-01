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
package org.doube.bonej;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;

import org.doube.geometry.FitCircle;
import org.doube.geometry.FitSphere;
import org.doube.geometry.Trig;
import org.doube.geometry.Vectors;
import org.doube.util.DialogModifier;
import org.doube.util.ImageCheck;
import org.doube.util.MatrixUtils;
import org.doube.util.ResultInserter;
import org.doube.util.RoiMan;
import org.doube.util.ThresholdGuesser;
import org.doube.util.UsageReporter;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

/**
 * <p>
 * Neck Shaft Angle<br />
 *
 * Tool to calculate the neck shaft angle of 3D images of femora.
 * </p>
 * <p>
 * Neck shaft angle is the angle formed at the intersection between the coplanar
 * lines <b>N</b> and <b>S</b>. <br>
 * <b>S</b> is the singular value decomposition (orthogonal distance regression)
 * vector that passes through the centroid (<b>B</b>) of the bone and describes
 * the long axis of the bone.<br/>
 * <b>C</b> is the centre of a sphere fit to the femoral head.<br/>
 *
 * <b>P</b> is the plane that contains <b>S</b> and <b>C</b>. <br />
 * <b>N</b> is the projection onto <b>P</b> of a vector originating at <b>C</b>
 * and passing through the 'middle' of the femoral neck.<br />
 *
 * Singular value decomposition performed with the
 * <a href="http://math.nist.gov/javanumerics/jama/">Jama</a> package
 * </p>
 *
 * @author Michael Doube
 * @version 0.1
 */
public class NeckShaftAngle implements PlugIn, MouseListener, DialogListener {

	private ImageCanvas canvas;

	private double[] headCentre;

	private double[][] shaftVector;

	private double[] centroid;

	private Calibration cal;

	private boolean fieldUpdated = false;

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment())
			return;
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
			return;
		}
		final ImageCheck ic = new ImageCheck();
		if (!ImageCheck.isMultiSlice(imp)) {
			IJ.error("A stack must be open");
			return;
		}

		final double[] thresholds = ThresholdGuesser.setDefaultThreshold(imp);
		double min = thresholds[0];
		double max = thresholds[1];
		String pixUnits;
		if (ImageCheck.huCalibrated(imp)) {
			pixUnits = "HU";
			fieldUpdated = true;
		} else
			pixUnits = "grey";
		cal = imp.getCalibration();

		final GenericDialog gd = new GenericDialog("Setup");
		gd.addNumericField("Shaft Start Slice:", 1, 0);
		gd.addNumericField("Shaft End Slice:", imp.getImageStackSize(), 0);

		gd.addCheckbox("HU Calibrated", ImageCheck.huCalibrated(imp));
		gd.addNumericField("Bone Min:", min, 1, 6, pixUnits + " ");
		gd.addNumericField("Bone Max:", max, 1, 6, pixUnits + " ");
		gd.addMessage("Only pixels >= bone min\n" + "and <= bone max are used.");
		gd.addCheckbox("Calculate curvature", true);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}
		final int startSlice = (int) gd.getNextNumber();
		final int endSlice = (int) gd.getNextNumber();
		final boolean isHUCalibrated = gd.getNextBoolean();
		min = gd.getNextNumber();
		max = gd.getNextNumber();
		if (isHUCalibrated) {
			min = cal.getRawValue(min);
			max = cal.getRawValue(max);
		}
		final boolean doCurvature = gd.getNextBoolean();

		// get coordinates from the ROI manager and fit a sphere
		final RoiManager roiMan = RoiManager.getInstance();
		if (roiMan == null) {
			IJ.run("ROI Manager...");
			IJ.error("Please populate ROI Manager with point ROIs\n" + "placed on the boundary of the femoral head");
			return;
		}
		final double[][] points = RoiMan.getRoiManPoints(imp, roiMan);
		try {
			this.headCentre = FitSphere.fitSphere(points);
		} catch (final IllegalArgumentException ia) {
			IJ.showMessage(ia.getMessage());
			return;
		} catch (final RuntimeException re) {
			IJ.showMessage("Can't fit sphere to points.\n" + "Add more point ROI's to the ROI Manager and try again.");
			return;
		}
		final ImageWindow win = imp.getWindow();
		this.canvas = win.getCanvas();

		// work out the centroid and regression vector of the bone
		final Moments m = new Moments();
		final double[] centroid = m.getCentroid3D(imp, startSlice, endSlice, min, max, 0, 1);
		this.centroid = centroid;
		if (centroid[0] < 0) {
			IJ.error("Empty Stack", "No voxels available for calculation." + "\nCheck your ROI and threshold.");
			return;
		}

		this.shaftVector = regression3D(imp, centroid, startSlice, endSlice, min, max);
		if (this.shaftVector == null)
			return;

		if (doCurvature)
			calculateCurvature(imp, this.shaftVector, this.headCentre, centroid, startSlice, endSlice, min, max);

		// remove stale MouseListeners
		final MouseListener[] l = this.canvas.getMouseListeners();
		for (int n = 0; n < l.length; n++) {
			this.canvas.removeMouseListener(l[n]);
		}
		// add a new MouseListener
		this.canvas.addMouseListener(this);

		new WaitForUserDialog("Click on the middle of the femoral neck.\n" + "Neck-shaft angle and out-of-plane skew\n"
				+ "will be recorded until you hit \'OK\'").show();
		this.canvas.removeMouseListener(this);
		UsageReporter.reportEvent(this).send();
		return;
	}

	/**
	 * Calculate the vector associated with the projection plane from the
	 * regression vector and the vector connecting the centroid and the femoral
	 * head centre
	 *
	 * @param shaftVector
	 *            double[][]
	 * @param headCentre
	 *            double[][]
	 * @param centroid
	 *            double[][]
	 * @return double[][] projectionPlane
	 */
	private double[][] getProjectionPlane(final double[][] shaftVector, final double[] headCentre,
			final double[] centroid) {
		// have to calculate distance between points
		// so that we find a unit vector

		double d = Trig.distance3D(headCentre, centroid);
		final double[][] cHVec = new double[3][1];
		cHVec[0][0] = (headCentre[0] - centroid[0]) / d;
		cHVec[1][0] = (headCentre[1] - centroid[1]) / d;
		cHVec[2][0] = (headCentre[2] - centroid[2]) / d;

		final Matrix cH = new Matrix(cHVec);
		MatrixUtils.printToIJLog(cH, "cHVec");

		// projectionPlane is the cross product of cHVec and shaftVector
		final double[][] projectionPlane = Vectors.crossProduct(cHVec, shaftVector);

		d = Trig.distance3D(projectionPlane[0][0], projectionPlane[1][0], projectionPlane[2][0]);
		projectionPlane[0][0] /= d;
		projectionPlane[1][0] /= d;
		projectionPlane[2][0] /= d;

		return projectionPlane;
	}

	/**
	 * Calculate the orthogonal distance regression plane of a set of points by
	 * the covariance method and Singular Value Decomposition
	 *
	 * @param stack
	 * @param centroid
	 * @return SingularValueDecomposition containing eigenvector and eigenvalue
	 *
	 * @see <a href="http://mathforum.org/library/drmath/view/63765.html">
	 *      Description on Ask Dr Math</a>
	 *
	 */
	private double[][] regression3D(final ImagePlus imp, final double[] centroid, final int startSlice,
			final int endSlice, final double min, final double max) {
		IJ.showStatus("Calculating SVD");
		final ImageStack stack = imp.getImageStack();
		final Rectangle r = stack.getRoi();
		final double cX = centroid[0];
		final double cY = centroid[1];
		final double cZ = centroid[2];
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final int rX = r.x;
		final int rY = r.y;
		final int rW = r.x + r.width;
		final int rH = r.y + r.height;
		double sDxDx = 0;
		double sDyDy = 0;
		double sDzDz = 0;
		double sDxDy = 0;
		double sDxDz = 0;
		double sDyDz = 0;
		double count = 0;
		for (int z = startSlice; z <= endSlice; z++) {
			IJ.showStatus("Getting covariance matrix...");
			IJ.showProgress(z, endSlice);
			final double dz = z * vD - cZ;
			final double dzdz = dz * dz;
			final ImageProcessor ip = stack.getProcessor(z);
			for (int y = rY; y < rH; y++) {
				final double dy = y * vH - cY;
				final double dydy = dy * dy;
				final double dydz = dy * dz;
				for (int x = rX; x < rW; x++) {
					final double testPixel = ip.get(x, y);
					if (testPixel >= min && testPixel <= max) {
						final double dx = x * vW - cX;
						sDxDx += dx * dx;
						sDyDy += dydy;
						sDzDz += dzdz;
						sDxDy += dx * dy;
						sDxDz += dx * dz;
						sDyDz += dydz;
						count++;
					}
				}
			}
		}
		if (count == 0) {
			IJ.log("Count == 0");
			return null;
		}
		final double[][] C = new double[3][3];
		C[0][0] = sDxDx;
		C[1][1] = sDyDy;
		C[2][2] = sDzDz;
		C[0][1] = sDxDy;
		C[0][2] = sDxDz;
		C[1][0] = sDxDy;
		C[1][2] = sDyDz;
		C[2][0] = sDxDz;
		C[2][1] = sDyDz;
		final double invCount = 1 / count;
		final Matrix covarianceMatrix = new Matrix(C).times(invCount);
		MatrixUtils.printToIJLog(covarianceMatrix, "Covariance matrix");
		final SingularValueDecomposition S = new SingularValueDecomposition(covarianceMatrix);
		final Matrix leftVectors = S.getU();
		MatrixUtils.printToIJLog(leftVectors, "Left vectors");
		final double[][] orthogonalDistanceRegression = new double[3][1];
		orthogonalDistanceRegression[0][0] = leftVectors.get(0, 0);
		orthogonalDistanceRegression[1][0] = leftVectors.get(1, 0);
		orthogonalDistanceRegression[2][0] = leftVectors.get(2, 0);
		return orthogonalDistanceRegression;
	}/* end Regression3D */

	private double[][] neckVector(final double[] headCentre, final double[] neckPoint) {
		// have to calculate d to make sure that neckVector is a unit vector
		final double d = Trig.distance3D(headCentre, neckPoint);

		final double[][] neckVector = new double[3][1];
		neckVector[0][0] = (headCentre[0] - neckPoint[0]) / d;
		neckVector[1][0] = (headCentre[1] - neckPoint[1]) / d;
		neckVector[2][0] = (headCentre[2] - neckPoint[2]) / d;
		return neckVector;
	}

	private void calculateAngles(final ImagePlus imp, final double[] neckPoint) {
		final double[][] neckVector = neckVector(headCentre, neckPoint);
		final double[][] projectionPlane = getProjectionPlane(shaftVector, headCentre, this.centroid);
		final double[][] neckPlane = Vectors.crossProduct(neckVector, projectionPlane);
		final double[][] testVector = Vectors.crossProduct(projectionPlane, neckPlane);
		// P . Q = ||P|| ||Q|| cos(a) so if P and Q are unit vectors, then P.Q =
		// cos(a)
		final Matrix PP = new Matrix(projectionPlane);
		MatrixUtils.printToIJLog(PP, "projectionPlane");

		final Matrix tV = new Matrix(testVector);
		MatrixUtils.printToIJLog(tV, "testVector");

		final Matrix sV = new Matrix(shaftVector);
		MatrixUtils.printToIJLog(sV, "shaftVector");

		final Matrix nV = new Matrix(neckVector);
		MatrixUtils.printToIJLog(nV, "neckVector");

		final double cosA1 = sV.get(0, 0) * tV.get(0, 0) + sV.get(1, 0) * tV.get(1, 0) + sV.get(2, 0) * tV.get(2, 0);
		// printMatrix(cosA1, "cosA1");
		IJ.log("cosA1: " + cosA1);

		final double cosA2 = nV.get(0, 0) * tV.get(0, 0) + nV.get(1, 0) * tV.get(1, 0) + nV.get(2, 0) * tV.get(2, 0);
		// printMatrix(cosA2, "cosA2");
		IJ.log("cosA2: " + cosA2);

		final double neckShaftAngle = Math.acos(cosA1);
		final double neckShaftSkew = Math.acos(cosA2);
		final ResultInserter ri = ResultInserter.getInstance();
		ri.setResultInRow(imp, "Angle (rad)", neckShaftAngle);
		ri.setResultInRow(imp, "Skew (rad)", neckShaftSkew);
		ri.updateTable();
		return;
	}

	/**
	 * <p>
	 * Calculate curvature of bone using shaft vector as a reference axis and
	 * centre of femoral head to define reference plane
	 * </p>
	 *
	 * @param stack
	 * @param shaftVector
	 * @param headCentre
	 */
	private void calculateCurvature(final ImagePlus imp, final double[][] shaftVector, final double[] headCentre,
			final double[] centroid, final int startSlice, final int endSlice, final double min, final double max) {
		// calculate the eigenvector of the reference plane containing
		// the shaftVector and the headCentre

		// get the 2D centroids
		final ImageStack stack = imp.getImageStack();
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final Rectangle r = stack.getRoi();
		final int rW = r.x + r.width;
		final int rH = r.y + r.height;
		// pixel counters

		final boolean[] emptySlices = new boolean[stack.getSize() + 1];
		final double[] cortArea = new double[stack.getSize() + 1];
		final double[][] sliceCentroids = new double[2][stack.getSize() + 1];

		final double pixelArea = vW * vH;
		for (int s = startSlice; s <= endSlice; s++) {
			double sumX = 0;
			double sumY = 0;
			double cslice = 0;
			final ImageProcessor ip = stack.getProcessor(s);
			for (int y = r.y; y < rH; y++) {
				for (int x = r.x; x < rW; x++) {
					final double pixel = ip.get(x, y);
					if (pixel >= min && pixel <= max) {
						cslice++;
						cortArea[s] += pixelArea;
						sumX += x * vW;
						sumY += y * vH;
					}
				}
			}
			if (cslice > 0) {
				sliceCentroids[0][s] = sumX / cslice;
				sliceCentroids[1][s] = sumY / cslice;
				emptySlices[s] = false;
			} else {
				emptySlices[s] = true;
			}
		}

		final double[][] projPlane = getProjectionPlane(shaftVector, headCentre, centroid);
		final double pPx = projPlane[0][0];
		final double pPy = projPlane[1][0];
		final double pPz = projPlane[2][0];

		final double x1x = centroid[0];
		final double x1y = centroid[1];
		final double x1z = centroid[2];
		final double x2x = x1x + shaftVector[0][0];
		final double x2y = x1y + shaftVector[1][0];
		final double x2z = x1z + shaftVector[2][0];

		// for each centroid, calculate the vector to the 3D regression line
		// using equation 10 from
		// http://mathworld.wolfram.com/Point-LineDistance3-Dimensional.html
		// with denominator = 1 because we use a unit vector for |(x2 - x1)|

		final double[][] mL = new double[endSlice - startSlice + 1][2];
		final double[][] cC = new double[endSlice - startSlice + 1][2];
		int i = 0;
		for (int s = startSlice; s <= endSlice; s++) {
			if (!emptySlices[s]) {
				final double x0x = sliceCentroids[0][s];
				final double x0y = sliceCentroids[1][s];
				final double x0z = s * vD;

				// distance is magnitude of cross product of (x0 - x1) and (x0 -
				// x2)
				final double[] a = { x0x - x1x, x0y - x1y, x0z - x1z };
				final double[] b = { x0x - x2x, x0y - x2y, x0z - x2z };

				final double[] cp = Vectors.crossProduct(a, b);

				final double distance = Trig.distance3D(cp);
				// IJ.log("distance to regression line is "+ distance +
				// " "+this.units+" for slice "+s);

				// work out t (number of unit vectors from centroid along
				// regression)
				// as per equation 3
				final double t = -1 * Trig.distance3D(x1x - x0x, x1y - x0y, x1z - x0z, x2x - x1x, x2y - x1y, x2z - x1z);

				// So now the intersection point x3 of the perpendicular is
				// known
				// as centroid + t * unitVector
				// and the vector to the deflection as (x0 - x3)

				final double x3x = x1x + t * shaftVector[0][0];
				final double x3y = x1y + t * shaftVector[1][0];
				final double x3z = x1z + t * shaftVector[2][0];

				final double defVectX = x0x - x3x;
				final double defVectY = x0y - x3y;
				final double defVectZ = x0z - x3z;

				// project deflection vector onto projection plane vector by
				// taking the dot product
				// this is the craniocaudal deflection

				final double cranioCaudal = (defVectX * pPx + defVectY * pPy + defVectZ * pPz);
				cC[i][0] = cranioCaudal;
				cC[i][1] = t;
				// IJ.log("Craniocaudal deflection at slice "+s+" is
				// "+cranioCaudal);

				// mediolateral deflection is distance in projectionPlane, i.e.
				// deflection projected onto projectionPlane // double cross
				// product
				// B x (A x B), provided that B is a unit vector

				final double aBx = defVectY * pPz - defVectZ * pPy;
				final double aBy = defVectZ * pPx - defVectX * pPz;
				final double aBz = defVectX * pPy - defVectY * pPx;

				final double mLx = pPy * aBz - pPz * aBy;
				final double mLy = pPz * aBx - pPx * aBz;
				final double mLz = pPx * aBy - pPy * aBx;

				double medioLateral = Math.sqrt(mLx * mLx + mLy * mLy + mLz * mLz);

				// give the scalar a direction
				final double sign = (mLx * mLy * mLz) / Math.abs(mLx * mLy * mLz);

				medioLateral *= sign;

				// IJ.log("Mediolateral deflection at slice "+s+" is
				// "+medioLateral);
				IJ.log(s + "," + t + ", " + distance + ", " + medioLateral + ", " + cranioCaudal);
				mL[i][0] = medioLateral;
				mL[i][1] = t;
				i++;
			} else {
				// IJ.log("No pixels to calculate centroid in slice "+s);
			}
		}
		// Calculate circle fitting for mL and cC deflections
		final ResultInserter ri = ResultInserter.getInstance();
		final String units = cal.getUnits();

		final double[] mLabR = FitCircle.hyperStable(mL);
		final double[] cCabR = FitCircle.hyperStable(cC);
		ri.setResultInRow(imp, "M-L radius (" + units + ")", mLabR[2]);
		ri.setResultInRow(imp, "M-L centre X (" + units + ")", mLabR[0]);
		ri.setResultInRow(imp, "M-L centre Y (" + units + ")", mLabR[1]);
		ri.setResultInRow(imp, "Cr-Ca radius (" + units + ")", cCabR[2]);
		ri.setResultInRow(imp, "Cr-Ca centre X (" + units + ")", cCabR[0]);
		ri.setResultInRow(imp, "Cr-Ca centre Y (" + units + ")", cCabR[1]);
		ri.updateTable();
		return;
	}

	@Override
	public void mousePressed(final MouseEvent e) {
		final ImagePlus imp = IJ.getImage();
		final Calibration cal = imp.getCalibration();
		final int x = canvas.offScreenX(e.getX());
		final int y = canvas.offScreenY(e.getY());
		final int z = imp.getCurrentSlice();
		final double[] neckPoint = { x * cal.pixelWidth, y * cal.pixelHeight, z * cal.pixelDepth };
		IJ.log("neckPoint: (" + neckPoint[0] + "," + neckPoint[1] + ", " + neckPoint[2] + ")");
		calculateAngles(imp, neckPoint);
	}

	@Override
	public void mouseReleased(final MouseEvent e) {
	}

	@Override
	public void mouseExited(final MouseEvent e) {
	}

	@Override
	public void mouseClicked(final MouseEvent e) {
	}

	@Override
	public void mouseEntered(final MouseEvent e) {
	}

	public void mouseMoved(final MouseEvent e) {
	}

	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
		if (!DialogModifier.allNumbersValid(gd.getNumericFields()))
			return false;
		final Vector<?> checkboxes = gd.getCheckboxes();
		final Vector<?> nFields = gd.getNumericFields();
		final Checkbox box0 = (Checkbox) checkboxes.get(0);
		final boolean isHUCalibrated = box0.getState();
		final TextField minT = (TextField) nFields.get(2);
		final TextField maxT = (TextField) nFields.get(3);
		final double min = Double.parseDouble(minT.getText().replace("∞", "Infinity"));
		final double max = Double.parseDouble(maxT.getText().replace("∞", "Infinity"));

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
		if (isHUCalibrated)
			DialogModifier.replaceUnitString(gd, "grey", "HU");
		else
			DialogModifier.replaceUnitString(gd, "HU", "grey");
		DialogModifier.registerMacroValues(gd, gd.getComponents());
		return true;
	}

}
