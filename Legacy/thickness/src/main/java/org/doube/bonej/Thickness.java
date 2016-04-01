/*
 * #%L
 * BoneJ fork of LocalThickness plugin.
 * %%
 * Copyright (C) 2007 - 2016 OptiNav Inc., Bob Doughtery, Michael Doube, BoneJ developers.
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

import java.awt.Checkbox;

import org.doube.util.ImageCheck;
import org.doube.util.ResultInserter;
import org.doube.util.RoiMan;
import org.doube.util.StackStats;
import org.doube.util.UsageReporter;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.macro.Interpreter;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * @see
 * 		<p>
 *      Hildebrand T, Rüegsegger P (1997) A new method for the model-independent
 *      assessment of thickness in three-dimensional images. J Microsc 185:
 *      67-75.
 *      <a href="http://dx.doi.org/10.1046/j.1365-2818.1997.1340694.x">doi
 *      :10.1046/j.1365-2818.1997.1340694.x</a>
 *      </p>
 *
 *      <p>
 *      Saito T, Toriwaki J (1994) New algorithms for euclidean distance
 *      transformation of an n-dimensional digitized picture with applications.
 *      Pattern Recognit 27: 1551-1565.
 *      <a href="http://dx.doi.org/10.1016/0031-3203(94)90133-3" >doi:10.1016/
 *      0031-3203(94)90133-3</a>
 *      </p>
 *
 * @author Bob Dougherty
 * @author Michael Doube (refactoring for BoneJ)
 * @author Richard Domander (refactoring for BoneJ)
 *
 */
public class Thickness implements PlugIn {
	private static final String THICKNESS_PREFERENCE_KEY = "bonej.localThickness.doThickness";
	private static final String SPACING_PREFERENCE_KEY = "bonej.localThickness.doSpacing";
	private static final String GRAPHIC_PREFERENCE_KEY = "bonej.localThickness.doGraphic";
	private static final String ROI_PREFERENCE_KEY = "bonej.localThickness.doRoi";
	private static final String MASK_PREFERENCE_KEY = "bonej.localThickness.doMask";

	private static final boolean THICKNESS_DEFAULT = true;
	private static final boolean SPACING_DEFAULT = false;
	private static final boolean GRAPHIC_DEFAULT = true;
	private static final boolean ROI_DEFAULT = false;
	private static final boolean MASK_DEFAULT = true;

	private float[][] sNew;
	private GenericDialog setupDialog = null;
	private RoiManager roiManager = null;
	private boolean doThickness = THICKNESS_DEFAULT;
	private boolean doSpacing = SPACING_DEFAULT;
	private boolean doGraphic = GRAPHIC_DEFAULT;
	private boolean doRoi = ROI_DEFAULT;
	private boolean doMask = MASK_DEFAULT;

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment()) {
			return;
		}

		final ImagePlus imp = IJ.getImage();
		if (!ImageCheck.isBinary(imp)) {
			IJ.error("8-bit binary (black and white only) image required.");
			return;
		}

		if (!ImageCheck.isVoxelIsotropic(imp, 1E-3)) {
			final boolean cancel = !IJ.showMessageWithCancel("Anisotropic voxels",
					"This image contains anisotropic voxels, which will\n"
							+ "result in incorrect thickness calculation.\n\n"
							+ "Consider rescaling your data so that voxels are isotropic\n" + "(Image > Scale...).\n\n"
							+ "Continue anyway?");
			if (cancel) {
				return;
			}
		}

		roiManager = RoiManager.getInstance();

		loadSettings();
		createSetupDialog();
		setupDialog.showDialog();
		if (setupDialog.wasCanceled()) {
			return;
		}
		getProcessingSettingsFromDialog();

		if (!doThickness && !doSpacing) {
			IJ.error("Nothing to process, exiting plugin.");
			return;
		}

		saveSettings();

		final long startTime = System.currentTimeMillis();
		final String title = stripExtension(imp.getTitle());

		// calculate trabecular thickness (Tb.Th)
		if (doThickness) {
			final boolean inverse = false;
			ImagePlus impLTC;
			if (doRoi && roiManager != null) {
				final ImageStack stack = RoiMan.cropStack(roiManager, imp.getStack(), true, 0, 1);
				final ImagePlus crop = new ImagePlus(imp.getTitle(), stack);
				crop.setCalibration(imp.getCalibration());
				impLTC = getLocalThickness(crop, inverse, doMask);
			} else {
				impLTC = getLocalThickness(imp, inverse, doMask);
			}
			impLTC.setTitle(title + "_Tb.Th");
			impLTC.setCalibration(imp.getCalibration());
			backgroundToNaN(impLTC, 0x00);
			final double[] stats = StackStats.meanStdDev(impLTC);
			insertResults(imp, stats, inverse);
			if (doGraphic && !Interpreter.isBatchMode()) {
				impLTC.show();
				impLTC.setSlice(1);
				impLTC.getProcessor().setMinAndMax(0, stats[2]);
				IJ.run("Fire");
			}
		}
		if (doSpacing) {
			final boolean inverse = true;
			ImagePlus impLTCi;
			if (doRoi && roiManager != null) {
				final ImageStack stack = RoiMan.cropStack(roiManager, imp.getStack(), true, 255, 1);
				final ImagePlus crop = new ImagePlus(imp.getTitle(), stack);
				crop.setCalibration(imp.getCalibration());
				impLTCi = getLocalThickness(crop, inverse, doMask);
			} else {
				impLTCi = getLocalThickness(imp, inverse, doMask);
			}
			// check marrow cavity size (i.e. trabcular separation, Tb.Sp)
			impLTCi.setTitle(title + "_Tb.Sp");
			impLTCi.setCalibration(imp.getCalibration());
			backgroundToNaN(impLTCi, 0x00);
			final double[] stats = StackStats.meanStdDev(impLTCi);
			insertResults(imp, stats, inverse);
			if (doGraphic && !Interpreter.isBatchMode()) {
				impLTCi.show();
				impLTCi.setSlice(1);
				impLTCi.getProcessor().setMinAndMax(0, stats[2]);
				IJ.run("Fire");
			}
		}
		IJ.showProgress(1.0);
		IJ.showStatus("Done");
		final double duration = ((double) System.currentTimeMillis() - (double) startTime) / 1000;
		IJ.log("Duration = " + IJ.d2s(duration, 3) + " s");
		UsageReporter.reportEvent(this).send();
	}

	// Modified from ImageJ code by Wayne Rasband
	String stripExtension(String name) {
		if (name != null) {
			final int dotIndex = name.lastIndexOf(".");
			if (dotIndex >= 0)
				name = name.substring(0, dotIndex);
		}
		return name;
	}

	private void createSetupDialog() {
		setupDialog = new GenericDialog("Plugin options");
		setupDialog.addCheckbox("Thickness", doThickness);
		setupDialog.addCheckbox("Spacing", doSpacing);
		setupDialog.addCheckbox("Graphic Result", doGraphic);

		setupDialog.addCheckbox("Crop using ROI Manager", doRoi);
		if (roiManager == null) {
			final Checkbox cropCheckbox = (Checkbox) setupDialog.getCheckboxes().elementAt(3);
			cropCheckbox.setState(false);
			cropCheckbox.setEnabled(false);
		}

		setupDialog.addCheckbox("Mask thickness map", doMask);
		setupDialog.addHelp("http://bonej.org/thickness");
	}

	private void getProcessingSettingsFromDialog() {
		doThickness = setupDialog.getNextBoolean();
		doSpacing = setupDialog.getNextBoolean();
		doGraphic = setupDialog.getNextBoolean();
		doRoi = setupDialog.getNextBoolean();
		doMask = setupDialog.getNextBoolean();
	}

	private void loadSettings() {
		doThickness = Prefs.get(THICKNESS_PREFERENCE_KEY, THICKNESS_DEFAULT);
		doSpacing = Prefs.get(SPACING_PREFERENCE_KEY, SPACING_DEFAULT);
		doGraphic = Prefs.get(GRAPHIC_PREFERENCE_KEY, GRAPHIC_DEFAULT);
		doRoi = Prefs.get(ROI_PREFERENCE_KEY, ROI_DEFAULT);
		doMask = Prefs.get(MASK_PREFERENCE_KEY, MASK_DEFAULT);
	}

	private void saveSettings() {
		Prefs.set(THICKNESS_PREFERENCE_KEY, doThickness);
		Prefs.set(SPACING_PREFERENCE_KEY, doSpacing);
		Prefs.set(GRAPHIC_PREFERENCE_KEY, doGraphic);
		Prefs.set(ROI_PREFERENCE_KEY, doRoi);
		Prefs.set(MASK_PREFERENCE_KEY, doMask);
	}

	/**
	 * <p>
	 * Saito-Toriwaki algorithm for Euclidian Distance Transformation. Direct
	 * application of Algorithm 1. Bob Dougherty 8/8/2006
	 * </p>
	 *
	 * <ul>
	 * <li>Version S1A: lower memory usage.</li>
	 * <li>Version S1A.1 A fixed indexing bug for 666-bin data set</li>
	 * <li>Version S1A.2 Aug. 9, 2006. Changed noResult value.</li>
	 * <li>Version S1B Aug. 9, 2006. Faster.</li>
	 * <li>Version S1B.1 Sept. 6, 2006. Changed comments.</li>
	 * <li>Version S1C Oct. 1, 2006. Option for inverse case. <br />
	 * Fixed inverse behavior in y and z directions.</li>
	 * <li>Version D July 30, 2007. Multithread processing for step 2.</li>
	 * </ul>
	 *
	 * <p>
	 * This version assumes the input stack is already in memory, 8-bit, and
	 * outputs to a new 32-bit stack. Versions that are more stingy with memory
	 * may be forthcoming.
	 * </p>
	 *
	 * @param imp
	 *            8-bit (binary) ImagePlus
	 *
	 */
	private float[][] geometryToDistanceMap(final ImagePlus imp, final boolean inv) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		final int nThreads = Runtime.getRuntime().availableProcessors();

		// Create references to input data
		final ImageStack stack = imp.getStack();
		final byte[][] data = new byte[d][];
		for (int k = 0; k < d; k++)
			data[k] = (byte[]) stack.getPixels(k + 1);

		// Create 32 bit floating point stack for output, s. Will also use it
		// for g in Transformation 1.
		final float[][] s = new float[d][];
		for (int k = 0; k < d; k++) {
			final ImageProcessor ipk = new FloatProcessor(w, h);
			s[k] = (float[]) ipk.getPixels();
		}
		float[] sk;
		// Transformation 1. Use s to store g.
		IJ.showStatus("EDT transformation 1/3");
		final Step1Thread[] s1t = new Step1Thread[nThreads];
		for (int thread = 0; thread < nThreads; thread++) {
			s1t[thread] = new Step1Thread(thread, nThreads, w, h, d, inv, s, data);
			s1t[thread].start();
		}
		try {
			for (int thread = 0; thread < nThreads; thread++) {
				s1t[thread].join();
			}
		} catch (final InterruptedException ie) {
			IJ.error("A thread was interrupted in step 1 .");
		}
		// Transformation 2. g (in s) -> h (in s)
		IJ.showStatus("EDT transformation 2/3");
		final Step2Thread[] s2t = new Step2Thread[nThreads];
		for (int thread = 0; thread < nThreads; thread++) {
			s2t[thread] = new Step2Thread(thread, nThreads, w, h, d, s);
			s2t[thread].start();
		}
		try {
			for (int thread = 0; thread < nThreads; thread++) {
				s2t[thread].join();
			}
		} catch (final InterruptedException ie) {
			IJ.error("A thread was interrupted in step 2 .");
		}
		// Transformation 3. h (in s) -> s
		IJ.showStatus("EDT transformation 3/3");
		final Step3Thread[] s3t = new Step3Thread[nThreads];
		for (int thread = 0; thread < nThreads; thread++) {
			s3t[thread] = new Step3Thread(thread, nThreads, w, h, d, inv, s, data);
			s3t[thread].start();
		}
		try {
			for (int thread = 0; thread < nThreads; thread++) {
				s3t[thread].join();
			}
		} catch (final InterruptedException ie) {
			IJ.error("A thread was interrupted in step 3 .");
		}
		// Find the largest distance for scaling
		// Also fill in the background values.
		float distMax = 0;
		final int wh = w * h;
		float dist;
		for (int k = 0; k < d; k++) {
			sk = s[k];
			for (int ind = 0; ind < wh; ind++) {
				if (((data[k][ind] & 255) < 128) ^ inv) {
					sk[ind] = 0;
				} else {
					dist = (float) Math.sqrt(sk[ind]);
					sk[ind] = dist;
					distMax = (dist > distMax) ? dist : distMax;
				}
			}
		}
		IJ.showProgress(1.0);
		IJ.showStatus("Done");
		return s;
	}

	class Step1Thread extends Thread {
		int thread, nThreads, w, h, d;
		float[][] s;
		byte[][] data;
		boolean inv;

		public Step1Thread(final int thread, final int nThreads, final int w, final int h, final int d,
				final boolean inv, final float[][] s, final byte[][] data) {
			this.thread = thread;
			this.nThreads = nThreads;
			this.w = w;
			this.h = h;
			this.d = d;
			this.inv = inv;
			this.data = data;
			this.s = s;
		}

		@Override
		public void run() {
			final int width = this.w;
			final int height = this.h;
			final int depth = this.d;
			final boolean inverse = inv;
			float[] sk;
			int n = width;
			if (height > n)
				n = height;
			if (depth > n)
				n = depth;
			final int noResult = 3 * (n + 1) * (n + 1);
			final boolean[] background = new boolean[n];
			int test, min;
			for (int k = thread; k < depth; k += nThreads) {
				IJ.showProgress(k / (1. * depth));
				sk = s[k];
				final byte[] dk = data[k];
				for (int j = 0; j < height; j++) {
					final int wj = width * j;
					for (int i = 0; i < width; i++) {
						background[i] = ((dk[i + wj] & 255) < 128) ^ inverse;
					}
					for (int i = 0; i < width; i++) {
						min = noResult;
						for (int x = i; x < width; x++) {
							if (background[x]) {
								test = i - x;
								test *= test;
								min = test;
								break;
							}
						}
						for (int x = i - 1; x >= 0; x--) {
							if (background[x]) {
								test = i - x;
								test *= test;
								if (test < min)
									min = test;
								break;
							}
						}
						sk[i + wj] = min;
					}
				}
			}
		}// run
	}// Step1Thread

	class Step2Thread extends Thread {
		int thread, nThreads, w, h, d;
		float[][] s;

		public Step2Thread(final int thread, final int nThreads, final int w, final int h, final int d,
				final float[][] s) {
			this.thread = thread;
			this.nThreads = nThreads;
			this.w = w;
			this.h = h;
			this.d = d;
			this.s = s;
		}

		@Override
		public void run() {
			final int width = this.w;
			final int height = this.h;
			final int depth = this.d;
			float[] sk;
			int n = width;
			if (height > n)
				n = height;
			if (depth > n)
				n = depth;
			final int noResult = 3 * (n + 1) * (n + 1);
			final int[] tempInt = new int[n];
			final int[] tempS = new int[n];
			boolean nonempty;
			int test, min, delta;
			for (int k = thread; k < depth; k += nThreads) {
				IJ.showProgress(k / (1. * depth));
				sk = s[k];
				for (int i = 0; i < width; i++) {
					nonempty = false;
					for (int j = 0; j < height; j++) {
						tempS[j] = (int) sk[i + width * j];
						if (tempS[j] > 0)
							nonempty = true;
					}
					if (nonempty) {
						for (int j = 0; j < height; j++) {
							min = noResult;
							delta = j;
							for (int y = 0; y < height; y++) {
								test = tempS[y] + delta * delta--;
								if (test < min)
									min = test;
							}
							tempInt[j] = min;
						}
						for (int j = 0; j < height; j++) {
							sk[i + width * j] = tempInt[j];
						}
					}
				}
			}
		}// run
	}// Step2Thread

	class Step3Thread extends Thread {
		int thread, nThreads, w, h, d;
		float[][] s;
		byte[][] data;
		boolean inv;

		public Step3Thread(final int thread, final int nThreads, final int w, final int h, final int d,
				final boolean inv, final float[][] s, final byte[][] data) {
			this.thread = thread;
			this.nThreads = nThreads;
			this.w = w;
			this.h = h;
			this.d = d;
			this.s = s;
			this.data = data;
			this.inv = inv;
		}

		@Override
		public void run() {
			final int width = this.w;
			final int height = this.h;
			final int depth = this.d;
			final byte[][] daTa = this.data;
			final boolean inverse = inv;
			int zStart, zStop, zBegin, zEnd;
			// float[] sk;
			int n = width;
			if (height > n)
				n = height;
			if (depth > n)
				n = depth;
			final int noResult = 3 * (n + 1) * (n + 1);
			final int[] tempInt = new int[n];
			final int[] tempS = new int[n];
			boolean nonempty;
			int test, min, delta;
			for (int j = thread; j < height; j += nThreads) {
				final int wj = width * j;
				IJ.showProgress(j / (1. * height));
				for (int i = 0; i < width; i++) {
					nonempty = false;
					for (int k = 0; k < depth; k++) {
						tempS[k] = (int) s[k][i + wj];
						if (tempS[k] > 0)
							nonempty = true;
					}
					if (nonempty) {
						zStart = 0;
						while ((zStart < (depth - 1)) && (tempS[zStart] == 0))
							zStart++;
						if (zStart > 0)
							zStart--;
						zStop = depth - 1;
						while ((zStop > 0) && (tempS[zStop] == 0))
							zStop--;
						if (zStop < (depth - 1))
							zStop++;

						for (int k = 0; k < depth; k++) {
							// Limit to the non-background to save time,
							if (((daTa[k][i + wj] & 255) >= 128) ^ inverse) {
								min = noResult;
								zBegin = zStart;
								zEnd = zStop;
								if (zBegin > k)
									zBegin = k;
								if (zEnd < k)
									zEnd = k;
								delta = k - zBegin;
								for (int z = zBegin; z <= zEnd; z++) {
									test = tempS[z] + delta * delta--;
									if (test < min)
										min = test;
									// min = (test < min) ? test : min;
								}
								tempInt[k] = min;
							}
						}
						for (int k = 0; k < depth; k++) {
							s[k][i + wj] = tempInt[k];
						}
					}
				}
			}
		}
	}

	/**
	 * <p>
	 * DistanceMaptoDistanceRidge
	 * </p>
	 * <p>
	 * Output: Distance ridge resulting from a local scan of the distance map.
	 * Overwrites the input.
	 * </p>
	 * <p>
	 * Note: Non-background points that are not part of the distance ridge are
	 * assiged a VERY_SMALL_VALUE. This is used for subsequent processing by
	 * other plugins to find the local thickness. Bob Dougherty August 10, 2006
	 * </p>
	 *
	 * <ul>
	 * <li>Version 1: August 10-11, 2006. Subtracts 0.5 from the distances.</li>
	 * <li>Version 1.01: September 6, 2006. Corrected some typos in the
	 * comments.</li>
	 * <li>Version 1.01: Sept. 7, 2006. More tiny edits.</li>
	 * <li>Version 2: Sept. 25, 2006. Creates a separate image stack for
	 * symmetry. <br />
	 * Temporary version that is very conservative. <br />
	 * Admittedly does not produce much impovement on real images.</li>
	 * <li>Version 3: Sept. 30, 2006. Ball calculations based on grid points.
	 * Should be much more accurate.</li>
	 * <li>Version 3.1 Oct. 1, 2006. Faster scanning of search points.</li>
	 * </ul>
	 *
	 * @param imp
	 *            3D Distance map (32-bit stack)
	 */
	private void distanceMaptoDistanceRidge(final ImagePlus imp, final float[][] s) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		sNew = new float[d][];
		for (int k = 0; k < d; k++) {
			final ImageProcessor ipk = new FloatProcessor(w, h);
			sNew[k] = (float[]) ipk.getPixels();
		}

		// Do it
		int k1, j1, i1, dz, dy, dx;
		boolean notRidgePoint;
		float[] sk1;
		float[] sk, skNew;
		int sk0Sq, sk0SqInd, sk1Sq;
		// Find the largest distance in the data
		IJ.showStatus("Distance Ridge: scanning the data");
		float distMax = 0;
		for (int k = 0; k < d; k++) {
			sk = s[k];
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					final int ind = i + wj;
					if (sk[ind] > distMax)
						distMax = sk[ind];
				}
			}
		}
		final int rSqMax = (int) (distMax * distMax + 0.5f) + 1;
		final boolean[] occurs = new boolean[rSqMax];
		for (int i = 0; i < rSqMax; i++)
			occurs[i] = false;
		for (int k = 0; k < d; k++) {
			sk = s[k];
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					final int ind = i + wj;
					occurs[(int) (sk[ind] * sk[ind] + 0.5f)] = true;
				}
			}
		}
		int numRadii = 0;
		for (int i = 0; i < rSqMax; i++) {
			if (occurs[i])
				numRadii++;
		}
		// Make an index of the distance-squared values
		final int[] distSqIndex = new int[rSqMax];
		final int[] distSqValues = new int[numRadii];
		int indDS = 0;
		for (int i = 0; i < rSqMax; i++) {
			if (occurs[i]) {
				distSqIndex[i] = indDS;
				distSqValues[indDS++] = i;
			}
		}
		/*
		 * Build template The first index of the template is the number of
		 * nonzero components in the offest from the test point to the remote
		 * point. The second index is the radii index (of the test point). The
		 * value of the template is the minimum square radius of the remote
		 * point required to cover the ball of the test point.
		 */
		IJ.showStatus("Distance Ridge: creating search templates");
		final int[][] rSqTemplate = createTemplate(distSqValues);
		int numCompZ, numCompY, numCompX, numComp;
		for (int k = 0; k < d; k++) {
			IJ.showStatus("Distance Ridge: processing slice " + (k + 1) + "/" + d);
			// IJ.showProgress(k/(1.*d));
			sk = s[k];
			skNew = sNew[k];
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					final int ind = i + wj;
					if (sk[ind] > 0) {
						notRidgePoint = false;
						sk0Sq = (int) (sk[ind] * sk[ind] + 0.5f);
						sk0SqInd = distSqIndex[sk0Sq];
						for (dz = -1; dz <= 1; dz++) {
							k1 = k + dz;
							if ((k1 >= 0) && (k1 < d)) {
								sk1 = s[k1];
								if (dz == 0) {
									numCompZ = 0;
								} else {
									numCompZ = 1;
								}
								for (dy = -1; dy <= 1; dy++) {
									j1 = j + dy;
									final int wj1 = w * j1;
									if ((j1 >= 0) && (j1 < h)) {
										if (dy == 0) {
											numCompY = 0;
										} else {
											numCompY = 1;
										}
										for (dx = -1; dx <= 1; dx++) {
											i1 = i + dx;
											if ((i1 >= 0) && (i1 < w)) {
												if (dx == 0) {
													numCompX = 0;
												} else {
													numCompX = 1;
												}
												numComp = numCompX + numCompY + numCompZ;
												if (numComp > 0) {
													final float sk1i1wj1 = sk1[i1 + wj1];
													sk1Sq = (int) (sk1i1wj1 * sk1i1wj1 + 0.5f);
													if (sk1Sq >= rSqTemplate[numComp - 1][sk0SqInd])
														notRidgePoint = true;
												}
											} // if in grid for i1
											if (notRidgePoint)
												break;
										} // dx
									} // if in grid for j1
									if (notRidgePoint)
										break;
								} // dy
							} // if in grid for k1
							if (notRidgePoint)
								break;
						} // dz
						if (!notRidgePoint)
							skNew[ind] = sk[ind];
					} // if not in background
				} // i
			} // j
		} // k
		IJ.showStatus("Distance Ridge complete");
	}

	/*
	 * For each offset from the origin, (dx,dy,dz), and each radius-squared,
	 * rSq, find the smallest radius-squared, r1Squared, such that a ball of
	 * radius r1 centered at (dx,dy,dz) includes a ball of radius rSq centered
	 * at the origin. These balls refer to a 3D integer grid. The set of
	 * (dx,dy,dz) points considered is a cube center at the origin. The size of
	 * the computed array could be considerably reduced by symmetry, but then
	 * the time for the calculation using this array would increase (and more
	 * code would be needed).
	 */
	int[][] createTemplate(final int[] distSqValues) {
		final int[][] t = new int[3][];
		t[0] = scanCube(1, 0, 0, distSqValues);
		t[1] = scanCube(1, 1, 0, distSqValues);
		t[2] = scanCube(1, 1, 1, distSqValues);
		return t;
	}

	/*
	 * For a list of r² values, find the smallest r1² values such that a "ball"
	 * of radius r1 centered at (dx,dy,dz) includes a "ball" of radius r
	 * centered at the origin. "Ball" refers to a 3D integer grid.
	 */
	int[] scanCube(final int dx, final int dy, final int dz, final int[] distSqValues) {
		final int numRadii = distSqValues.length;
		final int[] r1Sq = new int[numRadii];
		if ((dx == 0) && (dy == 0) && (dz == 0)) {
			for (int rSq = 0; rSq < numRadii; rSq++) {
				r1Sq[rSq] = Integer.MAX_VALUE;
			}
		} else {
			final int dxAbs = -Math.abs(dx);
			final int dyAbs = -Math.abs(dy);
			final int dzAbs = -Math.abs(dz);
			for (int rSqInd = 0; rSqInd < numRadii; rSqInd++) {
				final int rSq = distSqValues[rSqInd];
				int max = 0;
				final int r = 1 + (int) Math.sqrt(rSq);
				int scank, scankj;
				int dk, dkji;
				// int iBall;
				int iPlus;
				for (int k = 0; k <= r; k++) {
					scank = k * k;
					dk = (k - dzAbs) * (k - dzAbs);
					for (int j = 0; j <= r; j++) {
						scankj = scank + j * j;
						if (scankj <= rSq) {
							iPlus = ((int) Math.sqrt(rSq - scankj)) - dxAbs;
							dkji = dk + (j - dyAbs) * (j - dyAbs) + iPlus * iPlus;
							if (dkji > max)
								max = dkji;
						}
					}
				}
				r1Sq[rSqInd] = max;
			}
		}
		return r1Sq;
	}

	/**
	 * <p>
	 * DistanceRidgetoLocalThickness
	 * </p>
	 * <p>
	 * Input: Distance Ridge (32-bit stack) (Output from Distance Ridge.java)
	 * Output: Local Thickness. Overwrites the input.
	 * </p>
	 * <ul>
	 * <li>Version 1: September 6, 2006.</li>
	 * <li>Version 2: September 25, 2006. Fixed several bugs that resulted in
	 * non-symmetrical output from symmetrical input.</li>
	 * <li>Version 2.1 Oct. 1, 2006. Fixed a rounding error that caused some
	 * points to be missed.</li>
	 * <li>Version 3 July 31, 2007. Parallel processing version.</li>
	 * <li>Version 3.1 Multiplies the output by 2 to conform with the definition
	 * of local thickness</li>
	 * </ul>
	 *
	 * @param imp
	 */
	private void distanceRidgetoLocalThickness(final ImagePlus imp, final float[][] s) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		float[] sk;
		// Count the distance ridge points on each slice
		final int[] nRidge = new int[d];
		int ind, nr, iR;
		IJ.showStatus("Local Thickness: scanning stack ");
		for (int k = 0; k < d; k++) {
			sk = s[k];
			nr = 0;
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					ind = i + wj;
					if (sk[ind] > 0)
						nr++;
				}
			}
			nRidge[k] = nr;
		}
		final int[][] iRidge = new int[d][];
		final int[][] jRidge = new int[d][];
		final float[][] rRidge = new float[d][];
		// Pull out the distance ridge points
		int[] iRidgeK, jRidgeK;
		float[] rRidgeK;
		float sMax = 0;
		for (int k = 0; k < d; k++) {
			nr = nRidge[k];
			iRidge[k] = new int[nr];
			jRidge[k] = new int[nr];
			rRidge[k] = new float[nr];
			sk = s[k];
			iRidgeK = iRidge[k];
			jRidgeK = jRidge[k];
			rRidgeK = rRidge[k];
			iR = 0;
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					ind = i + wj;
					if (sk[ind] > 0) {
						iRidgeK[iR] = i;
						jRidgeK[iR] = j;
						rRidgeK[iR++] = sk[ind];
						if (sk[ind] > sMax)
							sMax = sk[ind];
						sk[ind] = 0;
					}
				}
			}
		}
		final int nThreads = Runtime.getRuntime().availableProcessors();
		final Object[] resources = new Object[d];// For synchronization
		for (int k = 0; k < d; k++) {
			resources[k] = new Object();
		}
		final LTThread[] ltt = new LTThread[nThreads];
		for (int thread = 0; thread < nThreads; thread++) {
			ltt[thread] = new LTThread(thread, nThreads, w, h, d, nRidge, s, iRidge, jRidge, rRidge, resources);
			ltt[thread].start();
		}
		try {
			for (int thread = 0; thread < nThreads; thread++) {
				ltt[thread].join();
			}
		} catch (final InterruptedException ie) {
			IJ.error("A thread was interrupted .");
		}

		// Fix the square values and apply factor of 2
		IJ.showStatus("Local Thickness: square root ");
		for (int k = 0; k < d; k++) {
			sk = s[k];
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					ind = i + wj;
					sk[ind] = (float) (2 * Math.sqrt(sk[ind]));
				}
			}
		}
		IJ.showStatus("Local Thickness complete");
	}

	class LTThread extends Thread {
		int thread, nThreads, w, h, d;
		float[][] s;
		int[] nRidge;
		int[][] iRidge, jRidge;
		float[][] rRidge;
		Object[] resources;

		public LTThread(final int thread, final int nThreads, final int w, final int h, final int d, final int[] nRidge,
				final float[][] s, final int[][] iRidge, final int[][] jRidge, final float[][] rRidge,
				final Object[] resources) {
			this.thread = thread;
			this.nThreads = nThreads;
			this.w = w;
			this.h = h;
			this.d = d;
			this.s = s;
			this.nRidge = nRidge;
			this.iRidge = iRidge;
			this.jRidge = jRidge;
			this.rRidge = rRidge;
			this.resources = resources;
		}

		@Override
		public void run() {
			final int width = this.w;
			final int height = this.h;
			final int depth = this.d;
			final float[][] stack = this.s;
			float[] sk1;// sk,sk1;
			/*
			 * Loop through ridge points. For each one, update the local
			 * thickness for the points within its sphere.
			 */
			int rInt;
			int iStart, iStop, jStart, jStop, kStart, kStop;
			float r1SquaredK, r1SquaredJK, r1Squared, s1;
			int rSquared;
			for (int k = thread; k < depth; k += nThreads) {
				IJ.showStatus("Local Thickness: processing slice " + (k + 1) + "/" + depth);
				final int nR = nRidge[k];
				final int[] iRidgeK = iRidge[k];
				final int[] jRidgeK = jRidge[k];
				final float[] rRidgeK = rRidge[k];
				for (int iR = 0; iR < nR; iR++) {
					final int i = iRidgeK[iR];
					final int j = jRidgeK[iR];
					final float r = rRidgeK[iR];
					rSquared = (int) (r * r + 0.5f);
					rInt = (int) r;
					if (rInt < r)
						rInt++;
					iStart = i - rInt;
					if (iStart < 0)
						iStart = 0;
					iStop = i + rInt;
					if (iStop >= width)
						iStop = width - 1;
					jStart = j - rInt;
					if (jStart < 0)
						jStart = 0;
					jStop = j + rInt;
					if (jStop >= height)
						jStop = height - 1;
					kStart = k - rInt;
					if (kStart < 0)
						kStart = 0;
					kStop = k + rInt;
					if (kStop >= depth)
						kStop = depth - 1;
					for (int k1 = kStart; k1 <= kStop; k1++) {
						r1SquaredK = (k1 - k) * (k1 - k);
						sk1 = stack[k1];
						for (int j1 = jStart; j1 <= jStop; j1++) {
							final int widthJ1 = width * j1;
							r1SquaredJK = r1SquaredK + (j1 - j) * (j1 - j);
							if (r1SquaredJK <= rSquared) {
								for (int i1 = iStart; i1 <= iStop; i1++) {
									r1Squared = r1SquaredJK + (i1 - i) * (i1 - i);
									if (r1Squared <= rSquared) {
										final int ind1 = i1 + widthJ1;
										s1 = sk1[ind1];
										if (rSquared > s1) {
											/*
											 * Get a lock on sk1 and check again
											 * to make sure that another thread
											 * has not increased sk1[ind1] to
											 * something larger than rSquared. A
											 * test shows that this may not be
											 * required...
											 */
											synchronized (resources[k1]) {
												s1 = sk1[ind1];
												if (rSquared > s1) {
													sk1[ind1] = rSquared;
												}
											}
										}
									} // if within sphere of DR point
								} // i1
							} // if k and j components within sphere of DR point
						} // j1
					} // k1
				} // iR
			} // k
		}// run
	}// LTThread

	/**
	 * <p>
	 * LocalThicknesstoCleanedUpLocalThickness
	 * </p>
	 *
	 * <p>
	 * Input: 3D Local Thickness map (32-bit stack)
	 * </p>
	 * <p>
	 * Output: Same as input with border voxels corrected for "jaggies."
	 * Non-background voxels adjacent to background voxels are have their local
	 * thickness values replaced by the average of their non-background
	 * neighbors that do not border background points. Bob Dougherty August 1,
	 * 2007
	 * </p>
	 *
	 * <ul>
	 * <li>August 10. Version 3 This version also multiplies the local thickness
	 * by 2 to conform with the official definition of local thickness.</li>
	 * </ul>
	 *
	 */
	private ImagePlus localThicknesstoCleanedUpLocalThickness(final ImagePlus imp, final float[][] s) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		IJ.showStatus("Cleaning up local thickness...");
		// Create 32 bit floating point stack for output, sNew.
		final ImageStack newStack = new ImageStack(w, h);
		sNew = new float[d][];
		for (int k = 0; k < d; k++) {
			final ImageProcessor ipk = new FloatProcessor(w, h);
			newStack.addSlice(null, ipk);
			sNew[k] = (float[]) ipk.getPixels();
		}
		/*
		 * First set the output array to flags: 0 for a background point -1 for
		 * a non-background point that borders a background point s (input data)
		 * for an interior non-background point
		 */
		for (int k = 0; k < d; k++) {
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					sNew[k][i + wj] = setFlag(s, i, j, k, w, h, d);
				} // i
			} // j
		} // k
		/*
		 * Process the surface points. Initially set results to negative values
		 * to be able to avoid including them in averages of for subsequent
		 * points. During the calculation, positive values in sNew are interior
		 * non-background local thicknesses. Negative values are surface points.
		 * In this case the value might be -1 (not processed yet) or -result,
		 * where result is the average of the neighboring interior points.
		 * Negative values are excluded from the averaging.
		 */
		for (int k = 0; k < d; k++) {
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					final int ind = i + wj;
					if (sNew[k][ind] == -1) {
						sNew[k][ind] = -averageInteriorNeighbors(s, i, j, k, w, h, d);
					}
				} // i
			} // j
		} // k
			// Fix the negative values and double the results
		for (int k = 0; k < d; k++) {
			for (int j = 0; j < h; j++) {
				final int wj = w * j;
				for (int i = 0; i < w; i++) {
					final int ind = i + wj;
					sNew[k][ind] = Math.abs(sNew[k][ind]);
				} // i
			} // j
		} // k
		IJ.showStatus("Clean Up Local Thickness complete");
		final String title = stripExtension(imp.getTitle());
		final ImagePlus impOut = new ImagePlus(title + "_CL", newStack);
		final double vW = imp.getCalibration().pixelWidth;
		// calibrate the pixel values to pixel width
		// so that thicknesses represent real units (not pixels)
		for (int z = 0; z < d; z++) {
			impOut.setSlice(z + 1);
			impOut.getProcessor().multiply(vW);
		}
		return impOut;
	}

	float setFlag(final float[][] s, final int i, final int j, final int k, final int w, final int h, final int d) {
		if (s[k][i + w * j] == 0)
			return 0;
		// change 1
		if (look(s, i, j, k - 1, w, h, d) == 0)
			return -1;
		if (look(s, i, j, k + 1, w, h, d) == 0)
			return -1;
		if (look(s, i, j - 1, k, w, h, d) == 0)
			return -1;
		if (look(s, i, j + 1, k, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j, k, w, h, d) == 0)
			return -1;
		if (look(s, i + 1, j, k, w, h, d) == 0)
			return -1;
		// change 1 before plus
		if (look(s, i, j + 1, k - 1, w, h, d) == 0)
			return -1;
		if (look(s, i, j + 1, k + 1, w, h, d) == 0)
			return -1;
		if (look(s, i + 1, j - 1, k, w, h, d) == 0)
			return -1;
		if (look(s, i + 1, j + 1, k, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j, k + 1, w, h, d) == 0)
			return -1;
		if (look(s, i + 1, j, k + 1, w, h, d) == 0)
			return -1;
		// change 1 before minus
		if (look(s, i, j - 1, k - 1, w, h, d) == 0)
			return -1;
		if (look(s, i, j - 1, k + 1, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j - 1, k, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j + 1, k, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j, k - 1, w, h, d) == 0)
			return -1;
		if (look(s, i + 1, j, k - 1, w, h, d) == 0)
			return -1;
		// change 3, k+1
		if (look(s, i + 1, j + 1, k + 1, w, h, d) == 0)
			return -1;
		if (look(s, i + 1, j - 1, k + 1, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j + 1, k + 1, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j - 1, k + 1, w, h, d) == 0)
			return -1;
		// change 3, k-1
		if (look(s, i + 1, j + 1, k - 1, w, h, d) == 0)
			return -1;
		if (look(s, i + 1, j - 1, k - 1, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j + 1, k - 1, w, h, d) == 0)
			return -1;
		if (look(s, i - 1, j - 1, k - 1, w, h, d) == 0)
			return -1;
		return s[k][i + w * j];
	}

	float averageInteriorNeighbors(final float[][] s, final int i, final int j, final int k, final int w, final int h,
			final int d) {
		int n = 0;
		float sum = 0;
		// change 1
		float value = lookNew(i, j, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i, j, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i, j - 1, k, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i, j + 1, k, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j, k, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i + 1, j, k, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		// change 1 before plus
		value = lookNew(i, j + 1, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i, j + 1, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i + 1, j - 1, k, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i + 1, j + 1, k, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i + 1, j, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		// change 1 before minus
		value = lookNew(i, j - 1, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i, j - 1, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j - 1, k, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j + 1, k, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i + 1, j, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		// change 3, k+1
		value = lookNew(i + 1, j + 1, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i + 1, j - 1, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j + 1, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j - 1, k + 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		// change 3, k-1
		value = lookNew(i + 1, j + 1, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i + 1, j - 1, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j + 1, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		value = lookNew(i - 1, j - 1, k - 1, w, h, d);
		if (value > 0) {
			n++;
			sum += value;
		}
		if (n > 0)
			return sum / n;
		return s[k][i + w * j];
	}

	float look(final float[][] s, final int i, final int j, final int k, final int w, final int h, final int d) {
		if ((i < 0) || (i >= w))
			return -1;
		if ((j < 0) || (j >= h))
			return -1;
		if ((k < 0) || (k >= d))
			return -1;
		return s[k][i + w * j];
	}

	// A positive result means this is an interior, non-background, point.
	float lookNew(final int i, final int j, final int k, final int w, final int h, final int d) {
		if ((i < 0) || (i >= w))
			return -1;
		if ((j < 0) || (j >= h))
			return -1;
		if ((k < 0) || (k >= d))
			return -1;
		return sNew[k][i + w * j];
	}

	private void insertResults(final ImagePlus imp, final double[] stats, final boolean inverse) {
		final double meanThick = stats[0];
		final double stDev = stats[1];
		final double maxThick = stats[2];
		final String units = imp.getCalibration().getUnits();

		final ResultInserter ri = ResultInserter.getInstance();
		if (!inverse) {
			// trab thickness
			ri.setResultInRow(imp, "Tb.Th Mean (" + units + ")", meanThick);
			ri.setResultInRow(imp, "Tb.Th Std Dev (" + units + ")", stDev);
			ri.setResultInRow(imp, "Tb.Th Max (" + units + ")", maxThick);
		} else {
			// trab separation
			ri.setResultInRow(imp, "Tb.Sp Mean (" + units + ")", meanThick);
			ri.setResultInRow(imp, "Tb.Sp Std Dev (" + units + ")", stDev);
			ri.setResultInRow(imp, "Tb.Sp Max (" + units + ")", maxThick);
		}
		ri.updateTable();
	}

	/**
	 * Get a local thickness map from an ImagePlus with optional masking
	 * correction
	 *
	 * @param imp
	 *            Binary ImagePlus
	 * @param inv
	 *            false if you want the thickness of the foreground and true if
	 *            you want the thickness of the background
	 * @param doMask
	 *            true to apply a masking operation to enforce the map to
	 *            contain thickness values only at coordinates where there is a
	 *            corresponding input pixel
	 * @return 32-bit ImagePlus containing a local thickness map
	 */
	public ImagePlus getLocalThickness(final ImagePlus imp, final boolean inv, final boolean doMask) {
		if (!ImageCheck.isVoxelIsotropic(imp, 1E-3)) {
			IJ.log("Warning: voxels are anisotropic. Local thickness results will be inaccurate");
		}
		final float[][] s = geometryToDistanceMap(imp, inv);
		distanceMaptoDistanceRidge(imp, s);
		distanceRidgetoLocalThickness(imp, s);
		ImagePlus impLTC = localThicknesstoCleanedUpLocalThickness(imp, s);
		if (doMask)
			impLTC = trimOverhang(imp, impLTC, inv);
		return impLTC;
	}

	/**
	 * Get a local thickness map from an ImagePlus, without masking correction
	 *
	 * @see #getLocalThickness(ImagePlus imp, boolean inv, boolean doMask)
	 * @param imp
	 *            Binary ImagePlus
	 * @param inv
	 *            false if you want the thickness of the foreground and true if
	 *            you want the thickness of the background
	 * @return 32-bit ImagePlus containing a local thickness map
	 */
	public ImagePlus getLocalThickness(final ImagePlus imp, final boolean inv) {
		return getLocalThickness(imp, inv, false);
	}

	/**
	 * Reduce error in thickness quantization by trimming the one pixel overhang
	 * in the thickness map
	 *
	 * @param imp
	 *            Binary input image
	 * @param impLTC
	 *            Thickness map
	 * @param inv
	 *            true if calculating thickness of background, false for
	 *            foreground
	 * @return Thickness map with pixels masked by input image
	 */
	private ImagePlus trimOverhang(final ImagePlus imp, final ImagePlus impLTC, final boolean inv) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();

		final ImageStack stack = imp.getImageStack();
		final ImageStack mapStack = impLTC.getImageStack();

		final int keepValue = inv ? 0 : 255;
		ImageProcessor ip;
		ImageProcessor map;
		for (int z = 1; z <= d; z++) {
			IJ.showStatus("Masking thickness map...");
			IJ.showProgress(z, d);
			ip = stack.getProcessor(z);
			map = mapStack.getProcessor(z);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (ip.get(x, y) != keepValue)
						map.set(x, y, 0);
				}
			}
		}
		return impLTC;
	}

	/**
	 * Sets the value of the background pixels in the given image to Float.NaN.
	 *
	 * @param image
	 *            A 32-bit floating point image
	 * @param backgroundColor
	 *            The color used to identify background pixel (usually 0x00)
	 */
	private static void backgroundToNaN(final ImagePlus image, final int backgroundColor) {
		final int depth = image.getNSlices();
		final int pixelsPerSlice = image.getWidth() * image.getHeight();
		final ImageStack stack = image.getStack();

		for (int z = 1; z <= depth; z++) {
			final float pixels[] = (float[]) stack.getPixels(z);
			for (int i = 0; i < pixelsPerSlice; i++) {
				if (Float.compare(pixels[i], backgroundColor) == 0) {
					pixels[i] = Float.NaN;
				}
			}
		}
	}
}
