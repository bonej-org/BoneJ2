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
package org.doube.util;

import Jama.Matrix;
import ij.IJ;

/**
 * Utility methods for working with {@link Matrix}es
 *
 * @author Michael Doube
 * @author Mark Hiner
 */
public class MatrixUtils {

	/**
	 * Get the diagonal of the matrix as a column vector
	 *
	 * @return Column vector containing diagonal
	 */
	public static Matrix diag(final Matrix matrix) {
		final int min = Math.min(matrix.getRowDimension(), matrix.getColumnDimension());
		final double[][] diag = new double[min][1];
		for (int i = 0; i < min; i++) {
			diag[i][0] = matrix.get(i, i);
		}
		return new Matrix(diag);
	}

	/**
	 * Print Matrix to ImageJ log window
	 */
	public static void printToIJLog(final Matrix matrix) {
		printToIJLog(matrix, "");
		return;
	}

	/**
	 * Print the Matrix to the ImageJ log
	 *
	 * @param title
	 *            Title of the Matrix
	 */
	public static void printToIJLog(final Matrix matrix, final String title) {
		if (!title.isEmpty())
			IJ.log(title);
		final int nCols = matrix.getColumnDimension();
		final int nRows = matrix.getRowDimension();
		final double[][] eVal = matrix.getArrayCopy();
		for (int r = 0; r < nRows; r++) {
			String row = "||";
			for (int c = 0; c < nCols; c++) {
				row = row + IJ.d2s(eVal[r][c], 3) + "|";
			}
			row = row + "|";
			IJ.log(row);
		}
		IJ.log("");
		return;
	}

	/**
	 * Check if a 3 x 3 Matrix is right handed. If the matrix is a rotation
	 * matrix, then right-handedness implies rotation only, while
	 * left-handedness implies a reflection will be performed.
	 *
	 * @return true if the Matrix is right handed, false if it is left handed
	 */
	public static boolean isRightHanded(final Matrix matrix) {
		if (matrix.getColumnDimension() != 3 || matrix.getRowDimension() != 3) {
			throw new IllegalArgumentException();
		}

		final double x0 = matrix.get(0, 0);
		final double x1 = matrix.get(1, 0);
		final double x2 = matrix.get(2, 0);
		final double y0 = matrix.get(0, 1);
		final double y1 = matrix.get(1, 1);
		final double y2 = matrix.get(2, 1);
		final double z0 = matrix.get(0, 2);
		final double z1 = matrix.get(1, 2);
		final double z2 = matrix.get(2, 2);

		final double c0 = x1 * y2 - x2 * y1;
		final double c1 = x2 * y0 - x0 * y2;
		final double c2 = x0 * y1 - x1 * y0;

		final double dot = c0 * z0 + c1 * z1 + c2 * z2;

		if (dot > 0)
			return true;

		return false;
	}

	/**
	 * Check if a rotation matrix will flip the direction of the z component of
	 * the original
	 *
	 * @return true if the rotation matrix will cause z-flipping
	 */
	public static boolean isZFlipped(final Matrix matrix) {
		final double x2 = matrix.get(2, 0);
		final double y2 = matrix.get(2, 1);
		final double z2 = matrix.get(2, 2);

		final double dot = x2 + y2 + z2;

		if (dot < 0)
			return true;

		return false;
	}

	/**
	 * Create an n * n square identity matrix with 1 on the diagonal and 0
	 * elsewhere
	 *
	 * @param n
	 *            square matrix dimension
	 * @return n * n identity matrix
	 */
	public static Matrix eye(final int n) {
		return eye(n, n);
	}

	/**
	 * Create an m * n identity matrix
	 *
	 * @param m
	 * @param n
	 * @return
	 */
	public static Matrix eye(final int m, final int n) {
		final double[][] eye = new double[m][n];
		final int min = Math.min(m, n);
		for (int i = 0; i < min; i++) {
			eye[i][i] = 1;
		}
		return new Matrix(eye);
	}

	/**
	 * Create an m * n Matrix filled with 1
	 *
	 * @param m
	 *            number of rows
	 * @param n
	 *            number of columns
	 * @return m * n Matrix filled with 1
	 */
	public static Matrix ones(final int m, final int n) {
		final double[][] ones = new double[m][n];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				ones[i][j] = 1;
			}
		}
		return new Matrix(ones);
	}
}
