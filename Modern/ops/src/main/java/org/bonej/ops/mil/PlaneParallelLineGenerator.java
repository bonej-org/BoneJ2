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

import java.util.stream.IntStream;
import java.util.stream.LongStream;

import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imglib2.Interval;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import static org.apache.commons.math3.util.MathArrays.shuffle;

/**
 * A class that generates random lines that pass through a plane.
 * <p>
 * All the lines pass through a point on a d * d plane, where d = the longest diagonal of a 3D interval.
 * The lines are also normal to the plane.
 * </p>
 * <p>
 * The plane is d * d so that it's big enough that normal lines passing through it cover the entire interval,
 * regardless of the orientation of the plane. However, this means that nextLine() might return a line
 * that entirely misses the interval.
 * </p>
 * @author Richard Domander
 */
public class PlaneParallelLineGenerator implements ParallelLineGenerator {

	private final double size;
	private final Vector3dc translation;
	private final Vector3dc centroid;
	private final Vector3dc direction;
	private final RandomGenerator random = new MersenneTwister();
	private final Quaterniondc rotation;
	private final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;
	private final long sections;
	private final double sectionSize;
	private double uOffset = 0.0;
	private double tOffset = 0.0;
	private final int[] order;
	private int cycle;

	/**
	 * Creates and initializes an instance for generating lines.
	 *
	 * @param interval a 3D interval through which the lines pass.
	 * @param direction the direction of the lines through the interval described as a rotation.
	 * @param rotateOp an op the generator needs for rotating vectors
	 * @param sections number of sections each line point coordinate is generated from.
	 * @param <I> type of the interval.
	 * @throws IllegalArgumentException if sections is not positive, or interval is not 3D.
	 */
	public <I extends Interval> PlaneParallelLineGenerator(final I interval, final Quaterniondc direction,
														   final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp,
														   final long sections) throws IllegalArgumentException
	{
		if (sections < 1) {
			throw new IllegalArgumentException("Sections must be positive");
		}

		if (interval.numDimensions() != 3) {
			throw new IllegalArgumentException("Interval must be 3D");
		}

		size = findPlaneSize(interval);
		translation = new Vector3d(-size * 0.5, -size * 0.5, 0.0);
		centroid = findCentroid(interval);
		this.rotateOp = rotateOp;
		this.rotation = direction;
		this.direction = createDirection();
		this.sections = sections;
		sectionSize =  1.0 / sections;
		final int sectionsSq = (int) (sections * sections);

		order = new int[sectionsSq];
		initOrder();
	}

	/**
	 * Generates the next random line.
	 * <p>
	 * If the class is initialised with more than one section, then lines pass the plane cyclically
	 * through each section. For example, if sections == 2 then the next four lines come from the four
	 * different quadrants of the plane. Calling nextLine() for the fifth time resets the cycle.
	 * Each line has the same random offset within its section. This offset is randomized when the
	 * cycle resets. The order of the quadrants is randomised as well.
	 * </p>
	 * <p>
	 * NB: the line might miss the interval the class was initialised with!
	 * </p>
	 * @return a line passing through a point on a plane described by the class.
	 */
	@Override
	public Line nextLine() {
        if (cycle == 0) {
			shuffle(order, random);
			uOffset = random.nextDouble() * sectionSize;
			tOffset = random.nextDouble() * sectionSize;
		}

		final long uSection = order[cycle] / sections;
		final long tSection = order[cycle] - uSection * sections;
		final double u = uSection * sectionSize + uOffset;
		final double t = tSection * sectionSize + tOffset;

		final Vector3dc point = createOrigin(t, u);

		cycle++;
		if (cycle >= order.length) {
			cycle = 0;
		}

		return new Line(point, direction);
	}

	/**
	 * Returns the direction of the lines this generator creates
	 *
	 * @return direction as a unit vector
	 */
	@Override
	public Vector3dc getDirection() {
		return direction;
	}

	/**
	 * Sets the seed of the underlying random number generator
	 *
	 * @param seed seed value
	 * @see RandomGenerator#setSeed(long)
	 */
	public void setSeed(final long seed) { random.setSeed(seed); }

	/**
	 * Resets the cycle of the line generation
	 *
	 * @see #nextLine()
	 */
	public void reset() {
		cycle = 0;
		// Order needs to be reset to initial permutation {1, 2, 3 ... n},
		// so that reset(); setSeed(seed); always produces the same sequence.
		initOrder();
	}

	// region -- Helper methods --
	private Vector3dc createDirection() {
		final Vector3d direction = new Vector3d(0, 0, 1);
		rotateOp.mutate1(direction, rotation);
		return direction;
	}

	private Vector3dc createOrigin(final double t, final double u) {
		final double x = t * size;
		final double y = u * size;
		final Vector3d origin = new Vector3d(x, y, 0);
		origin.add(translation);
		rotateOp.mutate1(origin, rotation);
		origin.add(centroid);
		return origin;
	}

	private static <I extends Interval> Vector3dc findCentroid(final I interval) {
		final double[] coordinates = IntStream.range(0, 3).mapToDouble(d -> interval
			.max(d) + 1 - interval.min(d)).map(d -> d / 2.0).toArray();
		return new Vector3d(coordinates[0], coordinates[1], coordinates[2]);
	}

	private static <I extends Interval> double findPlaneSize(final I interval) {
		final long sqSum = LongStream.of(interval.dimension(0), interval.dimension(
			1), interval.dimension(2)).map(x -> x * x).sum();
		return Math.sqrt(sqSum);
	}

	private void initOrder() {
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
	}
	// endregion
}
