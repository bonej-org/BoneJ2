package org.bonej.wrapperPlugins;

import net.imagej.axis.CalibratedAxis;
import net.imagej.space.AnnotatedSpace;
import org.bonej.utilities.AxisUtils;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Static utility methods for the wrapper plugins
 *
 * @author Richard Domander 
 */
public class WrapperUtils {
    private WrapperUtils() {}

    /** @see WrapperUtils#getUnitHeader(AnnotatedSpace, String) getUnitHeader(AnnotatedSpace, String) */
    public static <T extends AnnotatedSpace<CalibratedAxis>> String getUnitHeader(@Nullable final T space) {
        return getUnitHeader(space, "");
    }

    /**
     * Returns the unit used in the calibration of the space that can be shown in e.g. ResultsTable
     * <p>
     * Returns "(mm)" if calibration unit is "mm"
     *
     * @param exponent An exponent to be added to the unit
     * @return Unit for column headers or empty if there's no unit
     */
    public static <T extends AnnotatedSpace<CalibratedAxis>> String getUnitHeader(@Nullable final T space,
            @Nullable final String exponent) {
        final Optional<String> unit = AxisUtils.getSpatialUnit(space);
        if (!unit.isPresent()) {
            return "";
        }

        final String unitHeader = unit.get();
        if ("pixel".equalsIgnoreCase(unitHeader) || "unit".equalsIgnoreCase(unitHeader) || unitHeader.isEmpty()) {
            // Don't show default units
            return "";
        }

        return "(" + unitHeader + exponent + ")";
    }
}
