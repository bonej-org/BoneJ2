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

import static org.junit.Assert.assertEquals;

import org.bonej.geometry.TestDataMaker;
import org.junit.Ignore;
import org.junit.Test;

import ij.ImagePlus;

public class StructureModelIndexTest {

	// FIXME: out of memory error from command line
	@Ignore
	@Test
	public void testHildRuegRod() {
		final ImagePlus imp = TestDataMaker.rod(16384, 64);
		final double smi = StructureModelIndex.hildRueg(imp, 6, 0.5f);
		assertEquals(3.0, smi, 0.03);
	}

	// FIXME: out of memory error from command line
	@Ignore
	@Test
	public void testHildRuegPlate() {
		final ImagePlus imp = TestDataMaker.brick(2048, 2048, 6);
		final double smi = StructureModelIndex.hildRueg(imp, 6, 0.5f);
		assertEquals(0.0, smi, 0.05);
	}

	@Ignore // Test abuses GUI and fails on Jenkins server
	@Test
	public void testHildRuegSphere() {
		final ImagePlus imp = TestDataMaker.sphere(256);
		final double smi = StructureModelIndex.hildRueg(imp, 6, 0.5f);
		assertEquals(4.0, smi, 0.01);
	}
}
