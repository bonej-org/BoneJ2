
package org.bonej.ops.mil;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.LongStream;
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
 * Creates a cloud of mean intercept length (MIL) vectors that describes the
 * anisotropy of the interval.
 * <p>
 * First using the {@link LineGrid} class the op draws a grid of lines that
 * intercept the interval. Then for each line it counts how many time it
 * intercepts objects in the interval. The intercepts are found by sampling
 * elements in the interval along the lines. The method is not exact, because it
 * may not sample each element. Each time the previous sample was background and
 * the current is foreground -- or vice-versa -- the intercept count is
 * increment. Thus when a line enters and exits a foreground object it counts as
 * two intercepts.
 * </p>
 * <p>
 * Next each line is mapped to a unit vector centered on the origin. The vector
 * has the same direction as the line. The vectors are then divided by the
 * number of intercepts. Lines that sample the interval less than two times are
 * ignored. These vectors form the mean intercept length point cloud. In a
 * completely isotropic interval all of them will be unit vectors.
 * </p>
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class MILGrid<B extends BooleanType<B>> extends
	AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, AxisAngle4d, List<Vector3d>>
	implements Contingent
{

	private static final Function<RandomAccessibleInterval, LongStream> dimStream =
		rai -> LongStream.of(rai.dimension(0), rai.dimension(1), rai.dimension(2));
	/**
	 * Number lines drawn from the planes of the grid. For example, if
	 * linesPerDimension = 2 then 2 * 2 lines are drawn from each plane - a total
	 * of 2 * 2 * 3 = 12.
	 * <p>
	 * If left null, the parameter is set to the size of the biggest dimension of
	 * the interval.
	 * </p>
	 * <p>
	 * The higher the number, the more likely it is that the MIL vectors returned
	 * are truly the shortest ones.
	 * </p>
	 *
	 * @see LineGrid#lines(long)
	 */
	@Parameter(required = false, persist = false)
	private Long linesPerDimension;
	/**
	 * The scalar step between the sample positions. For example, an increment of
	 * 1.0 adds a vector of length 1.0 to the position. The increment is to the
	 * same direction as the line.
	 * <p>
	 * If left null, the increment is set to 1.0.
	 * </p>
	 * <p>
	 * The higher the number, the more likely it is that all foreground --
	 * background intercepts are found.
	 * </p>
	 */
	@Parameter(required = false, persist = false)
	private Double samplingIncrement;

	// TODO Figure out a better way to test the effect of bins & increment
	/**
	 * The seed used in the Op's random generator.
	 * <p>
	 * If left null, a new generator is created with the default constructor.
	 * </p>
	 */
	@Parameter(required = false, persist = false)
	private Long seed;

	/**
	 * Returns a point cloud of MIL vectors around the origin.
	 *
	 * @param interval an interval with at least three dimensions. The method
	 *          assumes that the first three are X,Y and Z. It ignores others.
	 * @param rotation a rotation applied to the grid.
	 * @return a collection of vectors that show mean intercept lengths in
	 *         different directions. Returns an empty collection if the interval
	 *         is empty.
	 * @throws IllegalArgumentException if {@link MILGrid#samplingIncrement} is
	 *           too small.
	 */
	@Override
	public List<Vector3d> calculate(final RandomAccessibleInterval<B> interval,
		final AxisAngle4d rotation)
	{
		final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp;
		final BinaryFunctionOp<ValuePair<Point3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>> intersectOp;
		final long bins;
		final double increment;
		final Random random;
		synchronized (this) {
			rotateOp = Hybrids.binaryCFI1(ops(), RotateAboutAxis.class, Tuple3d.class,
				new Vector3d(), new AxisAngle4d());
			intersectOp = (BinaryFunctionOp) Functions.binary(ops(),
				BoxIntersect.class, Optional.class, ValuePair.class, interval);
			final long defaultBins = dimStream.apply(interval).max().orElse(0);
			bins = linesPerDimension != null ? linesPerDimension : defaultBins;
			increment = samplingIncrement != null ? samplingIncrement : 1.0;
			if (increment < 1e-4) {
				throw new IllegalArgumentException("Increment too small");
			}
			random = seed != null ? new Random(seed) : new Random();
		}
		final LineGrid grid = createGrid(interval, rotateOp, rotation, random);
		final Stream<Section> sections = grid.lines(bins).map((line) -> findSection(
			line, interval, intersectOp)).filter(Objects::nonNull);
		return sections.map(section -> toMILVector(section, interval, increment,
			random)).filter(Objects::nonNull).collect(toList());
	}

	/**
	 * Finds the MIL vectors of the interval.
	 * <p>
	 * Applies a random rotation to the grid.
	 * </p>
	 *
	 * @param interval an interval with at least three dimensions. The method
	 *          assumes that the first three are X,Y and Z. It ignores others.
	 * @return a cloud of MIL vectors around the origin.
	 * @throws IllegalArgumentException if {@link MILGrid#samplingIncrement} is
	 *           too small.
	 */
	@Override
	public List<Vector3d> calculate(final RandomAccessibleInterval<B> interval)
		throws IllegalArgumentException
	{
		return this.calculate(interval, RotateAboutAxis.randomAxisAngle());
	}

	@Override
	public boolean conforms() {
		return in().numDimensions() >= 3;
	}

	// region -- Helper methods --

	private static LineGrid createGrid(final Interval interval,
		final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp,
		final AxisAngle4d rotation, final Random rng)
	{
		final LineGrid grid = new LineGrid(interval);
		grid.setRandomGenerator(rng);
		grid.setRotation(rotateOp, rotation);
		if (rng.nextBoolean()) {
			grid.randomReflection();
		}
		return grid;
	}

	/**
	 * Maps the line to section in the interval.
	 *
	 * @param line an (origin, direction pair).
	 * @param interval an interval.
	 * @param intersectOp an op for finding the intersections of the line and the
	 *          interval.
	 * @return a {@link Section} in the interval, null if line doesn't intersect
	 *         it.
	 */
	private static Section findSection(final ValuePair<Point3d, Vector3d> line,
		final Interval interval,
		final BinaryFunctionOp<ValuePair<Point3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>> intersectOp)
	{
		final Optional<ValuePair<DoubleType, DoubleType>> result = intersectOp
			.calculate(line, interval);
		if (!result.isPresent()) {
			return null;
		}
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		final double tMin = scalars.getA().get();
		final double tMax = scalars.getB().get();
		return new Section(line.a, line.b, tMin, tMax);
	}

	private static <B extends BooleanType<B>> long sampleIntercepts(
		final RandomAccessibleInterval<B> interval, final Section section,
		final double increment, final Random random)
	{
		// Add a random offset so that sampling doesn't always start from the same
		// plane
		final double startT = section.tMin + random.nextDouble() * increment;
		final Vector3d samplePoint = new Vector3d(section.direction);
		samplePoint.scale(startT);
		samplePoint.add(section.origin);
		final Vector3d gap = new Vector3d(section.direction);
		gap.scale(increment);
		long intercepts = 0;
		final RandomAccess<B> access = interval.randomAccess();
		boolean previous = false;
		final long iterations = (long) Math.ceil((section.tMax - startT) /
			increment);
		for (long i = 0; i < iterations; i++) {
			final boolean current = sampleVoxel(access, samplePoint);
			if (current != previous) {
				intercepts++;
			}
			previous = current;
			samplePoint.add(gap);
		}
		return intercepts;
	}

	private static <B extends BooleanType<B>> boolean sampleVoxel(
		final RandomAccess<B> access, final Vector3d v)
	{
		access.setPosition((long) v.x, 0);
		access.setPosition((long) v.y, 1);
		access.setPosition((long) v.z, 2);
		return access.get().get();
	}

	private static <B extends BooleanType<B>> Vector3d toMILVector(
		final Section section, final RandomAccessibleInterval<B> interval,
		final double increment, final Random random)
	{
		final long intercepts = sampleIntercepts(interval, section, increment,
			random);
		if (intercepts <= 0) {
			return null;
		}
		final Vector3d milVector = new Vector3d(section.direction);
		final double length = section.tMax - section.tMin;
		milVector.scale(length);
		milVector.scale(1.0 / intercepts);
		return milVector;
	}

	/**
	 * Stores the origin <b>a</b> and direction <b>v</b> of a line, and the
	 * scalars for <b>a</b> + <em>t<sub>1</sub></em><b>v</b> and <b>a</b> +
	 * <em>t<sub>2</sub></em><b>v</b> where the line intersects an interval.
	 */
	private static class Section {

		private final double tMin;
		private final double tMax;
		private final Point3d origin;
		private final Vector3d direction;

		private Section(final Point3d origin, final Vector3d direction,
			final double tMin, final double tMax)
		{
			this.tMin = tMin;
			this.tMax = tMax;
			this.origin = new Point3d(origin);
			this.direction = new Vector3d(direction);
		}
	}

	// endregion
}
