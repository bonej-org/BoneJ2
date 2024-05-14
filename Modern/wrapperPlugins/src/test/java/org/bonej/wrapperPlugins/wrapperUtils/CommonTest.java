/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.wrapperPlugins.wrapperUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.Result.CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.CLOSED_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

import java.util.stream.IntStream;

import ij.process.LUT;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.legacy.IJ1Helper;
import net.imagej.legacy.LegacyService;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.Context;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.measure.Calibration;

/**
 * Unit tests for the {@link Common} utility class.
 *
 * @author Richard Domander
 */
public class CommonTest {

	private static ImageJ IMAGE_J = new ImageJ();

	@Test
	public void makeFire() {
		final LUT lut = Common.makeFire();

		assertEquals(3 * 256, lut.getBytes().length);
		assertEquals(8, lut.getPixelSize());
	}

	@Test
	public void testToBitTypeImgPlus() throws AssertionError {
		final String unit = "mm";
		final String name = "Test image";
		final double scale = 0.5;
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
		final Img<DoubleType> img = ArrayImgs.doubles(3);
		final ImgPlus<DoubleType> source = new ImgPlus<>(img, name, xAxis);

		final ImgPlus<BitType> result = Common.toBitTypeImgPlus(IMAGE_J.op(),
			source);

		final int dimensions = source.numDimensions();
		assertEquals("Number of dimensions copied incorrectly", dimensions, result
			.numDimensions());
		assertTrue("Dimensions copied incorrectly", IntStream.range(0, dimensions)
			.allMatch(d -> source.dimension(d) == result.dimension(d)));
		assertEquals("Image name was not copied", name, result.getName());
		assertEquals("Axis type was not copied", Axes.X, result.axis(0).type());
		assertEquals("Axis unit was not copied", unit, result.axis(0).unit());
		assertEquals("Axis scale was not copied", scale, result.axis(0)
			.averageScale(0, 1), 1e-12);
	}

	@Test(expected = NullPointerException.class)
	public void testToBitTypeImgPlusThrowsNPEIfOpEnvironmentNull() {
		final Img<DoubleType> img = ArrayImgs.doubles(3, 3);
		final ImgPlus<DoubleType> image = new ImgPlus<>(img);

		Common.toBitTypeImgPlus(null, image);
	}

	@Test(expected = NullPointerException.class)
	public void testToBitTypeImgPlusThrowsNPEIfImageNull() {
		Common.toBitTypeImgPlus(IMAGE_J.op(), null);
	}

	@Test
	@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
	public void testWarnAnisotropyReturnsFalseIfAnisotropicImageAndUserCancels() {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		final Calibration anisotropicCalibration = new Calibration();
		anisotropicCalibration.pixelWidth = 0.5;
		when(imagePlus.getCalibration()).thenReturn(anisotropicCalibration);
		final UIService uiService = mock(UIService.class);
		when(uiService.showDialog(anyString(), any(MessageType.class), any()))
			.thenReturn(CANCEL_OPTION);
		final LogService logService = mock(LogService.class);
		assertFalse(Common.warnAnisotropy(imagePlus, uiService, logService));
		verify(uiService, timeout(1000)).showDialog(anyString(), any(
			MessageType.class), any());
	}

	@Test
	@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
	public void testWarnAnisotropyReturnsFalseIfAnisotropicImageAndUserCloses() {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		final Calibration anisotropicCalibration = new Calibration();
		anisotropicCalibration.pixelWidth = 0.5;
		when(imagePlus.getCalibration()).thenReturn(anisotropicCalibration);
		final UIService uiService = mock(UIService.class);
		when(uiService.showDialog(anyString(), any(MessageType.class), any()))
			.thenReturn(CLOSED_OPTION);

		final LogService logService = mock(LogService.class);
		assertFalse(Common.warnAnisotropy(imagePlus, uiService, logService));
		verify(uiService, timeout(1000)).showDialog(anyString(), any(
			MessageType.class), any());
	}

	@Test
	@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
	public void testWarnAnisotropyReturnsTrueIfAnisotropicImageAndUserOK() {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		final Calibration anisotropicCalibration = new Calibration();
		anisotropicCalibration.pixelWidth = 0.5;
		when(imagePlus.getCalibration()).thenReturn(anisotropicCalibration);
		final UIService uiService = mock(UIService.class);
		when(uiService.showDialog(anyString(), any(MessageType.class), any()))
			.thenReturn(OK_OPTION);

		final LogService logService = mock(LogService.class);
		assertTrue(Common.warnAnisotropy(imagePlus, uiService, logService));
		verify(uiService, timeout(1000)).showDialog(anyString(), any(
			MessageType.class), any());
	}

	@Test
	@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
	public void testWarnAnisotropyReturnsTrueIfIsotropicImage() {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		final Calibration isotropic = new Calibration();
		when(imagePlus.getCalibration()).thenReturn(isotropic);
		final UIService uiService = mock(UIService.class);

		final LogService logService = mock(LogService.class);
		assertTrue(Common.warnAnisotropy(imagePlus, uiService, logService));
	}

	@Test(expected = NullPointerException.class)
	public void testWarnAnisotropyThrowsNPEIfImageNull() {
		Common.warnAnisotropy(null, mock(UIService.class), mock(LogService.class));
	}

	@Test(expected = NullPointerException.class)
	public void testWarnAnisotropyThrowsNPEIfUIServiceNull() {
		Common.warnAnisotropy(mock(ImagePlus.class), null, mock(LogService.class));
	}
	
	@Test(expected = NullPointerException.class)
	public void testWarnAnisotropyThrowsNPEIfLogServiceNull() {
		Common.warnAnisotropy(mock(ImagePlus.class), mock(UIService.class), null);
	}

	@Test
	public void testCancelMacroSafeCancelsNormallyIfNotMacro() {
		final String reason = "No.";
		final IJ1Helper ij1Helper = mock(IJ1Helper.class);
		when(ij1Helper.isMacro()).thenReturn(false);
		final LegacyService legacyService = mock(LegacyService.class);
		when(legacyService.getIJ1Helper()).thenReturn(ij1Helper);
		final Context context = mock(Context.class);
		when(context.getService(LegacyService.class)).thenReturn(legacyService);
		final ContextCommand command = mock(ContextCommand.class);
		when(command.context()).thenReturn(context);

		Common.cancelMacroSafe(command, reason);

		verify(command).cancel(reason);
	}

	@Test
	public void testCancelMacroSafeLogsErrorWhenMacro() {
		final String reason = "No.";
		final String expectedError = "Plugin cancelled: " + reason;
		final IJ1Helper ij1Helper = mock(IJ1Helper.class);
		when(ij1Helper.isMacro()).thenReturn(true);
		final LogService logService = mock(LogService.class);
		final LegacyService legacyService = mock(LegacyService.class);
		when(legacyService.getIJ1Helper()).thenReturn(ij1Helper);
		final Context context = mock(Context.class);
		when(context.getService(LegacyService.class)).thenReturn(legacyService);
		when(context.getService(LogService.class)).thenReturn(logService);
		final ContextCommand command = mock(ContextCommand.class);
		when(command.context()).thenReturn(context);

		Common.cancelMacroSafe(command, reason);

		verify(logService).error(expectedError);
	}


	@Test
	public void testCancelMacroSafeCancelsWithNullWhenMacro() {
		final String reason = "No.";
		final IJ1Helper ij1Helper = mock(IJ1Helper.class);
		when(ij1Helper.isMacro()).thenReturn(true);
		final LogService logService = mock(LogService.class);
		final LegacyService legacyService = mock(LegacyService.class);
		when(legacyService.getIJ1Helper()).thenReturn(ij1Helper);
		final Context context = mock(Context.class);
		when(context.getService(LegacyService.class)).thenReturn(legacyService);
		when(context.getService(LogService.class)).thenReturn(logService);
		final ContextCommand command = mock(ContextCommand.class);
		when(command.context()).thenReturn(context);

		Common.cancelMacroSafe(command, reason);

		verify(command).cancel(null);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
		IMAGE_J = null;
	}
}
