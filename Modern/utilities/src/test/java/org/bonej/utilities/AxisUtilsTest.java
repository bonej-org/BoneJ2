package org.bonej.utilities;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.units.UnitService;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the AxisUtil class
 *
 * @author Richard Domander
 */
public class AxisUtilsTest {
    public static final ImageJ IMAGE_J = new ImageJ();
    public static final UnitService unitService = IMAGE_J.context().getService(UnitService.class);

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testGetMaxConversion() throws Exception {
        final double[][] scales =
                {{16.0, 8.0, 32.0}, {4.0, 10.0, 1.0}, {1.0, 2.0, 3.0}, {3.0, 2.0, 1.0}, {1.0, 40.0, 2.0}};
        final String[][] units =
                {{"m", "km", "µm"}, {"cm", "mm", "km"}, {"m", "m", "m"}, {"mm", "mm", "mm"}, {"cm", "mm", "cm"}};
        final double[] expected = {500_000, 25_000, 3.0, 3.0, 4.0};
        final Img<ByteType> img = ArrayImgs.bytes(1, 1, 1);
        final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img);

        for (int i = 0; i < scales.length; i++) {
            final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, units[i][0], scales[i][0]);
            final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, units[i][1], scales[i][1]);
            final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, units[i][2], scales[i][2]);
            imgPlus.setAxis(xAxis, 0);
            imgPlus.setAxis(yAxis, 1);
            imgPlus.setAxis(zAxis, 2);

            final double conversion = AxisUtils.getMaxConversion(xAxis.scale(), xAxis.unit(), imgPlus, unitService);

            assertEquals("Unit conversion is incorrect", expected[i], conversion, 1e-12);
        }
    }

    @Test
    public void testGetSpatialUnitUnconvertibleUnits() throws Exception {
        final String[][] units = {{"m", ""}, {"cm", "kg"}};
        final Img<ByteType> img = ArrayImgs.bytes(1, 1);
        final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img);

        for (final String[] unit : units) {
            final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit[0]);
            final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit[1]);
            imgPlus.setAxis(xAxis, 0);
            imgPlus.setAxis(yAxis, 1);

            final Optional<String> result = AxisUtils.getSpatialUnit(imgPlus, unitService);

            assertFalse("Unit should not be present when calibrations are unconvertible", result.isPresent());
        }
    }

    @Test
    public void testGetSpatialUnitAllAxesUncalibrated() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "", 5.0);
        final Img<ByteType> img = ArrayImgs.bytes(1, 1);
        final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis);

        final Optional<String> unit = AxisUtils.getSpatialUnit(imgPlus, unitService);

        assertTrue("String should be present when units are convertible", unit.isPresent());
        assertTrue("Unit should be empty", unit.get().isEmpty());
    }

    @Test
    public void testGetSpatialUnit() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "µm", 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 5.0);
        final Img<ByteType> img = ArrayImgs.bytes(1, 1);
        final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis);

        final Optional<String> unit = AxisUtils.getSpatialUnit(imgPlus, unitService);

        assertTrue("String should be present when units are convertible", unit.isPresent());
        assertEquals("Unit is incorrect", "µm", unit.get());
    }

    @Test
    public void testGetXYZIndicesEmptyIfSpaceNull() throws Exception {
        final Optional<int[]> result = AxisUtils.getXYZIndices(null);

        assertFalse("Optional should be empty", result.isPresent());
    }

    @Test
    public void testGetXYZIndicesEmptyIfSpaceNot3D() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final Img<DoubleType> img = ArrayImgs.doubles(1, 1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final Optional<int[]> result = AxisUtils.getXYZIndices(imgPlus);

        assertFalse("Optional should be empty", result.isPresent());
    }

    @Test
    public void testGetXYZIndices() throws Exception {
        final int[] expectedIndices = {0, 1, 3};
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final Img<DoubleType> img = ArrayImgs.doubles(1, 1, 1, 1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, cAxis, zAxis);

        final Optional<int[]> result = AxisUtils.getXYZIndices(imgPlus);

        assertTrue("Optional should be present", result.isPresent());
        assertArrayEquals("Indices are incorrect", expectedIndices, result.get());
    }

    @Test
    public void testHasSpatialDimensionsFalseIfSpaceNull() throws Exception {
        final boolean result = AxisUtils.hasSpatialDimensions(null);

        assertFalse("Null image should not have a time dimension", result);
    }

    @Test
    public void testHasSpatialDimensions() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<DoubleType> img = ArrayImgs.doubles(1, 1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, tAxis);

        final boolean result = AxisUtils.hasTimeDimensions(imgPlus);

        assertTrue("Should be true when image has spatial dimensions", result);
    }

    @Test
    public void testHasTimeDimensionsFalseIfSpaceNull() throws Exception {
        final boolean result = AxisUtils.hasTimeDimensions(null);

        assertFalse("Null image should not have a time dimension", result);
    }

    @Test
    public void testHasTimeDimensions() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<DoubleType> img = ArrayImgs.doubles(1, 1, 1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, tAxis);

        final boolean result = AxisUtils.hasTimeDimensions(imgPlus);

        assertTrue("Should be true when image has time dimensions", result);
    }

    @Test
    public void testHasChannelDimensionsFalseIfSpaceNull() throws Exception {
        final boolean result = AxisUtils.hasChannelDimensions(null);

        assertFalse("Null image should not have a channel dimension", result);
    }

    @Test
    public void testHasChannelDimensions() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final Img<DoubleType> img = ArrayImgs.doubles(1, 1, 1);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, cAxis);

        final boolean result = AxisUtils.hasChannelDimensions(imgPlus);

        assertTrue("Should be true when image has channel dimensions", result);
    }

    @Test
    public void testCountSpatialDimensionsNullSpace() throws Exception {
        final long result = AxisUtils.countSpatialDimensions(null);

        assertEquals("A null space should contain zero dimensions", 0, result);
    }

    @Test
    public void testCountSpatialDimensions() throws Exception {
        // Create a test image
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final long[] dimensions = {10, 10, 3};
        final Img<DoubleType> img = ArrayImgs.doubles(dimensions);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, channelAxis);

        final long result = AxisUtils.countSpatialDimensions(imgPlus);

        assertEquals("Wrong number of spatial dimensions", 2, result);
    }

    @Test
    public void testGetTimeIndexReturnMinusOneIfSpaceNull() throws AssertionError {
        final int timeIndex = AxisUtils.getTimeIndex(null);

        assertEquals("Index of time dimension is incorrect", -1, timeIndex);
    }

    @Test
    public void testGetTimeIndex() throws AssertionError {
        // Create a test image
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.TIME);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final long[] dimensions = {10, 3, 10};
        final Img<DoubleType> img = ArrayImgs.doubles(dimensions);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, channelAxis, yAxis);

        final int timeIndex = AxisUtils.getTimeIndex(imgPlus);

        assertEquals("Index of time dimension is incorrect", 1, timeIndex);
    }

    @Test
    public void testGetChannelIndexReturnMinusOneIfSpaceNull() throws AssertionError {
        final int channelIndex = AxisUtils.getChannelIndex(null);

        assertEquals("Index of channel dimension is incorrect", -1, channelIndex);
    }

    @Test
    public void testGetChannelIndex() throws AssertionError {
        // Create a test image
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final long[] dimensions = {10, 3, 10};
        final Img<DoubleType> img = ArrayImgs.doubles(dimensions);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, channelAxis, yAxis);

        final int channelIndex = AxisUtils.getChannelIndex(imgPlus);

        assertEquals("Index of channel dimension is incorrect", 1, channelIndex);
    }
}