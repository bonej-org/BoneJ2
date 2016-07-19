package org.bonej.utilities;

import net.imagej.axis.CalibratedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;
import net.imglib2.IterableInterval;
import net.imglib2.type.BooleanType;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nullable;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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
            @Nullable final T space, final UnitService unitService) {
        if (AxisUtils.hasNonLinearSpatialAxes(space)) {
            return Double.NaN;
        }

        try {
            final String unit = AxisUtils.getSpatialUnit(space, unitService).get().replaceFirst("^µ[mM]$", "um");
            if (unit.isEmpty()) {
                return spatialAxisStream(space).map(a -> a.averageScale(0, 1)).reduce((x, y) -> x * y).orElse(0.0);
            }

            final List<CalibratedAxis> axes = spatialAxisStream(space).collect(Collectors.toList());
            double elementSize = axes.get(0).averageScale(0.0, 1.0);
            for (int i = 1; i < axes.size(); i++) {
                double scale = axes.get(i).averageScale(0.0, 1.0);
                final String axisUnit = axes.get(i).unit().replaceFirst("^µ[mM]$", "um");
                final double axisSize = unitService.value(scale, axisUnit, unit);
                elementSize *= axisSize;
            }

            return elementSize;
        } catch (NoSuchElementException e) {
            return Double.NaN;
        }
    }
}
