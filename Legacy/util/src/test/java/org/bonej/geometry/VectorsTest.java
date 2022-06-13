/*
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VectorsTest {

	@Test
	public void testRandomVectors() {
		final int n = 1000;
		final double[][] v = Vectors.randomVectors(n);
		// check that vectors are unit vectors
		for (int i = 0; i < n; i++) {
			final double x = v[i][0];
			final double y = v[i][1];
			final double z = v[i][2];
			final double length = Math.sqrt(x * x + y * y + z * z);
			assertEquals(1, length, 1e-9);
		}
	}

	@Test
	public void testRegularVectors() {
		final int n = 1000;
		final double[][] v = Vectors.regularVectors(n);
		// check that vectors are unit vectors
		for (int i = 0; i < n; i++) {
			final double x = v[i][0];
			final double y = v[i][1];
			final double z = v[i][2];
			final double length = Math.sqrt(x * x + y * y + z * z);
			assertEquals(1, length, 1e-9);
		}
	}
}
