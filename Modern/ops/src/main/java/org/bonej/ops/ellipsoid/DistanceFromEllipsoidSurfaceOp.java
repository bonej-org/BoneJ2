/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.type.numeric.real.DoubleType;

import org.joml.Matrix4dc;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * An Op that calculates the distance between a point and an ellipsoid surface
 * <p>
 * Uses a hard-coded bivariate Newton-Raphson solver to efficiently find values
 * of theta and phi that solve the orthogonality condition F(theta,phi)=0 for
 * the vector between the surface and the point. Then converts into Cartesian
 * coordinates to calculate the distance. Derivation of required terms courtesy
 * of Robert Nürnberg, Imperial College London (See <a href=
 * "https://wwwf.imperial.ac.uk/~rn/distance2ellipse.pdf">https://wwwf.imperial.ac.uk/~rn/distance2ellipse.pdf</a>).
 * Works for three-dimensional case only.
 * </p>
 *
 * @author Alessandro Felder
 */

@Plugin(name = "Distance from Point to Ellipsoid Surface", type = Op.class)
public class DistanceFromEllipsoidSurfaceOp extends
	AbstractBinaryFunctionOp<Ellipsoid, Vector3dc, DoubleType>
{

	/**
	 * Tolerance below which the Newton-Raphson solver considers the current
	 * solution converged
	 */
	@Parameter(required = false, persist = false)
	private double tolerance = 1.0e-12;

	/**
	 * Maximum number of iterations performed by Newton-Raphson solver
	 */
	@Parameter(required = false, persist = false)
	private long maxIterations = 100;

	/**
	 * Calculates the shortest distance between a point and the surface of an
	 * ellipsoid
	 * 
	 * @param ellipsoid the ellipsoid in question
	 * @param point the point in question
	 * @return shortest distance
	 * @throws IllegalArgumentException if {@link #tolerance} is negative, or
	 *           {@link #maxIterations} is not positive.
	 */
	@Override
	public DoubleType calculate(final Ellipsoid ellipsoid, final Vector3dc point)
		throws IllegalArgumentException
	{
		if (tolerance < 0.0) {
			throw new IllegalArgumentException("Tolerance cannot be negative");
		}
		if (maxIterations < 1) {
			throw new IllegalArgumentException("Max iterations must be positive");
		}
		final double a = ellipsoid.getA();
		final double b = ellipsoid.getB();
		final double c = ellipsoid.getC();
		final Vector3dc pointInEllipsoidCoordinates = toEllipsoidCoordinates(point,
			ellipsoid);

		final double rootTerm = Math.sqrt(pointInEllipsoidCoordinates.x() *
			pointInEllipsoidCoordinates.x() / (a * a) + pointInEllipsoidCoordinates.y() *
				pointInEllipsoidCoordinates.y() / (b * b));
		Vector2dc anglesK = new Vector2d(Math.atan2(a *
			pointInEllipsoidCoordinates.y(), b * pointInEllipsoidCoordinates.x()), Math
				.atan2(pointInEllipsoidCoordinates.z(), c * rootTerm));
		Vector2d anglesKPlus1 = new Vector2d(0.0, 0.0);
		long iterations = 0;
		while (iterations < maxIterations) {
			anglesKPlus1 = new Vector2d(anglesK.x(), anglesK.y());
			anglesKPlus1.add(inverseJacobian(anglesK, ellipsoid,
				pointInEllipsoidCoordinates));
			if (getDifference(anglesK, anglesKPlus1) <= tolerance) {
				break;
			}
			anglesK = new Vector2d(anglesKPlus1.x, anglesKPlus1.y);
			iterations++;
		}

		final Vector3d closestPointOnEllipsoidSurface =
			getCartesianCoordinatesFromAngleParametrization(anglesKPlus1, ellipsoid);
		final Vector3d v = new Vector3d(closestPointOnEllipsoidSurface);
		v.negate();
		v.add(pointInEllipsoidCoordinates);
		return new DoubleType(v.length());
	}

	/**
	 * Get the 3D vector x(theta, phi) on the ellipsoid surface from the angle
	 * parameters (theta, phi). The ellipsoid surface is parametrized by
	 * (a*cos(phi)*cos(theta), b*cos(phi)*sin(theta), ), where a,b,c are the
	 * semi-axis lengths.
	 *
	 * @param angles a 2D vector containing (theta,phi)
	 * @param ellipsoid the ellipsoid
	 * @return x(theta,phi)
	 */
	private static Vector3d getCartesianCoordinatesFromAngleParametrization(
		final Vector2dc angles, final Ellipsoid ellipsoid)
	{
		final double theta = angles.x();
		final double phi = angles.y();
		final double x = ellipsoid.getA() * Math.cos(phi) * Math.cos(theta);
		final double y = ellipsoid.getB() * Math.cos(phi) * Math.sin(theta);
		final double z = ellipsoid.getC() * Math.sin(phi);
		return new Vector3d(x, y, z);
	}

	private static double getDifference(final Vector2dc angles1,
		final Vector2dc angles2)
	{
		final Vector2d difference = new Vector2d(angles1);
		difference.sub(angles2);
		return difference.length();
	}

	/**
	 * Calculates the inverse Jacobian matrix DF^{-1} multiplied by F(angles) -
	 * details in (See <a href=
	 * "https://wwwf.imperial.ac.uk/~rn/distance2ellipse.pdf">https://wwwf.imperial.ac.uk/~rn/distance2ellipse.pdf</a>)
	 * 
	 * @param angles current estimate for ellipsoid surface angular parameters
	 * @param ellipsoid ellipsoid to which the closest distance should be found
	 * @param point point for which the closest distance to the ellipsoid surface
	 *          should be found
	 * @return inverse Jacobian matrix DF^{-1} times F(angles)
	 */
	private static Vector2d inverseJacobian(final Vector2dc angles,
		final Ellipsoid ellipsoid, final Vector3dc point)
	{
		final double a = ellipsoid.getA();
		final double b = ellipsoid.getB();
		final double c = ellipsoid.getC();
		final double a2mb2 = (a * a - b * b);

		final double x = point.x();
		final double y = point.y();
		final double z = point.z();

		final double theta = angles.x();
		final double sinTheta = Math.sin(theta);
		final double sinThetaSq = sinTheta * sinTheta;
		final double cosTheta = Math.cos(theta);
		final double cosThetaSq = cosTheta * cosTheta;

		final double phi = angles.y();
		final double sinPhi = Math.sin(phi);
		final double cosPhi = Math.cos(phi);

		final double a11 = a2mb2 * (cosThetaSq - sinThetaSq) * cosPhi - x * a *
			cosTheta - y * b * sinTheta;
		final double a12 = -a2mb2 * cosTheta * sinTheta * sinPhi;
		final double a21 = -2.0 * a2mb2 * cosTheta * sinTheta * sinPhi * cosPhi +
			x * a * sinPhi * sinTheta - y * b * sinPhi * cosTheta;
		final double a22 = (a * a * cosThetaSq + b * b * sinThetaSq - c * c) *
			(cosThetaSq - sinThetaSq) - x * a * cosPhi * cosTheta - y * b * cosPhi *
				sinTheta - z * c * sinPhi;

		final double f1 = a2mb2 * cosTheta * sinTheta * cosPhi - x * a * sinTheta +
			y * b * cosTheta;
		final double f2 = (a * a * cosThetaSq + b * b * sinThetaSq - c * c) *
			sinPhi * cosPhi - x * a * sinPhi * cosTheta - y * b * sinPhi * sinTheta +
			z * c * cosPhi;

		final double out1 = a22 * f1 - a12 * f2;
		final double out2 = -a21 * f1 + a11 * f2;

		final double determinant = a11 * a22 - a12 * a21;

		if (determinant == 0.0) {
			throw new ArithmeticException("Solution is not unique.");
		}
		return new Vector2d(out1 / determinant, out2 / determinant);
	}

	/**
	 * Perform appropriate transformations on point to find its representation
	 * relative to ellipsoid centre and orientation.
	 *
	 * @param point point to transform
	 * @param ellipsoid ellipsoid determining coordinates
	 * @return point in ellipsoid coordinates
	 */
	static Vector3dc toEllipsoidCoordinates(final Vector3dc point,
		final Ellipsoid ellipsoid)
	{
		final Vector3d translated = new Vector3d(ellipsoid.getCentroid());
		translated.mul(-1.0);
		translated.add(point);

		final Matrix4dc orientation = ellipsoid.getOrientation();
		final double x = orientation.m00() * translated.x + orientation.m10() *
			translated.y + orientation.m20() * translated.z;
		final double y = orientation.m01() * translated.x + orientation.m11() *
			translated.y + orientation.m21() * translated.z;
		final double z = orientation.m02() * translated.x + orientation.m12() *
			translated.y + orientation.m22() * translated.z;
		return new Vector3d(x, y, z);
	}
}
