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

import java.util.List;

import org.doube.geometry.TestDataMaker;
import org.junit.Test;
import org.scijava.vecmath.Point3f;

import ij.ImagePlus;
import marchingcubes.MCTriangulator;

public class MeasureSurfaceTest {

	@Test
	public void testGetSurfaceAreaOctahedron() {
		final double area = MeasureSurface.getSurfaceArea(TestDataMaker.octahedron());
		assertEquals(4.4558146404, area, 1e-6);
	}

	@Test
	public void testGetSurfaceAreaSphere() {
		final int r = 64;
		final ImagePlus imp = TestDataMaker.sphere(r);
		final MCTriangulator mct = new MCTriangulator();
		final boolean[] channels = { true, false, false };
		@SuppressWarnings("unchecked")
		final List<Point3f> points = mct.getTriangles(imp, 128, channels, 4);
		final double area = MeasureSurface.getSurfaceArea(points);
		assertEquals(4 * Math.PI * r * r, area, area * 0.05);
	}

	@Test
	public void testGetSurfaceAreaBox() {
		final int d = 128;
		final ImagePlus imp = TestDataMaker.brick(d, d, d);
		final MCTriangulator mct = new MCTriangulator();
		final boolean[] channels = { true, false, false };
		@SuppressWarnings("unchecked")
		final List<Point3f> points = mct.getTriangles(imp, 128, channels, 4);
		final double area = MeasureSurface.getSurfaceArea(points);
		assertEquals(6 * d * d, area, area * 0.02);
	}
}