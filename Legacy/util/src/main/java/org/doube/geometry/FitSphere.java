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

import Jama.Matrix;

/**
 * <p>
 * Find the best fitting sphere
 *
 * Ported from Angelo Tardugno's C++
 * </p>
 *
 *
 * @author Michael Doube and Angelo Tardugno
 * @version 0.1
 */
public class FitSphere {

	/**
	 * Fit a sphere to 3D coordinates
	 *
	 * @param points
	 *            double[n][3] containing n (x, y, z) coordinates
	 * @return double[4] containing (x, y, z) centre and radius
	 * @throws IllegalArgumentException
	 *             if n < 5
	 */
	public static double[] fitSphere(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 5) {
			throw new IllegalArgumentException("Too few points to fit sphere; n = " + nPoints);
		}
		final double[] centroid = Centroid.getCentroid(points);

		double x = centroid[0];
		double y = centroid[1];
		double z = centroid[2];

		final double[] radii = new double[nPoints];
		double g_new = 100.0;
		double g_old = 1.0;
		double r = 0;

		for (int i = 0; i < nPoints; i++) {
			r += Trig.distance3D(points[i], centroid);
		}
		r /= nPoints;

		while (Math.abs(g_new - g_old) > 1e-10) {
			Matrix J = new Matrix(nPoints, 4);
			final double[][] Jp = J.getArray();
			Matrix D = new Matrix(nPoints, 1);
			final double[][] dp = D.getArray(); // dp is a pointer to d's values
			g_old = g_new;
			for (int i = 0; i < nPoints; i++) {
				final double pX = points[i][0] - x;
				final double pY = points[i][1] - y;
				final double pZ = points[i][2] - z;
				final double ri = Trig.distance3D(pX, pY, pZ);
				dp[i][0] = ri - r;
				Jp[i][0] = -pX / ri;
				Jp[i][1] = -pY / ri;
				Jp[i][2] = -pZ / ri;
				Jp[i][3] = -1;
				radii[i] = ri;
			}
			D = D.times(-1);
			final Matrix J1 = J;
			J = J.transpose();
			final Matrix J2 = J.times(J1);
			final Matrix Jd = J.times(D);
			final Matrix X = J2.inverse().times(Jd);
			final double[][] xp = X.getArray();
			x += xp[0][0];
			y += xp[1][0];
			z += xp[2][0];
			r += xp[3][0];
			D = D.times(-1);
			final Matrix G = J.times(D);
			final double[][] Gp = G.getArray();
			g_new = 0.0;
			for (int i = 0; i < 4; i++)
				g_new += Gp[i][0];
		}
		final double[] centreRadius = { x, y, z, r };
		return centreRadius;
	}
}
