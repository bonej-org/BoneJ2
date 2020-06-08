/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.plugin.Plugin;

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
 * @see net.imagej.ops.stats.regression.leastSquares.Quadric
 */
@Plugin(type = Op.class)
public class QuadricToEllipsoid extends
	AbstractUnaryFunctionOp<Matrix4dc, Optional<Ellipsoid>>
{

	@Override
	public Optional<Ellipsoid> calculate(final Matrix4dc quadricSolution) {
		final Vector3dc center = findCenter(quadricSolution);
		final Matrix4dc translated = translateToCenter(quadricSolution, center);
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
		final Matrix3dc orientation = toOrientationMatrix(decomposition);
		ellipsoid.setOrientation(orientation);
		return Optional.of(ellipsoid);
	}

	/**
	 * Finds the center point of a quadric surface.
	 *
	 * @param quadric the general equation of a quadric in algebraic matrix form.
	 * @return the 3D center point in a vector.
	 */
	private static Vector3dc findCenter(final Matrix4dc quadric) {
		// @formatter:off
		final Matrix3dc sub = new Matrix3d(
				quadric.m00(), quadric.m01(), quadric.m02(),
				quadric.m10(), quadric.m11(), quadric.m12(),
				quadric.m20(), quadric.m21(), quadric.m22()
		).scale(-1.0).invert();
		// @formatter:on
		final Vector3d translation = new Vector3d(quadric.m03(), quadric.m13(),
			quadric.m23());
		return sub.transform(translation);
	}

	private static EigenDecomposition solveEigenDecomposition(
		final Matrix4dc quadric)
	{
		// TODO Figure out how to solve eigen decomposition with ojAlgo!
		// @formatter:off
		final RealMatrix input = new Array2DRowRealMatrix(new double[][]{
				{ quadric.m00(), quadric.m01(), quadric.m02() },
				{ quadric.m10(), quadric.m11(), quadric.m12() },
				{ quadric.m20(), quadric.m21(), quadric.m22() }
		}).scalarMultiply(-1.0 / quadric.m33());
		// @formatter:on
		return new EigenDecomposition(input);
	}

	private static Matrix3dc toOrientationMatrix(
		final EigenDecomposition decomposition)
	{
		final List<Vector3d> vectors = IntStream.range(0, 3).mapToObj(
			decomposition::getEigenvector).map(e -> new Vector3d(e.getEntry(0), e
				.getEntry(1), e.getEntry(2))).collect(Collectors.toList());
		return new Matrix3d(vectors.get(0), vectors.get(1), vectors.get(2));
	}

	/**
	 * Translates the quadratic surface to the center point.
	 *
	 * @param quadric the general equation of a quadric in algebraic matrix form.
	 * @param center the center point of the surface.
	 */
	private static Matrix4d translateToCenter(final Matrix4dc quadric,
		final Vector3dc center)
	{
		//@formatter:off
		final Matrix4d t = new Matrix4d(
				1, 0, 0, center.x(),
				0, 1, 0, center.y(),
				0, 0, 1, center.z(),
				0, 0, 0, 1
		);
		//@formatter:on
		final Matrix4d tT = t.transpose(new Matrix4d());
		t.mul(quadric);
		t.mul(tT);
		return t;
	}
}
