
package org.bonej.ops.ellipsoid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import net.imagej.ImageJ;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;

import org.bonej.ops.SolveQuadricEq;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
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
	private static UnaryFunctionOp<Matrix4d, Ellipsoid> quadricToEllipsoid =
		(UnaryFunctionOp) Functions.unary(IMAGE_J.op(), QuadricToEllipsoid.class,
			Ellipsoid.class, UNIT_SPHERE);
	@SuppressWarnings("unchecked")
	private static BinaryFunctionOp<double[], Long, List<Vector3d>> ellipsoidPoints =
		(BinaryFunctionOp) Functions.binary(IMAGE_J.op(), EllipsoidPoints.class,
			List.class, new double[] { 1, 2, 3 }, 0);

	@Test(expected = IllegalArgumentException.class)
	public void testConeFailsMatching() {
		//@formatter:off
        final Matrix4d cone = new Matrix4d(new double[]{
                1, 0, 0, 0,
                0, -1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, -1});
        //@formatter:on
		IMAGE_J.op().run(QuadricToEllipsoid.class, cone);
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
		final Ellipsoid ellipsoid = quadricToEllipsoid.calculate(quadric);

		// VERIFY
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
		final Quaterniondc q = new Quaterniond(new org.joml.AxisAngle4d(Math.PI / 4.0, 0, 0, 1));
		final double[] radii = { 1, 2, 3 };
		final BinaryHybridCFI1<org.joml.Vector3d, Quaterniondc, org.joml.Vector3d> rotate = Hybrids
				.binaryCFI1(IMAGE_J.op(), Rotate3d.class, org.joml.Vector3d.class,
						new org.joml.Vector3d(), q);
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
		final List<Vector3d> rotated = points.stream().map(
			p -> new org.joml.Vector3d(p.x, p.y, p.z)).peek(rotate::mutate).map(
				v -> new Vector3d(v.x, v.y, v.z)).peek(p -> p.add(centroid)).collect(Collectors.toList());

		final Matrix4d quadric = (Matrix4d) IMAGE_J.op().run(SolveQuadricEq.class,
				rotated);
		final Ellipsoid transformedEllipsoid = quadricToEllipsoid.calculate(
			quadric);

		// VERIFY
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

		final Ellipsoid unitSphere = quadricToEllipsoid.calculate(UNIT_SPHERE);

		assertEquals(1.0, unitSphere.getA(), 1e-12);
		assertEquals(1.0, unitSphere.getB(), 1e-12);
		assertEquals(1.0, unitSphere.getC(), 1e-12);
		assertTrue(unitSphere.getCentroid().epsilonEquals(new Vector3d(0, 0, 0),
			1e-12));
		assertTrue(expectedOrientation.epsilonEquals(unitSphere.getOrientation(),
			1e-12));
	}

	@Test
	public void testNanRadiusIsNotEllipsoid() {
		// This quadric looks like an ellipsoid (3x3 diagonals positive), but it has
		// a NaN radius (negative eigenvalue). These typically result from quadrics
		// solved from sparse data.
		final Matrix4d nanRadiusEllipsoid = new Matrix4d(new double[] {
			0.01085019421630129, -0.026230819423660012, -0.0012390257941481408,
			0.016336103119147793, -0.026230819423660012, 0.02043899336863353,
			0.01731688607718951, 0.03182508790873584, -0.0012390257941481408,
			0.01731688607718951, 0.2516413880666182, 0.0022183414533909485,
			0.016336103119147793, 0.03182508790873584, 0.0022183414533909485, -1.0 });

		assertFalse(QuadricToEllipsoid.isEllipsoid(nanRadiusEllipsoid));
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
