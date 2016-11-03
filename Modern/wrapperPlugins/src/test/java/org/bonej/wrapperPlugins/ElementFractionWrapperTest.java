
package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;

import org.bonej.testImages.Cuboid;
import org.bonej.utilities.ResultsInserter;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

import ij.measure.ResultsTable;

/**
 * Regression tests for the {@link ElementFractionWrapper} plugin
 *
 * @author Richard Domander
 */
public class ElementFractionWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@BeforeClass
	public static void oneTimeSetup() {
		ResultsInserter.getInstance().setHeadless(true);
	}

	@Before
	public void setup() {
		ResultsInserter.getInstance().getResultsTable().reset();
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test
	public void testNullImageCancelsElementFraction() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			ElementFractionWrapper.class);
	}

	@Test
	public void testNonBinaryImageCancelsElementFraction() throws Exception {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
			ElementFractionWrapper.class);
	}

	@Test
	public void testNoCalibrationShowsWarning() throws Exception {
		CommonWrapperTests.testNoCalibrationShowsWarning(IMAGE_J,
			ElementFractionWrapper.class);
	}

	@Test
	public void testWeirdSpatialImageCancelsPlugin() throws Exception {
		// Mock UI
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockUI.dialogPrompt(anyString(), anyString(), any(), any()))
			.thenReturn(mockPrompt);
		IMAGE_J.ui().setDefaultUI(mockUI);

		// Create an hyperstack with no calibration
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final Img<DoubleType> img = ArrayImgs.doubles(5, 5);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			cAxis);

		// Run command
		final CommandModule module = IMAGE_J.command().run(
			ElementFractionWrapper.class, true, "inputImage", imgPlus).get();

		assertTrue("A non 2D and 3D image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", CommonMessages.WEIRD_SPATIAL,
			module.getCancelReason());
		verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@Test
	public void testResults() throws Exception {
		// Create an test image of a cuboid
		final String unit = "mm";
		final double scale = 0.9;
		final int cubeSide = 5;
		final int padding = 1;
		final int stackSide = cubeSide + padding * 2;
		final int cubeVolume = cubeSide * cubeSide * cubeSide;
		final int spaceSize = stackSide * stackSide * stackSide;
		final double elementSize = scale * scale * scale;
		final ImgPlus<BitType> imgPlus = (ImgPlus<BitType>) IMAGE_J.op().run(
			Cuboid.class, cubeSide, cubeSide, cubeSide, 1, 1, padding, scale, unit);

		// Run command and get results
		IMAGE_J.command().run(ElementFractionWrapper.class, true, "inputImage",
			imgPlus).get();
		final ResultsTable resultsTable = ResultsInserter.getInstance()
			.getResultsTable();
		final String[] headings = resultsTable.getHeadings();
		final double boneVolume = resultsTable.getValue(headings[1], 0);
		final double totalVolume = resultsTable.getValue(headings[2], 0);
		final double ratio = resultsTable.getValue(headings[3], 0);

		assertEquals("Wrong number of results", 1, resultsTable.size());
		assertEquals("Column header is incorrect", "Bone Volume (" + unit + "³)",
			headings[1]);
		assertEquals("Column header is incorrect", "Total Volume (" + unit + "³)",
			headings[2]);
		assertEquals("Column header is incorrect", "Volume Ratio", headings[3]);
		assertEquals("Bone volume is incorrect", cubeVolume * elementSize,
			boneVolume, 1e-12);
		assertEquals("Total volume is incorrect", spaceSize * elementSize,
			totalVolume, 1e-12);
		assertEquals("Ratio is incorrect", (cubeVolume * elementSize) / (spaceSize *
			elementSize), ratio, 1e-12);
	}
}
