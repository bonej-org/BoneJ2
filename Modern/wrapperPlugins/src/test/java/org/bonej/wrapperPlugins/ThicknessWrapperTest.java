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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import net.imagej.ImageJ;
import net.imagej.table.DefaultColumn;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.Gateway;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;

/**
 * Tests for {@link ThicknessWrapper}
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class ThicknessWrapperTest {

	private static final Gateway IMAGE_J = new ImageJ();

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@Test
	public void test2DImageCancelsPlugin() throws Exception {
		CommonWrapperTests.test2DImagePlusCancelsPlugin(IMAGE_J,
			ThicknessWrapper.class);
	}

	@Test
	public void testAnisotropicImageShowsWarningDialog() throws Exception {
		CommonWrapperTests.testAnisotropyWarning(IMAGE_J, ThicknessWrapper.class);
	}

	@Test
	public void testCompositeImageCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_CHANNEL_DIMENSIONS +
			". Please split the channels.";
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 3, 3, 1, 8);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("A composite image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testMapImages() throws Exception {
		final ImagePlus imagePlus = NewImage.createByteImage("", 2, 2, 2, 1);

		CommandModule module = IMAGE_J.command().run(ThicknessWrapper.class, true,
			"inputImage", imagePlus, "mapChoice", "Both", "showMaps", false).get();
		assertNull(module.getOutput("trabecularMap"));
		assertNull(module.getOutput("spacingMap"));

		module = IMAGE_J.command().run(ThicknessWrapper.class, true, "inputImage",
			imagePlus, "mapChoice", "Trabecular thickness", "showMaps", true).get();
		assertNotNull(module.getOutput("trabecularMap"));
		assertNull(module.getOutput("spacingMap"));

		module = IMAGE_J.command().run(ThicknessWrapper.class, true, "inputImage",
			imagePlus, "mapChoice", "Trabecular spacing", "showMaps", true).get();
		assertNull(module.getOutput("trabecularMap"));
		assertNotNull(module.getOutput("spacingMap"));

		module = IMAGE_J.command().run(ThicknessWrapper.class, true, "inputImage",
			imagePlus, "mapChoice", "Both", "showMaps", true).get();
		final ImagePlus trabecularMap = (ImagePlus) module.getOutput(
			"trabecularMap");
		final ImagePlus spacingMap = (ImagePlus) module.getOutput("spacingMap");
		assertNotNull(trabecularMap);
		assertNotNull(spacingMap);
		assertNotSame("Original image should not have been overwritten", imagePlus,
			trabecularMap);
		assertNotSame("Original image should not have been overwritten", imagePlus,
			spacingMap);
		assertNotSame("Map images should be independent", trabecularMap,
			spacingMap);
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(IMAGE_J,
			ThicknessWrapper.class);
	}

	@Test
	public void testNullImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			ThicknessWrapper.class);
	}

	@Test
	public void testNullROIManagerCancelsPlugin() throws Exception {
		// SETUP
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus imagePlus = NewImage.createByteImage("", 5, 5, 5, 1);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "cropToRois", true).get();

		// VERIFY
		assertTrue("No ROI Manager should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect",
			"Can't crop without valid ROIs in the ROIManager", module
				.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testResults() throws Exception {
		// SETUP
		final ImagePlus imagePlus = NewImage.createByteImage("", 2, 2, 2, 1);
		final Calibration calibration = new Calibration();
		calibration.setUnit("mm");
		imagePlus.setCalibration(calibration);
		final String[] expectedHeaders = { "Tb.Th Mean (mm)", "Tb.Th Std Dev (mm)",
			"Tb.Th Max (mm)", "Tb.Sp Mean (mm)", "Tb.Sp Std Dev (mm)",
			"Tb.Sp Max (mm)" };
		final String[][] expectedValues = { { "", "NaN" }, { "", "NaN" }, { "",
			"NaN" }, { "10.392304420471191", "" }, { "0.0", "" }, {
				"10.392304420471191", "" } };

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "mapChoice", "Both", "maskArtefacts",
			false, "cropToRois", false, "showMaps", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<String>> table =
			(List<DefaultColumn<String>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Results table has wrong number of columns", 7, table.size());
		for (int i = 0; i < 6; i++) {
			final DefaultColumn<String> column = table.get(i + 1);
			assertEquals(expectedHeaders[i], column.getHeader());
			for (int j = 0; j < 2; j++) {
				assertEquals("Column has an incorrect value", expectedValues[i][j],
					column.getValue(j));
			}
		}
	}

	@Test
	public void testTimeDimensionCancelsPlugin() throws Exception {
		// SETUP
		final String expectedMessage = CommonMessages.HAS_TIME_DIMENSIONS +
			". Please split the hyperstack.";
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 1, 3, 3, 8);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus).get();

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
