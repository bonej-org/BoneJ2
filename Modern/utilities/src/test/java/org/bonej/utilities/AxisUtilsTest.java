package org.bonej.utilities;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.axis.PowerAxis;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Arrays;
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
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
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
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
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
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10, 3, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, cAxis, zAxis);

        final Optional<int[]> result = AxisUtils.getXYZIndices(imgPlus);

        assertTrue("Optional should be present", result.isPresent());
        assertArrayEquals("Indices are incorrect", expectedIndices, result.get());
    }

    @Test
    public void testSpatialSpaceSizeNaNIfSpaceNull() throws Exception {
        final double result = AxisUtils.spatialSpaceSize(null);

        assertTrue("Size should be NaN when space is null", Double.isNaN(result));
    }

    @Test
    public void testSpatialSpaceSize() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final int[] dimSizes = {123, 12};
        final int expectedSize = Arrays.stream(dimSizes).reduce(1, (i, j) -> i * j);
        final Img<DoubleType> img = IMAGE_J.op().create().img(dimSizes);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = AxisUtils.spatialSpaceSize(imgPlus);

        assertEquals("Space size is incorrect", expectedSize, result, 1e-12);
    }

    @Test
    public void testCalibratedSpatialElementSizeNaNIfSpaceNull() throws Exception {
        final double result = AxisUtils.calibratedSpatialElementSize(null);

        assertTrue("Size should be NaN when space is null", Double.isNaN(result));
    }

    @Test
    public void testCalibratedSpatialElementSizeNaNIfNonLinear() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final PowerAxis yAxis = new PowerAxis(Axes.Y, 2);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = AxisUtils.calibratedSpatialElementSize(imgPlus);

        assertTrue("Size should be NaN when space has nonlinear axes", Double.isNaN(result));
    }

    @Test
    public void testCalibratedSpatialElementSizeNaNIfUnitsMismatch() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "cm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = AxisUtils.calibratedSpatialElementSize(imgPlus);

        assertTrue("Size should be NaN when the units of axes mismatch", Double.isNaN(result));
    }

    @Test
    public void testCalibratedSpatialElementSize() throws Exception {
        final double xScale = 1.5;
        final double yScale = 2.25;
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, xScale);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, yScale);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = AxisUtils.calibratedSpatialElementSize(imgPlus);

        assertEquals("Element size is wrong", xScale * yScale, result, 1e-12);
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
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{5, 5});
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
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{5, 5, 5});
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
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{5, 5, 5});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, cAxis);

        final boolean result = AxisUtils.hasChannelDimensions(imgPlus);

        assertTrue("Should be true when image has channel dimensions", result);
    }

    @Test
    public void testGetSpatialCalibrationAnisotropy() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, 2);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, 1);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = AxisUtils.getSpatialCalibrationAnisotropy(imgPlus);

        assertEquals(result, 1.0, 1e-12);
    }

    @Test
    public void testGetSpatialCalibrationAnisotropyReturnNaNWhenSpaceNull() throws Exception {
        final double result = AxisUtils.getSpatialCalibrationAnisotropy(null);

        assertTrue("Anisotropy should be NaN when space is null", Double.isNaN(result));
    }

    @Test
    public void testGetSpatialCalibrationAnisotropyReturnNaNWhenNoSpatialAxes() throws Exception {
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", tAxis);

        final double result = AxisUtils.getSpatialCalibrationAnisotropy(imgPlus);

        assertTrue("Anisotropy should be NaN when space has no spatial axes", Double.isNaN(result));
    }

    @Test
    public void testGetSpatialCalibrationAnisotropyReturnNaNWhenUnitsMismatch() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "cm");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = AxisUtils.getSpatialCalibrationAnisotropy(imgPlus);

        assertTrue("Anisotropy should be NaN when units don't match", Double.isNaN(result));
    }

    @Test
    public void testGetSpatialCalibrationAnisotropyReturnNaNWithNonlinearAxes() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final PowerAxis yAxis = new PowerAxis(Axes.Y, 2);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final double result = AxisUtils.getSpatialCalibrationAnisotropy(imgPlus);

        assertTrue("Anisotropy should be NaN when there are nonlinear axes", Double.isNaN(result));
    }

    @Test
    public void testGetSpatialUnit() throws Exception {
        final String unit = "mm";
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit);
        final PowerAxis yAxis = new PowerAxis(Axes.Y, unit, 0, 1, 2);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final Optional<String> result = AxisUtils.getSpatialUnit(imgPlus);

        assertTrue("Unit String should be present", result.isPresent());
        assertEquals("Unit String should be " + unit, unit, result.get());
    }

    @Test
    public void testGetSpatialUnitReturnEmptyIfAxesHaveDifferentUnits() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "cm");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final Optional<String> result = AxisUtils.getSpatialUnit(imgPlus);

        assertFalse("Optional should be empty when axes have different units", result.isPresent());
    }

    @Test
    public void testGetSpatialUnitReturnEmptyIfSomeAxesUncalibrated() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, null);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis);

        final Optional<String> result = AxisUtils.getSpatialUnit(imgPlus);

        assertFalse("Optional should be empty when some axes are uncalibrated", result.isPresent());
    }

    @Test
    public void testGetSpatialUnitReturnEmptyIfSpaceNull() throws Exception {
        final Optional<String> result = AxisUtils.getSpatialUnit(null);

        assertFalse("Optional should be empty when space is null", result.isPresent());
    }

    @Test
    public void testGetSpatialUnitReturnEmptyStringIfAllAxesUncalibrated() {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, null);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final Optional<String> result = AxisUtils.getSpatialUnit(imgPlus);

        assertTrue("Optional should be present when all axes are uncalibrated", result.isPresent());
        assertTrue("The unit should be an empty string (uncalibrated)", result.get().isEmpty());
    }

    @Test
    public void testGetSpatialUnitReturnEmptyIfNoSpatialAxes() throws Exception {
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", tAxis);

        final Optional<String> result = AxisUtils.getSpatialUnit(imgPlus);

        assertFalse("Optional should be empty when space has no spatial axes", result.isPresent());
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
        final int[] dimensions = {10, 10, 3};
        final Img<DoubleType> img = IMAGE_J.op().create().img(dimensions);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, channelAxis);

        final long result = AxisUtils.countSpatialDimensions(imgPlus);

        assertEquals("Wrong number of spatial dimensions", 2, result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicReturnsFalseIfSpaceNull() throws Exception {
        final boolean result = AxisUtils.isSpatialCalibrationIsotropic(null);

        assertFalse("Calibration should not be isotropic if space is null", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicFalseIfNonLinearAxis() throws Exception {
        // Create a test image with a nonlinear spatial axis
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final PowerAxis yAxis = new PowerAxis(Axes.Y, 2);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final boolean result = AxisUtils.isSpatialCalibrationIsotropic(imgPlus);

        assertFalse("Calibration should not be isotropic if there's a nonlinear spatial axis", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicFalseIfNoSpatialAxes() throws Exception {
        // Create a test image with no spatial axes
        final DefaultLinearAxis timeAxis = new DefaultLinearAxis(Axes.TIME);
        final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 3});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", timeAxis, channelAxis);

        final boolean result = AxisUtils.isSpatialCalibrationIsotropic(imgPlus);

        assertFalse("Calibration should not be isotropic if there are no spatial axes", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicFalseIfUnitsMismatch() throws Exception {
        // Create a test image with no spatial axes
        final DefaultLinearAxis timeAxis = new DefaultLinearAxis(Axes.X, "cm");
        final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.Y, "mm");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 3});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", timeAxis, channelAxis);

        final boolean result = AxisUtils.isSpatialCalibrationIsotropic(imgPlus);

        assertFalse("Calibration should not be isotropic if there are no spatial axes", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropicFalseIfScalesNotWithinTolerance() throws Exception {
        // Create a test image with anisotropic calibration
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, 1000.0);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final boolean result = AxisUtils.isSpatialCalibrationIsotropic(imgPlus, 1.0);

        assertFalse("Calibration should not be isotropic if anisotropy is not within tolerance", result);
    }

    @Test
    public void testIsSpatialCalibrationIsotropic() throws Exception {
        // Create a test image with anisotropic calibration (within tolerance)
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, 1.1);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

        final boolean result = AxisUtils.isSpatialCalibrationIsotropic(imgPlus, 0.1);

        assertTrue("Calibration should be isotropic when anisotropy is within tolerance", result);
    }
}