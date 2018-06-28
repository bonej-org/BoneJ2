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

import static org.junit.Assert.assertEquals;

import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.bonej.menuWrappers.LocalThickness;
import org.bonej.util.TestDataMaker;
import org.junit.Test;

import ij.ImagePlus;

public class LocalThicknessTest {

	@Test
	public void testGetLocalThicknessRod() {
		final LocalThickness th = new LocalThickness();
		for (int d = 1; d < 25; d += 1) {
			final ImagePlus rod = rod(d * 100, d);
			final ImagePlus imp = th.getLocalThickness(rod, false);
			final double[] stats = meanStdDev(imp);
			assertEquals(d, stats[0], 1.5);
		}
	}

	@Test
	public void testGetLocalThicknessSphere() {
		final LocalThickness th = new LocalThickness();
		for (int r = 2; r < 25; r++) {
			final ImagePlus sphere = sphere(r);
			final ImagePlus imp = th.getLocalThickness(sphere, false);
			final double[] stats = meanStdDev(imp);
			final double regression = r * 1.9441872882 - 1.218936;
			assertEquals(regression, stats[0], regression * 0.1);
		}
	}

	@Test
	public void testGetLocalThicknessBrick() {
		final LocalThickness th = new LocalThickness();
		for (int t = 1; t < 21; t++) {
			final ImagePlus brick = TestDataMaker.brick(128, 128, t);
			final ImagePlus imp = th.getLocalThickness(brick, false);
			final double[] stats = meanStdDev(imp);
			int expected = t;
			// pixelation and *2 (radius to diameter conversion) weirdness
			if (t % 2 != 0)
				expected++;
			assertEquals(expected, stats[0], expected * 0.05);
		}
	}

	/**
	 * Generate a rod of circular cross-section. The rod is oriented with its axis
	 * in the z direction and in a stack of 2*diameter wide and high.
	 *
	 * @param length length of the rod.
	 * @param diameter diameter of the rod.
	 * @return an image containing the rod.
	 */
	private static ImagePlus rod(final int length, final int diameter) {
		final ImageStack stack = new ImageStack(2 * diameter, 2 * diameter);
		for (int i = 0; i < length; i++) {
			final ImageProcessor ip = new ByteProcessor(2 * diameter, 2 * diameter);
			ip.setColor(255);
			ip.fillOval((int) Math.floor(diameter / 2.0), (int) Math.floor(diameter /
				2.0), diameter, diameter);
			stack.addSlice("" + i, ip);
		}
		return new ImagePlus("rod", stack);
	}

	/**
	 * Work out some summary stats
	 *
	 * @param imp 32-bit thickness image
	 * @return double[] containing mean, standard deviation and maximum as its 0th
	 *         and 1st and 2nd elements respectively
	 */
	private static double[] meanStdDev(final ImagePlus imp) {
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

	/**
	 * Draw a solid sphere in the foreground, padded with 1 voxel of background on
	 * all stack faces
	 *
	 * @param radius radius of the sphere.
	 * @return stack containing solid binary sphere
	 */
	private static ImagePlus sphere(final int radius) {
		final int side = 2 * radius + 2;
		final ImageStack stack = new ImageStack(side, side);
		final ImageProcessor ip = new ByteProcessor(side, side);
		stack.addSlice("", ip); // padding
		for (int zd = -radius; zd <= radius; zd++) {
			final int rc = (int) Math.round(Math.sqrt(radius * radius - zd * zd));
			final ImageProcessor ipr = new ByteProcessor(side, side);
			ipr.setColor(255);
			ipr.fillOval(radius + 1 - rc, radius + 1 - rc, 2 * rc, 2 * rc);
			stack.addSlice("", ipr);
		}
		final ImageProcessor ipe = new ByteProcessor(side, side);
		stack.addSlice("", ipe); // padding
		return new ImagePlus("sphere", stack);
	}
}
