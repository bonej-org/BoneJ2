/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.util;

import Jama.Matrix;
import ij.IJ;

/**
 * Utility methods for working with {@link Matrix}es
 *
 * @author Michael Doube
 * @author Mark Hiner
 */
public final class MatrixUtils {

	private MatrixUtils() {}

	/**
	 * Get the diagonal of the matrix as a column vector
	 *
	 * @param matrix a JAMA matrix.
	 * @return Column vector containing diagonal
	 */
	public static Matrix diag(final Matrix matrix) {
		final int min = Math.min(matrix.getRowDimension(), matrix
			.getColumnDimension());
		final double[][] diag = new double[min][1];
		for (int i = 0; i < min; i++) {
			diag[i][0] = matrix.get(i, i);
		}
		return new Matrix(diag);
	}

	/**
	 * Check if a 3 x 3 Matrix is right handed. If the matrix is a rotation
	 * matrix, then right-handedness implies rotation only, while left-handedness
	 * implies a reflection will be performed.
	 *
	 * @param matrix a JAMA matrix.
	 * @return true if the Matrix is right handed, false if it is left handed
	 */
	// TODO Move to Moments
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

		return dot > 0;
	}

	/**
	 * Check if a rotation matrix will flip the direction of the z component of
	 * the original
	 *
	 * @param matrix a JAMA matrix.
	 * @return true if the rotation matrix will cause z-flipping
	 */
	// TODO Move to Moments
	public static boolean isZFlipped(final Matrix matrix) {
		final double x2 = matrix.get(2, 0);
		final double y2 = matrix.get(2, 1);
		final double z2 = matrix.get(2, 2);

		final double dot = x2 + y2 + z2;

		return dot < 0;
	}

	/**
	 * Create an m * n Matrix filled with 1
	 *
	 * @param m number of rows
	 * @param n number of columns
	 * @return m * n Matrix filled with 1
	 */
	// TODO Move to FitEllipsoid
	public static Matrix ones(final int m, final int n) {
		final double[][] ones = new double[m][n];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				ones[i][j] = 1;
			}
		}
		return new Matrix(ones);
	}

	/**
	 * Print the Matrix to the ImageJ log
	 *
	 * @param matrix a JAMA matrix.
	 * @param title Title of the Matrix
	 */
	public static void printToIJLog(final Matrix matrix, final String title) {
		if (!title.isEmpty()) IJ.log(title);
		final int nCols = matrix.getColumnDimension();
		final int nRows = matrix.getRowDimension();
		final double[][] eVal = matrix.getArrayCopy();
		for (int r = 0; r < nRows; r++) {
			final StringBuilder row = new StringBuilder("||");
			for (int c = 0; c < nCols; c++) {
				row.append(IJ.d2s(eVal[r][c], 3)).append("|");
			}
			row.append("|");
			IJ.log(row.toString());
		}
		IJ.log("");
	}
}
