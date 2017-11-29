
package org.bonej.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for the {@link BoxIntersect} op.
 *
 * @author Richard Domander
 */
public class BoxIntersectTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static BinaryFunctionOp<ValuePair<Tuple3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>> boxIntersect;
	private static Img<BitType> interval;

	@Test
	public void testCalculate() {
		final Point3d origin = new Point3d(2, 2, -1);
		final Vector3d direction = new Vector3d(0, 0, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);
		assertTrue(result.isPresent());
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		assertEquals(1.0, scalars.getA().get(), 1e-11);
		assertEquals(6.0, scalars.getB().get(), 1e-11);
	}

	@Test
	public void testCornerToCornerLine() {
		final Point3d origin = new Point3d(-1, -1, -1);
		final Vector3d direction = new Vector3d(1, 1, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertTrue(result.isPresent());
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		assertEquals(Math.sqrt(3), scalars.a.get(), 1e-11);
		assertEquals(Math.sqrt(6 * 6 * 3), scalars.b.get(), 1e-11);
	}

	@Test
	public void testDirectionIsNotMutated() {
		final Point3d origin = new Point3d(0, 0, -1);
		final Vector3d direction = new Vector3d(0, 0, 3);
		final Vector3d original = new Vector3d(direction);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		boxIntersect.calculate(line);

		assertEquals("Direction vector has changed", original, direction);
	}

	@Test
	public void testDirectionNormalization() {
		final Point3d origin = new Point3d(2, 2, -1);
		final Vector3d direction = new Vector3d(0, 0, 3);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertTrue(result.isPresent());
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		assertEquals("Direction wasn't normalized, or was normalized incorrectly",
			1.0, scalars.getA().get(), 1e-12);
	}

	@Test
	public void testEmptyInterval() {
		final Point3d origin = new Point3d(0, 0, -1);
		final Vector3d direction = new Vector3d(0, 0, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);
		final Img<BitType> emptyInterval = ArrayImgs.bits(0, 0, 0);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line, emptyInterval);

		assertFalse(result.isPresent());
	}

	@Test
	public void testExcludeMaxBounds() {
		final Point3d origin = new Point3d(2, 2, -1);
		final Vector3d direction = new Vector3d(0, 0, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final ValuePair<DoubleType, DoubleType> excludeResult = boxIntersect
			.calculate(line).get();
		@SuppressWarnings("unchecked")
		final ValuePair<DoubleType, DoubleType> includeResult =
			((Optional<ValuePair<DoubleType, DoubleType>>) IMAGE_J.op().run(
				BoxIntersect.class, line, interval, false)).get();

		assertEquals(
			"Max bounds exclusion should not change intersection of min bounds",
			excludeResult.getA().get(), includeResult.getA().get(), 1e-12);
		final double tMaxExc = excludeResult.getB().get();
		final double tMaxInc = includeResult.getB().get();
		assertNotEquals("Scalar for max bounds intersection point should change",
			tMaxExc, tMaxInc, 1e-12);
		final Point3d exit = new Point3d(direction);
		exit.scaleAdd(tMaxExc, origin);
		assertEquals("The floored z-coordinate of the exit point is incorrect", 4,
			(long) exit.z);
		final Point3d exitInc = new Point3d(direction);
		exitInc.scaleAdd(tMaxInc, origin);
		assertEquals(
			"The floored z-coordinate of the exit point (max bounds included) is incorrect",
			5, (long) exitInc.z);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInfiniteDirection() throws Exception {
		final Point3d origin = new Point3d(2, 2, -2);
		final Vector3d direction = new Vector3d(0, 0, Double.POSITIVE_INFINITY);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		boxIntersect.calculate(line);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInfiniteOrigin() throws Exception {
		final Point3d origin = new Point3d(2, 2, Double.NEGATIVE_INFINITY);
		final Vector3d direction = new Vector3d(0, 0, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertTrue(result.isPresent());
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		assertEquals(Double.POSITIVE_INFINITY, scalars.a.get(), 1e-11);
		assertEquals(Double.POSITIVE_INFINITY, scalars.b.get(), 1e-11);
	}

	@Test
	public void testLineParallelToStackEdge() {
		final Point3d origin = new Point3d(0, 0, -1);
		final Vector3d direction = new Vector3d(0, 0, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertTrue(result.isPresent());
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		assertEquals(1.0, scalars.a.get(), 1e-11);
		assertEquals(6.0, scalars.b.get(), 1e-11);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIfInterval2D() {
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(new Point3d(2, 2,
			-1), new Vector3d(0, 0, 1));
		final Img<BitType> interval2D = ArrayImgs.bits(5, 5);

		IMAGE_J.op().run(BoxIntersect.class, line, interval2D);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNaNDirection() throws Exception {
		final Point3d origin = new Point3d(2, 2, -1);
		final Vector3d direction = new Vector3d(0, 0, Double.NaN);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		boxIntersect.calculate(line);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNanOrigin() throws Exception {
		final Point3d origin = new Point3d(2, Double.NaN, -1);
		final Vector3d direction = new Vector3d(0, 0, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertFalse(result.isPresent());
	}

	@Test
	public void testOppositeDirection() {
		final Point3d origin = new Point3d(2, 2, -1);
		final Vector3d direction = new Vector3d(0, 0, -1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertTrue(result.isPresent());
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		assertEquals(-1, scalars.a.get(), 1e-11);
		assertEquals(-6, scalars.b.get(), 1e-11);
	}

	// Tests a line that only enters the interval for a short while
	@Test
	public void testShortSegment() {
		final Point3d origin = new Point3d(0.5, 0.6, -0.5);
		final Vector3d direction = new Vector3d(0, -1, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertTrue(result.isPresent());
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		assertEquals(Math.sqrt(2) * 0.5, scalars.a.get(), 1e-11);
		assertEquals(Math.sqrt(2) * 0.6, scalars.b.get(), 1e-11);
	}

	@Test
	public void testXYMissingLine() {
		final Point3d origin = new Point3d(-1, 5, 0);
		final Vector3d direction = new Vector3d(1, 1, 0);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertFalse(result.isPresent());
	}

	@Test
	public void testXZMissingLine() {
		final Point3d origin = new Point3d(-1, 0, 5);
		final Vector3d direction = new Vector3d(1, 0, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertFalse(result.isPresent());
	}

	@Test
	public void testYZMissingLine() {
		final Point3d origin = new Point3d(0, -1, 5);
		final Vector3d direction = new Vector3d(0, 1, 1);
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line);

		assertFalse(result.isPresent());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testZeroLengthDirection() {
		final Point3d origin = new Point3d(2, 2, -2);
		final Vector3d direction = new Vector3d();
		final ValuePair<Tuple3d, Vector3d> line = new ValuePair<>(origin,
			direction);

		boxIntersect.calculate(line);
	}

	@BeforeClass
	@SuppressWarnings("unchecked")
	public static void oneTimeSetup() {
		interval = ArrayImgs.bits(5, 5, 5);
		boxIntersect = (BinaryFunctionOp) Functions.binary(IMAGE_J.op(),
			BoxIntersect.class, Optional.class, ValuePair.class, interval);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
