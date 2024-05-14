/*-
 * #%L
 * Utility methods for BoneJ2
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.utilities;

import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;

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
	 * @return a Stream of all the axes in the space.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> Stream<A>
		axisStream(final S space)
	{
		final int dimensions = space.numDimensions();
		final Builder<A> builder = Stream.builder();
		for (int d = 0; d < dimensions; d++) {
			builder.add(space.axis(d));
		}

		return builder.build();
	}

	/**
	 * Generates a {@link Stream} from the spatial axes in the given space.
	 * 
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of the axes.
	 * @return a Stream of spatial axes.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> Stream<A>
		spatialAxisStream(final S space)
	{
		return axisStream(space).filter(a -> a.type().isSpatial());
	}
}
