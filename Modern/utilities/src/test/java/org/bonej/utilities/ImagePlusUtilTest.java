/*-
 * #%L
 * Utility methods for BoneJ2
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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


package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.ImageStatistics;

/**
 * Unit tests for the ImagePlusCheck class
 *
 * @author Richard Domander
 */
public class ImagePlusUtilTest {

	@Test
	public void testAnisotropy() {
		// Mock an isotropic 2D image
		final ImagePlus testImage = mock(ImagePlus.class);
		final Calibration anisotropicCalibration = new Calibration();
		anisotropicCalibration.pixelWidth = 1;
		anisotropicCalibration.pixelHeight = 1;

		// Should not care about pixelDepth because image is 2D
		anisotropicCalibration.pixelDepth = 5;
		when(testImage.getCalibration()).thenReturn(anisotropicCalibration);
		when(testImage.getNSlices()).thenReturn(1);

		final double result = ImagePlusUtil.anisotropy(testImage);

		assertEquals("Anisotropy should be 0.0", 0.0, result, 1e-12);
	}

	@Test
	public void testAnisotropy3D() {
		final double expected = 4.0;
		// Mock an anisotropic 3D image
		final ImagePlus testImage = mock(ImagePlus.class);
		final Calibration anisotropicCalibration = new Calibration();
		anisotropicCalibration.pixelWidth = 1;
		anisotropicCalibration.pixelHeight = 2;
		anisotropicCalibration.pixelDepth = 5;
		when(testImage.getCalibration()).thenReturn(anisotropicCalibration);
		when(testImage.getNSlices()).thenReturn(10);

		final double result = ImagePlusUtil.anisotropy(testImage);

		assertEquals("Anisotropy should be " + expected, expected, result, 1e-12);
	}

	@Test(expected = NullPointerException.class)
	public void testAnisotropyThrowsNPEIfImageNull() {
		ImagePlusUtil.anisotropy(null);
	}

	@Test
	public void testCleanDuplicate() {
		final String title = "bonej-test-image.tiff";
		final int width = 5;
		final int height = 7;
		final int depth = 11;
		final Roi roi = new Roi(1, 1, 3, 3);
		final ImagePlus image = IJ.createImage(title, width, height, depth, 8);
		image.setRoi(roi);

		final ImagePlus result = ImagePlusUtil.cleanDuplicate(image);

		assertEquals("Duplicate has wrong title", result.getTitle(), title);
		assertEquals("ROI should not affect duplicate size", width, result
			.getWidth());
		assertEquals("ROI should not affect duplicate size", height, result
			.getHeight());
		assertEquals("The original image should still have its ROI", roi, image
			.getRoi());
	}

	@Test(expected = NullPointerException.class)
	public void testCleanDuplicateThrowsNPEIfImageNull() {
		ImagePlusUtil.cleanDuplicate(null);
	}

	@Test
	public void testIs3D() throws AssertionError {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		when(imagePlus.getNSlices()).thenReturn(10);

		final boolean result = ImagePlusUtil.is3D(imagePlus);

		assertTrue("Image with more than 1 slice should be 3D", result);
	}

	@Test(expected = NullPointerException.class)
	public void testIs3DThrowsNPEIfImageNull() {
		ImagePlusUtil.is3D(null);
	}

	@Test
	public void testIs3DFalseIfImage2D() {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		when(imagePlus.getNSlices()).thenReturn(1);

		final boolean result = ImagePlusUtil.is3D(imagePlus);

		assertFalse("2D image should not be 3D", result);
	}

	@Test
	public void testIsBinaryColour() {
		final ImagePlus testImage = mock(ImagePlus.class);
		final ImageStatistics binaryStats = new ImageStatistics();
		binaryStats.pixelCount = 2;
		binaryStats.histogram = new int[256];
		binaryStats.histogram[0x00] = 1;
		binaryStats.histogram[0xFF] = 1;

		when(testImage.getStatistics()).thenReturn(binaryStats);

		final boolean result = ImagePlusUtil.isBinaryColour(testImage);
		assertTrue("Image with two colors (black & white) should be binary",
			result);
	}

	@Test(expected = NullPointerException.class)
	public void testIsBinaryColourThrowsNPEIfImageNull() {
		ImagePlusUtil.isBinaryColour(null);
	}

	@Test
	public void testIsBinaryColourMonochrome() {
		final ImagePlus testImage = mock(ImagePlus.class);
		final ImageStatistics binaryStats = new ImageStatistics();
		binaryStats.pixelCount = 1;
		binaryStats.histogram = new int[256];
		binaryStats.histogram[0xFF] = 1;

		when(testImage.getStatistics()).thenReturn(binaryStats);

		final boolean result = ImagePlusUtil.isBinaryColour(testImage);
		assertTrue("A monochrome image should be binary", result);
	}

	@Test
	public void testIsBinaryColourMulticolor() {
		final ImagePlus testImage = mock(ImagePlus.class);
		final ImageStatistics binaryStats = new ImageStatistics();
		binaryStats.pixelCount = 3;
		binaryStats.histogram = new int[256];
		binaryStats.histogram[0x00] = 1;
		binaryStats.histogram[0x01] = 1;
		binaryStats.histogram[0xFF] = 1;

		when(testImage.getStatistics()).thenReturn(binaryStats);

		final boolean result = ImagePlusUtil.isBinaryColour(testImage);
		assertFalse("A multicolor image should be binary", result);
	}

}
