package org.bonej.ops.thresholdFraction;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.FinalDimensions;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.LongType;
import org.bonej.ops.thresholdFraction.ThresholdElementFraction.Results;
import org.bonej.ops.thresholdFraction.ThresholdElementFraction.Settings;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Iterator;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the {@link ThresholdElementFraction ThresholdElementFraction} class
 *
 * @author Richard Domander
 */
public class ThresholdElementFractionTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testCompute2() {
        // Create a 2x2x2x2x2 test image with values from 0 to 7
        final Img<LongType> img = IMAGE_J.op().create().img(new FinalDimensions(2, 2, 2, 2, 2), new LongType());
        final ImgPlus<LongType> imgPlus = new ImgPlus<>(img);
        final Settings<LongType> settings = new Settings<>(new LongType(2L), new LongType(5L));
        final Iterator<Long> longIterator = LongStream.iterate(0, i -> (i + 1) % 8).iterator();
        imgPlus.cursor().forEachRemaining(e -> e.set(longIterator.next()));

        final Results result = (Results) IMAGE_J.op().run(ThresholdElementFraction.class, imgPlus, settings);

        assertEquals("Number of elements within thresholds is incorrect", 16, result.thresholdElements);
        assertEquals("Total number of elements is incorrect", 32, result.elements);
        assertEquals("Ratio of elements is incorrect", 0.5, result.ratio, 1e-12);
    }
}