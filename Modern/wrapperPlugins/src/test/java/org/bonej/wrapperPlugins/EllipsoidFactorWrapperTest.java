package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.stream.Stream;

import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.Test;

public class EllipsoidFactorWrapperTest {


    /**
     * test for EF wrapper findContactPointsForGivenDirections function
     *
     * uses a 6x6x6 byte array image representation of a cuboid that touches the image boundary at z=0
     * and otherwise has a surface with 1 pixel distance from the image boundary
     *
     * axis-aligned ellipsoid, centred at 3,3,2 and with x,y,z direction axis lengths 1,2.1,3.1
     * the six search directions are +- x-direction, +- y-direction and +- z-direction
     * since the ellipsoid is narrower than the FG in x, and the FG touches the image boundary at z=0
     * the ellipsoid will only find contact points in positive z and in both + and - y directions.
     * The expected boundary points are (3,5.1,2),(3,3,5.1), and (3,0.9,2)
     *
     */
    @Test
    public void testFindContactPoints() {
        //SETUP
        QuickEllipsoid e = new QuickEllipsoid(1,2.1,3.1,3,3,2,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        double [][] vectors = new double[6][3];
        vectors[0][0] = 1; //x-direction
        vectors[1][1] = 1; //y-direction
        vectors[2][2] = 1; //z-direction
        vectors[3][0] = -1; //-x-direction
        vectors[4][1] = -1; //-y-direction
        vectors[5][2] = -1; //-z-direction

        final byte[][] cubeImage = getCubeImage();

        double[][] expectedContact = {{3,5.1,2},{3,3,5.1},{3,0.9,2}};


        //EXECUTE
        EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = new ArrayList<>();
        wrapper.findContactPointsForGivenDirections(e, contactPoints, vectors, cubeImage,6,6,6);

        //VERIFY
        assertEquals(3, contactPoints.size());
        Stream.of(0,1,2).forEach
                (i -> Stream.of(0,1,2).forEach(
                        j -> assertEquals(expectedContact[i][j],contactPoints.get(i)[j],1e-12)));
    }

    /**
     * test for EF wrapper calculateTorque method
     *
     * see testFindContactPoints in this file for explanation on what contact points are used
     * based on these points, the torque is expected to be zero
     *
     */

    @Test
    public void testCalculateTorque() {
        QuickEllipsoid e = new QuickEllipsoid(1,2.1,3.1,3,3,2,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        double [][] vectors = new double[7][3];
        vectors[0][0] = 1; //x-direction
        vectors[1][1] = 1; //y-direction
        vectors[2][2] = 1; //z-direction
        vectors[3][0] = -1; //-x-direction
        vectors[4][1] = -1; //-y-direction
        vectors[5][2] = -1; //-z-direction

        final byte[][] cubeImage = getCubeImage();

        //EXECUTE
        EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = new ArrayList<>();
        wrapper.findContactPointsForGivenDirections(e, contactPoints, vectors, cubeImage,6,6,6);
        final double[] torque = EllipsoidFactorWrapper.calculateTorque(e, contactPoints);

        assertEquals(0,torque[0],1e-12);
        assertEquals(0,torque[1],1e-12);
        assertEquals(0,torque[2],1e-12);
    }

    @Test
    public void testWiggleSurfacePoint() {
        QuickEllipsoid e = new QuickEllipsoid(1,2,3,0,0,0,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        EllipsoidFactorWrapper.wiggle(e,new double[]{1,0,0});

        assertTrue("Wiggle does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
    }

    @Test
    public void testBumpSurfacePoint() {
        QuickEllipsoid e = new QuickEllipsoid(1,2,3,0,0,0,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        final EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = new ArrayList<>();
        contactPoints.add(new double[]{0,0,3});
        wrapper.bump(e, contactPoints, e.getCentre()[0], e.getCentre()[1], e.getCentre()[2], new double[]{1,0,0});

        assertTrue("Bump does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
    }

    @Test
    public void testTurnSurfacePoint() {
        QuickEllipsoid e = new QuickEllipsoid(1,2,3,0,0,0,new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        final EllipsoidFactorWrapper wrapper = new EllipsoidFactorWrapper();
        final ArrayList<double[]> contactPoints = new ArrayList<>();
        contactPoints.add(new double[]{0,0,3});
        wrapper.turn(e,contactPoints,getCubeImage(),6,6,6, new double[]{1,0,0});

        assertTrue("Bump does not preserve surface point.",onSurface(e, new double[]{1,0,0}));
    }

    private boolean onSurface(QuickEllipsoid e, double[] point) {

        final double[][] ev = e.getRotation();
        double[] c = e.getCentre();
        final double[] r = e.getRadii();

        final Matrix3d Q = new Matrix3d(
                ev[0][0],ev[0][1],ev[0][2],
                ev[1][0],ev[1][1],ev[1][2],
                ev[2][0],ev[2][1],ev[2][2]);
        final Matrix3d L = new Matrix3d(
                1.0/r[0]/r[0],0,0,
                0,1.0/r[1]/r[1],0,
                0,0,1.0/r[2]/r[2]);
        Matrix3d A = new Matrix3d();
        L.mul(Q,A);
        Matrix3d QT = new Matrix3d(Q);
        QT.transpose();
        QT.mul(A,A);

        Vector3d APMinusC = new Vector3d();
        Vector3d PMinusC = new Vector3d(point[0]-c[0],point[1]-c[1],point[2]-c[2]);
        A.transform(PMinusC, APMinusC);
        final double oneOnSurface = PMinusC.dot(APMinusC);

        return Math.abs(oneOnSurface-1.0)<1.e-12;
    }

    private byte[][] getCubeImage() {
        int dimension = 6;
        final byte[][] cubeImage = new byte[dimension][dimension*dimension];

        for(int x=0;x<dimension;x++) {
            for (int y = 0; y < dimension; y++) {
                for (int z = 0; z < dimension; z++) {
                    if (x != 0 && x != 5 && y != 0 && y != 5 && z != 5) //part of x=0,y=0 plane is on img boundary and FG
                    {
                        cubeImage[z][y * dimension + x] = (byte) 255;//will be -1 as byte has values in [-128,127]
                    }
                }
            }
        }
        return cubeImage;
    }
}
