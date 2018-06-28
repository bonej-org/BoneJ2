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
