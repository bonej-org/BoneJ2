package org.bonej.utilities;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VectorUtilTest {
    @Test
    public void testToPixelGrid() {
        final Vector3dc v = new Vector3d(1.7, 0.3, 2.5);
        final long[] expected = new long[]{1L,0L,2L};
        final long[] pixel = VectorUtil.toPixelGrid(v);

        assertTrue(pixel[0]==expected[0]);
        assertTrue(pixel[1]==expected[1]);
        assertTrue(pixel[2]==expected[2]);
    }
}
