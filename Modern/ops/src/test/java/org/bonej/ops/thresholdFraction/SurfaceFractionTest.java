package org.bonej.ops.thresholdFraction;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.FinalDimensions;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import org.bonej.ops.thresholdFraction.SurfaceFraction.Results;
import org.bonej.testImages.Cuboid;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for the {@link SurfaceFraction SurfaceFraction} class
 *
 * @author Richard Domander 
 */
public class SurfaceFractionTest {
    private final static ImageJ IMAGE_J = new ImageJ();
    private static final double DELTA = 1e-12;

    /** Test that conforms fails when there aren't 3 spatial dimensions in the image */
    @Test(expected = IllegalArgumentException.class)
    public void testConforms() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final Img<BitType> img = IMAGE_J.op().create().img(new FinalDimensions(10, 10, 3), new BitType());
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis, cAxis);

        IMAGE_J.op().op(SurfaceFraction.class, imgPlus);
    }

    /**
     * Test the volume calculation of SurfaceFraction with a 1x1x1 cube in a 3x3x3 image
     * <p>
     * The surface created by the marching cubes algorithm in SurfaceFraction is "in between pixels".
     * In the case of a unit cube it creates an octahedron,
     * whose vertices are in the middle of the faces of the cube.
     */
    @Test
    public void testCompute2() throws Exception {

        final long width = 1L;
        final long height = 1L;
        final long depth = 1L;
        final double a = width * 0.5;
        final double b = depth * 0.5;
        final double pyramidSide = Math.sqrt(a * a + b * b);
        final double pyramidVolume = pyramidSide * pyramidSide * (height / 2.0) / 3.0;
        final double thresholdVolume = pyramidVolume * 2.0;
        // Unit cube with 1 padding on each side, so 3x3x3 stack
        final IterableInterval<BitType> unitCube =
                (IterableInterval<BitType>) IMAGE_J.op().run(Cuboid.class, null, 1, 1, 1, 1, 1, 1);
        final Thresholds settings = new Thresholds<>(unitCube, 1.0, 1.0);
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