package org.bonej.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Polygon;
import java.util.ArrayList;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

/**
 * Unit tests for the org.doube.util.RoiMan class
 *
 * @author Richard Domander
 */
public class RoiManTest {
	RoiManager mockRoiManager;
	private static ImagePlus testImage;
	private static ImageStack testStack;

	private static final int TEST_IMAGE_WIDTH = 100;
	private static final int TEST_IMAGE_HEIGHT = 100;
	private static final int TEST_IMAGE_DEPTH = 4;

	private static final int MIN_Z_INDEX = 4;
	private static final int MAX_Z_INDEX = 5;

	@BeforeClass
	public static void oneTimeSetUp() {
		IJ.newImage("testImage", "8-bit", TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT, TEST_IMAGE_DEPTH);
		testImage = IJ.getImage();
		testStack = testImage.getStack();
	}

	@AfterClass
	public static void oneTimeTearDown() {
		if (testImage != null) {
			testImage.flush();
			testImage.close();
			testImage = null;
			testStack = null;
		}
	}

	@Before
	public void setUp() throws Exception {
		mockRoiManager = mock(RoiManager.class);
	}

	@Test
	public void testGetSliceRoiReturnsEmptyListIfRoiManagerIsNull() throws Exception {
		final ArrayList<Roi> resultRois = RoiMan.getSliceRoi(null, testStack, 1);
		assertEquals(true, resultRois.isEmpty());
	}

	@Test
	public void testGetSliceRoiReturnsEmptyListIfStackIsNull() throws Exception {
		final ArrayList<Roi> resultRois = RoiMan.getSliceRoi(mockRoiManager, null, 1);
		assertEquals(true, resultRois.isEmpty());
	}

	@Test
	public void testGetSliceRoiReturnsEmptyListSliceIsOutOfBounds() throws Exception {
		ArrayList<Roi> resultRois = RoiMan.getSliceRoi(mockRoiManager, testStack, 0);
		assertEquals("Out of bounds slice number should return no ROIs", 0, resultRois.size());

		resultRois = RoiMan.getSliceRoi(mockRoiManager, testStack, testStack.getSize() + 1);
		assertEquals("Out of bounds slice number should return no ROIs", 0, resultRois.size());
	}

	@Test
	public void testGetSliceRoi() throws Exception {
		final int NO_ROI_SLICE_NO = 2;
		final int SINGLE_ROI_SLICE_NO = 3;
		final int MULTI_ROI_SLICE_NO = 4;

		// RoiManager.getSliceNumber tries to parse the number of the slice from
		// the label of the Roi it's given.
		// It doesn't - for example - check the slice attribute of the given
		// Roi...
		final String singleRoiLabel = "000" + SINGLE_ROI_SLICE_NO + "-0000-0001";
		final String multiRoi1Label = "000" + MULTI_ROI_SLICE_NO + "-0000-0001";
		final String multiRoi2Label = "000" + MULTI_ROI_SLICE_NO + "-0000-0002";
		final String noSliceLabel = "NO_SLICE";

		final Roi singleRoi = new Roi(10, 10, 10, 10);
		singleRoi.setName(singleRoiLabel);

		final Roi multiRoi1 = new Roi(10, 10, 10, 10);
		multiRoi1.setName(multiRoi1Label);

		final Roi multiRoi2 = new Roi(30, 30, 10, 10);
		multiRoi2.setName(multiRoi2Label);

		final Roi noSliceRoi = new Roi(50, 50, 10, 10);
		noSliceRoi.setName(noSliceLabel);

		final Roi rois[] = { singleRoi, multiRoi1, multiRoi2, noSliceRoi };

		when(mockRoiManager.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiManager.getRoisAsArray()).thenReturn(rois);

		// Slice with no (associated) Rois
		ArrayList<Roi> resultRois = RoiMan.getSliceRoi(mockRoiManager, testStack, NO_ROI_SLICE_NO);
		assertEquals("Wrong number of ROIs returned", 1, resultRois.size());
		assertEquals("Wrong ROI returned", noSliceLabel, resultRois.get(0).getName());

		// Slice with one Roi
		resultRois = RoiMan.getSliceRoi(mockRoiManager, testStack, SINGLE_ROI_SLICE_NO);

		assertEquals("Wrong number of ROIs returned", 2, resultRois.size());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", singleRoiLabel, resultRois.get(0).getName());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", noSliceLabel, resultRois.get(1).getName());

		// Slice with multiple Rois
		resultRois = RoiMan.getSliceRoi(mockRoiManager, testStack, MULTI_ROI_SLICE_NO);

		assertEquals("Wrong number of ROIs returned", 3, resultRois.size());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", multiRoi1Label, resultRois.get(0).getName());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", multiRoi2Label, resultRois.get(1).getName());
		assertEquals("Wrong ROI returned, or ROIs in wrong order", noSliceLabel, resultRois.get(2).getName());
	}

	@Test
	public void testGetLimitsReturnsNullIfRoiManagerIsNull() throws Exception {
		final int limitsResult[] = RoiMan.getLimits(null, testStack);
		assertNull(limitsResult);
	}

	@Test
	public void testGetLimitsReturnsNullIfRoiManagerIsEmpty() throws Exception {
		when(mockRoiManager.getCount()).thenReturn(0);

		final int limitsResult[] = RoiMan.getLimits(mockRoiManager, testStack);
		assertNull(limitsResult);
	}

	@Test
	public void testGetLimitsReturnsNullIfStackIsNull() throws Exception {
		final int limitsResult[] = RoiMan.getLimits(mockRoiManager, null);
		assertNull(limitsResult);
	}

	@Test
	public void testGetLimitsReturnsNullIfThereAreNoValidRois() throws Exception {
		final Roi badXYRoi = new Roi(-100, -100, 10, 10);
		badXYRoi.setName("0001-0000-0001");

		final Roi badSliceRoi = new Roi(10, 10, 30, 60);
		badSliceRoi.setName("9999-0000-0001"); // slice #9999

		final Roi badRois[] = { badXYRoi, badSliceRoi };

		when(mockRoiManager.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiManager.getRoisAsArray()).thenReturn(badRois);
		when(mockRoiManager.getCount()).thenReturn(badRois.length);

		final int limitsResult[] = RoiMan.getLimits(mockRoiManager, testStack);
		assertNull("Limits should be null if there are no valid ROIs", limitsResult);
	}

	@Test
	public void testGetLimitsAccountsForRoiActiveOnAllSlides() throws Exception {
		final Roi roi = new Roi(10, 10, 30, 60);
		roi.setName("0001-0000-0001");
		final Roi allActive = new Roi(80, 80, 10, 10);
		allActive.setName(""); // name can't be null
		final Roi rois[] = { roi, allActive };

		when(mockRoiManager.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiManager.getRoisAsArray()).thenReturn(rois);
		when(mockRoiManager.getCount()).thenReturn(rois.length);

		assertTrue("Sanity check failed: ROI is not active on all slides",
				RoiMan.isActiveOnAllSlices(mockRoiManager, allActive));

		final int limitsResult[] = RoiMan.getLimits(mockRoiManager, testStack);

		assertEquals("Limits should start from the first slide", 1, limitsResult[MIN_Z_INDEX]);
		assertEquals("Limits should end on the last slide", testStack.getSize(), limitsResult[MAX_Z_INDEX]);
	}

	@Test
	public void testGetLimitsCropsTooLargeRois() throws Exception {
		final int STACK_WIDTH = testStack.getWidth();
		final int STACK_HEIGHT = testStack.getHeight();
		final Roi hugeRoi = new Roi(-100, -100, STACK_WIDTH + 100, STACK_HEIGHT + 100);
		hugeRoi.setName("0001-0000-0001");
		final Roi rois[] = { hugeRoi };

		when(mockRoiManager.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiManager.getRoisAsArray()).thenReturn(rois);
		when(mockRoiManager.getCount()).thenReturn(rois.length);

		final int limitsResult[] = RoiMan.getLimits(mockRoiManager, testStack);
		assertEquals("Limits minimum x is incorrect", 0, limitsResult[0]);
		assertEquals("Limits maximum x is incorrect", STACK_WIDTH, limitsResult[1]);
		assertEquals("Limits minimum y is incorrect", 0, limitsResult[2]);
		assertEquals("Limits maximum y is incorrect", STACK_HEIGHT, limitsResult[3]);
	}

	@Test
	public void testGetLimits() throws Exception {
		final int NUM_LIMITS = 6;
		final int ROI1_X = 10;
		final int ROI1_Y = 10;
		final int ROI1_WIDTH = 30;
		final int ROI1_HEIGHT = 60;
		final int ROI2_X = 20;
		final int ROI2_Y = 5;
		final int ROI2_WIDTH = 40;
		final int ROI2_HEIGHT = 30;
		final int MIN_X = ROI1_X;
		final int MIN_Y = ROI2_Y;
		final int MAX_X = ROI2_X + ROI2_WIDTH;
		final int MAX_Y = ROI1_Y + ROI1_HEIGHT;
		final int MIN_Z = 2;
		final int MAX_Z = 3;

		final String roi1Label = "000" + MIN_Z + "-0000-0001";
		final String roi2Label = "000" + MAX_Z + "-0000-0001";

		final Roi roi1 = new Roi(ROI1_X, ROI1_Y, ROI1_WIDTH, ROI1_HEIGHT);
		roi1.setName(roi1Label);

		final Roi roi2 = new Roi(ROI2_X, ROI2_Y, ROI2_WIDTH, ROI2_HEIGHT);
		roi2.setName(roi2Label);

		final Roi rois[] = { roi1, roi2 };

		when(mockRoiManager.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiManager.getRoisAsArray()).thenReturn(rois);
		when(mockRoiManager.getCount()).thenReturn(rois.length);

		final int limitsResult[] = RoiMan.getLimits(mockRoiManager, testStack);
		assertNotNull(limitsResult);
		assertEquals("Wrong number of limits", NUM_LIMITS, limitsResult.length);
		assertEquals("Limits minimum x is incorrect", MIN_X, limitsResult[0]);
		assertEquals("Limits maximum x is incorrect", MAX_X, limitsResult[1]);
		assertEquals("Limits minimum y is incorrect", MIN_Y, limitsResult[2]);
		assertEquals("Limits maximum y is incorrect", MAX_Y, limitsResult[3]);
		assertEquals("Limits minimum z is incorrect", MIN_Z, limitsResult[MIN_Z_INDEX]);
		assertEquals("Limits maximum z is incorrect", MAX_Z, limitsResult[MAX_Z_INDEX]);
	}

	@Test
	public void testCropStack() throws Exception {
		final int WIDTH = 6;
		final int HEIGHT = 3;
		final int DEPTH = 3;
		final int ROI_WIDTH = 2;
		final int ROI_HEIGHT = 2;
		final int WHITE_COUNT = 8;
		final int BINARY_WHITE = -1;
		final int BACKGROUND_COLOR = 0;
		final int BACKGROUND_COUNT = 46;

		final Roi roi1 = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		roi1.setName("0002-0000-0001");
		final Roi roi2 = new Roi(6, 3, ROI_WIDTH, ROI_HEIGHT);
		roi2.setName("0003-0000-0001");
		final Roi noColorRoi = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		noColorRoi.setName("0001-0000-0001");
		final Roi rois[] = { noColorRoi, roi1, roi2 };

		final ImagePlus image = TestDataMaker.brick(10, 10, 10);
		final ImageStack originalStack = image.getStack();

		when(mockRoiManager.getCount()).thenReturn(rois.length);
		when(mockRoiManager.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiManager.getRoisAsArray()).thenReturn(rois);

		final ImageStack resultStack = RoiMan.cropStack(mockRoiManager, originalStack, false, 0x00, 0);
		assertEquals("Cropped stack has wrong width", WIDTH, resultStack.getWidth());
		assertEquals("Cropped stack has wrong height", HEIGHT, resultStack.getHeight());
		assertEquals("Cropped stack has wrong depth", DEPTH, resultStack.getSize());

		final int foregroundCount = countColorPixels(resultStack, BINARY_WHITE);
		assertEquals("Cropped area has wrong amount of foreground color", WHITE_COUNT, foregroundCount);

		final int backgroundCount = countColorPixels(resultStack, BACKGROUND_COLOR);
		assertEquals("Cropped area has wrong amount of background color", BACKGROUND_COUNT, backgroundCount);
	}

	@Test
	public void testCropStackPadding() throws Exception {
		final int WIDTH = 6;
		final int HEIGHT = 3;
		final int DEPTH = 3;
		final int PADDING = 2;
		final int TOTAL_PADDING = 2 * PADDING;
		final int ROI_WIDTH = 2;
		final int ROI_HEIGHT = 2;

		final Roi roi1 = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		roi1.setName("0002-0000-0001");
		final Roi roi2 = new Roi(6, 3, ROI_WIDTH, ROI_HEIGHT);
		roi2.setName("0003-0000-0001");
		final Roi noColorRoi = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		noColorRoi.setName("0001-0000-0001");
		final Roi rois[] = { noColorRoi, roi1, roi2 };

		final ImagePlus image = TestDataMaker.brick(10, 10, 10);
		final ImageStack originalStack = image.getStack();

		when(mockRoiManager.getCount()).thenReturn(rois.length);
		when(mockRoiManager.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiManager.getRoisAsArray()).thenReturn(rois);

		final ImageStack paddedResultStack = RoiMan.cropStack(mockRoiManager, originalStack, false, 0x00, PADDING);
		assertEquals("Cropped stack has wrong padded width", WIDTH + TOTAL_PADDING, paddedResultStack.getWidth());
		assertEquals("Cropped stack has wrong padded height", HEIGHT + TOTAL_PADDING, paddedResultStack.getHeight());
		assertEquals("Cropped stack has wrong padded depth", DEPTH + TOTAL_PADDING, paddedResultStack.getSize());
	}

	@Test
	public void testCropStackFilling() throws Exception {
		final int TEST_COLOR_COUNT = 8;
		final byte TEST_COLOR = -1;
		final byte BACKGROUND_COLOR = 0x00;
		final int BACKGROUND_COLOR_COUNT = 46;
		final byte FILL_COLOR = 0x10;
		final int ORIGINAL_BG_COLOR_COUNT = 4;
		final int FILL_COLOR_COUNT = BACKGROUND_COLOR_COUNT - ORIGINAL_BG_COLOR_COUNT;
		final int ROI_WIDTH = 2;
		final int ROI_HEIGHT = 2;

		final Roi roi1 = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		roi1.setName("0002-0000-0001");
		final Roi roi2 = new Roi(6, 3, ROI_WIDTH, ROI_HEIGHT);
		roi2.setName("0003-0000-0001");
		final Roi noColorRoi = new Roi(2, 2, ROI_WIDTH, ROI_HEIGHT);
		noColorRoi.setName("0001-0000-0001");
		final Roi rois[] = { noColorRoi, roi1, roi2 };

		final ImagePlus image = TestDataMaker.brick(10, 10, 10);
		final ImageStack originalStack = image.getStack();

		when(mockRoiManager.getCount()).thenReturn(rois.length);
		when(mockRoiManager.getSliceNumber(anyString())).thenCallRealMethod();
		when(mockRoiManager.getRoisAsArray()).thenReturn(rois);

		final ImageStack resultStack = RoiMan.cropStack(mockRoiManager, originalStack, true, FILL_COLOR, 0);

		final int foregroundCount = countColorPixels(resultStack, TEST_COLOR);
		assertEquals("Cropped area has wrong amount of foreground color", TEST_COLOR_COUNT, foregroundCount);

		final int backgroundCount = countColorPixels(resultStack, BACKGROUND_COLOR);
		assertEquals("Cropped area has wrong amount of original background color", ORIGINAL_BG_COLOR_COUNT,
				backgroundCount);

		final int fillCount = countColorPixels(resultStack, FILL_COLOR);
		assertEquals("Cropped area has wrong amount of background fill color", FILL_COLOR_COUNT, fillCount);
	}

	@Test
	public void testCopyRoiWithMask() throws Exception {
		final int TEST_COLOR = 0x20;
		final int TEST_COLOR_COUNT = 75;

		// Create a mask from an L-shaped polygon
		final Polygon polygon = new Polygon();
		polygon.addPoint(0, 0);
		polygon.addPoint(10, 0);
		polygon.addPoint(10, 5);
		polygon.addPoint(5, 5);
		polygon.addPoint(5, 10);
		polygon.addPoint(0, 10);
		polygon.addPoint(0, 0);

		final ImageProcessor result = testStack.getProcessor(1).createProcessor(testImage.getWidth(),
				testImage.getHeight());
		final ImageProcessor ip = testStack.getProcessor(1).createProcessor(testImage.getWidth(),
				testImage.getHeight());
		ip.setRoi(polygon);
		final ImageProcessor mask = ip.getMask();

		// set up mock ImageProcessor
		final ImageProcessor mockSource = mock(ImageProcessor.class);
		when(mockSource.getMask()).thenReturn(mask);
		when(mockSource.get(anyInt(), anyInt())).thenReturn(TEST_COLOR);

		// get and assert result
		RoiMan.copyRoiWithMask(mockSource, result, 0, 0, 10, 10, 0);
		final ImageStack stack = new ImageStack(result.getWidth(), result.getHeight());
		stack.addSlice(result);

		final int foregroundCount = countColorPixels(stack, TEST_COLOR);
		assertEquals("Wrong area copied", TEST_COLOR_COUNT, foregroundCount);
	}

	/**
	 * Counts the number of pixels that have the given color in all the slices
	 * of the given stack
	 *
	 * @param stack
	 *            The stack to inspect
	 * @param color
	 *            The color to be searched
	 * @return The number of pixels that match the color
	 */
	private int countColorPixels(final ImageStack stack, final int color) {
		int count = 0;
		final int height = stack.getHeight();
		final int width = stack.getWidth();

		for (int z = 1; z <= stack.getSize(); z++) {
			final byte pixels[] = (byte[]) stack.getPixels(z);
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
}