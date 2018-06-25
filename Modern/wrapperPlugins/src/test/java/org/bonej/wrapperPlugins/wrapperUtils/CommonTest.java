
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

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
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

	private static final ImageJ IMAGE_J = new ImageJ();

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	// Test ignored, because it fails when executed by Maven (but only then!)
	@Ignore
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

		assertTrue(Common.warnAnisotropy(imagePlus, uiService));
        verify(uiService, timeout(1000)).showDialog(anyString(), any(
                MessageType.class), any());
	}

	@Test
	public void testWarnAnisotropyReturnsFalseIfAnisotropicImageAndUserCancels() {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		final Calibration anisotropicCalibration = new Calibration();
		anisotropicCalibration.pixelWidth = 0.5;
		when(imagePlus.getCalibration()).thenReturn(anisotropicCalibration);
		final UIService uiService = mock(UIService.class);
		when(uiService.showDialog(anyString(), any(MessageType.class), any()))
			.thenReturn(CANCEL_OPTION);

        assertFalse(Common.warnAnisotropy(imagePlus, uiService));
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

        assertFalse(Common.warnAnisotropy(imagePlus, uiService));
        verify(uiService, timeout(1000)).showDialog(anyString(), any(
                MessageType.class), any());
	}

	@Test
    @Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
	public void testWarnAnisotropyReturnsTrueIfIsotropicImage() {
		final ImagePlus imagePlus = mock(ImagePlus.class);
		final Calibration calibration = new Calibration();
		when(imagePlus.getCalibration()).thenReturn(calibration);
		final UIService uiService = mock(UIService.class);

		assertTrue(Common.warnAnisotropy(imagePlus, uiService));
	}
}
