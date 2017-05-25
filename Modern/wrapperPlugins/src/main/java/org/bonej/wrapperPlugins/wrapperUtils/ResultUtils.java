
package org.bonej.wrapperPlugins.wrapperUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.TypedAxis;
import net.imagej.space.AnnotatedSpace;
import net.imagej.table.GenericColumn;
import net.imagej.table.LongColumn;
import net.imagej.units.UnitService;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import org.bonej.utilities.AxisUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.scijava.util.StringUtils;

/**
 * Static utility methods that help display results to the user
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
		final T space, final UnitService unitService, final char exponent)
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

	/**
	 * Creates a column for a {@link net.imagej.table.GenericTable} that repeats
	 * the given label on each row
	 */
	public static GenericColumn createLabelColumn(final String label, int rows) {
		final GenericColumn labelColumn = new GenericColumn("Label");
		final int n = Math.max(0, rows);
		final String s = StringUtils.isNullOrEmpty(label) ? "-" : label;
		Stream.generate(() -> s).limit(n).forEach(labelColumn::add);
		return labelColumn;
	}

	/**
	 * Creates columns for a {@link net.imagej.table.Table} that describe the
	 * positions of the subspaces in a hyperspace
	 * <p>
	 * For example, if you've split a {X, Y, Z, C, T} space into {X, Y, Z}, the
	 * method returns "Channel" and "Time" columns that show list the positions of
	 * the subspaces in C and T.
	 * </p>
	 * 
	 * @see Subspace
	 */
	public static <T extends RealType<T> & NativeType<T>> List<LongColumn>
		createCoordinateColumns(List<Subspace<T>> subspaces)
	{
		final List<LongColumn> coordinateColumns = new ArrayList<>();
		if (subspaces == null) {
			return coordinateColumns;
		}
		final AxisType[] types = subspaces.get(0).getAxisTypes().toArray(
			AxisType[]::new);
		final List<long[]> positions = subspaces.stream().map(s -> s.getPosition()
			.toArray()).collect(Collectors.toList());
		for (int i = 0; i < types.length; i++) {
			final AxisType type = types[i];
			final LongColumn coordinateColumn = new LongColumn(type.getLabel());
			final int index = i;
			positions.stream().mapToLong(p -> toConventionalIndex(type, p[index]))
				.forEach(coordinateColumn::add);
			coordinateColumns.add(coordinateColumn);
		}
		return coordinateColumns;
	}

	/**
	 * If needed, converts the given index to the ImageJ(1) convention where Z,
	 * Channel and Time axes start from 1
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
