package org.bonej.utilities;

import net.imglib2.Dimensions;

public final class ImageBoundsUtil {

    public static boolean outOfBounds(final Dimensions dimensions, final long[] currentPixelPosition) {
        for (int i = 0; i < currentPixelPosition.length; i++) {
            final long position = currentPixelPosition[i];
            if (position < 0 || position >= dimensions.dimension(i)) {
                return true;
            }
        }
        return false;
    }
}