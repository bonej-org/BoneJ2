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
import static org.junit.Assert.assertNull;

import org.doube.geometry.TestDataMaker;
import org.junit.Test;

import ij.ImagePlus;

public class SkeletonAnglesTest {

	private final double pi4 = Math.PI / 4;
	private final double pi2 = Math.PI / 2;
	/** Not quite pi / 2 because vertex isn't exactly at corner */
	private final double pi2ish = 1.5904976894727854;

	private final double[][][] circleCrossResult = {
			{ { pi4, pi4, pi2 }, null, { pi4, pi2, pi4 }, { pi4, pi2, pi4 }, { pi4, pi4, pi2 } } };

	private final double[][][] circleCrossResultNth = {
			{ { pi2, pi2, Math.PI }, null, { pi2, Math.PI, pi2 }, { pi2, Math.PI, pi2 }, { pi2, pi2, Math.PI } } };

	private final double[][][] boxFrameResult = { { { pi2, pi2, pi2 }, { pi2, pi2, pi2 }, { pi2, pi2, pi2 },
			{ pi2, pi2, pi2 }, { pi2, pi2, pi2 }, { pi2, pi2, pi2 }, { pi2, pi2, pi2 }, { pi2, pi2, pi2 } } };

	private final double[][][] boxFrameResultNth = { { { pi2ish, pi2ish, pi2ish }, { pi2ish, pi2ish, pi2ish },
			{ pi2ish, pi2ish, pi2ish }, { pi2ish, pi2ish, pi2ish }, { pi2ish, pi2ish, pi2ish },
			{ pi2ish, pi2ish, pi2ish }, { pi2ish, pi2ish, pi2ish }, { pi2ish, pi2ish, pi2ish } } };

	// FIXME: requires updates to Analyze Skeleton behavior
	// @Test
	public void testCalculateTriplePointAnglesCrossedCircle() {
		final ImagePlus imp = TestDataMaker.crossedCircle(256);
		final double[][][] result = (new SkeletonAngles()).calculateTriplePointAngles(imp,
				SkeletonAngles.VERTEX_TO_VERTEX);
		for (int g = 0; g < circleCrossResult.length; g++)
			for (int v = 0; v < circleCrossResult[g].length; v++)
				assertArrayEquals(circleCrossResult[g][v], result[g][v], 1e-12);
	}

	// FIXME: requires updates to Analyze Skeleton behavior
	// @Test
	public void testCalculateTriplePointAnglesCrossedCircleNth() {
		final ImagePlus imp = TestDataMaker.crossedCircle(256);
		final double[][][] result = (new SkeletonAngles()).calculateTriplePointAngles(imp, 8);
		for (int g = 0; g < circleCrossResultNth.length; g++)
			for (int v = 0; v < circleCrossResultNth[g].length; v++)
				assertArrayEquals(circleCrossResultNth[g][v], result[g][v], 1e-12);
	}

	@Test
	public void testCalculateTriplePointAnglesBoxFrame() {
		final ImagePlus imp = TestDataMaker.boxFrame(128, 128, 128);
		final double[][][] result = (new SkeletonAngles()).calculateTriplePointAngles(imp,
				SkeletonAngles.VERTEX_TO_VERTEX);
		for (int g = 0; g < boxFrameResult.length; g++)
			for (int v = 0; v < boxFrameResult[g].length; v++)
				assertArrayEquals(boxFrameResult[g][v], result[g][v], 1e-12);
	}

	// FIXME
	// @Test
	public void testCalculateTriplePointAnglesBoxFrameNth() {
		final ImagePlus imp = TestDataMaker.boxFrame(128, 128, 128);
		final double[][][] result = (new SkeletonAngles()).calculateTriplePointAngles(imp, 32);
		for (int g = 0; g < boxFrameResultNth.length; g++)
			for (int v = 0; v < boxFrameResultNth[g].length; v++)
				assertArrayEquals(boxFrameResultNth[g][v], result[g][v], 1e-12);
	}

	// FIXME
	// @Test
	public void testCalculateTriplePointAnglesReturnsNullIfImageCannotBeSkeletonized() {
		final ImagePlus imp = TestDataMaker.brick(10, 10, 10);
		final double[][][] result = (new SkeletonAngles()).calculateTriplePointAngles(imp,
				SkeletonAngles.VERTEX_TO_VERTEX);
		assertNull("Result should be null if image cannot be skeletonized", result);
	}
}
