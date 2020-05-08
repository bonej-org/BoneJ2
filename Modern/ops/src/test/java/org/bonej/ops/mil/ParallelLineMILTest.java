/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.ops.mil;

import static org.bonej.ops.mil.PlaneParallelLineGenerator.createFromInterval;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.imagej.ImageJ;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tests for {@link ParallelLineMIL}.
 *
 * @author Richard Domander
 */
public class ParallelLineMILTest {

	private static ImageJ IMAGE_J = new ImageJ();
	private static final long SIZE = 100;
	private static Img<BitType> XY_SHEETS = ArrayImgs.bits(SIZE, SIZE, SIZE);
	private static final Quaterniondc IDENTITY_ROTATION = new Quaterniond();
	private static BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;

	@Test
	public void testMILLengthParameter() {
		// SETUP
		final double milLength = 200.0;
		final double milLength2 = 400.0;
		final Img<BitType> emptyStack = ArrayImgs.bits(100, 100, 100);
		final ParallelLineGenerator generator =
				createFromInterval(emptyStack, IDENTITY_ROTATION, rotateOp, 2L);

		// EXECUTE
		final Vector3dc milVector = (Vector3dc) IMAGE_J.op().run(ParallelLineMIL.class,
				emptyStack, generator, milLength, 1.0);
		final Vector3dc milVector2 = (Vector3dc) IMAGE_J.op().run(ParallelLineMIL.class,
				emptyStack, generator, milLength2, 1.0);

		// VERIFY
		assertEquals(milLength, milVector.length(), 1e-12);
		assertEquals(milLength2, milVector2.length(), 1e-12);
	}

	/**
	 * Tests that changing the increment parameter has an effect on the result.
	 * <p>
	 * Increasing increment should make the MIL vector longer, because it finds fewer foreground voxels.
	 * </p>
	 */
	@Test
	public void testIncrementParameter() {
		// SETUP
		final double milLength = 100.0;
		final ParallelLineGenerator generator =
				createFromInterval(XY_SHEETS, IDENTITY_ROTATION, rotateOp, 2L);

		// EXECUTE
		final Vector3dc milVector = (Vector3dc) IMAGE_J.op().run(ParallelLineMIL.class,
				XY_SHEETS, generator, milLength, 1.0);
		final Vector3dc milVector2 = (Vector3dc) IMAGE_J.op().run(ParallelLineMIL.class,
				XY_SHEETS, generator, milLength, 4.0);

		// VERIFY
		assertTrue(milVector.length() < milVector2.length());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIf2DInterval() {
		final Img<BitType> img = ArrayImgs.bits(5, 5);
		IMAGE_J.op().run(ParallelLineMIL.class, img);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIf4DInterval() {
		final Img<BitType> img = ArrayImgs.bits(5, 5, 5, 5);
		IMAGE_J.op().run(ParallelLineMIL.class, img);
	}

	/**
	 * Tests the op with an image with a foreground sheet on every other XY-slice.
	 * Since the direction of the lines is (0, 0, 1) they should encounter all of
	 * them and count both in-out and out-in boundaries.
	 * Their length should be image depth / (2 * sheets) = 1.0.
	 */
	@Test
	public void testXYSheets() {
		// SETUP
		final Quaterniond zAxis = new Quaterniond(new AxisAngle4d(0.0, 0, 0, 1));
		final long sections = 2L;
		final ParallelLineGenerator zGenerator =
				createFromInterval(XY_SHEETS, zAxis, rotateOp, sections);
		final double milLength = SIZE * sections * sections;

		// EXECUTE
		final Vector3dc milVector = (Vector3dc) IMAGE_J.op().run(ParallelLineMIL.class,
				XY_SHEETS, zGenerator, milLength, 1.0);

		// VERIFY
		assertEquals(1.0, milVector.length(), 1e-12);
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
		final ConstantZGenerator generator = new ConstantZGenerator();
		final long milLength = 4 * SIZE;
		// Two of the vectors from the constant generator will intercept a sheet, two won't.
		final double expectedLength = milLength / 2.0;

		// EXECUTE
		final Vector3dc milVector = (Vector3dc) IMAGE_J.op().run(ParallelLineMIL.class,
			xzSheets, generator, milLength, 1.0);

		// VERIFY
		assertEquals(expectedLength, milVector.length(), 1e-12);
	}

	// This test is more for consistency than correctness per se
	@Test
	public void testBinaryNoise() {
		// SETUP
		final long seed = 0xc0ff33;
		final Random noiseRNG = new Random();
		noiseRNG.setSeed(seed);
		final double milLength = Math.sqrt(SIZE * SIZE * 3);
		final Img<BitType> binaryNoise = ArrayImgs.bits(SIZE, SIZE, SIZE);
		binaryNoise.forEach(e -> {
			if (noiseRNG.nextDouble() >= 0.5) {
				e.setOne();
			}
		});
		final Quaterniondc rotation = new Quaterniond(new AxisAngle4d(Math.PI / 4.0, 0, 1, 0));
		final PlaneParallelLineGenerator generator =
				createFromInterval(binaryNoise, rotation, rotateOp, 16);
		generator.resetAndSetSeed(seed);
		ParallelLineMIL.setSeed(seed);

		// EXECUTE
		final Vector3dc milVector = (Vector3dc) IMAGE_J.op().run(ParallelLineMIL.class,
				binaryNoise, generator, milLength, 1.0);

		// VERIFY
		assertEquals(1.946124502886379, milVector.length(), 1e-12);
	}

	@Test
	public void testRotateDirection() {
		final Quaterniondc rotation = new Quaterniond(new AxisAngle4d(Math.PI / 4.0, 0, 0, 1));
		final Vector3d expectedDirection = new Vector3d(0, 0, 1);
		rotateOp.mutate1(expectedDirection, rotation);
		final PlaneParallelLineGenerator generator =
				createFromInterval(XY_SHEETS, IDENTITY_ROTATION, rotateOp, 1);

		generator.rotateDirection(rotation);
		assertTrue(generator.getDirection().equals(expectedDirection, 1e-12));

		generator.rotateDirection(rotation);
		assertTrue("Repeating the same rotation should give the same result",
				generator.getDirection().equals(expectedDirection, 1e-12));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRotateDirectionIAEIfQuaternionNotUnit() {
		final PlaneParallelLineGenerator generator =
				createFromInterval(XY_SHEETS, IDENTITY_ROTATION, rotateOp, 1);
		final Quaterniondc badRotation = new Quaterniond(1.0, 2.0, 3.0, 4.0);

		generator.rotateDirection(badRotation);
	}

	@BeforeClass
	public static void oneTimeSetup() {
		rotateOp = Hybrids.binaryCFI1(IMAGE_J.op(), Rotate3d.class, Vector3d.class,
				new Vector3d(), new Quaterniond());
		drawXYSheets();
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
		IMAGE_J = null;
		XY_SHEETS = null;
	}

	// region -- Helper methods --
	private static void drawXYSheets() {
		for (int i = 0; i < SIZE; i += 2) {
			final IntervalView<BitType> view = Views.interval(XY_SHEETS, new long[] { 0,
				0, i }, new long[] { SIZE - 1, SIZE - 1, i });
			view.forEach(BitType::setOne);
		}
	}

	private static class ConstantZGenerator implements ParallelLineGenerator {
		private static final Vector3dc direction = new Vector3d(0, 0, 1);
		private static final List<Line> LINES = new ArrayList<>(4);
		private int cycle;

		static {
			LINES.add(new Line(new Vector3d(1, 1, -1), direction));
			LINES.add(new Line(new Vector3d(4, 4, -1), direction));
			LINES.add(new Line(new Vector3d(7, 7, -1), direction));
			LINES.add(new Line(new Vector3d(10, 10, -1), direction));
		}

		@Override
		public Line nextLine() {
			int index = cycle % LINES.size();
			cycle++;
			return LINES.get(index);
		}

		@Override
		public Vector3dc getDirection() {
			return direction;
		}

		@Override
		public void rotateDirection(final Quaterniondc rotation) {}
	}
	// endregion
}
