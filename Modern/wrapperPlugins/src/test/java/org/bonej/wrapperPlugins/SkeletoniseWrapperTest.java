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

package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import net.imagej.ImageJ;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.Gateway;
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
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class SkeletoniseWrapperTest {

	private static final Gateway IMAGE_J = new ImageJ();

	@After
	public void tearDown() {
		SharedTable.reset();
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
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(IMAGE_J,
			SkeletoniseWrapper.class);
	}

	@Test
	public void testNullImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			SkeletoniseWrapper.class);
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
		assertNotSame("Original image should not have been overwritten", imagePlus,
			skeleton);
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
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

}
