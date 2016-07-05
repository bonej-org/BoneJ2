package org.bonej.ops.thresholdFraction;

import net.imglib2.IterableInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * A helper class to pass threshold values as a single input. The thresholds fit a certain type of image.
 *
 * @author Richard Domander 
 */
public final class Thresholds<T extends NativeType<T> & RealType<T>> {
    public final T min;
    public final T max;

    /**
     * Constructor for Thresholds
     *
     * @param interval  Needed to determine the runtime type of the thresholds
     * @param min       Minimum value for elements within threshold
     * @param max       Maximum value for elements within threshold
     */
    public Thresholds(final IterableInterval<T> interval, final double min, final double max) {
        final T element = interval.firstElement();
        this.min = element.createVariable();
        this.min.setReal(min);
        this.max = element.createVariable();
        this.max.setReal(max);
    }

    /**
     * Constructor for Thresholds
     *
     * @param min       Minimum value for elements within threshold
     * @param max       Maximum value for elements within threshold
     */
    public Thresholds(final T min, final T max) {
        this.min = min;
        this.max = max;
    }
}
