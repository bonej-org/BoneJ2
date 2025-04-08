/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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

package org.bonej.ops.ellipsoid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.imagej.ImageJ;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imagej.ops.stats.regression.leastSquares.Quadric;

import org.joml.AxisAngle4d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

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
	private static final Matrix4dc UNIT_SPHERE =
            new Matrix4d(
                    1, 0, 0, 0,
                    0, 1, 0, 0,
                    0, 0, 1, 0,
                    0, 0, 0, -1
            );
    //@formatter:on
	private static ImageJ IMAGE_J = new ImageJ();
	// Constant seed for random generators
	private static final long SEED = 0xc0ffee;
	@SuppressWarnings("unchecked")
	private static final UnaryFunctionOp<Matrix4dc, Optional<Ellipsoid>> quadricToEllipsoid =

		(UnaryFunctionOp) Functions.unary(IMAGE_J.op(), QuadricToEllipsoid.class,
			Optional.class, UNIT_SPHERE);
	@SuppressWarnings("unchecked")
	private static final BinaryFunctionOp<double[], Long, List<Vector3d>> ellipsoidPoints =
		(BinaryFunctionOp) Functions.binary(IMAGE_J.op(), EllipsoidPoints.class,
			List.class, new double[] { 1, 2, 3 }, 0);

	@Test
	public void testCone() {
		//@formatter:off
        final Matrix4d cone = new Matrix4d(
                1, 0, 0, 0,
                0, -1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, -1);
        //@formatter:on
		IMAGE_J.op().run(QuadricToEllipsoid.class, cone);

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
		// @formatter:off
		final Matrix4dc symmetry = new Matrix4d(
				1, 0, 0, 0,
				0, -1, 0, 0,
				0, 0, -1, 0,
			    0, 0, 0, 1);
		// @formatter:on
		final Random rng = new Random(SEED);
		// The points are isotropically distributed on the ellipsoid surface, but
		// after the scaling they are not evenly distributed in space.
		final List<Vector3d> points = ellipsoidPoints.calculate(radii, 1_000L);
		points.forEach(p -> {
			final double scale = (2 * rng.nextDouble() - 1) * 0.05 + 1.0;
			p.mul(scale);
		});
		// EXECUTE
		final Matrix4dc quadric = (Matrix4dc) IMAGE_J.op().run(Quadric.class,
			points);
		final Optional<Ellipsoid> result = quadricToEllipsoid.calculate(quadric);

		// VERIFY
		assertTrue(result.isPresent());
		final Ellipsoid ellipsoid = result.get();
		final Vector3dc centroid = new Vector3d(ellipsoid.getCentroid().x, ellipsoid
			.getCentroid().y, ellipsoid.getCentroid().z);
		assertTrue("Ellipsoid centre point is not within tolerance", epsilonEquals(
			new Vector3d(0, 0, 0), centroid, 0.05));
		assertEquals(radii[0], ellipsoid.getA(), 0.025);
		assertEquals(radii[1], ellipsoid.getB(), 0.025);
		assertEquals(radii[2], ellipsoid.getC(), 0.025);
		epsilonEquals(symmetry, ellipsoid.getOrientation(), 0.025);
	}

	/**
	 * Tests a point cloud that's been translated and rotated, which should result
	 * in a translated an rotated ellipsoid.
	 */
	@Test
	public void testTransformedEllipsoid() {
		// SETUP
		final Vector3dc centroid = new Vector3d(1, 1, 1);
		final Quaterniondc q = new Quaterniond(new AxisAngle4d(Math.PI / 4.0, 0, 0,
			1));
		final double[] radii = { 1, 2, 3 };
		final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotate = Hybrids
			.binaryCFI1(IMAGE_J.op(), Rotate3d.class, Vector3d.class, new Vector3d(),
				q);
		// @formatter:off
		final double alpha = Math.sin(Math.PI / 4.0);
		final Matrix4dc orientation = new Matrix4d(
				alpha, alpha, 0, 0,
				alpha, -alpha, 0, 0,
				0, 0, -1, 0,
				0, 0, 0, 1);
		// @formatter:on
		final List<Vector3d> points = ellipsoidPoints.calculate(radii, 1_000L);
		points.forEach(rotate::mutate);
		points.forEach(p -> p.add(centroid));

		// EXECUTE
		final Matrix4dc quadric = (Matrix4dc) IMAGE_J.op().run(Quadric.class,
			points);
		final Optional<Ellipsoid> result = quadricToEllipsoid.calculate(quadric);

		// VERIFY
		assertTrue(result.isPresent());
		final Ellipsoid transformedEllipsoid = result.get();
		final Vector3d v = transformedEllipsoid.getCentroid();
		assertTrue(epsilonEquals(centroid, new Vector3d(v.x, v.y, v.z), 1e-12));
		assertEquals(radii[0], transformedEllipsoid.getA(), 1e-12);
		assertEquals(radii[1], transformedEllipsoid.getB(), 1e-12);
		assertEquals(radii[2], transformedEllipsoid.getC(), 1e-12);
		epsilonEquals(orientation, transformedEllipsoid.getOrientation(), 0.025);
	}

	@Test
	public void testUnitSphere() {
		// A unit sphere has no orientation, so it's matrix will always be identity
		final Matrix4d expectedOrientation = new Matrix4d().identity();

		final Optional<Ellipsoid> result = quadricToEllipsoid.calculate(
			UNIT_SPHERE);

		assertTrue(result.isPresent());
		final Ellipsoid unitSphere = result.get();
		assertEquals(1.0, unitSphere.getA(), 1e-12);
		assertEquals(1.0, unitSphere.getB(), 1e-12);
		assertEquals(1.0, unitSphere.getC(), 1e-12);
		final Vector3d c = unitSphere.getCentroid();
		assertTrue(epsilonEquals(new Vector3d(0, 0, 0), new Vector3d(c.x, c.y, c.z),
			1e-12));
		epsilonEquals(expectedOrientation, unitSphere.getOrientation(), 1e-12);
	}

	@BeforeClass
	public static void oneTimeSetup() {
		EllipsoidPoints.setSeed(SEED);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
		IMAGE_J = null;
	}

	private void epsilonEquals(final Matrix4dc a, final Matrix4dc b,
		final double eps)
	{
		assertEquals(a.m00(), b.m00(), eps);
		assertEquals(a.m01(), b.m01(), eps);
		assertEquals(a.m02(), b.m02(), eps);
		assertEquals(a.m03(), b.m03(), eps);
		assertEquals(a.m10(), b.m10(), eps);
		assertEquals(a.m11(), b.m11(), eps);
		assertEquals(a.m12(), b.m12(), eps);
		assertEquals(a.m13(), b.m13(), eps);
		assertEquals(a.m20(), b.m20(), eps);
		assertEquals(a.m21(), b.m21(), eps);
		assertEquals(a.m22(), b.m22(), eps);
		assertEquals(a.m23(), b.m23(), eps);
		assertEquals(a.m30(), b.m30(), eps);
		assertEquals(a.m31(), b.m31(), eps);
		assertEquals(a.m32(), b.m32(), eps);
		assertEquals(a.m33(), b.m33(), eps);
	}

	private boolean epsilonEquals(final Vector3dc u, final Vector3dc v,
		final double epsilon)
	{
		double diff;
		diff = u.x() - v.x();
		if (Double.isNaN(diff)) return false;
		if ((diff < 0 ? -diff : diff) > epsilon) return false;

		diff = u.y() - v.y();
		if (Double.isNaN(diff)) return false;
		if ((diff < 0 ? -diff : diff) > epsilon) return false;

		diff = u.z() - v.z();
		if (Double.isNaN(diff)) return false;
		return !((diff < 0 ? -diff : diff) > epsilon);
	}
}
