package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;
import org.scijava.vecmath.Vector4d;

import java.util.List;
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
public class EllipsoidDecomposition extends AbstractUnaryFunctionOp<List<ValuePair<Vector3d,Vector3d>>,List<Ellipsoid>> {

    @Override
    public List<Ellipsoid> calculate(final List<ValuePair<Vector3d, Vector3d>> vertexNormalPairs) {

        final List<Vector3d> vertices = vertexNormalPairs.stream().map(v -> v.getA()).collect(Collectors.toList());
        final List<Double> optimalRadii = vertexNormalPairs.stream().map(vnp -> calculateOptimalRadius(vertices, vnp)).filter(r -> !r.isNaN()).collect(Collectors.toList());

        return optimalRadii.stream().map(r -> new Ellipsoid(r,r,r)).collect(Collectors.toList());
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

    public static Matrix4d getQuadric2(Vector3d p, Vector3d q, Vector3d np, Vector3d nq){
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

    private static double calculateOptimalRadius(final List<Vector3d> vertices, final ValuePair<Vector3d, Vector3d> vnp) {
        final List<Double> positiveRadii = vertices.stream().map(v -> calculateRadius(v, vnp)).filter(r -> r > 0).collect(Collectors.toList());
        positiveRadii.sort(Double::compareTo);
        if(positiveRadii.size() > 0)
            return positiveRadii.get(0);
        else
            return Double.NaN;
    }

    private static double calculateRadius(Vector3d x, ValuePair<Vector3d, Vector3d> vertexWithNormal)
    {
        Vector3d xMinusP = new Vector3d(x.getX()-vertexWithNormal.getA().getX(), x.getY()-vertexWithNormal.getA().getY(), x.getZ()-vertexWithNormal.getA().getZ());
        double distanceSquared = xMinusP.lengthSquared();
        double scalarProduct = xMinusP.dot(vertexWithNormal.getB());
        if(scalarProduct > 0)
            return distanceSquared/(2*scalarProduct);
        else
            return -1.0;
    }
}