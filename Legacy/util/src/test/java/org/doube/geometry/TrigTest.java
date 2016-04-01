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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TrigTest {

	double[] p0 = { 1, 2, 3 };
	double[] p1 = { 4, 5, 6 };

	@Test
	public void testDistance3DDoubleArrayDoubleArray() {
		final double result = Trig.distance3D(p0, p1);
		final double expected = Math.sqrt(27);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleDoubleDoubleDoubleDoubleDouble() {
		final double result = Trig.distance3D(1, 2, 3, 4, 5, 6);
		final double expected = Math.sqrt(27);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleDoubleDouble() {
		final double result = Trig.distance3D(1, 2, 3);
		final double expected = Math.sqrt(14);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleArray() {
		final double result = Trig.distance3D(p1);
		final double expected = Math.sqrt(77);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testAngle3D() {
		double result = Trig.angle3D(1, 2, 3, 4, 5, 6, 7, 8, 9);
		double expected = 0;
		assertEquals(expected, result, 1e-12);
		result = Trig.angle3D(1, 2, 3, 4, 5, 6, 0, 0, 0);
		expected = Math.acos(32 / (Math.sqrt(14) * Math.sqrt(77)));
		assertEquals(expected, result, 1e-12);
	}

}
