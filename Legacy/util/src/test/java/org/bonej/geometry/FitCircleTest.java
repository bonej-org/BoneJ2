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
package org.bonej.geometry;

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
	/** Equally-spaced points on a full circle, without noise */
	final static double[][] points = FitCircle.getTestCircle(x, y, r, 1000, 0.0);
	/** Equally-spaced points on a full circle, with noise */
	final static double[][] noisyPoints = FitCircle.getTestCircle(x, y, r, 1000, 0.001);
	/** Equally-spaced points on a circular arc, without noise */
	final static double[][] arcPoints = FitCircle.getTestCircle(x, y, r, 100, 1, 1.5, 0.0);
	/** Equally-spaced points on a circular arc, with noise */
	final static double[][] noisyArcPoints = FitCircle.getTestCircle(x, y, r, 100, 1, 1.5, 0.001);

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
