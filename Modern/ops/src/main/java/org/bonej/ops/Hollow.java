
package org.bonej.ops;

import java.util.Arrays;
import java.util.stream.LongStream;

import net.imagej.ops.Op;
import net.imagej.ops.special.hybrid.AbstractUnaryHybridCF;
import net.imglib2.Cursor;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.outofbounds.OutOfBounds;
import net.imglib2.roi.labeling.BoundingBox;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * The Op creates an output interval where the objects are hollow versions from
 * the input. Rectangles become outlines, solid cubes become surfaces etc.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class Hollow extends
	AbstractUnaryHybridCF<RandomAccessibleInterval<BitType>, RandomAccessibleInterval<BitType>>
{

	/**
	 * Controls whether elements on stack edges are considered outline or not
	 * <p>
	 * 
	 * <pre>
	 * For example, a 2D square:
	 * - - - -
	 * 1 1 1 -
	 * E 1 1 -
	 * 1 1 1 -
	 * - - - -
	 * </pre>
	 * 
	 * Element E is removed if parameter true, kept if false
	 * </p>
	 */
	@Parameter(required = false)
	private boolean excludeEdges = true;

	@Override
	public RandomAccessibleInterval<BitType> createOutput(
		final RandomAccessibleInterval<BitType> input)
	{
		final long[] dims = new long[input.numDimensions()];
		input.dimensions(dims);
		final FinalDimensions dimensions = new FinalDimensions(dims);
		return ops().create().img(dimensions, new BitType());
	}

	@Override
	public void compute(final RandomAccessibleInterval<BitType> input,
		final RandomAccessibleInterval<BitType> output)
	{
		final Cursor<BitType> cursor = Views.iterable(input).localizingCursor();
		final long[] coordinates = new long[input.numDimensions()];
		// Create an extended view so that we don't have to worry about going out of
		// bounds
		final ExtendedRandomAccessibleInterval<BitType, RandomAccessibleInterval<BitType>> extendedInput =
			Views.extendValue(input, new BitType(excludeEdges));
		final RandomAccess<BitType> outputAccess = output.randomAccess();
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(coordinates);
			if (isOutline(extendedInput, coordinates)) {
				outputAccess.setPosition(coordinates);
				outputAccess.get().set(cursor.get());
			}
		}
	}

	// region -- Helper methods --
	/**
	 * Creates a view that spans from (x-1, y-1, ... i-1) to (x+1, y+1, ... i+1)
	 * around the given coordinates
	 *
	 * @param interval the space of the coordinates
	 * @param coordinates coordinates (x, y, ... i)
	 * @return a view of a neighbourhood in the space
	 */
	private IntervalView<BitType> neighbourhoodInterval(
		final ExtendedRandomAccessibleInterval<BitType, RandomAccessibleInterval<BitType>> interval,
		final long[] coordinates)
	{
		final int dimensions = interval.numDimensions();
		final BoundingBox box = new BoundingBox(dimensions);
		final long[] minBounds = Arrays.stream(coordinates).map(c -> c - 1).toArray();
		final long[] maxBounds = Arrays.stream(coordinates).map(c -> c + 1).toArray();
		box.update(minBounds);
		box.update(maxBounds);
		return Views.offsetInterval(interval, box);
	}

	/** Checks if any element in the neighbourhood is background */
	private boolean isAnyBackground(final IntervalView<BitType> neighbourhood) {
		final Cursor<BitType> cursor = neighbourhood.cursor();
		while (cursor.hasNext()) {
			cursor.fwd();
			final BitType element = cursor.get();
			if (!element.get()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if an element is part of the outline of an object
	 *
	 * @param source the location of the element
	 * @param coordinates coordinates of the element
	 * @return true if element is foreground and has at least one background
	 *         neighbour
	 */
	private boolean isOutline(
		final ExtendedRandomAccessibleInterval<BitType, RandomAccessibleInterval<BitType>> source,
		final long[] coordinates)
	{
		final OutOfBounds<BitType> access = source.randomAccess();
		access.setPosition(coordinates);
		if (!access.get().get()) {
			return false;
		}

		final IntervalView<BitType> neighbourhood = neighbourhoodInterval(source,
			coordinates);
		return isAnyBackground(neighbourhood);
	}
	// endregion
}
