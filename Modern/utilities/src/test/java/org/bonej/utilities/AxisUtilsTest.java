/*-
 * #%L
 * Utility methods for BoneJ2
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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


package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.units.UnitService;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.scijava.Contextual;

/**
 * Unit tests for the AxisUtil class
 *
 * @author Richard Domander
 */
public class AxisUtilsTest {

	private static Contextual IMAGE_J = new ImageJ();
	private static UnitService unitService = IMAGE_J.context().getService(
		UnitService.class);

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Test
	public void isSpatialCalibrationsIsotropic() {
		// SETUP
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 10);
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 1.0);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm", 1.0);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
			zAxis);

		// EXECUTE
		final boolean result = AxisUtils.isSpatialCalibrationsIsotropic(imgPlus,
			0.0, unitService);

		// VERIFY
		assertTrue(result);
	}

	@Test
	public void isSpatialCalibrationsIsotropicAnisotropicBeyondTolerance() {
		// SETUP
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 10);
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 1.06);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm", 1.0);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
			zAxis);

		// EXECUTE
		final boolean result = AxisUtils.isSpatialCalibrationsIsotropic(imgPlus,
			0.05, unitService);

		// VERIFY
		assertFalse(result);
	}

	@Test
	public void isSpatialCalibrationsIsotropicAnisotropicWithinTolerance() {
		// SETUP
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 10);
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 1.05);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm", 1.0);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
			zAxis);

		// EXECUTE
		final boolean result = AxisUtils.isSpatialCalibrationsIsotropic(imgPlus,
			0.05, unitService);

		// VERIFY
		assertTrue(result);
	}

	@Test
	public void isSpatialCalibrationsIsotropicDifferentUnits() {
		// SETUP
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 10);
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "cm", 0.1);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm", 1.0);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
			zAxis);

		// EXECUTE
		final boolean result = AxisUtils.isSpatialCalibrationsIsotropic(imgPlus,
			0.0, unitService);

		// VERIFY
		assertTrue(result);
	}

	@Test
	public void isSpatialCalibrationsIsotropicThrowsIAEInconvertibleUnits() {
		// SETUP
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 10);
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 1.0);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "kg", 1.0);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
			zAxis);
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage(
			"Isotropy cannot be determined: units of spatial calibrations are inconvertible");

		// EXECUTE
		AxisUtils.isSpatialCalibrationsIsotropic(imgPlus, 0.0, unitService);
	}

	@Test(expected = NullPointerException.class)
	public void testCountSpatialDimensionsThrowsNPEIfNullSpace() {
		AxisUtils.countSpatialDimensions(null);
	}

	@Test
	public void isSpatialCalibrationsIsotropicThrowsIAENanTolerance() {
		// SETUP
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 10);
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 1.0);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm", 1.0);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
			zAxis);
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("Tolerance cannot be NaN");

		// EXECUTE
		AxisUtils.isSpatialCalibrationsIsotropic(imgPlus, Double.NaN, unitService);
	}

	@Test
	public void isSpatialCalibrationsIsotropicThrowsIAENegativeTolerance() {
		// SETUP
		final Img<DoubleType> img = ArrayImgs.doubles(10, 10, 10);
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 1.0);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm", 1.0);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
			zAxis);
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectMessage("Tolerance cannot be negative");

		// EXECUTE
		AxisUtils.isSpatialCalibrationsIsotropic(imgPlus, -1.0, unitService);
	}

	@Test
	public void testGetSpatialUnit() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "µm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 5.0);
		final Img<ByteType> img = ArrayImgs.bytes(1, 1);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis);

		final Optional<String> unit = AxisUtils.getSpatialUnit(imgPlus,
			unitService);

		assertTrue("String should be present when units are convertible", unit
			.isPresent());
		assertEquals("Unit is incorrect", "µm", unit.get());
	}

	@Test
	public void testGetSpatialUnitAllAxesUncalibrated() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "", 5.0);
		final Img<ByteType> img = ArrayImgs.bytes(1, 1);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis);

		final Optional<String> unit = AxisUtils.getSpatialUnit(imgPlus,
			unitService);

		assertTrue("String should be present when units are convertible", unit
			.isPresent());
		assertTrue("Unit should be empty", unit.get().isEmpty());
	}

	@Test
	public void testGetSpatialUnitInconvertibleUnits() {
		final String[][] units = { { "m", "" }, { "cm", "kg" } };
		final Img<ByteType> img = ArrayImgs.bytes(1, 1);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img);

		for (final String[] unit : units) {
			final CalibratedAxis xAxis = new DefaultLinearAxis(Axes.X, unit[0]);
			final CalibratedAxis yAxis = new DefaultLinearAxis(Axes.Y, unit[1]);
			imgPlus.setAxis(xAxis, 0);
			imgPlus.setAxis(yAxis, 1);

			final Optional<String> result = AxisUtils.getSpatialUnit(imgPlus,
				unitService);

			assertFalse(result.isPresent());
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetSpatialUnitThrowsIAEIfNoSpatialAxes() {
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<ByteType> img = ArrayImgs.bytes(1, 1);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", cAxis, tAxis);

		AxisUtils.getSpatialUnit(imgPlus, unitService);
	}

	@Test(expected = NullPointerException.class)
	public void testGetSpatialUnitThrowsNPEIfSpaceNull() {
		AxisUtils.getSpatialUnit(null, unitService);
	}

	@Test(expected = NullPointerException.class)
	public void testGetSpatialUnitThrowsNPEIfUnitServiceNull() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "µm", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", 5.0);
		final Img<ByteType> img = ArrayImgs.bytes(1, 1);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis);

		AxisUtils.getSpatialUnit(imgPlus, null);
	}

	@Test
	public void testHasChannelDimensions() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final Img<DoubleType> img = ArrayImgs.doubles(1, 1, 1);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, cAxis);

		final boolean result = AxisUtils.hasChannelDimensions(imgPlus);

		assertTrue("Should be true when image has channel dimensions", result);
	}

	@Test(expected = NullPointerException.class)
	public void testHasChannelDimensionsThrowsNPEIfSpaceNull() {
		AxisUtils.hasChannelDimensions(null);
	}

	@Test
	public void testHasSpatialDimensions() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<DoubleType> img = ArrayImgs.doubles(1, 1);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			tAxis);

		final boolean result = AxisUtils.hasTimeDimensions(imgPlus);

		assertTrue("Should be true when image has spatial dimensions", result);
	}

	@Test(expected = NullPointerException.class)
	public void testHasSpatialDimensionsThrowsNPEIfSpaceNull() {
		AxisUtils.hasSpatialDimensions(null);
	}

	@Test
	public void testHasTimeDimensions() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<DoubleType> img = ArrayImgs.doubles(1, 1, 1);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, tAxis);

		final boolean result = AxisUtils.hasTimeDimensions(imgPlus);

		assertTrue("Should be true when image has time dimensions", result);
	}

	@Test(expected = NullPointerException.class)
	public void testHasTimeDimensionsThrowsNPEIfSpaceNull() {
		AxisUtils.hasTimeDimensions(null);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
		IMAGE_J = null;
		unitService = null;
	}
}
