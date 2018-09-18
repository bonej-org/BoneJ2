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

package org.bonej.ops.ellipsoid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.imagej.ImageJ;

import org.joml.Vector3d;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Tests for {@link EllipsoidPoints}.
 *
 * @author Richard Domander
 */
public class EllipsoidPointsTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@Test
	public void testEllipsoidEquation() {
		final double a = 1.0;
		final double b = 2.0;
		final double c = 3.0;
		final Function<Vector3d, Double> eq = p -> {
			final BiFunction<Double, Double, Double> g = (x, r) -> (x * x) / (r * r);
			return g.apply(p.x, a) + g.apply(p.y, b) + g.apply(p.z, c);
		};

		@SuppressWarnings("unchecked")
		final Collection<Vector3d> points = (Collection<Vector3d>) IMAGE_J.op().run(
			EllipsoidPoints.class, new double[] { b, a, c }, 1000);

		points.forEach(p -> assertEquals("Point not on the ellipsoid surface", 1.0,
			eq.apply(p), 1e-10));
		assertTrue(points.stream().allMatch(p -> Math.abs(p.x) <= a));
		assertTrue(points.stream().allMatch(p -> Math.abs(p.y) <= b));
		assertTrue(points.stream().allMatch(p -> Math.abs(p.z) <= c));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsWithInfiniteRadii() {
		IMAGE_J.op().run(EllipsoidPoints.class, new double[] {
			Double.POSITIVE_INFINITY, 1, 1 }, 1_000);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsWithNanRadii() {
		IMAGE_J.op().run(EllipsoidPoints.class, new double[] { 1, 1, Double.NaN },
			1_000);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsWithNegativeN() {
		IMAGE_J.op().run(EllipsoidPoints.class, new double[] { 1, 1, 1 }, -1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsWithNegativeRadii() {
		IMAGE_J.op().run(EllipsoidPoints.class, new double[] { 1, 1, -1 }, 1_000);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsWithTooFewRadii() {
		IMAGE_J.op().run(EllipsoidPoints.class, new double[] { 1, 1 }, 1_000);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsWithTooManyRadii() {
		IMAGE_J.op().run(EllipsoidPoints.class, new double[] { 1, 1, 1, 1 }, 1_000);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsWithZeroRadii() {
		IMAGE_J.op().run(EllipsoidPoints.class, new double[] { 1, 0, 1 }, 1_000);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
