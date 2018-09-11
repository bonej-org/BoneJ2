package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.joml.*;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Tries to do one step of an {@link Ellipsoid} decomposition, starting from a point and a list of possible other points to collide with
 * <p>
 * The ellipsoid decomposition is drawn from the paper of Bischoff and Kobbelt, 2002 (https://www.graphics.rwth-aachen.de/media/papers/ellipsoids1.pdf)
 * The variable naming widely follows Bischoff and Kobbelt's nomenclature, it is recommended to read this code in conjunction with the paper.
 * The Ellipsoid-Plane intersection is done slightly differently, using the {@link EllipsoidPlaneIntersection}. This op starts growing an ellipsoid
 * from the starting point p, and keeps growing in various directions until it hits three more points (q,r,s). If less than four distinct points are found,
 * the op returns an empty ellipsoid. More details on the growing process are in the original publication.
 * </p>
 *
 * @author Alessandro Felder
 */
@Plugin(type = Op.class)
public class FindEllipsoidFromBoundaryPoints extends AbstractBinaryFunctionOp<List<ValuePair<Vector3d,Vector3d>>,Vector3d,Optional<Ellipsoid>> {

    private static QuadricToEllipsoid quadricToEllipsoid = new QuadricToEllipsoid();
    private static EllipsoidPlaneIntersection intersectionOp = new EllipsoidPlaneIntersection();

    @Parameter(persist=false, required = false)
    private double calibratedLargestImageDimension = 100.0;

    @Override
    public Optional<Ellipsoid>calculate(List<ValuePair<Vector3d,Vector3d>> fourVerticesWithNormals, Vector3d centre) {
        List<Vector3d> fourVertices = new ArrayList<>();
        fourVerticesWithNormals.forEach(v -> fourVertices.add(v.getA()));

        fourVerticesWithNormals.sort(Comparator.comparingDouble(p->euclideanDistance(p.getA(),centre)));
        VertexWithNormal p = new VertexWithNormal(fourVerticesWithNormals.get(0));
        fourVertices.remove(p.getVertex());

        ValuePair<VertexWithNormal, Double> qAndRadius = calculateQ(fourVertices, p);
        if (qAndRadius == null || qAndRadius.getB()>calibratedLargestImageDimension) return Optional.empty();
        fourVertices.remove(qAndRadius.getA().getVertex());

        Vector3d np = new Vector3d(p.getNormal());
        np.mul(qAndRadius.getB());
        p.setNormal(np);

        Vector3d c = new Vector3d(p.getNormal());
        c.add(p.getVertex());
        Matrix4d q1 = getQ1(c, qAndRadius.getB());
        Matrix4d q2 = getQ2(p, qAndRadius.getA());

        ValuePair<Vector3d, Double> rAndAlpha = calculateSurfacePointAndGreekCoefficient(q1, q2, fourVertices);
        if (rAndAlpha == null) return Optional.empty();
        fourVertices.remove(rAndAlpha.getA());

        Matrix4d q1PlusAlphaQ2 = new Matrix4d(q2);
        Matrix4d fullScalingMatrix = new Matrix4d();
        fullScalingMatrix.scaling(rAndAlpha.getB());
        fullScalingMatrix.m33(rAndAlpha.getB());
        q1PlusAlphaQ2.mul(fullScalingMatrix, q1PlusAlphaQ2);
        q1PlusAlphaQ2.add(q1, q1PlusAlphaQ2);

        Optional<Ellipsoid> ellipsoid = quadricToEllipsoid.calculate(q1PlusAlphaQ2);
        if(!ellipsoid.isPresent()) return ellipsoid;

        Matrix4d q3 = getQ3(Arrays.asList(p.getVertex(), qAndRadius.getA().getVertex(), rAndAlpha.getA()), ellipsoid.get());

        ValuePair<Vector3d, Double> sAndBeta = calculateSurfacePointAndGreekCoefficient(q1PlusAlphaQ2, q3, fourVertices);
        if (sAndBeta == null) return ellipsoid;

        Matrix4d q1PlusAlphaQ2plusBetaQ3 = new Matrix4d(q3);
        fullScalingMatrix.scaling(sAndBeta.getB());
        fullScalingMatrix.m33(sAndBeta.getB());
        q1PlusAlphaQ2plusBetaQ3.mul(fullScalingMatrix, q1PlusAlphaQ2plusBetaQ3);
        q1PlusAlphaQ2plusBetaQ3.add(q1PlusAlphaQ2);

        return quadricToEllipsoid.calculate(q1PlusAlphaQ2plusBetaQ3);
    }

    /**
     * calculates the quadric denoted by Q_1 in the original paper
     *
     * @param sphereCentre centre of the sphere touching p and q
     * @param radius       radius of the sphere touching p and q
     * @return Q_1
     */
    static Matrix4d getQ1(Vector3d sphereCentre, double radius) {
        Matrix3d identity = new Matrix3d();
        identity = identity.identity();
        Vector4d minusSphereCentre = new Vector4d(-sphereCentre.x(), -sphereCentre.y(), -sphereCentre.z(),0);
        Matrix4d q1 = new Matrix4d(identity);
        q1.setColumn(3, new Vector4d(minusSphereCentre));
        q1.setRow(3, new Vector4d(minusSphereCentre));
        q1.m33(sphereCentre.lengthSquared() - radius * radius);
        return q1;
    }

    /**
     * Calculates the quadric denoted by Q_2 in the original paper
     *
     * @param pnp p and normal at p
     * @param qnq q and normal at q
     * @return the 4x4 matrix Q_2
     */
    static Matrix4d getQ2(final VertexWithNormal pnp, final VertexWithNormal qnq) {
        Vector3d p = pnp.getVertex();
        Vector3d np = pnp.getNormal();
        Vector3d q = qnq.getVertex();
        Vector3d nq = qnq.getNormal();

        Matrix3d NRotation = new Matrix3d();

        NRotation.m00 = -np.x() * nq.x();
        NRotation.m01 = -np.x() * nq.y();
        NRotation.m02 = -np.x() * nq.z();

        NRotation.m10 = -np.y() * nq.x();
        NRotation.m11 = -np.y() * nq.y();
        NRotation.m12 = -np.y() * nq.z();

        NRotation.m20 = -np.z() * nq.x();
        NRotation.m21 = -np.z() * nq.y();
        NRotation.m22 = -np.z() * nq.z();

        Matrix3d NRotationTransposed = new Matrix3d(NRotation);
        NRotationTransposed.transpose();
        Matrix3d q2Rotation = new Matrix3d(NRotation);
        q2Rotation.add(NRotationTransposed);
        q2Rotation.scale(0.5);

        double nqDotqHalf = nq.dot(q) / 2.0;
        double npDotpHalf = np.dot(p) / 2.0;

        Vector3d q2Translation = new Vector3d(np.x() * nqDotqHalf + nq.x() * npDotpHalf, np.y() * nqDotqHalf + nq.y() * npDotpHalf, np.z() * nqDotqHalf + nq.z() * npDotpHalf);

        Matrix4d quadric2 = new Matrix4d(q2Rotation);
        quadric2.setColumn(3, new Vector4d(q2Translation,1));
        quadric2.setRow(3, new Vector4d(q2Translation,1));
        quadric2.m33(-np.dot(p) * nq.dot(q));
        return quadric2;
    }


    /**
     * Calculates the elliptic cylinder denoted by Q_3 in the original paper
     *
     * @param pqr       three non-collinear points defining a plane
     * @param ellipsoid ellipsoid through p,q, and r
     * @return a 4x4 matrix representation of the elliptic cylinder defined by the intersection ellipse of pqr and qBar
     */
    private static Matrix4d getQ3(final List<Vector3d> pqr, Ellipsoid ellipsoid) {
        final Vector3d p = new Vector3d(pqr.get(0));
        final Vector3d q = new Vector3d(pqr.get(1));
        final Vector3d r = new Vector3d(pqr.get(2));

        Vector3d interiorPoint = new Vector3d(p);
        interiorPoint.mul(-1);
        interiorPoint.add(q);
        interiorPoint.mul(0.5);
        interiorPoint.add(p);

        Vector3d planeNormal = new Vector3d();
        q.mul(-1);
        q.add(p);
        r.mul(-1);
        r.add(p);
        q.cross(r, planeNormal);
        List<Vector3d> ellipse = intersectionOp.calculate(ellipsoid, new ValuePair<>(interiorPoint, planeNormal));

        Vector3dc a0 = ellipse.get(0);
        Vector3dc a1 = ellipse.get(1);
        Vector3dc a2 = ellipse.get(2);

        Matrix4d quadric = new Matrix4d();

        //axis and translation contributions calculated with sympy - see documentation
        Matrix3d A1 = getAxisContribution(a1);
        Matrix3d A2 = getAxisContribution(a2);
        A2.add(A1);
        quadric.set3x3(A2);

        Vector3d translationVector1 = getTranslationContribution(a0, a1);
        Vector3d translationVector2 = getTranslationContribution(a0, a2);
        translationVector2.add(translationVector1);

        quadric.setRow(3, new Vector4d(translationVector2,0));
        quadric.setColumn(3, new Vector4d(translationVector2,0));

        quadric.m33(getConstantContribution(a0,a1)+getConstantContribution(a0,a2)-1.0);

        return quadric;
    }

    private static Vector3d getTranslationContribution(final Vector3dc centre, final Vector3dc axis) {

        double radius = axis.length();

        Vector3d unitAxis = new Vector3d(axis);
        unitAxis.normalize();

        Vector3d translationVector = new Vector3d();

        translationVector.x = -unitAxis.x * centre.dot(unitAxis);
        translationVector.y = -unitAxis.y * centre.dot(unitAxis);
        translationVector.z = -unitAxis.z * centre.dot(unitAxis);

        translationVector.mul(1.0 / (radius * radius));

        return translationVector;
    }

    private static double getConstantContribution(final Vector3dc centre, final Vector3dc axis) {
        double radius = axis.length();

        Vector3d unitAxis = new Vector3d(axis);
        unitAxis.normalize();

        double constantComponent = centre.x() * centre.x() * unitAxis.x * unitAxis.x;
        constantComponent +=centre.y()*centre.y()*unitAxis.y*unitAxis.y;
        constantComponent +=centre.z()*centre.z()*unitAxis.z*unitAxis.z;

        constantComponent +=2.0*centre.x()*centre.y()*unitAxis.x*unitAxis.y;
        constantComponent +=2.0*centre.x()*centre.z()*unitAxis.x*unitAxis.z;
        constantComponent +=2.0*centre.y()*centre.z()*unitAxis.y*unitAxis.z;

        return constantComponent/(radius*radius);
    }

    private static Matrix3d getAxisContribution(final Vector3dc axis) {
        double radius = axis.length();
        Vector3d normalizedAxis = new Vector3d(axis);
        normalizedAxis.normalize();

        Matrix3d axisContribution = new Matrix3d();
        axisContribution.m00(normalizedAxis.x()*normalizedAxis.x()/(radius*radius));
        axisContribution.m11(normalizedAxis.y()*normalizedAxis.y()/(radius*radius));
        axisContribution.m22(normalizedAxis.z()*normalizedAxis.z()/(radius*radius));

        axisContribution.m01(normalizedAxis.x()*normalizedAxis.y()/(radius*radius));
        axisContribution.m10(normalizedAxis.x()*normalizedAxis.y()/(radius*radius));

        axisContribution.m02(normalizedAxis.x()*normalizedAxis.z()/(radius*radius));
        axisContribution.m20(normalizedAxis.x()*normalizedAxis.z()/(radius*radius));

        axisContribution.m12(normalizedAxis.y()*normalizedAxis.z()/(radius*radius));
        axisContribution.m21(normalizedAxis.y()*normalizedAxis.z()/(radius*radius));

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

    private static double euclideanDistance(Vector3d q, Vector3d p) {
        Vector3d distance = new Vector3d(q);
        distance.sub(p);
        return distance.length();
    }

    private static ValuePair<VertexWithNormal,Double> calculatePossibleQAndR(Vector3d x, VertexWithNormal p)
    {
        Vector3d xMinusP = new Vector3d(p.getVertex());
        xMinusP.mul(-1);
        xMinusP.add(x);
        double distanceSquared = xMinusP.lengthSquared();
        double scalarProduct = xMinusP.dot(p.getNormal());

        if(scalarProduct<=0.0) return new ValuePair<>(null,-1.0);

        double radius = distanceSquared/(2*scalarProduct);

        Vector3d centre = new Vector3d(p.getNormal());
        centre.mul(radius);
        centre.add(p.getVertex());

        Vector3d cMinusX = new Vector3d(x);
        cMinusX.mul(-1);
        cMinusX.add(centre);
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
        Vector4d x4d = new Vector4d(x,1);
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

    public void setNormal(Vector3d n) {this.vwn = new ValuePair<>(this.vwn.getA(), new Vector3d(n));}
}