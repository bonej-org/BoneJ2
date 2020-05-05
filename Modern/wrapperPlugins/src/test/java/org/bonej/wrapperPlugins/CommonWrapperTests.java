/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.wrapperPlugins;

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

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.Assert;
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

	public static <C extends Command> void testNullImageCancelsPlugin(final Gateway imageJ,
																	  final Class<C> commandClass) {
		// SETUP
		final UserInterface oldUI = imageJ.ui().getDefaultUI();
		final UserInterface mockUI = mockUIDialogPrompt(imageJ);

		try {
			// EXECUTE
			final CommandModule module =
					imageJ.command().run(commandClass, true, "inputImage", null).get();

			// VERIFY
			assertTrue("Null image should have canceled the plugin", module.isCanceled());
			assertEquals("Cancel reason is incorrect", CommonMessages.NO_IMAGE_OPEN,
					module.getCancelReason());
			verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(), any());
		} catch (InterruptedException | ExecutionException e) {
			Assert.fail("Test timed out");
		} finally {
			imageJ.ui().setDefaultUI(oldUI);
		}
	}

	public static <C extends Command> void test2DImageCancelsPlugin(final Gateway imageJ,
																	final Class<C> commandClass) {
		// SETUP
		final UserInterface oldUI = imageJ.ui().getDefaultUI();
		final UserInterface mockUI = mockUIDialogPrompt(imageJ);
		// Create an image with only two spatial dimensions
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 3);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, cAxis);

		try {
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
		} catch (InterruptedException | ExecutionException e) {
			Assert.fail("Test timed out");
		} finally {
			imageJ.ui().setDefaultUI(oldUI);
		}
	}

	public static <C extends Command> void testNonBinaryImageCancelsPlugin(
			final Gateway imageJ, final Class<C> commandClass) {
		// SETUP
		final UserInterface oldUI = imageJ.ui().getDefaultUI();
		final UserInterface mockUI = mockUIDialogPrompt(imageJ);
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

		try {
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
		} catch (InterruptedException | ExecutionException e) {
			Assert.fail("Test timed out");
		} finally {
			imageJ.ui().setDefaultUI(oldUI);
		}
	}

	static <C extends Command> void testNonBinaryImagePlusCancelsPlugin(
		final Gateway imageJ, final Class<C> commandClass) {
		// SETUP
		final UserInterface oldUI = imageJ.ui().getDefaultUI();
		final UserInterface mockUI = mockUIDialogPrompt(imageJ);
		final ImagePlus nonBinaryImage = mock(ImagePlus.class);
		final ImageStatistics stats = new ImageStatistics();
		stats.pixelCount = 3;
		stats.histogram = new int[256];
		stats.histogram[0x00] = 1;
		stats.histogram[0x01] = 1;
		stats.histogram[0xFF] = 1;
		when(nonBinaryImage.getStatistics()).thenReturn(stats);
		when(nonBinaryImage.getNSlices()).thenReturn(2);

		try {
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
		} catch (InterruptedException | ExecutionException e) {
			Assert.fail("Test timed out");
		} finally {
			imageJ.ui().setDefaultUI(oldUI);
		}
	}

	static <C extends Command> void test2DImagePlusCancelsPlugin(
		final Gateway imageJ, final Class<C> commandClass) {
		// SETUP
		final UserInterface oldUI = imageJ.ui().getDefaultUI();
		final UserInterface mockUI = mockUIDialogPrompt(imageJ);
		final ImagePlus image = mock(ImagePlus.class);
		when(image.getNSlices()).thenReturn(1);

		try {
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
		} catch (InterruptedException | ExecutionException e) {
			Assert.fail("Test timed out");
		} finally {
			imageJ.ui().setDefaultUI(oldUI);
		}
	}

	private static UserInterface mockUIDialogPrompt(final Gateway imageJ) {
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
	static <C extends Command> void testAnisotropyWarning(final Gateway imageJ,
		final Class<C> commandClass) {
		// SETUP
		final UserInterface oldUI = imageJ.ui().getDefaultUI();
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

		try {
			// EXECUTE
			final CommandModule module = imageJ.command().run(commandClass, true,
					"inputImage", imagePlus).get();

			// VERIFY
			verify(mockUI, timeout(1000).times(1)).dialogPrompt(startsWith(
					"The image is anisotropic"), anyString(), eq(WARNING_MESSAGE), any());
			assertTrue("Pressing cancel on warning dialog should have cancelled plugin",
					module.isCanceled());
		} catch (InterruptedException | ExecutionException e) {
			Assert.fail("Test timed out");
		} finally {
			imageJ.ui().setDefaultUI(oldUI);
		}
	}
}
