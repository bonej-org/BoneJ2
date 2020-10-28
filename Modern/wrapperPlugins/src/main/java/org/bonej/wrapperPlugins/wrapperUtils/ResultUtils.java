/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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

package org.bonej.wrapperPlugins.wrapperUtils;

import ij.ImagePlus;

import java.util.Optional;
import java.util.stream.Stream;

import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;

import org.bonej.utilities.AxisUtils;
import org.scijava.util.StringUtils;

/**
 * Static utility methods that help display results to the user
 *
 * @author Richard Domander
 */
public final class ResultUtils {

	private ResultUtils() {}

	/**
	 * Returns the exponent character of the elements in this space, e.g. '³' for
	 * a spatial 3D space.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of the axes.
	 * @return the exponent character if the space has 2 - 9 spatial dimensions.
	 *         An empty character otherwise.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> char
		getExponent(final S space)
	{
		final long dimensions = AxisUtils.countSpatialDimensions(space);
		if (dimensions == 2) {
			return '\u00B2';
		}
		if (dimensions == 3) {
			return '\u00B3';
		}
		if (dimensions == 4) {
			return '\u2074';
		}
		if (dimensions == 5) {
			return '\u2075';
		}
		if (dimensions == 6) {
			return '\u2076';
		}
		if (dimensions == 7) {
			return '\u2077';
		}
		if (dimensions == 8) {
			return '\u2078';
		}
		if (dimensions == 9) {
			return '\u2079';
		}
		// Return an "empty" character
		return '\u0000';
	}

	/**
	 * Returns a verbal description of the size of the elements in the given
	 * space, e.g. "A" for 2D images and "V" for 3D images.
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param <A> type of the axes.
	 * @return the noun for the size of the elements.
	 */
	public static <S extends AnnotatedSpace<A>, A extends TypedAxis> String
		getSizeDescription(final S space)
	{
		final long dimensions = AxisUtils.countSpatialDimensions(space);
		if (dimensions == 2) {
			return "A";
		}
		if (dimensions == 3) {
			return "V";
		}
		return "Size";
	}

	/**
	 * Returns the common unit string, e.g. "mm<sup>3</sup>" that describes the
	 * elements in the space.
	 * <p>
	 * The common unit is the unit of the first spatial axis if it can be
	 * converted to the units of the other axes.
	 * </p>
	 *
	 * @param space an N-dimensional space.
	 * @param <S> type of the space.
	 * @param unitService an {@link UnitService} to convert axis calibrations.
	 * @param exponent an exponent to be added to the unit, e.g. '³'.
	 * @return the unit string with the exponent.
	 */
	public static <S extends AnnotatedSpace<CalibratedAxis>> String getUnitHeader(
		final S space, final UnitService unitService, final String exponent)
	{
		final Optional<String> unit = AxisUtils.getSpatialUnit(space, unitService);
		if (!unit.isPresent()) {
			return "";
		}

		final String unitHeader = unit.get();
		if (unitHeader.isEmpty())
		{
			// Don't show default units
			return "";
		}

		return "(" + unitHeader + exponent + ")";
	}

	/**
	 * Gets the unit of the image calibration, which can be displayed to the user.
	 *
	 * @param imagePlus a ImageJ1 style {@link ImagePlus}.
	 * @return calibration unit, or empty string if there's no unit.
	 */
	public static String getUnitHeader(final ImagePlus imagePlus) {
		final String unit = imagePlus.getCalibration().getUnit();
		if (StringUtils.isNullOrEmpty(unit))
		{
			return "";
		}
		return "(" + unit + ")";
	}

	/**
	 * If needed, converts the given index to the ImageJ1 convention where Z,
	 * Channel and Time axes start from 1.
	 *
	 * @param type type of the axis's dimension.
	 * @param index the index in the axis.
	 * @return index + 1 if type is Z, Channel or Time. Index otherwise.
	 */
	public static long toConventionalIndex(final AxisType type,
		final long index)
	{
		final Stream<AxisType> oneAxes = Stream.of(Axes.Z, Axes.CHANNEL, Axes.TIME);
		if (oneAxes.anyMatch(t -> t.equals(type))) {
			return index + 1;
		}
		return index;
	}
}
