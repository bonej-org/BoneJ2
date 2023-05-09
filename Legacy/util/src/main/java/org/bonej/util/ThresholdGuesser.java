/*-
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
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


package org.bonej.util;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.concurrent.atomic.AtomicInteger;

public final class ThresholdGuesser {

	private static final double airHU = -1000;

	private ThresholdGuesser() {}

	/**
	 * Set up default thresholds and report them to the user as HU values if the
	 * image has HU calibration or plain values if not. Used as a first guess for
	 * dialogs that have to handle both HU and uncalibrated images.
	 *
	 * @param imp an image.
	 * @return double[2] containing minimum and maximum thresholds
	 */
	public static double[] setDefaultThreshold(final ImagePlus imp) {
		final Calibration cal = imp.getCalibration();
		double min;
		double max;
		if (ImageCheck.huCalibrated(imp)) {
			// default bone thresholds are 0 and 4000 HU
			min = airHU + 1000;
			max = airHU + 5000;
			return new double[] { min, max };
		}
		// set some sensible thresholding defaults
		final int[] histogram = getStackHistogram(imp);
		final int histoLength = histogram.length;
		int histoMax = histoLength - 1;
		for (int i = histoLength - 1; i >= 0; i--) {
			if (histogram[i] > 0) {
				histoMax = i;
				break;
			}
		}
		min = imp.getProcessor().getAutoThreshold(histogram);
		max = histoMax;
		if (cal.isSigned16Bit() && cal.getCValue(0) == 0) {
			min += Short.MIN_VALUE;
			max += Short.MIN_VALUE;
		}
		return new double[] { min, max };
	}

	/**
	 * Get a histogram of stack's pixel values
	 *
	 * @param imp an image
	 * @return a histogram of the image.
	 */
	private static int[] getStackHistogram(final ImagePlus imp) {
		final int d = imp.getStackSize();
		final ImageStack stack = imp.getStack();
		if (stack.getProcessor(1) instanceof FloatProcessor)
			throw new IllegalArgumentException(
					"32-bit images not supported by this histogram method");
		final int[][] sliceHistograms = new int[d + 1][];
		final Roi roi = imp.getRoi();
		if (stack.getSize() == 1) {
			return imp.getProcessor().getHistogram();
		}

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
					IJ.showStatus("Getting stack histogram...");
					final ImageProcessor ip = stack.getProcessor(z);
					ip.setRoi(roi);
					sliceHistograms[z] = ip.getHistogram();
				}
			});
		}
		Multithreader.startAndJoin(threads);

		final int l = sliceHistograms[1].length;
		final int[] histogram = new int[l];

		for (int z = 1; z <= d; z++) {
			final int[] slice = sliceHistograms[z];
			for (int i = 0; i < l; i++)
				histogram[i] += slice[i];
		}
		return histogram;
	}
}
