/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
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

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;

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
public class SkeletoniseWrapperTest extends AbstractWrapperTest {

	@Test
	public void testCompositeImageCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_CHANNEL_DIMENSIONS +
			". Please split the channels.";
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 3, 3, 1, 8);

		// EXECUTE
		final CommandModule module = command().run(SkeletoniseWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("A composite image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(imageJ(),
			SkeletoniseWrapper.class);
	}

	@Test
	public void testNullImageCancelsPlugin() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
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
		final CommandModule module = command().run(SkeletoniseWrapper.class,
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
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 1, 3, 3, 8);

		// EXECUTE
		final CommandModule module = command().run(SkeletoniseWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("An image with time dimension should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}
}
