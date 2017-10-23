package org.bonej.ops.ellipsoids;

import clojure.core.Vec;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Test;

import static org.junit.Assert.*;

public class findEllipsoidOpTest {
    @Test
    public void testRayInEmptyImage(){
        final Img<BitType> img = ArrayImgs.bits(10, 10, 10);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Empty test image");

        findEllipsoidOp<BitType> findEllipsoidOp = new findEllipsoidOp<>();
        findEllipsoidOp.setInput1(imgPlus);
        Vector3D lastFGVoxelAlongRay = findEllipsoidOp.findLastFGVoxelAlongRay(new Vector3D(1, 0, 0), new Vector3D(5, 5, 5));

        assertEquals(5,lastFGVoxelAlongRay.getX(),1.0e-12);
        assertEquals(5,lastFGVoxelAlongRay.getY(),1.0e-12);
        assertEquals(5,lastFGVoxelAlongRay.getZ(),1.0e-12);
    }

    @Test
    public void testRayInForegroundOnlyImage(){
        final Img<BitType> img = ArrayImgs.bits(10, 10, 10);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Empty test image");
        Cursor<BitType> cursor = imgPlus.cursor();

        cursor.forEachRemaining(BitType::setOne);

        findEllipsoidOp<BitType> findEllipsoidOp = new findEllipsoidOp<>();
        findEllipsoidOp.setInput1(imgPlus);
        Vector3D lastFGVoxelAlongRay = findEllipsoidOp.findLastFGVoxelAlongRay(new Vector3D(1, 0, 0), new Vector3D(5, 5, 5));

        assertEquals(9,lastFGVoxelAlongRay.getX(),1.0e-12);
        assertEquals(5,lastFGVoxelAlongRay.getY(),1.0e-12);
        assertEquals(5,lastFGVoxelAlongRay.getZ(),1.0e-12);
    }

    @Test
    public void testRayInSphere(){
        final Img<BitType> img = ArrayImgs.bits(100, 100, 100);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Empty test image");
        IntervalView<BitType> imgCentre = Views.interval(imgPlus, new long[]{25, 25, 25}, new long[]{75, 75, 75});
        Cursor<BitType> cursor = imgCentre.localizingCursor();

        while(cursor.hasNext())
        {
            cursor.fwd();
            long [] currentCoords = new long[3];
            cursor.localize(currentCoords);
            if((50-currentCoords[0])*(50-currentCoords[0])+(50-currentCoords[1])*(50-currentCoords[1])+(50-currentCoords[2])*(50-currentCoords[2]) <= 625)
                cursor.get().setOne();
        }

        findEllipsoidOp<BitType> findEllipsoidOp = new findEllipsoidOp<>();
        findEllipsoidOp.setInput1(imgPlus);
        Vector3D lastFGVoxelAlongRay1 = findEllipsoidOp.findLastFGVoxelAlongRay(new Vector3D(-4, -3, -5).normalize(), new Vector3D(50, 50, 50));
        Vector3D lastFGVoxelAlongRay2 = findEllipsoidOp.findLastFGVoxelAlongRay(new Vector3D(1, 1, 1).normalize(), new Vector3D(50, 50, 50));

        Vector3D radialVector1 = lastFGVoxelAlongRay1.subtract(new Vector3D(50,50,50));
        assertEquals(25,radialVector1.getNorm(),1e-12);
        Vector3D radialVector2 = lastFGVoxelAlongRay2.subtract(new Vector3D(50,50,50));
        assertEquals(26,radialVector2.getNorm(),1e-12);
    }
}