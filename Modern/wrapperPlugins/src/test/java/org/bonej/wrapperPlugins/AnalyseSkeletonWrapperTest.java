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

import static ij.gui.NewImage.FILL_BLACK;
import static org.bonej.wrapperPlugins.CommonMessages.NO_SKELETONS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.PrimitiveColumn;

/**
 * Tests for {@link AnalyseSkeletonWrapper}
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class AnalyseSkeletonWrapperTest extends AbstractWrapperTest {

	@Test
	public void testAdditionalResultsTable() throws Exception {
		// SETUP
		final ImagePlus line = NewImage.createByteImage("Test", 3, 3, 1,
			FILL_BLACK);
		line.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		line.getStack().getProcessor(1).set(2, 2, (byte) 0xFF);
		final String length = String.valueOf(Math.sqrt(2.0) / 2);
		Calibration cal = line.getCalibration();
		cal.pixelWidth = 0.5;
		cal.pixelHeight = 0.5;
		cal.pixelDepth = 0.5;
		line.setCalibration(cal);
		final String[] expectedHeaders = { "# Skeleton", "# Branch",
			"Branch length", "V1 x", "V1 y", "V1 z", "V2 x", "V2 y", "V2 z",
			"Euclidean distance", "running average length",
			"average intensity (inner 3rd)", "average intensity" };
		final String[] expectedValues = { "1", "1", length, "0.5", "0.5", "0.0", "1.0", "1.0",
			"0.0", length, length, "255.0", "255.0" };

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", line,
			"pruneCycleMethod", "None", "verbose", true, "pruneEnds", false).get();

		// VERIFY
		final DefaultGenericTable table = (DefaultGenericTable) module.getOutput(
			"verboseTable");
		assertNotNull(table);
		assertEquals("Results table has wrong number of columns",
			expectedHeaders.length, table.size());
		for (int i = 0; i < table.size(); i++) {
			final PrimitiveColumn<?, ?> column = (PrimitiveColumn<?, ?>) table.get(i);
			assertEquals("Column has incorrect header", expectedHeaders[i], column
				.getHeader());
			assertEquals("Column has wrong number of rows", 1, column.size());
			assertEquals("Column "+i+" has an incorrect value", expectedValues[i], String
				.valueOf(column.get(0)));
		}
	}

	@Test
	public void testAdditionalResultsTableNullWhenVerboseFalse()
		throws Exception
	{
		// SETUP
		final ImagePlus pixel = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", pixel,
			"pruneCycleMethod", "None", "verbose", false).get();

		// VERIFY
		assertNull(module.getOutput("verboseTable"));
	}

	@Test
	public void testCompositeImageCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_CHANNEL_DIMENSIONS +
			". Please split the channels.";
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 3, 3, 1, 8);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("A composite image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	/**
	 * Test that no skeleton image pops open, when the input is already a skeleton
	 * (or skeletonisation didn't change it)
	 */
	@Test
	public void testNoImageWhenNoSkeletonisation() throws Exception {
		// SETUP
		final ImagePlus pixel = NewImage.createByteImage("Test", 3, 3, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", pixel,
			"pruneCycleMethod", "None").get();

		// VERIFY
		assertFalse(
			"Sanity check failed: plugin cancelled before image could have been shown",
			module.isCanceled());
		verify(MOCK_UI, after(250).never()).show(any(ImagePlus.class));
	}

	@Test
	public void testNoSkeletonsCancelsPlugin() throws Exception {
		// SETUP
		final ImagePlus imagePlus = IJ.createImage("test", 3, 3, 3, 8);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", imagePlus,
			"pruneCycleMethod", "None").get();

		// VERIFY
		assertTrue("Plugin should have cancelled", module.isCanceled());
		assertEquals("Cancel reason is incorrect", NO_SKELETONS, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(imageJ(),
			AnalyseSkeletonWrapper.class);
	}

	@Test
	public void testNullImageCancelsPlugin() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
			AnalyseSkeletonWrapper.class);
	}

	@Test
	public void testResultImages() throws Exception {
		// SETUP
		final ImagePlus pixel = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		CommandModule module = command().run(AnalyseSkeletonWrapper.class,
			true, "inputImage", pixel, "pruneCycleMethod", "None", "displaySkeletons",
			false, "calculateShortestPaths", true).get();

		// VERIFY
		assertNull(module.getOutput("labelledSkeleton"));
		assertNull(module.getOutput("shortestPaths"));

		// EXECUTE
		module = command().run(AnalyseSkeletonWrapper.class, true,
			"inputImage", pixel, "pruneCycleMethod", "None", "displaySkeletons", true,
			"calculateShortestPaths", false).get();

		// VERIFY
		assertNotNull(module.getOutput("labelledSkeleton"));
		assertNull(module.getOutput("shortestPaths"));

		// EXECUTE
		module = command().run(AnalyseSkeletonWrapper.class, true,
			"inputImage", pixel, "pruneCycleMethod", "None", "displaySkeletons", true,
			"calculateShortestPaths", true).get();

		// VERIFY
		final ImagePlus labelledSkeleton = (ImagePlus) module.getOutput(
			"labelledSkeleton");
		assertNotNull(labelledSkeleton);
		final ImagePlus shortestPaths = (ImagePlus) module.getOutput(
			"shortestPaths");
		assertNotNull(shortestPaths);
		assertNotSame("Input image should not have been overwritten", pixel,
			labelledSkeleton);
		assertNotSame("Input image should not have been overwritten", pixel,
			shortestPaths);
	}

	@Test
	public void testResultsTable() throws Exception {
		// SETUP
		// Can't create a helper method to this class with ImagePlus in signature,
		// will cause "legacy injection" to fail
		final ImagePlus pixels = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		pixels.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		pixels.getStack().getProcessor(1).set(3, 3, (byte) 0xFF);
		final String[] expectedHeaders = { "# Skeleton", "# Branches",
			"# Junctions", "# End-point voxels", "# Junction voxels", "# Slab voxels",
			"Average Branch Length", "# Triple points", "# Quadruple points",
			"Maximum Branch Length", "Longest Shortest Path", "spx", "spy", "spz" };
		final double[][] expectedValues = { { 1, 2 }, { 0, 0 }, { 0,
			0 }, { 1, 1 }, { 0, 0 }, { 0, 0 }, { 0.0, 0.0 }, { 0,
				0 }, { 0, 0 }, { 0.0, 0.0 }, { 0.0, 0.0 }, { 1.0,
					3.0 }, { 1.0, 3.0 }, { 0.0, 0.0 } };

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", pixels,
			"pruneCycleMethod", "None", "calculateShortestPaths", true).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Results table has wrong number of columns",
			expectedHeaders.length, table.size());
		for (int i = 0; i < table.size(); i++) {
			final DefaultColumn<Double> column = table.get(i);
			assertEquals("Column has incorrect header", expectedHeaders[i], column
				.getHeader());
			assertEquals("Column has wrong number of rows", 2, column.size());
			for (int j = 0; j < 2; j++) {
				assertEquals("Column has an incorrect value", expectedValues[i][j],
						column.get(j), 1e-12);
			}
		}
	}

	/**
	 * Results table should have different number of columns when option
	 * "calculateShortestPath" is false see {@link #testResultsTable()}
	 */
	@Test
	public void testResultsTableShortestPathFalse() throws Exception {
		// SETUP
		final ImagePlus pixel = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", pixel,
			"pruneCycleMethod", "None", "calculateShortestPaths", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final Collection<DefaultColumn<Double>> table =
			(Collection<DefaultColumn<Double>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Results table has wrong number of columns", 10, table.size());
	}

	/**
	 * Test that the skeleton is displayed, when the input image gets skeletonised
	 */
	@Test
	public void testSkeletonImageWhenSkeletonised() throws Exception {
		final ImagePlus square = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		square.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		square.getStack().getProcessor(1).set(1, 2, (byte) 0xFF);
		square.getStack().getProcessor(1).set(2, 1, (byte) 0xFF);
		square.getStack().getProcessor(1).set(2, 2, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", square,
			"pruneCycleMethod", "None").get();

		// VERIFY
		assertFalse(
			"Sanity check failed: plugin cancelled before image could have been shown",
			module.isCanceled());
		verify(MOCK_UI, timeout(1000)).show(any(ImagePlus.class));
	}

	@Test
	public void testTimeDimensionCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_TIME_DIMENSIONS +
			". Please split the hyperstack.";
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 1, 3, 3, 8);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("An image with time dimension should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testEmptyImageCancels() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		// run() should cancel, when an image has no skeletons
		final ImagePlus blank = NewImage.createByteImage("Blank", 4, 4, 1,
			FILL_BLACK);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", blank).get();

		// VERIFY
		assertTrue("Sanity check failed: method didn't cancel", module
			.isCanceled());
	}

	@Test
	public void testSensibleInputDoesNotCancel() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final ImagePlus image = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		image.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", image).get();

		// VERIFY
		assertFalse("Sanity check failed: method cancelled", module.isCanceled());
	}
}
