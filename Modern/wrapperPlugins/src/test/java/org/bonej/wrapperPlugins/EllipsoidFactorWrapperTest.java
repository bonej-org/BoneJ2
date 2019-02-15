package org.bonej.wrapperPlugins;

import org.bonej.ops.ellipsoid.Ellipsoid;
import org.joml.Vector3d;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EllipsoidFactorWrapperTest {

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
}
