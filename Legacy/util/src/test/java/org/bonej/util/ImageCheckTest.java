/*-
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
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
package org.bonej.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageStatistics;

/**
 * Unit tests for the org.doube.util.ImageCheck class
 *
 * Richard Domander
 */
public class ImageCheckTest {
	private static final int BINARY_WHITE = 0xFF;
	private static final int BINARY_BLACK = 0x00;

	@Test
	public void testIsVoxelIsotropic() throws Exception {
		final ImagePlus testImage = mock(ImagePlus.class);
		final Calibration anisotropicCalibration = new Calibration();

		// 2D anisotropic image with 0 tolerance
		anisotropicCalibration.pixelWidth = 2;
		anisotropicCalibration.pixelHeight = 1;

		when(testImage.getCalibration()).thenReturn(anisotropicCalibration);
		when(testImage.getStackSize()).thenReturn(1);

		boolean result = ImageCheck.isVoxelIsotropic(testImage, 0.0);
		assertFalse("Image where width > height should not be isotropic", result);

		// 2D image where anisotropy is within tolerance
		result = ImageCheck.isVoxelIsotropic(testImage, 1.0);
		assertTrue("Image should be isotropic if anisotropy is within tolerance", result);

		// 3D image where depth anisotropy is beyond tolerance
		anisotropicCalibration.pixelDepth = 1000;
		when(testImage.getStackSize()).thenReturn(100);

		result = ImageCheck.isVoxelIsotropic(testImage, 1.0);
		assertFalse("Pixel depth too great to be anisotropic within tolerance", result);
	}

	@Test
	public void testIsBinaryReturnsFalseIfImageIsNull() throws Exception {
		final boolean result = ImageCheck.isBinary(null);
		assertFalse("Null image should not be binary", result);
	}

	@Test
	public void testIsBinaryReturnsFalseIfImageHasWrongType() throws Exception {
		final ImagePlus testImage = mock(ImagePlus.class);
		final int wrongTypes[] = { ImagePlus.COLOR_256, ImagePlus.COLOR_RGB, ImagePlus.GRAY16, ImagePlus.GRAY32 };

		for (final int wrongType : wrongTypes) {
			when(testImage.getType()).thenReturn(wrongType);

			final boolean result = ImageCheck.isBinary(testImage);
			assertFalse("Only ImagePlus.GRAY8 type should be binary", result);
		}
	}
}
