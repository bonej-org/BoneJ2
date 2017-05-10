
package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.util.Iterator;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;

/**
 * Tests for {@link ThicknessWrapper}
 *
 * @author Richard Domander
 */
public class ThicknessWrapperTest {

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
			ThicknessWrapper.class);
	}

	@Test
	public void testNonBinaryImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(IMAGE_J,
			ThicknessWrapper.class);
	}

	@Test
	public void test2DImageCancelsPlugin() throws Exception {
		CommonWrapperTests.test2DImagePlusCancelsPlugin(IMAGE_J,
			ThicknessWrapper.class);
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
		final CommandModule module = IMAGE_J.command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("An image with time dimension should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", expectedMessage, module
			.getCancelReason());
		verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testAnisotropicImageShowsWarningDialog() throws Exception {
		// SETUP
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockPrompt.prompt()).thenReturn(DialogPrompt.Result.CANCEL_OPTION);
		when(mockUI.dialogPrompt(startsWith("The image is anisotropic"),
			anyString(), eq(WARNING_MESSAGE), any())).thenReturn(mockPrompt);
		IMAGE_J.ui().setDefaultUI(mockUI);
		final Calibration calibration = new Calibration();
		calibration.pixelWidth = 300;
		calibration.pixelHeight = 1;
		calibration.pixelDepth = 1;
		final ImagePlus imagePlus = NewImage.createByteImage("", 5, 5, 5, 1);
		imagePlus.setCalibration(calibration);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus).get();

		// VERIFY
		verify(mockUI, after(100).times(1)).dialogPrompt(startsWith(
			"The image is anisotropic"), anyString(), eq(WARNING_MESSAGE), any());
		assertTrue("Pressing cancel on warning dialog should have cancelled plugin",
			module.isCanceled());
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
		verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testNoHeaderUnitsWhenUncalibrated() throws Exception {
		// SETUP
		final ImagePlus imagePlus = NewImage.createByteImage("", 2, 2, 2, 1);
		final Iterator<String> expectedHeaders = Stream.of("Label", "Tb.Th Mean",
			"Tb.Th Std Dev", "Tb.Th Max").iterator();

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(ThicknessWrapper.class,
			true, "inputImage", imagePlus, "mapChoice", "Trabecular thickness").get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final Table<DefaultColumn<String>, String> table =
			(Table<DefaultColumn<String>, String>) module.getOutput("resultsTable");
		assertTrue(table.stream().allMatch(c -> expectedHeaders.next().equals(c
			.getHeader())));
		assertFalse(expectedHeaders.hasNext());
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
		final Table<DefaultColumn<String>, String> table =
			(Table<DefaultColumn<String>, String>) module.getOutput("resultsTable");
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
		assertNotEquals("Original image should not have been overwritten",
			imagePlus, trabecularMap);
		assertNotEquals("Original image should not have been overwritten",
			imagePlus, spacingMap);
		assertNotEquals("Map images should be independent", trabecularMap, spacingMap);
	}
}
