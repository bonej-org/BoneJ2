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
package org.bonej.plugins;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import java.awt.Rectangle;

import org.bonej.util.TestDataMaker;
import org.junit.Ignore;
import org.junit.Test;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(RoiManager.class)
public class VolumeFractionTest {
	private final ImagePlus rod = TestDataMaker.rod(256, 64);
	private final ImagePlus sphere = TestDataMaker.sphere(64);
	private final ImagePlus brick = TestDataMaker.brick(32, 64, 96);
	private final VolumeFraction vf = new VolumeFraction();
	private final double[] expectedRod = { 826368, 256 * 128 * 128 };
	private final double[] expectedSphere = { 1097342, 130 * 130 * 131 };
	private final double[] expectedBrick = { 32 * 64 * 96, 34 * 66 * 98 };
	private final double[] quarterRod = { 206592, 256 * 64 * 64 };
	private final double[] quarterSphere = { 274335, 65 * 65 * 131 };
	private final double[] quarterBrick = { 16 * 32 * 96, 17 * 33 * 98 };

	@Test
	public void testGetVolumesImagePlusDoubleDouble() {
		double[] vols = vf.getVolumes(rod, 1, 255);
		assertArrayEquals(expectedRod, vols, 0);

		vols = vf.getVolumes(sphere, 1, 255);
		assertArrayEquals(expectedSphere, vols, 0);

		vols = vf.getVolumes(brick, 1, 255);
		assertArrayEquals(expectedBrick, vols, 0);

		int w = rod.getWidth();
		rod.setRoi(new Rectangle(0, 0, w / 2, w / 2));
		vols = vf.getVolumes(rod, 1, 255);
		assertArrayEquals(quarterRod, vols, 0);

		w = sphere.getWidth();
		sphere.setRoi(new Rectangle(0, 0, w / 2, w / 2));
		vols = vf.getVolumes(sphere, 1, 255);
		assertArrayEquals(quarterSphere, vols, 0);

		w = brick.getWidth();
		final int h = brick.getHeight();
		brick.setRoi(new Rectangle(0, 0, w / 2, h / 2));
		vols = vf.getVolumes(brick, 1, 255);
		assertArrayEquals(quarterBrick, vols, 0);

		rod.setRoi(new Rectangle(0, 0, 0, 0));
		sphere.setRoi(new Rectangle(0, 0, 0, 0));
		brick.setRoi(new Rectangle(0, 0, 0, 0));
	}

	@Test
	public void testGetVolumesImagePlusDoubleDoubleBoolean() {
		//Mock a RoiManager with a roi that covers a quarter of the rod
		final RoiManager roiMan = mock(RoiManager.class);
		int w = rod.getWidth();
		final Roi rodRoi = new Roi(0, 0, w / 2, w / 2);
		rodRoi.setName(""); // mocking fails with a null name
		when(roiMan.getRoisAsArray()).thenReturn(new Roi[]{rodRoi});
		when(roiMan.getSliceNumber(anyString())).thenReturn(-1);
		mockStatic(RoiManager.class);
		when(RoiManager.getInstance()).thenReturn(roiMan);

		double[] vols = vf.getVolumes(rod, 1, 255, true);

		assertArrayEquals(quarterRod, vols, 0);

		//Mock a RoiManager with a roi that covers a quarter of the sphere
		w = sphere.getWidth();
		final Roi sphereRoi = new Roi(0, 0, w / 2, w / 2);
		sphereRoi.setName("");
		when(roiMan.getRoisAsArray()).thenReturn(new Roi[]{sphereRoi});

		vols = vf.getVolumes(sphere, 1, 255, true);

		assertArrayEquals(quarterSphere, vols, 0);

		//Mock a RoiManager with a roi that covers a quarter of the brick
		w = brick.getWidth();
		final int h = brick.getHeight();
		final Roi brickRoi = new Roi(0, 0, w / 2, h / 2);
		brickRoi.setName("");
		when(roiMan.getRoisAsArray()).thenReturn(new Roi[]{brickRoi});

		vols = vf.getVolumes(brick, 1, 255, true);

		assertArrayEquals(quarterBrick, vols, 0);
	}

	// FIXME: out of memory error from command line
    @Ignore
	@Test
	public void testGetSurfaceVolumeImagePlusDoubleDoubleInt() {
		double[] vols = vf.getSurfaceVolume(rod, 1, 255, 1);
		assertArrayEquals(expectedRod, vols, 3000);

		vols = vf.getSurfaceVolume(sphere, 1, 255, 1);
		assertArrayEquals(expectedSphere, vols, 2000);

		vols = vf.getSurfaceVolume(brick, 1, 255, 1);
		assertArrayEquals(expectedBrick, vols, 200);
	}

	@Ignore // Test abuses GUI and fails on Jenkins server
	@Test
	public void testGetSurfaceVolumeImagePlusDoubleDoubleIntBooleanBoolean() {
		//Mock a RoiManager with a roi that covers a quarter of the rod
		final RoiManager roiMan = mock(RoiManager.class);
		int w = rod.getWidth();
		final Roi rodRoi = new Roi(0, 0, w / 2, w / 2);
		rodRoi.setName(""); // mocking fails with a null name
		when(roiMan.getRoisAsArray()).thenReturn(new Roi[]{rodRoi});
		when(roiMan.getSliceNumber(anyString())).thenReturn(-1);
		mockStatic(RoiManager.class);
		when(RoiManager.getInstance()).thenReturn(roiMan);

		double[] vols = vf.getSurfaceVolume(rod, 1, 255, 1, true, false);

		assertArrayEquals(quarterRod, vols, 500);

		//Mock a RoiManager with a roi that covers a quarter of the sphere
		w = sphere.getWidth();
		final Roi sphereRoi = new Roi(0, 0, w / 2, w / 2);
		sphereRoi.setName("");
		when(roiMan.getRoisAsArray()).thenReturn(new Roi[]{sphereRoi});

		vols = vf.getSurfaceVolume(sphere, 1, 255, 1, true, false);

		assertArrayEquals(quarterSphere, vols, 300);

		//Mock a RoiManager with a roi that covers a quarter of the brick
		w = brick.getWidth();
		final int h = brick.getHeight();
		final Roi brickRoi = new Roi(0, 0, w / 2, h / 2);
		brickRoi.setName("");
		when(roiMan.getRoisAsArray()).thenReturn(new Roi[]{brickRoi});

		vols = vf.getSurfaceVolume(brick, 1, 255, 1, true, false);

		assertArrayEquals(quarterBrick, vols, 200);
	}

}
