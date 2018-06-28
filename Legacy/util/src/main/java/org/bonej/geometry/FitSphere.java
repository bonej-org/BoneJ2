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

package org.bonej.geometry;

import Jama.Matrix;

/**
 * <p>
 * Find the best fitting sphere Ported from Angelo Tardugno's C++
 * </p>
 *
 * @author Michael Doube and Angelo Tardugno
 * @version 0.1
 */
public final class FitSphere {

	private FitSphere() {}

	/**
	 * Fit a sphere to 3D coordinates
	 *
	 * @param points double[n][3] containing n (x, y, z) coordinates
	 * @return double[4] containing (x, y, z) centre and radius
	 * @throws IllegalArgumentException if n &lt; 5
	 */
	public static double[] fitSphere(final double[][] points) {
		final int nPoints = points.length;
		if (nPoints < 5) {
			throw new IllegalArgumentException("Too few points to fit sphere; n = " +
				nPoints);
		}
		final double[] centroid = Centroid.getCentroid(points);

		double x = centroid[0];
		double y = centroid[1];
		double z = centroid[2];

		double g_new = 100.0;
		double g_old = 1.0;
		double r = 0;

		for (final double[] point : points) {
			r += Trig.distance3D(point, centroid);
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
		return new double[] { x, y, z, r };
	}
}
