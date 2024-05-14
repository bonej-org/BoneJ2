/*-
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
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

import java.util.stream.Stream;

public final class Vectors {

	private Vectors() {}

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
}
