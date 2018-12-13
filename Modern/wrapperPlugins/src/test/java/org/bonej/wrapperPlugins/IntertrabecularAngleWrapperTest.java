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
import static org.bonej.wrapperPlugins.IntertrabecularAngleWrapper.NO_RESULTS_MSG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;

import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.table.DefaultResultsTable;

import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.UsageReporter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.Gateway;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

/**
 * Tests for {@link IntertrabecularAngleWrapper}
 * 
 * @author Alessandro Felder
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class IntertrabecularAngleWrapperTest {

	private static final Gateway IMAGE_J = new ImageJ();
	private UsageReporter mockReporter;

	@Before
	public void setup() {
		mockReporter = mock(UsageReporter.class);
		doNothing().when(mockReporter).reportEvent(anyString());
		IntertrabecularAngleWrapper.setReporter(mockReporter);
	}

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@Test
	public void testAngleResults() throws Exception {
		// SETUP
		final Predicate<Double> nonEmpty = Objects::nonNull;
		final URL resource = getClass().getClassLoader().getResource(
			"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 2,
			"marginCutOff", 0, "useClusters", true, "iteratePruning", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("anglesTable");
		assertNotNull(table);
		assertEquals(2, table.size());
		final DefaultColumn<Double> threeColumn = table.get(0);
		assertEquals("3", threeColumn.getHeader());
		assertEquals(10, threeColumn.size());
		assertEquals(3, threeColumn.stream().filter(nonEmpty).count());
		assertEquals(2, threeColumn.stream().filter(nonEmpty).distinct().count());
		final DefaultColumn<Double> fiveColumn = table.get(1);
		assertEquals("5", fiveColumn.getHeader());
		assertEquals(10, fiveColumn.size());
		assertEquals(10, fiveColumn.stream().filter(nonEmpty).count());
		assertEquals(6, fiveColumn.stream().filter(nonEmpty).distinct().count());
		assertEquals(1, fiveColumn.stream().filter(nonEmpty).map(Double::valueOf)
			.filter(d -> d == Math.PI).count());
		assertEquals(2, fiveColumn.stream().filter(nonEmpty).map(Double::valueOf)
			.filter(d -> d == Math.PI / 2).count());
	}

	@Test
	public void testAnisotropicImageShowsWarningDialog() throws Exception {
		CommonWrapperTests.testAnisotropyWarning(IMAGE_J,
			IntertrabecularAngleWrapper.class);
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
			IntertrabecularAngleWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("A composite image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testMargins() throws Exception {
		// SETUP
		final Predicate<Double> nonEmpty = s -> !s.equals(SharedTable.EMPTY_CELL);
		final URL resource = getClass().getClassLoader().getResource(
			"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 2,
			"marginCutOff", 10, "useClusters", true, "iteratePruning", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("anglesTable");
		assertNotNull(table);
		assertEquals(1, table.size());
		final DefaultColumn<Double> fiveColumn = table.get(0);
		assertEquals("5", fiveColumn.getHeader());
		assertEquals(10, fiveColumn.size());
		assertEquals(10, fiveColumn.stream().filter(nonEmpty).count());
	}

	@Test
	public void testMultipleGraphsShowsWarningDialog() throws Exception {
		// SETUP
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockUI.dialogPrompt(startsWith("Image has multiple skeletons"),
			anyString(), eq(WARNING_MESSAGE), any())).thenReturn(mockPrompt);
		IMAGE_J.ui().setDefaultUI(mockUI);
		final ImagePlus pixels = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		pixels.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		pixels.getStack().getProcessor(1).set(3, 3, (byte) 0xFF);

		// EXECUTE
		IMAGE_J.command().run(IntertrabecularAngleWrapper.class, true, "inputImage",
			pixels).get();

		// VERIFY
		verify(mockUI, timeout(1000)).dialogPrompt(startsWith(
			"Image has multiple skeletons"), anyString(), eq(WARNING_MESSAGE), any());
	}

	/**
	 * Test that no skeleton image pops open, when the input is already a skeleton
	 * (or skeletonisation didn't change it)
	 */
	@Test
	public void testNoImageWhenNoSkeletonisation() throws Exception {
		// SETUP
		final UserInterface mockUI = mock(UserInterface.class);
		doNothing().when(mockUI).show(any(ImagePlus.class));
		IMAGE_J.ui().setDefaultUI(mockUI);
		final ImagePlus pixel = NewImage.createByteImage("Test", 3, 3, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", pixel).get();

		// VERIFY
		assertEquals(
			"Sanity check failed: plugin cancelled before image could have been shown",
			NO_RESULTS_MSG, module.getCancelReason());
		verify(mockUI, after(250).never()).show(any(ImagePlus.class));
	}

	@Test
	public void testNoResultsCancelsPlugin() throws Exception {
		// SETUP
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus pixel = NewImage.createByteImage("Test", 3, 3, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", pixel).get();

		// VERIFY
		assertTrue("Plugin should have cancelled", module.isCanceled());
		assertEquals("Cancel reason is incorrect", NO_RESULTS_MSG, module
			.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testNoSkeletonsCancelsPlugin() throws Exception {
		// SETUP
		final ImagePlus imagePlus = IJ.createImage("test", 3, 3, 3, 8);
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", imagePlus).get();

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
			IntertrabecularAngleWrapper.class);
	}

	@Test
	public void testNullImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			IntertrabecularAngleWrapper.class);
	}

	@Test
	public void testPrintCentroids() throws Exception {
		// SETUP
		final UserInterface mockUI = mock(UserInterface.class);
		doNothing().when(mockUI).show(any(ImagePlus.class));
		IMAGE_J.ui().setDefaultUI(mockUI);
		final ImagePlus line = NewImage.createByteImage("Test", 5, 3, 1,
			FILL_BLACK);
		line.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		line.getStack().getProcessor(1).set(2, 1, (byte) 0xFF);
		line.getStack().getProcessor(1).set(3, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", line,
			"minimumTrabecularLength", 0, "printCentroids", true).get();

		final DefaultResultsTable centroids = (DefaultResultsTable) module
			.getOutput("centroidTable");
		// VERIFY

		assertEquals(6, centroids.getColumnCount());
		assertEquals(1, centroids.getRowCount());

		final Iterator<String> expectedColumnHeaders = Stream.of("V1x", "V1y",
			"V1z", "V2x", "V2y", "V2z").iterator();
		centroids.forEach(c -> assertEquals(expectedColumnHeaders.next(), c
			.getHeader()));

		assertEquals(1.0, centroids.get(0, 0), 1e-12);
		assertEquals(1.0, centroids.get(1, 0), 1e-12);
		assertEquals(0.0, centroids.get(2, 0), 1e-12);

		assertEquals(3.0, centroids.get(3, 0), 1e-12);
		assertEquals(1.0, centroids.get(4, 0), 1e-12);
		assertEquals(0.0, centroids.get(5, 0), 1e-12);

	}

	/**
	 * Test that the skeleton is displayed, when the input image gets skeletonised
	 */
	@Test
	public void testSkeletonImageWhenSkeletonised() throws Exception {
		// SETUP
		final UserInterface mockUI = mock(UserInterface.class);
		doNothing().when(mockUI).show(any(ImagePlus.class));
		IMAGE_J.ui().setDefaultUI(mockUI);
		final ImagePlus square = NewImage.createByteImage("Test", 4, 4, 1,
			FILL_BLACK);
		square.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		square.getStack().getProcessor(1).set(1, 2, (byte) 0xFF);
		square.getStack().getProcessor(1).set(2, 1, (byte) 0xFF);
		square.getStack().getProcessor(1).set(2, 2, (byte) 0xFF);

		// EXECUTE
		IMAGE_J.command().run(IntertrabecularAngleWrapper.class, true, "inputImage",
			square).get();

		// VERIFY
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
			IntertrabecularAngleWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("An image with time dimension should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
