package org.bonej.utilities;

import static org.junit.Assert.assertEquals;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
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