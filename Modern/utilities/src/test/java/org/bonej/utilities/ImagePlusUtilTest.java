
package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

/**
 * Unit tests for the ImagePlusCheck class
 *
 * @author Richard Domander
 */
public class ImagePlusUtilTest {

	@Test
	public void testAnisotropyReturnsNaNIfImageIsNull() throws Exception {
		final double anisotropy = ImagePlusUtil.anisotropy(null);

		assertTrue("Anisotropy should be NaN for a null image", Double.isNaN(
			anisotropy));
	}

	@Test
	public void testAnisotropy3D() throws Exception {
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
	public void testAnisotropy() throws Exception {
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
	public void testIsBinaryColourReturnsFalseIfImageIsNull() throws Exception {
		boolean result = ImagePlusUtil.isBinaryColour(null);
		assertFalse("Null image should not be binary", result);
	}

	@Test
	public void testMonochromeIsBinaryColour() throws Exception {
		ImagePlus testImage = mock(ImagePlus.class);
		ImageStatistics binaryStats = new ImageStatistics();
		binaryStats.pixelCount = 1;
		binaryStats.histogram = new int[256];
		binaryStats.histogram[0xFF] = 1;

		when(testImage.getStatistics()).thenReturn(binaryStats);

		boolean result = ImagePlusUtil.isBinaryColour(testImage);
		assertTrue("A monochrome image should be binary", result);
	}

	@Test
	public void testMulticolorIsNotBinaryColour() throws Exception {
		ImagePlus testImage = mock(ImagePlus.class);
		ImageStatistics binaryStats = new ImageStatistics();
		binaryStats.pixelCount = 3;
		binaryStats.histogram = new int[256];
		binaryStats.histogram[0x00] = 1;
		binaryStats.histogram[0x01] = 1;
		binaryStats.histogram[0xFF] = 1;

		when(testImage.getStatistics()).thenReturn(binaryStats);

		boolean result = ImagePlusUtil.isBinaryColour(testImage);
		assertFalse("A multicolor image should be binary", result);
	}

	@Test
	public void testIsBinaryColour() throws Exception {
		ImagePlus testImage = mock(ImagePlus.class);
		ImageStatistics binaryStats = new ImageStatistics();
		binaryStats.pixelCount = 2;
		binaryStats.histogram = new int[256];
		binaryStats.histogram[0x00] = 1;
		binaryStats.histogram[0xFF] = 1;

		when(testImage.getStatistics()).thenReturn(binaryStats);

		boolean result = ImagePlusUtil.isBinaryColour(testImage);
		assertTrue("Image with two colors (black & white) should be binary",
			result);
	}

	@Test
	public void testIs3DFalseIfImageNull() throws Exception {
		final boolean result = ImagePlusUtil.is3D(null);

		assertFalse("Null image should not be 3D", result);
	}

	@Test
	public void testIs3DFalseIfImage2D() throws Exception {
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

	@Test
	public void testRevertInversion() throws Exception {
		// SETUP (not inverted)
		ImagePlus image = NewImage.createByteImage("", 3, 3, 3,
			NewImage.FILL_RANDOM);

		// EXECUTE
		ImagePlusUtil.revertInversion(image);

		// VERIFY
		assertFalse(image.isInvertedLut());

		// SETUP (inverted)
		image = createInvertedLUTImage();

		// EXECUTE
		ImagePlusUtil.revertInversion(image);

		// VERIFY
		assertFalse(image.isInvertedLut());
	}

	private ImagePlus createInvertedLUTImage() {
		final ImagePlus image = NewImage.createByteImage("", 3, 3, 3,
			NewImage.FILL_RANDOM);
		ImageProcessor ip = image.getProcessor();
		ip.invertLut();
		if (image.getStackSize() > 1) image.getStack().setColorModel(ip
			.getColorModel());
		return image;
	}
}
