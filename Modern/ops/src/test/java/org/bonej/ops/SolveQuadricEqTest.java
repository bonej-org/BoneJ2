
package org.bonej.ops;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import net.imagej.ImageJ;

import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Tests for {@link SolveQuadricEq}.
 *
 * @see org.bonej.ops.ellipsoid.QuadricToEllipsoidTest for more tests related to
 *      quadric equations.
 * @author Richard Domander
 */
public class SolveQuadricEqTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static final double alpha = Math.cos(Math.PI / 4.0);
	private static final List<Vector3d> unitSpherePoints = Stream.of(new Vector3d(
		1, 0, 0), new Vector3d(-1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, -1,
			0), new Vector3d(0, 0, 1), new Vector3d(0, 0, -1), new Vector3d(alpha,
				alpha, 0), new Vector3d(-alpha, alpha, 0), new Vector3d(alpha, -alpha,
					0), new Vector3d(-alpha, -alpha, 0), new Vector3d(0, alpha, alpha),
		new Vector3d(0, -alpha, alpha), new Vector3d(0, alpha, -alpha),
		new Vector3d(0, -alpha, -alpha), new Vector3d(alpha, 0, alpha),
		new Vector3d(alpha, 0, -alpha), new Vector3d(-alpha, 0, alpha),
		new Vector3d(-alpha, 0, -alpha)).collect(toList());
	private static final Matrix4dc solution = (Matrix4dc) IMAGE_J.op().run(
		SolveQuadricEq.class, unitSpherePoints);
	private static final double a = solution.m00();
	private static final double b = solution.m11();
	private static final double c = solution.m22();
	private static final double d = solution.m01();
	private static final double e = solution.m02();
	private static final double f = solution.m12();
	private static final double g = solution.m03();
	private static final double h = solution.m13();
	private static final double i = solution.m23();

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIfTooFewPoints() {
		final List<Vector3d> tooFewPoints = Stream.generate(Vector3d::new).limit(8)
			.collect(toList());

		IMAGE_J.op().run(SolveQuadricEq.class, tooFewPoints);
	}

	@Test
	public void testMatrixElements() {
		assertEquals("The matrix element is incorrect", 1.0, solution.m00(), 1e-12);
		assertEquals("The matrix element is incorrect", 1.0, solution.m11(), 1e-12);
		assertEquals("The matrix element is incorrect", 1.0, solution.m22(), 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, solution.m01(), 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, solution.m02(), 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, solution.m12(), 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, solution.m03(), 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, solution.m13(), 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, solution.m23(), 1e-12);
		final Matrix4d transposed = new Matrix4d();
		solution.transpose(transposed);
		assertEquals("Matrix is not symmetric", solution, transposed);
	}

	@Test
	public void testSolution() {
		for (final Vector3d p : unitSpherePoints) {
			final double polynomial = a * p.x * p.x + b * p.y * p.y + c * p.z * p.z +
				2 * d * p.x * p.y + 2 * e * p.x * p.z + 2 * f * p.y * p.z + 2 * g *
					p.x + 2 * h * p.y + 2 * i * p.z;
			assertEquals("The matrix does not solve the polynomial equation", 1.0,
				polynomial, 1e-12);
		}
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
