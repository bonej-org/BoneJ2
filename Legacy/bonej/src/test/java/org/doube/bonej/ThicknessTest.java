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

import static org.junit.Assert.assertEquals;

import org.doube.geometry.TestDataMaker;
import org.doube.util.StackStats;
import org.junit.Test;

import ij.ImagePlus;

public class ThicknessTest {

	@Test
	public void testGetLocalThicknessRod() {
		final Thickness th = new Thickness();
		for (int d = 1; d < 25; d += 1) {
			final ImagePlus rod = TestDataMaker.rod(d * 100, d);
			final ImagePlus imp = th.getLocalThickness(rod, false);
			final double[] stats = StackStats.meanStdDev(imp);
			System.out.print(d + ", " + stats[0] + "\n");
			assertEquals(d, stats[0], 1.5);
		}
	}

	@Test
	public void testGetLocalThicknessSphere() {
		final Thickness th = new Thickness();
		for (int r = 2; r < 25; r++) {
			final ImagePlus sphere = TestDataMaker.sphere(r);
			final ImagePlus imp = th.getLocalThickness(sphere, false);
			final double[] stats = StackStats.meanStdDev(imp);
			final double regression = r * 1.9441872882 - 1.218936;
			System.out.print(r * 2 + ", " + stats[0] + "\n");
			assertEquals(regression, stats[0], regression * 0.1);
		}
	}

	@Test
	public void testGetLocalThicknessBrick() {
		final Thickness th = new Thickness();
		for (int t = 1; t < 21; t++) {
			final ImagePlus brick = TestDataMaker.brick(128, 128, t);
			final ImagePlus imp = th.getLocalThickness(brick, false);
			final double[] stats = StackStats.meanStdDev(imp);
			int expected = t;
			// pixelation and *2 (radius to diameter conversion) weirdness
			if (t % 2 != 0)
				expected++;
			System.out.print(t + ", " + stats[0] + "\n");
			assertEquals(expected, stats[0], expected * 0.05);
		}
	}

}
