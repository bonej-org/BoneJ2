package org.bonej.ops.ellipsoid;

import net.imglib2.util.ValuePair;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;


public class EllipsoidPlaneIntersectionTest {

    UnitSphereRandomVectorGenerator sphereRNG = new UnitSphereRandomVectorGenerator(3);

    @Test
    public void testGeneralEllipsoidAndPlane() throws Exception {
        Ellipsoid ellipsoid = new Ellipsoid(1,2,3);

        double oneOverSqrtThree = 1.0/Math.sqrt(3.0);
        Vector3d firstAxis = new Vector3d(new Vector3d(oneOverSqrtThree,oneOverSqrtThree,oneOverSqrtThree));
        Vector3d secondAxis = new Vector3d(new Vector3d(-oneOverSqrtThree,0,oneOverSqrtThree));
        secondAxis.normalize();
        Vector3d thirdAxis = new Vector3d();
        firstAxis.cross(secondAxis, thirdAxis);


        Matrix3d orientation = new Matrix3d();
        orientation.setColumn(0, firstAxis);
        orientation.setColumn(1, secondAxis);
        orientation.setColumn(2, thirdAxis);

        ellipsoid.setOrientation(orientation);


        ValuePair<Vector3d,Vector3d> plane = new ValuePair<>(new Vector3d(0,0,0.5),new Vector3d(0,0,1));

        EllipsoidPlaneIntersection intersection = new EllipsoidPlaneIntersection();
        final List<Vector3d> intersectionEllipse = intersection.calculate(ellipsoid, plane);

        assertTrue(intersectionEllipse.size()==3);

        Vector3d ellipseCentre = intersectionEllipse.get(0);
        Vector3d ellipseAxisA = intersectionEllipse.get(1);
        Vector3d ellipseAxisB = intersectionEllipse.get(2);
        assertEquals(ellipseAxisA.dot(ellipseAxisB),0.0, 1.0e-12);

        Vector3d pointOnPlaneAndEllipsoidA = new Vector3d(ellipseCentre);
        pointOnPlaneAndEllipsoidA.add(ellipseAxisA);
        Vector3d pointOnPlaneAndEllipsoidB = new Vector3d(ellipseCentre);
        pointOnPlaneAndEllipsoidB.add(ellipseAxisB);

        assertTrue(fulfillsPlaneEquation(pointOnPlaneAndEllipsoidA, plane));
        assertTrue(fulfillsEllipsoidEquation(pointOnPlaneAndEllipsoidA, ellipsoid));
        assertTrue(fulfillsPlaneEquation(pointOnPlaneAndEllipsoidB, plane));
        assertTrue(fulfillsEllipsoidEquation(pointOnPlaneAndEllipsoidB, ellipsoid));
    }

    @Test
    public void testAxisAlignedEllipsoidWithObliquePlane() throws Exception {
        Ellipsoid axisAligned = new Ellipsoid(1,2,3);
        double oneOverSqrtThree = 1.0/Math.sqrt(3.0);
        ValuePair<Vector3d,Vector3d> obliquePlane = new ValuePair<>(new Vector3d(0,0,0.5),new Vector3d(oneOverSqrtThree,oneOverSqrtThree,oneOverSqrtThree));

        EllipsoidPlaneIntersection intersection = new EllipsoidPlaneIntersection();
        List<Vector3d> axisAlignedIntersectionEllipse = intersection.findAxisAlignedCentredIntersectionEllipse(new Vector3d(axisAligned.getA(), axisAligned.getB(), axisAligned.getC()), obliquePlane);

        assertTrue(axisAlignedIntersectionEllipse.size()==3);

        Vector3d ellipseCentre = axisAlignedIntersectionEllipse.get(0);
        Vector3d ellipseAxisA = axisAlignedIntersectionEllipse.get(1);
        Vector3d ellipseAxisB = axisAlignedIntersectionEllipse.get(2);
        assertEquals(ellipseAxisA.dot(ellipseAxisB),0.0, 1.0e-12);

        Vector3d pointOnPlaneAndEllipsoidA = new Vector3d(ellipseCentre);
        pointOnPlaneAndEllipsoidA.add(ellipseAxisA);
        Vector3d pointOnPlaneAndEllipsoidB = new Vector3d(ellipseCentre);
        pointOnPlaneAndEllipsoidB.add(ellipseAxisB);

        assertTrue(fulfillsPlaneEquation(pointOnPlaneAndEllipsoidA, obliquePlane));
        assertTrue(fulfillsEllipsoidEquation(pointOnPlaneAndEllipsoidA, axisAligned));
        assertTrue(fulfillsPlaneEquation(pointOnPlaneAndEllipsoidB, obliquePlane));
        assertTrue(fulfillsEllipsoidEquation(pointOnPlaneAndEllipsoidB, axisAligned));
    }

    @Test
    public void testAxisAlignedEllipsoidWithParallelPlane() throws Exception {
        Ellipsoid axisAligned = new Ellipsoid(1,2,3);
        ValuePair<Vector3d,Vector3d> XYPlane = new ValuePair<>(new Vector3d(0,0,0.25),new Vector3d(0,0,1));

        EllipsoidPlaneIntersection intersection = new EllipsoidPlaneIntersection();
        List<Vector3d> axisAlignedIntersectionEllipse = intersection.findAxisAlignedCentredIntersectionEllipse(new Vector3d(axisAligned.getA(), axisAligned.getB(), axisAligned.getC()), XYPlane);

        assertTrue(axisAlignedIntersectionEllipse.size()==3);

        Vector3d ellipseCentre = axisAlignedIntersectionEllipse.get(0);
        assertEquals(ellipseCentre.x, 0, 1.0e-12);
        assertEquals(ellipseCentre.y, 0, 1.0e-12);
        assertEquals(ellipseCentre.z, XYPlane.getA().z, 1.0e-12);

        Vector3d ellipseAxisA = axisAlignedIntersectionEllipse.get(1);
        Vector3d ellipseAxisB = axisAlignedIntersectionEllipse.get(2);
        assertEquals(ellipseAxisA.dot(ellipseAxisB),0.0, 1.0e-12);

        Vector3d pointOnPlaneAndEllipsoidA = new Vector3d(ellipseCentre);
        pointOnPlaneAndEllipsoidA.add(ellipseAxisA);
        Vector3d pointOnPlaneAndEllipsoidB = new Vector3d(ellipseCentre);
        pointOnPlaneAndEllipsoidB.add(ellipseAxisB);

        assertTrue(fulfillsPlaneEquation(pointOnPlaneAndEllipsoidA, XYPlane));
        assertTrue(fulfillsEllipsoidEquation(pointOnPlaneAndEllipsoidA, axisAligned));
        assertTrue(fulfillsPlaneEquation(pointOnPlaneAndEllipsoidB, XYPlane));
        assertTrue(fulfillsEllipsoidEquation(pointOnPlaneAndEllipsoidB, axisAligned));

    }


    private boolean fulfillsEllipsoidEquation(final Vector3d point, final Ellipsoid ellipsoid) {
        Matrix3d Q = new Matrix3d();
        ellipsoid.getOrientation().get3x3(Q);

        //A = Q*A*Q^T
        Matrix3d D = new Matrix3d();
        D.scaling(1.0/(ellipsoid.getA()*ellipsoid.getA()),1.0/(ellipsoid.getB()*ellipsoid.getB()),1.0/(ellipsoid.getC()*ellipsoid.getC()));
        Matrix3d A = new Matrix3d();
        Q.mul(D,A);

        Matrix3d QT = Q.transpose();
        A.mul(QT,A);

        Vector3d pRelativeToCentre = ellipsoid.getCentroid();
        pRelativeToCentre.mul(-1.0);
        pRelativeToCentre.add(point);

        Vector3d Ap = new Vector3d(pRelativeToCentre);
        A.transform(Ap);

        return approximatelyEqual(pRelativeToCentre.dot(Ap),1.0,1.0e-12);
    }

    private boolean fulfillsPlaneEquation(final Vector3d point, final ValuePair<Vector3d, Vector3d> plane) {
        return approximatelyEqual(plane.getB().dot(point), plane.getA().dot(plane.getB()),1.0e-12);
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