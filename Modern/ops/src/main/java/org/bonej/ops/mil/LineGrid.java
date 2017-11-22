
package org.bonej.ops.mil;

import static org.bonej.ops.mil.LineGrid.LinePlane.Orientation.XY;
import static org.bonej.ops.mil.LineGrid.LinePlane.Orientation.XZ;
import static org.bonej.ops.mil.LineGrid.LinePlane.Orientation.YZ;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import net.imagej.ops.OpEnvironment;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.Interval;
import net.imglib2.util.ValuePair;

import org.bonej.ops.RotateAboutAxis;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Vector3d;

/**
 * A class that generates lines through an interval in a cubical grid pattern.
 * <p>
 * The lines go through three orthogonal planes that meet at the corner of the
 * grid. The size of the grid is d<sup>3</sup>, where d = sqrt(width<sup>2</sup>
 * + height<sup>2</sup> + depth<sup>2</sup>)
 * </p>
 *
 * @author Richard Domander
 */
public final class LineGrid {

	private final LinePlane xy;
	private final LinePlane xz;
	private final LinePlane yz;
	private final Function<Interval, LongStream> dims = interval -> LongStream.of(
		interval.dimension(0), interval.dimension(1), interval.dimension(2));
	private final Point3d centroid;
	private final Vector3d xyTranslation = new Vector3d();
	private final Vector3d xzTranslation = new Vector3d();
	private final Vector3d yzTranslation = new Vector3d();
	private long count;

	/**
	 * Constructs an instance of {@link LineGrid}.
	 *
	 * @param interval a 3D interval.
	 * @param <I> type of the interval.
	 */
	public <I extends Interval> LineGrid(final I interval) {
		final boolean emptyDims = dims.apply(interval).anyMatch(i -> i == 0);
		if (interval.numDimensions() < 3 || emptyDims) {
			throw new IllegalArgumentException(
				"Interval must have at least three dimensions");
		}
		setRandomGenerator(new Random());
		final double planeSize = findPlaneSize(interval);
		centroid = findCentroid(interval);
		xy = new LinePlane(XY, planeSize);
		xz = new LinePlane(XZ, planeSize);
		yz = new LinePlane(YZ, planeSize);
		initTranslations(planeSize);
	}

	/**
	 * Creates the next line of the grid.
	 * <p>
	 * Alternates between the three planes in sequence.
	 * </p>
	 * <p>
	 * NB the line may not intersect the interval, or it may do so only at one
	 * point.
	 * </p>
	 *
	 * @return an (origin, direction) pair that describes a line.
	 * @see LinePlane#getLine()
	 */
	public ValuePair<Point3d, Vector3d> nextLine() {
		final int sequence = (int) (count % 3);
		final ValuePair<Point3d, Vector3d> line;
		switch (sequence) {
			case 0:
				line = xy.getLine();
				line.a.add(xyTranslation);
				break;
			case 1:
				line = xz.getLine();
				line.a.add(xzTranslation);
				break;
			case 2:
				line = yz.getLine();
				line.a.add(yzTranslation);
				break;
			default:
				throw new RuntimeException("Execution should not go here");
		}
		line.a.add(centroid);
		count++;
		return line;
	}

	/**
	 * Sets the random generator used in generating the lines.
	 *
	 * @param random an instance of the {@link Random} class.
	 */
	public void setRandomGenerator(final Random random) {
		LinePlane.setRandomGenerator(random);
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

	private void initTranslations(final double planeSize) {
		final double halfGrid = 0.5 * planeSize;
		xyTranslation.set(-halfGrid, -halfGrid, -halfGrid);
		xzTranslation.set(-halfGrid, -halfGrid, -halfGrid);
		yzTranslation.set(-halfGrid, -halfGrid, -halfGrid);
	}

	// endregion

	// region -- Helper class --

	/**
	 * A class that generates lines, which pass through a plane in a direction
	 * parallel to its normal.
	 */
	public static final class LinePlane {

		private static final double[] X_UNIT = { 1, 0, 0 };
		private static final double[] Y_UNIT = { 0, 1, 0 };
		private static final double[] Z_UNIT = { 0, 0, 1 };
		private static Random rng = new Random();
		private final Vector3d u = new Vector3d();
		private final Vector3d v = new Vector3d();
		private final Vector3d normal = new Vector3d();

		/**
		 * Constructs an instance of {@link LinePlane}.
		 *
		 * @param orientation initial orientation of the plane.
		 */
		public LinePlane(final Orientation orientation) {
			this(orientation, 1.0);
		}

		/**
		 * Constructs an instance of {@link LinePlane}.
		 *
		 * @param orientation initial orientation of the plane.
		 * @param scalar the size of the four equal sides of the plane. More
		 *          formally, it's the scalar s that multiplies the unit vectors
		 *          used in line generation.
		 * @see #getLine()
		 * @throws IllegalArgumentException if scalar is not a finite number.
		 */
		public LinePlane(final Orientation orientation, final double scalar)
			throws IllegalArgumentException
		{
			if (!Double.isFinite(scalar)) {
				throw new IllegalArgumentException("Scalar must be a finite number");
			}

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
			u.scale(scalar);
			v.scale(scalar);
		}

		/**
		 * Returns a line in the parametric form a + t * v, where a is an origin
		 * point on the plane, t = 1 and v is a vector normal to the plane.
		 * <p>
		 * The origin points are generated from the parametric equation of the plane
		 * r = r<sub>0</sub> + s * (c * u + d * v), where r<sub>0</sub> = (0, 0, 0),
		 * s = the scalar set in the constructor, c,d ∈ [0.0, 1.0) random numbers,
		 * and u,v are orthogonal unit vectors. By default s = 1.
		 * </p>
		 *
		 * @return a (origin, direction) pair. Direction is a unit vector.
		 */
		public ValuePair<Point3d, Vector3d> getLine() {
			final double c = rng.nextDouble();
			final double d = rng.nextDouble();
			final Point3d origin = new Point3d(u);
			origin.scale(c);
			origin.scaleAdd(d, v, origin);
			final Vector3d direction = new Vector3d(normal);
			return new ValuePair<>(origin, direction);
		}

		/**
		 * Applies the rotation to the normal and unit vectors of the plane.
		 *
		 * @param rotation a rotation expressed as an {@link AxisAngle4d}.
		 * @param ops an {@link OpEnvironment} where a rotation op can be found.
		 * @see #getLine()
		 * @throws NullPointerException if rotation is null
		 */
		public void setRotation(final AxisAngle4d rotation, final OpEnvironment ops)
			throws NullPointerException
		{
			if (rotation == null) {
				throw new NullPointerException("Rotation cannot be null");
			}
			final BinaryHybridCFI1<Vector3d, AxisAngle4d, Vector3d> rotateOp = Hybrids
				.binaryCFI1(ops, RotateAboutAxis.class, Vector3d.class, new Vector3d(),
					rotation);
			rotateOp.mutate1(u, rotation);
			rotateOp.mutate1(v, rotation);
			rotateOp.mutate1(normal, rotation);
		}

		/**
		 * Sets the random generator used in generating the lines.
		 *
		 * @param random an instance of the {@link Random} class.
		 * @throws NullPointerException if random is null.
		 */
		public static void setRandomGenerator(final Random random)
			throws NullPointerException
		{
			if (random == null) {
				throw new NullPointerException("Random generator cannot be set null");
			}
			rng = random;
		}

		/**
		 * The initial orientation of the plane, i.e. before rotation.
		 * <p>
		 * For example, if orientation is XY, then the unit vectors u, v of the
		 * parametric equation of the plane are the x- and y-axes respectively.
		 * </p>
		 */
		public enum Orientation {
				XY, XZ, YZ
		}
	}
	// endregion
}
