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
import org.junit.Test;

import ij.ImagePlus;

public class AnisotropyTest {

	private final Anisotropy anisotropy = new Anisotropy();

	@Test
	public void testRunToStableResultIsotropy() {
		final ImagePlus imp = TestDataMaker.binaryNoise(256, 256, 256, 0.25);
		final Object[] result = anisotropy.runToStableResult(imp, 100, 2000, 50000, 256 / 4, 2.3, 0.005, true);
		final double da = ((double[]) result[0])[0];
		assertEquals(0, da, 1e-2);
	}

	@Test
	public void testRunToStableResultAnisotropy() {
		final ImagePlus imp = TestDataMaker.plates(256, 256, 256, 8);
		final Object[] result = anisotropy.runToStableResult(imp, 100, 2000, 50000, 256 / 4, 2.3, 0.005, true);
		final double da = ((double[]) result[0])[0];
		assertEquals(1, da, 1e-12);
	}

	@Test
	public void testCalculateSingleSphereIsotropy() {
		final ImagePlus imp = TestDataMaker.binaryNoise(256, 256, 256, 0.25);
		final double[] centroid = { 128, 128, 128 };
		final Object[] result = anisotropy.calculateSingleSphere(imp, centroid, 127, 2.3, 50000, false);
		final double da = ((double[]) result[0])[0];
		assertEquals(0, da, 1e-1);
	}

	@Test
	public void testCalculateSingleSphereAnisotropy() {
		final ImagePlus imp = TestDataMaker.plates(256, 256, 256, 8);
		final double[] centroid = { 128, 128, 128 };
		final Object[] result = anisotropy.calculateSingleSphere(imp, centroid, 127, 2.3, 50000, false);
		final double da = ((double[]) result[0])[0];
		assertEquals(1, da, 1e-2);
	}

}
