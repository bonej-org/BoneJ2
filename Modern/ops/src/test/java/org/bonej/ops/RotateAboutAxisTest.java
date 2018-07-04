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

package org.bonej.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.stream.DoubleStream;

import net.imagej.ImageJ;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for the {@link RotateAboutAxis} op.
 *
 * @author Richard Domander
 */
public class RotateAboutAxisTest {

	private static final ImageJ imageJ = new ImageJ();
	private static BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp;

	@Test
	public void testCalculateDoesNotMutateInput() {
		final Tuple3d v = new Vector3d(1, 2, 3);

		rotateOp.calculate(v, new AxisAngle4d(0, 0, 1, Math.PI / 2.0));

		assertEquals("Input changed.", new Vector3d(1, 2, 3), v);
	}

	@Test
	public void testMutateChangesInput() {
		final Tuple3d input = new Vector3d(1, 0, 0);

		rotateOp.mutate1(input, new AxisAngle4d(0, 0, 1, Math.PI / 2.0));

		assertFalse("Input did not change.", new Vector3d(1, 0, 0).epsilonEquals(
			input, 1e-12));
	}

	@Test
	public void testOp() {
		final Tuple3d expected = new Vector3d(Math.cos(Math.PI / 4.0), Math.sin(
			Math.PI / 4.0), 0);
		expected.scale(3.0);
		final AxisAngle4d axisAngle4d = new AxisAngle4d(0, 0, 3, Math.PI / 4.0);

		final Tuple3d rotated = rotateOp.calculate(new Vector3d(3, 0, 0),
			axisAngle4d);

		assertTrue("Rotated vector is incorrect.", expected.epsilonEquals(rotated,
			1e-12));
	}

	@Test
	public void testRandomAxisAngle() {
		final AxisAngle4d a = RotateAboutAxis.randomAxisAngle();

		assertNotNull(a);
		final double vectorLength = Math.sqrt(DoubleStream.of(a.x, a.y, a.z).map(
			x -> x * x).sum());
		final double magnitude = Math.sin(a.angle / 2.0) / vectorLength;
		final double qNorm = DoubleStream.of(a.x * magnitude, a.y * magnitude, a.z *
			magnitude, Math.cos(a.angle / 2.0)).map(x -> x * x).sum();
		assertEquals(
			"A random rotation axis-angle should correspond to a unit quaternion",
			1.0, Math.sqrt(qNorm), 1e-12);
	}

	@BeforeClass
	public static void oneTimeSetUp() {
		rotateOp = Hybrids.binaryCFI1(imageJ.op(), RotateAboutAxis.class,
			Tuple3d.class, new Vector3d(), new AxisAngle4d());
	}

	@AfterClass
	public static void oneTimeTearDown() {
		imageJ.context().dispose();
	}
}
