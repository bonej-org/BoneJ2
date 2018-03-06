
package org.bonej.ops.mil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link MILPlane}.
 *
 * @author Richard Domander
 */
public class MILPlaneTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static final long SIZE = 100;
	private static final Img<BitType> SHEETS = ArrayImgs.bits(SIZE, SIZE, SIZE);
	private static final AxisAngle4d IDENTITY_ROTATION = new AxisAngle4d();
	private static final Long SEED = 0xc0ffeeL;

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIf2DInterval() {
		final Img<BitType> img = ArrayImgs.bits(5, 5);
		IMAGE_J.op().run(MILPlane.class, img);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIf4DInterval() {
		final Img<BitType> img = ArrayImgs.bits(5, 5, 5, 5);
		IMAGE_J.op().run(MILPlane.class, img);
	}

	/**
	 * Tests that changing the bins parameter changes the op result.
	 * <p>
	 * An image with random noise should produce a longer MIL vector with more
	 * bins, because it's unlikely that that all of them intercept the same number
	 * of foreground voxels.
	 * </p>
	 */
	@Test
	public void testBinsParameter() {
		// SETUP
		final long seed = 0xc0ffee;
		final Img<BitType> noiseImg = ArrayImgs.bits(SIZE, SIZE, SIZE);
		noiseImg.forEach(voxel -> {
			if (Math.random() >= 0.5) {
				voxel.setOne();
			}
		});

		// EXECUTE
		final Vector3d milVector = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			noiseImg, IDENTITY_ROTATION, 1L, 1.0, seed);
		final Vector3d milVector2 = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			noiseImg, IDENTITY_ROTATION, 16L, 1.0, seed);

		// VERIFY
		assertTrue(milVector.length() < milVector2.length());
	}

	/**
	 * Tests that changing the increment parameter has an effect on the result.
	 * <p>
	 * Increasing increment should make the MIL vector longer, because intercepts
	 * fewer foreground voxels.
	 * </p>
	 */
	@Test
	public void testIncrementParameter() {
		// EXECUTE
		final Vector3d milVector = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			SHEETS, IDENTITY_ROTATION, 2L, 1.0, SEED);
		final Vector3d milVector2 = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			SHEETS, IDENTITY_ROTATION, 2L, 4.0, SEED);

		// VERIFY
		assertTrue(milVector.length() < milVector2.length());
	}

	@Test
	public void testRotationParameter() {
		final AxisAngle4d rotation = new AxisAngle4d(0, 1, 0, Math.PI / 2.0);
		final Vector3d expectedDirection = new Vector3d(1, 0, 0);

		// EXECUTE
		final Vector3d milVector = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			SHEETS, rotation, 2L, 1.0, SEED);

		assertTrue("Changing the rotation parameter had no effect", isParallel(
			expectedDirection, milVector));
	}

	/**
	 * Tests the op with an image with a foreground sheet on every other XY-slice.
	 * Since the direction of the lines is (0, 0, 1) they should encounter all of
	 * them. Their length should be image depth / sheets = 2.0.
	 */
	@Test
	public void testXYSheets() {
		final Vector3d milVector = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			SHEETS, IDENTITY_ROTATION, 2L, 1.0, SEED);

		assertEquals(2.0, milVector.length(), 1e-12);
	}

	/**
	 * Tests the op with an image with a foreground sheet on every other XZ-slice.
	 * Since the direction of the lines is (0, 1, 0) they should encounter at most
	 * one foreground object, and thus MIL vector should have the same length as
	 * the height of the image.
	 */
	@Test
	public void testXZSheets() {
		// SETUP
		final Img<BitType> xzSheets = ArrayImgs.bits(SIZE, SIZE, SIZE);
		for (int y = 0; y < SIZE; y += 2) {
			final IntervalView<BitType> view = Views.interval(xzSheets, new long[] {
				0, y, 0 }, new long[] { SIZE - 1, y, SIZE - 1 });
			view.forEach(BitType::setOne);
		}

		// EXECUTE
		final Vector3d milVector = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			xzSheets, IDENTITY_ROTATION, 2L, 1.0, SEED);

		// VERIFY
		assertEquals(SIZE, milVector.length(), 1e-12);
	}

	@Test
	public void testSeedParameter() {
		// SETUP
		final long seed2 = 0x70ffee;
		// Drawing lines in an angle where they're likely to encounter different
		// number of sheets based on where they start
		final AxisAngle4d rotation = new AxisAngle4d(1, 1, 0, Math.PI / 3.0);

		// EXECUTE
		final Vector3d milVector = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			SHEETS, rotation, 4L, 1.0, SEED);
		final Vector3d milVector2 = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			SHEETS, rotation, 4L, 1.0, SEED);
		final Vector3d milVector3 = (Vector3d) IMAGE_J.op().run(MILPlane.class,
			SHEETS, rotation, 4L, 1.0, seed2);

		// VERIFY
		assertEquals("Same seed should produce the same result", milVector.length(),
			milVector2.length(), 1e-12);
		assertNotEquals("Different seeds should produce different results",
			milVector.length(), milVector3.length(), 1e-12);
	}

	@BeforeClass
	public static void oneTimeSetup() {
		drawXYSheets();
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	// region -- Helper methods --
	private static void drawXYSheets() {
		for (int i = 0; i < SIZE; i += 2) {
			final IntervalView<BitType> view = Views.interval(SHEETS, new long[] { 0,
				0, i }, new long[] { SIZE - 1, SIZE - 1, i });
			view.forEach(BitType::setOne);
		}
	}

	private boolean isParallel(final Vector3d u, final Vector3d v) {
		final Vector3d product = new Vector3d();
		product.cross(u, v);
		final Vector3d zeroVector = new Vector3d(0, 0, 0);
		return product.epsilonEquals(zeroVector, 1e-12);
	}
	// endregion
}
