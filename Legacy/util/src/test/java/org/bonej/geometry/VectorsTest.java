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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VectorsTest {

	@Test
	public void testRandomVectors() {
		final int n = 1000;
		final double[][] v = Vectors.randomVectors(n);
		// check that vectors are unit vectors
		for (int i = 0; i < n; i++) {
			final double x = v[i][0];
			final double y = v[i][1];
			final double z = v[i][2];
			final double length = Math.sqrt(x * x + y * y + z * z);
			assertEquals(1, length, 1e-9);
		}
	}

	@Test
	public void testRegularVectors() {
		final int n = 1000;
		final double[][] v = Vectors.regularVectors(n);
		// check that vectors are unit vectors
		for (int i = 0; i < n; i++) {
			final double x = v[i][0];
			final double y = v[i][1];
			final double z = v[i][2];
			final double length = Math.sqrt(x * x + y * y + z * z);
			assertEquals(1, length, 1e-9);
		}
	}
}
