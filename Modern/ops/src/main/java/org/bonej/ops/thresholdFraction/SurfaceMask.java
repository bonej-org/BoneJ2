
package org.bonej.ops.thresholdFraction;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

import org.scijava.plugin.Plugin;

/**
 * An Op which creates an Img<BitType> mask of the given image. The mask can be
 * used to create a surface mesh with a marching cubes Op.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class, name = "surfaceMask")
public class SurfaceMask<T extends NativeType<T> & RealType<T>> extends
	AbstractBinaryFunctionOp<RandomAccessibleInterval<T>, Thresholds<T>, RandomAccessibleInterval<BitType>>
	implements Contingent
{

	@Override
	public boolean conforms() {
		return in1().numDimensions() >= 3;
	}

	/**
	 * Creates a surface mask
	 *
	 * @param interval A RAI where the first three dimensions are spatial
	 * @param thresholds Thresholds whose type matches that of the elements in the
	 *          interval
	 * @return A three dimensional mask that can be given to a marching cubes op
	 */
	@Override
	public RandomAccessibleInterval<BitType> calculate(
		final RandomAccessibleInterval<T> interval,
		final Thresholds<T> thresholds)
	{
		final long width = interval.dimension(0);
		final long height = interval.dimension(1);
		final long depth = interval.dimension(2);
		final Dimensions dimensions = new FinalDimensions(width, height, depth);
		final Img<BitType> mask = ops().create().img(dimensions, new BitType());
		final Cursor<BitType> cursor = mask.localizingCursor();
		final RandomAccess<T> intervalAccess = interval.randomAccess();
		final long[] maskPosition = new long[3];
		final long[] intervalPosition = new long[interval.numDimensions()];

		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.localize(maskPosition);
			System.arraycopy(maskPosition, 0, intervalPosition, 0, 3);
			intervalAccess.setPosition(intervalPosition);
			final T intervalElement = intervalAccess.get();
			if (intervalElement.compareTo(thresholds.min) >= 0 && intervalElement
				.compareTo(thresholds.max) <= 0)
			{
				cursor.get().setOne();
			}
		}

		return mask;
	}
}
