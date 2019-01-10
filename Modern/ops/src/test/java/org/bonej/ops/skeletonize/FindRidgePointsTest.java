package org.bonej.ops.skeletonize;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpMatchingService;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.cache.CacheService;
import org.scijava.plugin.Parameter;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class FindRidgePointsTest {

    //TODO: can I make this extend the imagej-ops AbstractTestOp class
    @Parameter
    protected Context context;

    @Parameter
    protected OpService ops;

    @Parameter
    protected OpMatchingService matcher;

    /** Subclasses can override to create a context with different services. */
    protected Context createContext() {
        return new Context(OpService.class, OpMatchingService.class,
                CacheService.class);
    }

    /** Sets up a SciJava context with {@link OpService}. */
    @Before
    public void setUp() {
        createContext().inject(this);
    }

    /**
     * Disposes of the {@link OpService} that was initialized in {@link #setUp()}.
     */
    @After
    public synchronized void cleanUp() {
        if (context != null) {
            context.dispose();
            context = null;
            ops = null;
            matcher = null;
        }
    }

    @Test
    public void testSphereRidge() throws Exception {
        final ImgPlus<BitType> sphereImage = getSphereImage();

        final List<Object> outputs = (List) ops.run(FindRidgePoints.class, sphereImage);
        final List<Vector3dc> ridgePointList = (List<Vector3dc>) outputs.get(0);

        assertEquals(1, ridgePointList.size());
    }


    private static ImgPlus<BitType> getSphereImage() {
        final long[] imageDimensions = { 101, 101, 101 };
        final Vector3dc centre = new Vector3d(Math.floor(imageDimensions[0] / 2.0),
                Math.floor(imageDimensions[1] / 2.0), Math.floor(imageDimensions[2] /
                2.0));
        final int radius = 10;
        final Img<BitType> sphereImg = ArrayImgs.bits(imageDimensions[0],
                imageDimensions[1], imageDimensions[2]);
        final ImgPlus<BitType> sphereImgPlus = new ImgPlus<>(sphereImg,
                "Sphere test image", new AxisType[] { Axes.X, Axes.Y, Axes.Z },
                new double[] { 1.0, 1.0, 1.0 }, new String[] { "", "", "" });
        final Cursor<BitType> cursor = sphereImgPlus.localizingCursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            final long[] coordinates = new long[3];
            cursor.localize(coordinates);
            final Vector3d v = centre.add(-coordinates[0], -coordinates[1],
                    -coordinates[2], new Vector3d());
            if (v.lengthSquared() <= radius * radius) {
                cursor.get().setOne();
            }
        }
        return sphereImgPlus;
    }
}