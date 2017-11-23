
package org.bonej.ops;

import java.util.Optional;
import java.util.stream.DoubleStream;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.Interval;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Solves scalar values (t<sub>1</sub>, t<sub>2</sub>) for the parametric
 * equation of a line a + v, where a is a point and v a unit vector. The line
 * enters and exits the interval at the points a + t<sub>1</sub> * v and a +
 * t<sub>2</sub> * v respectively.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class BoxIntersect extends
	AbstractBinaryFunctionOp<ValuePair<Tuple3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>>
	implements Contingent
{

	/**
	 * If true, the method solves for intersections with the interval at
	 * [minBounds, maxBounds). If false, then for maxBounds exactly.
	 * <p>
	 * For example, if you wanted to find the intersection points of a
	 * [100x100x100] image stack, exclusion makes sure that ((50, 50, 0); (0, 0,
	 * 1)) intersects the stack at (50, 50, 0) and (50, 50, 99), when the
	 * coordinates are floored to integers. Thus, you won't get index out of
	 * bounds exceptions.
	 * </p>
	 */
	@Parameter(required = false)
	private boolean excludeMaxBounds = true;

	/**
	 * Finds intersection points of the line and the minimum and maximum bounds of
	 * the interval.
	 *
	 * @param line parametric equation of a line a + v as a (point, vector) pair.
	 * @param interval an interval with integer coordinates.
	 * @return scalar values (t<sub>1</sub>, t<sub>2</sub>) for intersection
	 *         points (a + t<sub>1</sub> * v, a + t<sub>2</sub> * v).
	 *         {@link Optional#empty()} if the line doesn't intersect the stack.
	 */
	@Override
	public Optional<ValuePair<DoubleType, DoubleType>> calculate(
		final ValuePair<Tuple3d, Vector3d> line, final Interval interval)
	{
		final Vector3d direction = new Vector3d(line.b);
		final Point3d origin = new Point3d(line.a);
		if (!validCoordinates(direction) || !validCoordinates(origin)) {
			throw new IllegalArgumentException(
				"Direction or origin has non-finite coordinates");
		}
		if (direction.length() == 0.0) {
			throw new IllegalArgumentException("Direction either has zero length");
		}
		direction.normalize();
		final int d = interval.numDimensions();
		final long[] minBounds = new long[d];
		interval.min(minBounds);
		final long[] maxBounds = new long[d];
		interval.max(maxBounds);

		final ValuePair<DoubleType, DoubleType> pair = findIntervalIntersections(
			origin, direction, minBounds, maxBounds);
		if (pair == null) {
			return Optional.empty();
		}
		if (reverseScalars(pair)) {
			// Swap t-values so that the first is the intersection where the line
			// enters the interval.
			final double tmp = pair.a.get();
			pair.a.set(pair.b.get());
			pair.b.set(tmp);
		}
		return Optional.of(pair);
	}

	@Override
	public boolean conforms() {
		return in2().numDimensions() >= 3;
	}

	private ValuePair<DoubleType, DoubleType> findIntervalIntersections(
		final Tuple3d origin, final Vector3d direction, final long[] min,
		final long[] max)
	{
		// Because max = {w - 1, h - 1, d - 1} we need to add 1 to the bounds to
		// get the correct intersection point
		if (hasNanCoordinates(origin) || hasNanCoordinates(direction)) {
			return null;
		}
		final double eps = excludeMaxBounds ? 1 - 1e-12 : 1;
		final double minX = direction.x >= 0.0 ? min[0] : max[0] + eps;
		final double maxX = direction.x >= 0.0 ? max[0] + eps : min[0];
		final double minY = direction.y >= 0.0 ? min[1] : max[1] + eps;
		final double maxY = direction.y >= 0.0 ? max[1] + eps : min[1];
		final double minZ = direction.z >= 0.0 ? min[2] : max[2] + eps;
		final double maxZ = direction.z >= 0.0 ? max[2] + eps : min[2];
		final double tX0 = (minX - origin.x) / direction.x;
		final double tX1 = (maxX - origin.x) / direction.x;
		final double tY0 = (minY - origin.y) / direction.y;
		final double tY1 = (maxY - origin.y) / direction.y;
		final double tZ0 = (minZ - origin.z) / direction.z;
		final double tZ1 = (maxZ - origin.z) / direction.z;
		if (tX0 > tY1 || tY0 > tX1) {
			return null;
		}
		double tMin = maxNan(tX0, tY0);
		double tMax = minNan(tX1, tY1);
		if (tMin > tZ1 || tZ0 > tMax) {
			return null;
		}
		tMin = maxNan(tZ0, tMin);
		tMax = minNan(tZ1, tMax);
		if (Double.isNaN(tMin) || Double.isNaN(tMax)) {
			return null;
		}
		if (tMin > tMax) {
			final double tmp = tMin;
			tMin = tMax;
			tMax = tmp;
		}
		return new ValuePair<>(new DoubleType(tMin), new DoubleType(tMax));
	}

	// region -- Helper classes --

	private boolean hasNanCoordinates(final Tuple3d origin) {
		return DoubleStream.of(origin.x, origin.y, origin.z).anyMatch(
			Double::isNaN);
	}

	private static double maxNan(final double a, final double b) {
		if (Double.isNaN(a) && Double.isNaN(b)) {
			return Double.NaN;
		}
		else if (Double.isNaN(a)) {
			return b;
		}
		else if (Double.isNaN(b)) {
			return a;
		}
		return Math.max(a, b);
	}

	private static double minNan(final double a, final double b) {
		if (Double.isNaN(a) && Double.isNaN(b)) {
			return Double.NaN;
		}
		else if (Double.isNaN(a)) {
			return b;
		}
		else if (Double.isNaN(b)) {
			return a;
		}
		return Math.min(a, b);
	}

	/** Checks if the scalars reverse the direction of the line */
	private boolean reverseScalars(final ValuePair<DoubleType, DoubleType> pair) {
		return pair.a.get() < 0 && pair.b.get() < 0;
	}

	private boolean validCoordinates(final Tuple3d t) {
		return DoubleStream.of(t.x, t.y, t.z).allMatch(Double::isFinite);
	}

	// endregion
}
