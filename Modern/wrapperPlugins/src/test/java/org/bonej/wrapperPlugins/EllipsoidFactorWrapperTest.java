package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.stream.Stream;

import org.bonej.ops.ellipsoid.QuickEllipsoid;
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
        wrapper.findContactPointsForGivenDirections(e, contactPoints, vectors, cubeImage,1,1,1,6,6,6);

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
        wrapper.findContactPointsForGivenDirections(e, contactPoints, vectors, cubeImage,1,1,1,6,6,6);
        final double[] torque = EllipsoidFactorWrapper.calculateTorque(e, contactPoints);

        assertEquals(0,torque[0],1e-12);
        assertEquals(0,torque[1],1e-12);
        assertEquals(0,torque[2],1e-12);
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
