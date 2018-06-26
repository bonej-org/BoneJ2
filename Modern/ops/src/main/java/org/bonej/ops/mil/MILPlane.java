
package org.bonej.ops.mil;

import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;

import org.joml.Intersectiond;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * An op that finds the mean intercept length (MIL) vector of an interval.
 * <p>
 * A MIL vector is defined as <em>MIL(<b>v</b>) = C(<b>v</b>) / h</em>, where
 * <em><b>v</b></em> is a set of parallel lines, <em>C(<b>v</b>)</em> is the
 * number of times <em><b>v</b></em> intercept foreground, and <em>h</em> is the
 * sum of lengths of <em><b>v</b></em>. The lines <em><b>v</b></em> are traced
 * through the interval. A line intercepts foreground, when the previous point
 * sampled along it is background, and the current one is foreground.
 * </p>
 * <p>
 * For example MIL vectors can be used to estimate the anisotropy of the
 * "texture" in an image. It's best suited for images that are completely
 * filled, and a part of a larger whole, e.g. a volume of trabecular bone.
 * </p>
 * <p>
 * For more details, see:
 * </p>
 * <ul>
 * <li>Moreno et al., <a href=
 * "http://liu.diva-portal.org/smash/get/diva2:533443/FULLTEXT01.pdf">On the
 * Efficiency of the Mean Intercept Length Tensor</a></li>
 * <li>Odgaard A (1997) Three-dimensional methods for quantification of
 * cancellous bone architecture. Bone 20: 315-28. <a href=
 * "http://dx.doi.org/10.1016/S8756-3282(97)00007-0">doi:10.1016/S8756-3282(97)00007-0</a>.</li>
 * <li>Harrigan TP, Mann RW (1984) Characterization of microstructural
 * anisotropy in orthotropic materials using a second rank tensor. J Mater Sci
 * 19: 761-767. <a href=
 * "http://dx.doi.org/10.1007/BF00540446">doi:10.1007/BF00540446</a>.</li>
 * </ul>
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class MILPlane<B extends BooleanType<B>> extends
	AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, Quaterniondc, Vector3d>
	implements Contingent
{

	private static BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;
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
	 * Given that all other parameters all the same (including the interval and
	 * rotation), then using the same seed will give the same results.
	 * </p>
	 * <p>
	 * If left null, a new generator is created with the default constructor.
	 * </p>
	 */
	@Parameter(required = false, persist = false)
	private Long seed;

	/**
	 * Calculates the MIL vector of the interval.
	 *
	 * @param interval a 3D interval.
	 * @param rotation direction of the MIL lines in the interval.
	 * @return a vector <b>v</b> parallel to the MIL lines, whose magnitude
	 *         ||<b>v</b>|| = total length of lines / total phase changes from
	 *         background to foreground
	 */
	@Override
	public Vector3d calculate(final RandomAccessibleInterval<B> interval,
		final Quaterniondc rotation)
	{
		matchOps();
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
		return in().numDimensions() >= 3;
	}

	// region -- Helper methods --
	private static <B extends BooleanType<B>> long countPhaseChanges(
		final RandomAccessible<B> interval, final Vector3d start,
		final Vector3dc gap, final long samples)
	{
		final RandomAccess<B> access = interval.randomAccess();
		boolean previous = false;
		long phaseChanges = 0;
		for (long i = 0; i < samples; i++) {
			final boolean current = getVoxel(access, start);
			if (current && !previous) {
				phaseChanges++;
			}
			previous = current;
			start.add(gap);
		}
		return phaseChanges;
	}

	private long defaultBins(final RandomAccessibleInterval<B> interval) {
		final long sqSum = IntStream.range(0, 3).mapToLong(interval::dimension).map(
			d -> d * d).sum();
		return (long) Math.sqrt(sqSum);
	}

	private Stream<Section> findIntersectingSections(final LinePlane plane,
		final Interval interval)
	{
		final Vector3d direction = plane.getDirection();
		return plane.getOrigins(bins).map(origin -> intersectInterval(origin,
			direction, interval)).filter(Objects::nonNull);
	}

	private static <B extends BooleanType<B>> boolean getVoxel(
		final RandomAccess<B> access, final Vector3d v)
	{
		access.setPosition((long) v.x, 0);
		access.setPosition((long) v.y, 1);
		access.setPosition((long) v.z, 2);
		return access.get().get();
	}

	private static Section intersectInterval(final Vector3d origin,
		final Vector3d direction, final Interval interval)
	{
		final Vector2d result = new Vector2d();
		final Vector3dc o = new Vector3d(origin.x, origin.y, origin.z);
		final Vector3dc d = new Vector3d(direction.x, direction.y, direction.z);
		final Vector3dc min = new Vector3d(interval.min(0), interval.min(1),
			interval.min(2));
		final Vector3dc max = new Vector3d(interval.max(0) + 1, interval.max(1) + 1,
			interval.max(2) + 1);
		final boolean intersect = Intersectiond.intersectRayAab(o, d, min, max,
			result);
		if (!intersect) {
			return null;
		}
		return new Section(origin, result.x, result.y);
	}

	private ValuePair<Double, Long> mILValues(final RandomAccessible<B> interval,
		final Section section, final Vector3d direction, final double increment)
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
	private void matchOps() {
		rotateOp = Hybrids.binaryCFI1(ops(), Rotate3d.class, Vector3d.class,
			new Vector3d(), new Quaterniond());
	}

	private Vector3d sampleMILVector(final RandomAccessible<B> interval,
		final Stream<Section> sections, final Vector3d direction)
	{
		final DoubleType totalLength = new DoubleType();
		final LongType totalIntercepts = new LongType();
		sections.map(s -> mILValues(interval, s, direction, increment)).filter(
			Objects::nonNull).forEach(p -> {
				totalLength.set(p.a + totalLength.get());
				totalIntercepts.set(p.b + totalIntercepts.get());
			});
		totalIntercepts.set(Math.max(totalIntercepts.get(), 1));
		final Vector3d milVector = new Vector3d(direction);
		return milVector.mul(totalLength.get() / totalIntercepts.get());
	}

	private long sampleSection(final RandomAccessible<B> interval,
		final Section section, final Vector3d direction, final double increment)
	{
		// Add a random offset so that sampling doesn't always start from the same
		// plane
		final double startT = section.tMin + random.nextDouble() * increment;
		final Vector3d samplePoint = new Vector3d(direction);
		samplePoint.mul(startT);
		samplePoint.add(section.origin);
		final Vector3d gap = new Vector3d(direction);
		gap.mul(increment);
		final long samples = (long) Math.ceil((section.tMax - startT) / increment);
		if (samples < 1) {
			return -1;
		}
		return countPhaseChanges(interval, samplePoint, gap, samples);
	}
	// endregion

	// region -- Helper classes --
	/**
	 * Stores the origin <b>a</b> of a line, and the scalars for <b>a</b> +
	 * <em>t<sub>1</sub></em><b>v</b> and <b>a</b> +
	 * <em>t<sub>2</sub></em><b>v</b> where the origin + direction <b>v</b>
	 * intersects the input interval.
	 * <p>
	 * The direction comes from the {@link LinePlane} used in the op.
	 * </p>
	 */
	private static final class Section {

		private final double tMin;
		private final double tMax;
		private final Vector3d origin;

		private Section(final Vector3d origin, final double tMin,
			final double tMax)
		{
			this.tMin = tMin;
			this.tMax = tMax;
			this.origin = origin;
		}
	}

	// endregion
}
