
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
	public void testAnisotropyReturnsNaNIfImageIsNull() {
		final double anisotropy = ImagePlusUtil.anisotropy(null);

		assertTrue("Anisotropy should be NaN for a null image", Double.isNaN(
			anisotropy));
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
	public void testIsBinaryColourReturnsFalseIfImageIsNull() {
		final boolean result = ImagePlusUtil.isBinaryColour(null);
		assertFalse("Null image should not be binary", result);
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

	@Test
	public void testMonochromeIsBinaryColour() {
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
	public void testMulticolorIsNotBinaryColour() {
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

	@Test
	public void testIs3DFalseIfImageNull() {
		final boolean result = ImagePlusUtil.is3D(null);

		assertFalse("Null image should not be 3D", result);
	}

	@Test
	public void testIs3DFalseIfImage2D() {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		when(imagePlus.getNSlices()).thenReturn(1);

		final boolean result = ImagePlusUtil.is3D(imagePlus);

		assertFalse("2D image should not be 3D", result);
	}

	@Test
	public void testIs3D() throws AssertionError {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		when(imagePlus.getNSlices()).thenReturn(10);

		final boolean result = ImagePlusUtil.is3D(imagePlus);

		assertTrue("Image with more than 1 slice should be 3D", result);
	}

}
