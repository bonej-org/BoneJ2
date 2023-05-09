/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.bonej.ops.ellipsoid;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ops.OpEnvironment;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;

import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

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
// TODO Should vectors also be (returned) in homogeneous coordinates (Vector4d)?
public class Ellipsoid {

	private final Vector3d centroid = new Vector3d();
	private final Matrix3d orientation = new Matrix3d();
	private double a;
	private double b;
	private double c;
	// TODO Add a way to change the sampling function, either by passing and Op of
	// certain type, or by creating an enumerator.
	private BinaryFunctionOp<double[], Long, List<Vector3d>> isotropicSampling;

	/**
	 * Constructs an Ellipsoid.
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
	 * Constructs an Ellipsoid from semi-axes.
	 *
	 * @param u a semi-axis of the ellipsoid.
	 * @param v a semi-axis of the ellipsoid.
	 * @param w a semi-axis of the ellipsoid.
	 * @see #setSemiAxes(Vector3dc, Vector3dc, Vector3dc)
	 */
	public Ellipsoid(final Vector3dc u, final Vector3dc v, final Vector3dc w) {
		setSemiAxes(u, v, w);
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
		if (invalidRadius(a)) {
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
		if (invalidRadius(b)) {
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
		if (invalidRadius(c)) {
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
	 */
	public void setCentroid(final Vector3dc centroid)
	{
		this.centroid.set(centroid);
	}

	/**
	 * Gets a copy of the orientation unit vectors of the ellipsoid.
	 * <p>
	 * The orientations are the column vectors of the matrix.
	 * </p>
	 * <p>
	 * NB the vectors may form a left-handed basis! This may cause exceptions if
	 * the matrix is used with other linear algebra libraries.
	 * </p>
	 * 
	 * @return orientations of the semi-axes in homogeneous coordinates.
	 */
	public Matrix4d getOrientation() {
		return new Matrix4d(orientation);
	}

	/**
	 * Sets the values of the orientation vectors of the ellipsoid.
	 * <p>
	 * The 1st column vector will correspond to radius {@link #a}, and the 3rd to
	 * radius {@link #c}.
	 * </p>
	 *
	 * @param semiAxes matrix with the orientations of the semi-axes as column
	 *          vectors.
	 */
	public void setOrientation(final Matrix3dc semiAxes) {
		final Vector3d u = new Vector3d();
		semiAxes.getColumn(0, u);
		final Vector3d v = new Vector3d();
		semiAxes.getColumn(1, v);
		final Vector3d w = new Vector3d();
		semiAxes.getColumn(2, w);
		setOrientation(u, v, w);
	}

	/**
	 * Returns a copy of the semi-axes of the ellipsoid.
	 *
	 * @return semi-axes with radii a, b, c.
	 */
	public List<Vector3d> getSemiAxes() {
		final double[] radii = { a, b, c };
		final ArrayList<Vector3d> axes = Stream.generate(Vector3d::new).limit(3)
			.collect(Collectors.toCollection(ArrayList::new));
		for (int i = 0; i < 3; i++) {
			final Vector3d axis = axes.get(i);
			orientation.getColumn(i, axis);
			axis.mul(radii[i]);
		}
		return axes;
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
	 */
	@SuppressWarnings("unchecked")
	public void initSampling(final OpEnvironment ops) {
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
		points.forEach(this::mapToOrientation);
		points.forEach(p -> p.add(centroid));
		return points;
	}

	/**
	 * Sets the semi-axes of the ellipsoid.
	 * <p>
	 * Semi-axes will be sorted by length in ascending order so that the shortest
	 * vector becomes the 1st and the longest the 3rd column vector in the
	 * orientation matrix. Thus radius {@link #a} will be the length of the 1st
	 * column vector etc.
	 * </p>
	 *
	 * @param u a semi-axis of the ellipsoid.
	 * @param v a semi-axis of the ellipsoid.
	 * @param w a semi-axis of the ellipsoid.
	 */
	public void setSemiAxes(final Vector3dc u, final Vector3dc v,
		final Vector3dc w)
	{
		final List<Vector3d> semiAxes = Stream.of(u, v, w).map(Vector3d::new)
			.sorted(comparingDouble(Vector3d::length)).collect(toList());
		setC(semiAxes.get(2).length());
		setB(semiAxes.get(1).length());
		setA(semiAxes.get(0).length());
		setOrientation(semiAxes.get(0), semiAxes.get(1), semiAxes.get(2));
	}

	// region -- Helper methods --

	private static boolean invalidRadius(final double r) {
		return r <= 0 || !Double.isFinite(r);
	}

	private void mapToOrientation(final Vector3d v) {
		final Vector3d[] rowVectors = Stream.generate(Vector3d::new).limit(3)
			.toArray(Vector3d[]::new);
		final double[] coordinates = new double[3];
		for (int i = 0; i < 3; i++) {
			final Vector3d r = rowVectors[i];
			orientation.getRow(i, r);
			coordinates[i] = r.x * v.x() + r.y * v.y() + r.z * v.z();
		}
		v.set(coordinates[0], coordinates[1], coordinates[2]);
	}

	private boolean samplingInitialized() {
		return isotropicSampling != null;
	}

	private void setOrientation(final Vector3dc u, final Vector3dc v,
		final Vector3dc w) throws IllegalArgumentException
	{
		final double eps = 1e-12;
		if (u.dot(v) > eps || u.dot(w) > eps || v.dot(w) > eps) {
			throw new IllegalArgumentException("Vectors must be orthogonal");
		}
		orientation.setColumn(0, new Vector3d(u).normalize());
		orientation.setColumn(1, new Vector3d(v).normalize());
		orientation.setColumn(2, new Vector3d(w).normalize());
	}
	// endregion
}
