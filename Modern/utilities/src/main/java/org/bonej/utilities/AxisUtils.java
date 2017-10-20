
package org.bonej.utilities;

import static java.util.stream.Collectors.toList;
import static org.bonej.utilities.Streamers.axisStream;
import static org.bonej.utilities.Streamers.spatialAxisStream;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.LinearAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;

import org.scijava.util.StringUtils;

/**
 * Various utils for inspecting image axis properties
 *
 * @author Richard Domander
 */
public class AxisUtils {

	/**
	 * Indices of the first three spatial dimensions in the Axes array of the
	 * given space.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return an Optional containing the indices, or empty if failed to find
	 *         three spatial dimensions.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis>
		Optional<int[]> getXYZIndices(final S space)
	{
		if (space == null) {
			return Optional.empty();
		}

		final int dimensions = space.numDimensions();
		final int[] indices = IntStream.range(0, dimensions).filter(d -> space.axis(
			d).type().isSpatial()).toArray();

		return indices.length == 3 ? Optional.of(indices) : Optional.empty();
	}

	/**
	 * Gets the index of the time axis in the dimensions of the space.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return index of the time axis, or -1 if there's none.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> int
		getTimeIndex(final S space)
	{
		if (space == null) {
			return -1;
		}

		final int dimensions = space.numDimensions();
		return IntStream.range(0, dimensions).filter(d -> space.axis(d)
			.type() == Axes.TIME).findFirst().orElse(-1);
	}

	/**
	 * Gets the index of the channel axis in the dimensions of the space.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return index of the channel axis, or -1 if there's none.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> int
		getChannelIndex(final S space)
	{
		if (space == null) {
			return -1;
		}

		final int dimensions = space.numDimensions();
		return IntStream.range(0, dimensions).filter(d -> space.axis(d)
			.type() == Axes.CHANNEL).findFirst().orElse(-1);
	}

	/**
	 * Counts the number of spatial dimensions in the given space.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return number of spatial dimensions in the space.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> long
		countSpatialDimensions(final S space)
	{
		return spatialAxisStream(space).count();
	}

	/**
	 * Determines the coefficient to convert units from the spatial axis with the
	 * smallest calibration to the largest.
	 *
	 * @param scale scale of the first spatial axis.
	 * @param unit unit of the first spatial axis.
	 * @param space an n-dimensional space with calibrated axes.
	 * @param <S> type of the space.
	 * @param unitService an {@link UnitService} to convert axis calibrations.
	 * @return greatest conversion coefficient between two axes found. Coefficient
	 *         == 0.0 if space == null, or there are no spatial axes.
	 */
	//TODO scale and unit are redundant parameters
	public static <S extends AnnotatedSpace<CalibratedAxis>> double
		getMaxConversion(final double scale, final String unit,
						 final S space, final UnitService unitService)
	{
		final List<CalibratedAxis> axes = spatialAxisStream(space).collect(
			toList());
		double maxConversion = 0.0;

		for (CalibratedAxis axis : axes) {
			final double axisScale = axis.averageScale(0.0, 1.0);
			final String axisUnit = axis.unit().replaceFirst("^µ[mM]$", "um");
			final double toConversion = scale * unitService.value(1.0, unit,
				axisUnit) / axisScale;
			final double fromConversion = axisScale * unitService.value(1.0, axisUnit,
				unit) / scale;

			final double conversion = toConversion >= fromConversion ? toConversion
				: fromConversion;

			if (conversion >= maxConversion) {
				maxConversion = conversion;
			}
		}

		return maxConversion;
	}

	/**
	 * Returns the common unit of the spatial calibrations of the given space.
	 * <p>
	 * The common unit is the unit of the first spatial axis if it can be
	 * converted to the units of the other axes.
	 * </p>
	 *
	 * @param space an n-dimensional space with calibrated axes.
	 * @param <S> type of the space.
	 * @param unitService an {@link UnitService} to convert axis calibrations.
	 * @return an optional with the unit of spatial calibration. It's empty if the
	 *         space == null, there are no spatial axes, or there's no conversion
	 *         between their units. The Optional contains an empty string none of
	 *         the calibrations have a unit.
	 */
	public static <S extends AnnotatedSpace<CalibratedAxis>> Optional<String>
		 getSpatialUnit(final S space, final UnitService unitService)
	{
		if (space == null || !hasSpatialDimensions(space))
		{
			return Optional.empty();
		} else if (!isUnitsConvertible(space, unitService)) {
		    return Optional.of("");
        }

        final String unit = space.axis(0).unit();
        return unit == null ? Optional.of("") : Optional.of(unit);
	}

	/**
	 * Checks if the given space has a channel dimension.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if there are any channel type axes.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasChannelDimensions(final S space)
	{
		return axisStream(space).anyMatch(a -> a.type() == Axes.CHANNEL);
	}

	/**
	 * Checks if the given space has any spatial dimensions.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if there are any spatial type axes.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasSpatialDimensions(final S space)
	{
		return axisStream(space).anyMatch(a -> a.type().isSpatial());
	}

	/**
	 * Checks if the given space has a time dimension.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if there are any time type axes.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasTimeDimensions(final S space)
	{
		return axisStream(space).anyMatch(a -> a.type() == Axes.TIME);
	}

	/**
	 * Checks if the given space has any non-linear spatial dimensions.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of axes in the space.
	 * @return true if there are any power, logarithmic or other non-linear axes.
	 */
	//TODO is this really necessary?
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> boolean
		hasNonLinearSpatialAxes(final S space)
	{
		return axisStream(space).anyMatch(a -> !(a instanceof LinearAxis) && a
			.type().isSpatial());
	}

	// region -- Helper methods --

	/**
	 * Returns true if the scales of the all the axes can be converted to each
	 * other
	 * <p>
	 * NB Returns true also when all axes are uncalibrated (no units)
	 * </p>
	 */
	private static <T extends AnnotatedSpace<CalibratedAxis>> boolean
		isUnitsConvertible(T space, final UnitService unitService)
	{
		final long spatialDimensions = countSpatialDimensions(space);

		final long uncalibrated = spatialAxisStream(space).map(CalibratedAxis::unit)
			.filter(StringUtils::isNullOrEmpty).count();

		if (uncalibrated == spatialDimensions) {
			return true;
		}
		else if (uncalibrated > 0) {
			return false;
		}

		final List<String> units = spatialAxisStream(space).map(
			CalibratedAxis::unit).distinct().map(s -> s.replaceFirst("^µ[mM]$", "um"))
			.collect(toList());

        for (int i = 0; i < units.size(); i++) {
			for (int j = i; j < units.size(); j++) {
				try {
					unitService.value(1.0, units.get(i), units.get(j));
				}
				catch (Exception e) {
					return false;
				}
			}
		}

		return true;
	}
	// endregion
}
