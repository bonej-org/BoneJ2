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

import java.util.function.Supplier;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.type.numeric.real.DoubleType;

import org.apache.commons.math3.random.RandomVectorGenerator;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests for {@link DistanceFromEllipsoidSurfaceOp}.
 *
 * @author Alessandro Felder
 */
public class DistanceFromEllipsoidSurfaceOpTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static final RandomVectorGenerator sphereRng =
		new UnitSphereRandomVectorGenerator(3);
	private static BinaryFunctionOp<Ellipsoid, Vector3dc, DoubleType> distanceFromEllipsoidSurfaceOp;
	private static Ellipsoid axisAlignedEllipsoid;
	private static Ellipsoid transformedEllipsoid;
	private static Ellipsoid sphere;
	private final Supplier<Vector3dc> spherePointSupplier = () -> {
		final double[] c = sphereRng.nextVector();
		return new Vector3d(c[0], c[1], c[2]);
	};

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Test(expected = ArithmeticException.class)
	public void testArithmeticExceptionForZeroDeterminant() {
		final Ellipsoid ellipsoid = new Ellipsoid(2.0, 2.0, 2.0);
		final Vector3dc origin = new Vector3d();
		distanceFromEllipsoidSurfaceOp.calculate(ellipsoid, origin).get();
	}

	// Same three tests for a more complicated ellipsoid
	@Test
	public void testInsidePointForArbitraryEllipsoid() {
		final Vector3dc insidePoint = new Vector3d(1.0, 1.0 + Math.sqrt(3.0) / 2.0,
			1 - 0.5);
		final double distanceToInsidePoint = distanceFromEllipsoidSurfaceOp
			.calculate(transformedEllipsoid, insidePoint).get();
		assertEquals("Distance to inside point failed", 1.0, distanceToInsidePoint,
			1.0e-12);
	}

	// Test known distances from surface of an axis-aligned ellipsoid
	@Test
	public void testInsidePointForAxisAlignedEllipsoid() {
		final Vector3dc insidePoint = new Vector3d(0.0, 1.0, 0.0);
		final double distanceToInsidePoint = distanceFromEllipsoidSurfaceOp
			.calculate(axisAlignedEllipsoid, insidePoint).get();
		assertEquals("Distance to inside point failed", 1.0, distanceToInsidePoint,
			1.0e-12);
	}

	/**
	 * Same three tests for a sphere and arbitrarily chosen points
	 */
	@Test
	public void testInsidePointForSphere() {
		final Vector3d sphereVector = new Vector3d(spherePointSupplier.get());
		final Vector3dc insidePoint = new Vector3d(sphereVector);
		final double distanceToInsidePoint = distanceFromEllipsoidSurfaceOp
			.calculate(sphere, insidePoint).get();
		assertEquals("Distance to inside point failed", 1.0, distanceToInsidePoint,
			1.0e-12);
	}

	@Test
	public void testOutsidePointForArbitraryEllipsoid() {
		final Vector3dc outsidePoint = new Vector3d(1.0 + 2.0, 1.0, 1.0);
		final double distanceToOutsidePoint = distanceFromEllipsoidSurfaceOp
			.calculate(transformedEllipsoid, outsidePoint).get();
		assertEquals("Distance to outside point failed", 1.0,
			distanceToOutsidePoint, 1.0e-12);
	}

	@Test
	public void testOutsidePointForAxisAlignedEllipsoid() {
		final Vector3dc outsidePoint = new Vector3d(2.0, 0.0, 0.0);
		final double distanceToOutsidePoint = distanceFromEllipsoidSurfaceOp
			.calculate(axisAlignedEllipsoid, outsidePoint).get();
		assertEquals("Distance to outside point failed", 1.0,
			distanceToOutsidePoint, 1.0e-12);
	}

	@Test
	public void testOutsidePointForSphere() {
		final Vector3d sphereVector = new Vector3d(spherePointSupplier.get());
		sphereVector.mul(3.0);
		final Vector3dc outsidePoint = new Vector3d(sphereVector);
		final double distanceToOutsidePoint = distanceFromEllipsoidSurfaceOp
			.calculate(sphere, outsidePoint).get();
		assertEquals("Distance to outside point failed", 1.0,
			distanceToOutsidePoint, 1.0e-12);
	}

	@Test
	public void testRotationTransformation() {
		final Ellipsoid ellipsoid = new Ellipsoid(1.0, 2.0, 3.0);
		ellipsoid.setOrientation(new Matrix3d(0, 0, 1, 0, -1, 0, 1, 0, 0));
		final Vector3dc point = new Vector3d(0, 0, 8);

		final Vector3dc rotated = DistanceFromEllipsoidSurfaceOp
			.toEllipsoidCoordinates(point, ellipsoid);
		assertEquals(8.0, rotated.x(), 1.0e-12);
		assertEquals(0.0, rotated.y(), 1.0e-12);
		assertEquals(0.0, rotated.z(), 1.0e-12);

	}

	@Test
	public void testSurfacePointForArbitraryEllipsoid() {
		final Vector3dc surfacePoint = new Vector3d(1.0, 1.0 + 1.5, 1.0 + 3.0 * Math
			.sqrt(3.0) / 2.0);
		final double distanceToSurfacePoint = distanceFromEllipsoidSurfaceOp
			.calculate(transformedEllipsoid, surfacePoint).get();
		assertEquals("Distance to surface point failed", 0.0,
			distanceToSurfacePoint, 1.0e-12);
	}

	@Test
	public void testSurfacePointForAxisAlignedEllipsoid() {
		final Vector3dc surfacePoint = new Vector3d(0.0, 0.0, 3.0);
		final double distanceToSurfacePoint = distanceFromEllipsoidSurfaceOp
			.calculate(axisAlignedEllipsoid, surfacePoint).get();
		assertEquals("Distance to surface point failed", 0.0,
			distanceToSurfacePoint, 1.0e-12);
	}

	@Test
	public void testSurfacePointForSphere() {
		final Vector3d sphereVector = new Vector3d(spherePointSupplier.get());
		sphereVector.mul(2.0);
		final Vector3dc surfacePoint = new Vector3d(sphereVector);
		final double distanceToSurfacePoint = distanceFromEllipsoidSurfaceOp
			.calculate(sphere, surfacePoint).get();
		assertEquals("Distance to surface point failed", 0.0,
			distanceToSurfacePoint, 1.0e-12);
	}

	@Test
	public void testTranslationTransformation() {
		final Ellipsoid ellipsoid = new Ellipsoid(2.0, 2.0, 2.0);
		ellipsoid.setCentroid(new Vector3d(5, 7, 8));
		final Vector3dc point = new Vector3d(5, 7, 9);

		final Vector3dc translated = DistanceFromEllipsoidSurfaceOp
			.toEllipsoidCoordinates(point, ellipsoid);
		assertEquals(0.0, translated.x(), 1.0e-12);
		assertEquals(0.0, translated.y(), 1.0e-12);
		assertEquals(1.0, translated.z(), 1.0e-12);

	}

	@Test
	public void testCalculateThrowsIAEIfMaxIterationsNegative() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("Max iterations must be positive");
		IMAGE_J.op().run(DistanceFromEllipsoidSurfaceOp.class, sphere, new Vector3d(
			2, 0, 0), 1.0, -1);
	}

	@Test
	public void testCalculateThrowsIAEIfToleranceNegative() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("Tolerance cannot be negative");
		IMAGE_J.op().run(DistanceFromEllipsoidSurfaceOp.class, sphere, new Vector3d(
			2, 0, 0), -1.0, 100);
	}

	@BeforeClass
	public static void oneTimeSetUp() {
		axisAlignedEllipsoid = new Ellipsoid(1.0, 2.0, 3.0);

		// axisAlignedEllipsoid translated by (1,1,1) and rotated 30 degrees around
		// x-axis
		transformedEllipsoid = new Ellipsoid(1.0, 2.0, 3.0);
		transformedEllipsoid.setOrientation(new Matrix3d(1, 0, 0, 0, Math.sqrt(
			3.0) / 2.0, 0.5, 0, -0.5, Math.sqrt(3.0) / 2.0));
		transformedEllipsoid.setCentroid(new Vector3d(1, 1, 1));

		sphere = new Ellipsoid(2.0, 2.0, 2.0);
	}

	@BeforeClass
	public static void setUpBeforeClass() {
		distanceFromEllipsoidSurfaceOp = Functions.binary(IMAGE_J.op(),
			DistanceFromEllipsoidSurfaceOp.class, DoubleType.class, Ellipsoid.class,
			Vector3dc.class);
	}

	@AfterClass
	public static void tearDownAfterClass() {
		IMAGE_J.context().dispose();
	}
}
