/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
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
import static org.bonej.wrapperPlugins.IntertrabecularAngleWrapper.NO_RESULTS_MSG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;

import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.imagej.table.DefaultResultsTable;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;

/**
 * Tests for {@link IntertrabecularAngleWrapper}
 * 
 * @author Alessandro Felder
 * @author Richard Domander
 * @author Michael Doube
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class IntertrabecularAngleWrapperTest extends AbstractWrapperTest {

	@Test
	public void testAngleResultsUncalibratedPixels() throws Exception {
		// SETUP
		final Predicate<Double> nonEmpty = Objects::nonNull;
		final URL resource = getClass().getClassLoader().getResource(
			"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());
		
		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 2,
			"marginCutOff", 0, "useClusters", true, "iteratePruning", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
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
		assertEquals(1, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == Math.PI).count());
		assertEquals(2, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == Math.PI / 2).count());
	}
	
	@Test
	public void testAngleResultsIsotropicBigPixels() throws Exception {
		// SETUP
		final Predicate<Double> nonEmpty = Objects::nonNull;
		final URL resource = getClass().getClassLoader().getResource(
			"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());
		Calibration cal = new Calibration();
		cal.pixelDepth = 2;
		cal.pixelHeight = 2;
		cal.pixelWidth = 2;
		skelly.setCalibration(cal);

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 4,
			"marginCutOff", 0, "useClusters", true, "iteratePruning", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
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
		assertEquals(1, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == Math.PI).count());
		assertEquals(2, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == Math.PI / 2).count());
	}
	
	@Test
	public void testAngleResultsIsotropicSmallPixels() throws Exception {
		// SETUP
		final Predicate<Double> nonEmpty = Objects::nonNull;
		final URL resource = getClass().getClassLoader().getResource(
			"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());
		Calibration cal = new Calibration();
		cal.pixelDepth = 0.1; 
		cal.pixelHeight = 0.1;
		cal.pixelWidth = 0.1;
		skelly.setCalibration(cal);

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 0.2,
			"marginCutOff", 0, "useClusters", true, "iteratePruning", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
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
		assertEquals(1, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == Math.PI).count());
		assertEquals(2, fiveColumn.stream().filter(nonEmpty)
			.filter(d -> d == Math.PI / 2).count());
	}
	
	@Test
	public void testAnisotropicImageShowsWarningDialog() {
		CommonWrapperTests.testAnisotropyWarning(imageJ(),
			IntertrabecularAngleWrapper.class);
	}

	@Test
	public void testCompositeImageCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_CHANNEL_DIMENSIONS +
			". Please split the channels.";
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 3, 3, 1, 8);

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("A composite image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testMargins() throws Exception {
		// SETUP
		final URL resource = getClass().getClassLoader().getResource(
			"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 2,
			"marginCutOff", 10, "useClusters", true, "iteratePruning", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals(1, table.size());
		final DefaultColumn<Double> fiveColumn = table.get(0);
		assertEquals("5", fiveColumn.getHeader());
		assertEquals(10, fiveColumn.size());
		assertEquals(10, fiveColumn.stream().filter(Objects::nonNull).count());
	}
	
	/**
	 * Check that the skeleton image is returned as output when requested
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	@Test
	public void testShowSkeletonImageWhenRequested() throws ExecutionException,
		InterruptedException
	{
		final URL resource = getClass().getClassLoader().getResource(
				"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 2,
			"marginCutOff", 0, "useClusters", true, "iteratePruning", false, "showSkeleton", true).get();

		// VERIFY
		final ImagePlus skeleton = (ImagePlus) module.getOutput("skeletonImage");
		assertNotNull(skeleton);
		assertNotSame("Original image should not have been overwritten", skelly,
			skeleton);
	}
	
	/**
	 * Check that the skeleton image is not returned as output when not requested
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	@Test
	public void testDontShowSkeletonImageWhenNotRequested() throws ExecutionException,
		InterruptedException
	{
		final URL resource = getClass().getClassLoader().getResource(
				"test-skelly.zip");
		assert resource != null;
		final ImagePlus skelly = IJ.openImage(resource.getFile());

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", skelly,
			"minimumValence", 3, "maximumValence", 50, "minimumTrabecularLength", 2,
			"marginCutOff", 0, "useClusters", true, "iteratePruning", false, "showSkeleton", false).get();

		// VERIFY
		final ImagePlus skeleton = (ImagePlus) module.getOutput("skeletonImage");
		assertNull(skeleton);
		assertNotSame("Original image should not have been overwritten", skelly,
			skeleton);
	}
	

	@Test
	public void testNoResultsCancelsPlugin() throws Exception {
		// SETUP
		final ImagePlus pixel = NewImage.createByteImage("Test", 3, 3, 1,
			FILL_BLACK);
		pixel.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", pixel).get();

		// VERIFY
		assertTrue("Plugin should have cancelled", module.isCanceled());
		assertEquals("Cancel reason is incorrect", NO_RESULTS_MSG, module
			.getCancelReason());
		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testNoSkeletonsCancelsPlugin() throws Exception {
		// SETUP
		final ImagePlus imagePlus = IJ.createImage("test", 3, 3, 3, 8);

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", imagePlus).get();

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
			IntertrabecularAngleWrapper.class);
	}

	@Test
	public void testNullImageCancelsPlugin() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
			IntertrabecularAngleWrapper.class);
	}

	@Test
	public void testPrintCentroids() throws Exception {
		// SETUP
		doNothing().when(MOCK_UI).show(any(ImagePlus.class));
		final ImagePlus line = NewImage.createByteImage("Test", 5, 3, 1,
			FILL_BLACK);
		line.getStack().getProcessor(1).set(1, 1, (byte) 0xFF);
		line.getStack().getProcessor(1).set(2, 1, (byte) 0xFF);
		line.getStack().getProcessor(1).set(3, 1, (byte) 0xFF);

		// EXECUTE
		final CommandModule module = command().run(
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

	@Test
	public void testTimeDimensionCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_TIME_DIMENSIONS +
			". Please split the hyperstack.";
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 1, 3, 3, 8);

		// EXECUTE
		final CommandModule module = command().run(
			IntertrabecularAngleWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("An image with time dimension should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		//TODO reinstate if scijava and Mockito start playing well together again
//		verify(MOCK_UI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
//			any());
	}
}
