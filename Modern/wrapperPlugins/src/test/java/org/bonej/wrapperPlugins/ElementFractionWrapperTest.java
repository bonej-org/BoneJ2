
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

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

/**
 * Regression tests for the {@link ElementFractionWrapper} plugin
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class ElementFractionWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@Test
	public void testNoCalibrationShowsWarning() throws Exception {
		CommonWrapperTests.testNoCalibrationShowsWarning(IMAGE_J,
			ElementFractionWrapper.class);
	}

	@Test
	public void testNonBinaryImageCancelsElementFraction() throws Exception {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
			ElementFractionWrapper.class);
	}

	@Test
	public void testNullImageCancelsElementFraction() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			ElementFractionWrapper.class);
	}

	// TODO Rewrite with a hyper stack
	@Test
	public void testResults() throws Exception {
		// SETUP
		final String unit = "mm";
		final double scale = 0.9;
		final int cubeSide = 5;
		final int padding = 1;
		final int stackSide = cubeSide + padding * 2;
		final double cubeVolume = cubeSide * cubeSide * cubeSide;
		final double spaceVolume = stackSide * stackSide * stackSide;
		final double elementSize = scale * scale * scale;
		final double[] expectedVolumes = { cubeVolume * elementSize };
		final double[] expectedTotalVolumes = { spaceVolume * elementSize };
		final double[] expectedRatios = { cubeVolume / spaceVolume };
		final double[][] expectedValues = { expectedVolumes, expectedTotalVolumes,
			expectedRatios };
		final String[] expectedHeaders = { "Bone volume (" + unit + "³)",
			"Total volume (" + unit + "³)", "Volume Ratio" };
		// Create an test image of a cuboid
		final Img<BitType> img = ArrayImgs.bits(stackSide, stackSide, stackSide);
		Views.interval(img, new long[] { 1, 1, 1 }, new long[] { 5, 5, 5 }).forEach(
			BitType::setOne);
		double[] calibration = new double[] { scale, scale, scale };
		final String[] units = new String[] { unit, unit, unit };
		final AxisType[] axes = { Axes.X, Axes.Y, Axes.Z };
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Cube", axes,
			calibration, units);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			ElementFractionWrapper.class, true, "inputImage", imgPlus).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final Table<DefaultColumn<String>, String> table =
			(Table<DefaultColumn<String>, String>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Wrong number of columns", 4, table.size());
		for (int i = 0; i < 3; i++) {
			final DefaultColumn<String> column = table.get(i + 1);
			assertEquals("Column has wrong number of rows", 1, column.size());
			assertEquals("Column has incorrect header", expectedHeaders[i], column
				.getHeader());
			for (int j = 0; j < 1; j++) {
				assertEquals("Column has an incorrect value", expectedValues[i][j],
					Double.parseDouble(column.get(j)), 1e-12);
			}
		}
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
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
