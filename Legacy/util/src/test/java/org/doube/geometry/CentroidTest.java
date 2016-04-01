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
import static org.junit.Assert.fail;

import java.util.ArrayList;

import org.junit.Test;

import sc.fiji.analyzeSkeleton.Point;

public class CentroidTest {

	double[] oneDa = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
	double[] oneDb = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 };
	double[] oneDc = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 };
	double[] oneDd = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
	double[] oneDe = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
	double[] oneDf = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 };
	double[] oneDg = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 };
	double[] oneDh = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
	double[] oneDi = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
	double[] oneDj = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

	double[][] badNd = { oneDa, oneDb, oneDc, oneDd, oneDe, oneDf, oneDg, oneDh, oneDi, oneDj };

	double[][] goodNd = { oneDa, oneDa, oneDa, oneDa, oneDa, oneDa, oneDa, oneDa, oneDa };

	@Test
	public void testGetCentroidDoubleArrayArray() {
		try {
			Centroid.getCentroid(badNd);
			fail("Should throw IllegalArgumentException");
		} catch (final IllegalArgumentException e) {
		}

		// A 20-D centroid
		final double[] centroid = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
		assertArrayEquals(centroid, Centroid.getCentroid(goodNd), 1E-9);

		// A 3-D array
		final double[][] threeD = { { 1, 2, 3 }, { 4, 5, 6 }, { 7, 8, 9 }, { 10, 11, 12 }, { 13, 14, 15 },
				{ 16, 17, 18 } };
		// Its centroid
		final double[] centroid3d = { 8.5, 9.5, 10.5 };
		assertArrayEquals(centroid3d, Centroid.getCentroid(threeD), 1E-9);

		// A 2-D array
		final double[][] twoD = { { 1, 2 }, { 4, 5 }, { 7, 8 }, { 10, 11 }, { 13, 14 }, { 16, 17 } };
		// Its centroid
		final double[] centroid2d = { 8.5, 9.5 };
		assertArrayEquals(centroid2d, Centroid.getCentroid(twoD), 1E-9);

		// A 1-D array
		final double[][] oneD = { { 1 }, { 4 }, { 7 }, { 10 }, { 13 }, { 16 } };
		// Its centroid
		final double[] centroid1d = { 8.5 };
		assertArrayEquals(centroid1d, Centroid.getCentroid(oneD), 1E-9);

	}

	@Test
	public void testGetCentroidDoubleArray() {
		assertEquals(10.5, Centroid.getCentroid(oneDa), 1E-9);
		assertEquals(10.0, Centroid.getCentroid(oneDb), 1E-9);
		assertEquals(9.0, Centroid.getCentroid(oneDc), 1E-9);
		assertEquals(8.5, Centroid.getCentroid(oneDd), 1E-9);
		assertEquals(8.0, Centroid.getCentroid(oneDe), 1E-9);
		assertEquals(7.5, Centroid.getCentroid(oneDf), 1E-9);
		assertEquals(7.0, Centroid.getCentroid(oneDg), 1E-9);
		assertEquals(6.5, Centroid.getCentroid(oneDh), 1E-9);
		assertEquals(6.0, Centroid.getCentroid(oneDi), 1E-9);
		assertEquals(5.5, Centroid.getCentroid(oneDj), 1E-9);
	}

	@Test
	public void testGetCentroidArrayListPoint() {
		final ArrayList<Point> points = new ArrayList<Point>();
		for (int iMax = 10; iMax < 1000; iMax++) {
			points.clear();
			for (int i = 1; i < iMax; i++) {
				points.add(new Point(i, i, i));
			}
			final double expected = iMax / 2.0;
			final double[] expecteds = { expected, expected, expected };
			assertArrayEquals(expecteds, Centroid.getCentroid(points), 1E-9);
		}
	}

}
