
package org.bonej.ops.mil;

import static org.bonej.ops.mil.LineGrid.Orientation.XY;
import static org.bonej.ops.mil.LineGrid.Orientation.XZ;
import static org.bonej.ops.mil.LineGrid.Orientation.YZ;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import net.imagej.ops.special.hybrid.AbstractBinaryHybridCFI1;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imglib2.Interval;
import net.imglib2.util.ValuePair;

import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * A class that generates lines through an interval in a cubical grid pattern.
 * <p>
 * The lines go through three orthogonal planes that meet at the corner of the
 * grid. The size of the grid is <em>d<sup>3</sup></em>, where <em>d</em> =
 * sqrt(<em>width<sup>2</sup></em> + <em>height<sup>2</sup></em> +
 * <em>depth<sup>2</sup></em>). The grid is centered on the centroid of the
 * interval.
 * </p>
 *
 * @author Richard Domander
 */
public final class LineGrid {

	private final LinePlane xy;
	private final LinePlane xz;
	private final LinePlane yz;
	private static final Function<Interval, LongStream> dims =
		interval -> LongStream.of(interval.dimension(0), interval.dimension(1),
			interval.dimension(2));
	private Random rng;
	private boolean mirrorXY = false;
	private boolean mirrorXZ = false;
	private boolean mirrorYZ = false;

	/**
	 * Constructs an instance of {@link LineGrid}.
	 *
	 * @param interval a integer interval. The method assumes that the first three
	 *          dimensions are x, y and z.
	 * @param <I> type of the interval.
	 */
	public <I extends Interval> LineGrid(final I interval) {
		final boolean emptyDims = dims.apply(interval).anyMatch(i -> i == 0);
		if (interval.numDimensions() < 3 || emptyDims) {
			throw new IllegalArgumentException(
				"Interval must have at least three dimensions");
		}
		final double planeSize = findPlaneSize(interval);
		final double t = planeSize * 0.5;
		final Point3d centroid = findCentroid(interval);
		xy = new LinePlane(XY, planeSize, new Vector3d(0, 0, -t), centroid);
		xz = new LinePlane(XZ, planeSize, new Vector3d(0, -t, 0), centroid);
		yz = new LinePlane(YZ, planeSize, new Vector3d(-t, 0, 0), centroid);
		setRandomGenerator(new Random());
		setRotation(new NoopRotation(), new AxisAngle4d());
	}

	/**
	 * Generates <em>n</em> * <em>n</em> lines through each orthogonal plane of
	 * the grid.
	 * <p>
	 * The lines are in the parametric form <b>a</b> + <b>v</b>, where <b>a</b> is
	 * an origin point on one of the three planes, <b>v</b> is a vector normal to
	 * the plane. The lines are ordered so that first come all the lines on the
	 * first (xy) plane etc.
	 * </p>
	 * <p>
	 * The bins parameter controls from how many plane sections the origin points
	 * are generated. For example, if bins = 2 and plane size = 1 then each plane
	 * is divided into four sections. First origin point will be from the section
	 * [0 -- 1/2, 0 -- 1/2], second from [1/2 -- 1, 0 -- 1/2] and so forth.
	 * </p>
	 * 
	 * @param bins the number of bins (<em>n</em>) per plane dimension.
	 * @return a finite {@link Stream} of (origin, direction) pairs.
	 * @throws IllegalArgumentException if bins is non-positive.
	 */
	public Stream<ValuePair<Point3d, Vector3d>> lines(final long bins)
		throws IllegalArgumentException
	{
		if (bins < 1) {
			throw new IllegalArgumentException(
				"Must generate at least one line per plane.");
		}
		final Stream<ValuePair<Point3d, Vector3d>> xyLines = xy.lines(bins,
			mirrorXY);
		final Stream<ValuePair<Point3d, Vector3d>> xzLines = xz.lines(bins,
			mirrorXZ);
		final Stream<ValuePair<Point3d, Vector3d>> yzLines = yz.lines(bins,
			mirrorYZ);
		return Stream.of(xyLines, xzLines, yzLines).flatMap(s -> s);
	}

	/**
	 * Sets the random generator used in generating the lines.
	 *
	 * @param random an instance of the {@link Random} class.
	 * @throws NullPointerException if random is null.
	 */
	public void setRandomGenerator(final Random random)
		throws NullPointerException
	{
		if (random == null) {
			throw new NullPointerException("Random generator cannot be null");
		}
		rng = random;
		xy.setRandomGenerator(random);
		xz.setRandomGenerator(random);
		yz.setRandomGenerator(random);
	}

	/**
	 * Applies a rotation to the grid.
	 *
	 * @param rotateOp an op for rotation.
	 * @param rotation a rotation expressed as an {@link AxisAngle4d}.
	 * @throws NullPointerException if the op or rotation is null.
	 */
	public void setRotation(
		final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp,
		final AxisAngle4d rotation) throws NullPointerException
	{
		if (rotateOp == null) {
			throw new NullPointerException("Op cannot be null");
		}
		if (rotation == null) {
			throw new NullPointerException("Rotation cannot be null");
		}
		xy.setRotation(rotation, rotateOp);
		xz.setRotation(rotation, rotateOp);
		yz.setRotation(rotation, rotateOp);
	}

	/**
	 * Randomly mirrors each plane of the grid to the other side of the centroid.
	 * Also mirrors the directions of the lines.
	 */
	public void randomReflection() {
		mirrorXY = rng.nextBoolean();
		mirrorXZ = rng.nextBoolean();
		mirrorYZ = rng.nextBoolean();
	}

	// region -- Helper methods --

	private <I extends Interval> Point3d findCentroid(final I interval) {
		final double[] coordinates = IntStream.range(0, 3).mapToDouble(d -> interval
			.max(d) + 1 - interval.min(d)).map(d -> d / 2.0).toArray();
		return new Point3d(coordinates);
	}

	private <I extends Interval> double findPlaneSize(final I interval) {
		final long sqSum = dims.apply(interval).map(x -> x * x).sum();
		return Math.sqrt(sqSum);
	}

	// endregion

	// region -- Helper class --

	/**
	 * A class that generates lines, which pass through a plane in a direction
	 * parallel to its normal. The plane is centered around the origin.
	 */
	private static final class LinePlane {

		private static final double[] X_UNIT = { 1, 0, 0 };
		private static final double[] Y_UNIT = { 0, 1, 0 };
		private static final double[] Z_UNIT = { 0, 0, 1 };
		private Random rng = new Random();
		private final Vector3d u = new Vector3d();
		private final Vector3d v = new Vector3d();
		private final Vector3d normal = new Vector3d();
		private final Vector3d translation = new Vector3d();
		private final Point3d centroid = new Point3d();
		private final double size;
		private BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp;
		private AxisAngle4d rotation = new AxisAngle4d();

		/**
		 * Constructs an instance of {@link LinePlane}.
		 *
		 * @param orientation initial orientation of the plane.
		 * @param size the size of the four equal sides of the plane. More formally,
		 *          it's the scalar s that multiplies the unit vectors used in line
		 *          generation.
		 */
		private LinePlane(final Orientation orientation, final double size,
			final Vector3d translation, final Point3d centroid)
		{
			switch (orientation) {
				case XY:
					u.set(X_UNIT);
					v.set(Y_UNIT);
					normal.set(Z_UNIT);
					break;
				case XZ:
					u.set(X_UNIT);
					v.set(Z_UNIT);
					normal.set(Y_UNIT);
					break;
				case YZ:
					u.set(Y_UNIT);
					v.set(Z_UNIT);
					normal.set(X_UNIT);
					break;
				default:
					throw new IllegalArgumentException("Unexpected or null orientation");
			}
			this.size = size;
			this.translation.set(translation);
			this.centroid.set(centroid);
		}

		private Stream<ValuePair<Point3d, Vector3d>> lines(final long bins,
			final boolean mirror)
		{
			final double sign = mirror ? -1.0 : 1.0;
			final Vector3d direction = new Vector3d(normal);
			direction.scale(sign);
			rotateOp.mutate1(direction, rotation);
			final Vector3d t = new Vector3d(translation);
			t.scale(sign);
			final double range = 1.0 / bins;
			final Builder<ValuePair<Point3d, Vector3d>> builder = Stream.builder();
			for (double minC = 0.0; minC < 1.0; minC += range) {
				for (double minD = 0.0; minD < 1.0; minD += range) {
					final double c = (rng.nextDouble() * range + minC) * size - 0.5 *
						size;
					final double d = (rng.nextDouble() * range + minD) * size - 0.5 *
						size;
					final Point3d origin = createOrigin(c, d, t);
					builder.add(new ValuePair<>(origin, direction));
				}
			}
			return builder.build();
		}

		private Point3d createOrigin(final double c, final double d,
			final Vector3d translation)
		{
			final Vector3d a = new Vector3d(u);
			a.scale(c);
			final Vector3d b = new Vector3d(v);
			b.scale(d);
			final Point3d origin = new Point3d();
			origin.add(a);
			origin.add(b);
			origin.add(translation);
			rotateOp.mutate1(origin, rotation);
			origin.add(centroid);
			return origin;
		}

		/**
		 * Applies the rotation to the normal and unit vectors of the plane.
		 *
		 * @param rotation a rotation expressed as an {@link AxisAngle4d}.
		 * @param rotateOp an op for rotating.
		 */
		private void setRotation(final AxisAngle4d rotation,
			final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp)
		{
			this.rotation.set(rotation);
			this.rotateOp = rotateOp;
		}

		/**
		 * Sets the random generator used in generating the lines.
		 *
		 * @param random an instance of the {@link Random} class.
		 */
		private void setRandomGenerator(final Random random) {
			rng = random;
		}
	}

	/**
	 * The initial orientation of a plane, i.e. before rotation.
	 * <p>
	 * For example, if orientation is XY, then the unit vectors <b>u</b>, <b>v</b>
	 * of the parametric equation of the plane are the x- and y-axes respectively.
	 * </p>
	 */
	public enum Orientation {
			XY, XZ, YZ
	}

	private final class NoopRotation extends
		AbstractBinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d>
	{

		@Override
		public Tuple3d createOutput(final Tuple3d input1,
			final AxisAngle4d input2)
		{
			return new Vector3d(input1);
		}

		@Override
		public void compute(final Tuple3d input1, final AxisAngle4d input2,
			final Tuple3d output)
		{}

		@Override
		public void mutate1(final Tuple3d arg, final AxisAngle4d in) {}
	}
	// endregion
}
