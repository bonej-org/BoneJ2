
package org.bonej.ops.mil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
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
	private static final long EXPECTED_SIZE = 27;
	private static final AxisAngle4d IDENTITY_ROTATION = new AxisAngle4d();
	private static final double DEFAULT_INCREMENT = 1.0;

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
	 * created (in comparison to {@link #testEmptyInterval()})
	 */
	@Test
	public void testBinsParameter() throws Exception {
		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors = (List<Vector3d>) IMAGE_J.op().run(
			MILGrid.class, BG_IMG, DEFAULT_BINS + 1L, DEFAULT_INCREMENT,
			IDENTITY_ROTATION, new Random(0xc0ff33));

		assertTrue("The bins parameter had no effect", EXPECTED_SIZE < milVectors
			.size());
	}

	/**
	 * Tests the op on a cube. Most vectors should enter and exit it, i.e. their
	 * length should be 0.5.
	 */
	@Test
	public void testCube() throws Exception {
		final Img<BitType> cube = ArrayImgs.bits(100, 100, 100);
		final IntervalView<BitType> foreground = Views.interval(cube, new long[] {
			1, 1, 1 }, new long[] { 98, 98, 98 });
		foreground.cursor().forEachRemaining(BitType::setOne);
		final double expectedLength = 1.0 / 2.0;

		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors = (List<Vector3d>) IMAGE_J.op().run(
			MILGrid.class, cube, 10L, DEFAULT_INCREMENT, IDENTITY_ROTATION,
			new Random(0xc0ff33));

		// Some vectors will always miss the object, so not all vectors will have
		// length < 1.0
		assertEquals("Regression test failed: number of vectors changed", 95,
			milVectors.size());
		assertEquals(
			"Regression test failed: number of vectors that have intercepted the cube changed",
			91, milVectors.stream().filter(v -> v.length() == expectedLength)
				.count());
	}

	@Test
	public void testEmptyInterval() throws Exception {
		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors = (List<Vector3d>) IMAGE_J.op().run(
			MILGrid.class, BG_IMG, DEFAULT_BINS, DEFAULT_INCREMENT, IDENTITY_ROTATION,
			new Random(0xc0ff33));

		assertEquals("Regression test failed: number of vectors unexpected",
			EXPECTED_SIZE, milVectors.size());
		assertTrue(
			"Regression test failed: identity rotation should create vectors normal to the XY-plane",
			milVectors.stream().anyMatch(v -> v.equals(new Vector3d(0, 0, 1))));
		assertTrue("All vectors should have length 1.0", milVectors.stream()
			.allMatch(v -> v.length() == 1.0));
	}

	/**
	 * Tests that changing the increment parameter changes the number of vectors
	 * that have a certain number of interceptions in comparison to
	 * {@link #testXYSheets()}. The increasing the increment should move the
	 * sample points so that most of the vectors encounter the sheets less often.
	 */
	@Test
	public void testIncrementParameter() throws Exception {
		// SETUP
		final long numSheets = 10;
		final Img<BitType> sheets = drawXYSheets();

		// EXECUTE
		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors = (List<Vector3d>) IMAGE_J.op().run(
			MILGrid.class, sheets, DEFAULT_BINS, 1.1, IDENTITY_ROTATION, new Random(
				0xc0ff33));

		// VERIFY
		final Stream<Vector3d> zVectors = milVectors.stream().filter(v -> isParallel
			.test(v, new Vector3d(0, 0, 1)));
		assertEquals(
			"Regression test failed: changing the increment parameter had no effect",
			2, zVectors.filter(v -> v.length() == 1.0 / (2 * numSheets)).count());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIf2DInterval() throws Exception {
		final Img<BitType> img = ArrayImgs.bits(5, 5);
		IMAGE_J.op().run(MILGrid.class, img);
	}

	/**
	 * Tests that changing the rotation parameter changes the orientation of MIL
	 * vectors (in comparison to {@link #testEmptyInterval()})
	 */
	@Test
	public void testRotationParameter() throws Exception {
		final AxisAngle4d rotation = new AxisAngle4d(0, 1, 1, Math.PI / 4.0);

		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors = (List<Vector3d>) IMAGE_J.op().run(
			MILGrid.class, BG_IMG, DEFAULT_BINS, DEFAULT_INCREMENT, rotation,
			new Random(0xc0ff33));

		assertTrue("Changing the rotation parameter had no effect", milVectors
			.stream().noneMatch(v -> v.equals(new Vector3d(0, 0, 1))));
	}

	/**
	 * Tests the op with an image of xy-aligned "sheets". Vectors in z-direction
	 * should be shorter than in x or y.
	 */
	@Test
	public void testXYSheets() throws Exception {
		// SETUP
		final int numSheets = 10;
		final Vector3d zAxis = new Vector3d(0, 0, 1);
		final double expectedZLength = 1.0 / (numSheets * 2);
		final Img<BitType> sheets = drawXYSheets();

		// EXECUTE
		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors = (List<Vector3d>) IMAGE_J.op().run(
			MILGrid.class, sheets, DEFAULT_BINS, DEFAULT_INCREMENT, IDENTITY_ROTATION,
			new Random(0xc0ff33));

		// VERIFY
		final Stream<Vector3d> zVectors = milVectors.stream().filter(v -> isParallel
			.test(v, zAxis));
		assertTrue("MIL vectors in the Z-direction have unexpected length", zVectors
			.allMatch(v -> v.length() == expectedZLength));
		final Stream<Vector3d> otherVectors = milVectors.stream().filter(
			v -> !isParallel.test(v, zAxis));
		assertTrue("All MIL vectors in X- Y-directions should have length 1.0",
			otherVectors.allMatch(v -> v.length() == 1.0));
	}

	@Test
	public void testXZSheets() throws Exception {
		// SETUP
		final Img<BitType> sheets = ArrayImgs.bits(100, 100, 100);
		// Draw 19 XZ sheets
		final long numSheets = 19;
		for (long y = 5; y < 100; y += 5) {
			final IntervalView<BitType> sheet = Views.interval(sheets, new long[] { 0,
				y, 0 }, new long[] { 99, y, 99 });
			sheet.cursor().forEachRemaining(BitType::setOne);
		}

		// EXECUTE
		@SuppressWarnings("unchecked")
		final List<Vector3d> milVectors = (List<Vector3d>) IMAGE_J.op().run(
			MILGrid.class, sheets, DEFAULT_BINS, DEFAULT_INCREMENT, IDENTITY_ROTATION,
			new Random(0xc0ff33));

		// VERIFY
		final Stream<Vector3d> yVectors = milVectors.stream().filter(v -> isParallel
			.test(v, new Vector3d(0, 1, 0)));
		assertTrue("MIL vectors in the Y-direction have unexpected length", yVectors
			.allMatch(v -> v.length() == 1.0 / (2 * numSheets)));
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	// region -- Helper methods --

	private static Img<BitType> drawXYSheets() {
		final Img<BitType> sheets = ArrayImgs.bits(100, 100, 100);
		long z = 5;
		for (int i = 0; i < 10; i++) {
			final IntervalView<BitType> sheet = Views.interval(sheets, new long[] { 0,
				0, z }, new long[] { 99, 99, z });
			sheet.cursor().forEachRemaining(BitType::setOne);
			z += 10;
		}
		return sheets;
	}

	// endregion
}
