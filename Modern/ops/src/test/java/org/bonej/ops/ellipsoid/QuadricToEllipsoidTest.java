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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;

import org.bonej.ops.RotateAboutAxis;
import org.bonej.ops.SolveQuadricEq;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link QuadricToEllipsoid}.
 * <p>
 * Because an ellipsoid has rotational symmetries, we'll have to do with
 * asserting that the op returns an orientation symmetrical to the expected. I
 * don't know how to test for an exact orientation, or if it's even possible.
 * </p>
 * 
 * @author Richard Domander
 */
public class QuadricToEllipsoidTest {

	//@formatter:off
	private static final Matrix4d UNIT_SPHERE =
            new Matrix4d(new double[] {
                    1, 0, 0, 0,
                    0, 1, 0, 0,
                    0, 0, 1, 0,
                    0, 0, 0, -1
            });
    //@formatter:on
	private static final ImageJ IMAGE_J = new ImageJ();
	// Constant seed for random generators
	private static final long SEED = 0xc0ffee;
	@SuppressWarnings("unchecked")
	private static final UnaryFunctionOp<Matrix4d, Optional<Ellipsoid>> quadricToEllipsoid =
		(UnaryFunctionOp) Functions.unary(IMAGE_J.op(), QuadricToEllipsoid.class,
			Optional.class, UNIT_SPHERE);
	@SuppressWarnings("unchecked")
	private static final BinaryFunctionOp<double[], Long, List<Vector3d>> ellipsoidPoints =
		(BinaryFunctionOp) Functions.binary(IMAGE_J.op(), EllipsoidPoints.class,
			List.class, new double[] { 1, 2, 3 }, 0);

	@Test
	public void testCone() {
		//@formatter:off
		final Matrix4d cone = new Matrix4d(new double[]{
				1, 0, 0, 0,
				0, -1, 0, 0,
				0, 0, 1, 0,
				0, 0, 0, -1});
		//@formatter:on

		final Optional<Ellipsoid> result = quadricToEllipsoid.calculate(cone);

		assertFalse(result.isPresent());
	}

	/**
	 * Tests fitting ellipsoid on a point cloud that forms a "band" around an
	 * ellipsoid surface, i.e. the radii of the points on the surface is scaled
	 * randomly.
	 * <p>
	 * Because the point cloud is more sparse, the fit won't as accurate than in
	 * {@link #testTransformedEllipsoid()}
	 * </p>
	 */
	@Test
	public void testEllipsoidBand() {
		// SETUP
		final double[] radii = { 1, 2, 3 };
		final Matrix4d orientation = new Matrix4d();
		orientation.setIdentity();
		// @formatter:off
		final Matrix4d symmetry = new Matrix4d(
				1, 0, 0, 0,
				0, -1, 0, 0,
				0, 0, -1, 0,
			    0, 0, 0, 1);
		// @formatter:on
		final Random rng = new Random(SEED);

		// EXECUTE
		final List<Vector3d> points = ellipsoidPoints.calculate(radii, 1_000L);
		// The points are isotropically distributed on the ellipsoid surface, but
		// after the scaling they are not evenly distributed in space.
		points.forEach(p -> {
			final double scale = (2 * rng.nextDouble() - 1) * 0.05 + 1.0;
			p.scale(scale);
		});
		final Matrix4d quadric = (Matrix4d) IMAGE_J.op().run(SolveQuadricEq.class,
			points);
		final Optional<Ellipsoid> result = quadricToEllipsoid.calculate(quadric);

		// VERIFY
		assertTrue(result.isPresent());
		final Ellipsoid ellipsoid = result.get();
		assertTrue("Ellipsoid centre point is not within tolerance", new Vector3d(0,
			0, 0).epsilonEquals(ellipsoid.getCentroid(), 0.05));
		assertEquals(radii[0], ellipsoid.getA(), 0.025);
		assertEquals(radii[1], ellipsoid.getB(), 0.025);
		assertEquals(radii[2], ellipsoid.getC(), 0.025);
		assertTrue(symmetry.epsilonEquals(ellipsoid.getOrientation(), 0.025));
	}

	/**
	 * Tests a point cloud that's been translated and rotated, which should result
	 * in a translated an rotated ellipsoid.
	 */
	@Test
	public void testTransformedEllipsoid() {
		// SETUP
		final Vector3d centroid = new Vector3d(1, 1, 1);
		final AxisAngle4d rotation = new AxisAngle4d(0, 0, 1, Math.PI / 4.0);
		final double[] radii = { 1, 2, 3 };
		// @formatter:off
		final double alpha = Math.sin(Math.PI / 4.0);
		final Matrix4d orientation = new Matrix4d(
				alpha, alpha, 0, 0,
				alpha, -alpha, 0, 0,
				0, 0, -1, 0,
				0, 0, 0, 1);
		// @formatter:on

		// EXECUTE
		final List<Vector3d> points = ellipsoidPoints.calculate(radii, 1_000L);
		final BinaryHybridCFI1<Serializable, AxisAngle4d, Vector3d> rotate = Hybrids
			.binaryCFI1(IMAGE_J.op(), RotateAboutAxis.class, Vector3d.class,
				Vector3d.class, rotation);
		points.forEach(rotate::mutate);
		points.forEach(p -> p.add(centroid));
		final Matrix4d quadric = (Matrix4d) IMAGE_J.op().run(SolveQuadricEq.class,
			points);
		final Optional<Ellipsoid> result = quadricToEllipsoid.calculate(quadric);

		// VERIFY
		assertTrue(result.isPresent());
		final Ellipsoid transformedEllipsoid = result.get();
		assertTrue(transformedEllipsoid.getCentroid().epsilonEquals(centroid,
			1e-12));
		assertEquals(radii[0], transformedEllipsoid.getA(), 1e-12);
		assertEquals(radii[1], transformedEllipsoid.getB(), 1e-12);
		assertEquals(radii[2], transformedEllipsoid.getC(), 1e-12);
		assertTrue(orientation.epsilonEquals(transformedEllipsoid.getOrientation(),
			0.025));
	}

	@Test
	public void testUnitSphere() {
		// A unit sphere has no orientation, so it's matrix will always be identity
		final Matrix4d expectedOrientation = new Matrix4d();
		expectedOrientation.setIdentity();

		final Optional<Ellipsoid> result = quadricToEllipsoid.calculate(
			UNIT_SPHERE);

		assertTrue(result.isPresent());
		final Ellipsoid unitSphere = result.get();
		assertEquals(1.0, unitSphere.getA(), 1e-12);
		assertEquals(1.0, unitSphere.getB(), 1e-12);
		assertEquals(1.0, unitSphere.getC(), 1e-12);
		assertTrue(unitSphere.getCentroid().epsilonEquals(new Vector3d(0, 0, 0),
			1e-12));
		assertTrue(expectedOrientation.epsilonEquals(unitSphere.getOrientation(),
			1e-12));
	}

	@BeforeClass
	public static void oneTimeSetup() {
		EllipsoidPoints.setSeed(SEED);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
