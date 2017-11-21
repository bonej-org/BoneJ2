
package org.bonej.ops.mil;

import java.util.Random;

import net.imagej.ops.OpEnvironment;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.util.ValuePair;

import org.bonej.ops.RotateAboutAxis;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Vector3d;

/**
 * A class that describes a grid of vectors around a stack.
 *
 * @author Richard Domander
 */
public final class StackSamplingGrid {

	// region -- Helper class --

	/**
	 * A class that generates lines, which pass through a plane in a direction
	 * parallel to its normal.
	 */
	public static final class StackSamplingPlane {

		private static final double[] X_UNIT = { 1, 0, 0 };
		private static final double[] Y_UNIT = { 0, 1, 0 };
		private static final double[] Z_UNIT = { 0, 0, 1 };
		private static Random rng = new Random();
		private final Vector3d u = new Vector3d();
		private final Vector3d v = new Vector3d();
		private final Vector3d normal = new Vector3d();

		/**
		 * Constructs an instance of {@link StackSamplingPlane}.
		 *
		 * @param orientation initial orientation of the plane.
		 */
		public StackSamplingPlane(final Orientation orientation) {
			this(orientation, 1.0);
		}

		/**
		 * Constructs an instance of {@link StackSamplingPlane}.
		 *
		 * @param orientation initial orientation of the plane.
		 * @param scalar the size of the four equal sides of the plane. More
		 *          formally, it's the scalar s that multiplies the unit vectors
		 *          used in line generation.
		 * @see #getSamplingLine()
		 * @throws IllegalArgumentException if scalar is not a finite number.
		 */
		public StackSamplingPlane(final Orientation orientation,
			final double scalar) throws IllegalArgumentException
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
		public ValuePair<Point3d, Vector3d> getSamplingLine() {
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
		 * @see #getSamplingLine()
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
		 * Sets the random generator used in generating sampling lines.
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
