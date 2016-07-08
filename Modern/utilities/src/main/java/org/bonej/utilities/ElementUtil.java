package org.bonej.utilities;

import net.imglib2.IterableInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nullable;

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
}
