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

import org.doube.util.MatrixUtils;

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
	 * Calculate the best-fit ellipsoid by least squares. Currently broken;
	 * doesn't handle large numbers of points and often returns a singular
	 * matrix for EVD.
	 *
	 * @param coOrdinates
	 *            n x 3 array containing 3D coordinates
	 * @return 10 x 1 array containing constants for the ellipsoid equation
	 *         <i>ax</i><sup>2</sup> + <i>by</i><sup>2</sup> + <i>cz</i>
	 *         <sup>2</sup> + 2<i>fyz</i> + 2<i>gxz</i> + 2<i>hxy</i> + 2
	 *         <i>px</i> + 2<i>qy</i> + 2<i>rz</i> + <i>d</i> = 0
	 *
	 * @see
	 * 		<p>
	 *      Qingde Li, Griffiths J (2004) Least squares ellipsoid specific
	 *      fitting. Geometric Modeling and Processing, 2004. Proceedings. pp.
	 *      335-340.
	 *      <a href="http://dx.doi.org/10.1109/GMAP.2004.1290055">doi:10.1109
	 *      /GMAP.2004.1290055</a>
	 *      </p>
	 * @deprecated until debugged
	 */
	@Deprecated
	public static double[] liGriffiths(final double[][] coOrdinates, final int maxPoints) {
		final int nPoints = coOrdinates.length;
		final double[][] coOrd = new double[maxPoints][3];
		if (nPoints > maxPoints) {
			// randomly subsample
			for (int n = 0; n < maxPoints; n++) {
				final int point = (int) Math.floor(Math.random() * nPoints);
				coOrd[n][0] = coOrdinates[point][0];
			}
		}
		final double[][] Darray = new double[maxPoints][10];
		for (int n = 0; n < maxPoints; n++) {
			// populate each column of D with ten-element Xi
			final double xi = coOrd[n][0];
			final double yi = coOrd[n][1];
			final double zi = coOrd[n][2];
			Darray[n][0] = xi * xi;
			Darray[n][1] = yi * yi;
			Darray[n][2] = zi * zi;
			Darray[n][3] = yi * zi * 2;
			Darray[n][4] = xi * zi * 2;
			Darray[n][5] = xi * yi * 2;
			Darray[n][6] = xi * 2;
			Darray[n][7] = yi * 2;
			Darray[n][8] = zi * 2;
			Darray[n][9] = 1;
		}
		final Matrix D = new Matrix(Darray);
		final Matrix S = D.times(D.transpose());
		// Define 6x6 Matrix C1 (Eq. 7)
		final double[][] C1array = { { -1, 1, 1, 0, 0, 0 }, { 1, -1, 1, 0, 0, 0 }, { 1, 1, -1, 0, 0, 0 },
				{ 0, 0, 0, -4, 0, 0 }, { 0, 0, 0, 0, -4, 0 }, { 0, 0, 0, 0, 0, -4 } };
		final Matrix C1 = new Matrix(C1array);
		// Define S11, S12, S22 from S (Eq. 11)
		final Matrix S11 = S.getMatrix(0, 5, 0, 5);
		final Matrix S12 = S.getMatrix(0, 5, 6, 9);
		final Matrix S22 = S.getMatrix(6, 9, 6, 9);
		// Eq. 15
		final Matrix C1inv = C1.inverse();
		final Matrix S12T = S12.transpose();
		final Matrix S22inv = S22.inverse();
		final Matrix Eq15 = C1inv.times(S11.minus(S12.times(S22inv.times(S12T))));
		// However, the matrix (S11 - S12*S22^-1*S12^T ) can be singular in some
		// cases. In this situations, the corresponding u1 can be replaced
		// with the eigenvector associated with the largest eigenvalue.

		final EigenvalueDecomposition E = new EigenvalueDecomposition(Eq15);
		final Matrix eigenValues = E.getD();
		MatrixUtils.printToIJLog(eigenValues, "eigenValues");
		final Matrix eigenVectors = E.getV();
		MatrixUtils.printToIJLog(eigenVectors, "eigenVectors");
		Matrix v1 = new Matrix(6, 1);
		final double[][] EigenValueMatrix = eigenValues.getArray();
		double posVal = 1 - Double.MAX_VALUE;
		final int evl = EigenValueMatrix.length;
		for (int p = 0; p < evl; p++) {
			if (EigenValueMatrix[p][p] > posVal) {
				posVal = EigenValueMatrix[p][p];
				v1 = eigenVectors.getMatrix(0, 5, p, p);
			}
		}
		MatrixUtils.printToIJLog(v1, "v1");
		final Matrix v2 = S22.inverse().times(S12.transpose()).times(v1).times(-1);
		MatrixUtils.printToIJLog(v2, "v2");
		final Matrix v = new Matrix(10, 1);
		final int[] c = { 0 };
		v.setMatrix(0, 5, c, v1);
		v.setMatrix(6, 9, c, v2);
		final double[] ellipsoid = v.getColumnPackedCopy();
		return ellipsoid;
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
	 * Estimate an ellipsoid using the inertia tensor of the input points
	 *
	 * @param points
	 * @return
	 */
	public static Ellipsoid inertia(final double[][] points) {

		final int nPoints = points.length;

		// calculate centroid
		double sx = 0;
		double sy = 0;
		double sz = 0;
		for (final double[] p : points) {
			sx += p[0];
			sy += p[1];
			sz += p[2];
		}
		final double cx = sx / nPoints;
		final double cy = sy / nPoints;
		final double cz = sz / nPoints;

		double Icxx = 0;
		double Icyy = 0;
		double Iczz = 0;
		double Icxy = 0;
		double Icxz = 0;
		double Icyz = 0;

		// sum moments
		for (final double[] p : points) {
			final double x = p[0] - cx;
			final double y = p[1] - cy;
			final double z = p[2] - cz;
			final double xx = x * x;
			final double yy = y * y;
			final double zz = z * z;
			Icxx += yy + zz;
			Icyy += xx + zz;
			Iczz += xx + yy;
			Icxy += x * y;
			Icxz += x * z;
			Icyz += y * z;
		}
		// create the inertia tensor matrix
		final double[][] inertiaTensor = new double[3][3];
		inertiaTensor[0][0] = Icxx / nPoints;
		inertiaTensor[1][1] = Icyy / nPoints;
		inertiaTensor[2][2] = Iczz / nPoints;
		inertiaTensor[0][1] = -Icxy / nPoints;
		inertiaTensor[0][2] = -Icxz / nPoints;
		inertiaTensor[1][0] = -Icxy / nPoints;
		inertiaTensor[1][2] = -Icyz / nPoints;
		inertiaTensor[2][0] = -Icxz / nPoints;
		inertiaTensor[2][1] = -Icyz / nPoints;
		final Matrix inertiaTensorMatrix = new Matrix(inertiaTensor);
		MatrixUtils.printToIJLog(inertiaTensorMatrix, "Inertia tensor");

		// do the Eigenvalue decomposition
		final EigenvalueDecomposition E = new EigenvalueDecomposition(inertiaTensorMatrix);

		MatrixUtils.printToIJLog(E.getD(), "Eigenvalues");
		MatrixUtils.printToIJLog(E.getV(), "Eigenvectors");
		final double I1 = E.getD().get(2, 2);
		final double I2 = E.getD().get(1, 1);
		final double I3 = E.getD().get(0, 0);

		final Ellipsoid ellipsoid = new Ellipsoid(1 / Math.sqrt(I1), 1 / Math.sqrt(I2), 1 / Math.sqrt(I3), cx, cy, cz,
				E.getV().getArray());

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

	/**
	 * Return points on an ellipsoid with radii a, b, c and centred on (0,0,0),
	 * axes aligned with Cartesian axes
	 *
	 * @param a
	 * @param b
	 * @param c
	 * @param nPoints
	 * @return
	 */
	public static double[][] testEllipsoid(final double a, final double b, final double c, final int nPoints) {
		return testEllipsoid(a, b, c, 0, 0, 0, 0, 0, nPoints, false);
	}

	/**
	 * Return normal unit vectors at points on an ellipsoid given the radii of
	 * an ellipsoid and points on the ellipsoid. Assumes an ellipsoid centred on
	 * (0,0,0) and with no rotation
	 *
	 * @param a
	 *            First axis length
	 * @param b
	 *            Second axis length
	 * @param c
	 *            Third axis length
	 * @param points
	 *            points on the ellipsoid, e.g. result of testElipsoid()
	 * @return array of (x,y,z) unit vectors
	 */
	public static double[][] testNormals(final double a, final double b, final double c, final double[][] points) {
		final int nPoints = points.length;
		final double[][] ellipsoidNormals = new double[nPoints][3];
		final double p = 2 / (a * a);
		final double q = 2 / (b * b);
		final double r = 2 / (c * c);
		for (int i = 0; i < nPoints; i++) {
			ellipsoidNormals[i][0] = p * points[i][0];
			ellipsoidNormals[i][1] = q * points[i][1];
			ellipsoidNormals[i][2] = r * points[i][2];
		}

		for (int i = 0; i < nPoints; i++) {
			final double x = ellipsoidNormals[i][0];
			final double y = ellipsoidNormals[i][1];
			final double z = ellipsoidNormals[i][2];
			final double length = Trig.distance3D(x, y, z);
			ellipsoidNormals[i][0] /= length;
			ellipsoidNormals[i][1] /= length;
			ellipsoidNormals[i][2] /= length;
		}

		return ellipsoidNormals;
	}
}
