
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.BAD_CALIBRATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.IntStream;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.experimental.categories.Category;
import org.scijava.Gateway;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;
import ij.process.ImageStatistics;

/**
 * Common tests for wrapper plugins
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public final class CommonWrapperTests {

	static <C extends Command> void testNullImageCancelsPlugin(
			final Gateway imageJ, final Class<C> commandClass) throws Exception
	{
		// SETUP
		final UserInterface mockUI = mockUIService(imageJ);

		// EXECUTE
		final CommandModule module = imageJ.command().run(commandClass, true,
			"inputImage", null).get();

		// VERIFY
		assertTrue("Null image should have canceled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", CommonMessages.NO_IMAGE_OPEN,
			module.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	static <C extends Command> void test2DImageCancelsPlugin(
			final Gateway imageJ, final Class<C> commandClass) throws Exception
	{
		// SETUP
		final UserInterface mockUI = mockUIService(imageJ);
		// Create an image with only two spatial dimensions
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 3);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, cAxis);

		// EXECUTE
		final CommandModule module = imageJ.command().run(commandClass, true,
			"inputImage", imgPlus).get();

		// VERIFY
		assertTrue("2D image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", CommonMessages.NOT_3D_IMAGE,
			module.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	static <C extends Command> void testNonBinaryImageCancelsPlugin(
			final Gateway imageJ, final Class<C> commandClass) throws Exception
	{
		// SETUP
		final UserInterface mockUI = mockUIService(imageJ);
		// Create a test image with more than two colors
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
		final Img<DoubleType> img = ArrayImgs.doubles(5, 5, 5);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis);
		final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i + 1)
			.iterator();
		imgPlus.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

		// EXECUTE
		final CommandModule module = imageJ.command().run(commandClass, true,
			"inputImage", imgPlus).get();

		// VERIFY
		assertTrue(
			"An image with more than two colours should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect", CommonMessages.NOT_BINARY, module
			.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	static <C extends Command> void testNoCalibrationShowsWarning(
			final Gateway imageJ, final Class<C> commandClass,
			final Object... additionalInputs) throws Exception
	{
		// SETUP
		// Mock UI
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockUI.dialogPrompt(eq(BAD_CALIBRATION), anyString(), eq(
			WARNING_MESSAGE), any())).thenReturn(mockPrompt);
		imageJ.ui().setDefaultUI(mockUI);
		// Create an hyperstack with no calibration
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<DoubleType> img = ArrayImgs.doubles(5, 5, 5, 2);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis, tAxis);
		final Collection<Object> inputs = new ArrayList<>();
		inputs.add("inputImage");
		inputs.add(imgPlus);
		Collections.addAll(inputs, additionalInputs);

		// EXECUTE
		imageJ.command().run(commandClass, true, inputs.toArray()).get();

		// VERIFY
		verify(mockUI, timeout(1000).times(1)).dialogPrompt(eq(BAD_CALIBRATION),
			anyString(), eq(WARNING_MESSAGE), any());
	}

	static <C extends Command> void testNonBinaryImagePlusCancelsPlugin(
			final Gateway imageJ, final Class<C> commandClass) throws Exception
	{
		// SETUP
		final UserInterface mockUI = mockUIService(imageJ);
		final ImagePlus nonBinaryImage = mock(ImagePlus.class);
		final ImageStatistics stats = new ImageStatistics();
		stats.pixelCount = 3;
		stats.histogram = new int[256];
		stats.histogram[0x00] = 1;
		stats.histogram[0x01] = 1;
		stats.histogram[0xFF] = 1;
		when(nonBinaryImage.getStatistics()).thenReturn(stats);
		when(nonBinaryImage.getNSlices()).thenReturn(2);

		// EXECUTE
		final CommandModule module = imageJ.command().run(commandClass, true,
			"inputImage", nonBinaryImage).get();

		// VERIFY
		assertTrue(
			"An image with more than two colours should have cancelled the plugin",
			module.isCanceled());
		assertEquals("Cancel reason is incorrect",
			CommonMessages.NOT_8_BIT_BINARY_IMAGE, module.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	static <C extends Command> void test2DImagePlusCancelsPlugin(
			final Gateway imageJ, final Class<C> commandClass) throws Exception
	{
		// SETUP
		final UserInterface mockUI = mockUIService(imageJ);
		final ImagePlus image = mock(ImagePlus.class);
		when(image.getNSlices()).thenReturn(1);

		// EXECUTE
		final CommandModule module = imageJ.command().run(commandClass, true,
			"inputImage", image).get();

		// VERIFY
		assertTrue("2D image should have cancelled the plugin", module
			.isCanceled());
		assertEquals("Cancel reason is incorrect", CommonMessages.NOT_3D_IMAGE,
			module.getCancelReason());
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
	}

	static UserInterface mockUIService(final Gateway imageJ) {
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockUI.dialogPrompt(any(), anyString(), any(), any())).thenReturn(
			mockPrompt);
		imageJ.ui().setDefaultUI(mockUI);
		return mockUI;
	}

	/**
	 * Tests that running the given command with an anisotropic {@link ImagePlus}
	 * shows a warning dialog that can be used to cancel the plugin
	 */
	static <C extends Command> void testAnisotropyWarning(
			final Gateway imageJ, final Class<C> commandClass) throws Exception
	{
		// SETUP
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockPrompt.prompt()).thenReturn(Result.CANCEL_OPTION);
		when(mockUI.dialogPrompt(startsWith("The image is anisotropic"),
				anyString(), eq(WARNING_MESSAGE), any())).thenReturn(mockPrompt);
		imageJ.ui().setDefaultUI(mockUI);
		final Calibration calibration = new Calibration();
		calibration.pixelWidth = 300;
		calibration.pixelHeight = 1;
		calibration.pixelDepth = 1;
		final ImagePlus imagePlus = NewImage.createByteImage("", 5, 5, 5, 1);
		imagePlus.setCalibration(calibration);

		// EXECUTE
		final CommandModule module = imageJ.command().run(commandClass, true,
				"inputImage", imagePlus).get();

		// VERIFY
		verify(mockUI, timeout(1000).times(1)).dialogPrompt(startsWith(
				"The image is anisotropic"), anyString(), eq(WARNING_MESSAGE), any());
		assertTrue("Pressing cancel on warning dialog should have cancelled plugin",
				module.isCanceled());
	}
}
