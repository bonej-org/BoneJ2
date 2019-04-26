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

import static org.bonej.utilities.Streamers.axisStream;
import static org.bonej.utilities.Streamers.spatialAxisStream;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.LinearAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Various utility methods for inspecting image element properties
 *
 * @author Richard Domander
 */
public final class ElementUtil {

	private ElementUtil() {}

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
	 * @return Calibrated size of a spatial element, or Double.NaN if space has
	 *         nonlinear axes, or calibration units cannot be converted.
	 */
	public static <T extends AnnotatedSpace<CalibratedAxis>> double
		calibratedSpatialElementSize(final T space, final UnitService unitService)
	{
		final Optional<String> optional = AxisUtils.getSpatialUnit(space,
			unitService);
		if (!optional.isPresent() || hasNonLinearSpatialAxes(space)) {
			return Double.NaN;
		}
		final double elementSize = spatialAxisStream(space).map(a -> a.averageScale(
			0, 1)).reduce((x, y) -> x * y).orElse(0.0);
		// DefaultUnitService handles microns as "um" instead of "µm",
		final String outputUnit = optional.get().replaceFirst("^µ[mM]$", "um");
		if (outputUnit.isEmpty()) {
			// None of the axes have units, no conversions between units necessary
			return elementSize;
		}
		final List<CalibratedAxis> axes = spatialAxisStream(space).collect(
			Collectors.toList());
		double unitCoeff = 1.0;
		for (int i = 1; i < axes.size(); i++) {
			final String inputUnit = axes.get(i).unit().replaceFirst("^µ[mM]$", "um");
			final double conversion = unitService.value(1.0, inputUnit, outputUnit);
			unitCoeff *= conversion;
		}
		return elementSize * unitCoeff;
	}

	/**
	 * Checks whether the interval contains only two distinct values.
	 *
	 * @param interval an iterable interval.
	 * @param <T> type of the elements in the interval.
	 * @return true if only two distinct values, false if interval is empty
	 *         or has more values.
	 */
	public static <T extends RealType<T> & NativeType<T>> boolean isBinary(
		final IterableInterval<T> interval)
	{
		if (interval.size() == 0) {
			return false;
		}

		if (BooleanType.class.isAssignableFrom(interval.firstElement()
			.getClass()))
		{
			// by definition the elements can only be 0 or 1 so must be binary
			return true;
		}

		//a and b have the first pixel value
		double a = interval.firstElement().getRealDouble();
		double b = a;
		double c;
		
		final Cursor<T> cursor = interval.cursor();
		while (cursor.hasNext()){
			cursor.fwd();
			c = cursor.get().getRealDouble();
			//if we encounter a different pixel value
			// than a, assign it to b, and stop
			if (c != a) {
				b = c;
				break;
			}
		}
		//if we got to the end, there is only one pixel value,
		//the next while is skipped, and we return true.
		//Otherwise check the rest of the pixels
		while (cursor.hasNext()) {
			cursor.fwd();
		  c = cursor.get().getRealDouble();
		  //if c is neither a or b the image is not binary
		  if (c == a || c == b)
		  	continue;
		  return false;
		}
		return true;
	}

	//@region -- Helper methods --
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
	//@endregion
}
