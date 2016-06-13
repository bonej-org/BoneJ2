package org.bonej.wrapperPlugins;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;
import org.bonej.utilities.ResultsInserter;
import org.junit.AfterClass;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * Unit tests for the {@link ConnectivityWrapper ConnectivityWrapper} plugin
 *
 * @author Richard Domander 
 */
public class ConnectivityWrapperTest {
    private static final ImageJ IMAGE_J = new ImageJ();

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

        // Create a 2D image
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{10, 10});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis);

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
    public void testBadCalibrationShowsWarning() {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(WARNING_MESSAGE), any())).thenReturn(mockPrompt);
        when(mockPrompt.prompt()).thenReturn(DialogPrompt.Result.YES_OPTION);
        IMAGE_J.ui().setDefaultUI(mockUI);
        ResultsInserter.getInstance().setHeadless(true);

        // Create an image with bad calibration (units don't match)
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "µm");
        final Img<DoubleType> img = IMAGE_J.op().create().img(new int[]{5, 5, 5});
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis);

        final Future<CommandModule> future =
                IMAGE_J.command().run(ConnectivityWrapper.class, true, "inputImage", imgPlus);

        try {
            future.get();
            verify(mockUI, after(100)).dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(WARNING_MESSAGE), any());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}