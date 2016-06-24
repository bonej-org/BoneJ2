package org.bonej.utilities;

import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imglib2.type.numeric.RealType;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nullable;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility functions to generate streams from various ImageJ2 collections
 *
 * @author Richard Domander 
 */
public class Streamers {
    private Streamers() {}

    /**
     * Generates a Stream from the axes in the given space
     *
     * @return A Stream<S> of the axes. An empty stream if space == null or space has no axes
     */
    @Contract("null -> !null")
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
     * Generates a Stream from the realDouble values in the Iterable of T extends RealType<T>
     *
     * @return A DoubleStream of the realDouble values, or empty stream if iterable == null
     */
    @Contract("null -> !null")
    public static <T extends RealType<T>> DoubleStream realDoubleStream(@Nullable Iterable<T> iterable) {
        if (iterable == null) {
            return DoubleStream.empty();
        }

        return StreamSupport.stream(iterable.spliterator(), false).mapToDouble(RealType::getRealDouble);
    }

    /**
     * Generates a Stream from the spatial axes in the given space
     *
     * @return A Stream<S> of spatial axes. An empty stream if space == null
     */
    public static <T extends AnnotatedSpace<S>, S extends TypedAxis> Stream<S> spatialAxisStream(
            @Nullable final T space) {
        return axisStream(space).filter(a -> a.type().isSpatial());
    }
}
