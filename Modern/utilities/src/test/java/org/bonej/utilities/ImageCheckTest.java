package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.stream.IntStream;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.AfterClass;
import org.junit.Test;

/**
 * Unit tests for the ImageCheck utility class
 *
 * @author Richard Domander
 */
public class ImageCheckTest {
	private static final ImageJ IMAGE_J = new ImageJ();

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test(expected = NullPointerException.class)
	public void testIsColorsBinaryThrowsNPEIfIntervalNull() throws Exception {
		ImageCheck.isColorsBinary(null);
	}

	@Test
	public void testIsColorsBinaryReturnsFalseIfIntervalEmpty() throws Exception {
		final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{0});

		final boolean result = ImageCheck.isColorsBinary(interval);

		assertFalse("An empty image should not be binary", result);
	}

	@Test
	public void testIsColorsBinaryReturnsTrueForMonochrome() throws Exception {
		final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});

		final boolean result = ImageCheck.isColorsBinary(interval);

		assertTrue("Monochrome image should be binary", result);
	}

	@Test
	public void testIsColorsBinaryReturnsTrueForDichromatic() throws Exception {
		// Create a test image with two colors
		final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});
		final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i % 2).iterator();
		interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

		final boolean result = ImageCheck.isColorsBinary(interval);

		assertTrue("An image with two colours should be binary", result);
	}

	@Test
	public void testIsColorsBinaryReturnsFalseForMulticolor() throws Exception {
		// Create a test image with many colors
		final IterableInterval<DoubleType> interval = IMAGE_J.op().create().img(new long[]{5, 5});
		final Iterator<Integer> intIterator = IntStream.iterate(0, i -> i + 1).iterator();
		interval.cursor().forEachRemaining(e -> e.setReal(intIterator.next()));

		final boolean result = ImageCheck.isColorsBinary(interval);

		assertFalse("An image with more than two colours should not be binary", result);
	}

	@Test(expected = NullPointerException.class)
	public void testCountSpatialDimensionsThrowsNPEIfSpaceNull() throws Exception {
		ImageCheck.countSpatialDimensions(null);
	}

	@Test
	public void testCountSpatialDimensions() throws Exception {
		// Create a test image
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final DefaultLinearAxis channelAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final int[] dimensions = {10, 10, 3};
		Img<DoubleType> img = IMAGE_J.op().create().img(dimensions);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, channelAxis);

		final long result = ImageCheck.countSpatialDimensions(imgPlus);

		assertEquals("Wrong number of spatial dimensions", 2, result);
	}
}