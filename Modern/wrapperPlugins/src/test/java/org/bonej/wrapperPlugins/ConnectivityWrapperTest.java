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
import static org.bonej.wrapperPlugins.ConnectivityWrapper.NEGATIVE_CONNECTIVITY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * Integration / Regression tests for the {@link ConnectivityWrapper} plugin
 *
 * @author Richard Domander
 */
public class ConnectivityWrapperTest {
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
    public void testNullImageCancelsPlugin() {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(anyString(), anyString(), any(), any())).thenReturn(mockPrompt);
        IMAGE_J.ui().setDefaultUI(mockUI);

        final Future<CommandModule> future =
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", null);

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
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", imgPlus);

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
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", imgPlus);

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
    public void testNegativeConnectivityShowsInfoDialog() {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(eq(NEGATIVE_CONNECTIVITY), anyString(), eq(INFORMATION_MESSAGE), any()))
                .thenReturn(mockPrompt);
        IMAGE_J.ui().setDefaultUI(mockUI);

        // Create a 3D hyperstack with two channels. Each channel has two particles
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm");
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final Img<BitType> img =  ArrayImgs.bits(5, 5, 5, 2);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, cAxis);
        final RandomAccess<BitType> access = imgPlus.randomAccess();
        // Channel 0
        access.setPosition(new long[]{1, 1, 1, 0});
        access.get().setOne();
        access.setPosition(new long[]{3, 3, 3, 0});
        access.get().setOne();
        // Channel 1
        access.setPosition(new long[]{1, 1, 1, 1});
        access.get().setOne();
        access.setPosition(new long[]{3, 3, 3, 1});
        access.get().setOne();

        final Future<CommandModule> future =
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", imgPlus);
        try {
            future.get();
            // Dialog should only be shown once
            verify(mockUI, after(100).times(1))
                    .dialogPrompt(eq(NEGATIVE_CONNECTIVITY), anyString(), eq(INFORMATION_MESSAGE), any());
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
        final Img<DoubleType> img =  ArrayImgs.doubles(5, 5, 5, 2);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, tAxis);

        final Future<CommandModule> future =
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", imgPlus);

        try {
            future.get();
            // Warning should be shown only once
            verify(mockUI, after(100).times(1))
                    .dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(WARNING_MESSAGE), any());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testResults() {
        // Create an test image of a cuboid
        final String unit = "mm";
        final long size = 3;
        final double scale = 0.9;
        final long spaceSize = size * size * size;
        final double elementSize = scale * scale * scale;
        final double expectedDensity = 1.0 / (spaceSize * elementSize);
        final double[][] expectedValues = {
                {0.0, 0.0, 1.0, expectedDensity},
                {1.0, 1.0, 0.0, 0.0},
                {1.0, 1.0, 0.0, 0.0},
                {0.0, 0.0, 1.0, expectedDensity}
        };

        /*
         * Create a hyperstack with two channels and two frames.
         * Two of the 3D subspaces are empty, and two of them contain a single voxel
         */
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, scale);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit, scale);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<BitType> img = ArrayImgs.bits(size, size, size, 2, 2);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, cAxis, tAxis);
        final RandomAccess<BitType> access = imgPlus.randomAccess();
        // Add a voxel to Channel 1, Frame 0
        access.setPosition(new long[]{1, 1, 1, 1, 0});
        access.get().setOne();
        // Add a voxel to Channel 0, Frame 1
        access.setPosition(new long[]{1, 1, 1, 0, 1});
        access.get().setOne();

        final Future<CommandModule> future =
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", imgPlus);

        try {
            future.get();
            final ResultsTable resultsTable = ResultsInserter.getInstance().getResultsTable();
            final String[] headings = resultsTable.getHeadings();
            
            // Assert results table size
            assertEquals("Results table has wrong number of rows", expectedValues.length, resultsTable.size());
            assertEquals("Results table has wrong number of headings", 5, headings.length);
            
            // Assert column headings
            assertEquals("Incorrect heading in results table", "Euler char. (χ)", headings[1]);
            assertEquals("Incorrect heading in results table", "Corrected Euler (Δχ)", headings[2]);
            assertEquals("Incorrect heading in results table", "Connectivity", headings[3]);
            assertEquals("Incorrect heading in results table", String.format("Conn. density (%s³)", unit), headings[4]);
            
            // Assert values
            for (int row = 0; row < resultsTable.size(); row++) {
                final double eulerCharacteristic = resultsTable.getValue(headings[1], row);
                assertEquals("χ is incorrect", expectedValues[row][0], eulerCharacteristic, 1e-12);
                final double correctedEuler = resultsTable.getValue(headings[2], row);
                assertEquals("Corrected χ is incorrect", expectedValues[row][1], correctedEuler, 1e-12);
                final double connectivity = resultsTable.getValue(headings[3], row);
                assertEquals("Connectivity is incorrect", expectedValues[row][2], connectivity, 1e-12);
                final double connDensity = resultsTable.getValue(headings[4], row);
                assertEquals("Connectivity density is incorrect", expectedValues[row][3], connDensity, 1e-12);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}