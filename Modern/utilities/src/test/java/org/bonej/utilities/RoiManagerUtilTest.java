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

package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import org.joml.Vector3d;
import org.junit.BeforeClass;
import org.junit.Test;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Unit tests for the {@link RoiManagerUtil RoiManagerUtil} class.
 *
 * @author Richard Domander
 */
public class RoiManagerUtilTest {

	private static final int MIN_Z_INDEX = 4;
	private static final int MAX_Z_INDEX = 5;
	private static ImageStack testStack;

	/**
	 * A test for copying from source stack to target stack with a mask
	 * Complements testCropStack(), because I don't know how to set up a
	 * ImageStack with a mask on one of its slices.
	 */
	@Test
	public void testCopyRoiWithMask() {
		final int WIDTH = 10;
		final int HEIGHT = 10;
		final int TEST_COLOR = 0x20;
		final int TEST_COLOR_COUNT = 75;
		final ImageProcessor mask = createLMask(WIDTH, HEIGHT);

		// Set up mock RoiManager
		final Roi roi = new Roi(0, 0, WIDTH, HEIGHT);
		roi.setName("0001-0000-0001");
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(new Roi[] { roi });
		when(MOCK_ROI_MANAGER.getCount()).thenReturn(1);
		when(MOCK_ROI_MANAGER.getSliceNumber(anyString())).thenCallRealMethod();

		// Set up mock ImageStack
		final ImageProcessor mockProcessor = mock(ImageProcessor.class);
		when(mockProcessor.get(anyInt(), anyInt())).thenReturn(TEST_COLOR);
		when(mockProcessor.getMask()).thenReturn(mask);
		when(mockProcessor.getWidth()).thenReturn(WIDTH);
		when(mockProcessor.getHeight()).thenReturn(HEIGHT);
		when(mockProcessor.createProcessor(WIDTH, HEIGHT)).thenReturn(
			new ByteProcessor(WIDTH, HEIGHT));

		final ImageStack mockStack = mock(ImageStack.class);
		when(mockStack.getWidth()).thenReturn(WIDTH);
		when(mockStack.getHeight()).thenReturn(HEIGHT);
		when(mockStack.getSize()).thenReturn(1);
		when(mockStack.getProcessor(anyInt())).thenReturn(mockProcessor);
		when(mockStack.getBitDepth()).thenReturn(8);

		// Assert results
		final Optional<ImageStack> optionalResult = RoiManagerUtil.cropToRois(
			MOCK_ROI_MANAGER, mockStack, false, 0x00);
		assertTrue("Optional should not be empty", optionalResult.isPresent());
		final ImageStack result = optionalResult.get();

		final int foregroundCount = countColorPixels(result, TEST_COLOR);
		assertEquals("Image was cropped incorrectly", TEST_COLOR_COUNT,
			foregroundCount);
	}

	@Test(expected = NullPointerException.class)
	public void testCropToRoisThrowsNPEIfRoiManNull() {
		final ImageStack mockStack = mock(ImageStack.class);
		RoiManagerUtil.cropToRois(null, mockStack,  false, 0);
	}

	@Test(expected = NullPointerException.class)
	public void testCropToRoisThrowsNPEIfStackNull() {
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		RoiManagerUtil.cropToRois(MOCK_ROI_MANAGER, null,  false, 0);
	}

	@Test
	public void testCropStack() {
		final int WIDTH = 6;
		final int HEIGHT = 3;
		final int DEPTH = 3;
		final int ROI_WIDTH = 2;
		final int ROI_HEIGHT = 2;
		final int TEST_COLOR_COUNT = 8;
		final byte TEST_COLOR = 0x40;
		final byte BACKGROUND_COLOR = 0x00;
		final int BACKGROUND_COLOR_COUNT = 46;

		final Roi roi1 = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		roi1.setName("0002-0000-0001");

		final Roi roi2 = new Roi(6, 3, ROI_WIDTH, ROI_HEIGHT);
		roi2.setName("0003-0000-0001");

		final Roi noColorRoi = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		noColorRoi.setName("0001-0000-0001");

		final Roi[] rois = { noColorRoi, roi1, roi2 };
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getCount()).thenReturn(rois.length);
		when(MOCK_ROI_MANAGER.getSliceNumber(anyString())).thenCallRealMethod();
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(rois);

		final int CUBE_SIDE = 10;
		final ImagePlus image = createCuboid(CUBE_SIDE, CUBE_SIDE, CUBE_SIDE,
			TEST_COLOR, 1);
		final ImageStack originalStack = image.getStack();

		// All valid ROIs (basic cropping test)
		final Optional<ImageStack> optionalResult = RoiManagerUtil.cropToRois(
			MOCK_ROI_MANAGER, originalStack, false, 0x00, 0);
		final ImageStack resultStack = optionalResult.get();
		assertEquals("Cropped stack has wrong width", WIDTH, resultStack
			.getWidth());
		assertEquals("Cropped stack has wrong height", HEIGHT, resultStack
			.getHeight());
		assertEquals("Cropped stack has wrong depth", DEPTH, resultStack.getSize());

		final int foregroundCount = countColorPixels(resultStack, TEST_COLOR);
		assertEquals("Cropped area has wrong amount of foreground color",
			TEST_COLOR_COUNT, foregroundCount);

		final int backgroundCount = countColorPixels(resultStack, BACKGROUND_COLOR);
		assertEquals("Cropped area has wrong amount of background color",
			BACKGROUND_COLOR_COUNT, backgroundCount);
	}

	@Test
	public void testCropStackFillColor() {
		// Mock a RoiManager and create a test image
		final int TEST_COLOR_COUNT = 8;
		final byte TEST_COLOR = 0x40;
		final byte BACKGROUND_COLOR = 0x00;
		final int BACKGROUND_COLOR_COUNT = 46;
		final int ORIGINAL_BG_COLOR_COUNT = 4;
		final int FILL_COLOR_COUNT = BACKGROUND_COLOR_COUNT -
			ORIGINAL_BG_COLOR_COUNT;
		final byte FILL_COLOR = 0x10;
		final ImageStack stack = createCuboid(10, 10, 10, TEST_COLOR, 1).getStack();
		final Roi roi1 = new Roi(2, 2, 2, 2);
		final Roi roi2 = new Roi(6, 3, 2, 2);
		final Roi roi3 = new Roi(2, 2, 2, 2);
		final Roi rois[] = { roi3, roi1, roi2 };
		roi1.setName("0002-0000-0001");
		roi2.setName("0003-0000-0001");
		roi3.setName("0001-0000-0001");
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getCount()).thenReturn(rois.length);
		when(MOCK_ROI_MANAGER.getSliceNumber(anyString())).thenCallRealMethod();
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(rois);

		final Optional<ImageStack> optional = RoiManagerUtil.cropToRois(
			MOCK_ROI_MANAGER, stack, true, FILL_COLOR, 0);
		final ImageStack result = optional.get();

		final int foregroundCount = countColorPixels(result, TEST_COLOR);
		assertEquals("Cropped area has wrong amount of foreground color",
			TEST_COLOR_COUNT, foregroundCount);
		final int backgroundCount = countColorPixels(result, BACKGROUND_COLOR);
		assertEquals("Cropped area has wrong amount of original background color",
			ORIGINAL_BG_COLOR_COUNT, backgroundCount);
		final int fillCount = countColorPixels(result, FILL_COLOR);
		assertEquals("Cropped area has wrong amount of background fill color",
			FILL_COLOR_COUNT, fillCount);
	}

	@Test
	public void testCropStackWithPadding() {
		// Mock a RoiManager
		final int WIDTH = 6;
		final int HEIGHT = 3;
		final int DEPTH = 3;
		final int PADDING = 2;
		final int ROI_WIDTH = 2;
		final int ROI_HEIGHT = 2;
		final int TOTAL_PADDING = 2 * PADDING;
		final Roi roi1 = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		final Roi roi2 = new Roi(6, 3, ROI_WIDTH, ROI_HEIGHT);
		final Roi roi3 = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		final Roi rois[] = { roi3, roi1, roi2 };
		roi1.setName("0002-0000-0001");
		roi2.setName("0003-0000-0001");
		roi3.setName("0001-0000-0001");
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getCount()).thenReturn(rois.length);
		when(MOCK_ROI_MANAGER.getSliceNumber(anyString())).thenCallRealMethod();
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(rois);

		// Create a test image
		final ImageStack stack = createCuboid(10, 10, 10, (byte) 0xFF, 0)
			.getStack();
		final ImageStack unpadded = RoiManagerUtil.cropToRois(MOCK_ROI_MANAGER,
			stack, false, 0x00, 0).get();

		final ImageStack result = RoiManagerUtil.cropToRois(MOCK_ROI_MANAGER, stack,
			false, 0x00, PADDING).get();

		assertEquals("Cropped stack has wrong padded width", WIDTH + TOTAL_PADDING,
			result.getWidth());
		assertEquals("Cropped stack has wrong padded height", HEIGHT +
			TOTAL_PADDING, result.getHeight());
		assertEquals("Cropped stack has wrong padded depth", DEPTH + TOTAL_PADDING,
			result.getSize());
		assertTrue("Padding didn't shift the pixels correctly", pixelsShifted(
			unpadded, result, PADDING));
	}

	@Test
	public void testGetLimits() {
		// Mock a RoiManager with two Rois
		final int ROI1_X = 10;
		final int ROI1_Y = 10;
		final int ROI1_WIDTH = 30;
		final int ROI1_HEIGHT = 60;
		final int ROI2_X = 20;
		final int ROI2_Y = 5;
		final int ROI2_WIDTH = 40;
		final int ROI2_HEIGHT = 30;
		final int ROI1_Z = 2;
		final int ROI2_Z = 3;
		final String roi1Label = "000" + ROI1_Z + "-0000-0001";
		final String roi2Label = "000" + ROI2_Z + "-0000-0001";
		final Roi roi1 = new Roi(ROI1_X, ROI1_Y, ROI1_WIDTH, ROI1_HEIGHT);
		final Roi roi2 = new Roi(ROI2_X, ROI2_Y, ROI2_WIDTH, ROI2_HEIGHT);
		final Roi rois[] = { roi1, roi2 };
		roi1.setName(roi1Label);
		roi2.setName(roi2Label);
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getSliceNumber(anyString())).thenCallRealMethod();
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(rois);
		when(MOCK_ROI_MANAGER.getCount()).thenReturn(rois.length);

		final Optional<int[]> result = RoiManagerUtil.getLimits(MOCK_ROI_MANAGER,
			testStack);

		assertTrue(result.isPresent());
		final int[] limitsResult = result.get();
		assertEquals(6, limitsResult.length);
		assertEquals(ROI1_X, limitsResult[0]);
		assertEquals(ROI2_X + ROI2_WIDTH, limitsResult[1]);
		assertEquals(ROI2_Y, limitsResult[2]);
		assertEquals(ROI1_Y + ROI1_HEIGHT, limitsResult[3]);
		assertEquals(ROI1_Z, limitsResult[MIN_Z_INDEX]);
		assertEquals(ROI2_Z, limitsResult[MAX_Z_INDEX]);
	}

	@Test
	public void testGetLimitsAllActiveSlide() {
		// Mock a RoiManager with Rois
		final Roi roi = new Roi(0, 0, 10, 10);
		final Roi allActive = new Roi(0, 0, 10, 10);
		final Roi rois[] = { roi, allActive };
		roi.setName("0001-0000-0001");
		allActive.setName("ALL_ACTIVE");
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getSliceNumber(anyString())).thenCallRealMethod();
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(rois);
		when(MOCK_ROI_MANAGER.getCount()).thenReturn(rois.length);

		final Optional<int[]> result = RoiManagerUtil.getLimits(MOCK_ROI_MANAGER,
			testStack);
		assertTrue(result.isPresent());
		final int[] limits = result.get();
		assertEquals(1, limits[MIN_Z_INDEX]);
		assertEquals(testStack.size(), limits[MAX_Z_INDEX]);
	}

	@Test
	public void testGetLimitsIgnoresBadRois() {
		// Mock a RoiManager with invalid Rois
		final Roi farZRoi = new Roi(10, 10, 10, 10);
		final Roi badRoi = new Roi(-100, -100, 10, 10);
		final Roi rois[] = { farZRoi, badRoi };
		farZRoi.setName("9999-0000-0001"); // slice no == 9999
		badRoi.setName("0001-0000-0001");
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getSliceNumber(anyString())).thenCallRealMethod();
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(rois);
		when(MOCK_ROI_MANAGER.getCount()).thenReturn(rois.length);

		final Optional<int[]> result = RoiManagerUtil.getLimits(MOCK_ROI_MANAGER,
			testStack);

		assertFalse(result.isPresent());
	}

	@Test
	public void testGetLimitsReturnEmptyIfRoiManagerEmpty() {
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);

		final Optional<int[]> result = RoiManagerUtil.getLimits(MOCK_ROI_MANAGER,
			testStack);

		assertFalse(result.isPresent());
	}

	@Test
	public void testGetSafeRoiBounds() {
		final int X = 10;
		final int Y = 10;
		final int WIDTH = testStack.getWidth() + 100;
		final int HEIGHT = testStack.getHeight() + 100;
		final Rectangle tooLargeRectangle = new Rectangle(X, Y, WIDTH, HEIGHT);

		final boolean result = RoiManagerUtil.getSafeRoiBounds(tooLargeRectangle,
			testStack.getWidth(), testStack.getHeight());

		assertTrue("Rectangle should have been cropped OK", result);
		assertEquals("Rectangle X should not have changed", X, tooLargeRectangle.x);
		assertEquals("Rectangle Y should not have changed", Y, tooLargeRectangle.y);
		assertEquals("Rectangle width is incorrect", testStack.getWidth() - X,
			tooLargeRectangle.width);
		assertEquals("Rectangle height is incorrect", testStack.getHeight() - Y,
			tooLargeRectangle.height);
	}

	@Test
	public void testGetSafeRoiBoundsInvalidRoi() {
		final Rectangle rectangle = new Rectangle(-10, -10, 5, 5);

		final boolean result = RoiManagerUtil.getSafeRoiBounds(rectangle, testStack
			.getWidth(), testStack.getHeight());

		assertFalse("A rectangle out of bounds should be invalid", result);
	}

	@Test
	public void testGetSliceRoi() {
		// Mock a RoiManager with several Rois
		// RoiManager.getSliceNumber tries to parse the number of the slice from the
		// label of the Roi
		final String singleRoiLabel = "0003-0000-0001";
		final String multiRoi1Label = "0004-0000-0001";
		final String multiRoi2Label = "0004-0000-0002";
		final String noSliceLabel = "ALL_SLIDES";
		final Roi otherSliceRoi = new Roi(10, 10, 10, 10);
		final Roi sliceRoi1 = new Roi(10, 10, 10, 10);
		final Roi sliceRoi2 = new Roi(30, 30, 10, 10);
		final Roi allSliceRoi = new Roi(50, 50, 10, 10);
		final Roi rois[] = { otherSliceRoi, sliceRoi1, sliceRoi2, allSliceRoi };
		otherSliceRoi.setName(singleRoiLabel);
		sliceRoi1.setName(multiRoi1Label);
		sliceRoi2.setName(multiRoi2Label);
		allSliceRoi.setName(noSliceLabel);
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getSliceNumber(anyString())).thenCallRealMethod();
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(rois);

		final List<Roi> result = RoiManagerUtil.getSliceRoi(MOCK_ROI_MANAGER,
			testStack, 4);

		assertEquals("Wrong number of ROIs returned", 3, result.size());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", multiRoi1Label,
			result.get(0).getName());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", multiRoi2Label,
			result.get(1).getName());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", noSliceLabel,
			result.get(2).getName());
	}

	@Test
	public void testGetSliceRoiReturnEmptyListIfInvalidNumber() {
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		final List<Roi> result = RoiManagerUtil.getSliceRoi(MOCK_ROI_MANAGER,
			testStack, -3);

		assertTrue("Out of bounds slice number should return no ROIs", result
			.isEmpty());
	}

	@Test
	public void testGetSliceRoiReturnEmptyListIfRoiManagerNull() {
		final List<Roi> result = RoiManagerUtil.getSliceRoi(null, testStack, 1);

		assertTrue(result.isEmpty());
	}

	@Test
	public void testGetSliceRoiReturnEmptyListIfStackNull() {
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		final List<Roi> result = RoiManagerUtil.getSliceRoi(MOCK_ROI_MANAGER, null,
			1);

		assertTrue(result.isEmpty());
	}

	@Test
	public void testIsActiveOnAllSlices() {
		assertTrue(RoiManagerUtil.isActiveOnAllSlices(-1));
		assertTrue(RoiManagerUtil.isActiveOnAllSlices(0));
		assertFalse(RoiManagerUtil.isActiveOnAllSlices(1));
	}

	@Test
	public void testPointRoiCoordinates() {
		final PointRoi pointRoi = new PointRoi(8, 9);
		pointRoi.setPosition(13);
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(new Roi[] { new Roi(1, 2,
			1, 1), pointRoi, new TextRoi(3, 4, "foo") });

		final List<Vector3d> points = RoiManagerUtil.pointROICoordinates(
			MOCK_ROI_MANAGER);

		assertEquals(1, points.size());
		final Vector3d point = points.get(0);
		assertEquals(pointRoi.getXBase(), point.x, 1e-12);
		assertEquals(pointRoi.getYBase(), point.y, 1e-12);
		assertEquals(pointRoi.getPosition(), point.z, 1e-12);
	}

	// Tests that the method filters out duplicate points within and between
	// multi-point ROIs. Duplicate points have the same (x, y, z) coordinates.
	@Test
	public void testPointRoiCoordinatesMultiPointRois() {
		final PointRoi roi = new PointRoi(new int[] { 1, 1, 2 }, new int[] { 0, 0,
			0 }, 3);
		roi.setPosition(1);
		final PointRoi roi2 = new PointRoi(new int[] { 1, 1 }, new int[] { 0, 0 },
			2);
		roi2.setPosition(2);
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(new Roi[] { roi, roi2 });

		final List<Vector3d> result = RoiManagerUtil.pointROICoordinates(
			MOCK_ROI_MANAGER);

		assertEquals(3, result.size());
		assertEquals(new Vector3d(1, 0, 1), result.get(0));
		assertEquals(new Vector3d(2, 0, 1), result.get(1));
		assertEquals(new Vector3d(1, 0, 2), result.get(2));
	}

	@Test(expected = NullPointerException.class)
	public void testPointRoiCoordinatesThrowsNPEIfRoiManagerNull() {
		RoiManagerUtil.pointROICoordinates(null);
	}

	@BeforeClass
	public static void oneTimeSetUp() {
		final ImagePlus image = NewImage.createByteImage("testImage", 100, 100, 4,
			0);
		testStack = image.getStack();
	}

	// region -- Helper methods --

	/**
	 * Counts the number of pixels that have the given color in all the slices of
	 * the given stack
	 *
	 * @param stack The stack to inspect
	 * @param color The color to be searched
	 * @return The number of pixels that match the color
	 */
	private static int countColorPixels(final ImageStack stack, final int color) {
		int count = 0;
		final int height = stack.getHeight();
		final int width = stack.getWidth();

		for (int z = 1; z <= stack.getSize(); z++) {
			final byte[] pixels = (byte[]) stack.getPixels(z);
			for (int y = 0; y < height; y++) {
				final int offset = y * width;
				for (int x = 0; x < width; x++) {
					if (pixels[offset + x] == color) {
						count++;
					}
				}
			}
		}

		return count;
	}

	private ImagePlus createCuboid(final int width, final int height,
		final int depth, final byte color, final int padding)
	{
		final int totalPadding = 2 * padding;
		final int paddedWidth = width + totalPadding;
		final int paddedHeight = height + totalPadding;
		final int paddedDepth = depth + totalPadding;

		final ImagePlus imagePlus = IJ.createImage("Cuboid", "8black", paddedWidth,
			paddedHeight, paddedDepth);
		final ImageStack cuboidStack = imagePlus.getStack();

		final int firstCuboidSlice = padding + 1;
		final int lastCuboidSlice = padding + depth;
		ImageProcessor cuboidProcessor;
		for (int i = firstCuboidSlice; i <= lastCuboidSlice; i++) {
			cuboidProcessor = cuboidStack.getProcessor(i);
			cuboidProcessor.setColor(color);
			cuboidProcessor.setRoi(padding, padding, width, height);
			cuboidProcessor.fill();
		}

		return imagePlus;
	}

	/**
	 * Creates an L-shaped mask that blocks the lower right-hand corner of an
	 * image NB width & height need to be the same than the dimensions of the
	 * image this mask is used on.
	 *
	 * @param width Width of the mask
	 * @param height Height of the mask
	 * @return An ImageProcessor that can be passed to ImageProcessor#setMask
	 */
	private ImageProcessor createLMask(final int width, final int height) {
		final ImageProcessor mask = new ByteProcessor(width, height);
		final ImageProcessor tmp = new ByteProcessor(width, height);

		final Polygon polygon = new Polygon();
		polygon.addPoint(0, 0);
		polygon.addPoint(width, 0);
		polygon.addPoint(width, height / 2);
		polygon.addPoint(width / 2, height / 2);
		polygon.addPoint(width / 2, height);
		polygon.addPoint(0, height);
		polygon.addPoint(0, 0);
		tmp.setRoi(polygon);

		mask.setPixels(tmp.getMask().getPixels());
		return mask;
	}

	/**
	 * Checks that padding has moved all of the pixels to correct coordinates
	 *
	 * @param croppedStack The cropped image without padding
	 * @param paddedStack The same image with padding
	 * @param padding number of padding pixels on each side of paddedStack
	 * @return true if all the pixels have shifted the correct amount
	 */
	private static boolean pixelsShifted(final ImageStack croppedStack,
		final ImageStack paddedStack, final int padding)
	{
		for (int z = 1; z <= croppedStack.getSize(); z++) {
			final ImageProcessor sourceProcessor = croppedStack.getProcessor(z);
			final int targetZ = z + padding;
			final ImageProcessor targetProcessor = paddedStack.getProcessor(targetZ);
			for (int y = 0; y < croppedStack.getHeight(); y++) {
				final int targetY = y + padding;
				for (int x = 0; x < croppedStack.getWidth(); x++) {
					final int targetX = x + padding;
					final int sourceColor = sourceProcessor.get(x, y);
					final int targetColor = targetProcessor.get(targetX, targetY);
					if (sourceColor != targetColor) {
						return false;
					}
				}
			}
		}
		return true;
	}
	// endregion
}
