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

import java.util.stream.Stream;

import org.scijava.vecmath.Point3f;

public final class Vectors {

	private Vectors() {}

	/**
	 * Calculate the cross product of 3 Point3f's, which describe two vectors
	 * joined at the tails. Can be used to find the plane / surface normal of a
	 * triangle. Half of its magnitude is the area of the triangle.
	 *
	 * @param point0 both vectors' tails
	 * @param point1 vector 1's head
	 * @param point2 vector 2's head
	 * @return cross product vector
	 */
	public static Point3f crossProduct(final Point3f point0, final Point3f point1,
		final Point3f point2)
	{
		final double x1 = point1.x - point0.x;
		final double y1 = point1.y - point0.y;
		final double z1 = point1.z - point0.z;
		final double x2 = point2.x - point0.x;
		final double y2 = point2.y - point0.y;
		final double z2 = point2.z - point0.z;
		final Point3f crossVector = new Point3f();
		crossVector.x = (float) (y1 * z2 - z1 * y2);
		crossVector.y = (float) (z1 * x2 - x1 * z2);
		crossVector.z = (float) (x1 * y2 - y1 * x2);
		return crossVector;
	}

	/**
	 * Calculate the cross product of two vectors (x1, y1, z1) and (x2, y2, z2)
	 *
	 * @param x1 x-coordinate of the 1st vector.
	 * @param y1 y-coordinate of the 1st vector.
	 * @param z1 z-coordinate of the 1st vector.
	 * @param x2 x-coordinate of the 2nd vector.
	 * @param y2 y-coordinate of the 2nd vector.
	 * @param z2 z-coordinate of the 2nd vector.
	 * @return cross product in {x, y, z} format
	 */
	public static double[] crossProduct(final double x1, final double y1,
		final double z1, final double x2, final double y2, final double z2)
	{
		final double x = y1 * z2 - z1 * y2;
		final double y = z1 * x2 - x1 * z2;
		final double z = x1 * y2 - y1 * x2;
		return new double[] { x, y, z };
	}

	/**
	 * Calculate the cross product of 2 vectors, both in double[3] format
	 *
	 * @param a first vector
	 * @param b second vector
	 * @return resulting vector in double[3] format
	 */
	public static double[] crossProduct(final double[] a, final double[] b) {
		return crossProduct(a[0], a[1], a[2], b[0], b[1], b[2]);
	}

	/**
	 * Normalise a vector to have a length of 1 and the same orientation as the
	 * input vector a
	 *
	 * @param a a 3D vector.
	 * @return Unit vector in direction of a
	 */
	// TODO move to EllipsoidFactor
	public static double[] norm(final double[] a) {
		final double a0 = a[0];
		final double a1 = a[1];
		final double a2 = a[2];
		final double length = Math.sqrt(a0 * a0 + a1 * a1 + a2 * a2);

		final double[] normed = new double[3];
		normed[0] = a0 / length;
		normed[1] = a1 / length;
		normed[2] = a2 / length;
		return normed;
	}

	/**
	 * Generate a single randomly-oriented vector on the unit sphere
	 *
	 * @return 3-element double array containing [x y z]^T
	 */
	public static double[] randomVector() {
		final double z = 2 * Math.random() - 1;
		final double rho = Math.sqrt(1 - z * z);
		final double phi = Math.PI * (2 * Math.random() - 1);
		final double x = rho * Math.cos(phi);
		final double y = rho * Math.sin(phi);
		return new double[] { x, y, z };
	}

	/**
	 * Generate an array of randomly-oriented 3D unit vectors
	 *
	 * @param nVectors number of vectors to generate
	 * @return 2D array (nVectors x 3) containing unit vectors
	 */
	public static double[][] randomVectors(final int nVectors) {
		return Stream.generate(Vectors::randomVector).limit(nVectors).toArray(
			double[][]::new);
	}

	/**
	 * Generate an array of regularly-spaced 3D unit vectors. The vectors aren't
	 * equally spaced in all directions, but there is no clustering around the
	 * sphere's poles.
	 *
	 * @param nVectors number of vectors to generate
	 * @return 2D array (nVectors x 3) containing unit vectors
	 */
	public static double[][] regularVectors(final int nVectors) {

		final double[][] vectors = new double[nVectors][];
		final double inc = Math.PI * (3 - Math.sqrt(5));
		final double off = 2 / (double) nVectors;

		for (int k = 0; k < nVectors; k++) {
			final double y = k * off - 1 + (off / 2);
			final double r = Math.sqrt(1 - y * y);
			final double phi = k * inc;
			final double x = Math.cos(phi) * r;
			final double z = Math.sin(phi) * r;
			final double[] vector = { x, y, z };
			vectors[k] = vector;
		}
		return vectors;
	}

	/**
	 * Calculate the cross product of 2 column vectors, both in double[3][1]
	 * format
	 *
	 * @param a first vector
	 * @param b second vector
	 * @return resulting vector in double[3][1] format
	 */
	// TODO move to test class
	static double[][] crossProduct(final double[][] a, final double[][] b) {
		final double[][] c = new double[3][1];
		c[0][0] = a[1][0] * b[2][0] - a[2][0] * b[1][0];
		c[1][0] = a[2][0] * b[0][0] - a[0][0] * b[2][0];
		c[2][0] = a[0][0] * b[1][0] - a[1][0] * b[0][0];
		return c;
	}
}
