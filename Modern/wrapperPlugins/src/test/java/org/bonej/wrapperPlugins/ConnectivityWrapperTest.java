/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
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

package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.ConnectivityWrapper.NEGATIVE_CONNECTIVITY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;

import java.util.Arrays;
import java.util.List;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

/**
 * Integration / Regression tests for the {@link ConnectivityWrapper} plugin
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class ConnectivityWrapperTest extends AbstractWrapperTest {

	@Test
	public void test2DImageCancelsConnectivity() {
		CommonWrapperTests.test2DImageCancelsPlugin(imageJ(),
			ConnectivityWrapper.class);
	}

	@Test
	public void testNegativeConnectivityShowsInfoDialog() throws Exception {
		// Mock UI
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(MOCK_UI.dialogPrompt(eq(NEGATIVE_CONNECTIVITY), anyString(), eq(
			INFORMATION_MESSAGE), any())).thenReturn(mockPrompt);

		// Create a 3D hyperstack with two channels. Each channel has two particles
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm");
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final Img<BitType> img = ArrayImgs.bits(5, 5, 5, 2);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis, cAxis);
		final RandomAccess<BitType> access = imgPlus.randomAccess();
		// Channel 0
		access.setPosition(new long[] { 1, 1, 1, 0 });
		access.get().setOne();
		access.setPosition(new long[] { 3, 3, 3, 0 });
		access.get().setOne();
		// Channel 1
		access.setPosition(new long[] { 1, 1, 1, 1 });
		access.get().setOne();
		access.setPosition(new long[] { 3, 3, 3, 1 });
		access.get().setOne();

		// Run command
		command().run(ConnectivityWrapper.class, true, "inputImage",
			imgPlus).get();

		// Dialog should only be shown once
		verify(MOCK_UI, timeout(1000).times(1)).dialogPrompt(eq(
			NEGATIVE_CONNECTIVITY), anyString(), eq(INFORMATION_MESSAGE), any());
	}

	@Test
	public void testNonBinaryImageCancelsConnectivity() {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(imageJ(),
			ConnectivityWrapper.class);
	}

	@Test
	public void testNullImageCancelsConnectivity() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
			ConnectivityWrapper.class);
	}

	@Test
	public void testResults() throws Exception {
		// SETUP
		// Create an test image of a cuboid
		final String unit = "mm";
		final List<String> expectedHeaders = Arrays.asList("Euler char. (χ)",
			"Corr. Euler (χ + Δχ)", "Connectivity", String.format(
				"Conn.D (%s⁻³)", unit));
		final long size = 3;
		final double scale = 0.9;
		final long spaceSize = size * size * size;
		final double elementSize = scale * scale * scale;
		final double[] expectedChis = { 0.0, 1.0, 1.0, 0.0 };
		final double[] expectedDeltaChis = { 0.0, 1.0, 1.0, 0.0 };
		final double[] expectedConnectivities = { 1.0, 0.0, 0.0, 1.0 };
		final double expectedDensity = 1.0 / (spaceSize * elementSize);
		final double[] expectedDensities = { expectedDensity, 0.0, 0.0,
			expectedDensity };
		final double[][] expectedValues = { expectedChis, expectedDeltaChis,
			expectedConnectivities, expectedDensities };
		/* Create a hyperstack with two channels and two frames.
		 * Two of the 3D subspaces are empty, and two of them contain a single voxel
		 */
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, scale);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit, scale);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<BitType> img = ArrayImgs.bits(size, size, size, 2, 2);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis, cAxis, tAxis);
		final RandomAccess<BitType> access = imgPlus.randomAccess();
		// Add a voxel to Channel 1, Frame 0
		access.setPosition(new long[] { 1, 1, 1, 1, 0 });
		access.get().setOne();
		// Add a voxel to Channel 0, Frame 1
		access.setPosition(new long[] { 1, 1, 1, 0, 1 });
		access.get().setOne();

		// EXECUTE
		final CommandModule module = command().run(
			ConnectivityWrapper.class, true, "inputImage", imgPlus).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Results table has wrong number of columns", 4, table.size());
		for (int i = 0; i < 4; i++) {
			// Ignore column 0, the label column
			final DefaultColumn<Double> column = table.get(i);
			assertEquals("A column has wrong number of rows", 4, column.size());
			final String header = column.getHeader();
			assertEquals("A column has an incorrect header", expectedHeaders.get(i),
				header);
			for (int j = 0; j < column.size(); j++) {
				assertEquals(expectedValues[i][j], column.get(j),	1e-12);
			}
		}
	}
}
