package org.bonej.ops.connectivity;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.type.logic.BitType;
import org.bonej.testImages.Cuboid;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the {@link EulerContribution EulerContribution} Op
 *
 * @author Richard Domander 
 */
public class EulerContributionTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    /** Regression test EulerCharacteristic with a solid cuboid that never touches the edges of the stack */
    @Test
    public void testCompute1CuboidFreeFloat() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(Cuboid.class, null, 10, 10, 10, 1, 1, 5);

        final Integer result = (Integer) IMAGE_J.op().run(EulerContribution.class, cuboid);

        assertEquals("Euler contribution is incorrect", 0, result.intValue());
    }

    /**
     * Regression test EulerCharacteristic with a solid cuboid that's the same size as the image,
     * i.e. all faces touch the edges
     */
    @Test
    public void testCompute1CuboidStackSize() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(Cuboid.class, null, 10, 10, 10, 1, 1, 0);

        final Integer result = (Integer) IMAGE_J.op().run(EulerContribution.class, cuboid);

        assertEquals("Euler contribution is incorrect", 1, result.intValue());
    }
}