package org.bonej.wrapperPlugins;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the {@link WrapperUtils WrapperUtils} class.
 *
 * @author Richard Domander 
 */
public class WrapperUtilsTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testGetUnitHeaderReturnEmptyIfImageNull() throws Exception {
        final String result = WrapperUtils.getUnitHeader(null);

        assertTrue("Unit header should be empty", result.isEmpty());
    }

    @Test
    public void testGetUnitHeaderEmptyIfNoUnit() throws Exception {
        final DefaultLinearAxis axis = new DefaultLinearAxis(Axes.X);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", axis);

        final String result = WrapperUtils.getUnitHeader(imgPlus);

        assertTrue("Unit header should be empty", result.isEmpty());
    }

    @Test
    public void testGetUnitHeaderReturnEmptyIfDefaultUnitPixel() throws Exception {
        final DefaultLinearAxis axis = new DefaultLinearAxis(Axes.X, "pixel");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", axis);

        final String result = WrapperUtils.getUnitHeader(imgPlus);

        assertTrue("Unit header should be empty", result.isEmpty());
    }

    @Test
    public void testGetUnitHeaderReturnEmptyIfDefaultUnitUnit() throws Exception {
        final DefaultLinearAxis axis = new DefaultLinearAxis(Axes.X, "unit");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", axis);

        final String result = WrapperUtils.getUnitHeader(imgPlus);

        assertTrue("Unit header should be empty", result.isEmpty());
    }

    @Test
    public void testGetUnitHeader() throws Exception {
        final String unit = "mm";
        final String exponent = "³";
        final DefaultLinearAxis axis = new DefaultLinearAxis(Axes.X, unit);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", axis);

        final String result = WrapperUtils.getUnitHeader(imgPlus, exponent);

        assertEquals("Unexpected unit header", "(" + unit + exponent + ")", result);
    }
}