
package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;

import net.imagej.ImageJ;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;

/**
 * Tests for {@link SkeletoniseWrapper}
 *
 * @author Richard Domander
 */
public class SkeletoniseWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test
	public void testNullImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			SkeletoniseWrapper.class);
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(IMAGE_J,
			SkeletoniseWrapper.class);
	}

	@Test
	public void testCompositeImageCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_CHANNEL_DIMENSIONS +
			". Please split the channels.";
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 3, 3, 1, 8);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(SkeletoniseWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("A composite image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testTimeDimensionCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_TIME_DIMENSIONS +
			". Please split the hyperstack.";
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 1, 3, 3, 8);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(SkeletoniseWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("An image with time dimension should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testRun() throws Exception {
		// SETUP
		final String expectedTitle = "Skeleton of Test";
		final ImagePlus imagePlus = NewImage.createImage("Test", 5, 5, 5, 8, 1);
		final Calibration calibration = new Calibration();
		calibration.setUnit("my unit");
		imagePlus.setCalibration(calibration);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(SkeletoniseWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		final ImagePlus skeleton = (ImagePlus) module.getOutput("skeleton");
		assertNotNull("Skeleton image should not be null", skeleton);
		assertEquals("Skeleton has wrong title", expectedTitle, skeleton
			.getTitle());
		assertEquals("Skeleton should have same calibration", "my unit", skeleton
			.getCalibration().getUnit());
		assertNotEquals("Original image should not have been overwritten",
			imagePlus, skeleton);
	}

}
