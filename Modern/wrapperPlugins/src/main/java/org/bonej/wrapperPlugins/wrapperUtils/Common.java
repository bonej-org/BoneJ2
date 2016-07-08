package org.bonej.wrapperPlugins.wrapperUtils;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;

/**
 * Miscellaneous utility methods
 *
 * @author Richard Domander
 */
public class Common {
    /** Copies image metadata such as name, axis types and calibrations from source to target */
    public static void copyMetadata(ImgPlus<?> source, ImgPlus<?> target) {
        target.setName(source.getName());

        final int dimensions = source.numDimensions();
        for (int d = 0; d < dimensions; d++) {
            final CalibratedAxis axis = source.axis(d);
            target.setAxis(axis, d);
        }
    }
}
