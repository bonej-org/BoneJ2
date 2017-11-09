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
 *
 */
public class FitEllipsoid {

	/**
	 * Find the best-fit ellipsoid using the default method (yuryPetrov)
	 *
	 * @param coordinates
	 *            in double[n][3] format
	 * @return Object representing the best-fit ellipsoid
	 */
	public static Ellipsoid fitTo(final double[][] coordinates) {
		return new Ellipsoid(yuryPetrov(coordinates));
	}

	/**
	 * <p>
	 * Ellipsoid fitting method by Yury Petrov.<br />
	 * Fits an ellipsoid in the form <i>Ax</i><sup>2</sup> + <i>By</i>
	 * <sup>2</sup> + <i>Cz</i><sup>2</sup> + 2<i>Dxy</i> + 2<i>Exz</i> + 2
	 * <i>Fyz</i> + 2<i>Gx</i> + 2<i>Hy</i> + 2<i>Iz</i> = 1 <br />
	 * To an n * 3 array of coordinates.
	 * </p>
	 *
	 * @see
	 * 		<p>
	 *      <a href=
	 *      "http://www.mathworks.com/matlabcentral/fileexchange/24693-ellipsoid-fit"
	 *      >MATLAB script</a>
	 *      </p>
	 *
	 * @param coOrdinates
	 *            array[n][3] where n > 8
	 * @return Object[] array containing the centre, radii, eigenvectors of the
	 *         axes, the 9 variables of the ellipsoid equation and the EVD
	 * @throws IllegalArgumentException
	 *             if number of coordinates is less than 9
	 */
	public static Object[] yuryPetrov(final double[][] points) {

		final int nPoints = points.length;
		if (nPoints < 9) {
			throw new IllegalArgumentException("Too few points; need at least 9 to calculate a unique ellipsoid");
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
		final Matrix ones = MatrixUtils.ones(nPoints, 1);
		final Matrix V = ((D.transpose().times(D)).inverse()).times(D.transpose().times(ones));

		// the fitted equation
		final double[] v = V.getColumnPackedCopy();

		final Object[] matrices = Ellipsoid.matrixFromEquation(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);

		// pack data up for returning
		final EigenvalueDecomposition E = (EigenvalueDecomposition) matrices[3];
		final Matrix eVal = E.getD();
		final Matrix diagonal = MatrixUtils.diag(eVal);
		final int nEvals = diagonal.getRowDimension();
		final double[] radii = new double[nEvals];
		for (int i = 0; i < nEvals; i++) {
			radii[i] = Math.sqrt(1 / diagonal.get(i, 0));
		}
		final double[] centre = (double[]) matrices[0];
		final double[][] eigenVectors = (double[][]) matrices[2];
		final double[] equation = v;
		final Object[] ellipsoid = { centre, radii, eigenVectors, equation, E };
		return ellipsoid;
	}

	/**
	 * Return points on an ellipsoid with optional noise. Point density is not
	 * uniform, becoming more dense at the poles.
	 *
	 * @param a
	 *            First axis length
	 * @param b
	 *            Second axis length
	 * @param c
	 *            Third axis length
	 * @param angle
	 *            angle of axis (rad)
	 * @param xCentre
	 *            x coordinate of centre
	 * @param yCentre
	 *            y coordinate of centre
	 * @param zCentre
	 *            z coordinate of centre
	 * @param noise
	 *            Intensity of noise to add to the points
	 * @param nPoints
	 *            number of points to generate
	 * @param random
	 *            if true, use a random grid to generate points, else use a
	 *            regular grid
	 * @return array of (x,y,z) coordinates
	 */
	public static double[][] testEllipsoid(final double a, final double b, final double c, final double angle,
			final double xCentre, final double yCentre, final double zCentre, final double noise, final int nPoints,
			final boolean random) {

		final int n = (int) Math.floor(-3 / 4 + Math.sqrt(1 + 8 * nPoints) / 4);
		final int h = 2 * n + 2;
		final int w = n + 1;
		final double[][] s = new double[h][w];
		final double[][] t = new double[h][w];
		double value = -Math.PI / 2;
		// Random points
		if (random) {
			for (int j = 0; j < w; j++) {
				for (int i = 0; i < h; i++) {
					s[i][j] = value + Math.random() * 2 * Math.PI;
				}
			}
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					t[i][j] = value + Math.random() * 2 * Math.PI;
				}
			}
			// Regular points
		} else {
			final double increment = Math.PI / (n - 1);

			for (int j = 0; j < w; j++) {
				for (int i = 0; i < h; i++) {
					s[i][j] = value;
				}
				value += increment;
			}
			value = -Math.PI / 2;
			for (int i = 0; i < h; i++) {
				for (int j = 0; j < w; j++) {
					t[i][j] = value;
				}
				value += increment;
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
				x[i][j] = x[i][j] + Math.random() * noise;
				y[i][j] = y[i][j] + Math.random() * noise;
				z[i][j] = z[i][j] + Math.random() * noise;
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

}
