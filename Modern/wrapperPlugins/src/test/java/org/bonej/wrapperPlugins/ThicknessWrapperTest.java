/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;
import ij.process.LUT;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;

/**
 * Tests for {@link ThicknessWrapper}
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class ThicknessWrapperTest extends AbstractWrapperTest {

	@Test
	public void test2DImageCancelsPlugin() {
		CommonWrapperTests.test2DImagePlusCancelsPlugin(imageJ(),
			ThicknessWrapper.class);
	}

	@Test
	public void testAnisotropicImageShowsWarningDialog() {
		CommonWrapperTests.testAnisotropyWarning(imageJ(), ThicknessWrapper.class);
	}

	@Test
	public void testCompositeImageCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_CHANNEL_DIMENSIONS +
			". Please split the channels.";
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 3, 3, 1, 8);

		// EXECUTE
		final CommandModule module = command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("A composite image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testMapImageLUTs() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final LUT fireLUT = Common.makeFire();
		final ImagePlus imagePlus = NewImage.createByteImage("TinyTestImage", 2, 2,
			2, 1);

		// EXECUTE
		final CommandModule module = command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "mapChoice", "Both", "showMaps", true)
			.get();

		// VERIFY
		final LUT trabecularMap = ((ImagePlus) module.getOutput("trabecularMap"))
			.getLuts()[0];
		final LUT spacingMap = ((ImagePlus) module.getOutput("spacingMap"))
			.getLuts()[0];
		assertArrayEquals("Trabecular map doesn't have the 'fire' LUT", fireLUT.getBytes(),
				trabecularMap.getBytes());
		assertArrayEquals("Spacing map doesn't have the 'fire' LUT", fireLUT
				.getBytes(), spacingMap.getBytes());
	}

	@Test
	public void testMapImagesBoth() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final ImagePlus imagePlus = NewImage.createByteImage("image", 2, 2, 2, 1);

		// EXECUTE
		final CommandModule module = command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "mapChoice", "Both", "showMaps", true)
			.get();

		// VERIFY
		assertNotNull(module.getOutput("trabecularMap"));
		assertNotNull(module.getOutput("spacingMap"));
	}

	@Test
	public void testMapImagesShowMapsFalse() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final ImagePlus imagePlus = NewImage.createByteImage("image", 2, 2, 2, 1);

		// EXECUTE
		final CommandModule module = command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "mapChoice", "Both", "showMaps", false)
			.get();

		// VERIFY
		assertNull(module.getOutput("trabecularMap"));
		assertNull(module.getOutput("spacingMap"));
	}

	@Test
	public void testMapImagesTrabecularSpacing() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final ImagePlus imagePlus = NewImage.createByteImage("image", 2, 2, 2, 1);

		// EXECUTE
		final CommandModule module = command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "mapChoice", "Trabecular spacing",
			"showMaps", true).get();

		// VERIFY
		final ImagePlus spacingMap = (ImagePlus) module.getOutput("spacingMap");
		assertNotNull(spacingMap);
		assertNull(module.getOutput("trabecularMap"));
		assertNotSame("Original image should not have been overwritten", imagePlus,
			spacingMap);
	}

	@Test
	public void testMapImagesTrabecularThickness() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final ImagePlus imagePlus = NewImage.createByteImage("image", 2, 2, 2, 1);

		// EXECUTE
		final CommandModule module = command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "mapChoice", "Trabecular thickness",
			"showMaps", true).get();

		// VERIFY
		final ImagePlus trabecularMap = (ImagePlus) module.getOutput(
			"trabecularMap");
		assertNotNull(trabecularMap);
		assertNull(module.getOutput("spacingMap"));
		assertNotSame("Original image should not have been overwritten", imagePlus,
			trabecularMap);
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(imageJ(),
			ThicknessWrapper.class);
	}

	@Test
	public void testNullImageCancelsPlugin() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
			ThicknessWrapper.class);
	}

	@Test
	public void testResults() throws Exception {
		// SETUP
		final ImagePlus imagePlus = NewImage.createByteImage("TinyTestImage", 2, 2,
			2, 1);
		final Calibration calibration = new Calibration();
		calibration.setUnit("mm");
		imagePlus.setCalibration(calibration);
		final String[] expectedHeaders = { "Tb.Th Mean (mm)", "Tb.Th Std Dev (mm)",
			"Tb.Th Max (mm)", "Tb.Sp Mean (mm)", "Tb.Sp Std Dev (mm)",
			"Tb.Sp Max (mm)" };
		final Double[][] expectedValues = { { Double.NaN }, { Double.NaN }, {
			Double.NaN }, { 10.392304420471191 }, { 0.0 }, { 10.392304420471191 } };

		// EXECUTE
		final CommandModule module = command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "mapChoice", "Both", "maskArtefacts",
			false, "showMaps", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Results table has wrong number of columns", 6, table.size());
		for (int i = 0; i < 6; i++) {
			final DefaultColumn<Double> column = table.get(i);
			assertEquals(expectedHeaders[i], column.getHeader());
			assertEquals("Results table has wrong number of rows", 1, column.size());
			final int j = 0;
			assertEquals("Cell at i=" + i + ", j=" + j + " has an incorrect value",
				expectedValues[i][j], column.getValue(j));
		}
	}

	@Test
	public void testTimeDimensionCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_TIME_DIMENSIONS +
			". Please split the hyperstack.";
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 1, 3, 3, 8);

		// EXECUTE
		final CommandModule module = command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("An image with time dimension should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}
}
