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

//import org.doube.jama.Matrix;
import org.junit.Test;
import org.scijava.vecmath.Point3f;

public class VectorsTest {
	Point3f p0 = new Point3f(1.0f, 2.0f, 3.0f);
	Point3f p1 = new Point3f(6.0f, 5.0f, 4.0f);
	Point3f p2 = new Point3f(9.0f, 8.0f, 7.0f);

	double[] point0 = { 1, 2, 3 };
	double[] point1 = { 6, 5, 4 };
	double[] point2 = { 9, 8, 7 };

	double[][] pa0 = { { 5 }, { 3 }, { 1 } };
	double[][] pa1 = { { 8 }, { 6 }, { 4 } };

	double[] a = { 5, 3, 1 };
	double[] b = { 8, 6, 4 };

	@Test
	public void testCrossProductPoint3fPoint3fPoint3f() {
		final Point3f result = Vectors.crossProduct(p0, p1, p2);
		final Point3f expected = new Point3f(6.0f, -12.0f, 6.0f);
		assertEquals(expected, result);
	}

	@Test
	public void testCrossProductDoubleArrayDoubleArrayDoubleArray() {
		final double[] result = Vectors.crossProduct(point0, point1, point2);
		final double[] expected = { 6, -12, 6 };
		assertArrayEquals(expected, result, 1e-12);
	}

	@Test
	public void testCrossProductDoubleArrayArrayDoubleArrayArray() {
		final double[][] result = Vectors.crossProduct(pa0, pa1);
		final double[][] expected = { { 6 }, { -12 }, { 6 } };
		assertEquals(expected[0][0], result[0][0], 1e-12);
		assertEquals(expected[1][0], result[1][0], 1e-12);
		assertEquals(expected[2][0], result[2][0], 1e-12);
	}

	@Test
	public void testCrossProductDoubleArrayDoubleArray() {
		final double[] result = Vectors.crossProduct(a, b);
		final double[] expected = { 6, -12, 6 };
		assertArrayEquals(expected, result, 1e-12);
	}

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

	/*
	 * @Test public void TestInv3() { final int n = 1000;
	 *
	 * for (int j = 0; j < n; j++) { final double nudge = 0.4;
	 *
	 * double b = Math.random() * (nudge + nudge) - nudge; double c =
	 * Math.random() * (nudge + nudge) - nudge; double a = Math.sqrt(1 - b * b -
	 * c * c);
	 *
	 * // zeroth column, should be very close to [1, 0, 0]^T (mostly x) double[]
	 * zerothColumn = { a, b, c };
	 *
	 * // form triangle in random plane double[] vector =
	 * Vectors.randomVectors(1)[0];
	 *
	 * // first column, should be very close to [0, 1, 0]^T double[] firstColumn
	 * = Vectors.norm(Vectors.crossProduct( zerothColumn, vector));
	 *
	 * // second column, should be very close to [0, 0, 1]^T double[]
	 * secondColumn = Vectors.norm(Vectors.crossProduct( zerothColumn,
	 * firstColumn));
	 *
	 * double[][] rotation = { zerothColumn, firstColumn, secondColumn };
	 *
	 * // array has subarrays as rows, need them as columns // matrix to invert
	 * rotation = Ellipsoid.transpose(rotation);
	 *
	 * // the method under test should calculate the matrix inverse double[][]
	 * invRotation = Vectors.inv3(rotation);
	 *
	 * // get a reference inverted matrix to test against Matrix R = new
	 * Matrix(rotation); double[][] arrayRinv = R.inverse().getArray();
	 *
	 * //TODO alternatively use the un-transposed rotation array because //the
	 * inverse of a rotation matrix is its transpose
	 *
	 * for (int i = 0; i < 3; i++) assertArrayEquals(arrayRinv[i],
	 * invRotation[i], 1E-12); }
	 *
	 * }
	 */
}
