
package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;

import org.bonej.utilities.ResultsInserter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ij.measure.ResultsTable;

/**
 * Tests for the {@link SurfaceFractionWrapper} class
 *
 * @author Richard Domander
 */
public class SurfaceFractionWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@BeforeClass
	public static void oneTimeSetup() {
		ResultsInserter.getInstance().setHeadless(true);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@After
	public void tearDown() {
		ResultsInserter.getInstance().getResultsTable().reset();
	}

	@Test
	public void testNullImageCancelsSurfaceFraction() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			SurfaceFractionWrapper.class);
	}

	@Test
	public void testNonBinaryImageCancelsSurfaceFraction() throws Exception {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
			SurfaceFractionWrapper.class);
	}

	@Test
	public void testNoCalibrationShowsWarning() throws Exception {
		CommonWrapperTests.testNoCalibrationShowsWarning(IMAGE_J,
			SurfaceFractionWrapper.class);
	}

	@Test
	public void test2DImageCancelsConnectivity() throws Exception {
		CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J,
			ConnectivityWrapper.class);
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
		final double[][] expectedValues = { { 0.0, 0.0, totalVolume, 0.0 }, { 0.0,
			cubeVolume, totalVolume, ratio }, { 0.0, cubeVolume, totalVolume, ratio },
			{ 0.0, 0.0, totalVolume, 0.0 } };
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
		IMAGE_J.command().run(SurfaceFractionWrapper.class, true, "inputImage",
			imgPlus).get();

		// VERIFY
		final ResultsTable resultsTable = ResultsInserter.getInstance()
			.getResultsTable();
		final String[] headings = resultsTable.getHeadings();
		// Assert table size
		assertEquals("Wrong number of columns", 4, headings.length);
		assertEquals("Wrong number of rows", expectedValues.length, resultsTable
			.size());
		// Assert column headers
		assertEquals("Column header is incorrect", "Bone volume (" + unit + "³)",
			headings[1]);
		assertEquals("Column header is incorrect", "Total volume (" + unit + "³)",
			headings[2]);
		assertEquals("Column header is incorrect", "Volume ratio", headings[3]);
		// Assert results
		for (int row = 0; row < expectedValues.length; row++) {
			for (int column = 1; column < headings.length; column++) {
				final double value = resultsTable.getValue(headings[column], row);
				assertEquals("Incorrect result", expectedValues[row][column], value,
					1e-12);
			}
		}
	}
}
