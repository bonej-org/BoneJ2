/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.utilities;

import static java.util.stream.Collectors.toList;
import static org.bonej.utilities.Streamers.axisStream;
import static org.bonej.utilities.Streamers.spatialAxisStream;

import java.util.List;
import java.util.Optional;

import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;

import org.scijava.util.StringUtils;

/**
 * Various utils for inspecting image axis properties
 *
 * @author Richard Domander
 */
public final class AxisUtils {

	private AxisUtils() {}

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
		if (space == null || !hasSpatialDimensions(space)) {
			return Optional.empty();
		}
		if (!isUnitsConvertible(space, unitService)) {
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
	 * Check if all the spatial axes have a matching calibration, e.g. same unit,
	 * same scaling.
	 * <p>
	 * NB: Public and static for testing purposes.
	 * </p>
	 *
	 * @param space an N-dimensional space.
	 * @param <T> type of the space
	 * @return true if all spatial axes have matching calibration. Also returns
	 *         true if none of them have a unit
	 */
	public static <T extends AnnotatedSpace<CalibratedAxis>> boolean
		isAxesMatchingSpatialCalibration(final T space)
	{
		final boolean noUnits = spatialAxisStream(space).map(CalibratedAxis::unit)
			.allMatch(StringUtils::isNullOrEmpty);
		final boolean matchingUnit = spatialAxisStream(space).map(
			CalibratedAxis::unit).distinct().count() == 1;
		final boolean matchingScale = spatialAxisStream(space).map(a -> a
			.averageScale(0, 1)).distinct().count() == 1;

		return (matchingUnit || noUnits) && matchingScale;
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
		isUnitsConvertible(final T space, final UnitService unitService)
	{
		final long spatialDimensions = countSpatialDimensions(space);
		final long uncalibrated = spatialAxisStream(space).map(CalibratedAxis::unit)
			.filter(StringUtils::isNullOrEmpty).count();
		if (uncalibrated == spatialDimensions) {
			return true;
		}
		if (uncalibrated > 0) {
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
				catch (final Exception e) {
					return false;
				}
			}
		}
		return true;
	}
	// endregion
}
