/*-
 * #%L
 * Utility methods for BoneJ2
 * %%
 * Copyright (C) 2015 - 2026 Michael Doube, BoneJ developers
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
import java.util.Random;
import java.util.stream.IntStream;

import net.imagej.Dataset;
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
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;

import org.junit.AfterClass;
import org.junit.Before;
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

	private ImageJ ij;

    @Before
    public void setUp() {
        ij = new ImageJ();
    }

    @Test
    public void testPureBinaryImage() {
        Dataset ds = createBinaryDataset(10, 10, true);
        assertTrue("Pure binary image should return true", ElementUtil.isIJ1Binary(ds, 100));
    }

    @Test
    public void testNonBinaryImage() {
        Dataset ds = createBinaryDataset(10, 10, false); // Contains 128
        assertFalse("Image with intermediate values should return false", ElementUtil.isIJ1Binary(ds, 100));
    }

    @Test
    public void testWrongBitDepth() {
        // Create 16-bit image
        ArrayImg<UnsignedShortType, ?> img = new ArrayImgFactory<>(new UnsignedShortType()).create(10, 10);
        // Fill with 0 and 255
        fillWithValues(img, new int[]{0, 255});
        Dataset ds = ij.dataset().create(img);
        assertFalse("16-bit image should return false", ElementUtil.isIJ1Binary(ds, 100));
    }

    @Test
    public void testFloatImage() {
        ArrayImg<FloatType, ?> img = new ArrayImgFactory<>(new FloatType()).create(10, 10);
        fillWithValuesFloat(img, new float[]{0.0f, 255.0f});
        Dataset ds = ij.dataset().create(img);
        assertFalse("Float image should return false", ElementUtil.isIJ1Binary(ds, 100));
    }

    @Test
    public void test3DStack() {
        // 5x5x5 stack
        Dataset ds = createBinaryDataset(5, 5, 5, true);
        assertTrue("3D binary stack should return true", ElementUtil.isIJ1Binary(ds, 100));
        
        // All 128s
        Dataset dsBad = createBinaryDataset(5, 5, 5, false);
        assertFalse("3D stack with 128s should return false", ElementUtil.isIJ1Binary(dsBad, 100));
    }

    @Test
    public void testSmallImageFullScan() {
        Dataset ds = createSmallBinaryDataset(2, 2);
        assertTrue("Small binary image should return true", 
            ElementUtil.isIJ1Binary(ds, 100));
        
        Dataset dsBad = createNonBinaryDataset(2, 2);
        assertFalse("Small image with 128 should return false", 
            ElementUtil.isIJ1Binary(dsBad, 100));
    }

    // --- Helper Methods ---

    /**
     * Creates a 2D 8-bit dataset.
     * @param binary if true, fills with 0 and 255. If false, fills with 0, 128, 255.
     */
    private Dataset createBinaryDataset(int w, int h, boolean binary) {
        return createBinaryDataset(w, h, 1, binary);
    }

    /**
     * Creates a 3D 8-bit dataset.
     */
    private Dataset createBinaryDataset(int w, int h, int d, boolean binary) {
        long[] dims = new long[]{w, h, d};
        ArrayImg<UnsignedByteType, ?> img = new ArrayImgFactory<>(new UnsignedByteType()).create(dims);
        
        fillWithValues(img, binary ? new int[]{0, 255} : new int[]{0, 128, 255});
        
        return ij.dataset().create(img);
    }

//    /**
//     * Fills an image with random values from the provided array.
//     */    
//    private <T extends IntegerType<T> & NativeType<T>> void fillWithValues(ArrayImg<T, ?> img, int[] values) {
//        Random rand = new Random(42);
//        long[] pos = new long[img.numDimensions()];
//        for (long i = 0; i < img.size(); i++) {
//            long tempIdx = i;
//            for (int d = 0; d < img.numDimensions(); d++) {
//                pos[d] = tempIdx % img.dimension(d);
//                tempIdx /= img.dimension(d);
//            }
//            img.randomAccess().setPosition(pos);
//            img.randomAccess().get().setInteger(values[rand.nextInt(values.length)]);
//        }
//    }
//    
    
    /**
     * Fills an image of any IntegerType (Byte, Short, Int) with values from the provided array.
     * If values.length == 1, fills the entire image with that single value.
     * Otherwise, picks a random value from the array for each pixel.
     */
    private <T extends IntegerType<T> & NativeType<T>> void fillWithValues(ArrayImg<T, ?> img, int[] values) {
        if (values == null || values.length == 0) {
            return;
        }

        Random rand = new Random(42); // Fixed seed for reproducibility
        Cursor<T> cursor = img.cursor();

        while (cursor.hasNext()) {
            T pixel = cursor.next();
            int val;

            if (values.length == 1) {
                val = values[0];
            } else {
                val = values[rand.nextInt(values.length)];
            }

            // setInteger() works for all IntegerType implementations
            pixel.setInteger(val);
        }
    }
    
    /**
     * Fills a Float image with random values.
     */
    private void fillWithValuesFloat(ArrayImg<FloatType, ?> img, float[] values) {
        Random rand = new Random(42);
        int[] flatPos = new int[(int) img.numDimensions()];
        for (long i = 0; i < img.size(); i++) {
            long tempIdx = i;
            for (int d = 0; d < img.numDimensions(); d++) {
                flatPos[d] = (int) (tempIdx % img.dimension(d));
                tempIdx /= img.dimension(d);
            }
            
            img.randomAccess().setPosition(flatPos);
            float val = values[rand.nextInt(values.length)];
            img.randomAccess().get().set(val);
        }
    }

    /**
     * Creates a small image with a GUARANTEED 128 pixel at position (1,0,0).
     */
    private Dataset createNonBinaryDataset(int w, int h) {
        ArrayImg<UnsignedByteType, ?> img = 
            new ArrayImgFactory<>(new UnsignedByteType()).create(w, h);
        
        // Fill all with 0
        Cursor<UnsignedByteType> c = img.cursor();
        while (c.hasNext()) {
            c.next().set(0);
        }
        
        // Set one pixel to 128
        img.randomAccess().setPosition(new long[]{1, 0});
        img.randomAccess().get().set(128);
        
        return ij.dataset().create(img);
    }

    /**
     * Creates a small image with only 0 and 255.
     */
    private Dataset createSmallBinaryDataset(int w, int h) {
        ArrayImg<UnsignedByteType, ?> img = 
            new ArrayImgFactory<>(new UnsignedByteType()).create(w, h);
        
        // Alternate 0 and 255
        Cursor<UnsignedByteType> c = img.cursor();
        int i = 0;
        while (c.hasNext()) {
            c.next().set(i++ % 2 == 0 ? 0 : 255);
        }
        
        return ij.dataset().create(img);
    }
	
	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
		IMAGE_J = null;
		unitService = null;
	}
}
