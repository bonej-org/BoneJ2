
package org.bonej.utilities;

import static org.bonej.utilities.Streamers.axisStream;
import static org.bonej.utilities.Streamers.spatialAxisStream;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.LinearAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;
import net.imglib2.IterableInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Various utility methods for inspecting image element properties
 *
 * @author Richard Domander
 */
// TODO Throw exceptions
public final class ElementUtil {

	private ElementUtil() {}

	/**
	 * Checks whether the interval contains only two distinct values.
	 * <p>
	 * NB a hacky brute force approach.
	 * </p>
	 * 
	 * @param interval an iterable interval.
	 * @param <T> type of the elements in the interval.
	 * @return true if only two distinct values, false if interval is null, empty
	 *         or has more colors.
	 */
	public static <T extends RealType<T> & NativeType<T>> boolean isColorsBinary(
		final IterableInterval<T> interval)
	{
		if (interval == null || interval.size() == 0) {
			return false;
		}

		if (BooleanType.class.isAssignableFrom(interval.firstElement()
			.getClass()))
		{
			// by definition the elements can only be 0 or 1 so must be binary
			return true;
		}

        final long colours = Streamers.realDoubleStream(interval).distinct()
			.count();

		return colours <= 2;
	}

	/**
	 * Returns the calibrated size of a single spatial element in the given space,
	 * e.g. the volume of an element in a 3D space, or area in 2D.
	 * <p>
	 * Spatial axes do not have to have the same unit in calibration, but you must
	 * be able to convert between them.
	 * </p>
	 * 
	 * @param space an N-dimensional space.
	 * @param <T> type of the space.
	 * @param unitService needed to convert between units of different
	 *          calibrations.
	 * @return Calibrated size of a spatial element, or Double.NaN if space ==
	 *         null, has nonlinear axes, or calibration units don't match.
	 */
	public static <T extends AnnotatedSpace<CalibratedAxis>> double
		calibratedSpatialElementSize(final T space, final UnitService unitService)
	{
        final Optional<String> optional = AxisUtils.getSpatialUnit(space, unitService);
        if (!optional.isPresent() || hasNonLinearSpatialAxes(space)) {
            return Double.NaN;
        }
        final String unit = optional.get().replaceFirst("^µ[mM]$", "um");
        if (unit.isEmpty()) {
            return uncalibratedSize(space);
		}

		final List<CalibratedAxis> axes = spatialAxisStream(space).collect(
			Collectors.toList());
		double elementSize = axes.get(0).averageScale(0.0, 1.0);
		for (int i = 1; i < axes.size(); i++) {
			final double scale = axes.get(i).averageScale(0.0, 1.0);
			final String axisUnit = axes.get(i).unit().replaceFirst("^µ[mM]$", "um");
			try {
                final double axisSize = unitService.value(scale, axisUnit, unit);
                elementSize *= axisSize;
            } catch (final Exception e) {
			    return uncalibratedSize(space);
            }
		}

		return elementSize;
	}

	/**
	 * Checks if the given space has any non-linear spatial dimensions.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if there are any power, logarithmic or other non-linear axes.
	 */
	private static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasNonLinearSpatialAxes(final S space)
	{
		return axisStream(space).anyMatch(a -> !(a instanceof LinearAxis) && a
			.type().isSpatial());
	}

	// region -- Helper methods --
	private static <T extends AnnotatedSpace<CalibratedAxis>> double
		uncalibratedSize(final T space)
	{
		return spatialAxisStream(space).map(a -> a.averageScale(0, 1)).reduce((x,
			y) -> x * y).orElse(0.0);
	}
	// endregion
}
