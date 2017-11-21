
package org.bonej.ops.mil;

import java.util.Random;

import net.imglib2.util.ValuePair;

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
		 * Constructs an instance of {@link StackSamplingGrid}.
		 *
		 * @param orientation initial orientation of the plane.
		 */
		public StackSamplingPlane(final Orientation orientation) {
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
		}

		/**
		 * Returns a line in the parametric form a + t * v, where a is an origin
		 * point on the plane, t = 1 and v is a vector normal to the plane.
		 *
		 * @return a (origin, direction) pair. Direction is a unit vector.
		 */
		public ValuePair<Point3d, Vector3d> getSamplingLine() {
			final double s = rng.nextDouble();
			final double t = rng.nextDouble();
			final Point3d origin = new Point3d(u);
			origin.scale(s);
			origin.scaleAdd(t, v, origin);
			final Vector3d direction = new Vector3d(normal);
			return new ValuePair<>(origin, direction);
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
		 * For example, if orientation is XY, then the plane is parallel to the x-
		 * and y-axes.
		 * </p>
		 */
		public enum Orientation {
				XY, XZ, YZ
		}
	}
	// endregion
}
