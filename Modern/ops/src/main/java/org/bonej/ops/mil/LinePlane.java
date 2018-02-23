
package org.bonej.ops.mil;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imglib2.Interval;

import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Generates parallel lines normal to a plane.
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

	<I extends Interval> LinePlane(final I interval, final AxisAngle4d rotation,
		final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp)
	{
		this.size = findPlaneSize(interval);
		this.translation.set(-size * 0.5, -size * 0.5, 0.0);
		this.centroid = findCentroid(interval);
		this.rotateOp = rotateOp;
		this.rotation = rotation;
		direction = createDirection();
	}

	// region -- Helper methods --
	private Vector3d createDirection() {
		final Vector3d direction = new Vector3d(0, 0, 1);
		rotateOp.mutate1(direction, rotation);
		return direction;
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

	Stream<Point3d> getOrigins(final Long bins) {
		final Stream.Builder<Point3d> builder = Stream.builder();
		final long squares = 100;
		final double step = 1.0 / bins;
		final double offset = random.nextDouble() * step;
		for (long i = 0; i < squares; i++) {
			final double u = i * step + offset;
			for (long j = 0; j < squares; j++) {
				final double t = j * step + offset;
				final Point3d origin = createOrigin(t, u);
				builder.add(origin);
			}
		}
		return builder.build();
	}

	Vector3d getDirection() {
		return direction;
	}

	void setSeed(final long seed) {
		random.setSeed(seed);
	}
	// endregion
}
