package org.bonej.ops.thresholdFraction;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.FinalDimensions;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.LongType;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Iterator;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the {@link SurfaceMask SurfaceMask} class
 *
 * @author Richard Domander 
 */
public class SurfaceMaskTest {
    private final static ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConforms() throws Exception {
        final Img<LongType> img = ArrayImgs.longs(1, 1);
        final Thresholds<LongType> thresholds = new Thresholds<>(img, 0.0, 1.0);

        IMAGE_J.op().run(SurfaceMask.class, img, thresholds);
    }

    @Test
    public void testCompute2() throws Exception {
        // Create a 2x2x2 (x, y, z) ImgPlus with values from 0 to 7
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final Img<LongType> img = IMAGE_J.op().create().img(new FinalDimensions(2, 2, 2), new LongType());
        final ImgPlus<LongType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis, zAxis);
        final Iterator<Long> longIterator = LongStream.iterate(0, i -> (i + 1) % 8).iterator();
        imgPlus.cursor().forEachRemaining(e -> e.set(longIterator.next()));

        // Create a mask from voxels whose values are between 2 and 5
        final Thresholds<LongType> thresholds = new Thresholds<>(imgPlus, 2.0, 5.0);
        final Img<BitType> mask = (Img<BitType>) IMAGE_J.op().run(SurfaceMask.class, imgPlus, thresholds);
        final long voxels = StreamSupport.stream(mask.spliterator(), false).filter(BitType::get).count();

        assertEquals("Incorrect number of foreground voxels in the mask", 4, voxels);
    }
}