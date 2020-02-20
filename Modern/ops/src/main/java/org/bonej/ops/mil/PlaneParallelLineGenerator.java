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

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imglib2.Interval;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A class that generates random lines that pass through a plane.
 * <p>
 * All the lines pass through a point on a d * d plane, where d = the longest diagonal of an interval.
 * The lines are also normal to the plane.
 * </p>
 * <p>
 * The plane is d * d so that it's big enough that lines passing through it cover the entire interval,
 * regardless of the direction of the lines. However, this means that nextLine() might return a line
 * that entirely misses the interval.
 * </p>
 * @author Richard Domander
 */
public class PlaneParallelLineGenerator implements ParallelLineGenerator {

	private final double size;
	private final Vector3dc translation;
	private final Vector3dc centroid;
	private final Vector3dc direction;
	private final Random random = new Random();
	private final Quaterniondc rotation;
	private final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;
	private final long sections;
	private long uSection = 0;
	private long tSection = 0;
	private double uOffset = 0.0;
	private double tOffset = 0.0;

	/**
	 * Creates and initializes an instance for generating lines.
	 *
	 * @param interval an interval through which the lines pass.
	 * @param direction the direction of the lines through the interval described as a rotation.
	 * @param rotateOp an op the generator needs for rotating vectors
	 * @param sections number of sections each line point coordinate is generated from.
	 * @param <I> type of the interval.
	 */
	public <I extends Interval> PlaneParallelLineGenerator(final I interval, final Quaterniondc direction,
														   final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp,
														   final long sections)
	{
		size = findPlaneSize(interval);
		translation = new Vector3d(-size * 0.5, -size * 0.5, 0.0);
		centroid = findCentroid(interval);
		this.rotateOp = rotateOp;
		this.rotation = direction;
		this.direction = createDirection();
		this.sections = sections;
	}


	/**
	 * Generates the next random line.
	 * <p>
	 * If the class is initialised with more than one section, then lines pass the plane cyclically
	 * through each section. For example, if sections == 2 then first line generated passes through a random
	 * point on the top left quarter of the plane. The fourth line passes through a point on the bottom right quarter.
	 * For the fifth one the cycle resets, and it's top left again. The randomisation happens once
	 * per cycle, so the four points have consistent distance between each other.
	 * </p>
	 * <p>
	 * NB: the line might miss the interval the class was initialised with!
	 * </p>
	 * @return a line passing through a point on a plane described by the class.
	 */
	@Override
	public Line nextLine() {
		final double sectionSize = 1.0 / sections;
		if (uSection == 0 && tSection == 0) {
			uOffset = random.nextDouble() * sectionSize;
			tOffset = random.nextDouble() * sectionSize;
		}
		final double u = uSection * sectionSize + uOffset;
		final double t = tSection * sectionSize + tOffset;
		tSection++;
		if (tSection >= sections) {
			tSection = 0;
			uSection++;
		}
		if (uSection >= sections) {
			uSection = 0;
			tSection = 0;
		}
		final Vector3dc point = createOrigin(t, u);
		return new Line(point, direction);
	}

	@Override
	public Vector3dc getDirection() {
		return direction;
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
	// endregion
}
