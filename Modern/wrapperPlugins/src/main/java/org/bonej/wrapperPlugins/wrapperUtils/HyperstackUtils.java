/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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


package org.bonej.wrapperPlugins.wrapperUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imglib2.EuclideanSpace;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace.HyperAxisMeta;

/**
 * A static class containing utilities for splitting an n-dimensional hyperstack
 * into arbitrary subspaces
 * <p>
 * Doesn't copy the subspaces, rather provides {@link Subspace} objects, which
 * contain a {@link RandomAccessibleInterval} that can be used to traverse a
 * certain subspace. Each {@link Subspace} also contains metadata, which locates
 * the subspace in the hyperspace.
 * </p>
 * <p>
 * The code works for now, but a refactor is in place if/when {@link ImgPlus}
 * API changes or is replaced by another metadata rich class.
 * </p>
 * <p>
 * If you want to split a hyperspace into {X, Y, T} subspaces, and the
 * hyperspace has more than one time dimension, *all* of the time dimensions
 * will be lumped into {X, Y, T<sub>1</sub>, T<sub>2</sub>, .. T<sub>n</sub>}
 * subspaces. That is, instead of {X, Y, T<sub>1</sub>}, {X, Y, T<sub>2</sub>},
 * .. {X, Y, T<sub>n</sub>}.
 * </p>
 * 
 * @author Richard Domander
 */
public final class HyperstackUtils {

	private HyperstackUtils() {}

	/**
	 * Splits the hyperstack into {X, Y, Z} subspaces.
	 * 
	 * @see #splitSubspaces(ImgPlus, Collection)
	 * @param hyperStack an N-dimensional image.
	 * @param <T> type of the elements in the image.
	 * @return a stream of all the subspaces found.
	 */
	public static <T extends RealType<T> & NativeType<T>> Stream<Subspace<T>>
		split3DSubspaces(final ImgPlus<T> hyperStack)
	{
		return splitSubspaces(hyperStack, Arrays.asList(Axes.X, Axes.Y, Axes.Z));
	}

	/**
	 * Splits the hyperstack into subspaces defined by the given axes.
	 * <p>
	 * If all the given axis types are not found in the hyperstack, gives
	 * subspaces of the found types. If none of the types are found, returns an
	 * empty stream. For example, if you want to split a {X, Y, C, T} hyperstack
	 * into {X, Y, Z}, returns all the {X, Y} subspaces.
	 * </p>
	 * <p>
	 * NB Assumes that the given {@link ImgPlus} has the necessary metadata, i.e.
	 * its {@link CalibratedAxis} have {@link AxisType}.
	 * </p>
	 *
	 * @param hyperStack an N-dimensional image.
	 * @param <T> type of the elements in the image.
	 * @param subspaceTypes the types of the axis in the desired subspace.
	 * @return a stream of all the subspaces found.
	 */
	public static <T extends RealType<T> & NativeType<T>> Stream<Subspace<T>>
		splitSubspaces(final ImgPlus<T> hyperStack,
			final Collection<AxisType> subspaceTypes)
	{
		final Builder<Subspace<T>> builder = Stream.builder();
		final int[] splitIndices = findSplitAxisIndices(hyperStack, subspaceTypes);
		final long[] typeSubscripts = mapTypeSubscripts(hyperStack, splitIndices);
		final int numSplits = splitIndices.length;
		final HyperAxisMeta[] subspaceMeta = new HyperAxisMeta[numSplits];
		final List<ValuePair<IntType, LongType>> split = new ArrayList<>();
		splitDims(hyperStack, splitIndices, numSplits - 1, subspaceMeta, split,
			typeSubscripts, builder);
		return builder.build();
	}

	// region -- Helper methods --

	/**
	 * Splits a subspace along the given coordinates
	 * <p>
	 * For example, if you have a 5D {X, Y, Z, C, T} hyperstack, and give the
	 * coordinates {{3, 0}, {4, 1}} you'll get a 3D {X, Y, Z} subspace of the
	 * first channel, and second time frame
	 * </p>
	 *
	 * @param hyperstack an n-dimensional image
	 * @param splitCoordinates (dimension, position) pairs describing the
	 *          hyperstack split
	 * @return The subspace interval
	 */
	private static <T extends RealType<T> & NativeType<T>>
		RandomAccessibleInterval<T> applySplit(
			final RandomAccessibleInterval<T> hyperstack,
			final Iterable<ValuePair<IntType, LongType>> splitCoordinates)
	{
		final List<ValuePair<IntType, LongType>> workingSplit = createWorkingCopy(
			splitCoordinates);
		RandomAccessibleInterval<T> slice = hyperstack;
		for (int i = 0; i < workingSplit.size(); i++) {
			final int dimension = workingSplit.get(i).a.get();
			final long position = workingSplit.get(i).b.get();
			slice = Views.hyperSlice(slice, dimension, position);
			decrementIndices(workingSplit, dimension);
		}
		return slice;
	}

	/**
	 * Clones and sorts the given {@link List}.
	 * <p>
	 * It ensures that original ones don't get altered while applying a split (see
	 * {@link #applySplit(RandomAccessibleInterval, Iterable)}). Pairs are sorted
	 * in the order of dimension ({@link ValuePair#a}).
	 * </p>
	 */
	private static List<ValuePair<IntType, LongType>> createWorkingCopy(
		final Iterable<ValuePair<IntType, LongType>> splitCoordinates)
	{
		final List<ValuePair<IntType, LongType>> workingSplit = new ArrayList<>();
		for (final ValuePair<IntType, LongType> pair : splitCoordinates) {
			final ValuePair<IntType, LongType> copy = new ValuePair<>(pair.a.copy(),
				pair.b);
			workingSplit.add(copy);
		}
		workingSplit.sort(Comparator.comparingInt(pair -> pair.a.get()));
		return workingSplit;
	}

	/**
	 * A helper method for
	 * {@link {@link #applySplit(RandomAccessibleInterval, Iterable)})} that
	 * ensures that it doesn't throw a {@link IndexOutOfBoundsException}
	 * <p>
	 * After calling {@link Views#hyperSlice(RandomAccessibleInterval, int, long)}
	 * on an n-dimensional {@link ImgPlus} the resulting
	 * {@link RandomAccessibleInterval} will have n-1 dimensions. Thus the
	 * dimension indices in splitCoordinates need to be decremented if they come
	 * after the index used in the split.
	 * </p>
	 *
	 * @param splitCoordinates (dimension, position) pairs describing a hyperspace
	 *          split
	 * @param dimension The index of the dimension in the last split
	 */
	private static void decrementIndices(
		final Iterable<ValuePair<IntType, LongType>> splitCoordinates,
		final int dimension)
	{
		for (final ValuePair<IntType, LongType> pair : splitCoordinates) {
			final IntType pairDimension = pair.getA();
			if (pairDimension.get() >= dimension) {
				pairDimension.dec();
			}
		}
	}

	/**
	 * Finds the axis used to split the hyperstack
	 * <p>
	 * For example, if you want to split a 5D {X, Y, C, Z, T} {@link ImgPlus} into
	 * 3D {X, Y, Z} subspaces, call with types = {Axes.X, Axes.Y, Axes.Z}
	 * </p>
	 *
	 * @param hyperstack An n-dimensional image
	 * @param types The axes that define the subspaces
	 * @return Indices of the axis used to split the hyperstack
	 */
	private static int[] findSplitAxisIndices(
		final AnnotatedSpace<CalibratedAxis> hyperstack,
		final Collection<AxisType> types)
	{
		final int n = hyperstack.numDimensions();
		return IntStream.range(0, n).filter(d -> !isAnyOfTypes(hyperstack.axis(d),
			types)).toArray();
	}

	private static boolean isAnyOfTypes(final TypedAxis axis,
		final Collection<AxisType> types)
	{
		return types.stream().anyMatch(t -> axis.type() == t);
	}

	private static boolean isEmptySubspace(final EuclideanSpace hyperSlice) {
		return hyperSlice.numDimensions() == 0;
	}

	/**
	 * Maps the type subscripts of axes in the hyperstack.
	 * <p>
	 * If the hyperstack has multiple axes of the same type, e.g. more than one
	 * time-axis, the subscripts are used to tell them apart. The default
	 * subscript for each axis type is one.
	 * </p>
	 *
	 * @param hyperStack the space to be split.
	 * @param splitIndices indices of the axes.
	 * @return An array of subscripts where [i] is the subscript for axis at
	 *         splitIndices[i]
	 */
	private static long[] mapTypeSubscripts(
		final AnnotatedSpace<CalibratedAxis> hyperStack, final int[] splitIndices)
	{
		final long[] subscripts = new long[splitIndices.length];
		final Map<AxisType, Integer> typeCounts = new HashMap<>();
		for (int i = 0; i < splitIndices.length; i++) {
			final int splitIndex = splitIndices[i];
			final AxisType type = hyperStack.axis(splitIndex).type();
			typeCounts.compute(type, (key, count) -> count == null ? 1 : count + 1);
			subscripts[i] = typeCounts.get(type);
		}
		return subscripts;
	}

	/**
	 * Recursively calls {@link #applySplit(RandomAccessibleInterval, Iterable)}
	 * to split the hyperstack into subspaces.
	 *
	 * @param hyperstack an n-dimensional image
	 * @param splitIndices the indices of the axes in the hyperstack used for
	 *          splitting
	 * @param splitIndex the i in splitIndices[i] currently used. Start from the
	 *          last index
	 * @param meta the metadata describing the position of the next subspace
	 * @param splitCoordinates the (dimension, position) pairs describing the
	 *          current split
	 * @param subscripts the subscripts of the axes see
	 * @param subspaces A builder for the stream of all the subspaces formed
	 */
	private static <T extends RealType<T> & NativeType<T>> void splitDims(
		final ImgPlus<T> hyperstack, final int[] splitIndices, final int splitIndex,
		final HyperAxisMeta[] meta,
		final List<ValuePair<IntType, LongType>> splitCoordinates,
		final long[] subscripts, final Builder<Subspace<T>> subspaces)
	{
		if (splitIndex < 0) {
			final RandomAccessibleInterval<T> subspace = applySplit(hyperstack,
				splitCoordinates);
			if (!isEmptySubspace(subspace)) {
				subspaces.add(new Subspace<>(subspace, meta));
			}
		}
		else {
			final int splitDimension = splitIndices[splitIndex];
			final AxisType type = hyperstack.axis(splitDimension).type();
			final long subscript = subscripts[splitIndex];
			final long size = hyperstack.dimension(splitDimension);
			final ValuePair<IntType, LongType> pair = new ValuePair<>(new IntType(
				splitDimension), new LongType());
			for (long position = 0; position < size; position++) {
				pair.b.set(position);
				splitCoordinates.add(pair);
				meta[splitIndex] = new HyperAxisMeta(type, position, subscript);
				splitDims(hyperstack, splitIndices, splitIndex - 1, meta,
					splitCoordinates, subscripts, subspaces);
				splitCoordinates.remove(pair);
			}
		}
	}
	// endregion

	// region -- Helper classes --

	/**
	 * A class which stores a subspace interval of an n-dimensional hyperspace,
	 * and metadata
	 * <p>
	 * The metadata describes the position of the subspace in the hyperspace.
	 * </p>
	 */
	public static final class Subspace<T extends RealType<T> & NativeType<T>> {

		public final RandomAccessibleInterval<T> interval;
		private final List<HyperAxisMeta> subspaceMeta;

		/**
		 * Creates a subspace record
		 *
		 * @param subspace An interval which defines a subspace
		 * @param subspaceMeta positions of the subspace in the hyperspace
		 */
		private Subspace(final RandomAccessibleInterval<T> subspace,
			final HyperAxisMeta[] subspaceMeta)
		{
			interval = subspace;
			if (subspaceMeta == null) {
				this.subspaceMeta = new ArrayList<>();
				return;
			}
			this.subspaceMeta = Arrays.stream(subspaceMeta).filter(Objects::nonNull)
				.collect(Collectors.toList());
		}

		/**
		 * Types of the additional hyperspace dimensions
		 * <p>
		 * For example a 3D {X, Y, Z} subspace of a 5D {X, Y, C, Z, T} hyperspace,
		 * would have types {Axes.CHANNEL, Axes.TIME}.
		 * </p>
		 *
		 * @return a stream of the types in order.
		 */
		public Stream<AxisType> getAxisTypes() {
			return subspaceMeta.stream().map(HyperAxisMeta::getType);
		}

		/**
		 * Position of the subspace in each additional dimension of the hyperspace.
		 * <p>
		 * For example, one 3D {X, Y, Z} subspace of a 5D {X, Y, C, Z, T}
		 * hyperspace, would have position {0, 1} - 1st channel, 2nd frame.
		 * </p>
		 *
		 * @return a stream of the positions in order.
		 */
		public LongStream getPosition() {
			return subspaceMeta.stream().mapToLong(HyperAxisMeta::getPosition);
		}

		/**
		 * Subscripts of the additional hyperspace dimensions
		 * <p>
		 * Subscripts identify multiple axis of the same type
		 * </p>
		 * <p>
		 * For example a 3D {X, Y} subspace of a 6D {X, Y, C, Z, T, T} hyperspace,
		 * would have subscripts {1, 1, 1, 2}.
		 * </p>
		 * 
		 * @return a stream of the positions in order.
		 */
		public LongStream getSubScripts() {
			return subspaceMeta.stream().mapToLong(HyperAxisMeta::getSubscript);
		}

		@Override
		public String toString() {
			return subspaceMeta.stream().map(HyperAxisMeta::toString).reduce((a,
				b) -> a + ", " + b).orElse("");
		}

		/**
		 * Describes the metadata of the subspace in relation to one of the axes in
		 * the hyperspace
		 */
		protected static final class HyperAxisMeta {

			/** {@link AxisType} of the dimension */
			private final AxisType type;
			/** The position of the subspace in the dimension */
			private final long position;
			/** An ID number to separate the dimension from others of the same type */
			private final long subscript;
			/**
			 * A number added to the position when it's printed in {@link #toString()}
			 */
			private final long stringOffset;

			private HyperAxisMeta(final AxisType type, final long position,
				final long subscript)
			{
				this.type = type;
				this.position = position;
				this.subscript = subscript;
				stringOffset = ResultUtils.toConventionalIndex(type, 0);
			}

			@Override
			public String toString() {
				final String typeIndex = subscript > 1 ? "(" + subscript + ")" : "";
				return type + typeIndex + ": " + (position + stringOffset);
			}

			private long getPosition() {
				return position;
			}

			private long getSubscript() {
				return subscript;
			}

			private AxisType getType() {
				return type;
			}
		}
	}
	// endregion
}
