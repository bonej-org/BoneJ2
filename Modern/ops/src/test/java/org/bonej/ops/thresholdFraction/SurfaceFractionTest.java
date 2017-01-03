
package org.bonej.ops.thresholdFraction;

import static org.junit.Assert.assertEquals;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;

import org.bonej.ops.thresholdFraction.SurfaceFraction.Results;
import org.junit.Test;

/**
 * Tests for the {@link SurfaceFraction SurfaceFraction} class
 *
 * @author Richard Domander
 */
public class SurfaceFractionTest {

	private final static ImageJ IMAGE_J = new ImageJ();
	private static final double DELTA = 1e-12;

	/** Test that conforms fails when there aren't 3 dimensions in the image */
	@Test(expected = IllegalArgumentException.class)
	public void testConforms() throws Exception {
		final Img<DoubleType> img = ArrayImgs.doubles(1, 1);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img);

		IMAGE_J.op().op(SurfaceFraction.class, imgPlus);
	}

	/**
	 * Test the volume calculation of SurfaceFraction with a 3x3x3 cube in a 5x5x5
	 * image
	 */
	@Test
	public void testCompute2() throws Exception {
		// SETUP
		final long cubeWidth = 3;
		final long cubeHeight = 3;
		final long cubeDepth = 3;
		final long imgWidth = 5;
		final long imgHeight = 5;
		final long imgDepth = 5;
		// Marching cubes creates meshes that are effectively one voxel smaller
		final double expectedVolume = (cubeWidth - 1) * (cubeHeight - 1) * (cubeDepth - 1);
		final double totalVolume = (imgWidth - 1) * (imgHeight - 1) * (imgDepth - 1);
		final ArrayImg<BitType, LongArray> image = ArrayImgs.bits(imgWidth, imgHeight, imgDepth);
		final ArrayRandomAccess<BitType> access = image.randomAccess();
		for (int z = 1; z <= cubeDepth; z++) {
			for (int y = 1; y <= cubeHeight; y++) {
				for (int x = 1; x <= cubeWidth; x++) {
					access.setPosition(new long[] { x, y, z });
					access.get().setOne();
				}
			}
		}
		final Thresholds thresholds = new Thresholds<>(image, 1.0, 1.0);

		// EXECUTE
		final Results results = (Results) IMAGE_J.op().run(SurfaceFraction.class,
				image, thresholds);

		// VERIFY
		assertEquals("Incorrect threshold surface volume", expectedVolume,
			results.thresholdSurfaceVolume, DELTA);
		assertEquals("Incorrect total surface volume", totalVolume,
			results.totalSurfaceVolume, DELTA);
		assertEquals("Incorrect volume ratio", expectedVolume / totalVolume,
			results.ratio, DELTA);
	}
}
