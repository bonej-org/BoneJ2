
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

/**
 * Various utils for inspecting image axis properties
 *
 * @author Richard Domander
 */
public class AxisUtils {

	/**
	 * Indices of the first three spatial dimensions in the Axes array of the
	 * given space
	 *
	 * @return An Optional containing the indices, or empty if failed to find
	 *         three spatial dimensions
	 */
	public static <T extends AnnotatedSpace<A>, A extends TypedAxis>
		Optional<int[]> getXYZIndices(final T space)
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
	 * Gets the index of the first time dimension in the space, or -1 if there are
	 * no such dimensions
	 */
	public static <T extends AnnotatedSpace<A>, A extends TypedAxis> int
		getTimeIndex(final T space)
	{
		if (space == null) {
			return -1;
		}

		final int dimensions = space.numDimensions();
		return IntStream.range(0, dimensions).filter(d -> space.axis(d)
			.type() == Axes.TIME).findFirst().orElse(-1);
	}

	/**
	 * Gets the index of the first channel dimension in the space, or -1 if there
	 * are no such dimensions
	 */
	public static <T extends AnnotatedSpace<A>, A extends TypedAxis> int
		getChannelIndex(final T space)
	{
		if (space == null) {
			return -1;
		}

		final int dimensions = space.numDimensions();
		return IntStream.range(0, dimensions).filter(d -> space.axis(d)
			.type() == Axes.CHANNEL).findFirst().orElse(-1);
	}

	/**
	 * Counts the number of spatial dimensions in the given space
	 *
	 * @return Number of spatial dimensions in the space, or 0 if space == null
	 */
	public static <T extends AnnotatedSpace<S>, S extends TypedAxis> long
		countSpatialDimensions(final T space)
	{
		return spatialAxisStream(space).count();
	}

	/**
	 * Determines the maximum difference between the spatial axes calibrations
	 *
	 * @param scale Scale of the first spatial axis
	 * @param unit Unit of the first spatial axis
	 * @param space A space containing calibrated axes
	 * @param unitService An unit service to convert axis calibrations
	 * @return Greatest conversion coefficient between two axes found. Coefficient
	 *         == 0.0 if space == null, or there are no spatial axes
	 * @implNote Coefficient is always from the smaller unit to the larger, i.e.
	 *           >= 1.0
	 */
	public static <T extends AnnotatedSpace<CalibratedAxis>> double
		getMaxConversion(final double scale, final String unit,
			final T space, final UnitService unitService)
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

			double conversion = toConversion >= fromConversion ? toConversion
				: fromConversion;

			if (conversion >= maxConversion) {
				maxConversion = conversion;
			}
		}

		return maxConversion;
	}

	/**
	 * Returns the unit of the spatial calibration of the given space
	 *
	 * @return The Optional is empty if the space == null, or there are no spatial
	 *         axes, or there's no conversion between the units. The Optional
	 *         contains an empty string if all the axes are uncalibrated
	 */
	public static <T extends AnnotatedSpace<CalibratedAxis>> Optional<String>
		 getSpatialUnit(final T space, final UnitService unitService)
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
	 * Checks if the given space has a channel dimension
	 *
	 * @return true if for any axis CalibratedAxis.type() == Axes.CHANNEL, false
	 *         if not, or space == null
	 */
	public static <T extends AnnotatedSpace<S>, S extends TypedAxis> boolean
		hasChannelDimensions(final T space)
	{
		return axisStream(space).anyMatch(a -> a.type() == Axes.CHANNEL);
	}

	public static <T extends AnnotatedSpace<S>, S extends TypedAxis> boolean
		hasSpatialDimensions(final T space)
	{
		return axisStream(space).anyMatch(a -> a.type().isSpatial());
	}

	/**
	 * Checks if the given space has a time dimension
	 *
	 * @return true if for any axis CalibratedAxis.type() == Axes.TIME, false if
	 *         not, or space == null
	 */
	public static <T extends AnnotatedSpace<S>, S extends TypedAxis> boolean
		hasTimeDimensions(final T space)
	{
		return axisStream(space).anyMatch(a -> a.type() == Axes.TIME);
	}

	public static <T extends AnnotatedSpace<S>, S extends TypedAxis> boolean
		hasNonLinearSpatialAxes(final T space)
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
        //TODO Replace with StringUtils.isNullOrEmpty
		final long uncalibrated = spatialAxisStream(space).map(CalibratedAxis::unit)
			.filter(s -> s == null || s.isEmpty()).count();

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
