
package org.bonej.wrapperPlugins.wrapperUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import org.junit.Test;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;

/**
 * Unit tests for the {@link Common} utility class
 *
 * @author Richard Domander
 */
public class CommonTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test
	public void cleanDuplicate() throws Exception {
		final String title = "bonej-test-image.tiff";
		final int width = 5;
		final int height = 7;
		final int depth = 11;
		final Roi roi = new Roi(1, 1, 3, 3);
		final ImagePlus image = IJ.createImage(title, width, height, depth, 8);
		image.setRoi(roi);

		final ImagePlus result = Common.cleanDuplicate(image);

		assertEquals("Duplicate has wrong title", result.getTitle(), title);
		assertEquals("ROI should not affect duplicate size", width, result
			.getWidth());
		assertEquals("ROI should not affect duplicate size", height, result
			.getHeight());
		assertEquals("The original image should still have its ROI", roi, image
			.getRoi());
	}

	@Test
	public void testConvertWithMetadata() throws AssertionError {
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
}
