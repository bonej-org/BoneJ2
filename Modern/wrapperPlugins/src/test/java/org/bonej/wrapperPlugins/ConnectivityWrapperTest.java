
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.ConnectivityWrapper.NEGATIVE_CONNECTIVITY;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;

import java.util.Arrays;
import java.util.List;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

/**
 * Integration / Regression tests for the {@link ConnectivityWrapper} plugin
 *
 * @author Richard Domander
 */
public class ConnectivityWrapperTest {

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
	public void testNullImageCancelsConnectivity() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			ConnectivityWrapper.class);
	}

	@Test
	public void test2DImageCancelsConnectivity() throws Exception {
		CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J,
			ConnectivityWrapper.class);
	}

	@Test
	public void testNonBinaryImageCancelsConnectivity() throws Exception {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
			ConnectivityWrapper.class);
	}

	@Test
	public void testNoCalibrationShowsWarning() throws Exception {
		CommonWrapperTests.testNoCalibrationShowsWarning(IMAGE_J,
			ConnectivityWrapper.class);
	}

	@Test
	public void testNegativeConnectivityShowsInfoDialog() throws Exception {
		// Mock UI
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockUI.dialogPrompt(eq(NEGATIVE_CONNECTIVITY), anyString(), eq(
			INFORMATION_MESSAGE), any())).thenReturn(mockPrompt);
		IMAGE_J.ui().setDefaultUI(mockUI);

		// Create a 3D hyperstack with two channels. Each channel has two particles
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm");
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final Img<BitType> img = ArrayImgs.bits(5, 5, 5, 2);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis, cAxis);
		final RandomAccess<BitType> access = imgPlus.randomAccess();
		// Channel 0
		access.setPosition(new long[] { 1, 1, 1, 0 });
		access.get().setOne();
		access.setPosition(new long[] { 3, 3, 3, 0 });
		access.get().setOne();
		// Channel 1
		access.setPosition(new long[] { 1, 1, 1, 1 });
		access.get().setOne();
		access.setPosition(new long[] { 3, 3, 3, 1 });
		access.get().setOne();

		// Run command
		IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage",
			imgPlus).get();

		// Dialog should only be shown once
		verify(mockUI, after(100).times(1)).dialogPrompt(eq(NEGATIVE_CONNECTIVITY),
			anyString(), eq(INFORMATION_MESSAGE), any());
	}

	@Test
	public void testResults() throws Exception {
		// SETUP
		// Create an test image of a cuboid
		final String unit = "mm";
		final List<String> expectedHeaders = Arrays.asList("Euler char. (χ)",
			"Corrected Euler (χ + Δχ)", "Connectivity", String.format(
				"Conn. density (%s³)", unit));
		final long size = 3;
		final double scale = 0.9;
		final long spaceSize = size * size * size;
		final double elementSize = scale * scale * scale;
		final double[] expectedChis = { 0.0, 1.0, 1.0, 0.0 };
		final double[] expectedDeltaChis = { 0.0, 1.0, 1.0, 0.0 };
		final double[] expectedConnectivities = { 1.0, 0.0, 0.0, 1.0 };
		final double expectedDensity = 1.0 / (spaceSize * elementSize);
		final double[] expectedDensities = { expectedDensity, 0.0, 0.0,
			expectedDensity };
		final double[][] expectedValues = { expectedChis, expectedDeltaChis,
			expectedConnectivities, expectedDensities };
		/* Create a hyperstack with two channels and two frames.
		 * Two of the 3D subspaces are empty, and two of them contain a single voxel
		 */
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, scale);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit, scale);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<BitType> img = ArrayImgs.bits(size, size, size, 2, 2);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis, cAxis, tAxis);
		final RandomAccess<BitType> access = imgPlus.randomAccess();
		// Add a voxel to Channel 1, Frame 0
		access.setPosition(new long[] { 1, 1, 1, 1, 0 });
		access.get().setOne();
		// Add a voxel to Channel 0, Frame 1
		access.setPosition(new long[] { 1, 1, 1, 0, 1 });
		access.get().setOne();

		// EXECUTE
		IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage",
			imgPlus).get();

		// VERIFY
		final Table<DefaultColumn<String>, String> table = SharedTable.getTable();
		assertEquals("Results table has wrong number of columns", 5, table.size());
		for (int i = 0; i < 4; i++) {
			// Ignore column 0, the label column
			final DefaultColumn<String> column = table.get(i + 1);
			assertEquals("A column has wrong number of rows", 4, column.size());
			final String header = column.getHeader();
			assertEquals("A column has an incorrect header", expectedHeaders.get(i),
				header);
			for (int j = 0; j < column.size(); j++) {
				assertEquals(String.valueOf(expectedValues[i][j]), column.get(j));
			}
		}
	}
}
