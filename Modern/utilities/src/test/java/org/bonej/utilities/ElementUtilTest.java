package org.bonej.utilities;

import net.imagej.ImageJ;
import net.imglib2.IterableInterval;
import net.imglib2.type.numeric.real.DoubleType;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Iterator;
import java.util.stream.IntStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the ImageCheck utility class
 *
 * @author Richard Domander
 */
public class ElementUtilTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testIsColorsBinaryFalseWhenIntervalNull() throws Exception {
        final boolean result = ElementUtil.isColorsBinary(null);

        assertFalse("A null interval should not be binary color", result);
    }

    @Test
    public void testIsColorsBinaryReturnsFalseIfIntervalEmpty() throws Exception {
        final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{0});

        final boolean result = ElementUtil.isColorsBinary(interval);

        assertFalse("An empty image should not be binary color", result);
    }

    @Test
    public void testIsColorsBinaryReturnsTrueForMonochrome() throws Exception {
        final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});

        final boolean result = ElementUtil.isColorsBinary(interval);

        assertTrue("Monochrome image should be binary color", result);
    }

    @Test
    public void testIsColorsBinaryReturnsTrueForDichromatic() throws Exception {
        // Create a test image with two colors
        final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});
        final Iterator<Integer> intIterator = IntStream.iterate(0, i -> (i + 1) % 2).iterator();
        interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

        final boolean result = ElementUtil.isColorsBinary(interval);

        assertTrue("An image with two colours should be binary color", result);
    }

    @Test
    public void testIsColorsBinaryReturnsFalseForMulticolor() throws Exception {
        // Create a test image with many colors
        final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});
        final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i + 1).iterator();
        interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

        final boolean result = ElementUtil.isColorsBinary(interval);

        assertFalse("An image with more than two colours should not be binary color", result);
    }
}