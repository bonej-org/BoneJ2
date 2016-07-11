package org.bonej.wrapperPlugins;

import ij.measure.ResultsTable;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import org.bonej.utilities.ResultsInserter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.bonej.wrapperPlugins.CommonMessages.BAD_CALIBRATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * Tests for the {@link SurfaceFractionWrapper SurfaceFractionWrapper} class
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
    public void testNullImageCancelsPlugin() {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(anyString(), anyString(), any(), any())).thenReturn(mockPrompt);
        IMAGE_J.ui().setDefaultUI(mockUI);

        final Future<CommandModule> future =
                IMAGE_J.command().run(SurfaceFractionWrapper.class, true, "inputImage", null);

        try {
            final CommandModule module = future.get();
            assertTrue("Null image should have canceled the plugin", module.isCanceled());
            assertEquals("Cancel reason is incorrect", CommonMessages.NO_IMAGE_OPEN, module.getCancelReason());
            verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(), any());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2DImageCancelsPlugin() {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(anyString(), anyString(), any(), any())).thenReturn(mockPrompt);
        IMAGE_J.ui().setDefaultUI(mockUI);

        // Create an image with only two spatial dimensions
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 3);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, cAxis);

        final Future<CommandModule> future =
                IMAGE_J.command().run(SurfaceFractionWrapper.class, true, "inputImage", imgPlus);

        try {
            final CommandModule module = future.get();
            assertTrue("2D image should have cancelled the plugin", module.isCanceled());
            assertEquals("Cancel reason is incorrect", CommonMessages.NOT_3D_IMAGE, module.getCancelReason());
            verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(), any());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testNonBinaryImageCancelsPlugin() {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(anyString(), anyString(), any(), any())).thenReturn(mockPrompt);
        IMAGE_J.ui().setDefaultUI(mockUI);

        // Create a test image with more than two colors
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final Img<DoubleType> img = ArrayImgs.doubles(5, 5, 5);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis);
        final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i + 1).iterator();
        imgPlus.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

        final Future<CommandModule> future =
                IMAGE_J.command().run(SurfaceFractionWrapper.class, true, "inputImage", imgPlus);

        try {
            final CommandModule module = future.get();
            assertTrue("An image with more than two colours should have cancelled the plugin", module.isCanceled());
            assertEquals("Cancel reason is incorrect", CommonMessages.NOT_BINARY, module.getCancelReason());
            verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(), any());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBadCalibrationShowsWarning() {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(WARNING_MESSAGE), any())).thenReturn(mockPrompt);
        IMAGE_J.ui().setDefaultUI(mockUI);

        // Create an hyperstack with bad calibration (units don't match)
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "µm");
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<DoubleType> img = ArrayImgs.doubles(5, 5, 5, 2);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, tAxis);

        final Future<CommandModule> future =
                IMAGE_J.command().run(SurfaceFractionWrapper.class, true, "inputImage", imgPlus);

        try {
            future.get();
            // Warning should be shown only once
            verify(mockUI, after(100).times(1))
                    .dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(WARNING_MESSAGE), any());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /** Test that SurfaceFractionWrapper processes hyperstacks and presents results correctly */
    @Test
    public void testResults() {
        // Marching cubes creates an octahedral surface out of a single voxel
        final double expectedVolume = Math.sqrt(0.5) * Math.sqrt(0.5) * 0.5 / 3.0 * 2.0;
        final String unit = "mm";
        final double[][] expectedValues = {
                {0.0, 0.0, expectedVolume, 0.0},
                {0.0, expectedVolume, expectedVolume, 1.0},
                {0.0, expectedVolume, expectedVolume, 1.0},
                {0.0, 0.0, expectedVolume, 0.0}
        };

        /*
         * Create a hyperstack with two channels and two frames.
         * Two of the 3D subspaces are empty, and two of them contain a single voxel
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

        final Future<CommandModule> future =
                IMAGE_J.command().run(SurfaceFractionWrapper.class, true, "inputImage", imgPlus);

        try {
            future.get();
            final ResultsTable resultsTable = ResultsInserter.getInstance().getResultsTable();
            final String[] headings = resultsTable.getHeadings();

            // Assert table size
            assertEquals("Wrong number of columns", 4, headings.length);
            assertEquals("Wrong number of rows", expectedValues.length, resultsTable.size());

            // Assert column headers
            assertEquals("Column header is incorrect", "Bone volume (" + unit + "³)", headings[1]);
            assertEquals("Column header is incorrect", "Total volume (" + unit + "³)", headings[2]);
            assertEquals("Column header is incorrect", "Volume ratio", headings[3]);

            // Assert results
            for (int row = 0; row < expectedValues.length; row++) {
                for (int column = 1; column < headings.length; column++) {
                    final double value = resultsTable.getValue(headings[column], row);
                    assertEquals("Incorrect result", expectedValues[row][column], value, 1e-12);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}