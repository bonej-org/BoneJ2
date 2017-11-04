
package org.bonej.ops.ellipsoid;

import java.util.Arrays;
import java.util.List;

import net.imagej.ops.OpEnvironment;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;

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
	// TODO Add a way to change the sampling function, either by passing and Op of
	// certain type, or by creating an enumerator.
	private BinaryFunctionOp<double[], Long, List<Vector3d>> isotropicSampling;
	private OpEnvironment ops;

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
	}

	/**
	 * Constructs an {@link Ellipsoid} object.
	 * <p>
	 * The radii will be sorted in the constructor.
	 * </p>
	 * 
	 * @param a smallest radius of the ellipsoid.
	 * @param b 2nd radius of the ellipsoid.
	 * @param c largest radius of the ellipsoid.
	 * @param ops the environment used for the sampling ops.
	 */
	public Ellipsoid(final double a, final double b, final double c,
		final OpEnvironment ops)
	{
		this(a, b, c);
		setOpEnvironment(ops);
	}

	/**
	 * Sets the OpEnvironment where sampling ops can be found.
	 *
	 * @param ops a reference to the op environment in the current context.
	 * @throws NullPointerException if ops is null
	 */
	public void setOpEnvironment(final OpEnvironment ops)
		throws NullPointerException
	{
		if (ops == null) {
			throw new NullPointerException("Op environment can't be set null");
		}
		this.ops = ops;
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
	 * Gets the second radius of the ellipsoid.
	 *
	 * @return the real value radius.
	 */
	public double getB() {
		return b;
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

	private static boolean validRadius(final double r) {
		return r > 0 && Double.isFinite(r);
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
	 * Return a random collection of points on the ellipsoid surface.
	 *
	 * @param n number of points generated.
	 * @return a collection of points isotropically distributed on the ellipsoid.
	 * @throws NullPointerException if the object has no {@link OpEnvironment}.
	 * @see #setOpEnvironment(OpEnvironment)
	 */
	// TODO Remove OpEnvironment field and make param, or setSamplingMode?
	public List<Vector3d> samplePoints(final long n) throws NullPointerException {
		if (ops == null) {
			throw new NullPointerException("Can't sample without an op environment");
		}
		if (!samplingInitialized()) {
			matchSamplingOp(n);
		}
		final List<Vector3d> points = isotropicSampling.calculate(new double[] { a,
			b, c }, n);
		points.forEach(p -> p.add(centroid));
		return points;
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

	@SuppressWarnings("unchecked")
	private void matchSamplingOp(final long n) {
		isotropicSampling = (BinaryFunctionOp) Functions.binary(ops,
			EllipsoidPoints.class, List.class, new double[] { a, b, c }, n);
	}

	private boolean samplingInitialized() {
		return isotropicSampling != null;
	}
}
