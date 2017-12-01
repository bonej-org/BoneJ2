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

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import Jama.SingularValueDecomposition;

/**
 * Methods for fitting circles to coordinates
 *
 * @author Michael Doube, ported from Nikolai Chernov's MATLAB scripts
 * @see
 * 		<p>
 *      Al-Sharadqha & Chernov (2009)
 *      <a href="http://dx.doi.org/10.1214/09-EJS419"> Error analysis for circle
 *      fitting algorithms</a>. Electronic Journal of Statistics 3, pp. 886-911
 *      <br/>
 *      <br />
 *      <a href="http://www.math.uab.edu/~chernov/cl/MATLABcircle.html" >http://
 *      www.math.uab.edu/~chernov/cl/MATLABcircle.html</a>
 *      </p>
 * @deprecated Only used by deprecated code.
 */
@Deprecated
public class FitCircle {

	/**
	 * Chernov's non-biased Hyper algebraic method. Stability optimised version.
	 *
	 * @see
	 * 		<p>
	 *      <a href="http://www.math.uab.edu/~chernov/cl/HyperSVD.m">http://www.
	 *      math .uab.edu/~chernov/cl/HyperSVD.m</a>
	 *      </p>
	 *
	 * @param points
	 *            double[n][2] containing n (<i>x</i>, <i>y</i>) coordinates
	 * @return 3-element double[] containing (<i>x</i>, <i>y</i>) centre and
	 *         circle radius
	 */
	public static double[] hyperStable(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");
		final double[] centroid = Centroid.getCentroid(points);

		double sumZ = 0;
		final double[][] zxy1 = new double[nPoints][4];
		// centre data and assign vector values
		for (int n = 0; n < nPoints; n++) {
			final double x = points[n][0] - centroid[0];
			final double y = points[n][1] - centroid[1];
			final double z = x * x + y * y;
			sumZ += z;
			zxy1[n][0] = z;
			zxy1[n][1] = x;
			zxy1[n][2] = y;
			zxy1[n][3] = 1;
		}

		final Matrix ZXY1 = new Matrix(zxy1);
		final SingularValueDecomposition svd = new SingularValueDecomposition(ZXY1);

		final Matrix S = svd.getS();
		final Matrix V = svd.getV();

		Matrix A;

		// singular case
		if (S.get(3, 3) / S.get(0, 0) < 1e-12) {
			A = V.getMatrix(0, V.getRowDimension() - 1, 3, 3);
		} else {
			// regular case
			final Matrix Y = V.times(S.times(V.transpose()));

			final double[][] bInv = { { 0, 0, 0, 0.5 }, { 0, 1, 0, 0 }, { 0, 0, 1, 0 },
					{ 0.5, 0, 0, -2 * sumZ / nPoints } };
			final Matrix Binv = new Matrix(bInv);
			final EigenvalueDecomposition ED = new EigenvalueDecomposition((Y.transpose()).times(Binv.times(Y)));
			final Matrix D = ED.getD(); // eigenvalues
			final Matrix E = ED.getV(); // eigenvectors

			final int col = getNthSmallestCol(D, 2);

			A = E.getMatrix(0, E.getRowDimension() - 1, col, col);

			for (int i = 0; i < 4; i++) {
				S.set(i, i, 1 / S.get(i, i));
			}
			A = V.times(S.times((V.transpose()).times(A)));
		}

		final double a0 = A.get(0, 0);
		final double a1 = A.get(1, 0);
		final double a2 = A.get(2, 0);
		final double a3 = A.get(3, 0);

		final double[] centreRadius = new double[3];
		centreRadius[0] = -(a1 / a0) / 2 + centroid[0];
		centreRadius[1] = -(a2 / a0) / 2 + centroid[1];
		centreRadius[2] = (Math.sqrt(a1 * a1 + a2 * a2 - 4 * a0 * a3) / Math.abs(a0)) / 2;
		return centreRadius;
	}

	/**
	 * Generate coordinates of a circular arc
	 *
	 * @param x
	 *            x coordinate of centre
	 * @param y
	 *            y coordinate of centre
	 * @param r
	 *            radius of circle
	 * @param startAngle
	 *            initial angle in radians
	 * @param endAngle
	 *            final angle in radians
	 * @param n
	 *            Number of coordinates
	 * @param noise
	 *            Add noise of intensity 'noise'
	 *
	 * @return
	 */
	public static double[][] getTestCircle(final double x, final double y, final double r, final int n,
			final double startAngle, final double endAngle, final double noise) {
		final double[][] testCircle = new double[n][2];
		final double arc = (endAngle - startAngle) / (2 * Math.PI);
		for (int i = 0; i < n; i++) {
			final double theta = startAngle + i * 2 * Math.PI * arc / n;
			testCircle[i][0] = r * (1 + noise * (Math.random() - 0.5)) * Math.sin(theta) + x;
			testCircle[i][1] = r * (1 + noise * (Math.random() - 0.5)) * Math.cos(theta) + y;
		}

		return testCircle;
	}

	/**
	 * Generate coordinates of a circle
	 *
	 * @param x
	 *            x coordinate of centre
	 * @param y
	 *            y coordinate of centre
	 * @param r
	 *            radius of circle
	 * @param n
	 *            Number of coordinates
	 * @param noise
	 *            Add noise of intensity 'noise'
	 *
	 * @return
	 */
	public static double[][] getTestCircle(final double x, final double y, final double r, final int n,
			final double noise) {
		return getTestCircle(x, y, r, n, 0, 2 * Math.PI, noise);
	}

	/**
	 * Return the column in Matrix D that contains the nth smallest diagonal
	 * value
	 *
	 * @param D
	 *            the matrix to search
	 * @param n
	 *            the order of the diagonal value to find (1 = smallest, 2 =
	 *            second smallest, etc.)
	 * @return column index of the nth smallest diagonal in D
	 */
	private static int getNthSmallestCol(final Matrix D, final int n) {
		final double[] diagD = new double[D.getColumnDimension()];
		final int[] index = new int[D.getColumnDimension()];
		for (int i = 0; i < D.getColumnDimension(); i++) {
			diagD[i] = D.get(i, i);
			index[i] = i;
		}

		for (int a = diagD.length - 1; a >= 0; a--) {
			double currentMax = diagD[0];
			int maxIndex = 0;
			int maxValue = index[0];
			for (int b = 1; b <= a; b++) {
				if (currentMax > diagD[b]) {
					currentMax = diagD[b];
					maxIndex = b;
					maxValue = index[b];
				}
			}
			if (maxIndex != a) {
				diagD[maxIndex] = diagD[a];
				diagD[a] = currentMax;
				index[maxIndex] = index[a];
				index[a] = maxValue;
			}
		}

		if (diagD[diagD.length - 1] > 0) {
			System.out.println("Error: the smallest e-value is positive...");
		}
		if (diagD[diagD.length - 2] < 0) {
			System.out.println("Error: the second smallest e-value is negative...");
		}
		final int col = index[index.length - n];
		return col;
	}
}
