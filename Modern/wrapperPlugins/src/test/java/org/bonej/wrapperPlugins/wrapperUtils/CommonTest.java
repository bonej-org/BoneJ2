package org.bonej.wrapperPlugins.wrapperUtils;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the {@link Common} utility class
 *
 * @author Richard Domander
 */
public class CommonTest {
    public static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testConvertWithMetadata() throws AssertionError {
        final String unit = "mm";
        final String name = "Test image";
        final double scale = 0.5;
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
        final Img<DoubleType> img = ArrayImgs.doubles(3);
        final ImgPlus<DoubleType> source = new ImgPlus<>(img, name, xAxis);

        final ImgPlus<BitType> result = Common.toBitTypeImgPlus(IMAGE_J.op(), source);

        final int dimensions = source.numDimensions();
        assertEquals("Number of dimensions copied incorrectly", dimensions, result.numDimensions());
        assertTrue("Dimensions copied incorrectly",
                IntStream.range(0, dimensions).allMatch(d -> source.dimension(d) == result.dimension(d)));
        assertEquals("Image name was not copied", name, result.getName());
        assertEquals("Axis type was not copied", Axes.X, result.axis(0).type());
        assertEquals("Axis unit was not copied", unit, result.axis(0).unit());
        assertEquals("Axis scale was not copied", scale, result.axis(0).averageScale(0, 1), 1e-12);
    }
}