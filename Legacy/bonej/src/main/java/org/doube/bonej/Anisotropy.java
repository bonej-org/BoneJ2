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
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ListIterator;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
// for 3D plotting of coordinates

import org.doube.geometry.FitEllipsoid;
import org.doube.geometry.Vectors;
import org.doube.util.DialogModifier;
import org.doube.util.ImageCheck;
import org.doube.util.MatrixUtils;
import org.doube.util.Multithreader;
import org.doube.util.ResultInserter;
import org.doube.util.UsageReporter;
import org.scijava.vecmath.Color3f;
// for 3D plotting of coordinates
import org.scijava.vecmath.Point3f;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import customnode.CustomPointMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij3d.Content;

// for 3D plotting of coordinates

import ij3d.Image3DUniverse;

/**
 * <p>
 * Calculate degree of anisotropy using the mean intercept length method.
 * Samples vectors in a sphere; the location of the sphere is random within the
 * stack. New sampling spheres are generated until the coefficient of variation
 * of the anisotropy result reduces to a user-set threshold or a maximum number
 * of spheres has been sampled.
 * </p>
 *
 * @see
 * 		<p>
 *      Harrigan TP, Mann RW (1984) Characterization of microstructural
 *      anisotropy in orthotropic materials using a second rank tensor. J Mater
 *      Sci 19: 761-767.
 *      <a href="http://dx.doi.org/10.1007/BF00540446">doi:10.1007/BF00540446
 *      </a>.
 *      </p>
 * @author Michael Doube
 *
 */
public class Anisotropy implements PlugIn, DialogListener {

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment()) {
			return;
		}
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
			return;
		}
		final ImageCheck ic = new ImageCheck();
		if (!ImageCheck.isBinary(imp)) {
			IJ.error("8-bit binary (black and white only) image required.");
			return;
		}
		if (!ImageCheck.isMultiSlice(imp) || imp.getStackSize() < 5) {
			IJ.error("Stack with at least 5 slices required");
			return;
		}

		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		double vectorSampling = Math.max(vW, Math.max(vH, vD)) * 2.3;
		double radius = Math.min(h * vH, Math.min(d * vD, w * vW)) / 4;

		final GenericDialog gd = new GenericDialog("Setup");
		gd.addCheckbox("Auto Mode", true);
		gd.addCheckbox("Single Sphere", false);
		gd.addNumericField("Radius", radius, 1, 5, cal.getUnits());
		// number of random vectors in vector field
		gd.addNumericField("Vectors", 50000, 0, 6, "vectors");
		gd.addNumericField("Vector_sampling", vectorSampling, 3, 6, cal.getUnits());
		// number of randomly-positioned vector fields
		gd.addNumericField("Min_Spheres", 100, 0, 5, "");
		gd.addNumericField("Max_Spheres", 2000, 0, 5, "");
		gd.addNumericField("Tolerance", 0.005, 4, 6, "");
		gd.addCheckbox("Show_Plot", true);
		gd.addCheckbox("3D_Result", false);
		gd.addCheckbox("Align to fabric tensor", false);
		gd.addCheckbox("Record_Eigens", false);
		gd.addHelp("http://bonej.org/anisotropy");
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}
		final boolean doAutoMode = gd.getNextBoolean();
		final boolean doSingleSphere = gd.getNextBoolean();
		radius = gd.getNextNumber();
		final int nVectors = (int) gd.getNextNumber();
		vectorSampling = gd.getNextNumber();
		final int minSpheres = (int) gd.getNextNumber();
		final int maxSpheres = (int) gd.getNextNumber();
		final double tolerance = gd.getNextNumber();
		final boolean doPlot = gd.getNextBoolean();
		final boolean do3DResult = gd.getNextBoolean();
		final boolean doAlign = gd.getNextBoolean();
		final boolean doEigens = gd.getNextBoolean();

		Object[] result = new Object[3];
		if (doAutoMode && !doSingleSphere)
			result = runToStableResult(imp, minSpheres, maxSpheres, nVectors, radius, vectorSampling, tolerance,
					doPlot);
		else if (doSingleSphere) {
			final double[] centroid = { w * vW / 2, h * vH / 2, d * vD / 2 };
			// radius = Math.min(centroid[0], Math.min(centroid[1],
			// centroid[2]));
			result = calculateSingleSphere(imp, centroid, radius - vectorSampling * 2, vectorSampling, nVectors, false);
		} else
			result = runToStableResult(imp, minSpheres, minSpheres, nVectors, radius, vectorSampling, tolerance,
					doPlot);

		final double da = ((double[]) result[0])[0];
		final double[][] coOrdinates = (double[][]) result[1];

		final ResultInserter ri = ResultInserter.getInstance();
		ri.setResultInRow(imp, "DA", da);
		ri.setResultInRow(imp, "tDA", Math.pow(1 - da, -1));

		if (doEigens) {
			final EigenvalueDecomposition E = (EigenvalueDecomposition) result[2];
			final Matrix eVectors = E.getV();
			MatrixUtils.printToIJLog(eVectors, "Fabric tensor vectors");
			MatrixUtils.printToIJLog(E.getD(), "Fabric tensor values");
			final double[] eValues = E.getRealEigenvalues();
			for (int i = 0; i < 3; i++)
				for (int j = 0; j < 3; j++)
					ri.setResultInRow(imp, "V" + (i + 1) + "," + (j + 1), eVectors.get(i, j));
			for (int i = 0; i < eValues.length; i++)
				ri.setResultInRow(imp, "D" + (i + 1), eValues[i]);
		}

		ri.updateTable();

		if (do3DResult) {
			plotPoints3D(coOrdinates, "Intercept Lengths");
		}

		if (doAlign) {
			final EigenvalueDecomposition E = (EigenvalueDecomposition) result[2];
			final Moments m = new Moments();
			final ImagePlus alignedImp = m.alignImage(imp, E.getV(), false, 1, d, 128, 255, 0, 1);
			alignedImp.show();
		}
		UsageReporter.reportEvent(this).send();
		return;
	}

	/**
	 * Calculate degree of anisotropy for a binary stack, running until a stable
	 * result is achieved, or the maximum number of iterations occurs.
	 *
	 * @param imp
	 *            ImagePlus input. A binary stack is required.
	 * @param minSpheres
	 *            minimum number of iterations
	 * @param maxSpheres
	 *            maximum number of iterations
	 * @param nVectors
	 *            number of vectors in the sampling sphere
	 * @param radius
	 *            radius of the sampling sphere
	 * @param vectorSampling
	 *            distance between samples along the sampling vector. Set to 2.3
	 *            * maximum voxel dimension for a safe and efficient sampling
	 *            increment
	 * @param tolerance
	 *            coefficient of variation of results at which we accept result
	 *            is stable
	 * @param doPlot
	 *            set to true if you want to see a plot of anisotropy versus
	 *            number of repeats, updated in real time
	 * @return Object array containing degree of anisotropy, coordinates of rose
	 *         plot and Eigenvalue decomposition (fabric tensor)
	 */
	public Object[] runToStableResult(final ImagePlus imp, final int minSpheres, final int maxSpheres,
			final int nVectors, final double radius, final double vectorSampling, final double tolerance,
			boolean doPlot) {
		final int minIterations = minSpheres;
		final int maxIterations = maxSpheres;
		final double[][] vectorList = Vectors.randomVectors(nVectors);
		double variance = Double.NaN;
		double anisotropy = Double.NaN;
		double[][] centroidList = new double[1][3];
		final double[] centroid = new double[3];
		double[] interceptCounts = new double[nVectors];
		final double[] sumInterceptCounts = new double[nVectors];
		double[][] coOrdinates = new double[nVectors][3];
		ImagePlus plotImage = new ImagePlus();
		// never show a plot if this method is being called from a batch mode
		// macro
		if (Interpreter.isBatchMode())
			doPlot = false;
		if (doPlot) {
			plotImage = createGraph(imp.getTitle());
			plotImage.show();
		}
		final Vector<Double> anisotropyHistory = new Vector<Double>();
		final Vector<Double> errorHistory = new Vector<Double>();
		final double[][] emptyArray = new double[3][3];
		final Matrix emptyMatrix = new Matrix(emptyArray);
		EigenvalueDecomposition E = new EigenvalueDecomposition(emptyMatrix);
		int s = 0;
		while (s < minIterations || (s >= minIterations && s < maxIterations && variance > tolerance)) {
			s++;
			// return a single centroid within the bounds
			centroidList = gridCalculator(imp, 1, radius);
			// count intercepts at centroid
			centroid[0] = centroidList[0][0];
			centroid[1] = centroidList[0][1];
			centroid[2] = centroidList[0][2];
			IJ.showStatus("Counting intercepts at site " + s + ", anisotropy = " + IJ.d2s(anisotropy, 5) + ", CV = "
					+ IJ.d2s(variance, 3));
			interceptCounts = countIntercepts(imp, centroid, vectorList, nVectors, radius, vectorSampling);

			// add intercepts to vectors
			for (int i = 0; i < nVectors; i++) {
				sumInterceptCounts[i] += interceptCounts[i];
			}

			// work out the current mean intercept length
			final double[] meanInterceptLengths = new double[nVectors];
			final double probeLength = radius * s;
			for (int v = 0; v < nVectors; v++) {
				if (sumInterceptCounts[v] == 0)
					meanInterceptLengths[v] = probeLength;
				// MIL = total vector length / number of intercepts
				// +1 is to avoid divide-by-zero errors, other approach
				// is to replace 0 by 1
				else
					meanInterceptLengths[v] = probeLength / sumInterceptCounts[v];
			}
			// work out coordinates of vector cloud
			coOrdinates = calculateCoordinates(meanInterceptLengths, vectorList);
			final Object[] result = harriganMann(coOrdinates);
			anisotropy = ((double[]) result[0])[0];
			anisotropyHistory.add(anisotropy);
			E = (EigenvalueDecomposition) result[1];

			variance = getVariance(anisotropyHistory, minIterations);

			if (variance + anisotropy > 1 || anisotropy - variance < 0) {
				variance = Math.max(Math.min(1 - anisotropy, anisotropy), tolerance);
			}

			errorHistory.add(variance);
			if (doPlot)
				updateGraph(plotImage, anisotropyHistory, errorHistory);
		}
		final double[] da = { anisotropy };
		final Object[] result = { da, coOrdinates, E };
		return result;
	}

	/**
	 * Calculate anisotropy using a single sampling sphere
	 *
	 * @param imp
	 *            binary stack
	 * @param centroid
	 *            3 element array containing (x,y,z) coordinates of the centroid
	 * @param radius
	 *            radius of the single sphere
	 * @param vectorSampling
	 *            sampling step within each vector
	 * @param nVectors
	 *            number of sampling vectors
	 * @param randomVectors
	 *            if true, use randomly distributed vectors, otherwise use
	 *            regularly distributed vectors
	 * @return Object array containing the degree of anisotropy in a one-element
	 *         array, the coordinate array of the rose plot and
	 *         Eigendecomposition
	 */
	public Object[] calculateSingleSphere(final ImagePlus imp, final double[] centroid, final double radius,
			final double vectorSampling, final int nVectors, final boolean randomVectors)
					throws IllegalArgumentException {
		IJ.log("Single sphere parameters:");
		IJ.log(imp.getTitle() + " centroid: " + centroid[0] + ", " + centroid[1] + ", " + centroid[2] + ", radius: "
				+ radius + ", vectorSampling: " + vectorSampling + ", nVectors: " + nVectors + ", randomVectors: "
				+ randomVectors);

		final double[][] vectorList = Vectors.regularVectors(nVectors);
		double[] interceptCounts;
		interceptCounts = countIntercepts(imp, centroid, vectorList, nVectors, radius, vectorSampling);
		final double[] meanInterceptLengths = new double[nVectors];
		for (int v = 0; v < nVectors; v++) {
			if (interceptCounts[v] == 0)
				meanInterceptLengths[v] = 0;
			else
				meanInterceptLengths[v] = radius / interceptCounts[v];
		}
		final double[][] coOrdinates = calculateCoordinates(meanInterceptLengths, vectorList);
		final Object[] daResult = harriganMann(coOrdinates);
		final Object[] result = { daResult[0], coOrdinates, daResult[1] };
		return result;
	}

	/**
	 *
	 * @param meanInterceptLengths
	 * @param vectorList
	 * @return
	 */
	private double[][] calculateCoordinates(final double[] meanInterceptLengths, final double[][] vectorList) {
		final ArrayList<double[]> coordList = new ArrayList<double[]>();
		final int nVectors = vectorList.length;
		for (int v = 0; v < nVectors; v++) {
			final double milV = meanInterceptLengths[v];
			if (milV == 0)
				continue;
			final double[] coordinate = { milV * vectorList[v][0], milV * vectorList[v][1], milV * vectorList[v][2] };
			coordList.add(coordinate);
		}
		final double[][] coordinates = new double[coordList.size()][];
		for (int i = 0; i < coordList.size(); i++)
			coordinates[i] = coordList.get(i);

		return coordinates;
	}

	/**
	 * Create a graph for plotting anisotropy results
	 *
	 * @param id
	 *            Identification string for Anisotropy plot
	 * @return ImagePlus for drawing a plot on
	 */
	private ImagePlus createGraph(final String id) {
		final double[] x = { 0 }, y = { 0 };
		final Plot plot = new Plot("Anisotropy", "Repeats", "Anisotropy", x, y);
		plot.setLimits(0, 1, 0, 1);
		final ImageProcessor plotIp = plot.getProcessor();
		final ImagePlus plotImage = new ImagePlus("Anisotropy of " + id, plotIp);
		plotImage.setProcessor(null, plotIp);
		return plotImage;
	}

	/**
	 *
	 * Calculate coefficient of variation of last n results
	 *
	 * @param anisotropyHistory
	 *            list of anisotropy results, one result per iteration
	 *
	 * @return coefficient of variation, which is standard deviation / mean.
	 */

	private double getVariance(final Vector<Double> anisotropyHistory, final int n) {
		final ListIterator<Double> iter = anisotropyHistory.listIterator(anisotropyHistory.size());

		double sum = 0;

		double sumSquares = 0;

		int count = 0;

		while (iter.hasPrevious()) {
			final double value = iter.previous();
			sum += value;
			count++;
			if (count >= n)
				break;
		}

		final double mean = sum / n;
		final ListIterator<Double> itr = anisotropyHistory.listIterator(anisotropyHistory.size());

		count = 0;
		while (itr.hasPrevious()) {
			final double value = itr.previous();
			final double a = value - mean;
			sumSquares += a * a;
			count++;
			if (count >= n)
				break;
		}

		final double stDev = Math.sqrt(sumSquares / n);
		final double coeffVariation = stDev / mean;
		return coeffVariation;
	}

	/*-------------------------------------------------------------------*/
	/**
	 * Generate a set of centroids padded from the image edges
	 *
	 * @param imp
	 *            ImagePlus
	 * @param nCentroids
	 *            Number of centroids to generate
	 * @param radius
	 *            amount of padding between stack edges and centroid field
	 * @return nCentroids x 3 array of 3D coordinates
	 */
	private double[][] gridCalculator(final ImagePlus imp, final int nCentroids, final double radius) {
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		final double stackWidth = vW * w;
		final double stackHeight = vH * h;
		final double stackDepth = vD * d;
		// strategy: n random coordinates within bounding box (easy, no bias.)
		final double[][] gridCentroids = new double[nCentroids][3];
		for (int n = 0; n < nCentroids; n++) {
			gridCentroids[n][0] = Math.random() * (stackWidth - 2 * radius - 2 * vW) + radius;
			gridCentroids[n][1] = Math.random() * (stackHeight - 2 * radius - 2 * vH) + radius;
			gridCentroids[n][2] = Math.random() * (stackDepth - 2 * radius - 2 * vD) + radius;
		}
		// alternative: n regularly-spaced coordinates fitting
		// within bounding box
		// allegedly more efficient but could collide with periodic data

		return gridCentroids;
	} /* end gridCalculator */

	/*------------------------------------------------------*/
	/**
	 * <p>
	 * Counts the number of intercepts between each vector in a set of vectors
	 * and a binary 3D image.
	 * </p>
	 * <p>
	 * Vector length and intercept count should be summed over all vectors with
	 * same direction and sum of length divided by sum of count to give mean
	 * intercept length for each vector.
	 * </p>
	 *
	 *
	 * @param centroid
	 *            3-element array containing calibrated 3D centroid location
	 * @param vectorList
	 *            array containing unit vectors
	 * @param nVectors
	 *            number of vectors in each set
	 * @param radius
	 *            length of vectors
	 * @param vectorSampling
	 *            distance between tests along each vector
	 * @return 1D array containing a count of intercepts for each vector, null
	 *         if the centroid was < radius from sides of the image
	 */
	private double[] countIntercepts(final ImagePlus imp, final double[] centroid, final double[][] vectorList,
			final int nVectors, final double radius, final double vectorSampling) throws IllegalArgumentException {
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;

		final double cX = centroid[0];
		final double cY = centroid[1];
		final double cZ = centroid[2];

		final int width = imp.getWidth();
		final int height = imp.getHeight();
		final int depth = imp.getImageStackSize();

		// if centroid is < radius from the sides of the image, throw exception
		if (cX < radius || cY < radius || cZ < radius || cX > width * vW - radius || cY > height * vH - radius
				|| cZ > depth * vD - radius)
			throw new IllegalArgumentException("Centroid < radius from sides");

		// create a work array containing pixels +- 1 radius from centroid
		final int w = (int) Math.round(radius / vW);
		final int h = (int) Math.round(radius / vH);
		final int d = (int) Math.round(radius / vD);
		final byte[] workArray = new byte[(2 * w + 1) * (2 * h + 1) * (2 * d + 1)];

		final int startCol = (int) Math.round(cX / vW) - w;
		final int endCol = (int) Math.round(cX / vW) + w;
		final int startRow = (int) Math.round(cY / vH) - h;
		final int endRow = (int) Math.round(cY / vH) + h;
		final int startSlice = (int) Math.round(cZ / vD) - d;
		final int endSlice = (int) Math.round(cZ / vD) + d;

		final ImageStack stack = imp.getImageStack();
		// fill the work array
		int i = 0;
		for (int s = startSlice; s <= endSlice; s++) {
			final byte[] slicePixels = (byte[]) stack.getPixels(s + 1);
			for (int r = startRow; r <= endRow; r++) {
				final int index = width * r;
				for (int c = startCol; c <= endCol; c++) {
					workArray[i] = slicePixels[index + c];
					i++;
				}
			}
		}

		// centroid position in workArray is at (w+1, h+1, d+1), subtract one
		// for starting at 0.
		final int a = (2 * w + 1);
		final int b = a * (2 * h + 1);

		final int wVz = b * d;
		final int wVy = a * h;
		final int wVx = w;
		final int centroidIndex = wVz + wVy + wVx;

		// store an intercept count for each vector
		final double[] interceptCounts = new double[nVectors];

		// loop through all vectors
		// start multithreading here - each thread samples a set of vectors
		final double radVw = -radius / vW;
		final double radVh = -radius / vH;
		final double radVd = -radius / vD;

		// new multithread pattern
		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(new Runnable() {
				@Override
				public void run() {
					for (int v = ai.getAndIncrement(); v < nVectors; v = ai.getAndIncrement()) {
						double nIntercepts = 0;
						final double vX = vectorList[v][0];
						final double vY = vectorList[v][1];
						final double vZ = vectorList[v][2];

						// start at negative end of vector
						final int xS = (int) Math.round(radVw * vX);
						final int yS = (int) Math.round(radVh * vY);
						final int zS = (int) Math.round(radVd * vZ);

						final int startIndex = centroidIndex + b * zS + a * yS + xS;
						boolean lastPos, thisPos;
						if (workArray[startIndex] == 0) {
							lastPos = true;
						} else {
							lastPos = false;
						}

						final double vXvW = vX / vW;
						final double vYvH = vY / vH;
						final double vZvD = vZ / vD;

						for (double pos = -radius; pos <= radius; pos += vectorSampling) {
							// find the index of the voxel that the sample falls
							// within offset from centroid
							final int x = (int) Math.round(pos * vXvW);
							final int y = (int) Math.round(pos * vYvH);
							final int z = (int) Math.round(pos * vZvD);
							final int testIndex = centroidIndex + b * z + a * y + x;
							// determine if the voxel is thresholded or not
							if (workArray[testIndex] == 0) {
								thisPos = true;
							} else {
								thisPos = false;
							}
							// if this pos is not equal to last pos then an
							// interface is counted
							if (thisPos != lastPos) {
								nIntercepts++;
							}
							// then before incrementing the for loop, set
							// lastPos to thisPos
							lastPos = thisPos;
						}
						interceptCounts[v] = nIntercepts;
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);
		return interceptCounts;
	}/* end meanInterceptLengths */

	/*--------------------------------------------------------------------------*/
	/**
	 * Draw on plotImage the data in anisotropyHistory with error bars from
	 * errorHistory
	 *
	 * @param plotImage
	 *            the graph image
	 * @param anisotropyHistory
	 *            all anisotropy results, 1 for each iteration
	 * @param errorHistory
	 *            all error results, 1 for each iteration
	 */
	private void updateGraph(final ImagePlus plotImage, final Vector<Double> anisotropyHistory,
			final Vector<Double> errorHistory) {
		final double[] yVariables = new double[anisotropyHistory.size()];
		final double[] xVariables = new double[anisotropyHistory.size()];
		final Enumeration<Double> e = anisotropyHistory.elements();
		int i = 0;
		while (e.hasMoreElements()) {
			yVariables[i] = e.nextElement();
			xVariables[i] = i;
			i++;
		}
		final Enumeration<Double> f = errorHistory.elements();
		i = 0;
		final double[] errorBars = new double[errorHistory.size()];
		while (f.hasMoreElements()) {
			errorBars[i] = f.nextElement();
			i++;
		}
		final Plot plot = new Plot("Anisotropy", "Number of repeats", "Anisotropy", xVariables, yVariables);
		plot.addPoints(xVariables, yVariables, Plot.X);
		plot.addErrorBars(errorBars);
		plot.setLimits(0, anisotropyHistory.size(), 0, 1);
		final ImageProcessor plotIp = plot.getProcessor();
		plotImage.setProcessor(null, plotIp);
		return;
	}

	/*-------------------------------------------------------------*/
	/**
	 * Plot a set of 3D coordinates in Benjamin Schmidt's ImageJ 3D Viewer
	 *
	 * @param coOrdinates
	 *            float[][] n x 3 array of 3D (x,y,z) coordinates
	 * @param name
	 *            String name of the dataset
	 *
	 */
	private void plotPoints3D(final double[][] coOrdinates, final String name) {
		final int nPoints = coOrdinates.length;
		// Create a CustomMesh from the coordinates
		final List<Point3f> mesh = new ArrayList<Point3f>();
		for (int i = 0; i < nPoints; i++) {
			mesh.add(new Point3f((float) coOrdinates[i][0], (float) coOrdinates[i][1], (float) coOrdinates[i][2]));
		}
		// add the other ends of the vectors
		final List<Point3f> mesh2 = new ArrayList<Point3f>();
		for (int i = 0; i < nPoints; i++) {
			mesh2.add(new Point3f(-(float) coOrdinates[i][0], -(float) coOrdinates[i][1], -(float) coOrdinates[i][2]));
		}

		final CustomPointMesh cm = new CustomPointMesh(mesh);
		final CustomPointMesh cm2 = new CustomPointMesh(mesh2);

		// Create a universe and show it
		final Image3DUniverse univ = new Image3DUniverse();
		univ.show();

		// Add the mesh
		final Content c = univ.addCustomMesh(cm, "heads");
		final Content c2 = univ.addCustomMesh(cm2, "tails");
		final Color3f green = new Color3f(0.0f, 0.5f, 0.0f);
		final Color3f red = new Color3f(0.5f, 0.0f, 0.0f);
		c.getColor();
		c2.getColor();
		cm.setColor(green);
		cm2.setColor(red);
		cm.setPointSize(1);

		// Have a look at the source code of CustomPointMesh
		// for changing point size and anti-aliasing
	}/* end plotPoints3D */

	/**
	 * Calculate degree of anisotropy according to Harrigan and Mann's
	 * ellipsoidal tensor method
	 *
	 * @param coOrdinates
	 * @return Object[] containing degree of anisotropy and the
	 *         eigendecomposition
	 */
	private Object[] harriganMann(final double[][] coOrdinates) {
		Object[] ellipsoid = new Object[6];
		double da = 0;
		try {
			ellipsoid = FitEllipsoid.yuryPetrov(coOrdinates);
		} catch (final RuntimeException re) {
			da = Math.random();
		}
		final double[] coEf = (double[]) ellipsoid[3];
		final double[][] tensor = { { coEf[0], coEf[3], coEf[4] }, { coEf[3], coEf[1], coEf[5] },
				{ coEf[4], coEf[5], coEf[2] } };
		final Matrix M = new Matrix(tensor);
		final EigenvalueDecomposition E = M.eig();
		final Matrix EigenVal = E.getD();
		final double[] diag = MatrixUtils.diag(EigenVal).getColumnPackedCopy();
		da = 1 - diag[0] / diag[2];
		if (da > 1)
			da = 1;
		else if (da < 0)
			da = 0;
		final double[] anisotropy = { da };
		final Object[] result = { anisotropy, E };
		return result;
	}

	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
		final Vector<?> checkboxes = gd.getCheckboxes();
		final Vector<?> nFields = gd.getNumericFields();

		final Checkbox autoModeBox = (Checkbox) checkboxes.get(0);
		final Checkbox singleSphereBox = (Checkbox) checkboxes.get(1);
		final Checkbox showPlotBox = (Checkbox) checkboxes.get(2);

		final TextField radiusField = (TextField) nFields.get(0);
		final TextField minSpheresField = (TextField) nFields.get(3);
		final TextField maxSpheresField = (TextField) nFields.get(4);
		final TextField toleranceField = (TextField) nFields.get(5);

		if (singleSphereBox.getState()) {
			radiusField.setEnabled(true);
			autoModeBox.setEnabled(false);
			showPlotBox.setEnabled(false);
			minSpheresField.setEnabled(false);
			maxSpheresField.setEnabled(false);
			toleranceField.setEnabled(false);
		} else {
			radiusField.setEnabled(false);
			autoModeBox.setEnabled(true);
			showPlotBox.setEnabled(true);
			minSpheresField.setEnabled(true);
			maxSpheresField.setEnabled(true);
			toleranceField.setEnabled(true);
		}
		DialogModifier.registerMacroValues(gd, gd.getComponents());
		return true;
	}

}