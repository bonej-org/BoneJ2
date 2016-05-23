package org.bonej.utilities;

import com.google.common.base.Strings;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.LinearAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Various utils for inspecting image axis properties
 *
 * @author Richard Domander 
 */
public class AxisUtils {
    /**
     * Counts the number of spatial dimensions in the given space
     *
     * @return Number of spatial dimensions in the space, or 0 if space == null
     */
    public static <T extends AnnotatedSpace<S>, S extends TypedAxis> long countSpatialDimensions(
            @Nullable final T space) {
        return Streamers.spatialAxisStream(space).count();
    }

    /**
     * Returns the maximum difference between the scales of the calibrated axes in the given space
     *
     * @return The difference in scaling. Returns Double.NaN if space == null, or it has non-linear spatial axes,
     *         or calibration units don't match, or if there are no spatial axes
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> double getSpatialCalibrationAnisotropy(
            @Nullable final T space) {
        if (!getSpatialUnit(space).isPresent() || hasNonLinearSpatialAxes(space)) {
            return Double.NaN;
        }

        final double[] scales =
                Streamers.spatialAxisStream(space).mapToDouble(a -> a.averageScale(0, 1)).distinct().toArray();

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

    /**
     * Returns the unit of the spatial calibration of the given space
     *
     * @return The Optional is empty if the space == null, or units don't match, or there are no spatial axes
     *         The Optional contains an empty string if all the axes are uncalibrated
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> Optional<String> getSpatialUnit(@Nullable final T space) {
        if (space == null || !hasSpatialDimensions(space) || !spatialUnitsMatch(space)) {
            return Optional.empty();
        }

        String unit = space.axis(0).unit();
        return unit == null ? Optional.of("") : Optional.of(space.axis(0).unit());
    }

    /**
     * Checks if the given space has a channel dimension
     *
     * @return true if for any axis CalibratedAxis.type() == Axes.CHANNEL,
     *         false if not, or space == null
     */
    public static <T extends AnnotatedSpace<S>, S extends TypedAxis> boolean hasChannelDimensions(
            @Nullable final T space) {
        return Streamers.axisStream(space).anyMatch(a -> a.type() == Axes.CHANNEL);
    }

    public static <T extends AnnotatedSpace<S>, S extends TypedAxis> boolean hasSpatialDimensions(
            @Nullable final T space) {
        return Streamers.axisStream(space).anyMatch(a -> a.type().isSpatial());
    }

    /**
     * Checks if the given space has a time dimension
     *
     * @return true if for any axis CalibratedAxis.type() == Axes.TIME,
     *         false if not, or space == null
     */
    public static <T extends AnnotatedSpace<S>, S extends TypedAxis> boolean hasTimeDimensions(
            @Nullable final T space) {
        return Streamers.axisStream(space).anyMatch(a -> a.type() == Axes.TIME);
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
     * @return true if the scales of all linear spatial axes in the space are within tolerance of each other.
     *         false if space is null, there are no linear spatial axes, or axes are not within tolerance,
     *         or their units don't match
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> boolean isSpatialCalibrationIsotropic(
            @Nullable final T space,
            double tolerance) {
        if (!getSpatialUnit(space).isPresent() || hasNonLinearSpatialAxes(space)) {
            return false;
        }

        if (tolerance < 0.0) {
            tolerance = 0.0;
        } else if (tolerance > 1.0) {
            tolerance = 1.0;
        }

        final double[] scales =
                Streamers.spatialAxisStream(space).mapToDouble(a -> a.averageScale(0, 1)).distinct().toArray();

        for (int i = 0; i < scales.length - 1; i++) {
            for (int j = i + 1; j < scales.length; j++) {
                if (!withinTolerance(scales[i], scales[j], tolerance)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static <T extends AnnotatedSpace<S>, S extends TypedAxis> boolean hasNonLinearSpatialAxes(final T space) {
        return Streamers.axisStream(space).anyMatch(a -> !(a instanceof LinearAxis) && a.type().isSpatial());
    }

    private static <T extends AnnotatedSpace<CalibratedAxis>> boolean spatialUnitsMatch(final T space) {
        final boolean allUncalibrated =
                Streamers.spatialAxisStream(space).map(CalibratedAxis::unit).allMatch(Strings::isNullOrEmpty);
        final long units = Streamers.spatialAxisStream(space).map(CalibratedAxis::unit).distinct().count();

        return allUncalibrated || units == 1;
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
