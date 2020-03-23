package org.bonej.ops.skeletonize;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.AbstractOpTest;
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
import org.junit.Ignore;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.cache.CacheService;
import org.scijava.plugin.Parameter;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class FindRidgePointsTest extends AbstractOpTest {

    @Test
    public void testSphereRidge() throws Exception {
        //SET UP
        final Img<BitType> sphereImage = getSphereImage();
        final Vector3dc expectedSingleRidgePoint = new Vector3d(50.5, 50.5, 50.5);

        //EXECUTE
        final List<Vector3dc> ridgePointList = (List) ops.run(FindRidgePoints.class, new ImgPlus<>(sphereImage,
                "Sphere test image", new AxisType[] { Axes.X, Axes.Y, Axes.Z },
                new double[] { 1.0, 1.0, 1.0 }, new String[] { "", "", "" }));

        //VERIFY
        assertEquals(1, ridgePointList.size());
        assertEquals("Ridge point x-coordinate is wrong", expectedSingleRidgePoint.x(), ridgePointList.get(0).x(),0);
        assertEquals("Ridge point y-coordinate is wrong", expectedSingleRidgePoint.y(), ridgePointList.get(0).y(),0);
        assertEquals("Ridge point z-coordinate is wrong", expectedSingleRidgePoint.z(), ridgePointList.get(0).z(),0);
    }


    //TODO move to somewhere where all tests can find this.
    private static Img<BitType> getSphereImage() {
        final long[] imageDimensions = { 101, 101, 101 };
        final Vector3dc centre = new Vector3d(Math.floor(imageDimensions[0] / 2.0),
                Math.floor(imageDimensions[1] / 2.0), Math.floor(imageDimensions[2] /
                2.0));
        final int radius = 10;
        final Img<BitType> sphereImg = ArrayImgs.bits(imageDimensions[0],
                imageDimensions[1], imageDimensions[2]);
        final Cursor<BitType> cursor = sphereImg.localizingCursor();
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
        return sphereImg;
    }
}