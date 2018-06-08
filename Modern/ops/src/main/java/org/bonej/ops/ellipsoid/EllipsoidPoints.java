
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
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Vector3d;

/**
 * Generates isotropically located random points on an ellipsoid surface.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class EllipsoidPoints extends
	AbstractBinaryFunctionOp<double[], Long, List<Vector3d>> implements
	Contingent
{

	/** Smallest radius of the ellipsoid (x) */
	private double a;
	/** Second radius of the ellipsoid (y) */
	private double b;
	/** Largest radius of the ellipsoid (z) */
	private double c;
	private static final Random rng = new Random();
	private static UnitSphereRandomVectorGenerator sphereRng =
		new UnitSphereRandomVectorGenerator(3);

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

	private List<Vector3d> sampleEllipsoidPoints(final long n) {
		final Supplier<Vector3d> spherePoint = () -> new Vector3d(sphereRng
			.nextVector());
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

	@Override
	public boolean conforms() {
		final double[] radii = in1();
		final Long n = in2();
		return n >= 0 && radii.length == 3 && Arrays.stream(radii).allMatch(
			r -> r > 0 && Double.isFinite(r));
	}
}
