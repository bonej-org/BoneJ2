package org.bonej.ops.ellipsoids;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Test;

public class EllipsoidTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeEllipsoidRadiiThrowException()
    {
        Ellipsoid negative = new Ellipsoid(-1, 2, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNaNRadiusThrowsException()
    {
        Ellipsoid notANumber = new Ellipsoid(1,2,Double.NaN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZeroRadiusThrowException()
    {
        Ellipsoid zeroRadius = new Ellipsoid(1, 0, 3);
    }

    @Test
    public void testRadiiAreSorted()
    {
        Ellipsoid unsorted = new Ellipsoid(3,2,1);
        assertTrue("a > b!", unsorted.getA()<=unsorted.getB());
        assertTrue("b > c!", unsorted.getB()<=unsorted.getC());
    }

    //main method for manual visual testing
    public static void main(String[] args)
    {
        Ellipsoid axisAligned = new Ellipsoid(1,2,3);
        List<Vector3D> sampleVectors = axisAligned.sampleOnEllipsoid(10000);
        sampleVectors.forEach(v -> System.out.println(v.getX()+","+v.getY()+","+v.getZ()));
    }


}
