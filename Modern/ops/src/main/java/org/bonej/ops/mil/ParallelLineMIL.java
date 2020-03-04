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

package org.bonej.ops.mil;

import java.util.Arrays;
import java.util.Random;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.util.ValuePair;

import org.joml.Intersectiond;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static org.bonej.ops.mil.ParallelLineGenerator.Line;

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
public class ParallelLineMIL<B extends BooleanType<B>> extends
	AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, ParallelLineGenerator, Vector3d>
	implements Contingent
{
	/**
	 * Length of the MIL vector sampled.
	 * <p>
	 * The op generates and samples random lines through the interval until their total length
	 * reaches the given value.
	 * </p>
	 * <p>
	 * If left null, the length is 100.0 * d, where d = the size of the biggest dimension of the interval.
	 * </p>
	 */
	@Parameter(required = false, persist = false)
	private Double milLength;
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

	private final Random random = new Random();

	/**
	 * Calculates the MIL vector of the interval.
	 *
	 * @param interval a 3D interval.
	 * @param parallelLineGenerator a generator of random lines for MIL sampling.
	 * @return a vector <b>v</b> parallel to the MIL lines, whose magnitude
	 *         ||<b>v</b>|| = total length of lines / total phase changes from
	 *         background to foreground
	 */
	@Override
	public Vector3d calculate(final RandomAccessibleInterval<B> interval,
		final ParallelLineGenerator parallelLineGenerator)
	{
		if (seed != null) {
			random.setSeed(seed);
		}
		if (increment == null) {
			increment = 1.0;
		}
		if (milLength == null) {
			final long maxDim = getMaxDim();
			milLength = 100.0 * maxDim;
		}
		double totalLength = 0.0;
		long totalIntercepts = 0L;
		while (totalLength < milLength) {
			final Line line = parallelLineGenerator.nextLine();
			Segment segment = intersectInterval(line, interval);
			if (segment == null) {
				continue;
			}
			final double length = Math.abs(segment.tMax - segment.tMin);
			if (totalLength + length > milLength) {
				segment = limitSegment(milLength, totalLength, segment);
			}
			final ValuePair<Double, Long> mILValues = mILValues(interval, segment, increment);
			if (mILValues == null) {
				continue;
			}
			totalLength += mILValues.getA();
			totalIntercepts += mILValues.getB();
		}
		totalIntercepts = Math.max(totalIntercepts, 1);
		final Vector3dc direction = parallelLineGenerator.getDirection();
		return new Vector3d(direction).mul(totalLength / totalIntercepts);
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
			if (current != previous) {
				phaseChanges++;
			}
			previous = current;
			start.add(gap);
		}
		return phaseChanges;
	}

	private static <B extends BooleanType<B>> boolean getVoxel(
		final RandomAccess<B> access, final Vector3d v)
	{
		access.setPosition((long) v.x, 0);
		access.setPosition((long) v.y, 1);
		access.setPosition((long) v.z, 2);
		return access.get().get();
	}

	private static Segment intersectInterval(final Line line, final Interval interval)
	{
		final Vector3dc min = new Vector3d(interval.min(0), interval.min(1),
			interval.min(2));
		final Vector3dc max = new Vector3d(interval.max(0) + 1, interval.max(1) + 1,
			interval.max(2) + 1);
		final Vector2d tValues = new Vector2d();
		final boolean intersect = Intersectiond.intersectRayAab(line.point, line.direction, min, max, tValues);
		if (!intersect) {
			return null;
		}
		return new Segment(line, tValues.x, tValues.y);
	}

	private static Segment limitSegment(final double goalLength, final double totalLength,
										final Segment segment)
	{
		final double remaining = goalLength - totalLength;
		final double tMax;
		if (segment.tMax < segment.tMin) {
			tMax = segment.tMin - remaining;
		} else {
			tMax = segment.tMin + remaining;
		}
		return new Segment(segment.line, segment.tMin, tMax);
	}

	private ValuePair<Double, Long> mILValues(final RandomAccessible<B> interval,
											  final Segment segment, final double increment)
	{
		final long intercepts = sampleSegment(interval, segment, segment.line.direction,
			increment);
		if (intercepts < 0) {
			return null;
		}
		final double length = Math.abs(segment.tMax - segment.tMin);
		return new ValuePair<>(length, intercepts);
	}

	private long getMaxDim() {
		final RandomAccessibleInterval<B> interval = in();
		final long[] dimensions = new long[interval.numDimensions()];
		interval.dimensions(dimensions);
		return Arrays.stream(dimensions).max().orElse(0L);
	}

	private long sampleSegment(final RandomAccessible<B> interval,
							   final Segment segment, final Vector3dc direction, final double increment)
	{
		// Add a random offset so that sampling doesn't always start from where the segment
		// enters the interval
		final double startT = segment.tMin + random.nextDouble() * increment;
		final long samples = (long) Math.ceil((segment.tMax - startT) / increment);
		if (samples < 1) {
			return -1;
		}
		final Vector3d samplePoint = new Vector3d(direction);
		samplePoint.mul(startT);
		samplePoint.add(segment.line.point);
		final Vector3d gap = new Vector3d(direction);
		gap.mul(increment);
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
	 * The direction comes from the {@link PlaneParallelLineGenerator} used in the op.
	 * </p>
	 */
	private static final class Segment {

		private final double tMin;
		private final double tMax;
		private final Line line;

		private Segment(final Line line, final double tMin, final double tMax) {
			this.line = line;
			this.tMin = tMin;
			this.tMax = tMax;
		}
	}

	// endregion
}
