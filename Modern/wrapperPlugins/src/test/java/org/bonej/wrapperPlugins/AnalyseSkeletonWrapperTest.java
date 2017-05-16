
package org.bonej.wrapperPlugins;

import static ij.gui.NewImage.FILL_BLACK;
import static org.bonej.wrapperPlugins.CommonMessages.GOT_SKELETONISED;
import static org.bonej.wrapperPlugins.CommonMessages.NO_SKELETONS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;

import java.io.File;

import net.imagej.ImageJ;
import net.imagej.table.DefaultColumn;
import net.imagej.table.DefaultGenericTable;
import net.imagej.table.PrimitiveColumn;
import net.imagej.table.Table;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;

/**
 * Tests for {@link AnalyseSkeletonWrapper}
 *
 * @author Richard Domander
 */
public class AnalyseSkeletonWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test
	public void testNullImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			AnalyseSkeletonWrapper.class);
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(IMAGE_J,
			AnalyseSkeletonWrapper.class);
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
		verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(),
			any());
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
		verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(),
			any());
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
		verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	/**
	 * Test that there is no dialog about skeletonisation when image is already a
	 * skeleton (or skeletonisation didn't change it)
	 */
	@Test
	public void testNoDialogWhenImageSkeleton() throws Exception {
		// SETUP
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockUI.dialogPrompt(eq(GOT_SKELETONISED), anyString(), eq(
			INFORMATION_MESSAGE), any())).thenReturn(mockPrompt);
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
			"Sanity check failed: plugin cancelled before dialog could have been shown",
			module.isCanceled());
		verify(mockUI, after(100).never()).dialogPrompt(eq(GOT_SKELETONISED),
			anyString(), eq(INFORMATION_MESSAGE), any());
	}

	/** Test that a dialog about the image being skeletonised is shown */
	@Test
	public void testSkeletonisationDialog() throws Exception {
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockUI.dialogPrompt(eq(GOT_SKELETONISED), anyString(), eq(
			INFORMATION_MESSAGE), any())).thenReturn(mockPrompt);
		IMAGE_J.ui().setDefaultUI(mockUI);
		final ImagePlus square = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		square.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		square.getStack().getProcessor(1).set(1, 2, (byte) 0xFF);
		square.getStack().getProcessor(1).set(2, 1, (byte) 0xFF);
		square.getStack().getProcessor(1).set(2, 2, (byte) 0xFF);

		// EXECUTE
		CommandModule module = IMAGE_J.command().run(AnalyseSkeletonWrapper.class,
			true, "inputImage", square, "pruneCycleMethod", "None").get();

		// VERIFY
		assertFalse(
			"Sanity check failed: plugin cancelled before dialog could have been shown",
			module.isCanceled());
		verify(mockUI, after(100).times(1)).dialogPrompt(eq(GOT_SKELETONISED),
			anyString(), eq(INFORMATION_MESSAGE), any());
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
		final String[][] expectedValues = { { "1", "2" }, { "0", "0" }, { "0",
			"0" }, { "1", "1" }, { "0", "0" }, { "0", "0" }, { "0.0", "0.0" }, { "0",
				"0" }, { "0", "0" }, { "0.0", "0.0" }, { "0.0", "0.0" }, { "1.0",
					"3.0" }, { "1.0", "3.0" }, { "0.0", "0.0" } };

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			AnalyseSkeletonWrapper.class, true, "inputImage", pixels,
			"pruneCycleMethod", "None", "calculateShortestPath", true).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final Table<DefaultColumn<String>, String> table =
			(Table<DefaultColumn<String>, String>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Results table has wrong number of columns",
			expectedHeaders.length + 1, table.size());
		for (int i = 0; i < table.size() - 1; i++) {
			final DefaultColumn<String> column = table.get(i + 1);
			assertEquals("Column has incorrect header", expectedHeaders[i], column
				.getHeader());
			assertEquals("Column has wrong number of rows", 2, column.size());
			for (int j = 0; j < 2; j++) {
				assertEquals("Column has an incorrect value", expectedValues[i][j],
					column.get(j));
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
			"pruneCycleMethod", "None", "calculateShortestPath", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final Table<DefaultColumn<String>, String> table =
			(Table<DefaultColumn<String>, String>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Results table has wrong number of columns", 11, table.size());
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
	public void testResultImages() throws Exception {
		// SETUP
		final ImagePlus pixel = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		CommandModule module = IMAGE_J.command().run(AnalyseSkeletonWrapper.class,
			true, "inputImage", pixel, "pruneCycleMethod", "None", "displaySkeletons",
			false, "calculateShortestPath", true).get();

		// VERIFY
		assertNull(module.getOutput("labelledSkeleton"));
		assertNull(module.getOutput("shortestPaths"));

		// EXECUTE
		module = IMAGE_J.command().run(AnalyseSkeletonWrapper.class, true,
			"inputImage", pixel, "pruneCycleMethod", "None", "displaySkeletons", true,
			"calculateShortestPath", false).get();

		// VERIFY
		assertNotNull(module.getOutput("labelledSkeleton"));
		assertNull(module.getOutput("shortestPaths"));

		// EXECUTE
		module = IMAGE_J.command().run(AnalyseSkeletonWrapper.class, true,
			"inputImage", pixel, "pruneCycleMethod", "None", "displaySkeletons", true,
			"calculateShortestPath", true).get();

		// VERIFY
		final ImagePlus labelledSkeleton = (ImagePlus) module.getOutput(
			"labelledSkeleton");
		assertNotNull(labelledSkeleton);
		final ImagePlus shortestPaths = (ImagePlus) module.getOutput(
			"shortestPaths");
		assertNotNull(shortestPaths);
		assertNotEquals("Input image should not have been overwritten", pixel,
			labelledSkeleton);
		assertNotEquals("Input image should not have been overwritten", pixel,
			shortestPaths);
	}
}
