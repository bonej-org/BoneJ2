package org.bonej.utilities;

import net.imagej.axis.CalibratedAxis;
import net.imagej.space.AnnotatedSpace;

/**
 * Various utility methods for inspecting image properties
 *
 * @author Richard Domander 
 */
public class ImageCheck {
    private ImageCheck() {}

    public static <T extends AnnotatedSpace<CalibratedAxis>> long countSpatialDimensions(final T space) {
        long spatialDimensions = 0;

        final CalibratedAxis[] axes = new CalibratedAxis[space.numDimensions()];
        space.axes(axes);

        for (CalibratedAxis axis : axes) {
            if (axis.type().isSpatial()) {
                spatialDimensions++;
            }
        }

        return spatialDimensions;
    }
}
