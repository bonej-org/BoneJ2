package org.bonej.wrapperPlugins.wrapperUtils;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the {@link Common Common} utility class
 *
 * @author Richard Domander
 */
public class CommonTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testCopyMetadata() throws AssertionError {
        final String unit = "mm";
        final String name = "Test image";
        final double scale = 0.5;
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{3});
        final ImgPlus<DoubleType> source = new ImgPlus<>(img, name, xAxis);
        final ImgPlus<DoubleType> target = new ImgPlus<>(img);

        Common.copyMetadata(source, target);

        assertEquals("Image name was not copied", name, target.getName());
        assertEquals("Axis type was not copied", Axes.X, target.axis(0).type());
        assertEquals("Axis unit was not copied", unit, target.axis(0).unit());
        assertEquals("Axis scale was not copied", scale, target.axis(0).averageScale(0, 1), 1e-12);
    }
}