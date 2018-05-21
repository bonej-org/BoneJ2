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

import org.junit.Test;

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

public class ConnectivityTest {

	private final Connectivity conn = new Connectivity();

	@Test
	public void testGetConnDensity() {
		final ImagePlus imp = boxFrame(32, 64, 128);
		final Calibration cal = imp.getCalibration();
		cal.pixelDepth = 0.2;
		cal.pixelHeight = 0.2;
		cal.pixelWidth = 0.2;
		// boxFrame adds 32 pixels of padding around the box
		final double stackVolume = (32 + 64) * (64 + 64) * (128 + 64) * (0.2 * 0.2 * 0.2);
		final double sumEuler = conn.getSumEuler(imp);
		final double deltaChi = conn.getDeltaChi(imp, sumEuler);
		final double connectivity = conn.getConnectivity(deltaChi);
		final double connD = conn.getConnDensity(imp, connectivity);
		assertEquals(5 / stackVolume, connD, 1e-12);
	}

	@Test
	public void testGetSumEulerCrossedCircle() {
		for (int size = 16; size < 1024; size *= 2) {
			final ImagePlus imp = crossedCircle(size);
			final double sumEuler = conn.getSumEuler(imp);
			assertEquals(-3, sumEuler, 1e-12);
		}
	}

	@Test
	public void testGetSumEulerBoxFrame() {
		for (int size = 16; size < 256; size *= 2) {
			final ImagePlus imp = boxFrame(size, size, size);
			final double sumEuler = conn.getSumEuler(imp);
			assertEquals(-4, sumEuler, 1e-12);
		}
	}

	/**
	 * Draw the edges of a brick with 32 pixels of padding on all faces
	 *
	 * @param width Width of the box frame in pixels
	 * @param height Height of the box frame in pixels
	 * @param depth Depth of the box frame in pixels
	 * @return Image containing a 1-pixel wide outline of a 3D box
	 */
	private static ImagePlus boxFrame(final int width, final int height,
		final int depth)
	{
		final ImageStack stack = new ImageStack(width + 64, height + 64);
		for (int s = 1; s <= depth + 64; s++) {
			final ImageProcessor ip = new ByteProcessor(width + 64, height + 64);
			ip.setColor(0);
			ip.fill();
			stack.addSlice(ip);
		}
		ImageProcessor ip = stack.getProcessor(32);
		ip.setColor(255);
		ip.drawRect(32, 32, width, height);
		ip = stack.getProcessor(32 + depth);
		ip.setColor(255);
		ip.drawRect(32, 32, width, height);
		for (int s = 33; s < 32 + depth; s++) {
			ip = stack.getProcessor(s);
			ip.setColor(255);
			ip.drawPixel(32, 32);
			ip.drawPixel(32, 31 + height);
			ip.drawPixel(31 + width, 32);
			ip.drawPixel(31 + width, 31 + height);
		}
		return new ImagePlus("box-frame", stack);
	}

	/**
	 * Draw a circle with vertical and horizontal crossing, then skeletonize it
	 *
	 * @param size width and height of the image, circle diameter is size/2
	 * @return image containing a white (255) circle on black (0) background
	 */
	private static ImagePlus crossedCircle(final int size) {
		final ImageProcessor ip = new ByteProcessor(size, size);
		ip.setColor(0);
		ip.fill();
		ip.setColor(255);
		ip.drawOval(size / 4, size / 4, size / 2, size / 2);
		ip.drawLine(size / 2, size / 4, size / 2, 3 * size / 4);
		ip.drawLine(size / 4, size / 2, 3 * size / 4, size / 2);
		return new ImagePlus("crossed-circle", ip);
	}
}
