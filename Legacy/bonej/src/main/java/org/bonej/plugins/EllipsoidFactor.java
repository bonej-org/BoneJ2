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
package org.bonej.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.geometry.Ellipsoid;
import org.bonej.geometry.Trig;
import org.bonej.geometry.Vectors;
import org.bonej.util.ImageCheck;
import org.bonej.util.Multithreader;
import org.bonej.util.SkeletonUtils;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import customnode.CustomLineMesh;
import customnode.CustomPointMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij3d.Image3DUniverse;

/**
 * <p>
 * <b>Ellipsoid Factor</b>
 * </p>
 * <p>
 * ImageJ plugin to describe the local geometry of a binary image in an
 * oblate/prolate spheroid space. Uses Skeletonize3D to generate a 3D skeleton,
 * the points of which are used as centres for maximally inscribed ellipsoids.
 * The ellipsoid factor (EF) is a method for the local determination of the rod-
 * or plate-like nature of porous or spongy continua. EF at a point within a 3D
 * structure is defined as the difference in axis ratios of the greatest
 * ellipsoid that fits inside the structure and that contains the point of
 * interest, and ranges from −1 for strongly oblate (discus-shaped) ellipsoids,
 * to +1 for strongly prolate (javelin-shaped) ellipsoids. For an ellipsoid with
 * axes a ≤ b ≤ c, EF = a/b − b/c.
 * </p>
 *
 * @see <a href="http://dx.doi.org/10.3389/fendo.2015.00015">
 *      "The ellipsoid factor for quantification of rods, plates, and intermediate forms in 3D geometries"
 *      Frontiers in Endocrinology (2015)</a>
 * @author Michael Doube
 *
 */
public class EllipsoidFactor implements PlugIn, Comparator<Ellipsoid> {
	private int nVectors = 100;

	/**
	 * increment for vector searching in real units. Defaults to ~Nyquist
	 * sampling of a unit pixel
	 */
	private double vectorIncrement = 1 / 2.3;

	/**
	 * Number of skeleton points per ellipsoid. Sets the granularity of the
	 * ellipsoid fields.
	 */
	private int skipRatio = 50;
	private int contactSensitivity = 1;
	/** Safety value to prevent while() running forever */
	private int maxIterations = 100;

	/**
	 * maximum distance ellipsoid may drift from seed point. Defaults to voxel
	 * diagonal length
	 */
	private double maxDrift = Math.sqrt(3);
	// private ResultsTable rt;
	private Image3DUniverse universe;

	private double stackVolume;

	private double[][] regularVectors;

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment())
			return;
		final ImagePlus imp = IJ.getImage();
		if (imp == null) {
			IJ.noImage();
			return;
		}
		if (!ImageCheck.isBinary(imp) || !ImageCheck.isMultiSlice(imp) || !ImageCheck.isVoxelIsotropic(imp, 0.001)) {
			IJ.error("8-bit binary stack with isotropic pixel spacing required.");
			return;
		}
		final Calibration cal = imp.getCalibration();
		final String units = cal.getUnits();
		final double pW = cal.pixelWidth;
		final double pH = cal.pixelHeight;
		final double pD = cal.pixelDepth;
		vectorIncrement *= Math.min(pD, Math.min(pH, pW));
		maxDrift = Math.sqrt(pW * pW + pH * pH + pD * pD);
		stackVolume = pW * pH * pD * imp.getWidth() * imp.getHeight() * imp.getStackSize();
		final GenericDialog gd = new GenericDialog("Setup");
		gd.addMessage("Sampling options");
		gd.addNumericField("Sampling_increment", vectorIncrement, 3, 8, units);
		gd.addNumericField("Vectors", nVectors, 0, 8, "");
		gd.addNumericField("Skeleton_points per ellipsoid", skipRatio, 0);
		gd.addNumericField("Contact sensitivity", contactSensitivity, 0, 4, "");
		gd.addNumericField("Maximum_iterations", maxIterations, 0);
		gd.addNumericField("Maximum_drift", maxDrift, 5, 8, units);

		gd.addMessage("\nOutput options");
		gd.addCheckbox("EF_image", true);
		gd.addCheckbox("Ellipsoid_ID_image", false);
		gd.addCheckbox("Volume_image", false);
		gd.addCheckbox("Axis_ratio_images", false);
		gd.addCheckbox("Flinn_peak_plot", true);
		gd.addNumericField("Gaussian_sigma", 2, 0, 4, "px");
		gd.addCheckbox("Flinn_plot", false);

		gd.addMessage("Ellipsoid Factor is beta software.\n" + "Please report your experiences to the user group:\n"
				+ "http://bit.ly/bonej-group");
		gd.addHelp("http://bonej.org/ef");
		gd.showDialog();

		if (gd.wasCanceled())
			return;

		// if (!Interpreter.isBatchMode()) {
		vectorIncrement = gd.getNextNumber();
		nVectors = (int) Math.round(gd.getNextNumber());
		skipRatio = (int) Math.round(gd.getNextNumber());
		contactSensitivity = (int) Math.round(gd.getNextNumber());
		maxIterations = (int) Math.round(gd.getNextNumber());
		maxDrift = gd.getNextNumber();

		final boolean doEFImage = gd.getNextBoolean();
		final boolean doEllipsoidIDImage = gd.getNextBoolean();
		final boolean doVolumeImage = gd.getNextBoolean();
		final boolean doAxisRatioImages = gd.getNextBoolean();
		final boolean doFlinnPeakPlot = gd.getNextBoolean();
		final double gaussianSigma = gd.getNextNumber();
		final boolean doFlinnPlot = gd.getNextBoolean();

		// }

		final double[][] unitVectors = Vectors.regularVectors(nVectors);
		regularVectors = unitVectors.clone();

		final int[][] skeletonPoints = skeletonPoints(imp);

		IJ.log("Found " + skeletonPoints.length + " skeleton points");

		if (IJ.debugMode) {
			universe = new Image3DUniverse();
			universe.show();
		}

		long start = System.currentTimeMillis();
		final Ellipsoid[] ellipsoids = findEllipsoids(imp, skeletonPoints);
		long stop = System.currentTimeMillis();

		IJ.log("Found " + ellipsoids.length + " ellipsoids in " + (stop - start) + " ms");

		start = System.currentTimeMillis();
		final int[][] maxIDs = findMaxID(imp, ellipsoids);
		stop = System.currentTimeMillis();

		IJ.log("Found maximal ellipsoids in " + (stop - start) + " ms");

		final double fractionFilled = calculateFillingEfficiency(maxIDs);
		IJ.log(IJ.d2s((fractionFilled * 100), 3) + "% of foreground volume filled with ellipsoids");

		if (doVolumeImage) {
			final ImagePlus volumes = displayVolumes(imp, maxIDs, ellipsoids);
			volumes.show();
			volumes.setDisplayRange(0, ellipsoids[(int) (0.05 * ellipsoids.length)].getVolume());
			IJ.run("Fire");
		}

		if (doAxisRatioImages) {
			final ImagePlus middleOverLong = displayMiddleOverLong(imp, maxIDs, ellipsoids);
			middleOverLong.show();
			middleOverLong.setDisplayRange(0, 1);
			IJ.run("Fire");

			final ImagePlus shortOverMiddle = displayShortOverMiddle(imp, maxIDs, ellipsoids);
			shortOverMiddle.show();
			shortOverMiddle.setDisplayRange(0, 1);
			IJ.run("Fire");
		}

		if (doEFImage) {
			final ImagePlus eF = displayEllipsoidFactor(imp, maxIDs, ellipsoids);
			eF.show();
			eF.setDisplayRange(-1, 1);
			IJ.run("Fire");
		}

		if (doEllipsoidIDImage) {
			final ImagePlus maxID = displayMaximumIDs(maxIDs, imp);
			maxID.show();
			maxID.setDisplayRange(-ellipsoids.length / 2, ellipsoids.length);
		}

		if (doFlinnPlot) {
			final ImagePlus flinnPlot = drawFlinnPlot("Weighted-flinn-plot-" + imp.getTitle(), ellipsoids);
			flinnPlot.show();
		}

		if (doFlinnPeakPlot) {
			final ImagePlus flinnPeaks = drawFlinnPeakPlot("FlinnPeaks_" + imp.getTitle(), imp, maxIDs, ellipsoids,
					gaussianSigma);
			flinnPeaks.show();
		}

		// ResultInserter ri = ResultInserter.getInstance();
		// ri.updateTable();
		// if (IJ.debugMode)
		// rt.show("Ellipsoid volumes");
		UsageReporter.reportEvent(this).send();
		IJ.showStatus("Ellipsoid Factor completed");
	}

	private double calculateFillingEfficiency(final int[][] maxIDs) {
		final int l = maxIDs.length;
		final long[] foregroundCount = new long[l];
		final long[] filledCount = new long[l];

		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int i = ai.getAndIncrement(); i < l; i = ai.getAndIncrement()) {
					IJ.showStatus("Calculating filling effiency...");
					IJ.showProgress(i, l);
					final int[] idSlice = maxIDs[i];
					for (final int val : idSlice) {
						if (val >= -1) {
							foregroundCount[i]++;
						}
						if (val >= 0) {
							filledCount[i]++;
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);

		long sumForegroundCount = 0;
		long sumFilledCount = 0;

		for (int i = 0; i < l; i++) {
			sumForegroundCount += foregroundCount[i];
			sumFilledCount += filledCount[i];
		}

		final long unfilled = sumForegroundCount - sumFilledCount;
		IJ.log(unfilled + " pixels unfilled with ellipsoids out of " + sumForegroundCount + " total foreground pixels");

		return sumFilledCount / (double) sumForegroundCount;
	}

	/**
	 * Display each ellipsoid's axis ratios in a scatter plot
	 *
	 * @param title
	 * @param ellipsoids
	 * @return
	 */
	private ImagePlus drawFlinnPlot(final String title, final Ellipsoid[] ellipsoids) {

		final int l = ellipsoids.length;
		final double[] aOverB = new double[l];
		final double[] bOverC = new double[l];

		for (int i = 0; i < l; i++) {
			final double[] radii = ellipsoids[i].getSortedRadii();
			aOverB[i] = radii[0] / radii[1];
			bOverC[i] = radii[1] / radii[2];
		}

		final Plot plot = new Plot("Flinn Diagram of " + title, "b/c", "a/b");
		plot.setLimits(0, 1, 0, 1);
		plot.setSize(1024, 1024);
		plot.addPoints(bOverC, aOverB, Plot.CIRCLE);
		final ImageProcessor plotIp = plot.getProcessor();
		return new ImagePlus("Flinn Diagram of " + title, plotIp);
	}

	/**
	 * Draw a Flinn diagram with each point given an intensity proportional to
	 * the volume of the structure with that axis ratio
	 *
	 * @param title
	 * @param imp
	 * @param maxIDs
	 * @param ellipsoids
	 * @param sigma
	 * @return
	 */
	private ImagePlus drawFlinnPeakPlot(final String title, final ImagePlus imp, final int[][] maxIDs,
										final Ellipsoid[] ellipsoids, final double sigma) {

		final ImageStack stack = imp.getImageStack();
		final int w = stack.getWidth();
		final int h = stack.getHeight();
		final int d = stack.getSize();

		final float[][] ab = new float[d][];
		final float[][] bc = new float[d][];

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					IJ.showStatus("Generating Flinn Diagram");
					IJ.showProgress(z, d);
					final int[] idSlice = maxIDs[z];
					int l = 0;
					for (int y = 0; y < h; y++) {
						final int offset = y * w;
						for (int x = 0; x < w; x++)
							if (idSlice[offset + x] >= 0)
								l++;
					}
					final float[] abl = new float[l];
					final float[] bcl = new float[l];
					int j = 0;
					for (int y = 0; y < h; y++) {
						final int offset = y * w;
						for (int x = 0; x < w; x++) {
							final int i = offset + x;
							final int id = idSlice[i];
							if (id >= 0) {
								final double[] radii = ellipsoids[id].getSortedRadii();
								abl[j] = (float) (radii[0] / radii[1]);
								bcl[j] = (float) (radii[1] / radii[2]);
								j++;
							}
						}
					}
					ab[z - 1] = abl;
					bc[z - 1] = bcl;
				}
			});
		}
		Multithreader.startAndJoin(threads);

		int l = 0;
		for (final float[] f : ab)
			l += f.length;

		final float[] aOverB = new float[l];
		final float[] bOverC = new float[l];

		int i = 0;
		for (final float[] fl : ab) {
			for (final float f : fl) {
				aOverB[i] = f;
				i++;
			}
		}
		i = 0;
		for (final float[] fl : bc) {
			for (final float f : fl) {
				bOverC[i] = f;
				i++;
			}
		}

		final int size = 512;
		final float[][] pixels = new float[size][size];

		for (int j = 0; j < l; j++) {
			final int x = (int) Math.floor((size - 1) * bOverC[j]);
			final int y = (int) Math.floor((size - 1) * (1 - aOverB[j]));
			pixels[x][y] += 1;
		}

		final FloatProcessor fp = new FloatProcessor(pixels);
		if (sigma > 0)
			fp.blurGaussian(sigma);

		final Calibration cal = new Calibration();
		cal.setXUnit("b/c");
		cal.setYUnit("a/b");
		cal.pixelWidth = 1.0 / size;
		cal.pixelHeight = 1.0 / size;
		cal.setInvertY(true);
		final ImagePlus plot = new ImagePlus(title, fp);
		plot.setCalibration(cal);
		return plot;
	}

	private ImagePlus displayEllipsoidFactor(final ImagePlus imp, final int[][] maxIDs, final Ellipsoid[] ellipsoids) {
		final ImageStack stack = imp.getImageStack();
		final int w = stack.getWidth();
		final int h = stack.getHeight();
		final int d = stack.getSize();

		final ImageStack efStack = new ImageStack(imp.getWidth(), imp.getHeight());

		final float[][] stackPixels = new float[d + 1][w * h];

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					IJ.showStatus("Generating EF image");
					IJ.showProgress(z, d);
					final int[] idSlice = maxIDs[z];
					final float[] pixels = stackPixels[z];

					for (int y = 0; y < h; y++) {
						final int offset = y * w;
						for (int x = 0; x < w; x++) {
							final int i = offset + x;
							final int id = idSlice[i];
							if (id >= 0)
								pixels[i] = (float) ellipsoidFactor(ellipsoids[id]);
							else
								pixels[i] = Float.NaN;
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);

		for (int z = 1; z <= d; z++)
			efStack.addSlice("" + z, stackPixels[z]);

		final ImagePlus ef = new ImagePlus("EF-" + imp.getTitle(), efStack);
		ef.setCalibration(imp.getCalibration());
		return ef;
	}

	private ImagePlus displayShortOverMiddle(final ImagePlus imp, final int[][] maxIDs, final Ellipsoid[] ellipsoids) {
		final ImageStack stack = imp.getImageStack();
		final int w = stack.getWidth();
		final int h = stack.getHeight();
		final int d = stack.getSize();

		final ImageStack smStack = new ImageStack(imp.getWidth(), imp.getHeight());

		final float[][] stackPixels = new float[d + 1][w * h];

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					IJ.showStatus("Generating short/middle axis image");
					IJ.showProgress(z, d);
					final int[] idSlice = maxIDs[z];
					final float[] pixels = stackPixels[z];
					for (int y = 0; y < h; y++) {
						final int offset = y * w;
						for (int x = 0; x < w; x++) {
							final int i = offset + x;
							final int id = idSlice[i];
							if (id >= 0) {
								final double[] radii = ellipsoids[id].getSortedRadii();
								pixels[i] = (float) (radii[0] / radii[1]);
							} else
								pixels[i] = Float.NaN;
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);

		for (int z = 1; z <= d; z++)
			smStack.addSlice("" + z, stackPixels[z]);

		final ImagePlus shortmid = new ImagePlus("Short_Mid-" + imp.getTitle(), smStack);
		shortmid.setCalibration(imp.getCalibration());
		return shortmid;
	}

	private ImagePlus displayMiddleOverLong(final ImagePlus imp, final int[][] maxIDs, final Ellipsoid[] ellipsoids) {
		final ImageStack stack = imp.getImageStack();
		final int w = stack.getWidth();
		final int h = stack.getHeight();
		final int d = stack.getSize();

		final ImageStack mlStack = new ImageStack(imp.getWidth(), imp.getHeight());

		final float[][] stackPixels = new float[d + 1][w * h];

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					IJ.showStatus("Generating volume image");
					IJ.showProgress(z, d);
					final int[] idSlice = maxIDs[z];
					final float[] pixels = stackPixels[z];
					for (int y = 0; y < h; y++) {
						final int offset = y * w;
						for (int x = 0; x < w; x++) {
							final int i = offset + x;
							final int id = idSlice[i];
							if (id >= 0) {
								final double[] radii = ellipsoids[id].getSortedRadii();
								pixels[i] = (float) (radii[1] / radii[2]);
							} else
								pixels[i] = Float.NaN;
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);

		for (int z = 1; z <= d; z++)
			mlStack.addSlice("" + z, stackPixels[z]);

		final ImagePlus midLong = new ImagePlus("Mid_Long-" + imp.getTitle(), mlStack);
		midLong.setCalibration(imp.getCalibration());
		return midLong;
	}

	private ImagePlus displayVolumes(final ImagePlus imp, final int[][] maxIDs, final Ellipsoid[] ellipsoids) {
		final ImageStack stack = imp.getImageStack();
		final int w = stack.getWidth();
		final int h = stack.getHeight();
		final int d = stack.getSize();

		final ImageStack volStack = new ImageStack(imp.getWidth(), imp.getHeight());

		final float[][] stackPixels = new float[d + 1][w * h];

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					IJ.showStatus("Generating volume image");
					IJ.showProgress(z, d);
					final int[] idSlice = maxIDs[z];
					final float[] pixels = stackPixels[z];
					for (int y = 0; y < h; y++) {
						final int offset = y * w;
						for (int x = 0; x < w; x++) {
							final int i = offset + x;
							final int id = idSlice[i];
							if (id >= 0) {
								pixels[i] = (float) ellipsoids[id].getVolume();
							} else
								pixels[i] = Float.NaN;
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);

		for (int z = 1; z <= d; z++)
			volStack.addSlice("" + z, stackPixels[z]);

		final ImagePlus volImp = new ImagePlus("Volume-" + imp.getTitle(), volStack);
		volImp.setCalibration(imp.getCalibration());
		return volImp;
	}

	private ImagePlus displayMaximumIDs(final int[][] biggestEllipsoid,
										final ImagePlus imp) {

		final ImageStack bigStack = new ImageStack(imp.getWidth(), imp.getHeight());
		for (int i = 1; i < biggestEllipsoid.length; i++) {
			final int[] maxIDs = biggestEllipsoid[i];
			final int l = maxIDs.length;
			final float[] pixels = new float[l];
			for (int j = 0; j < l; j++) {
				pixels[j] = maxIDs[j];
			}
			bigStack.addSlice("" + i, pixels);
		}
		final ImagePlus bigImp = new ImagePlus("Max-ID-" + imp.getTitle(), bigStack);
		bigImp.setCalibration(imp.getCalibration());
		return bigImp;
	}

	/**
	 * For each foreground pixel of the input image, find the ellipsoid of
	 * greatest volume
	 *
	 * @param imp
	 * @param ellipsoids
	 * @return array containing the indexes of the biggest ellipsoids which
	 *         contain each point
	 */
	private int[][] findMaxID(final ImagePlus imp, final Ellipsoid[] ellipsoids) {

		final ImageStack stack = imp.getImageStack();
		final int w = stack.getWidth();
		final int h = stack.getHeight();
		final int d = stack.getSize();

		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;

		final int[][] biggest = new int[d + 1][w * h];

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					IJ.showStatus("Finding biggest ellipsoid");
					IJ.showProgress(z, d);
					final byte[] slicePixels = (byte[]) stack.getPixels(z);
					final int[] bigSlice = biggest[z];
					Arrays.fill(bigSlice, -ellipsoids.length);
					final double zvD = z * vD;

					// find the subset of ellipsoids whose bounding box
					// intersects with z
					final List<Ellipsoid> nearEllipsoids = new ArrayList<>();
					final int n = ellipsoids.length;
					for (int i = 0; i < n; i++) {
						final Ellipsoid e = ellipsoids[i];
						final double[] zMinMax = e.getZMinAndMax();
						if (zvD >= zMinMax[0] && zvD <= zMinMax[1]) {
							final Ellipsoid f = e.copy();
							f.id = i;
							nearEllipsoids.add(f);
						}
					}
					final int o = nearEllipsoids.size();
					final Ellipsoid[] ellipsoidSubSet = new Ellipsoid[o];
					for (int i = 0; i < o; i++) {
						ellipsoidSubSet[i] = nearEllipsoids.get(i);
					}

					for (int y = 0; y < h; y++) {
						final double yvH = y * vH;
						// find the subset of ellipsoids whose bounding box
						// intersects with y
						final List<Ellipsoid> yEllipsoids = new ArrayList<>();
						for (final Ellipsoid e : ellipsoidSubSet) {
							final double[] yMinMax = e.getYMinAndMax();
							if (yvH >= yMinMax[0] && yvH <= yMinMax[1]) {
								yEllipsoids.add(e);
							}
						}

						// TODO Remove unnecessary duplication
						final int r = yEllipsoids.size();
						final Ellipsoid[] ellipsoidSubSubSet = new Ellipsoid[r];
						for (int i = 0; i < r; i++) {
							ellipsoidSubSubSet[i] = yEllipsoids.get(i);
						}

						final int offset = y * w;
						for (int x = 0; x < w; x++) {
							if (slicePixels[offset + x] == -1) {
								bigSlice[offset + x] = biggestEllipsoid(ellipsoidSubSubSet, x * vW, yvH, zvD);
							}
						}
					}

				}
			});
		}
		Multithreader.startAndJoin(threads);
		return biggest;
	}

	/**
	 * Search the list of ellipsoids and return the index of the largest
	 * ellipsoid which contains the point x, y, z
	 *
	 * @param ellipsoids
	 *            sorted in order of descending size and with id set to the sort
	 *            position of the whole set. This means that subsets may be
	 *            searched in sorted order and the ID which is returned is the
	 *            index of the ellipsoid in the full array of ellipsoids rather
	 *            than its index in the subset. The advantage is much faster
	 *            searching.
	 * @param x
	 * @param y
	 * @param z
	 * @return the index of the largest ellipsoid which contains this point, -1
	 *         if none of the ellipsoids contain the point
	 */
	private int biggestEllipsoid(final Ellipsoid[] ellipsoids, final double x, final double y, final double z) {
		for (final Ellipsoid ellipsoid : ellipsoids) {
			if (ellipsoid.contains(x, y, z)) {
				return ellipsoid.id;
			}
		}
		return -1;
	}

	/**
	 * Using skeleton points as seeds, propagate along each vector until a
	 * boundary is hit. Use the resulting cloud of boundary points as input into
	 * an ellipsoid fit.
	 *
	 * @param imp
	 * @param skeletonPoints
	 * @return
	 */
	private Ellipsoid[] findEllipsoids(final ImagePlus imp, final int[][] skeletonPoints) {
		final int nPoints = skeletonPoints.length;
		final Ellipsoid[] ellipsoids = new Ellipsoid[nPoints];

		// make sure array contains null in the non-calculated elements
		Arrays.fill(ellipsoids, null);

		final AtomicInteger ai = new AtomicInteger(0);
		final AtomicInteger counter = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int i = ai.getAndAdd(skipRatio); i < nPoints; i = ai.getAndAdd(skipRatio)) {
					ellipsoids[i] = optimiseEllipsoid(imp, skeletonPoints[i]);
					IJ.showProgress(counter.getAndAdd(skipRatio), nPoints);
					IJ.showStatus("Optimising ellipsoids...");
				}
			});
		}
		Multithreader.startAndJoin(threads);

		final Ellipsoid[] sortedEllipsoids = removeNulls(ellipsoids);

		// Sort using this class' compare method
		Arrays.sort(sortedEllipsoids, this);

		return sortedEllipsoids;
	}

	private static Ellipsoid[] removeNulls(Ellipsoid[] ellipsoids) {
        return Arrays.stream(ellipsoids).filter(Objects::nonNull).toArray(Ellipsoid[]::new);
	}

	/**
	 * given a seed point, find the ellipsoid which best fits the binarised
	 * structure
	 *
	 * @param imp
	 * @return ellipsoid fitting the point cloud of boundaries lying at the end
	 *         of vectors surrounding the seed point. If ellipsoid fitting
	 *         fails, returns null
	 */
	private Ellipsoid optimiseEllipsoid(final ImagePlus imp, final int[] skeletonPoint) {

		final long start = System.currentTimeMillis();

		final Calibration cal = imp.getCalibration();
		final double pW = cal.pixelWidth;
		final double pH = cal.pixelHeight;
		final double pD = cal.pixelDepth;

		final ImageStack stack = imp.getImageStack();
		final int w = stack.getWidth();
		final int h = stack.getHeight();
		final int d = stack.getSize();

		// cache slices into an array
		final byte[][] pixels = new byte[d][w * h];
		for (int i = 0; i < d; i++) {
			pixels[i] = (byte[]) stack.getProcessor(i + 1).getPixels();
		}

		// centre point of vector field
		final double px = skeletonPoint[0] * pW;
		final double py = skeletonPoint[1] * pH;
		final double pz = skeletonPoint[2] * pD;

		// Instantiate a small spherical ellipsoid
		final double[][] orthogonalVectors = { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };

		Ellipsoid ellipsoid = new Ellipsoid(vectorIncrement, vectorIncrement, vectorIncrement, px, py, pz,
				orthogonalVectors);

		final Vector<Double> volumeHistory = new Vector<>();
		volumeHistory.add(ellipsoid.getVolume());

		// dilate the sphere until it hits the background
		while (isContained(ellipsoid, pixels, pW, pH, pD, w, h, d)) {
			ellipsoid.dilate(vectorIncrement, vectorIncrement, vectorIncrement);
		}

		volumeHistory.add(ellipsoid.getVolume());

		// instantiate the ArrayList
		ArrayList<double[]> contactPoints = new ArrayList<>();

		// get the points of contact
		contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

		// find the mean unit vector pointing to the points of contact from the
		// centre
		final double[] shortAxis = contactPointUnitVector(ellipsoid, contactPoints);

		// find an orthogonal axis
		final double[] xAxis = { 1, 0, 0 };
		double[] middleAxis = Vectors.crossProduct(shortAxis, xAxis);
		middleAxis = Vectors.norm(middleAxis);

		// find a mutually orthogonal axis by forming the cross product
		double[] longAxis = Vectors.crossProduct(shortAxis, middleAxis);
		longAxis = Vectors.norm(longAxis);

		// construct a rotation matrix
		double[][] rotation = { shortAxis, middleAxis, longAxis };
		rotation = Ellipsoid.transpose(rotation);

		// rotate ellipsoid to point this way...
		ellipsoid.setRotation(rotation);

		// shrink the ellipsoid slightly
		ellipsoid.contract(0.1);

		// dilate other two axes until number of contact points increases
		// by contactSensitivity number of contacts

		while (contactPoints.size() < contactSensitivity) {
			ellipsoid.dilate(0, vectorIncrement, vectorIncrement);
			contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
			if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
				IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz + ") is invalid, nullifying at initial oblation");
				return null;
			}
		}

		volumeHistory.add(ellipsoid.getVolume());

		// until ellipsoid is totally jammed within the structure, go through
		// cycles of contraction, wiggling, dilation
		// goal is maximal inscribed ellipsoid, maximal being defined by volume

		// store a copy of the 'best ellipsoid so far'
		Ellipsoid maximal = ellipsoid.copy();

		// alternately try each axis
		int totalIterations = 0;
		int noImprovementCount = 0;
		final int absoluteMaxIterations = maxIterations * 10;
		while (totalIterations < absoluteMaxIterations && noImprovementCount < maxIterations) {

			// rotate a little bit
			ellipsoid = wiggle(ellipsoid);

			// contract until no contact
			ellipsoid = shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

			// dilate an axis
			double[] abc = threeWayShuffle();
			ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, pW, pH, pD, w, h, d
			);

			if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
				IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz + ") is invalid, nullifying after "
						+ totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// bump a little away from the sides
			contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
			if (contactPoints.size() > 0)
				ellipsoid = bump(ellipsoid, contactPoints, px, py, pz);
			// if can't bump then do a wiggle
			else
				ellipsoid = wiggle(ellipsoid);

			// contract
			ellipsoid = shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

			// dilate an axis
			abc = threeWayShuffle();
			ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, pW, pH, pD, w, h, d
			);

			if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
				IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz + ") is invalid, nullifying after "
						+ totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// rotate a little bit
			ellipsoid = turn(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

			// contract until no contact
			ellipsoid = shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

			// dilate an axis
			abc = threeWayShuffle();
			ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, pW, pH, pD, w, h, d
			);

			if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
				IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz + ") is invalid, nullifying after "
						+ totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// keep the maximal ellipsoid found
			ellipsoid = maximal.copy();
			// log its volume
			volumeHistory.add(ellipsoid.getVolume());

			// if the last value is bigger than the second-to-last value
			// reset the noImprovementCount
			// otherwise, increment it by 1.
			// if noImprovementCount exceeds a preset value the while() is
			// broken
			final int i = volumeHistory.size() - 1;
			if (volumeHistory.get(i) > volumeHistory.get(i - 1))
				noImprovementCount = 0;
			else
				noImprovementCount++;

			totalIterations++;
		}

		// this usually indicates that the ellipsoid
		// grew out of control for some reason
		if (totalIterations == absoluteMaxIterations) {
			IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz + ") seems to be out of control, nullifying after "
					+ totalIterations + " iterations");
			return null;
		}
		if (IJ.debugMode) {
			// show in the 3D viewer
			display3D(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d, px, py, pz, px + " " + py + " " + pz);
		}

		final long stop = System.currentTimeMillis();

		if (IJ.debugMode)
			IJ.log("Optimised ellipsoid in " + (stop - start) + " ms after " + totalIterations + " iterations ("
					+ IJ.d2s((double) (stop - start) / totalIterations, 3) + " ms/iteration)");

		return ellipsoid;
	}

	private double[] threeWayShuffle() {
		final double[] a = { 0, 0, 0 };
		final double rand = Math.random();
		if (rand < 1.0 / 3.0)
			a[0] = 1;
		else if (rand >= 2.0 / 3.0)
			a[2] = 1;
		else
			a[1] = 1;
		return a;
	}

	/**
	 * Check whether this ellipsoid is sensible
	 *
	 * @param ellipsoid
	 * @param pW
	 * @param pH
	 * @param pD
	 * @param w
	 * @param h
	 * @param d
	 * @return true if half or more of the surface points are outside the image
	 *         stack, or if the volume of the ellipsoid exceeds that of the
	 *         image stack
	 */
	private boolean isInvalid(final Ellipsoid ellipsoid, final double pW, final double pH, final double pD, final int w,
							  final int h, final int d) {

		final double[][] surfacePoints = ellipsoid.getSurfacePoints(nVectors);
		int outOfBoundsCount = 0;
		final int half = nVectors / 2;
		for (final double[] p : surfacePoints) {
			if (isOutOfBounds((int) (p[0] / pW), (int) (p[1] / pD), (int) (p[2] / pH), w, h, d))
				outOfBoundsCount++;
			if (outOfBoundsCount > half)
				return true;
		}

		final double volume = ellipsoid.getVolume();
		return volume > stackVolume;

	}

	/**
	 * Display an ellipsoid in the 3D viewer
	 *
	 * @param ellipsoid
	 * @param pW
	 * @param pH
	 * @param pD
	 * @param w
	 * @param h
	 * @param d
	 * @param px
	 * @param py
	 * @param pz
	 */
	private void display3D(final Ellipsoid ellipsoid, ArrayList<double[]> contactPoints, final byte[][] pixels,
			final double pW, final double pH, final double pD, final int w, final int h, final int d, final double px,
			final double py, final double pz, final String name) {
		contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
		final ArrayList<Point3f> contactPointsf = new ArrayList<>(contactPoints.size());
		for (final double[] p : contactPoints) {
			final Point3f point = new Point3f((float) p[0], (float) p[1], (float) p[2]);
			contactPointsf.add(point);
		}
		final double[][] pointCloud = ellipsoid.getSurfacePoints(100);

		final List<Point3f> pointList = new ArrayList<>();
		for (final double[] aPointCloud : pointCloud) {
			if (aPointCloud == null) {
				continue;
			}
			final Point3f e = new Point3f();
			e.x = (float) aPointCloud[0];
			e.y = (float) aPointCloud[1];
			e.z = (float) aPointCloud[2];
			pointList.add(e);
		}
		final CustomPointMesh mesh = new CustomPointMesh(pointList);
		mesh.setPointSize(2.0f);
		final Color3f cColour = new Color3f((float) (px / pW) / w, (float) (py / pH) / h, (float) (pz / pD) / d);
		mesh.setColor(cColour);

		final CustomPointMesh contactPointMesh = new CustomPointMesh(contactPointsf);
		contactPointMesh.setPointSize(2.5f);
		final Color3f invColour = new Color3f(1 - cColour.x, 1 - cColour.y, 1 - cColour.z);
		contactPointMesh.setColor(invColour);

		final double[] torque = calculateTorque(ellipsoid, contactPoints);
		final double[] c = ellipsoid.getCentre();

		final List<Point3f> torqueList = new ArrayList<>();
		torqueList.add(new Point3f((float) c[0], (float) c[1], (float) c[2]));
		torqueList.add(new Point3f((float) (torque[0] + c[0]), (float) (torque[1] + c[1]), (float) (torque[2] + c[2])));
		final CustomLineMesh torqueLine = new CustomLineMesh(torqueList);
		final Color3f blue = new Color3f(0.0f, 0.0f, 1.0f);
		torqueLine.setColor(blue);

		// Axis-aligned bounding box
		final double[] box = ellipsoid.getAxisAlignedBoundingBox();
		final float[] b = { (float) box[0], (float) box[1], (float) box[2], (float) box[3], (float) box[4],
				(float) box[5] };
		final List<Point3f> aabb = new ArrayList<>();
		aabb.add(new Point3f(b[0], b[2], b[4]));
		aabb.add(new Point3f(b[1], b[2], b[4]));

		aabb.add(new Point3f(b[0], b[2], b[4]));
		aabb.add(new Point3f(b[0], b[3], b[4]));

		aabb.add(new Point3f(b[0], b[2], b[4]));
		aabb.add(new Point3f(b[0], b[2], b[5]));

		aabb.add(new Point3f(b[0], b[3], b[4]));
		aabb.add(new Point3f(b[0], b[3], b[5]));

		aabb.add(new Point3f(b[0], b[3], b[5]));
		aabb.add(new Point3f(b[0], b[2], b[5]));

		aabb.add(new Point3f(b[0], b[3], b[4]));
		aabb.add(new Point3f(b[1], b[3], b[4]));

		aabb.add(new Point3f(b[1], b[3], b[4]));
		aabb.add(new Point3f(b[1], b[3], b[5]));

		aabb.add(new Point3f(b[0], b[3], b[5]));
		aabb.add(new Point3f(b[1], b[3], b[5]));

		aabb.add(new Point3f(b[0], b[2], b[5]));
		aabb.add(new Point3f(b[1], b[2], b[5]));

		aabb.add(new Point3f(b[1], b[2], b[4]));
		aabb.add(new Point3f(b[1], b[3], b[4]));

		aabb.add(new Point3f(b[1], b[2], b[4]));
		aabb.add(new Point3f(b[1], b[2], b[5]));

		aabb.add(new Point3f(b[1], b[2], b[5]));
		aabb.add(new Point3f(b[1], b[3], b[5]));

		try {
			universe.addCustomMesh(mesh, "Point cloud " + name).setLocked(true);
			universe.addCustomMesh(contactPointMesh, "Contact points of " + name).setLocked(true);
			universe.addCustomMesh(torqueLine, "Torque of " + name).setLocked(true);
			universe.addLineMesh(aabb, new Color3f(1.0f, 0.0f, 0.0f), "AABB of " + name, false).setLocked(true);

		} catch (final Exception e) {
			IJ.log("Something went wrong adding meshes to 3D viewer:\n" + e.getMessage());
		}
	}

	/**
	 * Rotate the ellipsoid theta radians around the unit vector formed by the
	 * sum of torques effected by unit normals acting on the surface of the
	 * ellipsoid
	 *
	 * @param ellipsoid
	 * @param pW
	 * @param pH
	 * @param pD
	 * @param w
	 * @param h
	 * @param d
	 * @return
	 */
	private Ellipsoid turn(Ellipsoid ellipsoid, ArrayList<double[]> contactPoints,
						   final byte[][] pixels, final double pW, final double pH, final double pD, final int w, final int h,
						   final int d) {

		contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
		if (contactPoints.size() > 0) {
			final double[] torque = calculateTorque(ellipsoid, contactPoints);
			ellipsoid = rotateAboutAxis(ellipsoid, Vectors.norm(torque));
		}
		return ellipsoid;
	}

	/**
	 * Calculate the mean unit vector between the ellipsoid's centroid and
	 * contact points
	 *
	 * @param ellipsoid
	 * @param contactPoints
	 * @return
	 */
	private double[] contactPointUnitVector(final Ellipsoid ellipsoid, final Collection<double[]> contactPoints) {

		final int nPoints = contactPoints.size();

		if (nPoints < 1)
			throw new IllegalArgumentException("Need at least one contact point");

		final double[] c = ellipsoid.getCentre();
		final double cx = c[0];
		final double cy = c[1];
		final double cz = c[2];
		double xSum = 0;
		double ySum = 0;
		double zSum = 0;
		for (final double[] p : contactPoints) {
			final double x = p[0] - cx;
			final double y = p[1] - cy;
			final double z = p[2] - cz;
			final double l = Trig.distance3D(x, y, z);

			xSum += x / l;
			ySum += y / l;
			zSum += z / l;
		}

		final double x = xSum / nPoints;
		final double y = ySum / nPoints;
		final double z = zSum / nPoints;
		final double l = Trig.distance3D(x, y, z);

		return new double[] { x / l, y / l, z / l };
	}

	/**
	 * Calculate the torque of unit normals acting at the contact points
	 *
	 * @param ellipsoid
	 * @param contactPoints
	 * @return
	 */
	private double[] calculateTorque(final Ellipsoid ellipsoid, final Iterable<double[]> contactPoints) {

		final double[] pc = ellipsoid.getCentre();
		final double cx = pc[0];
		final double cy = pc[1];
		final double cz = pc[2];

		final double[] r = ellipsoid.getRadii();
		final double a = r[0];
		final double b = r[1];
		final double c = r[2];

		final double s = 2 / (a * a);
		final double t = 2 / (b * b);
		final double u = 2 / (c * c);

		final double[][] rot = ellipsoid.getRotation();
		final double[][] inv = Ellipsoid.transpose(rot);

		double t0 = 0;
		double t1 = 0;
		double t2 = 0;

		for (final double[] p : contactPoints) {
			// translate point to centre on origin
			final double px = p[0] - cx;
			final double py = p[1] - cy;
			final double pz = p[2] - cz;

			// derotate the point
			final double x = inv[0][0] * px + inv[0][1] * py + inv[0][2] * pz;
			final double y = inv[1][0] * px + inv[1][1] * py + inv[1][2] * pz;
			final double z = inv[2][0] * px + inv[2][1] * py + inv[2][2] * pz;

			// calculate the unit normal on the centred and derotated ellipsoid
			final double nx = s * x;
			final double ny = t * y;
			final double nz = u * z;
			final double length = Trig.distance3D(nx, ny, nz);
			final double unx = nx / length;
			final double uny = ny / length;
			final double unz = nz / length;

			// rotate the normal back to the original ellipsoid
			final double ex = rot[0][0] * unx + rot[0][1] * uny + rot[0][2] * unz;
			final double ey = rot[1][0] * unx + rot[1][1] * uny + rot[1][2] * unz;
			final double ez = rot[2][0] * unx + rot[2][1] * uny + rot[2][2] * unz;

			final double[] torqueVector = Vectors.crossProduct(px, py, pz, ex, ey, ez);

			t0 += torqueVector[0];
			t1 += torqueVector[1];
			t2 += torqueVector[2];

		}
		return new double[]{ -t0, -t1, -t2 };
	}

	/**
	 * Rotate the ellipsoid theta radians around an arbitrary unit vector
	 *
	 * @param ellipsoid
	 * @param axis
	 * @see <a href=
	 *      "http://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_and_angle">Rotation
	 *      matrix from axis and angle</a>
	 * @return
	 */
	private Ellipsoid rotateAboutAxis(final Ellipsoid ellipsoid, final double[] axis) {
		final double theta = 0.1;
		final double sin = Math.sin(theta);
		final double cos = Math.cos(theta);
		final double cos1 = 1 - cos;
		final double x = axis[0];
		final double y = axis[1];
		final double z = axis[2];
		final double xy = x * y;
		final double xz = x * z;
		final double yz = y * z;
		final double xsin = x * sin;
		final double ysin = y * sin;
		final double zsin = z * sin;
		final double xycos1 = xy * cos1;
		final double xzcos1 = xz * cos1;
		final double yzcos1 = yz * cos1;
		final double[][] rotation = { { cos + x * x * cos1, xycos1 - zsin, xzcos1 + ysin },
				{ xycos1 + zsin, cos + y * y * cos1, yzcos1 - xsin },
				{ xzcos1 - ysin, yzcos1 + xsin, cos + z * z * cos1 }, };

		ellipsoid.rotate(rotation);

		return ellipsoid;
	}

	private Ellipsoid shrinkToFit(final Ellipsoid ellipsoid, ArrayList<double[]> contactPoints, final byte[][] pixels,
			final double pW, final double pH, final double pD, final int w, final int h, final int d) {

		// get the contact points
		contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

		// get the unit vectors to the contact points
		final double[][] unitVectors = findContactUnitVectors(ellipsoid, contactPoints);

		// contract until no contact
		int safety = 0;
		while (contactPoints.size() > 0 && safety < maxIterations) {
			ellipsoid.contract(0.01);
			contactPoints = findContactPoints(ellipsoid, contactPoints, unitVectors, pixels, pW, pH, pD, w, h, d);
			safety++;
		}

		ellipsoid.contract(0.05);

		return ellipsoid;
	}

	private double[][] findContactUnitVectors(final Ellipsoid ellipsoid, final ArrayList<double[]> contactPoints) {
		final double[][] unitVectors = new double[contactPoints.size()][3];
		final double[] c = ellipsoid.getCentre();
		final double cx = c[0];
		final double cy = c[1];
		final double cz = c[2];

		for (int i = 0; i < contactPoints.size(); i++) {
			final double[] p = contactPoints.get(i);
			final double px = p[0];
			final double py = p[1];
			final double pz = p[2];

			final double l = Trig.distance3D(px, py, pz, cx, cy, cz);
			final double x = (px - cx) / l;
			final double y = (py - cy) / l;
			final double z = (pz - cz) / l;
			final double[] u = { x, y, z };
			unitVectors[i] = u;
		}
		return unitVectors;
	}

	private Ellipsoid inflateToFit(final Ellipsoid ellipsoid, ArrayList<double[]> contactPoints, final double a,
								   final double b, final double c, final byte[][] pixels, final double pW, final double pH, final double pD,
								   final int w, final int h, final int d) {

		contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

		final double av = a * vectorIncrement;
		final double bv = b * vectorIncrement;
		final double cv = c * vectorIncrement;

		int safety = 0;
		while (contactPoints.size() < contactSensitivity && safety < maxIterations) {
			ellipsoid.dilate(av, bv, cv);
			contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
			safety++;
		}

		return ellipsoid;
	}

	private Ellipsoid bump(final Ellipsoid ellipsoid, final Collection<double[]> contactPoints, final double px,
			final double py, final double pz) {

		final double displacement = vectorIncrement / 2;

		final double[] c = ellipsoid.getCentre();
		final double[] vector = contactPointUnitVector(ellipsoid, contactPoints);
		final double x = c[0] + vector[0] * displacement;
		final double y = c[1] + vector[1] * displacement;
		final double z = c[2] + vector[2] * displacement;

		if (Trig.distance3D(px, py, pz, x, y, z) < maxDrift)
			ellipsoid.setCentroid(x, y, z);

		return ellipsoid;
	}

	/**
	 * Rotate the ellipsoid by a small random amount
	 *
	 * @param ellipsoid
	 */
	private Ellipsoid wiggle(final Ellipsoid ellipsoid) {

		final double b = Math.random() * 0.2 - 0.1;
		final double c = Math.random() * 0.2 - 0.1;
		final double a = Math.sqrt(1 - b * b - c * c);

		// zeroth column, should be very close to [1, 0, 0]^T (mostly x)
		final double[] zerothColumn = { a, b, c };

		// form triangle in random plane
		final double[] vector = Vectors.randomVector();

		// first column, should be very close to [0, 1, 0]^T
		final double[] firstColumn = Vectors.norm(Vectors.crossProduct(zerothColumn, vector));

		// second column, should be very close to [0, 0, 1]^T
		final double[] secondColumn = Vectors.norm(Vectors.crossProduct(zerothColumn, firstColumn));

		double[][] rotation = { zerothColumn, firstColumn, secondColumn };

		// array has subarrays as rows, need them as columns
		rotation = Ellipsoid.transpose(rotation);

		ellipsoid.rotate(rotation);

		return ellipsoid;
	}

	private ArrayList<double[]> findContactPoints(final Ellipsoid ellipsoid, final ArrayList<double[]> contactPoints,
			final byte[][] pixels, final double pW, final double pH, final double pD, final int w, final int h,
			final int d) {
		return findContactPoints(ellipsoid, contactPoints, regularVectors.clone(), pixels, pW, pH, pD, w, h, d);
	}

	private ArrayList<double[]> findContactPoints(final Ellipsoid ellipsoid, final ArrayList<double[]> contactPoints,
			final double[][] unitVectors, final byte[][] pixels, final double pW, final double pH, final double pD,
			final int w, final int h, final int d) {
		contactPoints.clear();
		final double[][] points = ellipsoid.getSurfacePoints(unitVectors);
		for (final double[] p : points) {
			final int x = (int) Math.floor(p[0] / pW);
			final int y = (int) Math.floor(p[1] / pH);
			final int z = (int) Math.floor(p[2] / pD);
			if (isOutOfBounds(x, y, z, w, h, d)) {
				continue;
			}
			if (pixels[z][y * w + x] != -1) {
				contactPoints.add(p);
			}
		}
		return contactPoints;
	}

	private boolean isContained(final Ellipsoid ellipsoid, final byte[][] pixels, final double pW, final double pH,
			final double pD, final int w, final int h, final int d) {
		final double[][] points = ellipsoid.getSurfacePoints(nVectors);
		for (final double[] p : points) {
			final int x = (int) Math.floor(p[0] / pW);
			final int y = (int) Math.floor(p[1] / pH);
			final int z = (int) Math.floor(p[2] / pD);
			if (isOutOfBounds(x, y, z, w, h, d))
				continue;
			if (pixels[z][y * w + x] != -1)
				return false;
		}
		return true;
	}

	/**
	 * return true if pixel coordinate is out of image bounds
	 *
	 * @param x
	 * @param y
	 * @param z
	 * @param w
	 * @param h
	 * @param d
	 * @return
	 */
	private boolean isOutOfBounds(final int x, final int y, final int z, final int w, final int h, final int d) {
		return x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= d;
	}

	private int[][] skeletonPoints(final ImagePlus imp) {
		final ImagePlus skeleton = SkeletonUtils.getSkeleton(imp);
		final ImageStack skeletonStack = skeleton.getStack();

		final int d = imp.getStackSize();
		final int h = imp.getHeight();
		final int w = imp.getWidth();

		// Bare ArrayList is not thread safe for concurrent add() operations.
		final List<int[]> list = Collections.synchronizedList(new ArrayList<>());

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					final byte[] slicePixels = (byte[]) skeletonStack.getPixels(z);
					for (int y = 0; y < h; y++) {
						final int offset = y * w;
						for (int x = 0; x < w; x++) {
							if (slicePixels[offset + x] == -1) {
								final int[] array = { x, y, z - 1 };
								list.add(array);
							}
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);

		if (IJ.debugMode)
			IJ.log("Skeleton point ArrayList contains " + list.size() + " points");

		return list.toArray(new int[list.size()][]);
	}

	/**
	 * Calculate the ellipsoid factor of this ellipsoid as a / b - b / c where a
	 * < b < c and a, b and c are the ellipsoid semi axis lengths (radii). This
	 * formulation places more rod-like ellipsoids towards 1 and plate-like
	 * ellipsoids towards -1. Ellipsoids of EF = 0 have equal a:b and b:c ratios
	 * so are midway between plate and rod. Spheres are a special case of EF =
	 * 0.
	 *
	 * @param ellipsoid
	 * @return the ellipsoid factor
	 */
	private double ellipsoidFactor(final Ellipsoid ellipsoid) {
		final double[] radii = ellipsoid.getSortedRadii();
		final double a = radii[0];
		final double b = radii[1];
		final double c = radii[2];
		return a / b - b / c;
	}

	/**
	 * Compare Ellipsoids by volume.
	 *
	 * Sorting based on this method will result in Ellipsoids sorted in order of
	 * <b>descending</b> volume.
	 *
	 */
	@Override
	public int compare(final Ellipsoid o1, final Ellipsoid o2) {
		return Double.compare(o2.getVolume(), o1.getVolume());
	}

}
