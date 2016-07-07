package org.bonej.wrapperPlugins;

import ij.measure.ResultsTable;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import org.bonej.testImages.Cuboid;
import org.bonej.utilities.ResultsInserter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.bonej.wrapperPlugins.CommonMessages.BAD_CALIBRATION;
import static org.bonej.wrapperPlugins.ConnectivityWrapper.NEGATIVE_CONNECTIVITY;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * Integration / Regression tests for the {@link ConnectivityWrapper ConnectivityWrapper} plugin
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
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10, 3});
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
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{5, 5, 5});
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
        final Img<BitType> img = IMAGE_J.op().create().img(new FinalDimensions(5, 5, 5, 2), new BitType());
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
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{5, 5, 5, 2});
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
        final double scale = 0.9;
        final int elements = 5;
        final int spaceSize = elements * elements * elements;
        final double elementSize = scale * scale * scale;
        final ImgPlus<BitType> imgPlus =
                (ImgPlus<BitType>) IMAGE_J.op()
                        .run(Cuboid.class, null, elements, elements, elements, 1, 1, 0, scale, unit);

        final Future<CommandModule> future =
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", imgPlus);

        try {
            future.get();
            final ResultsTable resultsTable = ResultsInserter.getInstance().getResultsTable();
            final String[] headings = resultsTable.getHeadings();
            assertEquals("Results table has wrong number of rows", 1, resultsTable.size());

            final double eulerCharacteristic = resultsTable.getValue(headings[1], 0);
            assertEquals("Results table has incorrect heading", "Euler char. (χ)", headings[1]);
            assertEquals("The reported χ is incorrect", 1.0, eulerCharacteristic, 1e-12);

            final double correctedEuler = resultsTable.getValue(headings[2], 0);
            assertEquals("Results table has incorrect heading", "Corrected Euler (Δχ)", headings[2]);
            assertEquals("The reported Δχ is incorrect", 0.0, correctedEuler, 1e-12);

            final double connectivity = resultsTable.getValue(headings[3], 0);
            assertEquals("Results table has incorrect heading", "Connectivity", headings[3]);
            assertEquals("The reported connectivity is incorrect", 1.0, connectivity, 1e-12);

            final double expectedConnDensity = connectivity / (spaceSize * elementSize);
            final double connDensity = resultsTable.getValue(headings[4], 0);
            assertEquals(
                    "Results table has incorrect heading",
                    String.format("Conn. density (%s³)", unit),
                    headings[4]);
            assertEquals("The reported connectivity density is incorrect", expectedConnDensity, connDensity, 1e-12);

            assertFalse("Results table should not have empty cells", hasEmptyCells(resultsTable));
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testResultsTableHasNoUnitsWhenImageUncalibrated() {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(WARNING_MESSAGE), any())).thenReturn(mockPrompt);
        when(mockPrompt.prompt()).thenReturn(DialogPrompt.Result.YES_OPTION);
        IMAGE_J.ui().setDefaultUI(mockUI);

        // Create an uncalibrated test image
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{5, 5, 5});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis);

        final Future<CommandModule> future =
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", imgPlus);

        try {
            future.get();
            final String[] headings = ResultsInserter.getInstance().getResultsTable().getHeadings();
            assertEquals("Results table has incorrect heading", "Conn. density ", headings[4]);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private boolean hasEmptyCells(final ResultsTable resultsTable) {
        final int columns = resultsTable.getLastColumn();
        final int rows = resultsTable.size();

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                final double valueAsDouble = resultsTable.getValueAsDouble(column, row);
                final String stringValue = resultsTable.getStringValue(column, row);
                if (Double.isNaN(valueAsDouble) && !ResultsInserter.NAN_VALUE.equals(stringValue)) {
                    return true;
                }
            }
        }

        return false;
    }
}