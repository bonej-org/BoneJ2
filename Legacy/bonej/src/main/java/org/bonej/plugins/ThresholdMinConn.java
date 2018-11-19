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

import static org.bonej.plugins.ParticleCounter.JOINING.MULTI;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.TextField;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.plugins.ParticleCounter.JOINING;
import org.bonej.util.DialogModifier;
import org.bonej.util.ImageCheck;
import org.bonej.util.Multithreader;
import org.bonej.util.StackStats;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.macro.Interpreter;
import ij.measure.CurveFitter;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import process3d.Dilate_;
import process3d.Erode_;

@Deprecated
public class ThresholdMinConn implements PlugIn, DialogListener {

	private int testCount = 11;
	private int subVolume = 256;
	private double testRange = 0.2;

	/** Show a plot of connectivity vs. threshold */
	private boolean doPlot;

	/** Apply the threshold to the stack once it is found */
	private boolean applyThreshold;

	/**
	 * Return the autothreshold for the stack histogram without doing connectivity
	 * analysis
	 */
	private boolean thresholdOnly;

	/** Number of cycles of erosion to apply */
	private int nErodes;

	/** Number of cycles of dilation to apply */
	private int nDilates;

	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
		if (DialogModifier.hasInvalidNumber(gd.getNumericFields())) return false;
		final List<?> checkboxes = gd.getCheckboxes();
		final Checkbox to = (Checkbox) checkboxes.get(0);
		thresholdOnly = to.getState();
		if (thresholdOnly) {
			// uncheck show plot
			final Checkbox t = (Checkbox) checkboxes.get(2);
			t.setState(false);
			t.setEnabled(false);
			doPlot = false;
			// grey out fields
			final List<?> numbers = gd.getNumericFields();
			for (final Object number : numbers) {
				final TextField n = (TextField) number;
				n.setEnabled(false);
			}
		}
		if (!thresholdOnly) {
			// un-grey out fields
			final List<?> numbers = gd.getNumericFields();
			for (final Object number : numbers) {
				final TextField n = (TextField) number;
				n.setEnabled(true);
			}
			// enable show plot
			final Checkbox t = (Checkbox) checkboxes.get(2);
			t.setEnabled(true);
		}
		DialogModifier.registerMacroValues(gd, gd.getComponents());
		return true;
	}

	@Override @Deprecated
	public void run(final String arg) {
		final ImagePlus imp = IJ.getImage();
		final ImageProcessor ip = imp.getProcessor();
		if (ImageCheck.isBinary(imp)) {
			IJ.error("Can't threshold a binary image");
			return;
		}
		if (imp.getBitDepth() == 32) {
			IJ.error("Currently unable to threshold 32-bit images");
			return;
		}
		if (!showDialog()) {
			return;
		}

		if (!ImageCheck.isVoxelIsotropic(imp, 0.05)) {
			if (!Interpreter.isBatchMode()) IJ.run("Properties...");
		}

		final int[] histogram = StackStats.getStackHistogram(imp);
		double threshold = ip.getAutoThreshold(histogram);

		if (!thresholdOnly) {
			final double[] testThreshold = getTestThreshold(imp, histogram);
			final double[] conns = getConns(imp, testThreshold, subVolume);
			final double minimum = getMinimum(testThreshold, conns);
			threshold = checkMinimum(imp, minimum, histogram);
			if (doPlot) showPlot(testThreshold, conns);
		}
		IJ.log(imp.getTitle() + " threshold  = " + IJ.d2s(imp.getCalibration()
			.getCValue(threshold), 1) + " (" + IJ.d2s(threshold, 1) + ")");

		if (applyThreshold) {
			final ImageStack stack2 = thresholdStack(imp, threshold);
			imp.setStack(imp.getTitle(), stack2);
			IJ.selectWindow(imp.getTitle());
			if (!imp.isInvertedLut()) IJ.run("Invert LUT");
		}
		IJ.showStatus("");
		UsageReporter.reportEvent(this).send();
	}

	/**
	 * Check the calculated optimal (parabolic minimum) value for sanity. If the
	 * test passes, the original minimum value is returned, otherwise the
	 * autoThreshold of the histogram is returned.
	 *
	 * @param minimum
	 * @param histogram
	 * @return
	 */
	private static double checkMinimum(final ImagePlus imp, final double minimum,
		final int[] histogram)
	{
		double threshold = minimum;
		final ImageProcessor ip = imp.getProcessor();

		// threshold cannot be greater or less than min and max of histogram
		int i = 0;
		while (histogram[i] == 0)
			i++;
		final int histogramMin = i;

		i = histogram.length - 1;
		while (histogram[i] == 0)
			i--;
		final int histogramMax = i;

		if (minimum < histogramMin || minimum > histogramMax) {
			threshold = ip.getAutoThreshold(histogram);
			IJ.log("Calculated threshold is outside bounds of pixel values. " +
				"Using histogram-based auto threshold.");
		}

		return threshold;
	}

	/**
	 * Calculate connectivity after threshold-purify-erode-purify-dilate for
	 * several threshold values.
	 *
	 * @param imp2
	 * @param testThreshold array of test threshold values (from getTestThreshold)
	 * @return array containing connectivity resulting from each test threshold
	 */
	private double[] getConns(final ImagePlus imp2, final double[] testThreshold,
		final int subVolume)
	{
		final int nTests = testThreshold.length;
		final double[] conns = new double[nTests];

		// make a stack out of imp2 that is no greater than
		// subvolume pixels in any dimension
		final ImageStack stack = imp2.getImageStack();

		final int width = Math.min(imp2.getWidth(), subVolume);
		final int height = Math.min(imp2.getHeight(), subVolume);
		final int depth = Math.min(imp2.getStackSize(), subVolume);

		final ImageStack stack2 = new ImageStack(width, height);
		for (int z = 1; z <= depth; z++) {
			final ImageProcessor ip = stack.getProcessor(z);
			final ImageProcessor ip2 = ip.createProcessor(width, height);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					ip2.set(x, y, ip.get(x, y));
				}
			}
			stack2.addSlice(stack.getSliceLabel(z), ip2);
		}

		final ImagePlus imp3 = new ImagePlus();
		for (int i = 0; i < nTests; i++) {
			// apply threshold
			final double thresh = testThreshold[i];
			final ImageStack stack3 = new ImageStack(width, height);
			for (int z = 1; z <= depth; z++) {
				final ImageProcessor ip2 = stack2.getProcessor(z);
				final ByteProcessor bp = new ByteProcessor(width, height);
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						if (ip2.get(x, y) > thresh) {
							bp.set(x, y, 255);
						}
						else {
							bp.set(x, y, 0);
						}
					}
				}
				if (z > stack3.getSize()) stack3.addSlice(stack2.getSliceLabel(z), bp);
				else stack3.setPixels(bp, z);
			}
			// purify
			imp3.setStack("Threshold " + (i + 1) + "/" + nTests, stack3);
			imp3.setCalibration(imp2.getCalibration());
			imp3.show();
			if (!imp3.isInvertedLut()) IJ.run("Invert LUT");
			final Erode_ e = new Erode_();
			final Dilate_ d = new Dilate_();
			final JOINING labelMethod = MULTI;
			replaceImage(imp3, Purify.purify(imp3, 4, labelMethod));
			for (int j = 0; j < nErodes; j++)
				replaceImage(imp3, e.erode(imp3, 255, false));
			if (nErodes > 0) replaceImage(imp3, Purify.purify(imp3, 4, labelMethod));
			for (int j = 0; j < nDilates; j++)
				replaceImage(imp3, d.dilate(imp3, 255, false));

			// get the connectivity
			final Connectivity con = new Connectivity();
			final double sumEuler = con.getSumEuler(imp3);
			final double deltaChi = con.getDeltaChi(imp3, sumEuler);
			final double connectivity = con.getConnectivity(deltaChi);
			// add connectivity to the array
			conns[i] = connectivity;
		}
		imp3.close();
		return conns;
	}

	/**
	 * Fit a parabola to the threshold and connectivity data and return its
	 * minimum
	 *
	 * @param testThreshold
	 * @param conns
	 * @return
	 */
	private static double getMinimum(final double[] testThreshold,
		final double[] conns)
	{
		final CurveFitter cf = new CurveFitter(testThreshold, conns);
		cf.doFit(CurveFitter.POLY2);
		final double[] params = cf.getParams();
		final double b = params[1];
		final double c = params[2];
		return -b / (2 * c);
	}

	private double[] getTestThreshold(final ImagePlus imp2,
		final int[] histogram)
	{
		final ImageProcessor ip = imp2.getProcessor();
		final int startThreshold = ip.getAutoThreshold(histogram);

		// get a range of thresholds to test
		final int nTests = testCount;
		final double testStep = 2 * testRange * startThreshold / (nTests - 1);
		final double[] testThreshold = new double[nTests];
		for (int i = 0; i < nTests; i++) {
			testThreshold[i] = startThreshold * (1 - testRange) + i * testStep;
		}
		return testThreshold;
	}

	/**
	 * Replace the image in imp with imp2
	 *
	 * @param imp
	 * @param imp2
	 */
	private static void replaceImage(final ImagePlus imp, final ImagePlus imp2) {
		final ImageStack stack2 = imp2.getStack();
		imp.setStack(null, stack2);
		imp.show();
		if (!imp.isInvertedLut()) IJ.run("Invert LUT");
	}

	private boolean showDialog() {
		final GenericDialog gd = new GenericDialog("Options");
		gd.addCheckbox("Threshold Only", false);
		gd.addCheckbox("Apply Threshold", false);
		gd.addCheckbox("Show Plot", true);
		gd.addMessage("Connectivity Options");
		gd.addNumericField("Tests", testCount, 0);
		gd.addNumericField("Range (0 - 0.5)", testRange, 2);
		gd.addNumericField("Subvolume Size", subVolume, 0);
		gd.addNumericField("Erosion Cycles", nErodes, 0);
		gd.addNumericField("Dilation Cycles", nDilates, 0);
		gd.addDialogListener(this);
		gd.showDialog();

		if (gd.wasCanceled()) return false;

		thresholdOnly = gd.getNextBoolean();
		applyThreshold = gd.getNextBoolean();
		doPlot = gd.getNextBoolean();
		testCount = (int) Math.floor(gd.getNextNumber());
		if (testCount <= 1) thresholdOnly = true;
		testRange = gd.getNextNumber();
		if (testRange < 0) testRange = 0;
		if (testRange > 0.5) testRange = 0.5;
		subVolume = (int) Math.floor(gd.getNextNumber());
		nErodes = (int) Math.floor(gd.getNextNumber());
		nDilates = (int) Math.floor(gd.getNextNumber());
		return true;

	}

	/**
	 * Display a graph showing connectivity vs. threshold
	 *
	 * @param testThreshold
	 * @param conns
	 */
	private static void showPlot(final double[] testThreshold,
		final double[] conns)
	{
		// convert arrays to floats
		final int nPoints = testThreshold.length;
		final float[] xData = new float[nPoints];
		final float[] yData = new float[nPoints];
		double xMin = Double.MAX_VALUE;
		double xMax = Double.MIN_VALUE;
		double yMax = Double.MIN_VALUE;
		for (int i = 0; i < nPoints; i++) {
			xData[i] = (float) testThreshold[i];
			yData[i] = (float) conns[i];
			xMin = Math.min(xMin, xData[i]);
			xMax = Math.max(xMax, xData[i]);
			yMax = Math.max(yMax, yData[i]);
		}
		final Plot plot = new Plot("Connectivity vs. Threshold", "Threshold",
			"Connectivity", xData, yData);
		plot.addPoints(xData, yData, Plot.CIRCLE);
		plot.setLimits(xMin, xMax, 0, yMax);
		plot.draw();
		final ImageProcessor plotIp = plot.getProcessor();
		final ImagePlus plotImage = new ImagePlus();
		plotImage.setProcessor("Connectivity vs. Threshold", plotIp);
		plotImage.show();
	}

	private ImageStack thresholdStack(final ImagePlus imp,
		final double threshold)
	{
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		final ImageStack stack = imp.getImageStack();
		final ImageStack stack2 = new ImageStack(w, h, d);
		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					final ImageProcessor ip = stack.getProcessor(z);
					final ByteProcessor bp = new ByteProcessor(w, h);
					for (int y = 0; y < h; y++) {
						for (int x = 0; x < w; x++) {
							final double pixel = ip.get(x, y);
							if (pixel > threshold) {
								bp.set(x, y, 255);
							}
							else {
								bp.set(x, y, 0);
							}
						}
					}
					stack2.setPixels(bp.getPixels(), z);
				}
			});
		}
		Multithreader.startAndJoin(threads);
		return stack2;
	}
}
