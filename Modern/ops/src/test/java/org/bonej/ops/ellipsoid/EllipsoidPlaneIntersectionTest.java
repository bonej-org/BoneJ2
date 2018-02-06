package org.bonej.ops.ellipsoid;

import net.imglib2.util.ValuePair;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.junit.Test;
import org.scijava.vecmath.Vector3d;

import java.util.List;

import static org.junit.Assert.*;

public class EllipsoidPlaneIntersectionTest {

    UnitSphereRandomVectorGenerator sphereRNG = new UnitSphereRandomVectorGenerator(3);

    @Test
    public void calculate() throws Exception {
    }

    @Test
    public void testGenerationOfBasis() throws Exception {
        for(int i=0; i<10; i++) {
            double[] randomVector = sphereRNG.nextVector();
            Vector3d planeNormal = new Vector3d(randomVector[0], randomVector[1], randomVector[2]);
            planeNormal.normalize();

            Vector3d axisLengths = new Vector3d(1, 2, 3);
            assertTrue(basisConforms(axisLengths, planeNormal));
        }
    }

    private boolean basisConforms(Vector3d semiAxisLengths, Vector3d n)
    {
        List<Vector3d> basis = EllipsoidPlaneIntersection.completeBasis(semiAxisLengths,n);
        Vector3d r = basis.get(0);
        Vector3d s = basis.get(1);

        double a = semiAxisLengths.x;
        double b = semiAxisLengths.y;
        double c = semiAxisLengths.z;

        double tol = 1.0e-12;

        boolean basisVectorsUnitized = approximatelyEqual(n.length(),1.0, tol) && approximatelyEqual(r.length(), 1.0, tol) && approximatelyEqual(s.length(),1.0,tol);
        boolean basisVectorsOrthogonal = approximatelyEqual(r.dot(n), 0.0, tol) && approximatelyEqual(s.dot(n), 0.0, tol) && approximatelyEqual(r.dot(s), 0.0, tol);
        boolean equation7fulfilled = approximatelyEqual(r.x * s.x / (a * a) + r.y * s.y / (b * b) + r.z * s.z / (c * c),0.0, tol);

        return  basisVectorsUnitized && basisVectorsOrthogonal && equation7fulfilled;
    }

    private boolean approximatelyEqual(double one, double two, double tol)
    {
        return (Math.abs(one-two)<tol);
    }
}