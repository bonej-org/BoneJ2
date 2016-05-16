package org.bonej.utilities;

import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.LinearAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.RealType;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Various utility methods for inspecting image properties
 *
 * @author Richard Domander
 */
public class ImageCheck {
    private ImageCheck() {
    }

    /**
     * Checks whether the interval contains only two distinct values
     *
     * @implNote A hacky brute force approach
     * @return True if only two distinct values, false if interval is null, empty or has more colors
     */
    public static <T extends RealType<T>> boolean isColoursBinary(@Nullable final IterableInterval<T> interval) {
        if (interval == null || interval.size() == 0) {
            return false;
        }

        if (BooleanType.class.isAssignableFrom(interval.firstElement().getClass())) {
            // by definition the elements can only be 0 or 1 so must be binary
            return true;
        }

        final Cursor<T> cursor = interval.cursor();
        final TreeSet<Double> values = new TreeSet<>();

        while (cursor.hasNext()) {
            final double value = cursor.next().getRealDouble();
            values.add(value);
            if (values.size() > 2) {
                return false;
            }
        }

        return true;
    }

    /**
     * Counts the number of spatial dimensions in the given space
     * @return Number of spatial dimensions in the space, or 0 if space == null
     */
    public static <T extends AnnotatedSpace<S>, S extends TypedAxis> long countSpatialDimensions(
            @Nullable final T space) {
        return axisStream(space).filter(a -> a.type().isSpatial()).count();
    }

    /**
     * Generates a Stream from the axes in the given space
     * @return A Stream<S> of the axes. An empty stream if space == null or space has no axes
     */
    public static <T extends AnnotatedSpace<S>, S extends TypedAxis> Stream<S> axisStream(@Nullable final T space) {
        if (space == null) {
            return Stream.empty();
        }

        final int dimensions = space.numDimensions();
        final Stream.Builder<S> builder = Stream.builder();
        for (int d = 0; d < dimensions; d++) {
            builder.add(space.axis(d));
        }

        return builder.build();
    }

    /**
     * Calls isSpatialCalibrationIsotropic(AnnotatedSpace, 0.0)
     *
     * @see #isSpatialCalibrationIsotropic(AnnotatedSpace, double)
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> boolean isSpatialCalibrationIsotropic(final T space) {
        return isSpatialCalibrationIsotropic(space, 0.0);
    }

    /**
     * Checks if the calibration of the linear, spatial axes in the space is isotropic (within tolerance)
     *
     * @param tolerance How many percent the calibration may vary ([0.0, 1.0]) for the space to still be isotropic
     * @implNote tolerance is clamped to [0.0, 1.0]
     * @return true if the scales of all linear spatial axes in the space are within tolerance of each other,
     *         i.e. the space is isotropic. False if not, or space == null
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> boolean isSpatialCalibrationIsotropic(final T space,
            double tolerance) {
        if (space == null) {
            return false;
        }

        if (tolerance < 0.0) {
            tolerance = 0.0;
        } else if (tolerance > 1.0) {
            tolerance = 1.0;
        }

        final Optional<CalibratedAxis> nonLinearAxis =
                axisStream(space).filter(a -> !(a instanceof LinearAxis) && a.type().isSpatial()).findAny();
        if (nonLinearAxis.isPresent()) {
            return false;
        }

        final double[] scales =
                axisStream(space).filter(a -> a.type().isSpatial()).mapToDouble(a -> a.averageScale(0, 1)).distinct()
                        .toArray();
        if (scales.length == 0) {
            return false;
        }

        for (int i = 0; i < scales.length - 1; i++) {
            for (int j = i + 1; j < scales.length; j++) {
                if (!withinTolerance(scales[i], scales[j], tolerance)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean withinTolerance(double a, double b, final double tolerance) {
        if (b > a) {
            double tmp = a;
            a = b;
            b = tmp;
        }

        if (Double.compare(a, b * (1.0 - tolerance)) < 0) {
            return false;
        } else if (Double.compare(a, b * (1.0 + tolerance)) > 0) {
            return false;
        }
        return true;
    }
}
