package org.bonej.utilities;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageStatistics;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the ImagePlusCheck class
 *
 * @author Richard Domander 
 */
public class ImagePlusCheckTest {
    @Test
    public void testIsIsotropicReturnsFalseIfImageIsNull() throws Exception {
        boolean result = ImagePlusCheck.isIsotropic(null, 0.0);

        assertFalse("Null image should not be isotropic", result);
    }

    @Test
    public void testIsIsotropicFalseIfAnisotropyBeyondTolerance() throws Exception {
        // Mock an anisotropic 2D image
        final ImagePlus testImage = mock(ImagePlus.class);
        final Calibration anisotropicCalibration = new Calibration();
        anisotropicCalibration.pixelWidth = 1.001;
        anisotropicCalibration.pixelHeight = 1;
        when(testImage.getCalibration()).thenReturn(anisotropicCalibration);
        when(testImage.getNSlices()).thenReturn(1);

        final boolean result = ImagePlusCheck.isIsotropic(testImage, 0.0);

        assertFalse("Image where pixel width > height should not be isotropic", result);
    }

    @Test
    public void testIsIsotropicFalseIfAnisotropic3D() throws Exception {
        // Mock an anisotropic 3D image
        final ImagePlus testImage = mock(ImagePlus.class);
        final Calibration anisotropicCalibration = new Calibration();
        anisotropicCalibration.pixelWidth = 1;
        anisotropicCalibration.pixelHeight = 1;
        anisotropicCalibration.pixelDepth = 1.0001;
        when(testImage.getCalibration()).thenReturn(anisotropicCalibration);
        when(testImage.getNSlices()).thenReturn(10);

        final boolean result = ImagePlusCheck.isIsotropic(testImage, 0.0);

        assertFalse("Image where pixel depth > width should not be isotropic", result);
    }

    @Test
    public void testIsIsotropic() throws Exception {
        // Mock an isotropic 2D image
        final ImagePlus testImage = mock(ImagePlus.class);
        final Calibration anisotropicCalibration = new Calibration();
        anisotropicCalibration.pixelWidth = 1;
        anisotropicCalibration.pixelHeight = 1;
        when(testImage.getCalibration()).thenReturn(anisotropicCalibration);
        when(testImage.getNSlices()).thenReturn(1);

        final boolean result = ImagePlusCheck.isIsotropic(testImage, 0.0);

        assertTrue("Image should be isotropic if anisotropy is within tolerance", result);
    }

    @Test
    public void testIsBinaryColourReturnsFalseIfImageIsNull() throws Exception {
        boolean result = ImagePlusCheck.isBinaryColour(null);
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

        boolean result = ImagePlusCheck.isBinaryColour(testImage);
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

        boolean result = ImagePlusCheck.isBinaryColour(testImage);
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

        boolean result = ImagePlusCheck.isBinaryColour(testImage);
        assertTrue("Image with two colors (black & white) should be binary", result);
    }

    @Test
    public void testIs3DFalseIfImageNull() throws Exception {
        final boolean result = ImagePlusCheck.is3D(null);

        assertFalse("Null image should not be 3D", result);
    }

    @Test
    public void testIs3DFalseIfImage2D() throws Exception {
        final ImagePlus imagePlus = mock(ImagePlus.class);
        when(imagePlus.getNSlices()).thenReturn(1);

        final boolean result = ImagePlusCheck.is3D(imagePlus);

        assertFalse("2D image should not be 3D", result);
    }

    @Test
    public void testIs3D() throws AssertionError {
        final ImagePlus imagePlus = mock(ImagePlus.class);
        when(imagePlus.getNSlices()).thenReturn(10);

        final boolean result = ImagePlusCheck.is3D(imagePlus);

        assertTrue("Image with more than 1 slice should be 3D", result);
    }
}