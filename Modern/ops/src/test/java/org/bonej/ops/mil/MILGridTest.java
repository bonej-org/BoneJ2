
package org.bonej.ops.mil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link MILGrid}.
 *
 * @author Richard Domander
 */
public class MILGridTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static final long SIZE = 5;
	private static final Img<BitType> BG_IMG = ArrayImgs.bits(SIZE, SIZE, SIZE);
	private static final long DEFAULT_BINS = SIZE;
	private static final AxisAngle4d IDENTITY_ROTATION = new AxisAngle4d();
	private static final double DEFAULT_INCREMENT = 1.0;
	private static final Vector3d X_AXIS = new Vector3d(1, 0, 0);
	private static final Vector3d Y_AXIS = new Vector3d(0, 1, 0);
	private static final Vector3d Z_AXIS = new Vector3d(0, 0, 1);
	private static final long SEED = 0xc0ff33;
	
	// Each test creates its own random generator with a constant seed. This is
	// because called through IMAGE_J.op().run(), the ops run in different
	// threads, thus making the tests non-deterministic if they shared a
	// generator.
	private final BiPredicate<Vector3d, Vector3d> isParallel = (u, v) -> {
		final Vector3d product = new Vector3d();
		product.cross(u, v);
		final Vector3d zeroVector = new Vector3d(0, 0, 0);
		return product.equals(zeroVector);
	};

	/**
	 * Tests that changing the bins parameter changes the number of vectors
	 * created
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testBinsParameter() {
		final long expectedBaseline = SIZE * SIZE * SIZE * DEFAULT_BINS *
			DEFAULT_BINS * 3;
		final long expectedPlusOne = SIZE * SIZE * SIZE * (DEFAULT_BINS + 1) *
			(DEFAULT_BINS + 1) * 3;

		final Long defaultBinsSamples = (Long) ((ArrayList<Object>) IMAGE_J.op()
			.run(MILGrid.class, BG_IMG, IDENTITY_ROTATION, DEFAULT_BINS,
				DEFAULT_INCREMENT, SEED)).get(1);
		final Long plusOneBinsSamples = (Long) ((ArrayList<Object>) IMAGE_J.op()
			.run(MILGrid.class, BG_IMG, IDENTITY_ROTATION, DEFAULT_BINS + 1,
				DEFAULT_INCREMENT, SEED)).get(1);

		assertEquals("Sanity check failed: baseline value unexpected",
			expectedBaseline, defaultBinsSamples.longValue());
		assertEquals("Incrementing bins had no effect on the number of samples",
			expectedPlusOne, plusOneBinsSamples.longValue());
	}

	@Test
	public void testEmptyInterval() {
		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors =
			(List<Vector3d>) ((ArrayList<Object>) IMAGE_J.op().run(MILGrid.class,
				BG_IMG, IDENTITY_ROTATION, DEFAULT_BINS, DEFAULT_INCREMENT, SEED)).get(0);

		assertEquals("Wrong Number of vectors", 27, milVectors.size());
		final long xParallel = milVectors.stream().filter(v -> isParallel.test(v,
			X_AXIS)).count();
		assertEquals(9, xParallel);
		final long yParallel = milVectors.stream().filter(v -> isParallel.test(v,
			Y_AXIS)).count();
		assertEquals(xParallel, yParallel);
		final long zParallel = milVectors.stream().filter(v -> isParallel.test(v,
			Z_AXIS)).count();
		assertEquals(yParallel, zParallel);
		milVectors.stream().filter(v -> isParallel.test(v, X_AXIS)).forEach(
			yzNormal -> assertTrue(yzNormal.epsilonEquals(new Vector3d(-SIZE, 0, 0),
				1e-11)));
		milVectors.stream().filter(v -> isParallel.test(v, Y_AXIS)).forEach(
			xzNormal -> assertTrue(xzNormal.epsilonEquals(new Vector3d(0, -SIZE, 0),
				1e-11)));
		milVectors.stream().filter(v -> isParallel.test(v, Z_AXIS)).forEach(
			xyNormal -> assertTrue(xyNormal.epsilonEquals(new Vector3d(0, 0, SIZE),
				1e-11)));
	}

	/**
	 * Tests that changing the increment parameter changes the number samples
	 * taken.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testIncrementParameter() {
		// SETUP
		final long expectedBaseline = SIZE * SIZE * SIZE * DEFAULT_BINS *
			DEFAULT_BINS * 3;
		final long expectedDoubleIncrement = (long) ((SIZE * 0.5) * (SIZE * 0.5) *
			(SIZE * 0.5) * DEFAULT_BINS * DEFAULT_BINS * 3);

		// EXECUTE
		final Long defaultBinsSamples = (Long) ((ArrayList<Object>) IMAGE_J.op()
			.run(MILGrid.class, BG_IMG, IDENTITY_ROTATION, DEFAULT_BINS,
				DEFAULT_INCREMENT, SEED)).get(1);
		final Long plusOneBinsSamples = (Long) ((ArrayList<Object>) IMAGE_J.op()
			.run(MILGrid.class, BG_IMG, IDENTITY_ROTATION, DEFAULT_BINS,
				DEFAULT_INCREMENT * 2, SEED)).get(1);

		// VERIFY
		assertEquals("Sanity check failed: baseline value unexpected",
			expectedBaseline, defaultBinsSamples.longValue());
		assertEquals(
			"Doubling the increment had no effect on the number of samples",
			expectedDoubleIncrement, plusOneBinsSamples.longValue());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIf2DInterval() {
		final Img<BitType> img = ArrayImgs.bits(5, 5);
		IMAGE_J.op().run(MILGrid.class, img);
	}

	/**
	 * Tests that changing the rotation parameter changes the orientation of MIL
	 * vectors (in comparison to {@link #testEmptyInterval()})
	 */
	@Test
	public void testRotationParameter() {
		final AxisAngle4d rotation = new AxisAngle4d(0, 1, 1, Math.PI / 4.0);

		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors =
			(List<Vector3d>) ((ArrayList<Object>) IMAGE_J.op().run(MILGrid.class,
				BG_IMG, rotation, DEFAULT_BINS, DEFAULT_INCREMENT, SEED)).get(0);

		assertTrue("Changing the rotation parameter had no effect", milVectors
			.stream().noneMatch(v -> v.equals(new Vector3d(0, 0, 1))));
	}

	/**
	 * Tests the op with an image of xy-aligned "sheets". Vectors in z-direction
	 * should be shorter than in x or y.
	 */
	@Test
	public void testXYSheets() {
		// SETUP
		final Vector3d zAxis = new Vector3d(0, 0, 1);
		final long size = 100;
		final long expectedInterceptions = 20;
		final double expectedZLength = 1.0 * size / expectedInterceptions;
		final Img<BitType> sheets = ArrayImgs.bits(size, size, size);
		for (long z = 5; z < size; z += 10) {
			final IntervalView<BitType> sheet = Views.interval(sheets, new long[] { 0,
				0, z }, new long[] { size - 1, size - 1, z });
			sheet.cursor().forEachRemaining(BitType::setOne);
		}

		// EXECUTE
		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors =
			(List<Vector3d>) ((ArrayList<Object>) IMAGE_J.op().run(MILGrid.class,
				sheets, IDENTITY_ROTATION, DEFAULT_BINS, DEFAULT_INCREMENT, SEED)).get(0);

		// VERIFY
		final Stream<Vector3d> zVectors = milVectors.stream().filter(v -> isParallel
			.test(v, zAxis));
		zVectors.forEach(v -> assertEquals(expectedZLength, v.length(), 1e-12));
		final Stream<Vector3d> otherVectors = milVectors.stream().filter(
			v -> !isParallel.test(v, zAxis));
		otherVectors.forEach(v -> assertEquals(
			"Length should be equal to stack size", size, v.length(), 1e-12));
	}

	@Test
	public void testXZSheets() {
		// SETUP
		final long size = 100;
		final long expectedInterceptions = 10;
		final double expectedLength = size / expectedInterceptions;
		final Img<BitType> sheets = ArrayImgs.bits(size, size, size);
		for (long y = 5; y < size; y += 20) {
			final IntervalView<BitType> sheet = Views.interval(sheets, new long[] { 0,
				y, 0 }, new long[] { size - 1, y, size - 1 });
			sheet.cursor().forEachRemaining(BitType::setOne);
		}

		// EXECUTE
		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors =
			(List<Vector3d>) ((ArrayList<Object>) IMAGE_J.op().run(MILGrid.class,
				sheets, IDENTITY_ROTATION, DEFAULT_BINS, DEFAULT_INCREMENT, SEED)).get(0);

		// VERIFY
		final Stream<Vector3d> yVectors = milVectors.stream().filter(v -> isParallel
			.test(v, new Vector3d(0, 1, 0)));
		yVectors.forEach(v -> assertEquals(
			"An XZ-normal vector has unexpected length", expectedLength, v.length(),
			1e-12));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testThrowsIAEIfIncrementTooSmall() {
		IMAGE_J.op().run(MILGrid.class, BG_IMG, IDENTITY_ROTATION, DEFAULT_BINS,
			1e-12, SEED);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
