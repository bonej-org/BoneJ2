/*-
 * #%L
 * Utility methods for BoneJ2
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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

package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import org.joml.Vector3d;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Unit tests for the {@link RoiManagerUtil RoiManagerUtil} class.
 *
 * @author Richard Domander
 */
public class RoiManagerUtilTest {
	@Test
	public void testIsActiveOnAllSlices() {
		assertTrue(RoiManagerUtil.isActiveOnAllSlices(-1));
		assertTrue(RoiManagerUtil.isActiveOnAllSlices(0));
		assertFalse(RoiManagerUtil.isActiveOnAllSlices(1));
	}

	@Test
	public void testPointRoiCoordinates() {
		final PointRoi pointRoi = new PointRoi(8, 9);
		pointRoi.setPosition(13);
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(new Roi[] { new Roi(1, 2,
			1, 1), pointRoi, new TextRoi(3, 4, "foo") });

		final List<Vector3d> points = RoiManagerUtil.pointROICoordinates(
			MOCK_ROI_MANAGER);

		assertEquals(1, points.size());
		final Vector3d point = points.get(0);
		assertEquals(pointRoi.getXBase(), point.x, 1e-12);
		assertEquals(pointRoi.getYBase(), point.y, 1e-12);
		assertEquals(pointRoi.getPosition(), point.z, 1e-12);
	}

	// Tests that the method filters out duplicate points within and between
	// multi-point ROIs. Duplicate points have the same (x, y, z) coordinates.
	@Test
	public void testPointRoiCoordinatesMultiPointRois() {
		final PointRoi roi = new PointRoi(new int[] { 1, 1, 2 }, new int[] { 0, 0,
			0 }, 3);
		roi.setPosition(1);
		final PointRoi roi2 = new PointRoi(new int[] { 1, 1 }, new int[] { 0, 0 },
			2);
		roi2.setPosition(2);
		final RoiManager MOCK_ROI_MANAGER = mock(RoiManager.class);
		when(MOCK_ROI_MANAGER.getRoisAsArray()).thenReturn(new Roi[] { roi, roi2 });

		final List<Vector3d> result = RoiManagerUtil.pointROICoordinates(
			MOCK_ROI_MANAGER);

		assertEquals(3, result.size());
		assertEquals(new Vector3d(1, 0, 1), result.get(0));
		assertEquals(new Vector3d(2, 0, 1), result.get(1));
		assertEquals(new Vector3d(1, 0, 2), result.get(2));
	}

	@Test(expected = NullPointerException.class)
	public void testPointRoiCoordinatesThrowsNPEIfRoiManagerNull() {
		RoiManagerUtil.pointROICoordinates(null);
	}
}
