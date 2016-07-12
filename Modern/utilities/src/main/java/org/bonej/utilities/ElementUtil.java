package org.bonej.utilities;

import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imglib2.Dimensions;
import net.imglib2.IterableInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nullable;

import static org.bonej.utilities.Streamers.spatialAxisStream;

/**
 * Various utility methods for inspecting image element properties
 *
 * @author Richard Domander
 */
public class ElementUtil {
    private ElementUtil() {}

    /**
     * Checks whether the interval contains only two distinct values
     *
     * @implNote A hacky brute force approach
     * @return True if only two distinct values, false if interval is null, empty or has more colors
     */
    @Contract("null -> false")
    public static boolean isColorsBinary(@Nullable final IterableInterval interval) {
        if (interval == null || interval.size() == 0) {
            return false;
        }

        if (BooleanType.class.isAssignableFrom(interval.firstElement().getClass())) {
            // by definition the elements can only be 0 or 1 so must be binary
            return true;
        }

        final long colours = Streamers.realDoubleStream(interval).distinct().count();

        return colours <= 2;
    }

    /**
     * Returns the calibrated size of a single spatial element in the given space,
     * e.g. the volume of an element in a 3D space
     *
     * @return Calibrated size of a spatial element, or Double.NaN if space == null,
     *         has nonlinear axes, or calibration units don't match
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> double calibratedSpatialElementSize(
            @Nullable final T space) {
        if (space == null || AxisUtils.hasNonLinearSpatialAxes(space) || !AxisUtils.spatialUnitsMatch(space)) {
            return Double.NaN;
        }

        return spatialAxisStream(space).map(a -> a.averageScale(0, 1)).reduce((x, y) -> x * y).orElse(0.0);
    }
}
