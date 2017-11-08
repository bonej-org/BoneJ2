
package org.bonej.ops.ellipsoid;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import net.imagej.ops.OpEnvironment;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;

import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;

/**
 * A class that stores the properties of an ellipsoid.
 * <p>
 * The class doesn't allow degenerate cases like a disc where one or more of the
 * radii is zero.
 * </p>
 *
 * @author Richard Domander
 * @author Alessandro Felder
 */
public class Ellipsoid {

	private double a;
	private double b;
	private double c;
	private Vector3d centroid = new Vector3d();
	private Matrix3d orientation = new Matrix3d();
	// TODO Add a way to change the sampling function, either by passing and Op of
	// certain type, or by creating an enumerator.
	private BinaryFunctionOp<double[], Long, List<Vector3d>> isotropicSampling;

	/**
	 * Constructs an {@link Ellipsoid} object.
	 * <p>
	 * The radii will be sorted in the constructor.
	 * </p>
	 *
	 * @param a smallest radius of the ellipsoid.
	 * @param b 2nd radius of the ellipsoid.
	 * @param c largest radius of the ellipsoid.
	 */
	public Ellipsoid(final double a, final double b, final double c) {
		final double[] radii = { a, b, c };
		Arrays.sort(radii);
		setC(radii[2]);
		setB(radii[1]);
		setA(radii[0]);
		orientation.setIdentity();
	}

	/**
	 * Gets the smallest radius of the ellipsoid.
	 *
	 * @return the real value radius.
	 */
	public double getA() {
		return a;
	}

	/**
	 * Sets the smallest radius of the ellipsoid.
	 *
	 * @param a new value for the smallest radius.
	 * @throws IllegalArgumentException if the radius is non-positive, non-finite,
	 *           or greater than b or c.
	 */
	public void setA(final double a) throws IllegalArgumentException {
		if (!validRadius(a)) {
			throw new IllegalArgumentException(
				"Radius must be a finite positive number.");
		}
		if (a > b) {
			throw new IllegalArgumentException("Radius 'a' must be the smallest.");
		}
		this.a = a;
	}

	/**
	 * Gets the second radius of the ellipsoid.
	 *
	 * @return the real value radius.
	 */
	public double getB() {
		return b;
	}

	/**
	 * Sets the second radius of the ellipsoid.
	 *
	 * @param b new value for the second radius.
	 * @throws IllegalArgumentException if the radius is non-positive, non-finite,
	 *           or greater than c or less than a.
	 */
	public void setB(final double b) throws IllegalArgumentException {
		if (!validRadius(b)) {
			throw new IllegalArgumentException(
				"Radius must be a finite positive number.");
		}
		if (b < a || b > c) {
			throw new IllegalArgumentException(
				"Radius 'b' must be between 'a' and 'c'.");
		}
		this.b = b;
	}

	/**
	 * Gets the largest radius of the ellipsoid.
	 *
	 * @return the real value radius.
	 */
	public double getC() {
		return c;
	}

	/**
	 * Sets the largest radius of the ellipsoid.
	 *
	 * @param c new value for the smallest radius.
	 * @throws IllegalArgumentException if the radius is non-positive, non-finite
	 *           or less than b or c.
	 */
	public void setC(final double c) throws IllegalArgumentException {
		if (!validRadius(c)) {
			throw new IllegalArgumentException(
				"Radius must be a finite positive number.");
		}
		if (c < b) {
			throw new IllegalArgumentException("Radius 'c' must be the largest.");
		}
		this.c = c;
	}

	/**
	 * Gets a copy of center point of the ellipsoid.
	 *
	 * @return the centroid of the ellipsoid.
	 */
	public Vector3d getCentroid() {
		return new Vector3d(centroid);
	}

	/**
	 * Sets the coordinates of the centroid of the ellipsoid.
	 *
	 * @param centroid the new coordinates of the center point.
	 * @throws NullPointerException if the parameter is null.
	 */
	public void setCentroid(final Vector3d centroid) throws NullPointerException {
		if (centroid == null) {
			throw new NullPointerException("Centroid can't be null");
		}
		this.centroid.set(centroid);
	}

	/**
	 * Gets a copy of the orientation vectors of the ellipsoid.
	 * <p>
	 * The orientations are the column vectors of the matrix.
	 * </p>
	 *
	 * @return orientations of the semi-axes in homogeneous coordinates.
	 */
	public Matrix4d getOrientation() {
		final Matrix4d homogeneous = new Matrix4d();
		homogeneous.set(orientation);
		return homogeneous;
	}

	/**
	 * Sets the values of the orientation vectors of the ellipsoid.
	 *
	 * @param semiAxes matrix with the orientations of the semi-axes as column
	 *          vectors.
	 * @throws IllegalArgumentException if the matrix has non-positive determinant
	 *           or the column vectors are not orthogonal.
	 * @throws NullPointerException if the matrix is null.
	 */
	public void setOrientation(final Matrix3d semiAxes)
		throws IllegalArgumentException, NullPointerException
	{
		if (semiAxes == null) {
			throw new NullPointerException("Matrix cannot be null");
		}
		if (semiAxes.determinant() <= 0) {
			throw new IllegalArgumentException(
				"A rotation matrix must have a positive determinant");
		}
		final Vector3d u = new Vector3d();
		semiAxes.getColumn(0, u);
		final Vector3d v = new Vector3d();
		semiAxes.getColumn(1, v);
		final Vector3d w = new Vector3d();
		semiAxes.getColumn(2, w);
		if (u.dot(v) != 0 || u.dot(w) != 0 || v.dot(w) != 0) {
			throw new IllegalArgumentException("Vectors must be orthogonal");
		}
		u.normalize();
		v.normalize();
		w.normalize();
		final double[][] columns = Stream.of(u, v, w).map(e -> new double[] { e.x,
			e.y, e.z, 0 }).toArray(double[][]::new);
		for (int i = 0; i < 3; i++) {
			orientation.setColumn(i, columns[i]);
		}
	}

	/**
	 * Gets the volume of the ellipsoid.
	 *
	 * @return ellipsoid volume.
	 */
	public double getVolume() {
		return (4.0 / 3.0) * Math.PI * a * b * c;
	}

	/**
	 * Initializes the ellipsoid point sampling function.
	 *
	 * @param ops the op environment of the current context.
	 * @throws NullPointerException if the parameter is null
	 */
	@SuppressWarnings("unchecked")
	public void initSampling(final OpEnvironment ops)
		throws NullPointerException
	{
		if (ops == null) {
			throw new NullPointerException("Op environment cannot be null");
		}
		isotropicSampling = (BinaryFunctionOp) Functions.binary(ops,
			EllipsoidPoints.class, List.class, new double[] { a, b, c }, 0L);
	}

	/**
	 * Return a random collection of points on the ellipsoid surface.
	 *
	 * @param n number of points generated.
	 * @return a collection of points isotropically distributed on the ellipsoid.
	 * @throws RuntimeException if sampling hasn't been initialized.
	 * @see #initSampling(OpEnvironment)
	 */
	public List<Vector3d> samplePoints(final long n) throws RuntimeException {
		if (!samplingInitialized()) {
			throw new RuntimeException("Sampling has not been initialized");
		}
		final List<Vector3d> points = isotropicSampling.calculate(new double[] { a,
			b, c }, n);
		points.forEach(p -> p.add(centroid));
		// TODO add orientation
		return points;
	}

	private boolean samplingInitialized() {
		return isotropicSampling != null;
	}

	private static boolean validRadius(final double r) {
		return r > 0 && Double.isFinite(r);
	}
}
