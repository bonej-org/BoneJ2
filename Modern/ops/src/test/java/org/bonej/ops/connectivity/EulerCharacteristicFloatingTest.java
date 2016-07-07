package org.bonej.ops.connectivity;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.bonej.testImages.Cuboid;
import org.bonej.testImages.HollowCuboid;
import org.bonej.testImages.WireFrameCuboid;
import org.junit.AfterClass;
import org.junit.Test;

import static org.bonej.testImages.IJ1ImgPlus.*;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the {@link EulerCharacteristicFloating EulerCharacteristicFloating} Op
 *
 * @author Richard Domander
 */
public class EulerCharacteristicFloatingTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEulerCharacteristicMatchingFailsIfNotAtLeast3D() {
        final Img<BitType> img = IMAGE_J.op().create().img(new FinalDimensions(10, 10), new BitType());
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img);

        IMAGE_J.op().op(EulerCharacteristicFloating.class, imgPlus);
    }

    /**
     * Regression test EulerCharacteristicFloating with a solid cuboid that touches the edges of the stack
     * <p>
     * Here χ = β_0 - β_1 + β_2 = 1 - 0 + 0 = 1.
     * The formula χ = vertices - edges + faces for surfaces of polyhedra doesn't apply because the cuboid is solid.
     */
    @Test
    public void testCompute1Cuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(Cuboid.class, null, 5, 5, 5, 1, 1, 0);
        final IntervalView<BitType> view = Views.permute(cuboid, 2, 3);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristicFloating.class, view);

        assertEquals("Euler characteristic is incorrect", 1, result.intValue());
    }

    /**
     * Regression test EulerCharacteristicFloating with a wire-frame cuboid that doesn't touch edges
     * <p>
     * Here χ = vertices - edges + faces = V - E + F = 8 - 12 + 0 = -4
     */
    @Test
    public void testCompute1WireFrameCuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(WireFrameCuboid.class, null, 5, 5, 5, 1, 1, 1);
        final IntervalView<BitType> view = Views.permute(cuboid, 2, 3);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristicFloating.class, view);

        assertEquals("Euler characteristic is incorrect", -4, result.intValue());
    }

    /**
     * Regression test EulerCharacteristicFloating with a hollow cuboid that doesn't touch edges
     * <p>
     * Here χ = β_0 - β_1 + β_2 = 1 - 0 + 1 = 2
     */
    @Test
    public void testCompute1HollowCuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(HollowCuboid.class, null, 5, 5, 5, 1, 1, 1);
        final IntervalView<BitType> view = Views.permute(cuboid, 2, 3);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristicFloating.class, view);

        assertEquals("Euler characteristic is incorrect", 2, result.intValue());
    }

    /**
     * Regression test EulerCharacteristicFloating with a hollow cuboid that has a handle
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

        // Swap channel and z axes
        final IntervalView<BitType> view = Views.permute(cuboid, 2, 3);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristicFloating.class, view);

        assertEquals("Euler characteristic is incorrect", 1, result.intValue());
    }
}