package org.bonej.ops.thresholdFraction;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.FinalDimensions;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.LongType;
import org.bonej.ops.thresholdFraction.SurfaceFraction.Results;
import org.bonej.ops.thresholdFraction.SurfaceFraction.Settings;
import org.bonej.testImages.Cuboid;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Iterator;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the {@link SurfaceFraction SurfaceFraction} class
 *
 * @author Richard Domander 
 */
public class SurfaceFractionTest {
    private final static ImageJ IMAGE_J = new ImageJ();
    private static final double DELTA = 1e-12;

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testCreateSurfaceMask() throws Exception {
        // Create a 2x2x2 test image with values from 0 to 7
        final Img<LongType> img = IMAGE_J.op().create().img(new FinalDimensions(2, 2, 2), new LongType());
        final ImgPlus<LongType> imgPlus = new ImgPlus<>(img);
        final Iterator<Long> longIterator = LongStream.iterate(0, i -> (i + 1) % 8).iterator();
        imgPlus.cursor().forEachRemaining(e -> e.set(longIterator.next()));

        // Create a mask from voxels whose value is between 2 and 5
        final Img<BitType> mask = SurfaceFraction.createSurfaceMask(IMAGE_J.op(), imgPlus, 0, 1, 2, 2.0, 5.0);
        final long voxels = StreamSupport.stream(mask.spliterator(), false).filter(BitType::get).count();

        assertEquals("Incorrect number of foreground voxels in the mask", 4, voxels);
    }

    /**
     * Test the volume calculation of SurfaceFraction with a 1x1x1 cube
     * <p>
     * The surface created by the marching cubes algorithm in SurfaceFraction is "in between pixels".
     * In the case of a unit cube it creates an octahedron,
     * whose vertices are in the middle of the faces of the cube.
     */
    @Test
    public void regressionTestUnitCube() throws Exception {

        final long width = 1L;
        final long height = 1L;
        final long depth = 1L;
        final double a = width * 0.5;
        final double b = depth * 0.5;
        final double pyramidSide = Math.sqrt(a * a + b * b);
        final double pyramidVolume = pyramidSide * pyramidSide * (height / 2.0) / 3.0;
        final double thresholdVolume = pyramidVolume * 2.0;
        // Unit cube with 1 padding on each side, so 3x3x3 stack
        final IterableInterval unitCube = (IterableInterval) IMAGE_J.op().run(Cuboid.class, null, 1, 1, 1, 1, 1, 1);
        final Settings settings = new Settings(1.0, 1.0);
        /*
         * Each voxel in the surface is "full" except for the 8 corners of the stack.
         * IIRC if the corner had two neighbors (x & y), the corner would be half a voxel.
         * Because it has three (x, y & z), a quarter of a pyramid is also added
         */
        final double totalVolume = 27.0 - 8.0 * 0.5 + pyramidVolume * 2;

        final Results results = (Results) IMAGE_J.op().run(SurfaceFraction.class, unitCube, settings);

        assertEquals("Incorrect threshold surface volume", thresholdVolume, results.thresholdSurfaceVolume, DELTA);
        assertEquals("Incorrect total surface volume", totalVolume, results.totalSurfaceVolume, DELTA);
        assertEquals("Incorrect volume ratio", thresholdVolume / totalVolume, results.ratio, DELTA);
    }
}