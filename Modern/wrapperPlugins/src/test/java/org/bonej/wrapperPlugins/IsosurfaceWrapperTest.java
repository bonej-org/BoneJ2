package org.bonej.wrapperPlugins;

import ij.measure.ResultsTable;
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

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link IsosurfaceWrapper}
 *
 * @author Richard Domander 
 */
public class IsosurfaceWrapperTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @BeforeClass
    public static void oneTimeSetup() {
        ResultsInserter.getInstance().setHeadless(true);
    }

    @After
    public void tearDown() {
        ResultsInserter.getInstance().getResultsTable().reset();
    }

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testNullImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void test2DImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void testNonBinaryImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void testNoCalibrationShowsWarning() throws Exception {
        CommonWrapperTests.testNoCalibrationShowsWarning(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void testResults() throws Exception {
        // Marching cubes creates an octahedron out of a unit cube
        // Calculate the length of the side of the octahedron with Pythagoras' theorem
        final double side = Math.sqrt(0.5 * 0.5 + 0.5 * 0.5);
        final double height = 0.5;
        final double pyramidFaces = 2 * side * Math.sqrt(side * side / 4.0 + height * height);
        final double expectedArea = pyramidFaces * 2;
        final String unit = "mm";
        final double[] expectedValues = {0, expectedArea, expectedArea, 0};

        /*
         * Create a hyperstack with two channels and two frames.
         * Two of the 3D subspaces are empty, and two of them contain a unit cube (single voxel)
         */
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<BitType> img = ArrayImgs.bits(1, 1, 1, 2, 2);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, cAxis, tAxis);
        final RandomAccess<BitType> access = imgPlus.randomAccess();
        // Add a voxel to Channel 1, Frame 0
        access.setPosition(new long[]{0, 0, 0, 1, 0});
        access.get().setOne();
        // Add a voxel to Channel 0, Frame 1
        access.setPosition(new long[]{0, 0, 0, 0, 1});
        access.get().setOne();

        // Run command and get results
        IMAGE_J.command().run(IsosurfaceWrapper.class, true, "inputImage", imgPlus).get();
        final ResultsTable resultsTable = ResultsInserter.getInstance().getResultsTable();
        final String[] headings = resultsTable.getHeadings();

        // Assert table size
        assertEquals("Wrong number of columns", 2, headings.length);
        assertEquals("Wrong number of rows", expectedValues.length, resultsTable.size());

        // Assert column headers
        assertEquals("Column header is incorrect", "Surface area (" + unit + "²)", headings[1]);

        // Assert results
        for (int row = 0; row < expectedValues.length; row++) {
            for (int column = 1; column < headings.length; column++) {
                final double value = resultsTable.getValue(headings[column], row);
                assertEquals("Incorrect surface area", expectedValues[row], value, 1e-12);
            }
        }
    }
}