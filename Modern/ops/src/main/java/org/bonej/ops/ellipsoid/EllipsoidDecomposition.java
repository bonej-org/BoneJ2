package org.bonej.ops.ellipsoid;

import com.sun.org.glassfish.gmbal.GmbalException;
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
 * Tries to create an {@link Ellipsoid} decomposition from a list of vertex points with inward pointing normals
 * <p>
 * The ellipsoid decomposition is drawn from the paper of Bischoff and Kobbelt, 2002
 * The variable naming follows Bischoff and Kobbelt's nomenclature, it is recommended to read this code in conjunction with the paper.
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

        ValuePair<VertexWithNormal,Double> qAndRadius = calculateQ(otherVerticesWithNormals, p);
        if(qAndRadius==null) return Optional.empty();

        Vector3d c = new Vector3d(p.getNormal());
        c.scaleAdd(qAndRadius.getB(), p.getVertex());
        Matrix4d q1 = getQuadric1(c,qAndRadius.getB());
        Matrix4d q2 = getQuadric2(p,qAndRadius.getA());

        ValuePair<Vector3d,Double> rAndAlpha = calculateSurfacePointAndGreekCoefficient(q1, q2, otherVerticesWithNormals);
        if(rAndAlpha==null) return Optional.empty();

        Matrix4d q1PlusAlphaQ2 = new Matrix4d(q2);
        q1PlusAlphaQ2.mul(rAndAlpha.getB());
        q1PlusAlphaQ2.add(q1);

        Matrix4d q3 = getQuadric3(Stream.of(p.getVertex(), qAndRadius.getA().getVertex(), rAndAlpha.getA()).collect(Collectors.toList()),q1PlusAlphaQ2);
        //Matrix4d q3 = getQuadric3(Stream.of(p.getVertex(), qAndRadius.getA().getVertex(), new Vector3d(1,0,0)).collect(Collectors.toList()),q1PlusAlphaQ2);


        ValuePair<Vector3d,Double> sAndBeta = calculateSurfacePointAndGreekCoefficient(q1PlusAlphaQ2, q3, otherVerticesWithNormals);
        if(sAndBeta==null) return Optional.empty();

        Matrix4d q1PlusAlphaQ2plusBetaQ3 = new Matrix4d(q3);
        q1PlusAlphaQ2plusBetaQ3.mul(sAndBeta.getB());
        q1PlusAlphaQ2.add(q1PlusAlphaQ2);

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

    public static Matrix4d getQuadric3(final List<Vector3d> pqr, final Matrix4d qBar)
    {
        Matrix4d quadric = new Matrix4d();
        Matrix3d E = getEllipseMatrix(pqr, qBar);

        Vector2d uvCentre = getUVCentre(E);
        Vector3d centre = toWorldCoordinates(pqr, uvCentre);

        ValuePair<Vector2d, Vector2d> eigenDecomposition = getEllipseMatrixEigenDecomposition(E);

        ValuePair<Double, Double> lambda1and2 = new ValuePair<>(eigenDecomposition.getA().length(), eigenDecomposition.getB().length());
        double K = -E.determinant()/(lambda1and2.getA()*lambda1and2.getB());

        centre.scale(0.5);
        quadric.setTranslation(centre);
        quadric.setRow(3, new Vector4d(centre));

        Matrix3d a1 = getAxisContribution(toWorldCoordinates(pqr, eigenDecomposition.getA()));
        Matrix3d a2 = getAxisContribution(toWorldCoordinates(pqr, eigenDecomposition.getB()));
        a2.add(a1);

        quadric.setRotationScale(a2);

        return quadric;

    }

    public static Matrix3d getAxisContribution(final Vector3d axis) {
        double radius = axis.length();
        Vector3d normalizedAxis = new Vector3d(axis);
        normalizedAxis.normalize();

        Matrix3d axisContribution = new Matrix3d();
        axisContribution.setM00(normalizedAxis.getX()*normalizedAxis.getX());
        axisContribution.setM11(normalizedAxis.getY()*normalizedAxis.getY());
        axisContribution.setM22(normalizedAxis.getZ()*normalizedAxis.getZ());

        axisContribution.setM01(normalizedAxis.getX()*normalizedAxis.getY());
        axisContribution.setM10(normalizedAxis.getX()*normalizedAxis.getY());

        axisContribution.setM02(normalizedAxis.getX()*normalizedAxis.getZ());
        axisContribution.setM20(normalizedAxis.getX()*normalizedAxis.getZ());

        axisContribution.setM12(normalizedAxis.getY()*normalizedAxis.getZ());
        axisContribution.setM21(normalizedAxis.getY()*normalizedAxis.getZ());

        axisContribution.setScale(1.0/(radius*radius));
        return axisContribution;
    }

    // Eigenvalue calculation follows explicit formulae derived from characteristic equation
    // http://croninprojects.org/Vince/Geodesy/FindingEigenvectors.pdf
    private static ValuePair<Vector2d,Vector2d> getEllipseMatrixEigenDecomposition(final Matrix3d ellipseMatrix) {

        double A = ellipseMatrix.getElement(0, 0);
        double B = ellipseMatrix.getElement(0, 1);
        double C = ellipseMatrix.getElement(1, 1);

        double trace = A + C;
        double determinant = A * C - B * B;

        double lambda1 = trace / 2.0 + Math.sqrt(trace * trace / 4.0 - determinant);
        double lambda2 = trace / 2.0 - Math.sqrt(trace * trace / 4.0 - determinant);

        Vector2d e1;
        Vector2d e2;

        if (Math.abs(B) < 1.e-12) {
            e1 = new Vector2d(1.0, 0.0);
            e2 = new Vector2d(0.0, 1.0);
        } else {
            e1 = new Vector2d(1.0, (lambda1 - A) / B);
            e1.normalize();
            e2 = new Vector2d(1.0, (lambda2 - A) / B);
            e2.normalize();
        }

        if(Math.abs(lambda1)<1.e-12 || Math.abs(lambda2)<1.e-12)
        {
            throw new IllegalArgumentException("Input matrix represents degenerate ellipse.");
        }

        e1.scale(lambda1);
        e2.scale(lambda2);

        return new ValuePair<>(e1, e2);
    }


    private static Vector3d toWorldCoordinates(final List<Vector3d> threePointsOnAPlane, final Vector2d parametricCoordinates) {
        Vector3d p = threePointsOnAPlane.get(0);
        Vector3d q = threePointsOnAPlane.get(1);
        Vector3d r = threePointsOnAPlane.get(2);

        Vector3d qMinusP = new Vector3d(p);
        qMinusP.scaleAdd(-1.0, new Vector3d(q));
        //qMinusP.scale(parametricCoordinates.getX());

        Vector3d rMinusP = new Vector3d(p);
        rMinusP.scaleAdd(-1.0, new Vector3d(r));
       // rMinusP.scale(parametricCoordinates.getY());

        Vector3d result = new Vector3d(p);
        result.add(qMinusP);
        result.add(rMinusP);
        return result;
    }

    //formula from conic sections article on Wikipedia
    private static Vector2d getUVCentre(final Matrix3d e) {
        double A = e.getElement(0,0);
        double B = 2.0*e.getElement(0,1);
        double C = e.getElement(1,1);
        double D = 2.0*e.getElement(0,2);
        double E = 2.0*e.getElement(1,2);

        double fourACMinusBSquared = 4.0*A*C-B*B;

        return new Vector2d((B*E-2.0*C*D)/fourACMinusBSquared, (D*B-2.0*A*E)/fourACMinusBSquared);
    }

    private static Matrix3d getEllipseMatrix(final List<Vector3d> pqr, final Matrix4d qBar) {
        Vector3d p = pqr.get(0);
        Vector3d q = pqr.get(1);
        Vector3d r = pqr.get(2);

        Vector4d qMinusP = new Vector4d(p);
        qMinusP.scaleAdd(-1.0, new Vector4d(q));

        Vector4d rMinusP = new Vector4d(p);
        rMinusP.scaleAdd(-1.0, new Vector4d(r));

        GMatrix toParameterSpace = new GMatrix(4,3);
        toParameterSpace.setColumn(0, new GVector(qMinusP));
        toParameterSpace.setColumn(1, new GVector(rMinusP));
        toParameterSpace.setColumn(2, new GVector(new Vector4d(p)));
        toParameterSpace.setElement(3,2,1.0);

        GMatrix fromParameterSpace = new GMatrix(toParameterSpace);
        fromParameterSpace.transpose();

        GMatrix generalQBar = new GMatrix(4,4);
        generalQBar.set(qBar);

        toParameterSpace.mul(generalQBar, toParameterSpace);

        GMatrix gE = new GMatrix(3,3);
        gE.mul(fromParameterSpace,toParameterSpace);

        Matrix3d E = new Matrix3d();
        gE.get(E);
        return E;
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
