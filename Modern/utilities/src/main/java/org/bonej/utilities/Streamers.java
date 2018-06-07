
package org.bonej.utilities;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imglib2.type.numeric.RealType;

/**
 * Utility functions to generate streams from various ImageJ2 collections
 *
 * @author Richard Domander
 */
public final class Streamers {

	private Streamers() {}

	/**
	 * Generates a {@link Stream} from the axes in the given space.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of the axes.
	 * @return a Stream of the axes. An empty stream if space == null or space has
	 *         no axes.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> Stream<A>
		axisStream(final S space)
	{
		if (space == null) {
			return Stream.empty();
		}

		final int dimensions = space.numDimensions();
		final Stream.Builder<A> builder = Stream.builder();
		for (int d = 0; d < dimensions; d++) {
			builder.add(space.axis(d));
		}

		return builder.build();
	}

	/**
	 * Generates a Stream from the realDouble values in the Iterable.
	 *
	 * @param iterable an iterable collection.
	 * @param <T> type of the elements in the iterable.
	 * @return a DoubleStream of the realDouble values, or empty stream if
	 *         iterable == null.
	 */
	public static <T extends RealType<T>> DoubleStream realDoubleStream(
		final Iterable<T> iterable)
	{
		if (iterable == null) {
			return DoubleStream.empty();
		}

		return StreamSupport.stream(iterable.spliterator(), false).mapToDouble(
			RealType::getRealDouble);
	}

	/**
	 * Generates a {@link Stream} from the spatial axes in the given space.
	 * 
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of the axes.
	 * @return a Stream of spatial axes. An empty stream if space == null.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> Stream<A>
		spatialAxisStream(final S space)
	{
		return axisStream(space).filter(a -> a.type().isSpatial());
	}
}
