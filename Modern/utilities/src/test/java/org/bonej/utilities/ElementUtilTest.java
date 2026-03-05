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

import java.util.Iterator;
import java.util.stream.IntStream;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.axis.PowerAxis;
import net.imagej.units.UnitService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

import org.junit.AfterClass;
import org.junit.Test;

/**
 * Unit tests for the {@link ElementUtil} class
 *
 * @author Richard Domander
 */
public class ElementUtilTest {

	private static ImageJ IMAGE_J = new ImageJ();
	private static UnitService unitService = IMAGE_J.context().getService(
		UnitService.class);

	@Test
	public void testCalibratedSpatialElementSize() {
		final double[][] scales = { { 20.0, 4.0, 1.0 }, { 20.0, 1.0, 4.0 }, { 4.0,
			20.0, 1.0 } };
		final String[][] units = { { "mm", "cm", "m" }, { "m", "cm", "mm" }, { "µm",
			"µm", "µm" } };
		final double[] expected = { 800_000, 0.0008, 80.0 };
		final Img<ByteType> img = ArrayImgs.bytes(1, 1, 1);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img);

		for (int i = 0; i < scales.length; i++) {
			final CalibratedAxis xAxis = new DefaultLinearAxis(Axes.X, units[i][0],
				scales[i][0]);
			final CalibratedAxis yAxis = new DefaultLinearAxis(Axes.Y, units[i][1],
				scales[i][1]);
			final CalibratedAxis zAxis = new DefaultLinearAxis(Axes.Z, units[i][2],
				scales[i][2]);
			imgPlus.setAxis(xAxis, 0);
			imgPlus.setAxis(yAxis, 1);
			imgPlus.setAxis(zAxis, 2);

			final double elementSize = ElementUtil.calibratedSpatialElementSize(
				imgPlus, unitService);

			assertEquals("Element size is incorrect", expected[i], elementSize,
				1e-12);
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCalibratedSpatialElementSizeThrowsIAEIfNoSpatialAxes() {
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final Img<DoubleType> img = IMAGE_J.op().create().img(new int[] { 3 });
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", cAxis);

		ElementUtil.calibratedSpatialElementSize(imgPlus, unitService);
	}

	@Test
	public void testCalibratedSpatialElementSizeNoUnits() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, 20.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, 4.0);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, 1.0);
		final Img<ByteType> img = ArrayImgs.bytes(1, 1, 1);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
			zAxis);

		final double elementSize = ElementUtil.calibratedSpatialElementSize(imgPlus,
			unitService);

		assertEquals("Element size is incorrect", 80.0, elementSize, 1e-12);
	}

	@Test
	public void testCalibratedSpatialElementSizeNonLinearAxis() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final PowerAxis yAxis = new PowerAxis(Axes.Y, 2);
		final Img<DoubleType> img = IMAGE_J.op().create().img(new int[] { 10, 10 });
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis);

		final double result = ElementUtil.calibratedSpatialElementSize(imgPlus,
			unitService);

		assertTrue("Size should be NaN when space has nonlinear axes", Double.isNaN(
			result));
	}

	@Test(expected = NullPointerException.class)
	public void testCalibratedSpatialElementSizeThrowsNPIfENullSpace() {
		ElementUtil.calibratedSpatialElementSize(null, unitService);
	}

	@Test(expected = NullPointerException.class)
	public void testCalibratedSpatialElementSizeThrowsNPEIfNullUnitService() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "kg");
		final Img<ByteType> img = ArrayImgs.bytes(1, 1, 1);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis,
				zAxis);

		ElementUtil.calibratedSpatialElementSize(imgPlus, null);
	}

	@Test
	public void testCalibratedSpatialElementSizeUnitsInconvertible() {
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "cm");
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "");
		final Img<DoubleType> img = IMAGE_J.op().create().img(new int[] { 10, 10 });
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis);

		final double result = ElementUtil.calibratedSpatialElementSize(imgPlus,
			unitService);

		assertEquals("Size should be NaN if unit inconvertible", Double.NaN, result,
			1e-12);
	}

	@Test
	public void testIsBinary() {
		// Create a test image with two values
		final IterableInterval<DoubleType> interval = ArrayImgs.doubles(2, 2);
		final Iterator<Integer> intIterator = IntStream.iterate(0, i -> (i + 1) % 2)
			.iterator();
		interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

		final boolean result = ElementUtil.isBinary(interval);

		assertTrue("An image with two values should be binary", result);
	}

	@Test
	public void testIsBinaryBooleanTypeAssignable() {
		final Img<BitType> img = ArrayImgs.bits(1);

		final boolean isBinary = ElementUtil.isBinary(img);

		assertTrue("A BitType image should be binary", isBinary);
	}

	@Test(expected = NullPointerException.class)
	public void testIsBinaryThrowsNPEIfIntervalNull() {
		final boolean result = ElementUtil.isBinary(null);

		assertFalse("A null interval should not be binary", result);
	}

	@Test
	public void testIsBinaryReturnsFalseForMultiValue() {
		// Create a test image with many values
		final IterableInterval<DoubleType> interval = ArrayImgs.doubles(2, 2);
		final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i + 1)
			.iterator();
		interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

		final boolean result = ElementUtil.isBinary(interval);

		assertFalse(
			"An image with more than two values should not be binary", result);
	}

	@Test
	public void testIsBinaryReturnsTrueForMonochrome() {
		final IterableInterval<DoubleType> interval = ArrayImgs.doubles(2, 2);

		final boolean result = ElementUtil.isBinary(interval);

		assertTrue("Monochrome image should be binary", result);
	}
	
    // Helper method to create an ImgPlus with specified dimensions and axes
    private ImgPlus<UnsignedByteType> createImgPlus(long[] dimensions, String[] axisTypes) {
        Img<UnsignedByteType> img = ArrayImgs.unsignedBytes(dimensions);
        ImgPlus<UnsignedByteType> imgPlus = new ImgPlus<>(img, "test");

        // Set axes if provided
        if (axisTypes != null && axisTypes.length == dimensions.length) {
            for (int i = 0; i < axisTypes.length; i++) {
                switch (axisTypes[i]) {
                    case "X": imgPlus.axis(i).setType(Axes.X); break;
                    case "Y": imgPlus.axis(i).setType(Axes.Y); break;
                    case "Z": imgPlus.axis(i).setType(Axes.Z); break;
                    case "T": imgPlus.axis(i).setType(Axes.TIME); break;
                    case "C": imgPlus.axis(i).setType(Axes.CHANNEL); break;
                }
            }
        }

        return imgPlus;
    }

    // 2D Tests (XY)

    @Test
    public void test2DAllZerosIsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10}, new String[]{"X", "Y"});
        assertTrue("2D all-zero image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test2DAll255IsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10}, new String[]{"X", "Y"});
        Cursor<UnsignedByteType> cursor = imgPlus.cursor();
        while (cursor.hasNext()) {
            cursor.next().set(255);
        }
        assertTrue("2D all-255 image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test2DMixed0And255IsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10}, new String[]{"X", "Y"});
        Cursor<UnsignedByteType> cursor = imgPlus.cursor();
        int i = 0;
        while (cursor.hasNext()) {
            cursor.next().set(i++ % 2 == 0 ? 0 : 255);
        }
        assertTrue("2D mixed 0/255 image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test2DWithNonBinaryValueIsNotBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10}, new String[]{"X", "Y"});
        Views.flatIterable(imgPlus).cursor().next().set(128);
        assertFalse("2D image with non-binary value should not be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    // 3D Tests (XYZ)

    @Test
    public void test3DAllZerosIsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5}, new String[]{"X", "Y", "Z"});
        assertTrue("3D all-zero image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test3DMixed0And255IsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5}, new String[]{"X", "Y", "Z"});
        Cursor<UnsignedByteType> cursor = imgPlus.cursor();
        int i = 0;
        while (cursor.hasNext()) {
            cursor.next().set(i++ % 2 == 0 ? 0 : 255);
        }
        assertTrue("3D mixed 0/255 image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test3DWithNonBinaryValueIsNotBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5}, new String[]{"X", "Y", "Z"});
        Views.flatIterable(imgPlus).cursor().next().set(128);
        assertFalse("3D image with non-binary value should not be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    // 4D Tests (XYZT)

    @Test
    public void test4DAllZerosIsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5, 3}, new String[]{"X", "Y", "Z", "T"});
        assertTrue("4D all-zero image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test4DMixed0And255IsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5, 3}, new String[]{"X", "Y", "Z", "T"});
        Cursor<UnsignedByteType> cursor = imgPlus.cursor();
        int i = 0;
        while (cursor.hasNext()) {
            cursor.next().set(i++ % 2 == 0 ? 0 : 255);
        }
        assertTrue("4D mixed 0/255 image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test4DWithNonBinaryValueIsNotBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5, 3}, new String[]{"X", "Y", "Z", "T"});
        Views.flatIterable(imgPlus).cursor().next().set(128);
        assertFalse("4D image with non-binary value should not be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    // 5D Tests (XYZCT)

    @Test
    public void test5DAllZerosIsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5, 3, 2}, new String[]{"X", "Y", "Z", "T", "C"});
        assertTrue("5D all-zero image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test5DMixed0And255IsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5, 3, 2}, new String[]{"X", "Y", "Z", "T", "C"});
        Cursor<UnsignedByteType> cursor = imgPlus.cursor();
        int i = 0;
        while (cursor.hasNext()) {
            cursor.next().set(i++ % 2 == 0 ? 0 : 255);
        }
        assertTrue("5D mixed 0/255 image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void test5DWithNonBinaryValueIsNotBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{10, 10, 5, 3, 2}, new String[]{"X", "Y", "Z", "T", "C"});
        Views.flatIterable(imgPlus).cursor().next().set(128);
        assertFalse("5D image with non-binary value should not be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    // Edge Cases

    @Test
    public void testSinglePixelZeroIsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{1, 1}, new String[]{"X", "Y"});
        assertTrue("Single pixel zero image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void testSinglePixel255IsBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{1, 1}, new String[]{"X", "Y"});
        Views.flatIterable(imgPlus).cursor().next().set(255);
        assertTrue("Single pixel 255 image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void testSinglePixelNonBinaryIsNotBinary() {
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{1, 1}, new String[]{"X", "Y"});
        Views.flatIterable(imgPlus).cursor().next().set(128);
        assertFalse("Single pixel non-binary image should not be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void testEarlyTerminationWithNonBinaryValue() {
        // Create a large image with a non-binary value at the beginning
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{100, 100}, new String[]{"X", "Y"});
        Views.flatIterable(imgPlus).cursor().next().set(128);
        assertFalse("Image with early non-binary value should terminate early and not be binary",
                    ElementUtil.isImageJ1Binary(imgPlus));
    }

    @Test
    public void testLargeBinaryImage() {
        // Create a large binary image
        ImgPlus<UnsignedByteType> imgPlus = createImgPlus(new long[]{1000, 1000}, new String[]{"X", "Y"});
        Cursor<UnsignedByteType> cursor = imgPlus.cursor();
        int i = 0;
        while (cursor.hasNext()) {
            cursor.next().set(i++ % 2 == 0 ? 0 : 255);
        }
        assertTrue("Large binary image should be binary", ElementUtil.isImageJ1Binary(imgPlus));
    }

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
		IMAGE_J = null;
		unitService = null;
	}
}
