/*
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CentroidTest {

	double[] oneDa = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
	double[] oneDb = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 };
	double[] oneDc = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17 };
	double[] oneDd = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
	double[] oneDe = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
	double[] oneDf = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 };
	double[] oneDg = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 };
	double[] oneDh = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
	double[] oneDi = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
	double[] oneDj = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

	double[][] badNd = { oneDa, oneDb, oneDc, oneDd, oneDe, oneDf, oneDg, oneDh, oneDi, oneDj };

	double[][] goodNd = { oneDa, oneDa, oneDa, oneDa, oneDa, oneDa, oneDa, oneDa, oneDa };

	@Test
	public void testGetCentroidDoubleArrayArray() {
		try {
			Centroid.getCentroid(badNd);
			fail("Should throw IllegalArgumentException");
		} catch (final IllegalArgumentException e) {
		}

		// A 20-D centroid
		final double[] centroid = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 };
		assertArrayEquals(centroid, Centroid.getCentroid(goodNd), 1E-9);

		// A 3-D array
		final double[][] threeD = { { 1, 2, 3 }, { 4, 5, 6 }, { 7, 8, 9 }, { 10, 11, 12 }, { 13, 14, 15 },
				{ 16, 17, 18 } };
		// Its centroid
		final double[] centroid3d = { 8.5, 9.5, 10.5 };
		assertArrayEquals(centroid3d, Centroid.getCentroid(threeD), 1E-9);

		// A 2-D array
		final double[][] twoD = { { 1, 2 }, { 4, 5 }, { 7, 8 }, { 10, 11 }, { 13, 14 }, { 16, 17 } };
		// Its centroid
		final double[] centroid2d = { 8.5, 9.5 };
		assertArrayEquals(centroid2d, Centroid.getCentroid(twoD), 1E-9);

		// A 1-D array
		final double[][] oneD = { { 1 }, { 4 }, { 7 }, { 10 }, { 13 }, { 16 } };
		// Its centroid
		final double[] centroid1d = { 8.5 };
		assertArrayEquals(centroid1d, Centroid.getCentroid(oneD), 1E-9);

	}
}
