/*
 * #%L
 * BoneJ: open source tools for trabecular geometry and whole bone shape analysis.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers. See also individual class @authors.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.bonej;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.doube.geometry.TestDataMaker;
import org.junit.Test;

import ij.ImagePlus;

public class SkeletonAnglesTest {
	private static final ImagePlus crossedCircle = TestDataMaker.crossedCircle(256);
	private static final ImagePlus boxFrame = TestDataMaker.boxFrame(128, 128, 128);
	private static final SkeletonAngles skeletonAngles = new SkeletonAngles();
	private static final double PI_4 = Math.PI / 4;
	private static final double PI_2 = Math.PI / 2;
	/** Not quite pi / 2 because vertex isn't exactly at corner */
	private static final double PI_2_ISH = 1.5904976894727854;
	private static final double DELTA = 1e-12;

	private final static double[][][] circleCrossResult = {
			{{PI_4, PI_4, PI_2}, null, {PI_4, PI_2, PI_4}, {PI_4, PI_2, PI_4}, {PI_4, PI_4, PI_2}}};

	private final static double[][][] circleCrossResultNth = {
			{{PI_2, PI_2, Math.PI}, null, {PI_2, Math.PI, PI_2}, {PI_2, Math.PI, PI_2}, {PI_2, PI_2, Math.PI}}};

	@Test
	public void testCalculateTriplePointAnglesCrossedCircle() {
		final double[][][] result = skeletonAngles.calculateTriplePointAngles(crossedCircle,
				SkeletonAngles.VERTEX_TO_VERTEX);

		for (int g = 0; g < circleCrossResult.length; g++)
			for (int v = 0; v < circleCrossResult[g].length; v++)
				assertArrayEquals("Angle measured incorrectly", circleCrossResult[g][v], result[g][v], DELTA);
	}

	@Test
	public void testCalculateTriplePointAnglesCrossedCircleNth() {
		final double[][][] result = skeletonAngles.calculateTriplePointAngles(crossedCircle, 8);

		for (int g = 0; g < circleCrossResultNth.length; g++)
			for (int v = 0; v < circleCrossResultNth[g].length; v++)
				assertArrayEquals("Angle measured incorrectly", circleCrossResultNth[g][v], result[g][v], DELTA);
	}

	@Test
	public void testCalculateTriplePointAnglesBoxFrame() {
		final double[][][] result = skeletonAngles.calculateTriplePointAngles(boxFrame,
				SkeletonAngles.VERTEX_TO_VERTEX);

		assertEquals("Incorrect number of graphs", 1, result.length);
		for (final double[][] graph : result) {
			assertEquals("Incorrect number of vertices", 8, graph.length);
			for (final double[] vertex : graph) {
				assertEquals("Incorrect number of angles", 3, vertex.length);
				for (double angle : vertex) {
					assertEquals("Angle measured incorrectly", PI_2, angle, DELTA);
				}
			}
		}
	}

	@Test
	public void testCalculateTriplePointAnglesBoxFrameNth() {
		final double[][][] result = skeletonAngles.calculateTriplePointAngles(boxFrame, 32);

		assertEquals("Incorrect number of graphs", 1, result.length);
		for (final double[][] graph : result) {
			assertEquals("Incorrect number of vertices", 8, graph.length);
			for (final double[] vertex : graph) {
				assertEquals("Incorrect number of angles", 3, vertex.length);
				for (double angle : vertex) {
					assertEquals("Angle measured incorrectly", PI_2_ISH, angle, DELTA);
				}
			}
		}
	}

	@Test
	public void testCalculateTriplePointAnglesReturnsNullIfImageCannotBeSkeletonized() {
		final ImagePlus imp = TestDataMaker.brick(10, 10, 10);
		final double[][][] result = skeletonAngles.calculateTriplePointAngles(imp, SkeletonAngles.VERTEX_TO_VERTEX);
		assertNull("Result should be null if image cannot be skeletonized", result);
	}
}
