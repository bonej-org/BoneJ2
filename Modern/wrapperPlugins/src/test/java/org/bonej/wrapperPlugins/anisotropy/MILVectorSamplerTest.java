package org.bonej.wrapperPlugins.anisotropy;

import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import org.bonej.wrapperPlugins.SlowWrapperTest;
import org.joml.Vector3dc;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MILVectorSamplerTest {
    private static final int DIRECTIONS = 25;
    private static ImageJ IMAGE_J = new ImageJ();
    private final Img<BitType> emptyImage = ArrayImgs.bits(5, 5, 5);
    private MILVectorSampler sampler;

    @Test
    public void testVectorCount() throws ExecutionException, InterruptedException {
        final List<Vector3dc> mILVectors = sampler.sample(emptyImage);

        assertEquals(DIRECTIONS, mILVectors.size());
    }

    @Test
    public void testVectorLength() throws ExecutionException, InterruptedException {
        final long w = emptyImage.dimension(0);
        final long h = emptyImage.dimension(1);
        final long d = emptyImage.dimension(2);
        final double expectedLength = Math.sqrt(w*w + h*h + d*d);

        final List<Vector3dc> mILVectors = sampler.sample(emptyImage);

        mILVectors.forEach(v -> assertEquals(expectedLength, v.length(), 1e-12));
    }

    @Category(SlowWrapperTest.class)
    @Test
    public void testObserverNotifiedForEveryDirection()
            throws ExecutionException, InterruptedException {
        final ProgressObserver observer = mock(ProgressObserver.class);
        sampler.setObserver(observer);

        sampler.sample(emptyImage);

        for (int i = 1; i <= DIRECTIONS; i++) {
            verify(observer).updateProgress(eq(i), eq(DIRECTIONS));
        }
    }

    @Before
    public void setup() {
        sampler = new MILVectorSampler(IMAGE_J.op(), DIRECTIONS, 1);
        sampler.setSeed(12345L);
    }

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
        IMAGE_J = null;
    }
}