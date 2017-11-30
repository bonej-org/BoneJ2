
package org.bonej.ops.mil;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.imagej.ImageJ;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.util.ValuePair;

import org.bonej.ops.RotateAboutAxis;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link LineGrid}.
 *
 * @author Richard Domander
 */
public class LineGridTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp;
	private static long side = 10;
	private static double gridSize = Math.sqrt(side * side * 3);
	private static final double halfGrid = gridSize / 2.0;
	private static final Vector3d expectedCentroid = new Vector3d(side * 0.5,
		side * 0.5, side * 0.5);
	private static final Img<BitType> img = ArrayImgs.bits(side, side, side);
	private static final Vector3d xAxis = new Vector3d(1, 0, 0);
	private static final Vector3d yAxis = new Vector3d(0, 1, 0);
	private static final Vector3d zAxis = new Vector3d(0, 0, 1);

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
	public void testRandomReflection() throws Exception {
		// SETUP
		final LineGrid grid = new LineGrid(img);
		grid.setRandomGenerator(new OneGenerator());
		final Point3d expectedXYOrigin = new Point3d(halfGrid, halfGrid, halfGrid);
		expectedXYOrigin.add(expectedCentroid);
		final Vector3d expectedXYDirection = new Vector3d(zAxis);
		expectedXYDirection.negate();
		final Point3d expectedXZOrigin = new Point3d(halfGrid, halfGrid, halfGrid);
		expectedXZOrigin.add(expectedCentroid);
		final Vector3d expectedXZDirection = new Vector3d(yAxis);
		expectedXZDirection.negate();
		final Point3d expectedYZOrigin = new Point3d(halfGrid, halfGrid, halfGrid);
		expectedYZOrigin.add(expectedCentroid);
		final Vector3d expectedYZDirection = new Vector3d(xAxis);
		expectedYZDirection.negate();

		// EXECUTE
		grid.randomReflection();
		final List<ValuePair<Point3d, Vector3d>> lines = grid.lines(1).collect(
			toList());

		// VERIFY
		assertEquals("Origin mirrored incorrectly", expectedXYOrigin, lines.get(
			0).a);
		assertEquals("Direction mirrored incorrectly", expectedXYDirection, lines
			.get(0).b);
		assertEquals("Origin mirrored incorrectly", expectedXZOrigin, lines.get(
			1).a);
		assertEquals("Direction mirrored incorrectly", expectedXZDirection, lines
			.get(1).b);
		assertEquals("Origin mirrored incorrectly", expectedYZOrigin, lines.get(
			2).a);
		assertEquals("Direction mirrored incorrectly", expectedYZDirection, lines
			.get(2).b);
	}

	/**
	 * Tests that the grid generates lines correctly:
	 * <ul>
	 * <li>Planes are in XY, XZ, YZ order.</li>
	 * <li>Lines have their origin points on these planes.</li>
	 * <li>Directions of lines are normal to these planes.</li>
	 * <li>Planes are correctly translated to the grid.</li>
	 * </ul>
	 */
	@Test
	public void testLines() throws Exception {
		// SETUP
		final LineGrid grid = new LineGrid(img);
		grid.setRandomGenerator(new OneGenerator());
		// We expect origins to be at the "top-right" corner of their planes. In
		// case of the xy-plane the generator will always create a (1, 1, 0), which
		// then gets scaled, centered on the origin and translated to the grid.
		// Same for the other two planes.
		final Point3d expectedXYOrigin = new Point3d(halfGrid, halfGrid, -halfGrid);
		expectedXYOrigin.add(expectedCentroid);
		final Point3d expectedXZOrigin = new Point3d(halfGrid, -halfGrid, halfGrid);
		expectedXZOrigin.add(expectedCentroid);
		final Point3d expectedYZOrigin = new Point3d(-halfGrid, halfGrid, halfGrid);
		expectedYZOrigin.add(expectedCentroid);

		// EXECUTE
		final List<ValuePair<Point3d, Vector3d>> lines = grid.lines(1).collect(
			toList());

		// VERIFY
		assertEquals("Origin is incorrect", expectedXYOrigin, lines.get(0).a);
		assertEquals("Direction is incorrect", zAxis, lines.get(0).b);
		assertEquals("Origin is incorrect", expectedXZOrigin, lines.get(1).a);
		assertEquals("Direction is incorrect", yAxis, lines.get(1).b);
		assertEquals("Origin is incorrect", expectedYZOrigin, lines.get(2).a);
		assertEquals("Direction is incorrect", xAxis, lines.get(2).b);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLinesNonPositiveBinsThrowsIAE() throws Exception {
		final LineGrid grid = new LineGrid(img);

		grid.lines(0);
	}

	/** Test that lines are generated correctly with multiple bins. */
	@Test
	public void testLinesNBins() throws Exception {
		// SETUP
		final long bins = 10;
		final long binsPerPlane = bins * bins;
		final long expectedLines = binsPerPlane * 3;
		final LineGrid grid = new LineGrid(img);
		grid.setRandomGenerator(new OneGenerator());
		// Max coordinate of the first "bin"
		final double binMax = gridSize * (1.0 / bins);
		final Point3d expectedXYOrigin = new Point3d(binMax - halfGrid, binMax -
			halfGrid, -halfGrid);
		expectedXYOrigin.add(expectedCentroid);
		final Point3d expectedXZOrigin = new Point3d(binMax - halfGrid, -halfGrid,
			binMax - halfGrid);
		expectedXZOrigin.add(expectedCentroid);
		final Point3d expectedYZOrigin = new Point3d(-halfGrid, binMax - halfGrid,
			binMax - halfGrid);
		expectedYZOrigin.add(expectedCentroid);

		// EXECUTE
		final List<ValuePair<Point3d, Vector3d>> lines = grid.lines(bins).collect(
			toList());

		// VERIFY
		assertEquals("Wrong number of lines", expectedLines, lines.size());
		assertEquals("Wrong number of lines normal to the xy-plane", binsPerPlane,
			lines.stream().map(line -> line.b).filter(zAxis::equals).count());
		assertEquals("Wrong number of lines normal to the xz-plane", binsPerPlane,
			lines.stream().map(line -> line.b).filter(yAxis::equals).count());
		assertEquals("Wrong number of lines normal to the yz-plane", binsPerPlane,
			lines.stream().map(line -> line.b).filter(xAxis::equals).count());
		assertEquals("Incorrect xy-origin", expectedXYOrigin, lines.get(0).a);
		assertEquals("Incorrect xz-origin", expectedXZOrigin, lines.get(
			(int) binsPerPlane).a);
		assertEquals("Incorrect yz-origin", expectedYZOrigin, lines.get(
			(int) (binsPerPlane * 2)).a);
	}

	@Test
	public void testSetRotation() throws Exception {
		// SETUP
		final AxisAngle4d rotation = new AxisAngle4d(0, 0, 1, Math.PI / 4.0);
		final LineGrid grid = new LineGrid(img);
		grid.setRandomGenerator(new OneGenerator());
		grid.setRotation(rotateOp, rotation);
		final Point3d expectedXYOrigin = new Point3d(halfGrid, halfGrid, -halfGrid);
		rotateOp.mutate1(expectedXYOrigin, rotation);
		expectedXYOrigin.add(expectedCentroid);
		final Vector3d expectedXYDirection = new Vector3d(zAxis);
		rotateOp.mutate1(expectedXYDirection, rotation);
		final Point3d expectedXZOrigin = new Point3d(halfGrid, -halfGrid, halfGrid);
		rotateOp.mutate1(expectedXZOrigin, rotation);
		expectedXZOrigin.add(expectedCentroid);
		final Vector3d expectedXZDirection = new Vector3d(yAxis);
		rotateOp.mutate1(expectedXZDirection, rotation);
		final Point3d expectedYZOrigin = new Point3d(-halfGrid, halfGrid, halfGrid);
		rotateOp.mutate1(expectedYZOrigin, rotation);
		expectedYZOrigin.add(expectedCentroid);
		final Vector3d expectedYZDirection = new Vector3d(xAxis);
		rotateOp.mutate1(expectedYZDirection, rotation);

		// EXECUTE
		final List<ValuePair<Point3d, Vector3d>> lines = grid.lines(1).collect(
			toList());

		// VERIFY
		assertEquals("Origin rotated incorrectly", expectedXYOrigin, lines.get(
			0).a);
		assertEquals("Direction rotated incorrectly", expectedXYDirection, lines
			.get(0).b);
		assertEquals("Origin rotated incorrectly", expectedXZOrigin, lines.get(
			1).a);
		assertEquals("Direction rotated incorrectly", expectedXZDirection, lines
			.get(1).b);
		assertEquals("Origin rotated incorrectly", expectedYZOrigin, lines.get(
			2).a);
		assertEquals("Direction rotated incorrectly", expectedYZDirection, lines
			.get(2).b);
	}

	@Test
	public void testSetRandomGenerator() throws Exception {
		final LineGrid grid = new LineGrid(img);

		grid.setRandomGenerator(new OneGenerator());
		final Iterator<Point3d> origins = grid.lines(1).limit(1).map(line -> line.a)
			.iterator();
		grid.setRandomGenerator(new ZeroGenerator());
		final Iterator<Point3d> origins2 = grid.lines(1).limit(1).map(
			line -> line.a).iterator();

		assertNotEquals("Setter had no effect", origins.next(), origins2.next());

	}

	@Test(expected = NullPointerException.class)
	public void testSetRandomGeneratorThrowsNPE() throws Exception {
		final LineGrid grid = new LineGrid(img);

		grid.setRandomGenerator(null);
	}

	@Test(expected = NullPointerException.class)
	public void testSetRotationThrowsNPEIfOpNull() throws Exception {
		final Img<BitType> img = ArrayImgs.bits(1, 1, 1);
		final LineGrid grid = new LineGrid(img);

		grid.setRotation(null, new AxisAngle4d());
	}

	@Test(expected = NullPointerException.class)
	public void testSetRotationThrowsNPEIfRotationNull() throws Exception {
		final Img<BitType> img = ArrayImgs.bits(1, 1, 1);
		final LineGrid grid = new LineGrid(img);

		grid.setRotation(rotateOp, null);
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

	// region -- Helper classes --

	private static final class OneGenerator extends Random {

		@Override
		public double nextDouble() {
			return 1.0;
		}

		@Override
		public boolean nextBoolean() {
			return true;
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
