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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import net.imagej.ImageJ;

import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.UsageReporter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.PrimitiveColumn;
import org.scijava.ui.UserInterface;

/**
 * Tests for {@link AnalyseSkeletonWrapper}
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class AnalyseSkeletonWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private UsageReporter mockReporter;

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@Test
	public void testAdditionalResultsTable() throws Exception {
		// SETUP
		final ImagePlus line = NewImage.createByteImage("Test", 3, 3, 1,
			FILL_BLACK);
		line.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		line.getStack().getProcessor(1).set(2, 2, (byte) 0xFF);
		final String length = String.valueOf(Math.sqrt(2.0));
		final String[] expectedHeaders = { "# Skeleton", "# Branch",
			"Branch length", "V1 x", "V1 y", "V1 z", "V2 x", "V2 y", "V2 z",
			"Euclidean distance", "running average length",
			"average intensity (inner 3rd)", "average intensity" };
		final String[] expectedValues = { "1", "1", length, "1", "1", "0", "2", "2",
			"0", length, length, "255.0", "255.0" };

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
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
			assertEquals("Column has an incorrect value", expectedValues[i], String
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
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", pixel,
			"pruneCycleMethod", "None", "verbose", false).get();

		// VERIFY
		assertNull(module.getOutput("verboseTable"));
	}

	@Test
	public void testBadFormatIntensityImageCancelsPlugin() throws Exception {
		// SETUP
		final ImagePlus imagePlus = IJ.createImage("test", 3, 3, 3, 8);
		final UserInterface mockUI = mock(UserInterface.class);
		final File exceptionFile = mock(File.class);
		when(exceptionFile.getAbsolutePath()).thenReturn("file.foo");
		when(mockUI.chooseFile(any(File.class), anyString())).thenReturn(
			exceptionFile);
		IMAGE_J.ui().setDefaultUI(mockUI);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", imagePlus,
			"pruneCycleMethod", "Lowest intensity voxel").get();

		// VERIFY
		assertTrue("Plugin should have cancelled", module.isCanceled());
		assertEquals("Cancel reason is incorrect", "Image format is not recognized",
			module.getCancelReason());
	}

	@Test
	public void testCompositeImageCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_CHANNEL_DIMENSIONS +
			". Please split the channels.";
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 3, 3, 1, 8);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("A composite image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testIntensityImageChannelsCancelPlugin() throws Exception {
		final ImagePlus inputImage = IJ.createImage("test", 3, 3, 3, 8);
		final String intensityPath =
			"8bit-signed&pixelType=int8&planarDims=2&lengths=5,5,3&axes=X,Y,Channel.fake";
		mockIntensityFileOpening(intensityPath);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", inputImage,
			"pruneCycleMethod", "Lowest intensity voxel").get();

		// VERIFY
		assertTrue(module.isCanceled());
		assertEquals("The intensity image can't have a channel dimension", module
			.getCancelReason());
	}

	@Test
	public void testIntensityImageFramesCancelPlugin() throws Exception {
		final ImagePlus inputImage = IJ.createImage("test", 3, 3, 3, 8);
		final String intensityPath =
			"8bit-signed&pixelType=int8&planarDims=2&lengths=5,5,3&axes=X,Y,Time.fake";
		mockIntensityFileOpening(intensityPath);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", inputImage,
			"pruneCycleMethod", "Lowest intensity voxel").get();

		// VERIFY
		assertTrue(module.isCanceled());
		assertEquals("The intensity image can't have a time dimension", module
			.getCancelReason());
	}

	@Test
	public void testIntensityImageMismatchingDimensionsCancelPlugin()
		throws Exception
	{
		final ImagePlus inputImage = IJ.createImage("test", 3, 3, 1, 8);
		final String intensityPath =
			"8bit-signed&pixelType=int8&planarDims=2&lengths=5,5,3&axes=X,Y,Z.fake";
		mockIntensityFileOpening(intensityPath);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", inputImage,
			"pruneCycleMethod", "Lowest intensity voxel").get();

		// VERIFY
		assertTrue(module.isCanceled());
		assertEquals(
			"The intensity image should match the dimensionality of the input image",
			module.getCancelReason());
	}

	/**
	 * Test that no skeleton image pops open, when the input is already a skeleton
	 * (or skeletonisation didn't change it)
	 */
	@Test
	public void testNoImageWhenNoSkeletonisation() throws Exception {
		// SETUP
		final UserInterface mockUI = mock(UserInterface.class);
		IMAGE_J.ui().setDefaultUI(mockUI);
		final ImagePlus pixel = NewImage.createByteImage("Test", 3, 3, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", pixel,
			"pruneCycleMethod", "None").get();

		// VERIFY
		assertFalse(
			"Sanity check failed: plugin cancelled before image could have been shown",
			module.isCanceled());
		verify(mockUI, after(250).never()).show(any(ImagePlus.class));
	}

	@Test
	public void testNoSkeletonsCancelsPlugin() throws Exception {
		// SETUP
		final ImagePlus imagePlus = IJ.createImage("test", 3, 3, 3, 8);
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", imagePlus,
			"pruneCycleMethod", "None").get();

		// VERIFY
		assertTrue("Plugin should have cancelled", module.isCanceled());
		assertEquals("Cancel reason is incorrect", NO_SKELETONS, module
			.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(IMAGE_J,
			AnalyseSkeletonWrapper.class);
	}

	@Test
	public void testNot8BitIntensityImageCancelsPlugin() throws Exception {
		// SETUP
		final ImagePlus inputImage = IJ.createImage("test", 3, 3, 3, 8);
		final String intensityPath =
			"16bit-unsigned&pixelType=uint16&planarDims=2&lengths=10,11,3&axes=X,Y,Z.fake";
		mockIntensityFileOpening(intensityPath);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", inputImage,
			"pruneCycleMethod", "Lowest intensity voxel").get();

		// VERIFY
		assertTrue(module.isCanceled());
		assertEquals("The intensity image needs to be 8-bit greyscale", module
			.getCancelReason());
	}

	@Test
	public void testNullImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			AnalyseSkeletonWrapper.class);
	}

	@Test
	public void testResultImages() throws Exception {
		// SETUP
		final ImagePlus pixel = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		CommandModule module = IMAGE_J.command().run(AnalyseSkeletonWrapper.class,
			true, "inputImage", pixel, "pruneCycleMethod", "None", "displaySkeletons",
			false, "calculateShortestPaths", true).get();

		// VERIFY
		assertNull(module.getOutput("labelledSkeleton"));
		assertNull(module.getOutput("shortestPaths"));

		// EXECUTE
		module = IMAGE_J.command().run(AnalyseSkeletonWrapper.class, true,
			"inputImage", pixel, "pruneCycleMethod", "None", "displaySkeletons", true,
			"calculateShortestPaths", false).get();

		// VERIFY
		assertNotNull(module.getOutput("labelledSkeleton"));
		assertNull(module.getOutput("shortestPaths"));

		// EXECUTE
		module = IMAGE_J.command().run(AnalyseSkeletonWrapper.class, true,
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
		final CommandModule module = IMAGE_J.command().run(
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
					column.get(j).doubleValue(), 1e-12);
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
		final CommandModule module = IMAGE_J.command().run(
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
		final UserInterface mockUI = mock(UserInterface.class);
		IMAGE_J.ui().setDefaultUI(mockUI);
		final ImagePlus square = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		square.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		square.getStack().getProcessor(1).set(1, 2, (byte) 0xFF);
		square.getStack().getProcessor(1).set(2, 1, (byte) 0xFF);
		square.getStack().getProcessor(1).set(2, 2, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", square,
			"pruneCycleMethod", "None").get();

		// VERIFY
		assertFalse(
			"Sanity check failed: plugin cancelled before image could have been shown",
			module.isCanceled());
		verify(mockUI, timeout(1000)).show(any(ImagePlus.class));
	}

	@Test
	public void testTimeDimensionCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_TIME_DIMENSIONS +
			". Please split the hyperstack.";
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 1, 3, 3, 8);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("An image with time dimension should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testCancelledRunDoesNotReport() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		// run() should cancel, when an image has no skeletons
		final ImagePlus blank = NewImage.createByteImage("Blank", 4, 4, 1,
			FILL_BLACK);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", blank).get();

		// VERIFY
		assertTrue("Sanity check failed: method didn't cancel", module
			.isCanceled());
		verify(mockReporter, timeout(1000).times(0)).reportEvent(anyString());
	}

	@Test(expected = NullPointerException.class)
	public void testSetReporterThrowsNPEIfNull() {
		AnalyseSkeletonWrapper.setReporter(null);
	}

	@Test
	public void testSuccessfulRunReports() throws ExecutionException,
		InterruptedException
	{
		// SETUP
		final ImagePlus image = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		image.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", image).get();

		// VERIFY
		assertFalse("Sanity check failed: method cancelled", module.isCanceled());
		verify(mockReporter, timeout(1000)).reportEvent(anyString());
	}

	@Before
	public void setup() {
		mockReporter = mock(UsageReporter.class);
		doNothing().when(mockReporter).reportEvent(anyString());
		AnalyseSkeletonWrapper.setReporter(mockReporter);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	private void mockIntensityFileOpening(final String path) {
		final File intensityFile = mock(File.class);
		final UserInterface mockUI = mock(UserInterface.class);
		when(intensityFile.getAbsolutePath()).thenReturn(path);
		when(mockUI.chooseFile(any(File.class), anyString())).thenReturn(
			intensityFile);
		IMAGE_J.ui().setDefaultUI(mockUI);
	}
}
