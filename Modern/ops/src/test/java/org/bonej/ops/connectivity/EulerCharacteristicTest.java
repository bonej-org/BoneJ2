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
 */
public class EulerCharacteristicTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testNeighborhoodEulerIndex() throws Exception {
        // Create all 256 configurations of a 2x2x2 neighborhood
        for (int n = 0; n < 256; n++) {
            RandomAccess<BitType> neighborhood = createNeighborhood();
            setValue(neighborhood, 0, 0, 0, n & 0b00000001);
            setValue(neighborhood, 1, 0, 0, n & 0b00000010);
            setValue(neighborhood, 0, 1, 0, n & 0b00000100);
            setValue(neighborhood, 1, 1, 0, n & 0b00001000);
            setValue(neighborhood, 0, 0, 1, n & 0b00010000);
            setValue(neighborhood, 1, 0, 1, n & 0b00100000);
            setValue(neighborhood, 0, 1, 1, n & 0b01000000);
            setValue(neighborhood, 1, 1, 1, n & 0b10000000);

            final int result = EulerCharacteristic.neighborhoodEulerIndex(neighborhood, 0, 0, 0, 0, 1, 2);

            assertEquals("Euler index is incorrect", n, result);
        }
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

    /** Regression test for EulerCharacteristic */
    @Test
    public void testCompute1Cuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(Cuboid.class, null, 10, 10, 10, 1, 1, 1);

        final Integer result = (Integer) IMAGE_J.op().run(EulerCharacteristic.class, cuboid);

        assertEquals("Euler characteristic is incorrect", 1, result.intValue());
    }

    /** Regression test for EulerCharacteristic */
    @Test
    public void testCompute1WireFrameCuboid() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(WireFrameCuboid.class, null, 10, 10, 10, 1, 1, 1);

        final Integer result = (Integer) IMAGE_J.op().run(EulerCharacteristic.class, cuboid);

        // I don't really understand why this is -4, but it's the same in BoneJ1
        assertEquals("Euler characteristic is incorrect", -4, result.intValue());
    }

    //region -- Helper methods --
    private RandomAccess<BitType> createNeighborhood() {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final Img<BitType> img = IMAGE_J.op().create().img(new FinalDimensions(2, 2, 2), new BitType());
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "", xAxis, yAxis, zAxis);

        return imgPlus.randomAccess();
    }

    private void setValue(final RandomAccess<BitType> access, final int x, final int y, final int z,
            final int value) {
        access.setPosition(x, 0);
        access.setPosition(y, 1);
        access.setPosition(z, 2);
        access.get().setInteger(value);
    }
    //endregion
}