/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.generate;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.joml.Vector3d;
import org.scijava.plugin.Plugin;

/**
 * Generates isotropically located random points on an ellipsoid surface.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class EllipsoidPoints extends
	AbstractBinaryFunctionOp<double[], Long, List<Vector3d>> implements Contingent
{

	private static final Random rng = new Random();
	private static UnitSphereRandomVectorGenerator sphereRng =
		new UnitSphereRandomVectorGenerator(3);
	/** Smallest radius of the ellipsoid (x) */
	private double a;
	/** Second radius of the ellipsoid (y) */
	private double b;
	/** Largest radius of the ellipsoid (z) */
	private double c;

	/**
	 * Creates random points on an ellipsoid surface.
	 *
	 * @param radii the radii of the ellipsoid. They'll be sorted in ascending
	 *          order.
	 * @param n number of points to be created.
	 * @return ellipsoid points.
	 */
	@Override
	public List<Vector3d> calculate(final double[] radii, final Long n) {
		Arrays.sort(radii);
		a = radii[0];
		b = radii[1];
		c = radii[2];
		return sampleEllipsoidPoints(n);
	}

	@Override
	public boolean conforms() {
		final double[] radii = in1();
		final Long n = in2();
		return n >= 0 && radii.length == 3 && Arrays.stream(radii).allMatch(
			r -> r > 0 && Double.isFinite(r));
	}

	/**
	 * Calculates the &mu;-factor of a point.
	 *
	 * @param v a point on a unit sphere surface.
	 * @return inverse ratio of ellipsoid surface area around given point.
	 */
	private double mu(final Vector3d v) {
		final DoubleStream terms = DoubleStream.of(a * c * v.y, a * b * v.z, b * c *
			v.x);
		final double sqSum = terms.map(x -> x * x).sum();
		return Math.sqrt(sqSum);
	}

	private List<Vector3d> sampleEllipsoidPoints(final long n) {
		final Supplier<Vector3d> spherePoint = () -> {
			final double[] v = sphereRng.nextVector();
			return new Vector3d(v[0], v[1], v[2]);
		};
		// Probability function to keep a sphere point
		final double muMax = b * c;
		final Predicate<Vector3d> p = v -> rng.nextDouble() <= mu(v) / muMax;
		// Mapping function from sphere to ellipsoid
		final Function<Vector3d, Vector3d> toEllipsoid = v -> new Vector3d(a * v.x,
			b * v.y, c * v.z);
		return generate(spherePoint).filter(p).limit(n).map(toEllipsoid).collect(
			toList());
	}

	/**
	 * Sets the seed of the random generators used in point creation.
	 * <p>
	 * Setting a constant seed makes testing easier.
	 * </p>
	 *
	 * @param seed the seed number.
	 * @see Random#setSeed(long)
	 * @see MersenneTwister#MersenneTwister(long)
	 */
	static void setSeed(final long seed) {
		rng.setSeed(seed);
		sphereRng = new UnitSphereRandomVectorGenerator(3, new MersenneTwister(
			seed));
	}
}
