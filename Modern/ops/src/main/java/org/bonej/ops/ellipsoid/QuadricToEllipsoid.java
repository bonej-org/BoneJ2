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

package org.bonej.ops.ellipsoid;

import java.util.Arrays;
import java.util.Optional;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.GMatrix;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;
import org.scijava.vecmath.SingularMatrixException;

/**
 * Tries to create an {@link Ellipsoid} from a general equation of a quadratic
 * surface i.e. quadric.
 * <p>
 * The input equation must be in a matrix. If the quadric's polynomial in
 * homogeneous coordinates (w = 1) is Ax<sup>2</sup> + By<sup>2</sup> +
 * Cz<sup>2</sup> + 2Dxy + 2Exz + 2Fyz + 2Gx + 2Hy + 2Iz, then the matrix must
 * be:<br>
 * </p>
 * 
 * <pre>
 * [a, d, e, g]
 * [d, b, f, h]
 * [e, f, c, i]
 * [g, h, i, -1]
 * </pre>
 * 
 * @author Richard Domander
 * @see org.bonej.ops.SolveQuadricEq
 */
@Plugin(type = Op.class)
public class QuadricToEllipsoid extends
	AbstractUnaryFunctionOp<Matrix4d, Optional<Ellipsoid>>
{

	@Override
	public Optional<Ellipsoid> calculate(final Matrix4d quadricSolution) {
		try {
			Vector3d center = findCenter(quadricSolution);
			final Matrix4d translated = translateToCenter(quadricSolution, center);
			final EigenDecomposition decomposition = solveEigenDecomposition(
					translated);
			final boolean invalidEv = Arrays.stream(decomposition.getRealEigenvalues())
					.anyMatch(e -> e <= 0.0);
			if (invalidEv) {
				return Optional.empty();
			}
			final double[] radii = Arrays.stream(decomposition.getRealEigenvalues())
					.map(ev -> Math.sqrt(1.0 / ev)).toArray();
			final Ellipsoid ellipsoid = new Ellipsoid(radii[0], radii[1], radii[2]);
			ellipsoid.setCentroid(center);
			final Matrix3d orientation = toOrientationMatrix(decomposition);
			ellipsoid.setOrientation(orientation);
			return Optional.of(ellipsoid);
		}
		catch (Exception e)
		{
			//System.out.println(e.toString()+" occurred in maths underlying QuadricToEllipsoid, returning empty");
			return Optional.empty();
		}
	}

	/**
	 * Finds the center point of a quadric surface.
	 *
	 * @param quadric the general equation of a quadric in algebraic matrix form.
	 * @return the 3D center point in a vector.
	 */
	private static Vector3d findCenter(final Matrix4d quadric) {
		// @formatter:off
		final GMatrix sub = new GMatrix(3, 3, new double[] {
				quadric.m00, quadric.m01, quadric.m02,
				quadric.m10, quadric.m11, quadric.m12,
				quadric.m20, quadric.m21, quadric.m22
		});
		// @formatter:on
		sub.negate();
		sub.invert();
		final GMatrix translation = new GMatrix(3, 1, new double[] { quadric.m03,
			quadric.m13, quadric.m23 });
		final GMatrix center = new GMatrix(3, 1);
		center.mul(sub, translation);
		final double[] centerCoords = new double[3];
		center.getColumn(0, centerCoords);
		return new Vector3d(centerCoords);
	}

	// Using apache.commons.math3 since scijava.vecmath doesn't yet have eigen
	// decomposition, and I can't figure out how to use the tensor eigen stuff
	// from net.imglib2.algorithm.linalg.eigen
	private static EigenDecomposition solveEigenDecomposition(
		final Matrix4d quadric)
	{
		// @formatter:off
		final RealMatrix input = new Array2DRowRealMatrix(new double[][]{
				{quadric.m00, quadric.m01, quadric.m02},
				{quadric.m10, quadric.m11, quadric.m12},
				{quadric.m20, quadric.m21, quadric.m22},
		}).scalarMultiply(-1.0 / quadric.m33);
		// @formatter:on
		return new EigenDecomposition(input);
	}

	private static Matrix3d toOrientationMatrix(
		final EigenDecomposition decomposition)
	{
		final RealVector e1 = decomposition.getEigenvector(0);
		final RealVector e2 = decomposition.getEigenvector(1);
		final RealVector e3 = decomposition.getEigenvector(2);
		final Vector3d x = new Vector3d(e1.getEntry(0), e1.getEntry(1), e1.getEntry(
			2));
		final Vector3d y = new Vector3d(e2.getEntry(0), e2.getEntry(1), e2.getEntry(
			2));
		final Vector3d z = new Vector3d(e3.getEntry(0), e3.getEntry(1), e3.getEntry(
			2));
		final Matrix3d orientation = new Matrix3d();
		orientation.setColumn(0, x);
		orientation.setColumn(1, y);
		orientation.setColumn(2, z);
		return orientation;
	}

	/**
	 * Translates the quadratic surface to the center point.
	 *
	 * @param quadric the general equation of a quadric in algebraic matrix form.
	 * @param center the center point of the surface.
	 */
	private static Matrix4d translateToCenter(final Matrix4d quadric,
		final Vector3d center)
	{
		//@formatter:off
		final Matrix4d t = new Matrix4d(
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, 0,
				center.x, center.y, center.z, 1
		);
		//@formatter:on
		final Matrix4d tT = new Matrix4d(t);
		tT.transpose();
		final Matrix4d translated = new Matrix4d();
		translated.mul(t, quadric);
		translated.mul(tT);
		return translated;
	}
}
