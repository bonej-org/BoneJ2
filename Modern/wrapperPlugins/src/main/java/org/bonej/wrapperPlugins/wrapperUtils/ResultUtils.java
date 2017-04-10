
package org.bonej.wrapperPlugins.wrapperUtils;

import java.util.Optional;

import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;

import org.bonej.utilities.AxisUtils;

/**
 * Static utility methods for displaying results to the user
 *
 * @author Richard Domander
 */
public class ResultUtils {

	private ResultUtils() {}

	/**
	 * Returns a verbal description of the size of the elements in the given
	 * space, e.g. "Area" for 2D images
	 */
	public static <T extends AnnotatedSpace<A>, A extends TypedAxis> String
		getSizeDescription(T space)
	{
		final long dimensions = AxisUtils.countSpatialDimensions(space);

		if (dimensions == 2) {
			return "Area";
		}
		else if (dimensions == 3) {
			return "Volume";
		}

		return "Size";
	}

	/**
	 * Returns the exponent character of the elements in this space, e.g. '³' for
	 * a spatial 3D space
	 */
	public static <T extends AnnotatedSpace<A>, A extends TypedAxis> char
		getExponent(final T space)
	{
		final long dimensions = AxisUtils.countSpatialDimensions(space);

		if (dimensions == 2) {
			return '\u00B2';
		}
		else if (dimensions == 3) {
			return '\u00B3';
		}
		else if (dimensions == 4) {
			return '\u2074';
		}
		else if (dimensions == 5) {
			return '\u2075';
		}
		else if (dimensions == 6) {
			return '\u2076';
		}
		else if (dimensions == 7) {
			return '\u2077';
		}
		else if (dimensions == 8) {
			return '\u2078';
		}
		else if (dimensions == 9) {
			return '\u2079';
		}

		// Return an "empty" character
		return '\u0000';
	}

	/** @see ResultUtils#getUnitHeader(AnnotatedSpace, UnitService, char) */
	public static <T extends AnnotatedSpace<CalibratedAxis>> String getUnitHeader(
		final T space, final UnitService unitService)
	{
		return getUnitHeader(space, unitService, '\u0000');
	}

	/**
	 * Returns the unit used in the calibration of the space that can be shown in
	 * e.g. ResultsTable
	 * <p>
	 * Returns "(mm)" if calibration unit is "mm"
	 *
	 * @param exponent An exponent to be added to the unit, e.g. '³'
	 * @return Unit for column headers or empty if there's no unit
	 */
	public static <T extends AnnotatedSpace<CalibratedAxis>> String getUnitHeader(
		final T space, final UnitService unitService,
		final char exponent)
	{
		final Optional<String> unit = AxisUtils.getSpatialUnit(space, unitService);
		if (!unit.isPresent()) {
			return "";
		}

		final String unitHeader = unit.get();
		if ("pixel".equalsIgnoreCase(unitHeader) || "unit".equalsIgnoreCase(
			unitHeader) || unitHeader.isEmpty())
		{
			// Don't show default units
			return "";
		}

		return "(" + unitHeader + exponent + ")";
	}
}
