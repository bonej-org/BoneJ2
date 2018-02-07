package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Tries to do one step of an {@link Ellipsoid} decomposition, starting from a point and a list of possible other points to collide with
 * <p>
 * The ellipsoid decomposition is drawn from the paper of Bischoff and Kobbelt, 2002 (https://www.graphics.rwth-aachen.de/media/papers/ellipsoids1.pdf)
 * The variable naming widely follows Bischoff and Kobbelt's nomenclature, it is recommended to read this code in conjunction with the paper.
 * The Ellipsoid-Plane intersection is done slightly differently, using the {@link EllipsoidPlaneIntersectionOp}. This op starts growing an ellipsoid
 * from the starting point p, and keeps growing in various directions until it hits three more points (q,r,s). If less than four distinct points are found,
 * the op returns an empty ellipsoid. More details on the growing process are in the original publication.
 * </p>
 *
 * @author Alessandro Felder
 */
@Plugin(type = Op.class)
public class FindLocalEllipsoidOp extends AbstractBinaryFunctionOp<List<Vector3d>, ValuePair<Vector3d,Vector3d>,Optional<Ellipsoid>> {

    private static QuadricToEllipsoid quadricToEllipsoid = new QuadricToEllipsoid();
    private static EllipsoidPlaneIntersectionOp intersectionOp = new EllipsoidPlaneIntersectionOp();

    @Override
    public Optional<Ellipsoid> calculate(List<Vector3d> otherVertices, final ValuePair<Vector3d,Vector3d> startingVertexWithNormal) {

        VertexWithNormal p = new VertexWithNormal(startingVertexWithNormal);
        otherVertices.remove(p.getVertex());

        ValuePair<VertexWithNormal,Double> qAndRadius = calculateQ(otherVertices, p);
        if(qAndRadius==null) return Optional.empty();
        otherVertices.remove(qAndRadius.getA().getVertex());

        Vector3d c = new Vector3d(p.getNormal());
        c.scaleAdd(qAndRadius.getB(), p.getVertex());
        Matrix4d q1 = getQ1(c,qAndRadius.getB());
        Matrix4d q2 = getQ2(p,qAndRadius.getA());

        ValuePair<Vector3d,Double> rAndAlpha = calculateSurfacePointAndGreekCoefficient(q1, q2, otherVertices);
        if(rAndAlpha==null) return Optional.empty();
        otherVertices.remove(rAndAlpha.getA());

        Matrix4d q1PlusAlphaQ2 = new Matrix4d(q2);
        q1PlusAlphaQ2.mul(rAndAlpha.getB());
        q1PlusAlphaQ2.add(q1);

        Optional<Ellipsoid> ellipsoid = quadricToEllipsoid.calculate(q1PlusAlphaQ2);
        if(!ellipsoid.isPresent()) return Optional.empty();

        Matrix4d q3 = getQ3(Stream.of(p.getVertex(), qAndRadius.getA().getVertex(), rAndAlpha.getA()).collect(Collectors.toList()),ellipsoid.get());

        ValuePair<Vector3d,Double> sAndBeta = calculateSurfacePointAndGreekCoefficient(q1PlusAlphaQ2, q3, otherVertices);
        if(sAndBeta==null) return Optional.empty();

        Matrix4d q1PlusAlphaQ2plusBetaQ3 = new Matrix4d(q3);
        q1PlusAlphaQ2plusBetaQ3.mul(sAndBeta.getB());
        q1PlusAlphaQ2.add(q1PlusAlphaQ2plusBetaQ3);

        return quadricToEllipsoid.calculate(q1PlusAlphaQ2);
    }

    /**
     * calculates the quadric denoted by Q_1 in the original paper
     * @param sphereCentre centre of the sphere touching p and q
     * @param radius radius of the sphere touching p and q
     * @return Q_1
     */
    static Matrix4d getQ1(Vector3d sphereCentre, double radius){
        Matrix3d identity = new Matrix3d();
        identity.setIdentity();
        Vector3d minusSphereCentre = new Vector3d(-sphereCentre.getX(), -sphereCentre.getY(), -sphereCentre.getZ());
        Matrix4d q1 = new Matrix4d(identity,minusSphereCentre, 1.0);
        q1.setRow(3, new Vector4d(minusSphereCentre));
        q1.m33 = sphereCentre.lengthSquared()-radius*radius;
        return q1;
    }

    /**
     * Calculates the quadric denoted by Q_2 in the original paper
     * @param pnp p and normal at p
     * @param qnq q and normal at q
     * @return the 4x4 matrix Q_2
     */
    static Matrix4d getQ2(final VertexWithNormal pnp, final VertexWithNormal qnq){
        Vector3d p = pnp.getVertex();
        Vector3d np = pnp.getNormal();
        Vector3d q = qnq.getVertex();
        Vector3d nq = qnq.getNormal();

        Matrix3d NRotation = new Matrix3d();

        NRotation.m00 = -np.getX()*nq.getX();
        NRotation.m01 = -np.getX()*nq.getY();
        NRotation.m02 = -np.getX()*nq.getZ();

        NRotation.m10 = -np.getY()*nq.getX();
        NRotation.m11 = -np.getY()*nq.getY();
        NRotation.m12 = -np.getY()*nq.getZ();

        NRotation.m20 = -np.getZ()*nq.getX();
        NRotation.m21 = -np.getZ()*nq.getY();
        NRotation.m22 = -np.getZ()*nq.getZ();

        Matrix3d NRotationTransposed = new Matrix3d();
        NRotationTransposed.transpose(NRotation);
        Matrix3d q2Rotation = new Matrix3d(NRotation);
        q2Rotation.add(NRotationTransposed);
        q2Rotation.mul(0.5);

        double nqDotqHalf = nq.dot(q)/2.0;
        double npDotpHalf = np.dot(p)/2.0;

        Vector3d q2Translation = new Vector3d(np.getX()*nqDotqHalf+nq.getX()*npDotpHalf,np.getY()*nqDotqHalf+nq.getY()*npDotpHalf,np.getZ()*nqDotqHalf+nq.getZ()*npDotpHalf);

        Matrix4d quadric2 = new Matrix4d(q2Rotation,q2Translation, 1.0);
        quadric2.setRow(3, new Vector4d(q2Translation));
        quadric2.m33 = -np.dot(p)*nq.dot(q);
        return quadric2;
    }


    /**
     * Calculates the elliptic cylinder denoted by Q_3 in the original paper
     * @param pqr three non-collinear points defining a plane
     * @param ellipsoid ellipsoid through p,q, and r
     * @return a 4x4 matrix representation of the elliptic cylinder defined by the intersection ellipse of pqr and qBar
     */
    private static Matrix4d getQ3(final List<Vector3d> pqr, Ellipsoid ellipsoid)
    {
        Vector3d p = pqr.get(0);
        Vector3d q = pqr.get(1);
        Vector3d r = pqr.get(2);

        Vector3d interiorPoint = new Vector3d(p);
        interiorPoint.scaleAdd(-1.0,q);
        interiorPoint.scale(0.5);
        interiorPoint.add(p);

        Vector3d planeNormal = new Vector3d();
        q.scaleAdd(-1.0, p);
        r.scaleAdd(-1.0, p);
        planeNormal.cross(q,r);
        List<Vector3d> ellipse = intersectionOp.calculate(ellipsoid, new ValuePair<>(interiorPoint, planeNormal));

        Vector3d a0 = ellipse.get(0);
        Vector3d a1 = ellipse.get(1);
        Vector3d a2 = ellipse.get(2);

        Matrix4d quadric = new Matrix4d();

        //axis and translation contributions calculated with sympy - see documentation
        Matrix3d A1 = getAxisContribution(a1);
        Matrix3d A2 = getAxisContribution(a2);
        A2.add(A1);
        quadric.setRotationScale(A2);

        Vector4d translationVector1 = getTranslationContribution(a0, a1);
        Vector4d translationVector2 = getTranslationContribution(a0, a2);
        translationVector2.add(translationVector1);

        quadric.setRow(3, translationVector2);
        quadric.setColumn(3, translationVector2);

        return quadric;
    }

    private static Vector4d getTranslationContribution(final Vector3d centre, final Vector3d axis) {

        double radius = axis.length();

        Vector3d unitAxis = new Vector3d(axis);
        unitAxis.normalize();

        Vector4d translationVector = new Vector4d();

        translationVector.setX(-unitAxis.x*centre.dot(unitAxis));
        translationVector.setY(-unitAxis.y*centre.dot(unitAxis));
        translationVector.setZ(-unitAxis.z*centre.dot(unitAxis));

        double constantComponent = centre.x*centre.x*unitAxis.x*unitAxis.x;
        constantComponent += centre.y*centre.y*unitAxis.y*unitAxis.y;
        constantComponent += centre.z*centre.z*unitAxis.z*unitAxis.z;

        constantComponent += 2.0*centre.x*centre.y*unitAxis.x*unitAxis.y;
        constantComponent += 2.0*centre.x*centre.z*unitAxis.x*unitAxis.z;
        constantComponent += 2.0*centre.y*centre.z*unitAxis.y*unitAxis.z;

        translationVector.setW(constantComponent);
        translationVector.scale(1.0/(radius*radius));

        translationVector.add(new Vector4d(0,0,0,-0.5));
        return translationVector;
    }

    private static Matrix3d getAxisContribution(final Vector3d axis) {
        double radius = axis.length();
        Vector3d normalizedAxis = new Vector3d(axis);
        normalizedAxis.normalize();

        Matrix3d axisContribution = new Matrix3d();
        axisContribution.setM00(normalizedAxis.getX()*normalizedAxis.getX()/(radius*radius));
        axisContribution.setM11(normalizedAxis.getY()*normalizedAxis.getY()/(radius*radius));
        axisContribution.setM22(normalizedAxis.getZ()*normalizedAxis.getZ()/(radius*radius));

        axisContribution.setM01(normalizedAxis.getX()*normalizedAxis.getY()/(radius*radius));
        axisContribution.setM10(normalizedAxis.getX()*normalizedAxis.getY()/(radius*radius));

        axisContribution.setM02(normalizedAxis.getX()*normalizedAxis.getZ()/(radius*radius));
        axisContribution.setM20(normalizedAxis.getX()*normalizedAxis.getZ()/(radius*radius));

        axisContribution.setM12(normalizedAxis.getY()*normalizedAxis.getZ()/(radius*radius));
        axisContribution.setM21(normalizedAxis.getY()*normalizedAxis.getZ()/(radius*radius));

        return axisContribution;
    }


    private static ValuePair<VertexWithNormal,Double> calculateQ(final List<Vector3d> candidateQs, final VertexWithNormal p) {
        List<ValuePair<VertexWithNormal, Double>> candidateQAndRs = candidateQs.stream().map(q -> calculatePossibleQAndR(q,p)).filter(qnr -> qnr.getB()>0).collect(Collectors.toList());

        candidateQAndRs.sort(Comparator.comparingDouble(ValuePair::getB));

        if(candidateQAndRs.size() > 0)
            return candidateQAndRs.get(0);
        else
            return null;
    }

    private static ValuePair<VertexWithNormal,Double> calculatePossibleQAndR(Vector3d x, VertexWithNormal p)
    {
        Vector3d xMinusP = new Vector3d(p.getVertex());
        xMinusP.scaleAdd(-1.0, x);
        double distanceSquared = xMinusP.lengthSquared();
        double scalarProduct = xMinusP.dot(p.getNormal());

        if(scalarProduct<=0.0) return new ValuePair<>(null,-1.0);

        double radius = distanceSquared/(2*scalarProduct);
        Vector3d centre = new Vector3d(p.getNormal());
        centre.scaleAdd(radius,p.getVertex());
        Vector3d cMinusX = new Vector3d(x);
        cMinusX.scaleAdd(-1.0, centre);
        return new ValuePair<>(new VertexWithNormal(new ValuePair<>(x,cMinusX)),radius);

    }

    private static ValuePair<Vector3d, Double> calculateSurfacePointAndGreekCoefficient(final Matrix4d Q1, final Matrix4d Q2, final List<Vector3d> vertices)
    {
        final List<ValuePair<Vector3d,Double>> candidatePointsAndCoefficients = vertices.stream().map(v -> calculateCandidateSurfacePointAndGreekCoefficient(Q1, Q2, v)).filter(a -> !a.getB().isNaN()).collect(Collectors.toList());
        candidatePointsAndCoefficients.sort(Comparator.comparingDouble(ValuePair::getB));
        if(candidatePointsAndCoefficients.size() > 0)
            return candidatePointsAndCoefficients.get(0);
        else
            return null;
    }

    private static double calculateXtQX(final Matrix4d Q2, Vector3d x)
    {
        Vector4d x4d = new Vector4d(x);
        x4d.w = 1.0;
        Vector4d Q2X = new Vector4d(x4d);
        Q2.transform(Q2X);
        return x4d.dot(Q2X);
    }

    private static ValuePair<Vector3d, Double> calculateCandidateSurfacePointAndGreekCoefficient(final Matrix4d Q1, final Matrix4d Q2, final Vector3d x)
    {
        double xtQ2X = calculateXtQX(Q2, x);
        if(xtQ2X>=0) return new ValuePair<>(new Vector3d(), Double.NaN);

        double xtQ1X = calculateXtQX(Q1, x);
        return new ValuePair<>(new Vector3d(x),-xtQ1X/xtQ2X);
    }


}

class VertexWithNormal {

    private ValuePair<Vector3d,Vector3d> vwn;

    VertexWithNormal(ValuePair<Vector3d,Vector3d> vwn){
        this.vwn = vwn;
    }

    public Vector3d getVertex() {
        return new Vector3d(vwn.getA());
    }

    public Vector3d getNormal() {
        return new Vector3d(vwn.getB());
    }
}