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

/**
 * Methods for doing helpful things with arrays
 *
 * @author Michael Doube
 *
 */
public class ArrayHelper {

	/**
	 * Remove null values from an array
	 *
	 * @param d
	 * @return array containing only non-null values. Element ordering remains
	 *         intact, null elements are replaced with no element, so the
	 *         resulting array is the length of the input array minus the number
	 *         of null elements.
	 */
	public static double[][] removeNulls(final double[][] d) {
		final int l = d.length;
		int nullCount = 0;
		for (int i = 0; i < l; i++)
			if (d[i] == null)
				nullCount++;
		if (nullCount == 0)
			return d;
		final int nonNulls = l - nullCount;
		final double[][] array = new double[nonNulls][];

		int j = 0;
		for (int i = 0; i < l; i++) {
			if (d[i] != null) {
				array[j] = d[i];
				j++;
			}
		}

		return array;
	}

	/**
	 * Transpose a square (rows and columns all the same length) double array so
	 * that row values become column values and vice versa
	 *
	 * @param d
	 * @return
	 * @throws IllegalArgumentException
	 *             if arrays are not all of the same length
	 */
	public static double[][] transpose(final double[][] d) {
		final int l = d.length;

		final double[][] t = new double[l][l];
		for (int i = 0; i < l; i++) {
			final double[] di = d[i];
			if (di.length != l)
				throw new IllegalArgumentException();
			for (int j = 0; j < l; j++)
				t[j][i] = di[j];
		}
		return t;
	}

	/**
	 * Matrix-free version of Matrix.times
	 *
	 * Multiplies a by b in the matrix multiplication scheme c = ab
	 *
	 * @param a
	 * @param b
	 * @return c
	 */
	public static double[][] times(final double[][] a, final double[][] b) {
		final int am = a.length;
		final int an = a[0].length;
		final int bm = b.length;
		final int bn = b[0].length;
		if (bm != an) {
			throw new IllegalArgumentException("Matrix inner dimensions must agree.");
		}
		final double[][] c = new double[am][bn];
		final double[] bcolj = new double[an];
		for (int j = 0; j < bn; j++) {
			for (int k = 0; k < an; k++) {
				bcolj[k] = b[k][j];
			}
			for (int i = 0; i < am; i++) {
				final double[] arowi = a[i];
				double s = 0;
				for (int k = 0; k < an; k++) {
					s += arowi[k] * bcolj[k];
				}
				c[i][j] = s;
			}
		}
		return c;
	}
}
