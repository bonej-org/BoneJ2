
package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.table.DefaultColumn;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.Gateway;
import org.scijava.command.CommandModule;

/**
 * Tests for the {@link SurfaceFractionWrapper} class
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class SurfaceFractionWrapperTest {

	private static final Gateway IMAGE_J = new ImageJ();

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@Test
	public void test2DImageCancelsConnectivity() throws Exception {
		CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J,
			ConnectivityWrapper.class);
	}

	@Test
	public void testNoCalibrationShowsWarning() throws Exception {
		CommonWrapperTests.testNoCalibrationShowsWarning(IMAGE_J,
			SurfaceFractionWrapper.class);
	}

	@Test
	public void testNonBinaryImageCancelsSurfaceFraction() throws Exception {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
			SurfaceFractionWrapper.class);
	}

	@Test
	public void testNullImageCancelsSurfaceFraction() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			SurfaceFractionWrapper.class);
	}

	/**
	 * Test that SurfaceFractionWrapper processes hyperstacks and presents results
	 * correctly
	 */
	@Test
	public void testResults() throws Exception {
		// SETUP
		final int cubeDepth = 3;
		final int cubeHeight = 3;
		final int cubeWidth = 3;
		final long imgWidth = 5;
		final long imgHeight = 5;
		final long imgDepth = 5;
		// Marching cubes creates meshes that are effectively one voxel smaller
		final String unit = "mm";
		final double scale = 0.25;
		final double scaleCubed = scale * scale * scale;
		final double cubeVolume = ((cubeWidth - 1) * (cubeHeight - 1) * (cubeDepth -
			1)) * scaleCubed;
		final double totalVolume = ((imgWidth - 1) * (imgHeight - 1) * (imgDepth -
			1)) * scaleCubed;
		final double ratio = cubeVolume / totalVolume;
		final String[] expectedHeaders = { "Bone volume (" + unit + "³)",
			"Total volume (" + unit + "³)", "Volume ratio" };
		final double[][] expectedValues = { { 0.0, cubeVolume, cubeVolume, 0.0 }, {
			totalVolume, totalVolume, totalVolume, totalVolume }, { 0.0, ratio, ratio,
				0.0 } };
		/*
		 * Create a hyperstack with two channels and two frames.
		 * Two of the 5x5x5 3D subspaces are empty, and two of them contain a 3x3x3 cube
		 * The cuboids have one voxel of empty space around them
		 */
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, scale);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit, scale);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<BitType> img = ArrayImgs.bits(imgWidth, imgHeight, imgDepth, 2,
			2);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis, cAxis, tAxis);
		final RandomAccess<BitType> access = imgPlus.randomAccess();
		for (int z = 1; z <= cubeDepth; z++) {
			for (int y = 1; y <= cubeHeight; y++) {
				for (int x = 1; x <= cubeWidth; x++) {
					// Add a voxel to Channel 1, Frame 0
					access.setPosition(new long[] { x, y, z, 1, 0 });
					access.get().setOne();
					// Add a voxel to Channel 0, Frame 1
					access.setPosition(new long[] { x, y, z, 0, 1 });
					access.get().setOne();
				}
			}
		}

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			SurfaceFractionWrapper.class, true, "inputImage", imgPlus).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<String>> table =
			(List<DefaultColumn<String>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Wrong number of columns", 4, table.size());
		// Assert results
		for (int i = 0; i < 3; i++) {
			// Skip Label column
			final DefaultColumn<String> column = table.get(i + 1);
			assertEquals("Wrong number of rows", 4, column.size());
			assertEquals("Column has incorrect header", expectedHeaders[i], column
				.getHeader());
			for (int j = 0; j < expectedValues.length; j++) {
				assertEquals("Incorrect value in table", expectedValues[i][j], Double
					.parseDouble(column.get(j)), 1e-12);
			}
		}
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
