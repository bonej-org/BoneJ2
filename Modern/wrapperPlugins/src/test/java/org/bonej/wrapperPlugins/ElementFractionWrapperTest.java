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


package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

/**
 * Regression tests for the {@link ElementFractionWrapper} plugin
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class ElementFractionWrapperTest extends AbstractWrapperTest {

	@Test
	public void testNonBinaryImageCancelsElementFraction() {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(imageJ(),
			ElementFractionWrapper.class);
	}

	@Test
	public void testNullImageCancelsElementFraction() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
			ElementFractionWrapper.class);
	}

	@Test
	public void testResults3DHyperstack() throws Exception {
		// SETUP
		final String unit = "mm";
		final double scale = 0.9;
		final int cubeSide = 5;
		final int stackSide = cubeSide + 2;
		final int expectedSize = 4;
		final double elementSize = scale * scale * scale;
		final double cubeVolume = cubeSide * cubeSide * cubeSide * elementSize;
		final double spaceVolume = stackSide * stackSide * stackSide * elementSize;
		final double ratio = cubeVolume / spaceVolume;
		final double[] expectedVolumes = { cubeVolume, 0, 0, cubeVolume };
		final double[] expectedTotalVolumes = { spaceVolume, spaceVolume,
			spaceVolume, spaceVolume };
		final double[] expectedRatios = { ratio, 0, 0, ratio };
		final double[][] expectedValues = { expectedVolumes, expectedTotalVolumes,
			expectedRatios };
		final String[] expectedHeaders = { "BV (" + unit + "³)",
			"TV (" + unit + "³)", "BV/TV" };
		// Create an hyperstack Img with a cube at (channel:0, frame:0) and (c:1,
		// f:1)
		final Img<BitType> img = ArrayImgs.bits(stackSide, stackSide, stackSide, 2,
			2);
		Views.interval(img, new long[] { 1, 1, 1, 0, 0 }, new long[] { 5, 5, 5, 0,
			0 }).forEach(BitType::setOne);
		Views.interval(img, new long[] { 1, 1, 1, 1, 1 }, new long[] { 5, 5, 5, 1,
			1 }).forEach(BitType::setOne);
		// Wrap Img in a calibrated ImgPlus
		final double[] calibration = { scale, scale, scale, 1.0, 1.0 };
		final String[] units = { unit, unit, unit, "", "" };
		final AxisType[] axes = { Axes.X, Axes.Y, Axes.Z, Axes.TIME, Axes.CHANNEL };
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Cube", axes,
			calibration, units);

		// EXECUTE
		final CommandModule module = command().run(
			ElementFractionWrapper.class, true, "inputImage", imgPlus).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Wrong number of columns", 3, table.size());
		for (int i = 0; i < 3; i++) {
			final DefaultColumn<Double> column = table.get(i);
			assertEquals("Column has wrong number of rows", expectedSize, column
				.size());
			assertEquals("Column has incorrect header", expectedHeaders[i], column
				.getHeader());
			for (int j = 0; j < expectedSize; j++) {
				assertEquals("Incorrect value at row " + j + ", column " + i,
					expectedValues[i][j], column.get(j), 1e-12);
			}
		}
	}

	@Test
	public void testResultsComposite2D() throws Exception {
		// SETUP
		final String unit = "mm";
		final int squareSide = 5;
		final int stackSide = squareSide + 2;
		final int expectedSize = 2;
		final double squareArea = squareSide * squareSide;
		final double spaceArea = stackSide * stackSide;
		final double ratio = squareArea / spaceArea;
		final double[] expectedAreas = { 0, squareArea };
		final double[] expectedTotalAreas = { spaceArea, spaceArea };
		final double[] expectedRatios = { 0, ratio };
		final double[][] expectedValues = { expectedAreas, expectedTotalAreas,
			expectedRatios };
		final String[] expectedHeaders = { "BA (" + unit + "\u00B2)",
			"TA (" + unit + "\u00B2)", "BA/TA" };
		// Create an 2D image with two channels with a square drawn on channel 2
		final Img<BitType> img = ArrayImgs.bits(stackSide, stackSide, 2);
		Views.interval(img, new long[] { 1, 1, 1 }, new long[] { 5, 5, 1 }).forEach(
			BitType::setOne);
		// Wrap Img in an ImgPlus
		final double[] calibration = { 1.0, 1.0, 1.0 };
		final String[] units = { unit, unit, "" };
		final AxisType[] axes = { Axes.X, Axes.Y, Axes.CHANNEL };
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Square", axes,
			calibration, units);

		// EXECUTE
		final CommandModule module = command().run(
			ElementFractionWrapper.class, true, "inputImage", imgPlus).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Wrong number of columns", 3, table.size());
		for (int i = 0; i < 3; i++) {
			final DefaultColumn<Double> column = table.get(i);
			assertEquals("Column has wrong number of rows", expectedSize, column
				.size());
			assertEquals("Column has incorrect header", expectedHeaders[i], column
				.getHeader());
			for (int j = 0; j < expectedSize; j++) {
				assertEquals("Incorrect value at row " + j + ", column " + i,
					expectedValues[i][j], column.get(j), 1e-12);
			}
		}
	}

	@Test
	public void testWeirdSpatialImageCancelsPlugin() throws Exception {
		// Mock UI
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(MOCK_UI.dialogPrompt(anyString(), anyString(), any(), any()))
			.thenReturn(mockPrompt);

		// Create an hyperstack with no calibration
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final Img<DoubleType> img = ArrayImgs.doubles(5, 5);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			cAxis);

		// Run command
		final CommandModule module = command().run(
			ElementFractionWrapper.class, true, "inputImage", imgPlus).get();

		assertTrue("A non 2D and 3D image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", CommonMessages.WEIRD_SPATIAL,
			module.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}
}
