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
 *
 */
public class FitCircle {

	/**
	 * Kåsa fit
	 *
	 * @param double[n][2]
	 *            containing n (<i>x</i>, <i>y</i>) coordinates
	 * @return double[] containing (<i>x</i>, <i>y</i>) centre and radius
	 *
	 */
	public static double[] kasaFit(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");
		final double[][] z = new double[nPoints][1];
		final double[][] xy1 = new double[nPoints][3];
		for (int n = 0; n < nPoints; n++) {
			z[n][0] = points[n][0] * points[n][0] + points[n][1] * points[n][1];
			xy1[n][0] = points[n][0];
			xy1[n][1] = points[n][1];
			xy1[n][2] = 1;
		}

		final Matrix XY1 = new Matrix(xy1);
		final Matrix Z = new Matrix(z);
		final Matrix P = (XY1.inverse()).times(Z); // no direct left divide in
													// Jama!
		final double[] centreRadius = new double[3];

		final double p0 = P.get(0, 0);
		final double p1 = P.get(1, 0);
		final double p2 = P.get(2, 0);
		centreRadius[0] = p0 / 2;
		centreRadius[1] = p1 / 2;
		centreRadius[2] = Math.sqrt((p0 * p0 + p1 * p1) / 4 + p2);
		return centreRadius;
	}

	/**
	 * Pratt method (Newton style)
	 *
	 * @param double[n][2]
	 *            containing n (<i>x</i>, <i>y</i>) coordinates
	 * @return double[] containing (<i>x</i>, <i>y</i>) centre and radius
	 *
	 */
	public static double[] prattNewton(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");
		final double[] centroid = Centroid.getCentroid(points);
		double Mxx = 0, Myy = 0, Mxy = 0, Mxz = 0, Myz = 0, Mzz = 0;

		for (int i = 0; i < nPoints; i++) {
			final double Xi = points[i][0] - centroid[0];
			final double Yi = points[i][1] - centroid[1];
			final double Zi = Xi * Xi + Yi * Yi;
			Mxy += Xi * Yi;
			Mxx += Xi * Xi;
			Myy += Yi * Yi;
			Mxz += Xi * Zi;
			Myz += Yi * Zi;
			Mzz += Zi * Zi;
		}
		Mxx /= nPoints;
		Myy /= nPoints;
		Mxy /= nPoints;
		Mxz /= nPoints;
		Myz /= nPoints;
		Mzz /= nPoints;

		final double Mz = Mxx + Myy;
		final double Cov_xy = Mxx * Myy - Mxy * Mxy;
		final double Mxz2 = Mxz * Mxz;
		final double Myz2 = Myz * Myz;

		final double A2 = 4 * Cov_xy - 3 * Mz * Mz - Mzz;
		final double A1 = Mzz * Mz + 4 * Cov_xy * Mz - Mxz2 - Myz2 - Mz * Mz * Mz;
		final double A0 = Mxz2 * Myy + Myz2 * Mxx - Mzz * Cov_xy - 2 * Mxz * Myz * Mxy + Mz * Mz * Cov_xy;
		final double A22 = A2 + A2;

		final double epsilon = 1e-12;
		double ynew = 1e+20;
		final int IterMax = 20;
		double xnew = 0;
		for (int iter = 0; iter <= IterMax; iter++) {
			final double yold = ynew;
			ynew = A0 + xnew * (A1 + xnew * (A2 + 4 * xnew * xnew));
			if (Math.abs(ynew) > Math.abs(yold)) {
				System.out.println("Newton-Pratt goes wrong direction: |ynew| > |yold|");
				xnew = 0;
				break;
			}
			final double Dy = A1 + xnew * (A22 + 16 * xnew * xnew);
			final double xold = xnew;
			xnew = xold - ynew / Dy;
			if (Math.abs((xnew - xold) / xnew) < epsilon) {
				break;
			}
			if (iter >= IterMax) {
				System.out.println("Newton-Pratt will not converge");
				xnew = 0;
			}
			if (xnew < 0) {
				System.out.println("Newton-Pratt negative root:  x= " + xnew);
				xnew = 0;
			}
		}
		final double det = xnew * xnew - xnew * Mz + Cov_xy;
		final double x = (Mxz * (Myy - xnew) - Myz * Mxy) / (det * 2);
		final double y = (Myz * (Mxx - xnew) - Mxz * Mxy) / (det * 2);
		final double r = Math.sqrt(x * x + y * y + Mz + 2 * xnew);

		final double[] centreRadius = { x + centroid[0], y + centroid[1], r };
		return centreRadius;
	}

	/**
	 * Pratt method (SVD style)
	 *
	 *
	 * @param double[n][2]
	 *            containing n (<i>x</i>, <i>y</i>) coordinates
	 * @return double[] containing (<i>x</i>, <i>y</i>) centre and radius
	 */
	public static double[] prattSVD(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");
		final double[] centroid = Centroid.getCentroid(points);
		final double[][] xyXY1 = new double[nPoints][4];
		for (int i = 0; i < nPoints; i++) {
			final double x = points[i][0] - centroid[0];
			final double y = points[i][1] - centroid[1];
			xyXY1[i][0] = x * x + y * y;
			xyXY1[i][1] = x;
			xyXY1[i][2] = y;
			xyXY1[i][3] = 1;
		}

		final Matrix XYXY1 = new Matrix(xyXY1);
		final SingularValueDecomposition svd = new SingularValueDecomposition(XYXY1);
		final Matrix S = svd.getS();
		final Matrix V = svd.getV();
		Matrix A;
		Matrix W;
		if (S.get(3, 3) / S.get(0, 0) < 1e-12) {
			A = V.getMatrix(0, V.getRowDimension() - 1, 3, 3);
			System.out.println("Pratt singular case");
		} else {
			W = V.times(S);
			final double[][] bInv = { { 0, 0, 0, -0.5 }, { 0, 1, 0, 0 }, { 0, 0, 1, 0 }, { -0.5, 0, 0, 0 } };
			final Matrix Binv = new Matrix(bInv);
			final EigenvalueDecomposition ed = new EigenvalueDecomposition((W.transpose()).times(Binv.times(W)));
			final Matrix D = ed.getD();
			final Matrix E = ed.getV();
			final int col = getNthSmallestCol(D, 2);
			A = E.getMatrix(0, E.getRowDimension() - 1, col, col);
			for (int i = 0; i < 4; i++) {
				S.set(i, i, 1 / S.get(i, i));
			}
			A = V.times(S.times(A));
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
	 * Taubin method (Newton Style)
	 *
	 * @param double[n][2]
	 *            containing n (<i>x</i>, <i>y</i>) coordinates
	 * @return double[] containing (<i>x</i>, <i>y</i>) centre and radius
	 */
	public static double[] taubinNewton(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");
		final double[] centroid = Centroid.getCentroid(points);
		double Mxx = 0, Myy = 0, Mxy = 0, Mxz = 0, Myz = 0, Mzz = 0;
		for (int i = 0; i < nPoints; i++) {
			final double Xi = points[i][0] - centroid[0];
			final double Yi = points[i][1] - centroid[1];
			final double Zi = Xi * Xi + Yi * Yi;
			Mxy += Xi * Yi;
			Mxx += Xi * Xi;
			Myy += Yi * Yi;
			Mxz += Xi * Zi;
			Myz += Yi * Zi;
			Mzz += Zi * Zi;

		}
		Mxx /= nPoints;
		Myy /= nPoints;
		Mxy /= nPoints;
		Mxz /= nPoints;
		Myz /= nPoints;
		Mzz /= nPoints;

		final double Mz = Mxx + Myy;
		final double Cov_xy = Mxx * Myy - Mxy * Mxy;
		final double A3 = 4 * Mz;
		final double A2 = -3 * Mz * Mz - Mzz;
		final double A1 = Mzz * Mz + 4 * Cov_xy * Mz - Mxz * Mxz - Myz * Myz - Mz * Mz * Mz;
		final double A0 = Mxz * Mxz * Myy + Myz * Myz * Mxx - Mzz * Cov_xy - 2 * Mxz * Myz * Mxy + Mz * Mz * Cov_xy;
		final double A22 = A2 + A2;
		final double A33 = A3 + A3 + A3;

		double xnew = 0;
		double ynew = 1e+20;
		final double epsilon = 1e-12;
		final double iterMax = 20;

		for (int iter = 0; iter < iterMax; iter++) {
			final double yold = ynew;
			ynew = A0 + xnew * (A1 + xnew * (A2 + xnew * A3));
			if (Math.abs(ynew) > Math.abs(yold)) {
				System.out.println("Newton-Taubin goes wrong direction: |ynew| > |yold|");
				xnew = 0;
				break;
			}
			final double Dy = A1 + xnew * (A22 + xnew * A33);
			final double xold = xnew;
			xnew = xold - ynew / Dy;
			if (Math.abs((xnew - xold) / xnew) < epsilon) {
				break;
			}
			if (iter >= iterMax) {
				System.out.println("Newton-Taubin will not converge");
				xnew = 0;
			}
			if (xnew < 0.) {
				System.out.println("Newton-Taubin negative root: x = " + xnew);
				xnew = 0;
			}
		}
		final double[] centreRadius = new double[3];
		final double det = xnew * xnew - xnew * Mz + Cov_xy;
		final double x = (Mxz * (Myy - xnew) - Myz * Mxy) / (det * 2);
		final double y = (Myz * (Mxx - xnew) - Mxz * Mxy) / (det * 2);
		centreRadius[0] = x + centroid[0];
		centreRadius[1] = y + centroid[1];
		centreRadius[2] = Math.sqrt(x * x + y * y + Mz);

		return centreRadius;
	}

	/**
	 * Taubin method, SVD version
	 *
	 * @param double[n][2]
	 *            containing n (<i>x</i>, <i>y</i>) coordinates
	 * @return double[] containing (<i>x</i>, <i>y</i>) centre and radius
	 */
	public static double[] taubinSVD(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");
		final double[] centroid = Centroid.getCentroid(points);

		final double[][] zxy = new double[nPoints][3];
		final double[] z = new double[nPoints];
		double sumZ = 0;
		for (int n = 0; n < nPoints; n++) {
			final double x = points[n][0] - centroid[0];
			final double y = points[n][1] - centroid[1];
			zxy[n][1] = x;
			zxy[n][2] = y;
			z[n] = x * x + y * y;
			sumZ += z[n];
		}
		final double meanZ = sumZ / nPoints;
		final double sqrtMeanZ2 = 2 * Math.sqrt(meanZ);
		for (int n = 0; n < nPoints; n++) {
			zxy[n][0] = (z[n] - meanZ) / sqrtMeanZ2;
		}

		final Matrix ZXY = new Matrix(zxy);
		final SingularValueDecomposition svd = new SingularValueDecomposition(ZXY);
		final Matrix V = svd.getV();
		final Matrix A = V.getMatrix(0, V.getRowDimension() - 1, 2, 2);
		final double a1 = A.get(0, 0) / sqrtMeanZ2;
		A.set(0, 0, a1);
		final double[][] a = A.getArray();
		final double[] b = new double[a.length + 1];
		for (int i = 0; i < a.length; i++) {
			b[i] = a[i][0];
		}
		b[b.length - 1] = -meanZ * a1;
		final double[] centreRadius = new double[3];

		centreRadius[0] = -b[1] / (a1 * 2) + centroid[0];
		centreRadius[1] = -b[2] / (a1 * 2) + centroid[1];
		centreRadius[2] = Math.sqrt(b[1] * b[1] + b[2] * b[2] - 4 * b[0] * b[3]) / (Math.abs(b[0]) * 2);
		return centreRadius;
	}

	/**
	 * Chernov's non-biased Hyper algebraic method. Simple version.
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
	public static double[] hyperSimple(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");

		final double[][] zxy1 = new double[nPoints][4];
		double xSum = 0, ySum = 0, zSum = 0;
		for (int n = 0; n < nPoints; n++) {
			final double x = points[n][0];
			final double y = points[n][1];
			final double z = x * x + y * y;
			zxy1[n][0] = z;
			zxy1[n][1] = x;
			zxy1[n][2] = y;
			zxy1[n][3] = 1;

			xSum += x;
			ySum += y;
			zSum += z;

		}
		final double s0 = zSum / nPoints;
		final double s1 = xSum / nPoints;
		final double s2 = ySum / nPoints;

		final Matrix ZXY1 = new Matrix(zxy1);
		final Matrix M = (ZXY1.transpose()).times(ZXY1);

		final double[][] n = { { 8 * s0, 4 * s1, 4 * s2, 2 }, { 4 * s1, 1, 0, 0 }, { 4 * s2, 0, 1, 0 },
				{ 2, 0, 0, 0 } };
		final Matrix N = new Matrix(n);
		final Matrix NM = (N.inverse()).times(M);

		final EigenvalueDecomposition ED = new EigenvalueDecomposition(NM);
		final Matrix E = ED.getV();
		final Matrix D = ED.getD();

		final int col = getNthSmallestCol(D, 2);

		final Matrix A = E.getMatrix(0, E.getRowDimension() - 1, col, col);

		final double[] centreRadius = new double[3];

		centreRadius[0] = -1 * (A.get(1, 0) / A.get(0, 0)) / 2;
		centreRadius[1] = -1 * (A.get(2, 0) / A.get(0, 0)) / 2;

		final double[][] a = A.getArray();
		centreRadius[2] = Math.sqrt(a[1][0] * a[1][0] + a[2][0] * a[2][0] - 4 * a[0][0] * a[3][0]) / Math.abs(a[0][0])
				/ 2;

		return centreRadius;
	}

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
	 * Levenberg-Marquardt fit in the "full" (a,b,R) space
	 *
	 * @param points
	 *            double[n][2] containing n (<i>x</i>, <i>y</i>) coordinates
	 * @return 3-element double[] containing (<i>x</i>, <i>y</i>) centre and
	 *         circle radius
	 */
	public static double[] levenMarqFull(final double[][] points, final double lambdaIni) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");
		final double[] guess = hyperStable(points);
		double x = guess[0];
		double y = guess[1];
		double r = guess[2];

		final double[][] par = { { x, y, r } };
		Matrix Par = new Matrix(par);
		Matrix ParTemp = new Matrix(par);
		final double epsilon = 1e-6;
		double progress = epsilon;
		final int iterMax = 50;
		double lambda_sqrt = Math.sqrt(lambdaIni);

		double f = 0;
		double[][] j = new double[nPoints + 3][3];
		double[][] g = new double[nPoints + 3][1];
		for (int i = 0; i < nPoints; i++) {
			final double dX = points[i][0] - x;
			final double dY = points[i][1] - y;
			final double d = Math.sqrt(dX * dX + dY * dY);
			j[i][0] = -dX / d;
			j[i][1] = -dY / d;
			j[i][2] = -1;
			g[i][0] = d - r;
			f += (d - r) * (d - r);
		}

		final Matrix J = new Matrix(j);
		final Matrix G = new Matrix(g);

		double fTemp = 0;
		final double[][] jTemp = new double[nPoints + 3][3];
		final double[][] gTemp = new double[nPoints + 3][1];

		for (int iter = 0; iter < iterMax; iter++) {
			int safety = 0;
			while (safety < 100) {
				safety++;
				J.set(nPoints, 0, lambda_sqrt);
				J.set(nPoints + 1, 1, lambda_sqrt);
				J.set(nPoints + 2, 2, lambda_sqrt);
				G.set(nPoints, 0, 0);
				G.set(nPoints + 1, 0, 0);
				G.set(nPoints + 2, 0, 0);
				final Matrix DelPar = (J.inverse()).times(G);
				progress = DelPar.normF() / (Par.normF() + epsilon);
				if (progress < epsilon) {
					break;
				}
				ParTemp = Par.minus(DelPar.transpose());
				x = ParTemp.get(0, 0);
				y = ParTemp.get(0, 1);
				r = ParTemp.get(0, 2);
				for (int i = 0; i < nPoints; i++) {
					final double dX = points[i][0] - x;
					final double dY = points[i][1] - y;
					final double d = Math.sqrt(dX * dX + dY * dY);
					jTemp[i][0] = -dX / d;
					jTemp[i][1] = -dY / d;
					jTemp[i][2] = -1;
					gTemp[i][0] = d - r;
					fTemp += (d - r) * (d - r);
				}
				if (fTemp < f && ParTemp.get(0, 2) > 0) {
					lambda_sqrt /= 2;
					break;
				}
				lambda_sqrt *= 2;
				continue;
			}
			if (progress < epsilon) {
				break;
			}
			Par = ParTemp;
			j = jTemp;
			g = gTemp;
			f = fTemp;
		}
		final double[] centreRadius = { Par.get(0, 0), Par.get(0, 1), Par.get(0, 2) };
		return centreRadius;
	}

	/**
	 * If initial value of Lambda is not supplied, it defaults to 1
	 *
	 * @param points
	 * @return
	 */
	public static double[] levenMarqFull(final double[][] points) {
		return levenMarqFull(points, 1);
	}

	/**
	 * Levenberg-Marquardt fit in the "reduced" (a,b) space
	 *
	 * @param points
	 *            double[n][2] containing n (<i>x</i>, <i>y</i>) coordinates
	 * @return 3-element double[] containing (<i>x</i>, <i>y</i>) centre and
	 *         circle radius
	 */
	public static double[] levenMarqRed(final double[][] points, final double lambdaIni) {
		final int nPoints = points.length;
		if (nPoints < 3)
			throw new IllegalArgumentException("Too few points");
		final double[] guess = hyperStable(points);
		double x = guess[0];
		double y = guess[1];
		final double[][] par = { { x, y } };
		Matrix Par = new Matrix(par);
		Matrix ParTemp = new Matrix(par);
		final double epsilon = 1e-6;
		double progress = epsilon;
		final int iterMax = 50;
		double lambda_sqrt = Math.sqrt(lambdaIni);

		double f = 0;
		double[][] j = new double[nPoints + 2][2];
		double[][] g = new double[nPoints + 2][1];
		double sumDx = 0;
		double sumDy = 0;
		double sumD = 0;
		final double[] dd = new double[nPoints];
		for (int i = 0; i < nPoints; i++) {
			double dX = points[i][0] - x;
			double dY = points[i][1] - y;
			final double d = Math.sqrt(dX * dX + dY * dY);
			dX /= d;
			dY /= d;
			dd[i] = d;
			sumDx += dX;
			sumDy += dY;
			sumD += d;
			j[i][0] = -dX;
			j[i][1] = -dY;
		}
		double meanDx = sumDx / nPoints;
		double meanDy = sumDy / nPoints;
		double r = sumD / nPoints;
		for (int i = 0; i < nPoints; i++) {
			j[i][0] += meanDx;
			j[i][1] += meanDy;
			final double d = dd[i];
			g[i][0] = d - r;
			f += (d - r) * (d - r);
		}

		final Matrix J = new Matrix(j);
		final Matrix G = new Matrix(g);

		double fTemp = 0;
		double rTemp = 0;
		final double[][] jTemp = new double[nPoints + 2][2];
		final double[][] gTemp = new double[nPoints + 2][1];

		for (int iter = 0; iter < iterMax; iter++) {
			int safety = 0;
			while (safety < 100) {
				safety++;
				J.set(nPoints, 0, lambda_sqrt);
				J.set(nPoints + 1, 1, lambda_sqrt);
				G.set(nPoints, 0, 0);
				G.set(nPoints + 1, 0, 0);
				final Matrix DelPar = (J.inverse()).times(G);
				progress = DelPar.normF() / (r + Par.normF() + epsilon);
				if (progress < epsilon) {
					break;
				}
				ParTemp = Par.minus(DelPar.transpose());
				x = ParTemp.get(0, 0);
				y = ParTemp.get(0, 1);
				sumDx = 0;
				sumDy = 0;
				sumD = 0;
				for (int i = 0; i < nPoints; i++) {
					double dX = points[i][0] - x;
					double dY = points[i][1] - y;
					final double d = Math.sqrt(dX * dX + dY * dY);
					dX /= d;
					dY /= d;
					dd[i] = d;
					sumDx += dX;
					sumDy += dY;
					sumD += d;
					jTemp[i][0] = -dX;
					jTemp[i][1] = -dY;
				}
				meanDx = sumDx / nPoints;
				meanDy = sumDy / nPoints;
				rTemp = sumD / nPoints;
				for (int i = 0; i < nPoints; i++) {
					jTemp[i][0] += meanDx;
					jTemp[i][1] += meanDy;
					final double d = dd[i];
					gTemp[i][0] = d - rTemp;
					fTemp += (d - rTemp) * (d - rTemp);
				}
				if (fTemp < f) {
					lambda_sqrt /= 2;
					break;
				}
				lambda_sqrt *= 2;
				continue;

			}
			if (progress < epsilon) {
				break;
			}
			Par = ParTemp;
			j = jTemp;
			g = gTemp;
			f = fTemp;
			r = rTemp;
		}
		final double[] centreRadius = { Par.get(0, 0), Par.get(0, 1), r };
		return centreRadius;
	}

	/**
	 * If initial value for lambda is not supplied, it defaults to 1.
	 *
	 * @param points
	 * @return
	 */
	public static double[] levenMarqRed(final double[][] points) {
		return levenMarqRed(points, 1);
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
	 * Calculate the mean squared errors between the fit circle and the
	 * coordinates
	 *
	 * @param points
	 * @param abR
	 * @return double[] containing mean squared errors in x, y, R and sum of (x,
	 *         y, R)
	 */
	public static double[] getErrors(final double[][] points, final double[] abR) {
		final int nPoints = points.length;

		final double a = abR[0];
		final double b = abR[1];
		final double R = abR[2];
		double sumX2 = 0;
		double sumY2 = 0;
		double sumR2 = 0;

		for (int i = 0; i < nPoints; i++) {
			final double x = points[i][0];
			final double y = points[i][1];
			final double r = Math.sqrt((x - a) * (x - a) + (y - b) * (y - b));
			final double theta = Math.atan2((y - b), (x - a));
			final double xt = R * Math.cos(theta) + a;
			final double yt = R * Math.sin(theta) + b;

			sumX2 += (x - xt) * (x - xt);
			sumY2 += (y - yt) * (y - yt);
			sumR2 += (R - r) * (R - r);
		}
		final double[] errors = new double[4];
		errors[0] = sumX2 / nPoints;
		errors[1] = sumY2 / nPoints;
		errors[2] = sumR2 / nPoints;
		errors[3] = errors[0] + errors[1] + errors[2];
		return errors;
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
