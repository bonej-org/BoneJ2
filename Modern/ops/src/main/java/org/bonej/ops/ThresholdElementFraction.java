package org.bonej.ops;

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
 * @apiNote The plugin assumes that foregroundCutOff.compareTo(minThreshold) <= 0,
 *          and minThreshold.compareTo(maxThreshold) <= 0
 */
@Plugin(type = Op.class, name = "thresholdElementFraction")
public class ThresholdElementFraction<S, T extends Comparable<S>> extends
        AbstractBinaryFunctionOp<IterableInterval<T>, ThresholdElementFraction.Settings<S>, ThresholdElementFraction.Results> {

    @Override
    public Results compute2(final IterableInterval<T> interval, final Settings<S> settings) {
        final long elements = interval.size();
        final Stream<T> elementStream = StreamSupport.stream(interval.spliterator(), true);
        final long thresholdElements = elementStream
                .filter(e -> e.compareTo(settings.minThreshold) >= 0 && e.compareTo(settings.maxThreshold) <= 0)
                .count();

        return new Results(thresholdElements, elements);
    }

    /** A helper class to make the Op binary */
    public static final class Settings<S> {
        public final S minThreshold;
        public final S maxThreshold;

        public Settings(final S minThreshold, final S maxThreshold) {
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }
    }

    public static final class Results {
        public final long thresholdElements;
        public final long elements;
        public final double ratio;

        public Results(final long thresholdElements, final long elements) {
            this.thresholdElements = thresholdElements;
            this.elements = elements;
            ratio = 1.0 * thresholdElements / elements;
        }
    }
}
