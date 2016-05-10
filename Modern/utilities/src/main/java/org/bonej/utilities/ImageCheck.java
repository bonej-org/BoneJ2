package org.bonej.utilities;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.TreeSet;

import net.imagej.axis.CalibratedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.RealType;

/**
 * Various utility methods for inspecting image properties
 *
 * @author Richard Domander
 */
public class ImageCheck {
	private ImageCheck() {
	}

	/**
	 * Checks whether the interval contains only two distinct values
	 *
	 * @implNote A hacky brute force approach
	 * @throws NullPointerException if interval == null
	 * @return True if only two distinct values, false if interval is empty or
	 *         has more colors
	 */
	public static <T extends RealType<T>> boolean isColorsBinary(final IterableInterval<T> interval)
			throws NullPointerException {
		checkNotNull(interval);

		if (interval.size() == 0) {
			return false;
		}

		if (BooleanType.class.isAssignableFrom(interval.firstElement().getClass())) {
			// by definition the elements can only be 0 or 1 so must be binary
			return true;
		}

		final Cursor<T> cursor = interval.cursor();
		final TreeSet<Double> values = new TreeSet<>();

		while (cursor.hasNext()) {
			final double value = cursor.next().getRealDouble();
			values.add(value);
			if (values.size() > 2) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Counts the number of spatial dimensions in the given space
	 * 
	 * @throws NullPointerException
	 *             if space == null
	 */
	public static <T extends AnnotatedSpace<CalibratedAxis>> long countSpatialDimensions(final T space)
			throws NullPointerException {
		checkNotNull(space, "Cannot count axes of a null space");

		long spatialDimensions = 0;

		final CalibratedAxis[] axes = new CalibratedAxis[space.numDimensions()];
		space.axes(axes);

		for (CalibratedAxis axis : axes) {
			if (axis.type().isSpatial()) {
				spatialDimensions++;
			}
		}

		return spatialDimensions;
	}
}
