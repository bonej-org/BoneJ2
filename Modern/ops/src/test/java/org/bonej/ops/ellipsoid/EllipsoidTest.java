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

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;

import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4d;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests for {@link Ellipsoid}.
 *
 * @author Richard Domander
 */
public class EllipsoidTest {

	private static ImageJ IMAGE_J = new ImageJ();
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void testConstructor() {
		// SETUP
		final double a = 1.0;
		final double b = 2.0;
		final double c = 3.0;
		final Vector3dc expectedCentroid = new Vector3d();
		final Matrix4dc expectedOrientation = new Matrix4d();
		final List<Vector3d> expectedAxes = Arrays.asList(new Vector3d(a, 0, 0),
			new Vector3d(0, b, 0), new Vector3d(0, 0, c));

		// EXECUTE
		final Ellipsoid ellipsoid = new Ellipsoid(b, c, a);

		// VERIFY
		assertEquals(a, ellipsoid.getA(), 1e-12);
		assertEquals(b, ellipsoid.getB(), 1e-12);
		assertEquals(c, ellipsoid.getC(), 1e-12);
		final Vector3d centroid = ellipsoid.getCentroid();
		assertNotNull("Default centroid should not be null", centroid);
		assertEquals("Default centroid should be at origin", expectedCentroid,
			centroid);
		final Matrix4d orientation = ellipsoid.getOrientation();
		assertNotNull("Default orientation matrix should not be null", orientation);
		assertEquals("Default orientation matrix should be identity",
			expectedOrientation, orientation);
		final List<Vector3d> semiAxes = ellipsoid.getSemiAxes();
		assertNotNull("Default semi-axes should not be null", semiAxes);
		for (int i = 0; i < 3; i++) {
			assertEquals("Default semi-axis is incorrect", expectedAxes.get(i),
				semiAxes.get(i));
		}
	}

	@Test
	public void testGetCentroid() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);
		final Vector3dc centroid = new Vector3d(6, 7, 8);
		ellipsoid.setCentroid(centroid);

		final Vector3d c = ellipsoid.getCentroid();
		c.add(new Vector3d(1, 1, 1));

		assertEquals("Getter should have returned a copy, not a reference",
			centroid, ellipsoid.getCentroid());
	}

	@Test
	public void testGetOrientation() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		final Matrix4d expected = ellipsoid.getOrientation();
		final Matrix4d orientation = ellipsoid.getOrientation();

		assertNotSame("Getter should have returned a copy, not a reference",
			expected, orientation);
	}

	@Test
	public void testGetSemiAxes() {
		// SETUP
		final double a = 2.0;
		final double b = 4.0;
		final double c = 8.0;
		// @formatter:off
		final Matrix3dc orientation = new Matrix3d(
				-1, 0, 0,
				0, -1, 0,
				0, 0, 1
		);
		// @formatter:on
		final List<Vector3d> expectedAxes = Arrays.asList(new Vector3d(-a, 0, 0),
			new Vector3d(0, -b, 0), new Vector3d(0, 0, c));
		final Ellipsoid ellipsoid = new Ellipsoid(b, c, a);
		ellipsoid.setOrientation(orientation);

		// EXECUTE
		final List<Vector3d> semiAxes = ellipsoid.getSemiAxes();

		// VERIFY
		for (int i = 0; i < 3; i++) {
			assertEquals("Default semi-axis is incorrect", expectedAxes.get(i),
				semiAxes.get(i));
		}
	}

	@Test
	public void testGetVolume() {
		final double a = 2.3;
		final double b = 3.14;
		final double c = 4.25;
		final double expectedVolume = (4.0 / 3.0) * Math.PI * a * b * c;
		final Ellipsoid ellipsoid = new Ellipsoid(a, b, c);

		assertEquals(expectedVolume, ellipsoid.getVolume(), 1e-12);
	}

	@Test(expected = NullPointerException.class)
	public void testInitSamplingThrowsNPEIfOpsNull() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.initSampling(null);
	}

	@Test
	public void testSamplePointsAreOnEllipsoidSurface() {
		// SETUP
		final double a = 2.0;
		final double b = 3.0;
		final double c = 4.0;
		final Ellipsoid ellipsoid = new Ellipsoid(a, b, c);
		final Vector3dc centroid = new Vector3d(4, 5, 6);
		ellipsoid.setCentroid(centroid);
		final double sinAlpha = Math.sin(Math.PI / 4.0);
		final double cosAlpha = Math.cos(Math.PI / 4.0);
		// Orientation of an ellipsoid that's been rotated 45 deg around the z-axis
		// @formatter:off
		final Matrix3dc orientation = new Matrix3d(
				cosAlpha, -sinAlpha, 0,
				sinAlpha, cosAlpha, 0,
				0, 0, 1
		);
		// @formatter:on
		final AxisAngle4d invRotation = new AxisAngle4d();
		invRotation.set(orientation);
		invRotation.angle = -invRotation.angle;
		final Quaterniondc q = new Quaterniond(invRotation);
		final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> inverseRotation =
			Hybrids.binaryCFI1(IMAGE_J.op(), Rotate3d.class, Vector3d.class, new Vector3d(), q);
		ellipsoid.setOrientation(orientation);
		final long n = 10;
		final Function<Vector3d, Double> ellipsoidEq = (Vector3d p) -> {
			final BiFunction<Double, Double, Double> term = (x, r) -> (x * x) / (r *
				r);
			return term.apply(p.x, a) + term.apply(p.y, b) + term.apply(p.z, c);
		};
		ellipsoid.initSampling(IMAGE_J.op());

		// EXECUTE
		final List<Vector3d> points = ellipsoid.samplePoints(n);

		// VERIFY
		assertNotNull(points);
		assertEquals(n, points.size());
		// Reverse the translation and rotation so that we can assert the easier
		// equation of an axis-aligned ellipsoid at centroid
		points.forEach(p -> p.sub(centroid));
		points.forEach(inverseRotation::mutate);
		points.forEach(p -> assertEquals(
			"Point doesn't solve the ellipsoid equation", 1.0, ellipsoidEq.apply(p),
			1e-5));
	}

	@Test(expected = RuntimeException.class)
	public void testSamplePointsThrowsRuntimeExceptionIfNotInitialized() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.samplePoints(10);
	}

	@Test
	public void testSemiAxesConstructor() {
		// SETUP
		final Vector3d u = new Vector3d(2, -2, 0);
		final Vector3d v = new Vector3d(1, 1, 0);
		final Vector3d w = new Vector3d(0, 0, 1);
		final List<Vector3d> normalized = Stream.of(w, v, u).map(Vector3d::new)
			.peek(Vector3d::normalize).collect(toList());
		final Matrix4d expectedOrientation = new Matrix4d();
		for (int i = 0; i < 3; i++) {
			final Vector3d e = normalized.get(i);
			expectedOrientation.setColumn(i, new Vector4d(e.x, e.y, e.z, 0));
		}

		// EXECUTE
		final Ellipsoid ellipsoid = new Ellipsoid(u, w, v);

		// VERIFY
		final List<Vector3d> semiAxes = ellipsoid.getSemiAxes();
		assertEquals(w, semiAxes.get(0));
		assertEquals(v, semiAxes.get(1));
		assertEquals(u, semiAxes.get(2));
		assertEquals(w.length(), ellipsoid.getA(), 1e-12);
		assertEquals(v.length(), ellipsoid.getB(), 1e-12);
		assertEquals(u.length(), ellipsoid.getC(), 1e-12);
		assertEquals(expectedOrientation, ellipsoid.getOrientation());
	}

	@Test
	public void testSetA() {
		final Ellipsoid ellipsoid = new Ellipsoid(6, 7, 8);

		ellipsoid.setA(5);

		assertEquals(5, ellipsoid.getA(), 1e-12);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionGTB() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setA(3);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionGTC() {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 2, 2);

		ellipsoid.setA(3);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionNegativeRadius() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setA(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionNonFiniteRadius() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setA(Double.NaN);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionZeroRadius() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setA(0);
	}

	@Test
	public void testSetB() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 7, 8);

		ellipsoid.setB(4);

		assertEquals(4, ellipsoid.getB(), 1e-12);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetBThrowsExceptionGTC() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setB(4);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetBThrowsExceptionLTA() {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4);

		ellipsoid.setB(1);
	}

	@Test
	public void testSetC() {
		final Ellipsoid ellipsoid = new Ellipsoid(6, 7, 8);

		ellipsoid.setC(11);

		assertEquals(11, ellipsoid.getC(), 1e-12);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetCThrowsExceptionLTA() {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4);

		ellipsoid.setC(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetCThrowsExceptionLTB() {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4);

		ellipsoid.setC(2);
	}

	@Test
	public void testSetCentroid() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);
		final Vector3dc centroid = new Vector3d(6, 7, 8);

		ellipsoid.setCentroid(centroid);

		assertNotSame("Setter should not copy reference", centroid, ellipsoid
			.getCentroid());
		assertEquals("Setter copied values wrong", centroid, ellipsoid
			.getCentroid());
	}

	@Test(expected = NullPointerException.class)
	public void testSetCentroidThrowsNPEIfCentroidNull() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setCentroid(null);
	}

	@Test
	public void testSetOrientation() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 4);
		// @formatter:off
		final Matrix3d orientation = new Matrix3d(
				0, -1, 0,
				1, 0, 0,
				0, 0, 1
		);
		// @formatter:on
		final Matrix3dc original = new Matrix3d(orientation);

		ellipsoid.setOrientation(orientation);
		orientation.scale(1.234);
		final Matrix3d currentOrientation = new Matrix3d();
		ellipsoid.getOrientation().get3x3(currentOrientation);

		assertTrue("Setter copied values incorrectly or set a reference",
			original.equals(currentOrientation, 1e-12));
	}

	@Test
	public void testSetOrientationAllowsLeftHandedBasis() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 4);
		// @formatter:off
		final Matrix3dc leftHanded = new Matrix3d(
				1, 0, 0,
				0, 1, 0,
				0, 0, -1
		);
		// @formatter:on

		ellipsoid.setOrientation(leftHanded);
	}

	@Test
	public void testSetOrientationNormalizesVectors() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 4);
		// @formatter:off
		final Matrix3dc orientation = new Matrix3d(
				3, 0, 0,
				0, 3, 0,
				0, 0, 3
		);
		// @formatter:on

		ellipsoid.setOrientation(orientation);

		final Matrix4d m = ellipsoid.getOrientation();
		for (int i = 0; i < 4; i++) {
			final Vector4d v = new Vector4d();
			m.getColumn(i, v);
			assertEquals("Vector is not a unit vector", 1.0, v.length(), 1e-12);
		}
	}

	@Test
	public void testSetOrientationThrowsIAEIfNotOrthogonalVectors() {
		// SETUP
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);
		// @formatter:off
		final Matrix3d uVNotOrthogonal = new Matrix3d(
				1, 1, 0,
				0, 1, 0,
				0, 0, 1
		);
		final Matrix3d uWNotOrthogonal = new Matrix3d(
				1, 0, 1,
				0, 1, 0,
				0, 0, 1
		);
		final Matrix3d vWNotOrthogonal = new Matrix3d(
				1, 0, 0,
				0, 1, 1,
				0, 0, 1
		);
		// @formatter:on
		final List<Matrix3d> testMatrices = Arrays.asList(uVNotOrthogonal,
			uWNotOrthogonal, vWNotOrthogonal);
		int exceptions = 0;

		// EXECUTE
		for (final Matrix3d matrix : testMatrices) {
			try {
				ellipsoid.setOrientation(matrix);
			}
			catch (final IllegalArgumentException e) {
				assertEquals("Vectors must be orthogonal", e.getMessage());
				exceptions++;
			}
		}

		// VERIFY
		assertEquals("All non-orthogonal matrices should have thrown an exception",
			testMatrices.size(), exceptions);
	}

	@Test(expected = NullPointerException.class)
	public void testSetOrientationThrowsNPEIfMatrixNull() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setOrientation(null);
	}

	@Test
	public void testSetSemiAxesClonesParameters() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);
		final Vector3d v = new Vector3d(0, 0, 2);
		final Vector3d original = new Vector3d(v);

		ellipsoid.setSemiAxes(new Vector3d(2, 0, 0), new Vector3d(0, 2, 0), v);

		assertNotSame("Setter copied reference", v, ellipsoid.getSemiAxes().get(2));
		assertEquals("Setter changed parameter", original, v);
	}

	@Test(expected = NullPointerException.class)
	public void testSetSemiAxesThrowsNPEIfParameterNull() {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setSemiAxes(new Vector3d(), new Vector3d(), null);
	}

	@Test
	public void testGetRadii() {
		final double a = 1;
		final double b = 2;
		final double c = 3;
		final Ellipsoid ellipsoid = new Ellipsoid(a, b, c);

		final double[] radii = ellipsoid.getRadii();

		assertEquals(a, radii[0], 1e-12);
		assertEquals(b, radii[1], 1e-12);
		assertEquals(c, radii[2], 1e-12);
	}

	@Test
	public void testGetEigenMatrix() {
		final double a = 1;
		final double b = 2;
		final double c = 3;
		final Ellipsoid ellipsoid = new Ellipsoid(a, b, c);
		final Matrix3dc expected = new Matrix3d();

		final Matrix3dc result = ellipsoid.getEigenMatrix();

		assertTrue(expected.equals(result, 1e-12));
	}

	@Test
	public void testEigenValues() {
		final double a = 1;
		final double b = 2;
		final double c = 3;
		final Ellipsoid ellipsoid = new Ellipsoid(a, b, c);
		final double eVA = 1.0 / (a * a);
		final double eVB = 1.0 / (b * b);
		final double eVC = 1.0 / (c * c);

		final double[] eigenValues = ellipsoid.getEigenValues();

		assertEquals(eVC, eigenValues[0], 1e-12);
		assertEquals(eVB, eigenValues[1], 1e-12);
		assertEquals(eVA, eigenValues[2], 1e-12);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
		IMAGE_J = null;
	}
}
