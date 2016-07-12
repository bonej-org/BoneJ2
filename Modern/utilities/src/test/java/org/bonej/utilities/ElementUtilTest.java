package org.bonej.utilities;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.axis.PowerAxis;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.Test;

import java.util.Iterator;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * Unit tests for the {@link ElementUtil} class
 *
 * @author Richard Domander
 */
public class ElementUtilTest {
    @Test
    public void testIsColorsBinaryFalseWhenIntervalNull() throws Exception {
        final boolean result = ElementUtil.isColorsBinary(null);

        assertFalse("A null interval should not be binary color", result);
    }

    @Test
    public void testIsColorsBinaryReturnsFalseIfIntervalEmpty() throws Exception {
        final IterableInterval<DoubleType> interval = ArrayImgs.doubles(0);

        final boolean result = ElementUtil.isColorsBinary(interval);

        assertFalse("An empty image should not be binary color", result);
    }

    @Test
    public void testIsColorsBinaryReturnsTrueForMonochrome() throws Exception {
        final IterableInterval<DoubleType> interval = ArrayImgs.doubles(2, 2);

        final boolean result = ElementUtil.isColorsBinary(interval);

        assertTrue("Monochrome image should be binary color", result);
    }

    @Test
    public void testIsColorsBinaryReturnsTrueForDichromatic() throws Exception {
        // Create a test image with two colors
        final IterableInterval<DoubleType> interval = ArrayImgs.doubles(2, 2);
        final Iterator<Integer> intIterator = IntStream.iterate(0, i -> (i + 1) % 2).iterator();
        interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

        final boolean result = ElementUtil.isColorsBinary(interval);

        assertTrue("An image with two colours should be binary color", result);
    }

    @Test
    public void testIsColorsBinaryReturnsFalseForMulticolor() throws Exception {
        // Create a test image with many colors
        final IterableInterval<DoubleType> interval = ArrayImgs.doubles(2, 2);
        final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i + 1).iterator();
        interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

        final boolean result = ElementUtil.isColorsBinary(interval);

        assertFalse("An image with more than two colours should not be binary color", result);
    }

    @Test
    public void testCalibratedSpatialElementSizeNaNIfSpaceNull() throws Exception {
        final double result = ElementUtil.calibratedSpatialElementSize(null);

        assertTrue("Size should be NaN when space is null", Double.isNaN(result));
    }

    @Test
    public void testCalibratedSpatialElementSizeNaNIfNonLinear() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final PowerAxis yAxis = new PowerAxis(Axes.Y, 2);
        final Img<DoubleType> img = ArrayImgs.doubles(1, 1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = ElementUtil.calibratedSpatialElementSize(imgPlus);

        assertTrue("Size should be NaN when space has nonlinear axes", Double.isNaN(result));
    }

    @Test
    public void testCalibratedSpatialElementSizeNaNIfUnitsMismatch() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "cm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
        final Img<DoubleType> img = ArrayImgs.doubles(1, 1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = ElementUtil.calibratedSpatialElementSize(imgPlus);

        assertTrue("Size should be NaN when the units of axes mismatch", Double.isNaN(result));
    }

    @Test
    public void testCalibratedSpatialElementSizeWhenNoSpatialAxes() throws Exception {
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final Img<DoubleType> img = ArrayImgs.doubles(1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", cAxis);

        final double elementSize = ElementUtil.calibratedSpatialElementSize(imgPlus);

        assertEquals("Element size should be zero when there are no spatial axes", 0.0, elementSize, 1e-12);
    }

    @Test
    public void testCalibratedSpatialElementSize() throws Exception {
        final double xScale = 1.5;
        final double yScale = 2.25;
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, xScale);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, yScale);
        final Img<DoubleType> img = ArrayImgs.doubles(1, 1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = ElementUtil.calibratedSpatialElementSize(imgPlus);

        assertEquals("Element size is wrong", xScale * yScale, result, 1e-12);
    }
}