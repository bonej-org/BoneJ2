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

public class FitCircleTest {
	/** centroid x coordinate */
	final static double x = -100;
	/** centroid y coordinate */
	final static double y = 100;
	/** radius */
	final static double r = 100;
	/** expected result */
	final static double[] expected = { x, y, r };
	/** Equally-spaced points on a full circle, without noise */
	final static double[][] points = FitCircle.getTestCircle(x, y, r, 1000, 0.0);
	/** Equally-spaced points on a full circle, with noise */
	final static double[][] noisyPoints = FitCircle.getTestCircle(x, y, r, 1000, 0.001);
	/** Equally-spaced points on a circular arc, without noise */
	final static double[][] arcPoints = FitCircle.getTestCircle(x, y, r, 100, 1, 1.5, 0.0);
	/** Equally-spaced points on a circular arc, with noise */
	final static double[][] noisyArcPoints = FitCircle.getTestCircle(x, y, r, 100, 1, 1.5, 0.001);

	@Test
	public void testKasaFit() {
		assertArrayEquals(expected, FitCircle.kasaFit(points), 1e-10);
		assertArrayEquals(expected, FitCircle.kasaFit(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.kasaFit(arcPoints), 1e-10);
		assertArrayEquals(expected, FitCircle.kasaFit(noisyArcPoints), 1);
	}

	@Test
	public void testPrattNewton() {
		assertArrayEquals(expected, FitCircle.prattNewton(points), 1e-10);
		assertArrayEquals(expected, FitCircle.prattNewton(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.prattNewton(arcPoints), 1e-10);
		assertArrayEquals(expected, FitCircle.prattNewton(noisyArcPoints), 1);
	}

	@Test
	public void testPrattSVD() {
		assertArrayEquals(expected, FitCircle.prattSVD(points), 1e-10);
		assertArrayEquals(expected, FitCircle.prattSVD(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.prattSVD(arcPoints), 1e-10);
		assertArrayEquals(expected, FitCircle.prattSVD(noisyArcPoints), 1);
	}

	@Test
	public void testTaubinNewton() {
		assertArrayEquals(expected, FitCircle.taubinNewton(points), 1e-10);
		assertArrayEquals(expected, FitCircle.taubinNewton(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.taubinNewton(arcPoints), 1e-10);
		assertArrayEquals(expected, FitCircle.taubinNewton(noisyArcPoints), 1);
	}

	@Test
	public void testTaubinSVD() {
		assertArrayEquals(expected, FitCircle.taubinSVD(points), 1e-10);
		assertArrayEquals(expected, FitCircle.taubinSVD(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.taubinSVD(arcPoints), 1e-10);
		assertArrayEquals(expected, FitCircle.taubinSVD(noisyArcPoints), 1);
	}

	@Test
	public void testHyperSimple() {
		assertArrayEquals(expected, FitCircle.hyperSimple(points), 1e-6);
		assertArrayEquals(expected, FitCircle.hyperSimple(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.hyperSimple(arcPoints), 1e-6);
		assertArrayEquals(expected, FitCircle.hyperSimple(noisyArcPoints), 1);
	}

	@Test
	public void testHyperStable() {
		assertArrayEquals(expected, FitCircle.hyperSimple(points), 1e-6);
		assertArrayEquals(expected, FitCircle.hyperSimple(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.hyperSimple(arcPoints), 1e-6);
		assertArrayEquals(expected, FitCircle.hyperSimple(noisyArcPoints), 1);
	}

	@Test
	public void testLevenMarqFull() {
		assertArrayEquals(expected, FitCircle.levenMarqFull(points), 1e-10);
		assertArrayEquals(expected, FitCircle.levenMarqFull(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.levenMarqFull(arcPoints), 1e-10);
		assertArrayEquals(expected, FitCircle.levenMarqFull(noisyArcPoints), 1);
	}

	@Test
	public void testLevenMarqRed() {
		assertArrayEquals(expected, FitCircle.levenMarqRed(points), 1e-10);
		assertArrayEquals(expected, FitCircle.levenMarqRed(noisyPoints), 1e-2);
		assertArrayEquals(expected, FitCircle.levenMarqRed(arcPoints), 1e-10);
		assertArrayEquals(expected, FitCircle.levenMarqRed(noisyArcPoints), 1);
	}

	@Test
	public void testGetTestCircle() {
		final double r2 = r * r;
		for (int i = 0; i < points.length; i++) {
			final double xi = points[i][0];
			final double yi = points[i][1];
			final double equation = (xi - x) * (xi - x) + (yi - y) * (yi - y);
			assertEquals(r2, equation, 1e-10);
		}
		for (int i = 0; i < noisyPoints.length; i++) {
			final double xi = noisyPoints[i][0];
			final double yi = noisyPoints[i][1];
			final double equation = (xi - x) * (xi - x) + (yi - y) * (yi - y);
			assertEquals(r2, equation, 10);
		}
		for (int i = 0; i < arcPoints.length; i++) {
			final double xi = arcPoints[i][0];
			final double yi = arcPoints[i][1];
			final double equation = (xi - x) * (xi - x) + (yi - y) * (yi - y);
			assertEquals(r2, equation, 1e-10);
		}
		for (int i = 0; i < noisyArcPoints.length; i++) {
			final double xi = noisyArcPoints[i][0];
			final double yi = noisyArcPoints[i][1];
			final double equation = (xi - x) * (xi - x) + (yi - y) * (yi - y);
			assertEquals(r2, equation, 10);
		}
	}

}
