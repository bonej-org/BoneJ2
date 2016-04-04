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
package org.doube.bonej;

import static org.junit.Assert.assertArrayEquals;

import java.awt.Rectangle;

import org.doube.geometry.TestDataMaker;
import org.junit.Ignore;
import org.junit.Test;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

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
		RoiManager roiMan = new RoiManager();
		int w = rod.getWidth();
		roiMan.addRoi(new Roi(new Rectangle(0, 0, w / 2, w / 2)));
		double[] vols = vf.getVolumes(rod, 1, 255, true);
		assertArrayEquals(quarterRod, vols, 0);
		roiMan.close();

		roiMan = new RoiManager();
		w = sphere.getWidth();
		roiMan.addRoi(new Roi(new Rectangle(0, 0, w / 2, w / 2)));
		vols = vf.getVolumes(sphere, 1, 255, true);
		assertArrayEquals(quarterSphere, vols, 0);
		roiMan.close();

		roiMan = new RoiManager();
		w = brick.getWidth();
		final int h = brick.getHeight();
		roiMan.addRoi(new Roi(new Rectangle(0, 0, w / 2, h / 2)));
		vols = vf.getVolumes(brick, 1, 255, true);
		assertArrayEquals(quarterBrick, vols, 0);
		roiMan.close();
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

	@Test
	public void testGetSurfaceVolumeImagePlusDoubleDoubleIntBooleanBoolean() {
		RoiManager roiMan = new RoiManager();
		int w = rod.getWidth();
		roiMan.addRoi(new Roi(new Rectangle(0, 0, w / 2, w / 2)));
		double[] vols = vf.getSurfaceVolume(rod, 1, 255, 1, true, false);
		assertArrayEquals(quarterRod, vols, 500);
		roiMan.close();

		roiMan = new RoiManager();
		w = sphere.getWidth();
		roiMan.addRoi(new Roi(new Rectangle(0, 0, w / 2, w / 2)));
		vols = vf.getSurfaceVolume(sphere, 1, 255, 1, true, false);
		assertArrayEquals(quarterSphere, vols, 300);
		roiMan.close();

		roiMan = new RoiManager();
		w = brick.getWidth();
		final int h = brick.getHeight();
		roiMan.addRoi(new Roi(new Rectangle(0, 0, w / 2, h / 2)));
		vols = vf.getSurfaceVolume(brick, 1, 255, 1, true, false);
		assertArrayEquals(quarterBrick, vols, 200);
		roiMan.close();
	}

}
