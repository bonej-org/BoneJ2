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

import java.util.Arrays;

public final class Centroid {

	private Centroid() {}

	/**
	 * Find the centroid of an array in double[i][n] format, where i = number of
	 * points and n = number of dimensions
	 *
	 * @param points a set of points in N-dimensions.
	 * @return array containing centroid in N-dimensions
	 */
	static double[] getCentroid(final double[][] points) {
		if (Arrays.stream(points).mapToInt(p -> p.length).distinct().count() != 1) {
			throw new IllegalArgumentException(
				"Points must have the same dimensionality");
		}
		final int nDimensions = points[0].length;
		final double[] centroid = new double[nDimensions];
		for (final double[] point : points) {
			for (int d = 0; d < nDimensions; d++) {
				centroid[d] += point[d];
			}
		}
		for (int i = 0; i < centroid.length; i++) {
			centroid[i] /= points.length;
		}
		return centroid;
	}
}
