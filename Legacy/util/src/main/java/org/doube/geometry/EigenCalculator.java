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

public class EigenCalculator {

	/**
	 * Calculate the eigenvectors and eigenvalues of a set of points by the
	 * covariance method and eigendecomposition.
	 *
	 * @param coOrdinates
	 *            n x 3 array
	 * @return EigenvalueDecomposition containing eigenvectors and eigenvalues
	 *
	 */
	public static EigenvalueDecomposition principalComponents(final double[][] coOrdinates) {
		final int nPoints = coOrdinates.length;
		double sumX = 0, sumY = 0, sumZ = 0;
		// calculate the centroid of the points
		for (int n = 0; n < nPoints; n++) {
			sumX += coOrdinates[n][0];
			sumY += coOrdinates[n][1];
			sumZ += coOrdinates[n][2];
		}
		// centroid is the mean (x, y, z) position
		final double centX = sumX / nPoints;
		final double centY = sumY / nPoints;
		final double centZ = sumZ / nPoints;

		// construct the covariance matrix
		final double[][] C = new double[3][3];
		double count = 0;
		for (int n = 0; n < nPoints; n++) {
			// translate so that centroid is at (0,0,0)
			final double x = coOrdinates[n][0] - centX;
			final double y = coOrdinates[n][1] - centY;
			final double z = coOrdinates[n][2] - centZ;
			C[0][0] += x * x;
			C[1][1] += y * y;
			C[2][2] += z * z;
			final double xy = x * y;
			final double xz = x * z;
			final double yz = y * z;
			C[0][1] += xy;
			C[0][2] += xz;
			C[1][0] += xy;
			C[1][2] += yz;
			C[2][0] += xz;
			C[2][1] += yz;
			count += 1;
		}
		final double invCount = 1 / count;
		final Matrix covarianceMatrix = new Matrix(C).times(invCount);
		final EigenvalueDecomposition E = new EigenvalueDecomposition(covarianceMatrix);
		return E;
	}
}
