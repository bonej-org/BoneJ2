package org.bonej.utilities;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.type.logic.BitType;

import org.junit.AfterClass;
import org.junit.Test;

/**
 * Unit tests for the ImagePlusHelper class
 *
 * @author Richard Domander
 * FIXME: add tests for convertable and unconvertable datasets
 */
public class ImagePlusHelperTest {
	private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() throws Exception {
        IMAGE_J.context().dispose();
    }

	@Test(expected = NullPointerException.class)
	public void testToImagePlusThrowsNPEifConvertServiceNull() throws Exception {
		final Dataset dataset = IMAGE_J.dataset().create(new BitType(), new long[]{10, 10}, "",
				new AxisType[]{Axes.X, Axes.Y});

		ImagePlusHelper.toImagePlus(null, dataset);
	}

    @Test(expected = NullPointerException.class)
    public void testToImagePlusThrowsThrowsNPEifDatasetNull() throws Exception {
        ImagePlusHelper.toImagePlus(IMAGE_J.convert(), null);
    }
}