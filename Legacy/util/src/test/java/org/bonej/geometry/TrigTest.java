/*
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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

public class TrigTest {

	double[] p0 = { 1, 2, 3 };
	double[] p1 = { 4, 5, 6 };

	@Test
	public void testDistance3DDoubleArrayDoubleArray() {
		final double result = Trig.distance3D(p0, p1);
		final double expected = Math.sqrt(27);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleDoubleDoubleDoubleDoubleDouble() {
		final double result = Trig.distance3D(1, 2, 3, 4, 5, 6);
		final double expected = Math.sqrt(27);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleDoubleDouble() {
		final double result = Trig.distance3D(1, 2, 3);
		final double expected = Math.sqrt(14);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleArray() {
		final double result = Trig.distance3D(p1);
		final double expected = Math.sqrt(77);
		assertEquals(expected, result, 1e-12);
	}
}
