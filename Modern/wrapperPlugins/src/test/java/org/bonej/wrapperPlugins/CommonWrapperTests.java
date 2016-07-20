package org.bonej.wrapperPlugins;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

import java.util.Iterator;
import java.util.stream.IntStream;

import static org.bonej.wrapperPlugins.CommonMessages.*;
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
 * Common tests for wrapper plugins
 *
 * @author Richard Domander 
 */
public class CommonWrapperTests {
    public static <C extends Command> void testNullImageCancelsPlugin(final ImageJ imageJ, final Class<C> commandClass)
            throws Exception {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(anyString(), anyString(), any(), any())).thenReturn(mockPrompt);
        imageJ.ui().setDefaultUI(mockUI);

        // Run command
        final CommandModule module = imageJ.command().run(commandClass, true, "inputImage", null).get();

        assertTrue("Null image should have canceled the plugin", module.isCanceled());
        assertEquals("Cancel reason is incorrect", CommonMessages.NO_IMAGE_OPEN, module.getCancelReason());
        verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(), any());
    }

    public static <C extends Command> void test2DImageCancelsPlugin(final ImageJ imageJ, final Class<C> commandClass)
            throws Exception {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(anyString(), anyString(), any(), any())).thenReturn(mockPrompt);
        imageJ.ui().setDefaultUI(mockUI);

        // Create an image with only two spatial dimensions
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 3);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, cAxis);

        // Run command
        final CommandModule module = imageJ.command().run(commandClass, true, "inputImage", imgPlus).get();

        assertTrue("2D image should have cancelled the plugin", module.isCanceled());
        assertEquals("Cancel reason is incorrect", CommonMessages.NOT_3D_IMAGE, module.getCancelReason());
        verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(), any());
    }

    public static <C extends Command> void testNonBinaryImageCancelsPlugin(final ImageJ imageJ,
            final Class<C> commandClass) throws Exception {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(anyString(), anyString(), any(), any())).thenReturn(mockPrompt);
        imageJ.ui().setDefaultUI(mockUI);

        // Create a test image with more than two colors
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final Img<DoubleType> img = ArrayImgs.doubles(5, 5, 5);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis);
        final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i + 1).iterator();
        imgPlus.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

        // Run command
        final CommandModule module = imageJ.command().run(commandClass, true, "inputImage", imgPlus).get();

        assertTrue("An image with more than two colours should have cancelled the plugin", module.isCanceled());
        assertEquals("Cancel reason is incorrect", CommonMessages.NOT_BINARY, module.getCancelReason());
        verify(mockUI, after(100)).dialogPrompt(anyString(), anyString(), any(), any());
    }

    public static <C extends Command> void testNoCalibrationShowsWarning(final ImageJ imageJ,
            final Class<C> commandClass) throws Exception {
        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(WARNING_MESSAGE), any())).thenReturn(mockPrompt);
        imageJ.ui().setDefaultUI(mockUI);

        // Create an hyperstack with no calibration
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<DoubleType> img = ArrayImgs.doubles(5, 5, 5, 2);
        final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, tAxis);

        // Run command
        imageJ.command().run(commandClass, true, "inputImage", imgPlus).get();

        verify(mockUI, after(100).times(1)).dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(WARNING_MESSAGE), any());
    }
}
