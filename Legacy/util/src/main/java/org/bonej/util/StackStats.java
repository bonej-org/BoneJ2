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

package org.bonej.util;

import java.util.concurrent.atomic.AtomicInteger;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public final class StackStats {

	private StackStats() {}

	/**
	 * Get a histogram of stack's pixel values
	 *
	 * @param imp an image
	 * @return a histogram of the image.
	 */
	public static int[] getStackHistogram(final ImagePlus imp) {
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

	/**
	 * Work out some summary stats
	 *
	 * @param imp 32-bit thickness image
	 * @return double[] containing mean, standard deviation and maximum as its 0th
	 *         and 1st and 2nd elements respectively
	 */
	// TODO Move to LocalThicknessTest
	public static double[] meanStdDev(final ImagePlus imp) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getStackSize();
		final int wh = w * h;
		final ImageStack stack = imp.getStack();
		long pixCount = 0;
		double sumThick = 0;
		double maxThick = 0;

		for (int s = 1; s <= d; s++) {
			final float[] slicePixels = (float[]) stack.getPixels(s);
			for (int p = 0; p < wh; p++) {
				final double pixVal = slicePixels[p];
				if (pixVal > 0) {
					sumThick += pixVal;
					maxThick = Math.max(maxThick, pixVal);
					pixCount++;
				}
			}
		}
		final double meanThick = sumThick / pixCount;

		double sumSquares = 0;
		for (int s = 1; s <= d; s++) {
			final float[] slicePixels = (float[]) stack.getPixels(s);
			for (int p = 0; p < wh; p++) {
				final double pixVal = slicePixels[p];
				if (pixVal > 0) {
					final double residual = meanThick - pixVal;
					sumSquares += residual * residual;
				}
			}
		}
		final double stDev = Math.sqrt(sumSquares / pixCount);
		return new double[] { meanThick, stDev, maxThick };
	}

}
