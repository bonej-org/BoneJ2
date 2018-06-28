
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
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.Contextual;

/**
 * Unit tests for the AxisUtil class
 *
 * @author Richard Domander
 */
public class AxisUtilsTest {

	private static final Contextual IMAGE_J = new ImageJ();
	private static final UnitService unitService = IMAGE_J.context().getService(
		UnitService.class);

	@Test
	public void testCountSpatialDimensions() {
		// Create a test image
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final long[] dimensions = { 10, 10, 3 };
		final Img<DoubleType> img = ArrayImgs.doubles(dimensions);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, channelAxis);

		final long result = AxisUtils.countSpatialDimensions(imgPlus);

		assertEquals("Wrong number of spatial dimensions", 2, result);
	}

	@Test
	public void testCountSpatialDimensionsNullSpace() {
		final long result = AxisUtils.countSpatialDimensions(null);

		assertEquals("A null space should contain zero dimensions", 0, result);
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

			assertTrue(result.isPresent());
			assertTrue("Unit should be empty", result.get().isEmpty());
		}
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

	@Test
	public void testHasChannelDimensionsFalseIfSpaceNull() {
		final boolean result = AxisUtils.hasChannelDimensions(null);

		assertFalse("Null image should not have a channel dimension", result);
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

	@Test
	public void testHasSpatialDimensionsFalseIfSpaceNull() {
		final boolean result = AxisUtils.hasSpatialDimensions(null);

		assertFalse("Null image should not have a time dimension", result);
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

	@Test
	public void testHasTimeDimensionsFalseIfSpaceNull() {
		final boolean result = AxisUtils.hasTimeDimensions(null);

		assertFalse("Null image should not have a time dimension", result);
	}

	@Test
	public void testIsAxesMatchingSpatialCalibration() {
		// Create a test image with uniform calibration
		final String unit = "mm";
		final double scale = 0.75;
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, scale);
		final Img<BitType> img = ArrayImgs.bits(1, 1);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis);

		final boolean result = AxisUtils.isAxesMatchingSpatialCalibration(imgPlus);

		assertTrue("Axes should have matching calibration", result);
	}

	@Test
	public void testIsAxesMatchingSpatialCalibrationDifferentScales() {
		// Create a test image with different scales in calibration
		final String unit = "mm";
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, 0.5);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, 0.6);
		final Img<BitType> img = ArrayImgs.bits(1, 1);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis);

		final boolean result = AxisUtils.isAxesMatchingSpatialCalibration(imgPlus);

		assertFalse(
			"Different scales in axes should mean that calibration doesn't match",
			result);
	}

	@Test
	public void testIsAxesMatchingSpatialCalibrationDifferentUnits() {
		// Create a test image with different units in calibration
		final double scale = 0.75;
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "cm", scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm", scale);
		final Img<BitType> img = ArrayImgs.bits(1, 1);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis);

		final boolean result = AxisUtils.isAxesMatchingSpatialCalibration(imgPlus);

		assertFalse(
			"Different units in axes should mean that calibration doesn't match",
			result);
	}

	@Test
	public void testIsAxesMatchingSpatialCalibrationNoUnits() {
		// Create a test image with no calibration units
		final double scale = 0.75;
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "", scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, null, scale);
		final Img<BitType> img = ArrayImgs.bits(1, 1);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis);

		final boolean result = AxisUtils.isAxesMatchingSpatialCalibration(imgPlus);

		assertTrue("No units should mean matching calibration", result);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
