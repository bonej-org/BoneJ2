/*-
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.geometry;

/**
 * Provides simple trigonometric calculations
 *
 * @author Michael Doube
 */
public final class Trig {

	private Trig() {}

	/**
	 * Calculate the distance between 2 3D points <i>p</i>(x, y, z) and <i>q</i>
	 * (x, y, z) using Pythagoras' theorem
	 *
	 * @param px x-coordinate of first point
	 * @param py y-coordinate of first point
	 * @param pz z-coordinate of first point
	 * @param qx x-coordinate of second point
	 * @param qy y-coordinate of second point
	 * @param qz z-coordinate of second point
	 * @return distance between points.
	 */
	public static double distance3D(final double px, final double py,
		final double pz, final double qx, final double qy, final double qz)
	{
		return distance3D(px - qx, py - qy, pz - qz);
	}

	/**
	 * <p>
	 * Calculate the distance to the origin, (0,0,0). Given 3 orthogonal vectors,
	 * calculates the vector sum
	 * </p>
	 *
	 * @param x x-coordinate of the point.
	 * @param y y-coordinate of the point.
	 * @param z z-coordinate of the point.
	 * @return distance of the point to the origin.
	 */
	public static double distance3D(final double x, final double y,
		final double z)
	{
		return Math.sqrt(x * x + y * y + z * z);
	}

	/**
	 * <p>
	 * Calculate the distance between 2 3D points p and q using Pythagoras'
	 * theorem, <i>a</i><sup>2</sup> = <i>b</i><sup>2</sup> + <i>c</i>
	 * <sup>2</sup>
	 * </p>
	 *
	 * @param p a 3 element array
	 * @param q another 3 element array
	 * @return distance between <i>p</i> and <i>q</i>
	 */
	static double distance3D(final double[] p, final double[] q) {
		return distance3D(p[0], p[1], p[2], q[0], q[1], q[2]);
	}

	static double distance3D(final double[] v) {
		return distance3D(v[0], v[1], v[2]);
	}

}
