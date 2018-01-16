package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;
import org.scijava.vecmath.Vector4d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Tries to create an {@link Ellipsoid} decomposition from a list of vertex points with inward pointing normals
 * <p>
 * The ellipsoid decomposition is drawn from the paper of Bischoff and Kobbelt, 2002
 * The variable naming follows Bischoff and Kobbelt's nomenclature.
 * </p>
 *
 * @author Alessandro Felder
 */
@Plugin(type = Op.class)
public class EllipsoidDecomposition extends AbstractBinaryFunctionOp<List<Vector3d>, ValuePair<Vector3d,Vector3d>,Optional<Ellipsoid>> {

    private QuadricToEllipsoid quadricToEllipsoid = new QuadricToEllipsoid();

    @Override
    public Optional<Ellipsoid> calculate(final List<Vector3d> otherVerticesWithNormals, final ValuePair<Vector3d,Vector3d> vertexWithNormal) {

        VertexWithNormal p = new VertexWithNormal(vertexWithNormal);

        ValuePair<VertexWithNormal,Double> QAndR = calculateQ(otherVerticesWithNormals, p);
        if(QAndR==null) return Optional.empty();

        Vector3d c = new Vector3d(p.getNormal());
        c.scaleAdd(QAndR.getB(), p.getVertex());
        Matrix4d q1 = getQuadric1(c,QAndR.getB());
        Matrix4d q2 = getQuadric2(p,QAndR.getA());

        double alpha = calculateOptimalAlpha(q1, q2, otherVerticesWithNormals);

        Matrix4d q1PlusAlphaQ2 = new Matrix4d(q2);
        q1PlusAlphaQ2.mul(alpha);
        q1PlusAlphaQ2.add(q1);

        return quadricToEllipsoid.calculate(q1PlusAlphaQ2);
    }

    public static Matrix4d getQuadric1(Vector3d sphereCentre, double radius){
        Matrix3d identity = new Matrix3d();
        identity.setIdentity();
        Vector3d minusSphereCentre = new Vector3d(-sphereCentre.getX(), -sphereCentre.getY(), -sphereCentre.getZ());
        Matrix4d quadric1 = new Matrix4d(identity,minusSphereCentre, 1.0);
        quadric1.setRow(3, new Vector4d(minusSphereCentre));
        quadric1.m33 = sphereCentre.lengthSquared()-radius*radius;
        return quadric1;
    }

    public static Matrix4d getQuadric2(final VertexWithNormal pnp, final VertexWithNormal qnq){
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

    static double calculateOptimalAlpha(final Matrix4d Q1, final Matrix4d Q2, final List<Vector3d> vertices)
    {
        final List<Double> alphaValues = vertices.stream().map(v -> calculateAlpha(Q1, Q2, v)).filter(a -> !a.isNaN()).collect(Collectors.toList());
        alphaValues.sort(Double::compareTo);
        if(alphaValues.size() > 0)
            return alphaValues.get(0);
        else
            return Double.NaN;
    }

    private static double calculateXtQX(final Matrix4d Q2, Vector3d x)
    {
        Vector4d x4d = new Vector4d(x);
        x4d.w = 1.0;
        Vector4d Q2X = new Vector4d(x4d);
        Q2.transform(Q2X);
        return x4d.dot(Q2X);
    }

    static double calculateAlpha(final Matrix4d Q1, final Matrix4d Q2, final Vector3d x)
    {
        double xtQ2X = calculateXtQX(Q2, x);
        if(xtQ2X>=0) return Double.NaN;

        double xtQ1X = calculateXtQX(Q1, x);
        return -xtQ1X/xtQ2X;
    }


}

class VertexWithNormal {

    private ValuePair<Vector3d,Vector3d> vwn;

    VertexWithNormal(ValuePair<Vector3d,Vector3d> vwn){
        this.vwn = vwn;
    }
    public ValuePair<Vector3d, Vector3d> getVertexAndNormal() {
        return vwn;
    }

    public Vector3d getVertex() {
        return new Vector3d(vwn.getA());
    }

    public Vector3d getNormal() {
        return new Vector3d(vwn.getB());
    }
}

class EllipsoidDecompositionInfo {

    private VertexWithNormal p;
    private VertexWithNormal q;
    private VertexWithNormal r;
    private VertexWithNormal s;

    private double optimalRadius;
    private double optimalAlpha;

    private Matrix4d Q1;
    private Matrix4d Q2;

    EllipsoidDecompositionInfo(VertexWithNormal  initialVectorWithNormal)
    {
        this.setP(initialVectorWithNormal);
    }

    public VertexWithNormal getP() {
        return p;
    }

    public void setP(VertexWithNormal p) {
        this.p = p;
    }

    public VertexWithNormal getQ() {
        return q;
    }

    public void setQ(VertexWithNormal q) {
        this.q = q;
    }

    public VertexWithNormal getR() {
        return r;
    }

    public void setR(VertexWithNormal r) {
        this.r = r;
    }

    public VertexWithNormal getS() {
        return s;
    }

    public void setS(VertexWithNormal s) {
        this.s = s;
    }

    public double getOptimalRadius() {
        return optimalRadius;
    }

    public void setOptimalRadius(double optimalRadius) {
        this.optimalRadius = optimalRadius;
    }

    public double getOptimalAlpha() {
        return optimalAlpha;
    }

    public void setOptimalAlpha(double optimalAlpha) {
        this.optimalAlpha = optimalAlpha;
    }

    public Matrix4d getQ1() {
        return Q1;
    }

    public void setQ1(Matrix4d q1) {
        Q1 = q1;
    }

    public Matrix4d getQ2() {
        return Q2;
    }

    public void setQ2(Matrix4d q2) {
        Q2 = q2;
    }

}