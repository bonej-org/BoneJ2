/*-
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.geometry;

import java.util.Random;

import org.bonej.util.MatrixUtils;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

/**
 * Ellipsoid fitting methods. Both rely on eigenvalue decomposition, which fails
 * if the input matrix is singular. It is worth enclosing calls to yuryPetrov
 * and liGriffiths in a try{FitEllipsoid.yuryPetrov} catch(RuntimeException
 * re){} to gracefully handle cases where the methods cannot find a best-fit
 * ellipsoid.
 *
 * @author Michael Doube
 */
public final class FitEllipsoid {

	/** Random number generator*/
	private static Random rng = new Random();

	/** Constructor */
	private FitEllipsoid() {}

	/**
	 * Return points on an ellipsoid with optional noise. Point density is not
	 * uniform, becoming more dense at the poles.
	 *
	 * @param a First axis length
	 * @param b Second axis length
	 * @param c Third axis length
	 * @param angle angle of axis (rad)
	 * @param xCentre x coordinate of centre
	 * @param yCentre y coordinate of centre
	 * @param zCentre z coordinate of centre
	 * @param noise Intensity of noise to add to the points
	 * @param nPoints number of points to generate
	 * @param random if true, use a random grid to generate points, else use a
	 *          regular grid
	 * @return array of (x,y,z) coordinates
	 */
	public static double[][] testEllipsoid(final double a, final double b,
		final double c, final double angle, final double xCentre,
		final double yCentre, final double zCentre, final double noise,
		final int nPoints, final boolean random)
	{

		final int n = (int) Math.floor(-0.75 + Math.sqrt(1.0 + 8.0 * nPoints) /
			4.0);
		final int h = 2 * n + 2;
		final int w = n + 1;
		final double[][] s = new double[h][w];
		final double[][] t = new double[h][w];
		final double theta = -Math.PI / 2.0;
		if (random) {
			// Random points
			for (int j = 0; j < w; j++) {
				for (int i = 0; i < h; i++) {
					s[i][j] = theta + rng.nextDouble() * 2 * Math.PI;
				}
			}
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					t[i][j] = theta + rng.nextDouble() * 2 * Math.PI;
				}
			}
		}
		else {
			// Regular points
			final double increment = Math.PI / (n - 1);
			for (int j = 0; j < w; j++) {
				final double alpha = theta + j * increment;
				for (int i = 0; i < h; i++) {
					s[i][j] = alpha;
				}
			}
			for (int i = 0; i < h; i++) {
				final double alpha = theta + i * increment;
				for (int j = 0; j < w; j++) {
					t[i][j] = alpha;
				}
			}

		}
		final double[][] x = new double[h][w];
		final double[][] y = new double[h][w];
		final double[][] z = new double[h][w];
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				x[i][j] = a * Math.cos(s[i][j]) * Math.cos(t[i][j]);
				y[i][j] = b * Math.cos(s[i][j]) * Math.sin(t[i][j]);
				z[i][j] = c * Math.sin(s[i][j]);
			}
		}
		final double[][] xt = new double[h][w];
		final double[][] yt = new double[h][w];
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				xt[i][j] = x[i][j] * Math.cos(angle) - y[i][j] * Math.sin(angle);
				yt[i][j] = x[i][j] * Math.sin(angle) + y[i][j] * Math.cos(angle);
			}
		}
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				x[i][j] = xt[i][j] + xCentre;
				y[i][j] = yt[i][j] + yCentre;
				z[i][j] = z[i][j] + zCentre;
			}
		}
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				x[i][j] = x[i][j] + rng.nextDouble() * noise;
				y[i][j] = y[i][j] + rng.nextDouble() * noise;
				z[i][j] = z[i][j] + rng.nextDouble() * noise;
			}
		}
		final double[][] ellipsoidPoints = new double[w * h][3];
		int p = 0;
		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				ellipsoidPoints[p][0] = x[i][j];
				ellipsoidPoints[p][1] = y[i][j];
				ellipsoidPoints[p][2] = z[i][j];
				p++;
			}
		}
		return ellipsoidPoints;
	}

	/**
	 * Ellipsoid fitting method by Yury Petrov.
	 * <p>
	 * Fits an ellipsoid in the form <i>Ax</i><sup>2</sup> + <i>By</i>
	 * <sup>2</sup> + <i>Cz</i><sup>2</sup> + 2<i>Dxy</i> + 2<i>Exz</i> + 2
	 * <i>Fyz</i> + 2<i>Gx</i> + 2<i>Hy</i> + 2<i>Iz</i> = 1 To an n * 3 array of
	 * coordinates.
	 * </p>
	 * <p>
	 * See <a href=
	 * "https://www.mathworks.com/matlabcentral/fileexchange/24693-ellipsoid-fit"
	 * >MATLAB script</a>
	 * </p>
	 * 
	 * @param points array[n][3] where n &gt; 8
	 * @return Object[] array containing the centre, radii, eigenvectors of the
	 *         axes, the 9 variables of the ellipsoid equation and the EVD
	 * @throws IllegalArgumentException if number of coordinates is less than 9 or
	 * if ellipsoid matrix is not positive definite (i.e. the fitted function is
	 * one of the other quadrics).
	 */
	public static Object[] yuryPetrov(final double[][] points) {

		final int nPoints = points.length;
		if (nPoints < 9) {
			throw new IllegalArgumentException(
				"Too few points; need at least 9 to calculate a unique ellipsoid");
		}

		final double[][] d = new double[nPoints][9];
		for (int i = 0; i < nPoints; i++) {
			final double x = points[i][0];
			final double y = points[i][1];
			final double z = points[i][2];
			d[i][0] = x * x;
			d[i][1] = y * y;
			d[i][2] = z * z;
			d[i][3] = 2 * x * y;
			d[i][4] = 2 * x * z;
			d[i][5] = 2 * y * z;
			d[i][6] = 2 * x;
			d[i][7] = 2 * y;
			d[i][8] = 2 * z;
		}

		// do the fitting
		final Matrix D = new Matrix(d);
		final Matrix ones = new Matrix(nPoints, 1, 1.0);
		final Matrix V = ((D.transpose().times(D)).inverse()).times(D.transpose()
			.times(ones));

		// the fitted equation
		final double[] v = V.getColumnPackedCopy();

		final Object[] matrices = matrixFromEquation(v[0], v[1], v[2],
			v[3], v[4], v[5], v[6], v[7], v[8]);

		// pack data up for returning
		final EigenvalueDecomposition E = (EigenvalueDecomposition) matrices[3];
		final Matrix eVal = E.getD();
		final Matrix diagonal = MatrixUtils.diag(eVal);
		final int nEvals = diagonal.getRowDimension();
		final double[] radii = new double[nEvals];
		for (int i = 0; i < nEvals; i++) {
			final double eigenvalue = diagonal.get(i, 0);
			if (eigenvalue <= 0) {
				throw new IllegalArgumentException("Ellipsoid eigenvalues must be greater than 0 "
						+ "(positive definite matrix)");
			}
			radii[i] = Math.sqrt(1 / eigenvalue);
		}
		final double[] centre = (double[]) matrices[0];
		final double[][] eigenVectors = (double[][]) matrices[2];
		return new Object[] { centre, radii, eigenVectors, v, E };
	}

	/**
	 * Sets the seed number of the pseudo-random number generator in
	 * testEllipsoid.
	 *
	 * @param seed seed number.
	 */
	static void setSeed(final long seed) {
		rng.setSeed(seed);
	}

	/**
	 * Calculate the matrix representation of the ellipsoid (centre, eigenvalues,
	 * eigenvectors) from the equation <i>ax</i> <sup>2</sup> +
	 * <i>by</i><sup>2</sup> + <i>cz</i><sup>2</sup> + 2 <i>dxy</i> + 2<i>exz</i>
	 * + 2<i>fyz</i> + 2<i>gx</i> + 2<i>hy</i> + 2 <i>iz</i> = 1
	 *
	 * @param a coefficient of <em>x<sup>2</sup></em>
	 * @param b coefficient of <em>y<sup>2</sup></em>
	 * @param c coefficient of <em>z<sup>2</sup></em>.
	 * @param d coefficient of <em>x</em><em>y</em>.
	 * @param e coefficient of <em>x</em><em>z</em>.
	 * @param f coefficient of <em>y</em><em>z</em>.
	 * @param g coefficient of 2<em>x</em>.
	 * @param h coefficient of 2<em>y</em>.
	 * @param i coefficient of 2<em>z</em>.
	 * @return Object[] array containing centre (double[3]), eigenvalues
	 *         (double[3][3]), eigenvectors (double[3][3]), and the
	 *         EigenvalueDecomposition
	 */
	private static Object[] matrixFromEquation(final double a, final double b,
		final double c, final double d, final double e, final double f,
		final double g, final double h, final double i)
	{

		// the fitted equation
		final double[][] v = { { a }, { b }, { c }, { d }, { e }, { f }, { g }, {
			h }, { i } };
		final Matrix V = new Matrix(v);

		// 4x4 based on equation variables
		final double[][] aa = { { a, d, e, g }, { d, b, f, h }, { e, f, c, i }, { g,
			h, i, -1 }, };
		final Matrix A = new Matrix(aa);

		// find the centre
		final Matrix C = (A.getMatrix(0, 2, 0, 2).times(-1).inverse()).times(V
			.getMatrix(6, 8, 0, 0));

		// using the centre and 4x4 calculate the
		// eigendecomposition
		final Matrix T = Matrix.identity(4, 4);
		T.setMatrix(3, 3, 0, 2, C.transpose());
		final Matrix R = T.times(A.times(T.transpose()));
		final double r33 = R.get(3, 3);
		final Matrix R02 = R.getMatrix(0, 2, 0, 2);
		final EigenvalueDecomposition E = new EigenvalueDecomposition(R02.times(-1 /
			r33));

		final double[] centre = C.getColumnPackedCopy();
		final double[][] eigenVectors = E.getV().getArrayCopy();
		final double[][] eigenValues = E.getD().getArrayCopy();
		return new Object[] { centre, eigenValues, eigenVectors, E };
	}
}
