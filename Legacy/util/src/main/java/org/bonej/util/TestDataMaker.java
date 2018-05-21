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

import java.util.Random;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Static methods to generate images for testing
 *
 * @author Michael Doube
 */
public final class TestDataMaker {

	private TestDataMaker() {}

	/**
	 * Creates an ImagePlus with black (0x00) &amp; white (0xFF) noise
	 *
	 * @param generator A random generator for the noise. Using a generator with
	 *          predetermined {@link Random#Random(long)} seed} makes the result
	 *          of this method repeatable
	 * @return an image with binary noise.
	 */
	// TODO Move to AnisotropyTest
	public static ImagePlus binaryNoise(final Random generator) {
		final int size = 256;
		final int npixels = size * size;
		final ImageStack stack = new ImageStack(size, size);
		for (int i = 0; i < size; i++) {
			final ByteProcessor bp = new ByteProcessor(size, size);
			for (int index = 0; index < npixels; index++) {
				final double random = generator.nextDouble();
				if (random > 0.25) bp.set(index, 255);
			}
			stack.addSlice(bp);
		}
		return new ImagePlus("binary-noise", stack);
	}

	/**
	 * Draw the edges of a brick with 32 pixels of padding on all faces
	 *
	 * @param width Width of the box frame in pixels
	 * @param height Height of the box frame in pixels
	 * @param depth Depth of the box frame in pixels
	 * @return Image containing a 1-pixel wide outline of a 3D box
	 */
	// TODO Move to ConnectivityTest
	public static ImagePlus boxFrame(final int width, final int height,
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
	 * Create a binary brick of arbitrary width, height and depth, padded with 1
	 * voxel of background on all faces.
	 *
	 * @param width width of the brick.
	 * @param height height of the brick.
	 * @param depth depth of the brick.
	 * @return image with brick in foreground
	 */
	public static ImagePlus brick(final int width, final int height,
		final int depth)
	{
		final ImageStack stack = new ImageStack(width + 2, height + 2);
		final ImageProcessor ip = new ByteProcessor(width + 2, height + 2);
		stack.addSlice("", ip);
		for (int i = 0; i < depth; i++) {
			final ImageProcessor ip2 = new ByteProcessor(width + 2, height + 2);
			ip2.setColor(255);
			ip2.setRoi(1, 1, width, height);
			ip2.fill();
			stack.addSlice("", ip2);
		}
		final ImageProcessor ip3 = new ByteProcessor(width + 2, height + 2);
		stack.addSlice("", ip3);
		return new ImagePlus("brick", stack);
	}

	/**
	 * Draw a circle with vertical and horizontal crossing, then skeletonize it
	 *
	 * @param size width and height of the image, circle diameter is size/2
	 * @return image containing a white (255) circle on black (0) background
	 */
	// TODO Move to ConnectivityTest
	public static ImagePlus crossedCircle(final int size) {
		final ImageProcessor ip = new ByteProcessor(size, size);
		ip.setColor(0);
		ip.fill();
		ip.setColor(255);
		ip.drawOval(size / 4, size / 4, size / 2, size / 2);
		ip.drawLine(size / 2, size / 4, size / 2, 3 * size / 4);
		ip.drawLine(size / 4, size / 2, 3 * size / 4, size / 2);
		return new ImagePlus("crossed-circle", ip);
	}

	/**
	 * Creates and image with "plates" or "sheets".
	 *
	 * @return an image with xy-plates.
	 */
	// TODO Move to AnisotropyTest
	public static ImagePlus plates() {
		final int size = 256;
		final int spacing = 8;
		final ImageStack stack = new ImageStack(size, size);
		for (int i = 0; i < size; i++)
			stack.addSlice(new ByteProcessor(size, size));

		for (int i = 1; i <= size; i += spacing) {
			final ByteProcessor bp = (ByteProcessor) stack.getProcessor(i);
			bp.add(255);
		}
		return new ImagePlus("plates", stack);
	}

	/**
	 * Generate a rod of circular cross-section. The rod is oriented with its axis
	 * in the z direction and in a stack of 2*diameter wide and high.
	 *
	 * @param length length of the rod.
	 * @param diameter diameter of the rod.
	 * @return an image containing the rod.
	 */
	// TODO Move to LocalThicknessTest
	public static ImagePlus rod(final int length, final int diameter) {
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
	 * Draw a solid sphere in the foreground, padded with 1 voxel of background on
	 * all stack faces
	 *
	 * @param radius radius of the sphere.
	 * @return stack containing solid binary sphere
	 */
	public static ImagePlus sphere(final int radius) {
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
