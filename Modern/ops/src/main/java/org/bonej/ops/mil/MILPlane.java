
package org.bonej.ops.mil;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;

import org.bonej.ops.BoxIntersect;
import org.bonej.ops.RotateAboutAxis;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class MILPlane<B extends BooleanType<B>> extends
	AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, AxisAngle4d, Vector3d>
	implements Contingent
{

	private static BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp;
	private static BinaryFunctionOp<ValuePair<Point3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>> intersectOp;
	private final Random random = new Random();
	/**
	 * Number of sampling lines generated per dimension.
	 * <p>
	 * Each bin represents a sub-region of the plane where a sampling line
	 * originates. For example, if bins = 2 then we get four lines originating
	 * from the four quadrants of the plane.
	 * </p>
	 * <p>
	 * If left null, bins is set to <em>d</em>, where <em>d</em> is the largest
	 * diagonal of the input interval.
	 * </p>
	 * <p>
	 * The higher the number, the more likely you get an accurate result.
	 * </p>
	 */
	@Parameter(required = false, persist = false)
	private Long bins;
	/**
	 * The scalar step between positions on a sampling line where voxels are read.
	 * <p>
	 * For example, an increment of 1.0 adds a vector of length 1.0 to the
	 * position.
	 * </p>
	 * <p>
	 * If left null, the increment is set to 1.0.
	 * </p>
	 * <p>
	 * The higher the number, the more likely you get an accurate result.
	 * </p>
	 */
	@Parameter(required = false, persist = false)
	private Double increment;
	/**
	 * The seed used in the random generator in the algorithm.
	 * <p>
	 * If left null, a new generator is created with the default constructor.
	 * </p>
	 */
	@Parameter(required = false, persist = false)
	private Long seed;

	@Override
	public Vector3d calculate(final RandomAccessibleInterval<B> interval,
		final AxisAngle4d rotation)
	{
		matchOps(interval);
		final LinePlane samplingPlane = new LinePlane(interval, rotation, rotateOp);
		if (seed != null) {
			random.setSeed(seed);
			samplingPlane.setSeed(seed);
		}
		if (increment == null) {
			increment = 1.0;
		}
		if (bins == null) {
			bins = defaultBins(interval);
		}
		final Stream<Section> sections = findIntersectingSections(samplingPlane,
			interval);
		return sampleMILVector(interval, sections, samplingPlane.getDirection());
	}

	@Override
	public boolean conforms() {
		// TODO check empty dims?
		return in().numDimensions() >= 3;
	}

	// region -- Helper methods --
	private static <B extends BooleanType<B>> long countInterceptions(
		final RandomAccessibleInterval<B> interval, final Vector3d start,
		final Tuple3d gap, final long samples)
	{
		final RandomAccess<B> access = interval.randomAccess();
		boolean previous = false;
		long intercepts = 0;
		for (long i = 0; i < samples; i++) {
			final boolean current = getVoxel(access, start);
			if (current && !previous) {
				intercepts++;
			}
			previous = current;
			start.add(gap);
		}
		return intercepts;
	}

	private long defaultBins(final RandomAccessibleInterval<B> interval) {
		final long sqSum = IntStream.range(0, 3).mapToLong(interval::dimension).map(
			d -> d * d).sum();
		return (long) Math.sqrt(sqSum);
	}

	private Stream<Section> findIntersectingSections(final LinePlane plane,
		final RandomAccessibleInterval<B> interval)
	{
		final Vector3d direction = plane.getDirection();
		return plane.getOrigins(bins).map(origin -> intersectInterval(origin, direction,
			interval)).filter(Objects::nonNull);
	}

	private static <B extends BooleanType<B>> boolean getVoxel(
		final RandomAccess<B> access, final Vector3d v)
	{
		access.setPosition((long) v.x, 0);
		access.setPosition((long) v.y, 1);
		access.setPosition((long) v.z, 2);
		return access.get().get();
	}

	private static Section intersectInterval(final Point3d origin,
		final Vector3d direction, final Interval interval)
	{
		final ValuePair<Point3d, Vector3d> line = new ValuePair<>(origin,
			direction);
		final Optional<ValuePair<DoubleType, DoubleType>> result = intersectOp
			.calculate(line, interval);
		if (!result.isPresent()) {
			return null;
		}
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		final double tMin = scalars.getA().get();
		final double tMax = scalars.getB().get();
		return new Section(line.a, tMin, tMax);
	}

	private ValuePair<Double, Long> mILValues(
		final RandomAccessibleInterval<B> interval, final Section section,
		final Vector3d direction, final double increment)
	{
		final long intercepts = sampleSection(interval, section, direction,
			increment);
		if (intercepts < 0) {
			return null;
		}
		final double length = Math.abs(section.tMax - section.tMin);
		return new ValuePair<>(length, intercepts);
	}

	@SuppressWarnings("unchecked")
	private void matchOps(final RandomAccessibleInterval<B> interval) {
		rotateOp = Hybrids.binaryCFI1(ops(), RotateAboutAxis.class, Tuple3d.class,
			new Vector3d(), new AxisAngle4d());
		intersectOp = (BinaryFunctionOp) Functions.binary(ops(), BoxIntersect.class,
			Optional.class, ValuePair.class, interval);
	}

	private Vector3d sampleMILVector(final RandomAccessibleInterval<B> interval,
		final Stream<Section> sections, final Vector3d direction)
	{
		final DoubleType totalLength = new DoubleType();
		final LongType totalIntercepts = new LongType();
		sections.map(s -> mILValues(interval, s, direction, increment)).filter(
			Objects::nonNull).forEach(p -> {
				totalLength.set(p.a + totalLength.get());
				totalIntercepts.set(p.b + totalIntercepts.get());
			});
		// TODO throw exception if intercepts 0, or == sections (bad images)
		totalIntercepts.set(Math.max(totalIntercepts.get(), 1));
		final Vector3d milVector = new Vector3d(direction);
		milVector.scale(totalLength.get() / totalIntercepts.get());
		return milVector;
	}

	private long sampleSection(final RandomAccessibleInterval<B> interval,
		final Section section, final Vector3d direction, final double increment)
	{
		// Add a random offset so that sampling doesn't always start from the same
		// plane
		final double startT = section.tMin + random.nextDouble() * increment;
		final Vector3d samplePoint = new Vector3d(direction);
		samplePoint.scale(startT);
		samplePoint.add(section.origin);
		final Vector3d gap = new Vector3d(direction);
		gap.scale(increment);
		final long samples = (long) Math.ceil((section.tMax - startT) / increment);
		if (samples < 1) {
			return -1;
		}
		return countInterceptions(interval, samplePoint, gap, samples);
	}
	// endregion

	/**
	 * Stores the origin <b>a</b> of a line, and the scalars for <b>a</b> +
	 * <em>t<sub>1</sub></em><b>v</b> and <b>a</b> +
	 * <em>t<sub>2</sub></em><b>v</b> where the origin + direction <b>v</b>
	 * intersects the input interval.
	 */
	private static class Section {

		private final double tMin;
		private final double tMax;
		private final Point3d origin;

		private Section(final Point3d origin, final double tMin,
			final double tMax)
		{
			this.tMin = tMin;
			this.tMax = tMax;
			this.origin = origin;
		}
	}

	// endregion
}
