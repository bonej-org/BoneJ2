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
import org.bonej.testImages.WireFrameCuboid;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the {@link EulerCharacteristic EulerCharacteristic} Op
 *
 * @author Richard Domander
 * //TODO Add regression tests for torus, hollow sphere etc...
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

    /** Regression test EulerCharacteristic with a solid cuboid that touches the edges of the stack */
    @Test
    public void testCompute1Cuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(Cuboid.class, null, 10, 10, 10, 1, 1, 0);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, cuboid);

        assertEquals("Euler characteristic is incorrect", 1, result.intValue());
    }

    /**
     * Regression test EulerCharacteristic with a hollow wire-frame cuboid that doesn't touch edges
     * <p>
     * Here χ = V - E + F, where V, E, F are the numbers of vertices, edges and faces.
     * In this case χ = V - E + F = 8 - 12 + 0 = -4
     */
    @Test
    public void testCompute1WireFrameCuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(WireFrameCuboid.class, null, 10, 10, 10, 1, 1, 1);

        final Double result = (Double) IMAGE_J.op().run(EulerCharacteristic.class, cuboid);

        // I don't really understand why this is -4, but it's the same in BoneJ1
        assertEquals("Euler characteristic is incorrect", -4, result.intValue());
    }
}