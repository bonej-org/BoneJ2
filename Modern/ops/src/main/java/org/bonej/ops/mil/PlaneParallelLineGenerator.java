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

import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imglib2.Interval;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.joml.Quaterniondc;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import static org.apache.commons.math3.util.MathArrays.shuffle;

/**
 * A class that generates random lines that pass through a plane in 3D.
 *
 * @author Richard Domander
 */
public class PlaneParallelLineGenerator implements ParallelLineGenerator {
	private final double size;
	private final Vector3dc planeOrigin;
	private final Vector3dc centroid;
	private final Vector3dc normal;
	private final RandomGenerator random = new MersenneTwister();
	private final Quaterniondc rotation;
	private final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;
	private final long sectionsPerDimension;
	private final double sectionSize;
	private double xSectionOffset;
	private double ySectionOffset;
	private final int[] sectionOrder;
	private int sectionCycle;

	/**
	 * Creates a generator whose lines cover the given interval.
	 * <p>
	 * NB: the lines generated may not intersect the interval.
	 * </p>
	 * <p>
	 * Creates d * d plane, where d = the longest diagonal of a 3D interval. This way the plane is
	 * large enough to cover the entire interval regardless of its orientation. Conversely,
	 * depending on the orientation the plane may extend beyond the interval.
	 * </p>
	 * <p>
	 * The plane is centered on the centroid of the interval.
	 * </p>
	 * @param interval a 3D interval through which the lines pass.
	 * @param rotation rotation of the plane.
	 * @param rotateOp an op the generator needs for rotating vectors
	 * @param sectionsPerDimension number of plane sections line generation cycles through
	 * @throws IllegalArgumentException if sectionsPerDimension is not positive, or interval is not 3D.
	 */
	public static PlaneParallelLineGenerator createFromInterval(final Interval interval,
																final Quaterniondc rotation,
																final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp,
																final long sectionsPerDimension)
			throws IllegalArgumentException {
		validateParameters(interval, sectionsPerDimension);
		final double size = calculateLongestDiagonal(interval);
		final Vector3dc centroid = findCentroid(interval);
		return new PlaneParallelLineGenerator(size, centroid, rotation, rotateOp, sectionsPerDimension);
	}

	private static void validateParameters(final Interval interval, long sectionsPerDimension)
			throws IllegalArgumentException {
		if (interval.numDimensions() != 3) {
			throw new IllegalArgumentException("Interval must be 3D");
		}
		if (sectionsPerDimension < 1) {
			throw new IllegalArgumentException("Sections must be positive");
		}
	}

	private static double calculateLongestDiagonal(final Interval interval) {
		final long width = interval.dimension(0);
		final long height = interval.dimension(1);
		final long depth = interval.dimension(2);
		final long sqSum = width*width + height*height + depth*depth;
		return Math.sqrt(sqSum);
	}

	private static Vector3dc findCentroid(final Interval interval) {
		final double centerX = findMidPoint(interval, 0);
		final double centerY = findMidPoint(interval, 1);
		final double centerZ = findMidPoint(interval, 2);
		return new Vector3d(centerX, centerY, centerZ);
	}

	private static double findMidPoint(final Interval interval, final int d) {
		return interval.dimension(d) * 0.5 + interval.min(d);
	}

	private PlaneParallelLineGenerator(final double size, final Vector3dc centroid,
									  final Quaterniondc rotation,
									  final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp,
									  final long sectionsPerDimension) {
		this.size = size;
		planeOrigin = new Vector3d(-size * 0.5, -size * 0.5, 0.0);
		this.centroid = centroid;
		this.rotateOp = rotateOp;
		this.rotation = rotation;
		this.normal = calculateNormal();
		this.sectionsPerDimension = sectionsPerDimension;
		sectionSize =  1.0 / sectionsPerDimension;
		sectionOrder = createSectionOrder();
		resetSectionCycle();
	}

	private Vector3dc calculateNormal() {
		final Vector3d normal = new Vector3d(0, 0, 1);
		rotateOp.mutate1(normal, rotation);
		return normal;
	}

	private int[] createSectionOrder() {
		final int length = (int) (sectionsPerDimension * sectionsPerDimension);
		final int[] order = new int[length];
		fillZeroToLengthMinusOne(order);
		return order;
	}

	private static void fillZeroToLengthMinusOne(final int[] array) {
		for (int i = 0; i < array.length; i++) {
			array[i] = i;
		}
	}

	private void resetSectionCycle() {
		sectionCycle = 0;
		shuffle(sectionOrder, random);
		xSectionOffset = random.nextDouble() * sectionSize;
		ySectionOffset = random.nextDouble() * sectionSize;
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
	 * The lines from different sections are evenly spaced.
	 * </p>
	 * @return a line that passes through a point on the instance's plane and is normal to it.
	 */
	@Override
	public Line nextLine() {
		final Vector3dc point = nextSectionPoint();
		return new Line(point, normal);
	}

	private Vector3dc nextSectionPoint() {
		final Vector2dc coordinates = nextSectionCoordinates();
		final Vector3d sectionPoint = new Vector3d(coordinates.x(), coordinates.y(), 0);
		return applyPlaneTransformation(sectionPoint);
	}

	private Vector2dc nextSectionCoordinates() {
		final long ySection = sectionOrder[sectionCycle] / sectionsPerDimension;
		final long xSection = sectionOrder[sectionCycle] - ySection * sectionsPerDimension;
		final double x = xSection * sectionSize + xSectionOffset;
		final double y = ySection * sectionSize + ySectionOffset;
		updateSectionCycle();
		return new Vector2d(x, y);
	}

	private void updateSectionCycle() {
		sectionCycle++;
		if (sectionCycle >= sectionOrder.length) {
			resetSectionCycle();
		}
	}

	private Vector3dc applyPlaneTransformation(final Vector3d point) {
		point.mul(size);
		point.add(planeOrigin);
		rotateOp.mutate1(point, rotation);
		point.add(centroid);
		return point;
	}

	/**
	 * Returns the direction of the lines this generator creates
	 *
	 * @return direction as a unit vector
	 */
	@Override
	public Vector3dc getDirection() {
		return normal;
	}

	/**
	 * Resets the generator, and starts generating lines with the given seed
	 *
	 * @param seed seed value
	 * @see RandomGenerator#setSeed(long)
	 */
	public void resetAndSetSeed(final long seed) {
		random.setSeed(seed);
		fillZeroToLengthMinusOne(sectionOrder);
		resetSectionCycle();
	}
}
