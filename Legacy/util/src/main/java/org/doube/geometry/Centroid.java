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

import java.util.ArrayList;

import sc.fiji.analyzeSkeleton.Point;

public class Centroid {

	/**
	 * Find the centroid of an array in double[n][i] format, where n = number of
	 * points and i = number of dimensions
	 *
	 * @param points
	 * @return array containing centroid in i dimensions
	 */
	public static double[] getCentroid(final double[][] points) {
		final int nDimensions = points[0].length;

		switch (nDimensions) {
		case 1:
			return getCentroid1D(points);
		case 2:
			return getCentroid2D(points);
		case 3:
			return getCentroid3D(points);
		default:
			return getCentroidND(points);
		}
	}

	/**
	 * Find the centroid of a set of points in double[n][1] format
	 *
	 * @param points
	 * @return
	 */
	private static double[] getCentroid1D(final double[][] points) {
		final double[] centroid = new double[1];
		double sumX = 0;
		final int nPoints = points.length;

		for (int n = 0; n < nPoints; n++) {
			sumX += points[n][0];
		}

		centroid[0] = sumX / nPoints;

		return centroid;
	}

	/**
	 * Find the centroid of a set of points in double[n][2] format
	 *
	 * @param points
	 * @return
	 */
	private static double[] getCentroid2D(final double[][] points) {
		final double[] centroid = new double[2];
		double sumX = 0;
		double sumY = 0;
		final int nPoints = points.length;

		for (int n = 0; n < nPoints; n++) {
			sumX += points[n][0];
			sumY += points[n][1];
		}

		centroid[0] = sumX / nPoints;
		centroid[1] = sumY / nPoints;

		return centroid;
	}

	/**
	 * Find the centroid of a set of points in double[n][3] format
	 *
	 * @param points
	 * @return
	 */
	private static double[] getCentroid3D(final double[][] points) {
		final double[] centroid = new double[3];
		double sumX = 0;
		double sumY = 0;
		double sumZ = 0;
		final int nPoints = points.length;

		for (int n = 0; n < nPoints; n++) {
			sumX += points[n][0];
			sumY += points[n][1];
			sumZ += points[n][2];
		}

		centroid[0] = sumX / nPoints;
		centroid[1] = sumY / nPoints;
		centroid[2] = sumZ / nPoints;

		return centroid;
	}

	/**
	 * Find the centroid of a set of points in double[n][i] format
	 *
	 * @param points
	 * @return
	 */
	private static double[] getCentroidND(final double[][] points) {
		final int nPoints = points.length;
		final int nDimensions = points[0].length;
		final double[] centroid = new double[nDimensions];
		final double[] sums = new double[nDimensions];

		for (int n = 0; n < nPoints; n++) {
			if (points[n].length != nDimensions)
				throw new IllegalArgumentException("Number of dimensions must be equal");
			for (int i = 0; i < nDimensions; i++) {
				sums[i] += points[n][i];
			}
		}

		for (int i = 0; i < nDimensions; i++) {
			centroid[i] = sums[i] / nPoints;
		}

		return centroid;
	}

	/**
	 * Return the centroid of a 1D array, which is its mean value
	 *
	 * @param points
	 * @return the mean value of the points
	 */
	public static double getCentroid(final double[] points) {
		final int nPoints = points.length;
		double sum = 0;
		for (int n = 0; n < nPoints; n++) {
			sum += points[n];
		}
		return sum / nPoints;
	}

	/**
	 * Calculate the centroid of a list of 3D Points
	 *
	 * @param points
	 * @return
	 */
	public static double[] getCentroid(final ArrayList<Point> points) {
		double xsum = 0;
		double ysum = 0;
		double zsum = 0;
		final double n = points.size();

		for (final Point p : points) {
			xsum += p.x;
			ysum += p.y;
			zsum += p.z;
		}
		final double[] centroid = { xsum / n, ysum / n, zsum / n };
		return centroid;
	}

	/**
	 * Returns the centroid Point of the given Points
	 *
	 * NB Might not be the exact centroid, because Point coordinates are
	 * integers.
	 *
	 * @return The centroid Point Returns null if the given list is null or
	 *         empty
	 */
	public static Point getCentroidPoint(final ArrayList<Point> points) {
		if (points == null || points.isEmpty()) {
			return null;
		}

		double xSum = 0.0;
		double ySum = 0.0;
		double zSum = 0.0;

		for (final Point p : points) {
			xSum += p.x;
			ySum += p.y;
			zSum += p.z;
		}

		final double n = points.size();
		final int x = (int) Math.round(xSum / n);
		final int y = (int) Math.round(ySum / n);
		final int z = (int) Math.round(zSum / n);
		return new Point(x, y, z);
	}
}
