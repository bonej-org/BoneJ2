
package org.bonej.ops.mil;

import static java.util.stream.Collectors.toList;
import static org.bonej.ops.mil.StackSamplingGrid.StackSamplingPlane.Orientation.XY;
import static org.bonej.ops.mil.StackSamplingGrid.StackSamplingPlane.Orientation.XZ;
import static org.bonej.ops.mil.StackSamplingGrid.StackSamplingPlane.Orientation.YZ;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import net.imglib2.util.ValuePair;

import org.bonej.ops.mil.StackSamplingGrid.StackSamplingPlane;
import org.junit.Before;
import org.junit.Test;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link StackSamplingGrid} and {@link StackSamplingPlane}
 *
 * @author Richard Domander
 */
public class StackSamplingGridTest {

	private static final Random random = new Random();

	@Before
	public void setup() {
		StackSamplingPlane.setRandomGenerator(random);
	}

	@Test
	public void testSamplingPlaneNormalIsNotReference() throws Exception {
		final StackSamplingPlane plane = new StackSamplingPlane(XY);

		final Vector3d normal = plane.getSamplingLine().b;
		final Vector3d normal2 = plane.getSamplingLine().b;

		assertNotSame("Method returns references", normal, normal2);
	}

	@Test
	public void testSamplingPlaneNormalXY() throws Exception {
		final StackSamplingPlane plane = new StackSamplingPlane(XY);

		final ValuePair<Point3d, Vector3d> line = plane.getSamplingLine();

		assertEquals(new Vector3d(0, 0, 1), line.b);
	}

	@Test
	public void testSamplingPlaneNormalXZ() throws Exception {
		final StackSamplingPlane plane = new StackSamplingPlane(XZ);

		final ValuePair<Point3d, Vector3d> line = plane.getSamplingLine();

		assertEquals(new Vector3d(0, 1, 0), line.b);
	}

	@Test
	public void testSamplingPlaneNormalYZ() throws Exception {
		final StackSamplingPlane plane = new StackSamplingPlane(YZ);

		final ValuePair<Point3d, Vector3d> line = plane.getSamplingLine();

		assertEquals(new Vector3d(1, 0, 0), line.b);
	}

	@Test(expected = NullPointerException.class)
	public void testSamplingPlaneNullOrientationThrowsNPE() throws Exception {
		new StackSamplingPlane(null);
	}

	/**
	 * Tests that line origins lie on the XY-plane when plane orientation is
	 * {@link org.bonej.ops.mil.StackSamplingGrid.StackSamplingPlane.Orientation#XY}
	 */
	@Test
	public void testSamplingPlaneOriginsXY() throws Exception {
		final StackSamplingPlane plane = new StackSamplingPlane(XY);
		final Vector3d normal = new Vector3d(0, 0, 1);

		originTest(plane, normal);
	}

	/**
	 * Tests that line origins lie on the XZ-plane when plane orientation is
	 * {@link org.bonej.ops.mil.StackSamplingGrid.StackSamplingPlane.Orientation#XZ}
	 */
	@Test
	public void testSamplingPlaneOriginsXZ() throws Exception {
		final StackSamplingPlane plane = new StackSamplingPlane(XZ);
		final Vector3d normal = new Vector3d(0, 1, 0);

		originTest(plane, normal);
	}

	/**
	 * Tests that line origins lie on the YZ-plane when plane orientation is
	 * {@link org.bonej.ops.mil.StackSamplingGrid.StackSamplingPlane.Orientation#YZ}
	 */
	@Test
	public void testSamplingPlaneOriginsYZ() throws Exception {
		final StackSamplingPlane plane = new StackSamplingPlane(YZ);
		final Vector3d normal = new Vector3d(1, 0, 0);

		originTest(plane, normal);
	}

	@Test
	public void testSamplingPlaneSetRandomGenerator() throws Exception {
		final Random ones = new OneGenerator();
		final Random zeros = new ZeroGenerator();
		final StackSamplingPlane plane = new StackSamplingPlane(XY);

		StackSamplingPlane.setRandomGenerator(ones);
		final ValuePair<Point3d, Vector3d> line = plane.getSamplingLine();
		assertEquals("Sanity check failed: unexpected line origin", new Point3d(1,
			1, 0), line.a);
		StackSamplingPlane.setRandomGenerator(zeros);
		final ValuePair<Point3d, Vector3d> line2 = plane.getSamplingLine();

		assertNotEquals("Random generator setter had no effect", line.a, line2.a);
	}

	@Test(expected = NullPointerException.class)
	public void testSamplingPlaneSetRandomGeneratorThrowsNPE() throws Exception {
		StackSamplingPlane.setRandomGenerator(null);
	}

	// region -- Helper methods --

	private static void originTest(final StackSamplingPlane plane,
		final Vector3d normal) throws Exception
	{
		final List<Point3d> origins = Stream.generate(plane::getSamplingLine).map(
			ValuePair::getA).limit(1_000).collect(toList());
		final long distinctOrigins = origins.stream().distinct().count();
		assertTrue("Sanity check failed: all points are the same",
			distinctOrigins > 1);

		final BiFunction<Tuple3d, Tuple3d, Double> dot = (o, p) -> p.x * o.x + p.y *
			o.y + p.z * o.z;
		origins.stream().mapToDouble(o -> dot.apply(normal, o)).forEach(
			d -> assertEquals("Origin is not on the plane", 0.0, d, 1e-12));
	}

	// endregion

	// region -- Helper classes --

	private static final class OneGenerator extends Random {

		@Override
		public double nextDouble() {
			return 1.0;
		}
	}

	private static final class ZeroGenerator extends Random {

		@Override
		public double nextDouble() {
			return 0.0;
		}
	}

	// endregion
}
