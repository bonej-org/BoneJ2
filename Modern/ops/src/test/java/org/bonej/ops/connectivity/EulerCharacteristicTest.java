package org.bonej.ops.connectivity;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import org.bonej.testImages.Cuboid;
import org.bonej.testImages.HollowCuboid;
import org.bonej.testImages.IJ1ImgPlus;
import org.bonej.testImages.WireFrameCuboid;
import org.junit.AfterClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.bonej.testImages.IJ1ImgPlus.*;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the {@link EulerCharacteristic EulerCharacteristic} Op
 *
 * @author Richard Domander
 */
public class EulerCharacteristicTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEulerCharacteristicMatchingFailsIfNot3DSpatial() {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final Img<BitType> img = IMAGE_J.op().create().img(new FinalDimensions(10, 10, 3), new BitType());
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis, cAxis);

        IMAGE_J.op().op(EulerCharacteristic.class, imgPlus);
    }

    /**
     * Regression test EulerCharacteristic with a solid cuboid that touches the edges of the stack
     * <p>
     * Here χ = β_0 - β_1 + β_2 = 1 - 0 + 0 = 1.
     * The formula χ = vertices - edges + faces for surfaces of polyhedra doesn't apply because the cuboid is solid.
     */
    @Test
    public void testCompute1Cuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(Cuboid.class, null, 5, 5, 5, 1, 1, 0);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, cuboid);

        assertEquals("Euler characteristic is incorrect", 1, result.intValue());
    }

    /**
     * Regression test EulerCharacteristic with a wire-frame cuboid that doesn't touch edges
     * <p>
     * Here χ = vertices - edges + faces = V - E + F = 8 - 12 + 0 = -4
     */
    @Test
    public void testCompute1WireFrameCuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(WireFrameCuboid.class, null, 5, 5, 5, 1, 1, 1);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, cuboid);

        assertEquals("Euler characteristic is incorrect", -4, result.intValue());
    }

    /**
     * Regression test EulerCharacteristic with a hollow cuboid that doesn't touch edges
     * <p>
     * Here χ = β_0 - β_1 + β_2 = 1 - 0 + 1 = 2
     */
    @Test
    public void testCompute1HollowCuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(HollowCuboid.class, null, 5, 5, 5, 1, 1, 1);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, cuboid);

        assertEquals("Euler characteristic is incorrect", 2, result.intValue());
    }

    /**
     * Regression test EulerCharacteristic with a hollow cuboid that has a handle
     * <p>
     * Here χ = β_0 - β_1 + β_2 = 1 - 1 + 1 = 1
     */
    @Test
    public void testCompute1HollowCuboidHandle() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(HollowCuboid.class, null, 9, 9, 9, 1, 1, 5);

        // Draw a handle on the front xy-face of the cuboid
        final RandomAccess<BitType> access = cuboid.randomAccess();
        access.setPosition(9, X_DIM);
        access.setPosition(6, Y_DIM);
        access.setPosition(4, Z_DIM);
        access.get().setOne();
        access.setPosition(3, Z_DIM);
        access.get().setOne();
        access.setPosition(7, Y_DIM);
        access.get().setOne();
        access.setPosition(8, Y_DIM);
        access.get().setOne();
        access.setPosition(4, Z_DIM);
        access.get().setOne();

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, cuboid);

        assertEquals("Euler characteristic is incorrect", 1, result.intValue());
    }

    @Test
    public void testHyperStack() throws Exception {
        // Create a hyperstack with two channels and frames
        final ImgPlus<BitType> imgPlus = IJ1ImgPlus.createIJ1ImgPlus(IMAGE_J.op(), "", 3, 3, 3, 2, 2);
        final RandomAccess<BitType> access = imgPlus.randomAccess();
        // Add a particle to the middle of the stack in channel 1, frame 0
        access.setPosition(new long[]{1, 1, 1, 1, 0});
        access.get().setOne();
        // Add a particle to the middle of the stack in channel 0, frame 1
        access.setPosition(new long[]{1, 1, 0, 1, 1});
        access.get().setOne();

        // Tests channel 0, frame 0
        Double result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, imgPlus, Arrays.asList(0L, 0L, 0L, 0L, 0L));
        assertEquals("Euler characteristic is incorrect", 0, result.intValue());

        // Tests channel 1, frame 0
        result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, imgPlus, Arrays.asList(0L, 0L, 1L, 0L, 0L));
        assertEquals("Euler characteristic is incorrect", 1, result.intValue());

        // Tests channel 0, frame 1
        result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, imgPlus, Arrays.asList(0L, 0L, 0L, 0L, 1L));
        assertEquals("Euler characteristic is incorrect", 1, result.intValue());

        // Tests channel 1, frame 1
        result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, imgPlus, Arrays.asList(0L, 0L, 1L, 0L, 1L));
        assertEquals("Euler characteristic is incorrect", 0, result.intValue());
    }
}