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
package org.doube.geometry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FitEllipsoidTest {

	final static double a = 1.5;
	final static double b = 2.0;
	final static double c = 3.0;
	final static double r = 1.0;
	final static double x = 1.2;
	final static double y = -3.4;
	final static double z = 7.7;
	final static double[][] points = FitEllipsoid.testEllipsoid(a, b, c, r, x, y, z, 0.0, 1000, true);
	final static double[][] noisyPoints = FitEllipsoid.testEllipsoid(a, b, c, r, x, y, z, 0.01, 1000, true);

	@Test
	public void testYuryPetrov() {
		final double[] centre = { x, y, z };
		final double[] radii = { c, b, a };
		Object[] result = FitEllipsoid.yuryPetrov(points);
		assertArrayEquals(centre, (double[]) result[0], 1e-10);
		assertArrayEquals(radii, (double[]) result[1], 1e-10);
		result = FitEllipsoid.yuryPetrov(noisyPoints);
		assertArrayEquals(centre, (double[]) result[0], 1e-2);
		assertArrayEquals(radii, (double[]) result[1], 1e-2);
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
}
