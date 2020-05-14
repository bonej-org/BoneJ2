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

package org.bonej.wrapperPlugins.wrapperUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.Axis;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.units.UnitService;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.Contextual;

import ij.ImagePlus;
import ij.measure.Calibration;

/**
 * Unit tests for the {@link ResultUtils ResultUtils} class.
 *
 * @author Richard Domander
 */
public class ResultUtilsTest {

	private static Contextual IMAGE_J = new ImageJ();
	private static final UnitService unitService = IMAGE_J.context().getService(
		UnitService.class);

	@Test
	public void testGetExponent() {
		final char[] expected = { '\u0000', '\u00B2', '\u00B3', '\u2074', '\u2075',
			'\u2076', '\u2077', '\u2078', '\u2079', '\u0000' };
		final AxisType spatialAxis = Axes.get("Spatial", true);
		final CalibratedAxis axis = new DefaultLinearAxis(spatialAxis);
		final ImgPlus<?> mockImage = mock(ImgPlus.class);
		when(mockImage.axis(anyInt())).thenReturn(axis);

		for (int i = 0; i < expected.length; i++) {
			final int dimensions = i + 1;
			when(mockImage.numDimensions()).thenReturn(dimensions);

			final char exponent = ResultUtils.getExponent(mockImage);

			assertEquals("Wrong exponent character", expected[i], exponent);
		}
	}

	@Test(expected = NullPointerException.class)
	public void testGetExponentThrowsNPEIfSpaceNull() {
		ResultUtils.getExponent(null);
	}

	@Test
	public void testGetSizeDescription() {
		final String[] expected = { "Size", "Area", "Volume", "Size" };
		final AxisType spatialAxis = Axes.get("Spatial", true);
		final CalibratedAxis axis = new DefaultLinearAxis(spatialAxis);
		final ImgPlus<?> mockImage = mock(ImgPlus.class);
		when(mockImage.axis(anyInt())).thenReturn(axis);

		for (int i = 0; i < expected.length; i++) {
			final int dimensions = i + 1;
			when(mockImage.numDimensions()).thenReturn(dimensions);
			final String description = ResultUtils.getSizeDescription(mockImage);

			assertEquals("Size description is incorrect", expected[i], description);
		}
	}

	@Test(expected = NullPointerException.class)
	public void testGetSizeDescriptionThrowsNPEIfSpaceNull() {
		ResultUtils.getSizeDescription(null);
	}

	@Test
	public void testGetUnitHeader() {
		final String unit = "mm";
		final char exponent = '³';
		final DefaultLinearAxis axis = new DefaultLinearAxis(Axes.X, unit);
		final Img<DoubleType> img = ArrayImgs.doubles(10);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", axis);

		final String result = ResultUtils.getUnitHeader(imgPlus, unitService,
			exponent);

		assertEquals("Unexpected unit header", "(" + unit + exponent + ")", result);
	}

	@Test
	public void testGetUnitHeaderEmptyIfNoUnit() {
		final DefaultLinearAxis axis = new DefaultLinearAxis(Axes.X);
		final Img<DoubleType> img = ArrayImgs.doubles(10);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", axis);

		final String result = ResultUtils.getUnitHeader(imgPlus, unitService, '³');

		assertTrue("Unit header should be empty", result.isEmpty());
	}

	@Test(expected = NullPointerException.class)
	public void testGetUnitHeaderThrowsNPEIfSpaceNull() {
		ResultUtils.getUnitHeader(null, unitService, '³');
	}

	@Test(expected = NullPointerException.class)
	public void testGetUnitHeaderThrowsNPEIfUnitServiceNull() {
		final Img<DoubleType> img = ArrayImgs.doubles(10);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image",
			new DefaultLinearAxis(Axes.X, "mm"));

		ResultUtils.getUnitHeader(imgPlus, null, '³');
	}

	@Test
	public void testGetUnitHeaderImagePlus() {
		final ImagePlus imagePlus = new ImagePlus();
		final Calibration calibration = new Calibration();
		final String unit = "mm";
		calibration.setUnit(unit);
		imagePlus.setCalibration(calibration);

		final String unitHeader = ResultUtils.getUnitHeader(imagePlus);

		assertEquals("(" + unit + ")", unitHeader);
	}

	@Test
	public void testGetUnitHeaderImagePlusDefaultUnit() {
		final ImagePlus imagePlus = new ImagePlus();
		final Calibration calibration = new Calibration();
		imagePlus.setCalibration(calibration);

		final String unitHeader = ResultUtils.getUnitHeader(imagePlus);

		assertEquals("(pixel)", unitHeader);
	}

	@Test(expected = NullPointerException.class)
	public void testGetUnitHeaderThrowsNPEIfImagePlusNull() {
		ResultUtils.getUnitHeader(null);
	}

	@Test
	public void testGetUnitHeaderReturnPixelIfDefaultUnitPixel() {
		final String unit = "pixel";
		final String expected = "(" + unit + "³)";
		final DefaultLinearAxis axis = new DefaultLinearAxis(Axes.X, unit);
		final Img<DoubleType> img = ArrayImgs.doubles(10);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", axis);

		final String result = ResultUtils.getUnitHeader(imgPlus, unitService, '³');

		assertEquals(expected, result);
	}

	@Test
	public void testGetUnitHeaderReturnUnitIfDefaultUnit() {
		final String unit = "unit";
		final String expected = "(" + unit + "³)";
		final DefaultLinearAxis axis = new DefaultLinearAxis(Axes.X, unit);
		final Img<DoubleType> img = ArrayImgs.doubles(10);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", axis);

		final String result = ResultUtils.getUnitHeader(imgPlus, unitService, '³');

		assertEquals(expected, result);
	}

	@Test
	public void testToConventionalIndex() {
		final Stream<AxisType> types = Stream.of(Axes.X, Axes.Y, Axes.Z,
			Axes.CHANNEL, Axes.TIME);
		final Iterator<Long> expectedIndices = Stream.of(0L, 0L, 1L, 1L, 1L)
			.iterator();

		types.map(type -> ResultUtils.toConventionalIndex(type, 0)).forEach(
			i -> assertEquals(expectedIndices.next().longValue(), i.longValue()));
	}

	@Test
	public void testToConventionalIndexNullType() {
		assertEquals(0, ResultUtils.toConventionalIndex(null, 0));
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
		IMAGE_J = null;
	}
}
