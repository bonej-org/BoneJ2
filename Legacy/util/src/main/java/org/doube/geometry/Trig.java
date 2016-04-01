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

/**
 * Provides simple trigonometric calculations
 *
 * @author Michael Doube
 */
public class Trig {

	/**
	 * <p>
	 * Calculate the distance between 2 3D points p and q using Pythagoras'
	 * theorem, <i>a</i><sup>2</sup> = <i>b</i><sup>2</sup> + <i>c</i>
	 * <sup>2</sup>
	 * </p>
	 *
	 * @param p
	 *            a 3 element array
	 * @param q
	 *            another 3 element array
	 * @return distance between <i>p</i> and <i>q</i>
	 */
	public static double distance3D(final double[] p, final double[] q) {
		return distance3D(p[0], p[1], p[2], q[0], q[1], q[2]);
	}

	/**
	 * <p>
	 * Calculate the distance between 2 3D points <i>p</i>(x, y, z) and <i>q</i>
	 * (x, y, z) using Pythagoras' theorem
	 * </p>
	 *
	 * @param px
	 *            x-coordinate of first point
	 * @param py
	 *            y-coordinate of first point
	 * @param pz
	 *            z-coordinate of first point
	 * @param qx
	 *            x-coordinate of second point
	 * @param qy
	 *            y-coordinate of second point
	 * @param qz
	 *            z-coordinate of second point
	 * @return
	 */
	public static double distance3D(final double px, final double py, final double pz, final double qx, final double qy,
			final double qz) {
		return distance3D(px - qx, py - qy, pz - qz);
	}

	/**
	 * <p>
	 * Calculate the distance to the origin, (0,0,0). Given 3 orthogonal
	 * vectors, calculates the vector sum
	 * </p>
	 *
	 * @param x
	 * @param y
	 * @param z
	 * @return
	 */
	public static double distance3D(final double x, final double y, final double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}

	public static double distance3D(final double[] v) {
		return distance3D(v[0], v[1], v[2]);
	}

	/**
	 * Caculate the angle between two vectors joined at their tails at the point
	 * (xv, yv, zv)
	 *
	 * @param x0
	 *            x-coordinate of the head of vector 0
	 * @param y0
	 *            y-coordinate of the head of vector 0
	 * @param z0
	 *            z-coordinate of the head of vector 0
	 * @param x1
	 *            x-coordinate of the head of vector 1
	 * @param y1
	 *            y-coordinate of the head of vector 1
	 * @param z1
	 *            z-coordinate of the head of vector 1
	 * @param xv
	 *            x-coordinate of the mutual tail point
	 * @param yv
	 *            y-coordinate of the mutual tail point
	 * @param zv
	 *            z-coordinate of the mutual tail point
	 * @return angle formed by 0-V-1
	 */
	public static double angle3D(double x0, double y0, double z0, double x1, double y1, double z1, final double xv,
			final double yv, final double zv) {

		x0 -= xv;
		y0 -= yv;
		z0 -= zv;
		x1 -= xv;
		y1 -= yv;
		z1 -= zv;

		final double dot = x0 * x1 + y0 * y1 + z0 * z1;
		final double d0 = distance3D(x0, y0, z0);
		final double d1 = distance3D(x1, y1, z1);

		final double cosTheta = dot / (d0 * d1);

		return Math.acos(cosTheta);
	}

}
