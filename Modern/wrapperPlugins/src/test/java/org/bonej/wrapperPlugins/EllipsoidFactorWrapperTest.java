package org.bonej.wrapperPlugins;

import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.integer.IntType;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.Test;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class EllipsoidFactorWrapperTest {

    @Test
    public void testProperRotation() {
        final Ellipsoid ellipsoid = new Ellipsoid(1,1,1);
        ellipsoid.setSemiAxes(new Vector3d(-1,0,0), new Vector3d(0,2,0), new Vector3d(0,0,3));
        final Matrix3d rotationMatrix = EllipsoidFactorWrapper.getProperRotationMatrix(ellipsoid);

        final Matrix3d expected = new Matrix3d();

        Stream.of(0,1,2).forEach
                (i -> Stream.of(0,1,2).forEach(
                        j -> assertEquals(expected.get(i,j),rotationMatrix.get(i,j),1e-12)));

    }

    @Test
    public void testSurfacePoints() {
        Ellipsoid e = new Ellipsoid(1,2,3);
        e.setCentroid(new Vector3d(1,1,1));

        double [][] vectors = new double[2][3];
        vectors[0][2] = 1; //z-direction
        vectors[1][1] = 1; //y-direction

        final double[][] surfacePoints = EllipsoidFactorWrapper.getSurfacePoints(e, vectors);

        double [] surfacePoint0 = surfacePoints[0];
        assertEquals(1, surfacePoint0[0], 0.0);
        assertEquals(1, surfacePoint0[1], 0.0);
        assertEquals(4, surfacePoint0[2], 0.0);

        double [] surfacePoint1 = surfacePoints[1];
        assertEquals(1, surfacePoint1[0], 0.0);
        assertEquals(3, surfacePoint1[1], 0.0);
        assertEquals(1, surfacePoint1[2], 0.0);
    }


    @Test
    public void testFindContactPoints() {
        //SETUP
        Ellipsoid e = new Ellipsoid(1,2.1,3.1);
        e.setCentroid(new Vector3d(3,3,2));

        double [][] vectors = new double[6][3];
        vectors[0][0] = 1; //x-direction
        vectors[1][1] = 1; //y-direction
        vectors[2][2] = 1; //z-direction
        vectors[3][0] = -1; //-x-direction
        vectors[4][1] = -1; //-y-direction
        vectors[5][2] = -1; //-z-direction

        final ArrayImg<IntType, IntArray> ints = getCubeImage();

        double[][] expectedContact = {{3,5.1,2},{3,3,5.1},{3,0.9,2}};

        //EXECUTE
        EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = wrapper.findContactPoints(e, new ArrayList<>(), vectors, ints);

        //VERIFY
        assertEquals(3, contactPoints.size());
        Stream.of(0,1,2).forEach
                (i -> Stream.of(0,1,2).forEach(
                        j -> assertEquals(expectedContact[i][j],contactPoints.get(i)[j],1e-12)));
    }

    @Test
    public void testCalculateTorque() {
        Ellipsoid e = new Ellipsoid(1,2.1,3.1);
        e.setCentroid(new Vector3d(3,3,2));

        double [][] vectors = new double[7][3];
        vectors[0][0] = 1; //x-direction
        vectors[1][1] = 1; //y-direction
        vectors[2][2] = 1; //z-direction
        vectors[3][0] = -1; //-x-direction
        vectors[4][1] = -1; //-y-direction
        vectors[5][2] = -1; //-z-direction

        final ArrayImg<IntType, IntArray> ints = getCubeImage();

        //EXECUTE
        EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = wrapper.findContactPoints(e, new ArrayList<>(), vectors, ints);
        final Vector3d torque = EllipsoidFactorWrapper.calculateTorque(e, contactPoints);

        assertEquals(torque.get(0),0,1e-12);
        assertEquals(torque.get(1),0,1e-12);
        assertEquals(torque.get(2),0,1e-12);
    }

    private ArrayImg<IntType, IntArray> getCubeImage() {
        final ArrayImg<IntType, IntArray> ints = ArrayImgs.ints(6, 6, 6);
        final ArrayLocalizingCursor<IntType> cursor = ints.localizingCursor();
        final ArrayRandomAccess<IntType> access = ints.randomAccess();

        while (cursor.hasNext()) {
            cursor.fwd();

            long[] position = new long[3];
            cursor.localize(position);
            long x = position[0];
            long y = position[1];
            long z = position[2];

            if (x != 0 && x != 5 && y != 0 && y != 5 && z != 5) //part of x=0,y=0 plane is on img boundary and FG
            {
                access.setPosition(position);
                access.get().set(255);
            }
        }
        return ints;
    }


}

