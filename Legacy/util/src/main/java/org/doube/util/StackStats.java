/*
 * #%L
 * BoneJ utility classes.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers.
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
package org.doube.util;

import java.util.concurrent.atomic.AtomicInteger;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public class StackStats {
	/**
	 * Work out some summary stats
	 *
	 * @param imp
	 *            32-bit thickness image
	 * @return double[] containing mean, standard deviation and maximum as its
	 *         0th and 1st and 2nd elements respectively
	 *
	 */
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
		final double[] stats = { meanThick, stDev, maxThick };
		return stats;
	}

	/**
	 * Get a histogram of stack's pixel values
	 *
	 * @param imp2
	 * @return
	 */
	public static int[] getStackHistogram(final ImagePlus imp) {
		final int d = imp.getStackSize();
		final ImageStack stack = imp.getStack();
		if (stack.getProcessor(1) instanceof FloatProcessor)
			throw new IllegalArgumentException("32-bit images not supported by this histogram method");
		final int[][] sliceHistograms = new int[d + 1][];
		final Roi roi = imp.getRoi();
		if (stack.getSize() == 1) {
			return imp.getProcessor().getHistogram();
		}

		final AtomicInteger ai = new AtomicInteger(1);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(new Runnable() {
				@Override
				public void run() {
					for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
						IJ.showStatus("Getting stack histogram...");
						final ImageProcessor ip = stack.getProcessor(z);
						ip.setRoi(roi);
						sliceHistograms[z] = ip.getHistogram();
					}
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
