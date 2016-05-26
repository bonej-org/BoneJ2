package org.bonej.utilities;

import ij.ImagePlus;
import ij.process.ImageStatistics;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the
 *
 * @author Richard Domander 
 */
public class ImagePlusCheckTest {
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
}