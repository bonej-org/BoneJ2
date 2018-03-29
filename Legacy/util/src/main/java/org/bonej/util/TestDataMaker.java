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
package org.bonej.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.scijava.vecmath.Point3f;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Static methods to generate images for testing
 *
 * @author Michael Doube
 */
public class TestDataMaker {

	/**
	 * An octahedron with vertices defined by points using values of 2, 1.198039
	 * and 2.801961.
	 *
	 * It has a calculated surface area of 4.4558146404 units<sup>2</sup>
	 *
	 * @return points defining the vertices of the octahedron's triangles
	 */
	static public List<Point3f> octahedron() {
		final float a = 2.0f;
		final float b = 1.198039f;
		final float c = 2.801961f;
		final List<Point3f> points = new ArrayList<Point3f>();
		points.add(new Point3f(a, a, b));
		points.add(new Point3f(b, a, a));
		points.add(new Point3f(a, b, a));
		points.add(new Point3f(a, a, b));
		points.add(new Point3f(a, c, a));
		points.add(new Point3f(b, a, a));
		points.add(new Point3f(a, b, a));
		points.add(new Point3f(c, a, a));
		points.add(new Point3f(a, a, b));
		points.add(new Point3f(c, a, a));
		points.add(new Point3f(a, c, a));
		points.add(new Point3f(a, a, b));
		points.add(new Point3f(a, b, a));
		points.add(new Point3f(b, a, a));
		points.add(new Point3f(a, a, c));
		points.add(new Point3f(b, a, a));
		points.add(new Point3f(a, c, a));
		points.add(new Point3f(a, a, c));
		points.add(new Point3f(a, b, a));
		points.add(new Point3f(a, a, c));
		points.add(new Point3f(c, a, a));
		points.add(new Point3f(c, a, a));
		points.add(new Point3f(a, a, c));
		points.add(new Point3f(a, c, a));
		return points;
	}

	/**
	 * Generate a rod of circular cross-section. The rod is oriented with its
	 * axis in the z direction and in a stack of 2*diameter wide and high.
	 *
	 * @param length length of the rod.
	 * @param diameter diameter of the rod.
	 *
	 * @return an image containing the rod.
	 */
	public static ImagePlus rod(final int length, final int diameter) {
		final ImageStack stack = new ImageStack(2 * diameter, 2 * diameter);
		for (int i = 0; i < length; i++) {
			final ImageProcessor ip = new ByteProcessor(2 * diameter, 2 * diameter);
			ip.setColor(255);
			ip.fillOval((int) Math.floor(diameter / 2), (int) Math.floor(diameter / 2), diameter, diameter);
			stack.addSlice("" + i, ip);
		}
		return new ImagePlus("rod", stack);
	}

	/**
	 * Draw a solid sphere in the foreground, padded with 1 voxel of background
	 * on all stack faces
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

	/**
	 * Create a binary brick of arbitrary width, height and depth, padded with 1
	 * voxel of background on all faces.
	 *
	 * @param width width of the brick.
	 * @param height height of the brick.
	 * @param depth depth of the brick.
	 * @return image with brick in foreground
	 */
	public static ImagePlus brick(final int width, final int height, final int depth) {
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
	 * Draw the edges of a brick with 32 pixels of padding on all faces
	 *
	 * @param width Width of the box frame in pixels
	 * @param height Height of the box frame in pixels
	 * @param depth Depth of the box frame in pixels
	 * @return Image containing a 1-pixel wide outline of a 3D box
	 */
	public static ImagePlus boxFrame(final int width, final int height, final int depth) {
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
	 * Creates an ImagePlus with black (0x00) &amp; white (0xFF) noise
	 *
	 * @param width width of the image.
	 * @param height height of the image.
	 * @param depth depth of the image.
	 * @param ratio Probability that pixel P(x is black)
	 * @param generator A random generator for the noise. Using a generator with
	 *          predetermined {@link Random#Random(long)} seed} makes the result
	 *          of this method repeatable
	 * @return an image with binary noise.
	 */
	public static ImagePlus binaryNoise(final int width, final int height, final int depth, final double ratio,
										final Random generator) {
		final int npixels = width * height;
		final ImageStack stack = new ImageStack(width, height);
		for (int i = 0; i < depth; i++) {
			final ByteProcessor bp = new ByteProcessor(width, height);
			for (int index = 0; index < npixels; index++) {
				final double random = generator.nextDouble();
				if (random > ratio)
					bp.set(index, 255);
			}
			stack.addSlice(bp);
		}
		return new ImagePlus("binary-noise", stack);
	}

    /**
     * Creates and image with "plates" or "sheets".
     *
     * @param width width of the image.
     * @param height height of the image.
     * @param depth depth of the image.
     * @param spacing space between the sheets in z.
     * @return an image with xy-plates.
     */
    public static ImagePlus plates(final int width, final int height, final int depth, final int spacing) {
		final ImageStack stack = new ImageStack(width, height);
		for (int i = 0; i < depth; i++)
			stack.addSlice(new ByteProcessor(width, height));

		for (int i = 1; i <= depth; i += spacing) {
			final ByteProcessor bp = (ByteProcessor) stack.getProcessor(i);
			bp.add(255);
		}
		return new ImagePlus("plates", stack);
	}
}
