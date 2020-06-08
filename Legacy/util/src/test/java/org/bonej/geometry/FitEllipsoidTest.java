/*
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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
package org.bonej.geometry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

public class FitEllipsoidTest {

	final static double a = 1.5;
	final static double b = 2.0;
	final static double c = 3.0;
	final static double r = 1.0;
	final static double x = 1.2;
	final static double y = -3.4;
	final static double z = 7.7;

	@Test
	public void testYuryPetrov() {
		final double[][] points = FitEllipsoid.testEllipsoid(a, b, c, r, x, y, z, 0.0, 1000, true);
		final double[][] noisyPoints = FitEllipsoid.testEllipsoid(a, b, c, r, x, y, z, 0.01, 1000, true);
		final double[] centre = { x, y, z };
		final double[] radii = { c, b, a };
		Object[] result = FitEllipsoid.yuryPetrov(points);
		assertArrayEquals(centre, (double[]) result[0], 1e-10);
		assertArrayEquals(radii, (double[]) result[1], 1e-10);
		result = FitEllipsoid.yuryPetrov(noisyPoints);
		assertArrayEquals(centre, (double[]) result[0], 1e-2);
		assertArrayEquals(radii, (double[]) result[1], 1e-2);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testYuryPetrovCylinder() {
		//generate points on a cylinder with d = h, centred on 0, 0, 0.
		final int r = 50;
		final int nPoints = 100;
		final double[][] points = new double[(int)(2 * r * nPoints)][3];
		final double radialIncrement = 2 * Math.PI / nPoints;
		int p = 0;
		for (double z = -r; z < r; z += 1) {
			for (double theta = -Math.PI; theta < Math.PI; theta += radialIncrement) {
				theta += radialIncrement;
				final double x = Math.cos(theta) * r;
				final double y = Math.sin(theta) * r;
				final double[] point = {x, y, z};
				points[p] = point;
				p++;
			}
		}
		FitEllipsoid.yuryPetrov(points);
	}

	@Test
	public void testTestEllipsoid() {
		// spherical case
		final double r = 8.3;
		final double r2 = 8.3 * 8.3;
		double[][] xyz = FitEllipsoid.testEllipsoid(r, r, r, 0, 0, 0, 0, 0.0, 1000, true);
		for (int i = 0; i < xyz.length; i++) {
			final double xi = xyz[i][0];
			final double yi = xyz[i][1];
			final double zi = xyz[i][2];
			final double equation = xi * xi / r2 + yi * yi / r2 + zi * zi / r2;
			assertEquals(1.0, equation, 1e-15);
		}

		// scalene ellipsoid
		xyz = FitEllipsoid.testEllipsoid(a, b, c, 0, 0, 0, 0, 0.0, 1000, true);
		for (int i = 0; i < xyz.length; i++) {
			final double xi = xyz[i][0];
			final double yi = xyz[i][1];
			final double zi = xyz[i][2];
			final double equation = xi * xi / (a * a) + yi * yi / (b * b) + zi * zi / (c * c);
			assertEquals(1.0, equation, 1e-15);
		}
	}

	@BeforeClass
	public static void oneTimeSetup() {
		FitEllipsoid.setSeed(0xc0ffee);
	}
}
