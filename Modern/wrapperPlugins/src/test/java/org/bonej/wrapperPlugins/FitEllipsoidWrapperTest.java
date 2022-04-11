/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import net.imagej.ops.stats.regression.leastSquares.Quadric;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;

import ij.ImagePlus;
import ij.gui.NewImage;

/**
 * Tests for {@link FitEllipsoidWrapper}.
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class FitEllipsoidWrapperTest extends AbstractWrapperTest {

	@Test
	public void test2DImageCancelsPlugin() {
		CommonWrapperTests.test2DImagePlusCancelsPlugin(imageJ(),
			FitEllipsoidWrapper.class);
	}

	@Test
	public void testAnisotropicImageShowsWarningDialog() {
		CommonWrapperTests.testAnisotropyWarning(imageJ(),
			FitEllipsoidWrapper.class);
	}

	@Test
	public void testNullImageCancelsPlugin() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
			FitEllipsoidWrapper.class);
	}

	@Test
	public void testNullROIManagerCancelsPlugin() throws Exception {
		// SETUP
		final ImagePlus imagePlus = NewImage.createImage("", 5, 5, 5, 8, 1);

		// EXECUTE
		final CommandModule module = command().run(
			FitEllipsoidWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("No ROI Manager should have cancelled the plugin", module
			.isCanceled());
		assertTrue("Cancel reason is incorrect", module.getCancelReason()
			.startsWith("Please populate ROI Manager with at least " +
				Quadric.MIN_DATA));
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}
}
