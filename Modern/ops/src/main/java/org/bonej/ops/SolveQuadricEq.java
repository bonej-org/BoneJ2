/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.ops;

import java.util.Collection;
import java.util.Iterator;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.ojalgo.matrix.BasicMatrix.Builder;
import org.ojalgo.matrix.PrimitiveMatrix;
import org.ojalgo.random.Deterministic;
import org.scijava.plugin.Plugin;

/**
 * An op that fits a quadratic surface (quadric) into a set of points.
 * <p>
 * The op first solves the quadric that best fits the point cloud by minimising
 * the distance by least squares fitting. It's found by solving a polynomial -
 * the general equation of a quadric. The algorithm is sensitive to outlier
 * points.
 * </p>
 * <p>
 * The op is based on the the implementations of Yury Petrov &amp; "KalebKE".
 * </p>
 * <p>
 * The input collection must have at least {@link #QUADRIC_TERMS} points.
 * </p>
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class SolveQuadricEq extends
	AbstractUnaryFunctionOp<Collection<Vector3d>, Matrix4d> implements Contingent
{

	/**
	 * Number of terms in the quadric equation that needs to be solved.
	 * <p>
	 * Due the number of terms, we also need at least 9 points in the input to
	 * solve the equation.
	 * </p>
	 */
	public static final int QUADRIC_TERMS = 9;

	@Override
	public Matrix4d calculate(final Collection<Vector3d> points) {
		final double[] vector = solveVector(points);
		return toQuadricMatrix(vector);
	}

	@Override
	public boolean conforms() {
		return in().size() >= QUADRIC_TERMS;
	}

	/**
	 * Creates a design matrix used for least squares fitting from a collection of
	 * points.
	 *
	 * @see #solveVector(Collection)
	 * @param points points in a 3D space.
	 * @return a [points.size()][9] matrix of real values.
	 */
	private static PrimitiveMatrix createDesignMatrix(
		final Collection<Vector3d> points)
	{
		final Builder<PrimitiveMatrix> builder = PrimitiveMatrix.FACTORY.getBuilder(
			points.size(), QUADRIC_TERMS);
		final Iterator<Vector3d> iterator = points.iterator();
		for (int i = 0; i < points.size(); i++) {
			final Vector3d p = iterator.next();
			builder.set(i, 0, p.x * p.x);
			builder.set(i, 1, p.y * p.y);
			builder.set(i, 2, p.z * p.z);
			builder.set(i, 3, 2 * p.x * p.y);
			builder.set(i, 4, 2 * p.x * p.z);
			builder.set(i, 5, 2 * p.y * p.z);
			builder.set(i, 6, 2 * p.x);
			builder.set(i, 7, 2 * p.y);
			builder.set(i, 8, 2 * p.z);
		}
		return builder.build();
	}

	/**
	 * Solves the equation for the quadratic surface that best fits the given
	 * points.
	 * <p>
	 * The vector solved is the polynomial Ax<sup>2</sup> + By<sup>2</sup> +
	 * Cz<sup>2</sup> + 2Dxy + 2Exz + 2Fyz + 2Gx + 2Hy + 2Iz, i.e. the general
	 * equation of a quadric. The fitting is done with least squares.
	 * </p>
	 *
	 * @param points A collection of points in a 3D space.
	 * @return the solution vector of the surface.
	 */
	private static double[] solveVector(final Collection<Vector3d> points) {
		final int n = points.size();
		// Find (dT * d)^-1
		final PrimitiveMatrix d = createDesignMatrix(points);
		final PrimitiveMatrix dT = d.transpose();
		final PrimitiveMatrix dTDInv = dT.multiply(d).invert();
		// Multiply dT * O, where O = [1, 1, ... 1] (n x 1) matrix
		final PrimitiveMatrix o = PrimitiveMatrix.FACTORY.makeFilled(n, 1,
			new Deterministic(1.0));
		final PrimitiveMatrix dTO = dT.multiply(o);
		// Find solution A = (dT * d)^-1 * (dT * O)
		return dTDInv.multiply(dTO).toRawCopy1D();
	}

	/**
	 * Creates a matrix out of a quadric surface solution vector in homogeneous
	 * coordinates (w = 1).
	 *
	 * @see #solveVector(Collection)
	 * @return a matrix representing the polynomial solution vector in an
	 *         algebraic form.
	 */
	private Matrix4d toQuadricMatrix(final double[] solution) {
		// I'm not a clever man, so I'm using named variables and row setters to
		// better follow the matrix assignment.
		final double a = solution[0];
		final double b = solution[1];
		final double c = solution[2];
		final double d = solution[3];
		final double e = solution[4];
		final double f = solution[5];
		final double g = solution[6];
		final double h = solution[7];
		final double i = solution[8];
		// @formatter:off
		return new Matrix4d(
				a, d, e, g,
				d, b, f, h,
				e, f, c, i,
				g, h, i, -1
		);
		// @formatter:on
	}
}
