package org.bonej.utilities;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.axis.LinearAxis;
import net.imagej.axis.PolynomialAxis;
import net.imagej.axis.PowerAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.DefaultLinearSpace;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Iterator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the ImageCheck utility class
 *
 * @author Richard Domander
 */
public class ImageCheckTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testIsColorsBinaryFalseWhenIntervalNull() throws Exception {
        final boolean result = ImageCheck.isColoursBinary(null);

        assertFalse("A null interval should not be binary colour", result);
    }

    @Test
    public void testIsColorsBinaryReturnsFalseIfIntervalEmpty() throws Exception {
        final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{0});

        final boolean result = ImageCheck.isColoursBinary(interval);

        assertFalse("An empty image should not be binary colour", result);
    }

    @Test
    public void testIsColorsBinaryReturnsTrueForMonochrome() throws Exception {
        final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});

        final boolean result = ImageCheck.isColoursBinary(interval);

        assertTrue("Monochrome image should be binary colour", result);
    }

    @Test
    public void testIsColorsBinaryReturnsTrueForDichromatic() throws Exception {
        // Create a test image with two colors
        final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});
        final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i % 2).iterator();
        interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

        final boolean result = ImageCheck.isColoursBinary(interval);

        assertTrue("An image with two colours should be binary colour", result);
    }

    @Test
    public void testIsColorsBinaryReturnsFalseForMulticolor() throws Exception {
        // Create a test image with many colors
        final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});
        final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i + 1).iterator();
        interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

        final boolean result = ImageCheck.isColoursBinary(interval);

        assertFalse("An image with more than two colours should not be binary colour", result);
    }

    @Test
    public void testCountSpatialDimensionsNullSpace() throws Exception {
        final long result = ImageCheck.countSpatialDimensions(null);

        assertEquals("A null space should contain zero dimensions", 0, result);
    }

    @Test
    public void testCountSpatialDimensions() throws Exception {
        // Create a test image
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final int[] dimensions = {10, 10, 3};
        Img<DoubleType> img = IMAGE_J.op().create().img(dimensions);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, channelAxis);

        final long result = ImageCheck.countSpatialDimensions(imgPlus);

        assertEquals("Wrong number of spatial dimensions", 2, result);
    }

    @Test
    public void testAxisStreamReturnsEmptyIfSpaceNull() throws Exception {
        final Stream<TypedAxis> result = ImageCheck.axisStream(null);

        assertNotNull("Stream should not be null", result);
        assertEquals("Stream should be empty", result.count(), 0);
    }

    @Test
    public void testAxisStreamReturnsEmptyIfSpaceHasNoAxes() throws Exception {
        final DefaultLinearSpace zeroDimensionsSpace = new DefaultLinearSpace(0);

        final Stream<LinearAxis> result = ImageCheck.axisStream(zeroDimensionsSpace);

        assertEquals("Stream should be empty", result.count(), 0);
    }

    @Test
    public void testAxisStream() throws Exception {
        // Create a test image that has axes
        final double firstScale = 1.0;
        final DefaultLinearAxis firstAxis = new DefaultLinearAxis(firstScale);
        final double secondScale = 0.5;
        final DefaultLinearAxis secondAxis = new DefaultLinearAxis(secondScale);
        final int[] dimensions = {10, 10};
        Img<DoubleType> img = IMAGE_J.op().create().img(dimensions);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", firstAxis, secondAxis);

        final double[] result = ImageCheck.axisStream(imgPlus).mapToDouble(a -> a.averageScale(0, 1)).toArray();

        assertNotNull("Stream should not be null", result);
        assertEquals("Wrong number of axes in stream", 2, result.length);
        assertEquals("Axes in the stream are in wrong order", firstScale, result[0], 1e-12);
        assertEquals("Axes in the stream are in wrong order", secondScale, result[1], 1e-12);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicReturnsFalseIfSpaceNull() throws Exception {
        final boolean result = ImageCheck.isSpatialCalibrationIsotropic(null);

        assertFalse("Calibration should not be isotropic if space is null", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicFalseIfNonLinearAxis() throws Exception {
        // Create a test image with a nonlinear spatial axis
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final PowerAxis yAxis = new PowerAxis(Axes.Y, 2);
        Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final boolean result = ImageCheck.isSpatialCalibrationIsotropic(imgPlus);

        assertFalse("Calibration should not be isotropic if there's a nonlinear spatial axis", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicFalseIfNoSpatialAxes() throws Exception {
        // Create a test image with no spatial axes
        final DefaultLinearAxis timeAxis = new DefaultLinearAxis(Axes.TIME);
        final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.CHANNEL);
        Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 3});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", timeAxis, channelAxis);

        final boolean result = ImageCheck.isSpatialCalibrationIsotropic(imgPlus);

        assertFalse("Calibration should not be isotropic if there are no spatial axes", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicFalseIfScalesNotWithinTolerance() throws Exception {
        // Create a test image with anisotropic calibration
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, 1000.0);
        Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final boolean result = ImageCheck.isSpatialCalibrationIsotropic(imgPlus, 1.0);

        assertFalse("Calibration should not be isotropic if anisotropy is not within tolerance", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropic() throws Exception {
        // Create a test image with anisotropic calibration (within tolerance)
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, 1.5);
        Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final boolean result = ImageCheck.isSpatialCalibrationIsotropic(imgPlus, 1.0);

        assertTrue("Calibration should be isotropic when anisotropy is within tolerance", result);
    }
}