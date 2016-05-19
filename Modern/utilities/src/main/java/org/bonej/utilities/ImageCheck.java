package org.bonej.utilities;

import com.google.common.base.Strings;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.LinearAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imglib2.IterableInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.RealType;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Various utility methods for inspecting image properties
 *
 * @author Richard Domander
 */
public class ImageCheck {
    private ImageCheck() {}

    /**
     * Returns the unit of the spatial calibration of the given space
     *
     * @return The Optional is empty if the space == null, or the units of the axes in the space don't match.
     *         The Optional contains an empty string if all the axes are uncalibrated
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> Optional<String> getSpatialUnit(@Nullable final T space) {
        if (space == null || countSpatialDimensions(space) == 0 || !spatialUnitsMatch(space)) {
            return Optional.empty();
        }

        return Optional.of(space.axis(0).unit());
    }

    private static <T extends AnnotatedSpace<CalibratedAxis>> boolean spatialUnitsMatch(final T space) {
        final long spatialDimensions = countSpatialDimensions(space);
        final long uncalibrated =
                spatialAxisStream(space).map(CalibratedAxis::unit).filter(Strings::isNullOrEmpty).count();
        final long units = spatialAxisStream(space).map(CalibratedAxis::unit).distinct().count();

        return uncalibrated == spatialDimensions || units == 1;
    }

    /**
     * Checks whether the interval contains only two distinct values
     *
     * @implNote A hacky brute force approach
     * @return True if only two distinct values, false if interval is null, empty or has more colors
     */
    public static <T extends RealType<T>> boolean isColorsBinary(@Nullable final IterableInterval<T> interval) {
        if (interval == null || interval.size() == 0) {
            return false;
        }

        if (BooleanType.class.isAssignableFrom(interval.firstElement().getClass())) {
            // by definition the elements can only be 0 or 1 so must be binary
            return true;
        }

        final long colours =
                StreamSupport.stream(interval.spliterator(), false).mapToDouble(RealType::getRealDouble).distinct()
                        .count();

        return colours <= 2;
    }

    /**
     * Counts the number of spatial dimensions in the given space
     *
     * @return Number of spatial dimensions in the space, or 0 if space == null
     */
    public static <T extends AnnotatedSpace<S>, S extends TypedAxis> long countSpatialDimensions(
            @Nullable final T space) {
        return spatialAxisStream(space).count();
    }

    /**
     * Generates a Stream from the axes in the given space
     *
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
    public static <T extends AnnotatedSpace<CalibratedAxis>> boolean isSpatialCalibrationIsotropic(
            @Nullable final T space) {
        return isSpatialCalibrationIsotropic(space, 0.0);
    }

    /**
     * Checks if the linear, spatial dimensions in the given space are isotropic. Isotropic means that the calibration
     * of the different axes vary only within tolerance.
     *
     * @param tolerance How many percent the calibration may vary ([0.0, 1.0]) for the space to still be isotropic
     * @implNote tolerance is clamped to [0.0, 1.0]
     * @return true if the scales of all linear spatial axes in the space are within tolerance of each other,
     *         i.e. the space is isotropic. False if not, or space == null, or there are no spatial axes
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> boolean isSpatialCalibrationIsotropic(
            @Nullable final T space,
            double tolerance) {
        if (space == null || hasNonLinearSpatialAxes(space) || !spatialUnitsMatch(space)) {
            return false;
        }

        if (tolerance < 0.0) {
            tolerance = 0.0;
        } else if (tolerance > 1.0) {
            tolerance = 1.0;
        }

        final double[] scales = spatialAxisStream(space).mapToDouble(a -> a.averageScale(0, 1)).distinct().toArray();
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

    /**
     * Returns the maximum difference between the scales of the calibrated axes in the given space
     *
     * @return The difference in scaling. Returns Double.NaN if space == null, or it has non-linear spatial axes,
     *         or calibration units don't match, or if there are no spatial axes
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> double getSpatialCalibrationAnisotropy(
            @Nullable final T space) {
        if (space == null || hasNonLinearSpatialAxes(space) || !spatialUnitsMatch(space)) {
            return Double.NaN;
        }

        final double[] scales = spatialAxisStream(space).mapToDouble(a -> a.averageScale(0, 1)).distinct().toArray();
        if (scales.length == 0) {
            return Double.NaN;
        }

        double maxDifference = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < scales.length - 1; i++) {
            for (int j = i + 1; j < scales.length; j++) {
                final double difference = Math.abs(scales[i] - scales[j]);
                if (difference > maxDifference) {
                    maxDifference = difference;
                }
            }
        }

        return maxDifference;
    }

    private static <T extends AnnotatedSpace<S>, S extends TypedAxis> Stream<S> spatialAxisStream(
            @Nullable final T space) {
        return axisStream(space).filter(a -> a.type().isSpatial());
    }

    private static <T extends AnnotatedSpace<CalibratedAxis>> boolean hasNonLinearSpatialAxes(final T space) {
        return axisStream(space).anyMatch(a -> !(a instanceof LinearAxis) && a.type().isSpatial());
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
