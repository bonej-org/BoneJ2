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
import ij.measure.Calibration;

public class ConnectivityTest {

	private final Connectivity conn = new Connectivity();

	@Test
	public void testGetConnDensity() {
		final ImagePlus imp = TestDataMaker.boxFrame(32, 64, 128);
		final Calibration cal = imp.getCalibration();
		cal.pixelDepth = 0.2;
		cal.pixelHeight = 0.2;
		cal.pixelWidth = 0.2;
		// boxFrame adds 32 pixels of padding around the box
		final double stackVolume = (32 + 64) * (64 + 64) * (128 + 64) * (0.2 * 0.2 * 0.2);
		final double sumEuler = conn.getSumEuler(imp);
		final double deltaChi = conn.getDeltaChi(imp, sumEuler);
		final double connectivity = conn.getConnectivity(deltaChi);
		final double connD = conn.getConnDensity(imp, connectivity);
		assertEquals(5 / stackVolume, connD, 1e-12);
	}

	@Test
	public void testGetSumEulerCrossedCircle() {
		for (int size = 16; size < 1024; size *= 2) {
			final ImagePlus imp = TestDataMaker.crossedCircle(size);
			final double sumEuler = conn.getSumEuler(imp);
			assertEquals(-3, sumEuler, 1e-12);
		}
	}

	@Test
	public void testGetSumEulerBoxFrame() {
		for (int size = 16; size < 256; size *= 2) {
			final ImagePlus imp = TestDataMaker.boxFrame(size, size, size);
			final double sumEuler = conn.getSumEuler(imp);
			assertEquals(-4, sumEuler, 1e-12);
		}
	}
}
