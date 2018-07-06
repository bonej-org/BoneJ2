
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
	private static final ImageJ IMAGE_J = new ImageJ();
	// Constant seed for random generators
	private static final long SEED = 0xc0ffee;
	@SuppressWarnings("unchecked")
	private static final UnaryFunctionOp<Matrix4dc, Ellipsoid> quadricToEllipsoid =
		(UnaryFunctionOp) Functions.unary(IMAGE_J.op(), QuadricToEllipsoid.class,
			Ellipsoid.class, UNIT_SPHERE);
	@SuppressWarnings("unchecked")
	private static final BinaryFunctionOp<double[], Long, List<org.scijava.vecmath.Vector3d>> ellipsoidPoints =
		(BinaryFunctionOp) Functions.binary(IMAGE_J.op(), EllipsoidPoints.class,
			List.class, new double[] { 1, 2, 3 }, 0);

	@Test(expected = IllegalArgumentException.class)
	public void testConeFailsMatching() {
		//@formatter:off
        final Matrix4d cone = new Matrix4d(
                1, 0, 0, 0,
                0, -1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, -1);
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
		final List<Vector3d> points = ellipsoidPoints.calculate(radii, 1_000L)
			.stream().map(p -> new Vector3d(p.x, p.y, p.z)).peek(p -> {
				final double scale = (2 * rng.nextDouble() - 1) * 0.05 + 1.0;
				p.mul(scale);
			}).collect(Collectors.toList());

		// EXECUTE
		final Matrix4dc quadric = (Matrix4dc) IMAGE_J.op().run(SolveQuadricEq.class,
				points);
		final Ellipsoid ellipsoid = quadricToEllipsoid.calculate(quadric);

		// VERIFY
		final Vector3dc centroid = new Vector3d(ellipsoid.getCentroid().x, ellipsoid
			.getCentroid().y, ellipsoid.getCentroid().z);
		assertTrue("Ellipsoid centre point is not within tolerance", epsilonEquals(
			new Vector3d(0, 0, 0), centroid, 0.05));
		assertEquals(radii[0], ellipsoid.getA(), 0.025);
		assertEquals(radii[1], ellipsoid.getB(), 0.025);
		assertEquals(radii[2], ellipsoid.getC(), 0.025);
		final Matrix4d orientation = new Matrix4d();
		final org.scijava.vecmath.Matrix4d o = ellipsoid.getOrientation();
		orientation.set(o.m00, o.m01, o.m02, o.m03, o.m10, o.m11, o.m12, o.m13,
			o.m20, o.m21, o.m22, o.m23, o.m30, o.m31, o.m32, o.m33);
		epsilonEquals(symmetry, o, 0.025);
	}

	@Test
	public void testNanRadiusIsNotEllipsoid() {
		// This quadric looks like an ellipsoid (3x3 diagonals positive), but it has
		// a NaN radius (negative eigenvalue). These typically result from quadrics
		// solved from sparse data.

		final Matrix4dc nanRadiusEllipsoid = new Matrix4d(0.01085019421630129,
			-0.026230819423660012, -0.0012390257941481408, 0.016336103119147793,
			-0.026230819423660012, 0.02043899336863353, 0.01731688607718951,
			0.03182508790873584, -0.0012390257941481408, 0.01731688607718951,
			0.2516413880666182, 0.0022183414533909485, 0.016336103119147793,
			0.03182508790873584, 0.0022183414533909485, -1.0);

		assertFalse(QuadricToEllipsoid.isEllipsoid(nanRadiusEllipsoid));
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
		final List<Vector3d> points = ellipsoidPoints.calculate(radii, 1_000L)
			.stream().map(p -> new Vector3d(p.x, p.y, p.z)).collect(Collectors
				.toList());
		points.forEach(rotate::mutate);
		points.forEach(p -> p.add(centroid));

		// EXECUTE
		final Matrix4dc quadric = (Matrix4dc) IMAGE_J.op().run(SolveQuadricEq.class,
				points);
		final Ellipsoid transformedEllipsoid = quadricToEllipsoid.calculate(
			quadric);

		// VERIFY
		final org.scijava.vecmath.Vector3d v = transformedEllipsoid.getCentroid();
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

		final Ellipsoid unitSphere = quadricToEllipsoid.calculate(UNIT_SPHERE);

		assertEquals(1.0, unitSphere.getA(), 1e-12);
		assertEquals(1.0, unitSphere.getB(), 1e-12);
		assertEquals(1.0, unitSphere.getC(), 1e-12);
		final org.scijava.vecmath.Vector3d c = unitSphere.getCentroid();
		assertTrue(epsilonEquals(new Vector3d(0, 0, 0), new Vector3d(c.x, c.y, c.z), 1e-12));
		epsilonEquals(expectedOrientation, unitSphere.getOrientation(), 1e-12);
	}

	@BeforeClass
	public static void oneTimeSetup() {
		EllipsoidPoints.setSeed(SEED);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	private void epsilonEquals(final Matrix4dc a,
		final org.scijava.vecmath.Matrix4d b, final double eps)
	{
		assertEquals(a.m00(), b.m00, eps);
		assertEquals(a.m01(), b.m01, eps);
		assertEquals(a.m02(), b.m02, eps);
		assertEquals(a.m03(), b.m03, eps);
		assertEquals(a.m10(), b.m10, eps);
		assertEquals(a.m11(), b.m11, eps);
		assertEquals(a.m12(), b.m12, eps);
		assertEquals(a.m13(), b.m13, eps);
		assertEquals(a.m20(), b.m20, eps);
		assertEquals(a.m21(), b.m21, eps);
		assertEquals(a.m22(), b.m22, eps);
		assertEquals(a.m23(), b.m23, eps);
		assertEquals(a.m30(), b.m30, eps);
		assertEquals(a.m31(), b.m31, eps);
		assertEquals(a.m32(), b.m32, eps);
		assertEquals(a.m33(), b.m33, eps);
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
