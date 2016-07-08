package org.bonej.ops.thresholdFraction;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.IterableInterval;
import org.scijava.plugin.Plugin;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Counts the total number of elements in the interval, elements within thresholds, and their fraction
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class, name = "elementFraction")
public class ElementFraction<S, T extends Comparable<S>> extends
        AbstractBinaryFunctionOp<IterableInterval<T>, ElementFraction.Settings<S>, ElementFraction.Results> {

    @Override
    public Results compute2(final IterableInterval<T> interval, final Settings<S> settings) {
        final long elements = interval.size();
        final Stream<T> elementStream = StreamSupport.stream(interval.spliterator(), false);
        final long thresholdElements = elementStream
                .filter(e -> e.compareTo(settings.minThreshold) >= 0 && e.compareTo(settings.maxThreshold) <= 0)
                .count();

        return new Results(thresholdElements, elements);
    }

    /** A helper class to pass inputs while keeping the Op binary */
    public static final class Settings<S> {
        /** Minimum value for elements within threshold */
        public final S minThreshold;
        /** Maximum value for elements within threshold */
        public final S maxThreshold;

        public Settings(final S minThreshold, final S maxThreshold) {
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }
    }

    /** A helper class for passing outputs */
    public static final class Results {
        /** Number of elements within thresholds */
        public final long thresholdElements;
        /** Total number of elements in the interval */
        public final long elements;
        /** Ratio of thresholdElements / elements */
        public final double ratio;

        public Results(final long thresholdElements, final long elements) {
            this.thresholdElements = thresholdElements;
            this.elements = elements;
            ratio = 1.0 * thresholdElements / elements;
        }
    }
}
