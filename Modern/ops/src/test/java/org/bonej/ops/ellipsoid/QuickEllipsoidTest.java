package org.bonej.ops.ellipsoid;

import org.junit.Test;

import static org.junit.Assert.*;

public class QuickEllipsoidTest {
    /**
     * Test for {@link QuickEllipsoid#getSurfacePoints(double[][])}
     *
     * searches in positive y and positive z directions for the surface point of an axis-aligned ellipsoid
     * with axis lengths (1,2,3) and centre (1,1,1)
     *
     *
     * ASCII sketch of situation in the x==1 plane:
     * '*' denotes ellipsoid centre
     * 'o' denotes a surface point that should be found, and its coordinates are noted as (x,y,z)
     *  the y and z axes, and the ellipsoid cross-section in the (x=1)-plane are sketched too
     *
     *   z
     *   ^  (1,1,4)
     *   | -o-
     *   |/ | \
     *   /  |  \
     *  ||  *--o (1,3,1)
     *   \-----/---> y
     *    \__ /
     */
    @Test
    public void testSurfacePoints() {
        QuickEllipsoid e = new QuickEllipsoid(new double[]{1,2,3}, new double[]{1,1,1},new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        double [][] vectors = new double[2][3];
        vectors[0][2] = 1; //z-direction
        vectors[1][1] = 1; //y-direction

        final double[][] surfacePoints = e.getSurfacePoints(vectors);

        double [] surfacePoint0 = surfacePoints[0];
        assertEquals(1, surfacePoint0[0], 0.0);
        assertEquals(1, surfacePoint0[1], 0.0);
        assertEquals(4, surfacePoint0[2], 0.0);

        double [] surfacePoint1 = surfacePoints[1];
        assertEquals(1, surfacePoint1[0], 0.0);
        assertEquals(3, surfacePoint1[1], 0.0);
        assertEquals(1, surfacePoint1[2], 0.0);
    }

    /**
     * Test for {@link QuickEllipsoid#contains(double, double, double)}
     *
     * ASCII sketch of situation in the x==1 plane:
     * '*' denotes ellipsoid centre
     * 'o' denotes points that are tested for correct classification as inside/outside
     *  the ellipsoid cross-section in the (x=1)-plane are sketched too
     *
     *   z
     *   ^  (1,1,2)
     *   | ---
     *   |/ o \
     *   /     \
     *  ||  *   | o (1,4,1)
     *   \-----/---> y
     *    \__ /
     */

    @Test
    public void testContains() {
        QuickEllipsoid e = new QuickEllipsoid(new double[]{1,2,3}, new double[]{1,1,1},new double[][]{{1,0,0},{0,1,0},{0,0,1}});
        assertFalse(e.contains(1,4,1));
        assertTrue(e.contains(1,1,2));
    }
}
