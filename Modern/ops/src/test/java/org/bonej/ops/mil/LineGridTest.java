
package org.bonej.ops.mil;

import static java.util.stream.Collectors.toList;
import static org.bonej.ops.mil.LineGrid.LinePlane.Orientation.XY;
import static org.bonej.ops.mil.LineGrid.LinePlane.Orientation.XZ;
import static org.bonej.ops.mil.LineGrid.LinePlane.Orientation.YZ;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.util.ValuePair;

import org.bonej.ops.RotateAboutAxis;
import org.bonej.ops.mil.LineGrid.LinePlane;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link LineGrid} and {@link LinePlane}
 *
 * @author Richard Domander
 */
public class LineGridTest {

	private static final Random random = new Random();
	private static final ImageJ IMAGE_J = new ImageJ();
	private static final BiFunction<Tuple3d, Tuple3d, Double> dot = (o,
		p) -> p.x * o.x + p.y * o.y + p.z * o.z;
	private static BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp;

	@Before
	public void setup() {
		LinePlane.setRandomGenerator(random);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorThrowsIAEIfIntervalLT3D() throws Exception {
		final Img<BitType> img = ArrayImgs.bits(5, 5);

		new LineGrid(img);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructorThrowsIAEIfZeroDimension() throws Exception {
		final Img<BitType> img = ArrayImgs.bits(5, 5, 0);

		new LineGrid(img);
	}

	@Test(expected = NullPointerException.class)
	public void testConstructorThrowsNPEIfIntervalNull() throws Exception {
		new LineGrid(null);
	}

	@Test
	public void testFlipPlanes() {
		// SETUP
		final long side = 10;
		final double planeSize = Math.sqrt(side * side * 3);
		final double halfPlane = planeSize * 0.5;
		final Vector3d centroid = new Vector3d(side * 0.5, side * 0.5, side * 0.5);
		final Point3d expectedXYOrigin = new Point3d(planeSize, planeSize, 0);
		expectedXYOrigin.add(new Vector3d(-halfPlane, -halfPlane, halfPlane));
		final Point3d expectedXZOrigin = new Point3d(planeSize, 0, planeSize);
		expectedXZOrigin.add(new Vector3d(-halfPlane, halfPlane, -halfPlane));
		final Point3d expectedYZOrigin = new Point3d(0, planeSize, planeSize);
		expectedYZOrigin.add(new Vector3d(halfPlane, -halfPlane, -halfPlane));
		Stream.of(expectedXYOrigin, expectedXZOrigin, expectedYZOrigin).forEach(
			o -> o.add(centroid));
		final Img<BitType> img = ArrayImgs.bits(side, side, side);
		final LineGrid grid = new LineGrid(img);
		grid.setRandomGenerator(new OneGenerator());
		grid.flipPlanes();

		// EXECUTE
		final ValuePair<Point3d, Vector3d> xyLine = grid.nextLine();
		final ValuePair<Point3d, Vector3d> xzLine = grid.nextLine();
		final ValuePair<Point3d, Vector3d> yzLine = grid.nextLine();

		// VERIFY
		assertEquals("Origin wasn't mirrored properly", expectedXYOrigin, xyLine.a);
		assertEquals("Normal wasn't mirrored properly", new Vector3d(0, 0, -1),
			xyLine.b);
		assertEquals("Origin wasn't mirrored properly", expectedXZOrigin, xzLine.a);
		assertEquals("Normal wasn't mirrored properly", new Vector3d(0, -1, 0),
			xzLine.b);
		assertEquals("Origin wasn't mirrored properly", expectedYZOrigin, yzLine.a);
		assertEquals("Normal wasn't mirrored properly", new Vector3d(-1, 0, 0),
			yzLine.b);
	}

	@Test
	public void testGridLineSequence() {
		final Img<BitType> img = ArrayImgs.bits(1, 1, 1);
		final LineGrid grid = new LineGrid(img);

		grid.nextLine();
		grid.nextLine();
		grid.nextLine();
		final Vector3d normal = grid.nextLine().b;

		assertEquals("The line should have come from the xy-plane again",
			new Vector3d(0, 0, 1), normal);
	}

	@Test
	public void testGridPlanes() {
		final Img<BitType> img = ArrayImgs.bits(1, 1, 1);
		final LineGrid grid = new LineGrid(img);

		final Vector3d xyNormal = grid.nextLine().b;
		final Vector3d xzNormal = grid.nextLine().b;
		final Vector3d yzNormal = grid.nextLine().b;

		assertEquals("First line should be normal to the xy-plane", new Vector3d(0,
			0, 1), xyNormal);
		assertEquals("Second line should be normal to the xz-plane", new Vector3d(0,
			1, 0), xzNormal);
		assertEquals("Third line should be normal to the yz-plane", new Vector3d(1,
			0, 0), yzNormal);
	}

	@Test
	public void testGridRotation() {
		// SETUP
		final long side = 10;
		final Img<BitType> img = ArrayImgs.bits(side, side, side);
		final double planeSize = Math.sqrt(side * side * 3);
		final Vector3d centroid = new Vector3d(side * 0.5, side * 0.5, side * 0.5);
		final Vector3d translation = new Vector3d(-0.5 * planeSize, -0.5 *
			planeSize, -0.5 * planeSize);
		final Vector3d axis = new Vector3d(0, 0, 1);
		final double angle = Math.PI / 4.0;
		final AxisAngle4d rotation = new AxisAngle4d(axis, angle);
		final Point3d expectedXYOrigin = new Point3d(planeSize, planeSize, 0);
		final Point3d expectedXZOrigin = new Point3d(planeSize, 0, planeSize);
		final Point3d expectedYZOrigin = new Point3d(0, planeSize, planeSize);
		Stream.of(expectedXYOrigin, expectedXZOrigin, expectedYZOrigin).forEach(
			o -> {
				rotateOp.mutate1(o, rotation);
				o.add(translation);
				o.add(centroid);
			});
		final Vector3d expectedXYNormal = new Vector3d(0, 0, 1);
		final Vector3d expectedXZNormal = new Vector3d(0, 1, 0);
		final Vector3d expectedYZNormal = new Vector3d(1, 0, 0);
		Stream.of(expectedXYNormal, expectedXZNormal, expectedYZNormal).forEach(
			n -> rotateOp.mutate1(n, rotation));
		final LineGrid grid = new LineGrid(img);
		grid.setRandomGenerator(new OneGenerator());
		grid.setRotation(rotation, IMAGE_J.op());

		// EXECUTE
		final ValuePair<Point3d, Vector3d> xyLine = grid.nextLine();
		final ValuePair<Point3d, Vector3d> xzLine = grid.nextLine();
		final ValuePair<Point3d, Vector3d> yzLine = grid.nextLine();

		// VERIFY
		assertTrue("Rotation incorrect", expectedXYOrigin.epsilonEquals(xyLine.a,
			1e-12));
		assertTrue("Rotation incorrect", expectedXYNormal.epsilonEquals(xyLine.b,
			1e-12));
		assertTrue("Rotation incorrect", expectedXZOrigin.epsilonEquals(xzLine.a,
			1e-12));
		assertTrue("Rotation incorrect", expectedXZNormal.epsilonEquals(xzLine.b,
			1e-12));
		assertTrue("Rotation incorrect", expectedYZOrigin.epsilonEquals(yzLine.a,
			1e-12));
		assertTrue("Rotation incorrect", expectedYZNormal.epsilonEquals(yzLine.b,
			1e-12));
	}

	@Test(expected = NullPointerException.class)
	public void testGridSetRandomGeneratorThrowsNPE() {
		new LineGrid(null);
	}

	@Test(expected = NullPointerException.class)
	public void testGridSetRotationThrowsNPEIfOpsNull() {
		final Img<BitType> img = ArrayImgs.bits(1, 1, 1);
		final LineGrid grid = new LineGrid(img);

		grid.setRotation(new AxisAngle4d(), null);
	}

	@Test(expected = NullPointerException.class)
	public void testGridSetRotationThrowsNPEIfRotationNull() {
		final Img<BitType> img = ArrayImgs.bits(1, 1, 1);
		final LineGrid grid = new LineGrid(img);

		grid.setRotation(null, IMAGE_J.op());
	}

	@Test
	public void testLinePlaneNormalIsNotReference() throws Exception {
		final LinePlane plane = new LinePlane(XY);

		final Vector3d normal = plane.getLine().b;
		final Vector3d normal2 = plane.getLine().b;

		assertNotSame("Method returns references", normal, normal2);
	}

	@Test
	public void testLinePlaneNormalXY() throws Exception {
		final LinePlane plane = new LinePlane(XY);

		final ValuePair<Point3d, Vector3d> line = plane.getLine();

		assertEquals(new Vector3d(0, 0, 1), line.b);
	}

	@Test
	public void testLinePlaneNormalXZ() throws Exception {
		final LinePlane plane = new LinePlane(XZ);

		final ValuePair<Point3d, Vector3d> line = plane.getLine();

		assertEquals(new Vector3d(0, 1, 0), line.b);
	}

	@Test
	public void testLinePlaneNormalYZ() throws Exception {
		final LinePlane plane = new LinePlane(YZ);

		final ValuePair<Point3d, Vector3d> line = plane.getLine();

		assertEquals(new Vector3d(1, 0, 0), line.b);
	}

	@Test(expected = NullPointerException.class)
	public void testLinePlaneNullOrientationThrowsNPE() throws Exception {
		new LinePlane(null);
	}

	@Test
	public void testLinePlaneOriginScaling() throws Exception {
		LinePlane.setRandomGenerator(new OneGenerator());
		final LinePlane plane = new LinePlane(XY, 3.0);

		final Point3d origin = plane.getLine().a;

		assertEquals(new Point3d(3, 3, 0), origin);
	}

	/**
	 * Tests that line origins lie on the XY-plane when plane orientation is
	 * {@link LinePlane.Orientation#XY}
	 */
	@Test
	public void testLinePlaneOriginsXY() throws Exception {
		final LinePlane plane = new LinePlane(XY);
		final Vector3d normal = new Vector3d(0, 0, 1);

		originTest(plane, normal);
	}

	/**
	 * Tests that line origins lie on the XZ-plane when plane orientation is
	 * {@link LinePlane.Orientation#XZ}
	 */
	@Test
	public void testLinePlaneOriginsXZ() throws Exception {
		final LinePlane plane = new LinePlane(XZ);
		final Vector3d normal = new Vector3d(0, 1, 0);

		originTest(plane, normal);
	}

	/**
	 * Tests that line origins lie on the YZ-plane when plane orientation is
	 * {@link LinePlane.Orientation#YZ}
	 */
	@Test
	public void testLinePlaneOriginsYZ() throws Exception {
		final LinePlane plane = new LinePlane(YZ);
		final Vector3d normal = new Vector3d(1, 0, 0);

		originTest(plane, normal);
	}

	@Test
	public void testLinePlaneSetRandomGenerator() throws Exception {
		final Random ones = new OneGenerator();
		final Random zeros = new ZeroGenerator();
		final LinePlane plane = new LinePlane(XY);

		LinePlane.setRandomGenerator(ones);
		final ValuePair<Point3d, Vector3d> line = plane.getLine();
		assertEquals("Sanity check failed: unexpected line origin", new Point3d(1,
			1, 0), line.a);
		LinePlane.setRandomGenerator(zeros);
		final ValuePair<Point3d, Vector3d> line2 = plane.getLine();

		assertNotEquals("Random generator setter had no effect", line.a, line2.a);
	}

	@Test(expected = NullPointerException.class)
	public void testLinePlaneSetRandomGeneratorThrowsNPE() throws Exception {
		LinePlane.setRandomGenerator(null);
	}

	@Test
	public void testLinePlaneSetRotation() {
		// SETUP

		LinePlane.setRandomGenerator(new OneGenerator());
		final LinePlane plane = new LinePlane(XY);
		final Vector3d axis = new Vector3d(1, 0, 1);
		axis.normalize();
		final double angle = Math.PI / 4.0;
		final AxisAngle4d rotation = new AxisAngle4d(axis, angle);
		final Point3d expectedOrigin = new Point3d(1, 1, 0);
		IMAGE_J.op().run(RotateAboutAxis.class, expectedOrigin, expectedOrigin,
			rotation);
		final Vector3d expectedNormal = new Vector3d(0, 0, 1);
		IMAGE_J.op().run(RotateAboutAxis.class, expectedNormal, expectedNormal,
			rotation);
		plane.setRotation(rotation, IMAGE_J.op());

		// EXECUTE
		final ValuePair<Point3d, Vector3d> line = plane.getLine();

		// VERIFY
		assertTrue("Origin rotated incorrectly", expectedOrigin.epsilonEquals(
			line.a, 1e-12));
		assertEquals("Normal rotated incorrectly", expectedNormal, line.b);
	}

	@Test(expected = NullPointerException.class)
	public void testLinePlaneSetRotationThrowsNPEIfOpEnvironmentNull() {
		final LinePlane plane = new LinePlane(XY);

		plane.setRotation(new AxisAngle4d(), null);
	}

	@Test(expected = NullPointerException.class)
	public void testLinePlaneSetRotationThrowsNPEIfRotationNull() {
		final LinePlane plane = new LinePlane(XY);

		plane.setRotation(null, IMAGE_J.op());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLinePlaneThrowsIAEIfScalarNotFinite() {
		new LinePlane(XY, Double.NaN);
	}

	@Test
	public void testNextLine() {
		// SETUP
		final long side = 10;
		final Img<BitType> img = ArrayImgs.bits(side, side, side);
		final LineGrid grid = new LineGrid(img);
		grid.setRandomGenerator(new OneGenerator());
		final double planeSize = Math.sqrt(side * side * 3);
		final Vector3d centroid = new Vector3d(side * 0.5, side * 0.5, side * 0.5);
		final Vector3d translation = new Vector3d(-0.5 * planeSize, -0.5 *
			planeSize, -0.5 * planeSize);
		final Point3d expectedXYOrigin = new Point3d(planeSize, planeSize, 0);
		final Point3d expectedXZOrigin = new Point3d(planeSize, 0, planeSize);
		final Point3d expectedYZOrigin = new Point3d(0, planeSize, planeSize);
		Stream.of(expectedXYOrigin, expectedXZOrigin, expectedYZOrigin).forEach(
			o -> {
				o.add(translation);
				o.add(centroid);
			});

		// EXECUTE
		final Point3d xyOrigin = grid.nextLine().a;
		final Point3d xzOrigin = grid.nextLine().a;
		final Point3d yzOrigin = grid.nextLine().a;

		// VERIFY
		assertEquals(
			"Unexpected origin - nextLine() scales and/or translates incorrectly",
			expectedXYOrigin, xyOrigin);
		assertEquals(
			"Unexpected origin - nextLine() scales and/or translates incorrectly",
			expectedXZOrigin, xzOrigin);
		assertEquals(
			"Unexpected origin - nextLine() scales and/or translates incorrectly",
			expectedYZOrigin, yzOrigin);
	}

	@BeforeClass
	public static void oneTimeSetup() {
		rotateOp = Hybrids.binaryCFI1(IMAGE_J.op(), RotateAboutAxis.class,
			Tuple3d.class, new Vector3d(), new AxisAngle4d());
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	// region -- Helper methods --

	private static void originTest(final LinePlane plane, final Vector3d normal)
		throws Exception
	{
		final List<Point3d> origins = Stream.generate(plane::getLine).map(
			ValuePair::getA).limit(1_000).collect(toList());
		final long distinctOrigins = origins.stream().distinct().count();
		assertTrue("Sanity check failed: all points are the same",
			distinctOrigins > 1);

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
