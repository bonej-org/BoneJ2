package org.bonej.ops.ellipsoids;

import static org.junit.Assert.assertEquals;

import java.util.List;

import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Test;

public class FindEllipsoidOpTest {
    @Test
    public void testContactPointsInCylinder()
    {
        long[] imageDimensions = {100,100,100};
        Vector3D imageCentre = new Vector3D(Math.floor(imageDimensions[0]/2),Math.floor(imageDimensions[1]/2),Math.floor(imageDimensions[2]/2));
        long cylinderRadius = 25;

        final Img<BitType> img = ArrayImgs.bits(imageDimensions[0], imageDimensions[1], imageDimensions[2]);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Cylinder test image");
        Cursor<BitType> cursor = imgPlus.localizingCursor();

        while(cursor.hasNext())
        {
            cursor.fwd();
            long [] coordinates = new long[3];
            cursor.localize(coordinates);
            if(coordinates[2]==0 || coordinates[2]==imageDimensions[2]) continue;
            long x = (long)(imageCentre.getX()-coordinates[0]);
            long y = (long)(imageCentre.getY()-coordinates[1]);
            double distanceFromCentreLine = x*x+y*y;
            if( distanceFromCentreLine <= cylinderRadius*cylinderRadius)
                cursor.get().setOne();
        }

        FindEllipsoidOp<BitType> findEllipsoidOp = new FindEllipsoidOp<>();

        findEllipsoidOp.setInput1(imgPlus);
        findEllipsoidOp.setInput2(imageCentre);

        Ellipsoid sphere = findEllipsoidOp.calculate();

        assertEquals(cylinderRadius, sphere.getA(), 1+1e-12);
        assertEquals(cylinderRadius, sphere.getB(), 1+1e-12);
        assertEquals(imageDimensions[2]/2, sphere.getC(), 1+1e-12);

    }

    @Test
    public void testRayInEmptyImage(){
        final Img<BitType> img = ArrayImgs.bits(10, 10, 10);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Empty test image");

        FindEllipsoidOp<BitType> FindEllipsoidOp = new FindEllipsoidOp<>();
        FindEllipsoidOp.setInput1(imgPlus);
        Vector3D lastFGVoxelAlongRay = FindEllipsoidOp.findFirstBGVoxelAlongRay(new Vector3D(1, 0, 0), new Vector3D(5, 5, 5));

        assertEquals(5,lastFGVoxelAlongRay.getX(),1.0e-12);
        assertEquals(5,lastFGVoxelAlongRay.getY(),1.0e-12);
        assertEquals(5,lastFGVoxelAlongRay.getZ(),1.0e-12);
    }

    @Test
    public void testRayInForegroundOnlyImage(){
        final Img<BitType> img = ArrayImgs.bits(10, 10, 10);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Foreground only test image");
        Cursor<BitType> cursor = imgPlus.cursor();

        cursor.forEachRemaining(BitType::setOne);

        FindEllipsoidOp<BitType> FindEllipsoidOp = new FindEllipsoidOp<>();
        FindEllipsoidOp.setInput1(imgPlus);
        Vector3D lastFGVoxelAlongRay = FindEllipsoidOp.findFirstBGVoxelAlongRay(new Vector3D(1, 0, 0), new Vector3D(5, 5, 5));

        assertEquals(9,lastFGVoxelAlongRay.getX(),1.0e-12);
        assertEquals(5,lastFGVoxelAlongRay.getY(),1.0e-12);
        assertEquals(5,lastFGVoxelAlongRay.getZ(),1.0e-12);
    }

    @Test
    public void testRayInSphere(){
        final Img<BitType> img = ArrayImgs.bits(100, 100, 100);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Sphere test image");
        IntervalView<BitType> imgCentre = Views.interval(imgPlus, new long[]{25, 25, 25}, new long[]{75, 75, 75});
        Cursor<BitType> cursor = imgCentre.localizingCursor();

        while(cursor.hasNext())
        {
            cursor.fwd();
            long [] coordinates = new long[3];
            cursor.localize(coordinates);
            if((50-coordinates[0])*(50-coordinates[0])+(50-coordinates[1])*(50-coordinates[1])+(50-coordinates[2])*(50-coordinates[2]) <= 625)
                cursor.get().setOne();
        }

        FindEllipsoidOp<BitType> FindEllipsoidOp = new FindEllipsoidOp<>();
        FindEllipsoidOp.setInput1(imgPlus);
        Vector3D lastFGVoxelAlongRay1 = FindEllipsoidOp.findFirstBGVoxelAlongRay(new Vector3D(-4, -3, -5).normalize(), new Vector3D(50, 50, 50));
        Vector3D lastFGVoxelAlongRay2 = FindEllipsoidOp.findFirstBGVoxelAlongRay(new Vector3D(1, 1, 1).normalize(), new Vector3D(50, 50, 50));

        Vector3D radialVector1 = lastFGVoxelAlongRay1.subtract(new Vector3D(50,50,50));
        assertEquals(25,radialVector1.getNorm(),1e-12);
        Vector3D radialVector2 = lastFGVoxelAlongRay2.subtract(new Vector3D(50,50,50));
        assertEquals(26,radialVector2.getNorm(),1e-12);
    }

    //main method for manual visual testing
    public static void main(String[] args)
    {
        List<Vector3D> spiralVectors = FindEllipsoidOp.getGeneralizedSpiralSetOnSphere(700);
        spiralVectors.forEach(v -> System.out.println(v.getX()+","+v.getY()+","+v.getZ()));
    }
}