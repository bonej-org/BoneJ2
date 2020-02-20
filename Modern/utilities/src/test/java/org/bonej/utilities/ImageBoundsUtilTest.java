package org.bonej.utilities;

import net.imglib2.Dimensions;
import net.imglib2.img.array.ArrayImgs;
import org.junit.Test;

import static org.bonej.utilities.ImageBoundsUtil.outOfBounds;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageBoundsUtilTest {
    @Test
    public void testBounds() {
        Dimensions dimensions = ArrayImgs.bits(100, 100, 100);
        long[] positionInside = {50, 50, 50};
        long[] positionTooLow = {-1, -1, -1};
        long[] positionTooHigh = {100, 100, 100};
        assertFalse(outOfBounds(dimensions, positionInside));
        assertTrue(outOfBounds(dimensions, positionTooLow));
        assertTrue(outOfBounds(dimensions, positionTooHigh));
    }
}
