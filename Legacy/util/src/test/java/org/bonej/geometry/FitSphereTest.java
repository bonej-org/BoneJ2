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

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class FitSphereTest {

	@Test
	public void testFitSphere() {
		FitEllipsoid.setSeed(0xc0ffee);
		for (double r = 1; r < 5; r += Math.random()) {
			for (double x = -2; x < 2; x += Math.random()) {
				for (double y = -2; y < 2; y += Math.random()) {
					for (double z = -2; z < 2; z += Math.random()) {
						for (double theta = -Math.PI; theta < Math.PI; theta += Math.random()) {
							final double[][] points = FitEllipsoid.testEllipsoid(r, r, r, theta, x, y, z, 0.001, 10,
									true);
							final double[] sphere = FitSphere.fitSphere(points);
							final double[] expected = { x, y, z, r };
							assertArrayEquals(expected, sphere, 1e-2);
						}
					}
				}
			}
		}
	}

}
