/*
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
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
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

public class EllipsoidTest {

	/** Ellipsoid of radii = 1 and centred on origin */
	Ellipsoid unitSphere = new Ellipsoid(1, 1, 1, 0, 0, 0, new double[][] { { 0, 0, 1 }, { 0, 1, 0 }, { 1, 0, 0 } });

	/** Ellipsoid of radii = 1 and centred on (17, 31, 71) */
	Ellipsoid unitSphereTrans = fitTo(FitEllipsoid.testEllipsoid(1, 1, 1, 0, 17, 31, 71, 0, 1000, true));

	/** Ellipsoid with radii of 1, 2, and 3, centred on (1,2,3) */
	Ellipsoid oneTwoThree = fitTo(FitEllipsoid.testEllipsoid(1, 2, 3, 0, 1, 2, 3, 0, 10000, true));

	/** Ellipsoid with radii of 3, 5, 17, centred on (0, 0, 0) */
	Ellipsoid threeFiveSeventeen = fitTo(FitEllipsoid.testEllipsoid(3, 5, 17, 0, 0, 0, 0, 0, 10000, true));

	/** Ellipsoid with radii of 17, 5, 3, centred on (0, 0, 0) */
	Ellipsoid seventeenFiveThree = fitTo(FitEllipsoid.testEllipsoid(17, 5, 3, 0, 0, 0, 0, 0, 10000, true));

	/** Ellipsoid rotated a bit */
	Ellipsoid rotated =fitTo(FitEllipsoid.testEllipsoid(7, 13, 17, Math.PI / 4.32, 0, 0, 0, 0, 1000, true));

	@Test
	public void testGetVolume() {
		assertEquals(Math.PI * 4 / 3, unitSphere.getVolume(), 1E-9);
		assertEquals(1 * 2 * 3 * Math.PI * 4 / 3, oneTwoThree.getVolume(), 1E-9);
		assertEquals(3 * 5 * 17 * Math.PI * 4 / 3, threeFiveSeventeen.getVolume(), 1E-9);
	}

	@Test
	public void testGetRadii() {
		assertArrayEquals(new double[] { 1, 1, 1 }, unitSphere.getRadii(), 1E-9);

		assertArrayEquals(new double[] { 3, 2, 1 }, oneTwoThree.getRadii(), 1E-9);

		assertArrayEquals(new double[] { 17, 5, 3 }, threeFiveSeventeen.getRadii(), 1E-9);

		assertArrayEquals(new double[] { 17, 13, 7 }, rotated.getRadii(), 1E-9);
	}

	@Test
	public void testContains() {
		final double[][] points = unitSphere.getSurfacePoints(1000);

		// centroid
		assertTrue(unitSphere.contains(0, 0, 0));

		// inside
		for (final double[] v : points) {
			final double rand = Math.random();
			final double x = v[0] * rand;
			final double y = v[1] * rand;
			final double z = v[2] * rand;
			assertTrue("Testing (" + x + ", " + y + ", " + z + ")", unitSphere.contains(x, y, z));
		}

		// outside
		for (final double[] v : points) {
			final double rand = Math.random();
			final double x = v[0] / rand;
			final double y = v[1] / rand;
			final double z = v[2] / rand;
			assertTrue("Testing (" + x + ", " + y + ", " + z + ")", !unitSphere.contains(x, y, z));
		}

		assertTrue("Testing centroid (1, 2, 3)", oneTwoThree.contains(1, 2, 3));

		// random vectors greater than major radius
		final double[][] vectors = Vectors.randomVectors(1000);
		for (final double[] v : vectors) {
			final double x = v[0] * 18;
			final double y = v[1] * 18;
			final double z = v[2] * 18;
			assertTrue(!threeFiveSeventeen.contains(x, y, z));
		}

		// random vectors smaller than the minor radius
		for (final double[] v : vectors) {
			final double x = v[0] * 2;
			final double y = v[1] * 2;
			final double z = v[2] * 2;
			assertTrue(threeFiveSeventeen.contains(x, y, z));
		}

		final double[][] pointsU = unitSphereTrans.getSurfacePoints(1000);

		// random vectors translated
		for (final double[] v : pointsU) {
			final double rand = Math.random();
			final double x = 17 + (v[0] - 17) * rand;
			final double y = 31 + (v[1] - 31) * rand;
			final double z = 71 + (v[2] - 71) * rand;
			assertTrue(unitSphereTrans.contains(x, y, z));
		}

		for (final double[] v : pointsU) {
			final double rand = Math.random();
			final double x = 17 + (v[0] - 17) / rand;
			final double y = 31 + (v[1] - 31) / rand;
			final double z = 71 + (v[2] - 71) / rand;
			assertTrue(!unitSphereTrans.contains(x, y, z));
		}

		final double[][] points4 = rotated.getSurfacePoints(1000);

		// inside
		for (final double[] p : points4) {
			// contract by random fraction
			final double rand = Math.random();
			final double x = p[0] * rand;
			final double y = p[1] * rand;
			final double z = p[2] * rand;
			assertTrue(rotated.contains(x, y, z));
		}

		// outside
		for (final double[] p : points4) {
			// dilate by random fraction
			final double rand = Math.random() * 0.5;
			final double x = p[0] / rand;
			final double y = p[1] / rand;
			final double z = p[2] / rand;
			assertTrue(!rotated.contains(x, y, z));
		}

		final double[][] points3 = seventeenFiveThree.getSurfacePoints(1000);

		// inside
		for (final double[] p : points3) {
			// contract by random fraction
			final double rand = Math.random();
			final double x = p[0] * rand;
			final double y = p[1] * rand;
			final double z = p[2] * rand;
			assertTrue(seventeenFiveThree.contains(x, y, z));
		}

		// outside
		for (final double[] p : points3) {
			// dilate by random fraction
			final double rand = Math.random() * 0.1;
			final double x = p[0] / rand;
			final double y = p[1] / rand;
			final double z = p[2] / rand;
			assertTrue(!seventeenFiveThree.contains(x, y, z));
		}

		final double[][] points2 = threeFiveSeventeen.getSurfacePoints(1000);

		// inside
		for (final double[] p : points2) {
			// contract by random fraction
			final double rand = Math.random();
			final double x = p[0] * rand;
			final double y = p[1] * rand;
			final double z = p[2] * rand;
			assertTrue(threeFiveSeventeen.contains(x, y, z));
		}

		// outside
		for (final double[] p : points2) {
			// dilate by random fraction
			final double rand = Math.random() / 3;
			final double x = p[0] / rand;
			final double y = p[1] / rand;
			final double z = p[2] / rand;
			assertTrue(!threeFiveSeventeen.contains(x, y, z));
		}
	}

	@Test
	public void testGetCentre() {
		assertArrayEquals(new double[] { 0, 0, 0 }, unitSphere.getCentre(), 1E-9);
		assertArrayEquals(new double[] { 1, 2, 3 }, oneTwoThree.getCentre(), 1E-9);
		assertArrayEquals(new double[] { 0, 0, 0 }, threeFiveSeventeen.getCentre(), 1E-9);
	}

	@Test
	public void testGetSurfacePoints() {
		final double[][] points = unitSphere.getSurfacePoints(10000);
		for (final double[] p : points) {
			assertEquals(1, Trig.distance3D(p), 1E-9);
		}
	}

	@Test
	public void testDilate() {
		unitSphere.dilate(1);
		final double[][] points = unitSphere.getSurfacePoints(10000);
		for (final double[] p : points) {
			assertEquals(2, Trig.distance3D(p), 1E-9);
		}
	}

	@Test
	public void testContract() {
		unitSphere.contract(0.015);
		final double[][] points = unitSphere.getSurfacePoints(10000);
		for (final double[] p : points) {
			assertEquals(0.985, Trig.distance3D(p), 1E-9);
		}
	}

	@Test
	public void testGetSortedRadii() {
		final int t = 1000;
		final Ellipsoid e = unitSphere.copy();
		for (int i = 0; i < t; i++) {
			e.dilate(Math.random(), Math.random(), Math.random());
			final double[] r = e.getSortedRadii();
			assertTrue(r[0] < r[1]);
			assertTrue(r[1] < r[2]);
		}
	}

	/**
	 * Find the best-fit ellipsoid using the default method (yuryPetrov)
	 *
	 * @param coordinates in double[n][3] format
	 * @return Object representing the best-fit ellipsoid
	 */
	private static Ellipsoid fitTo(final double[][] coordinates) {
		return new Ellipsoid(FitEllipsoid.yuryPetrov(coordinates));
	}

	@BeforeClass
	public static void oneTimeSetup() {
		FitEllipsoid.setSeed(0xc0ffee);
	}
}
