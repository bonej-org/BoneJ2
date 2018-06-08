
package org.bonej.ops.mil;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imglib2.Interval;

import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * A class that describes a set of parallel lines normal to a plane. The plane
 * passes through an interval.
 *
 * @author Richard Domander
 */
class LinePlane {

	private final double size;
	private final Vector3d translation = new Vector3d();
	private final Point3d centroid;
	private final Vector3d direction;
	private final Random random = new Random();
	private final AxisAngle4d rotation;
	private final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp;

	/**
	 * Creates an instance of LinePlane, and initializes it for generating lines.
	 *
	 * @param interval a discrete interval through which the lines pass.
	 * @param rotation the direction of the lines through the interval.
	 * @param rotateOp the Op used to rotate the origin points and direction.
	 * @param <I> type of the interval.
	 */
	<I extends Interval> LinePlane(final I interval, final AxisAngle4d rotation,
		final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp)
	{
		size = findPlaneSize(interval);
		translation.set(-size * 0.5, -size * 0.5, 0.0);
		centroid = findCentroid(interval);
		this.rotateOp = rotateOp;
		this.rotation = rotation;
		direction = createDirection();
	}

	// region -- Helper methods --
	private Vector3d createDirection() {
		final Vector3d newDirection = new Vector3d(0, 0, 1);
		rotateOp.mutate1(newDirection, rotation);
		return newDirection;
	}

	private Point3d createOrigin(final double t, final double u) {
		final double x = t * size;
		final double y = u * size;
		final Point3d origin = new Point3d(x, y, 0);
		origin.add(translation);
		rotateOp.mutate1(origin, rotation);
		origin.add(centroid);
		return origin;
	}

	private static <I extends Interval> Point3d findCentroid(final I interval) {
		final double[] coordinates = IntStream.range(0, 3).mapToDouble(d -> interval
			.max(d) + 1 - interval.min(d)).map(d -> d / 2.0).toArray();
		return new Point3d(coordinates);
	}

	private static <I extends Interval> double findPlaneSize(final I interval) {
		final long sqSum = LongStream.of(interval.dimension(0), interval.dimension(
			1), interval.dimension(2)).map(x -> x * x).sum();
		return Math.sqrt(sqSum);
	}

	/**
	 * Generates a random set of points through which lines pass the plane.
	 * <p>
	 * If the direction of the lines is (0, 0, 1), then their coordinate range of
	 * the points will be will be (c<sub>x</sub> &plusmn; d/2, c<sub>y</sub>
	 * &plusmn; d/2, c<sub>z</sub>), where <b>c</b> is the centroid, and
	 * <em>d</em> is the length of the largest, "corner-to-corner" diagonal of the
	 * interval.
	 * </p>
	 * <p>
	 * The points are equidistant from each other in both directions, but
	 * translated randomly so that they don't always have the same co-ordinates.
	 * </p>
	 * <p>
	 * NB points may lay outside the interval!
	 * </p>
	 *
	 * @param bins number of sub-regions in both dimensions. Bins = 2 creates four
	 *          points, one from each quadrant of the plane.
	 * @return a finite stream of origin points on the plane.
	 */
	Stream<Point3d> getOrigins(final Long bins) {
		final Builder<Point3d> builder = Stream.builder();
		final double step = 1.0 / bins;
		final double uOffset = random.nextDouble() * step;
		final double tOffset = random.nextDouble() * step;
		for (long i = 0; i < bins; i++) {
			final double u = i * step + uOffset;
			for (long j = 0; j < bins; j++) {
				final double t = j * step + tOffset;
				final Point3d origin = createOrigin(t, u);
				builder.add(origin);
			}
		}
		return builder.build();
	}

	/**
	 * Gets a reference of the plane's normal.
	 *
	 * @return direction of the lines passing through the plane.
	 */
	Vector3d getDirection() {
		return direction;
	}

	/**
	 * Sets the seed of the pseudo random number generator used in drawing the
	 * origin points.
	 *
	 * @see #getOrigins(Long)
	 * @param seed a seed number.
	 */
	void setSeed(final long seed) {
		random.setSeed(seed);
	}

	// endregion
}
