package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Vector2d;
import org.scijava.vecmath.Vector3d;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds the intersection between an {@link Ellipsoid} and a plane given by a point and a normal vector.
 * <p>
 * Valid for any ellipsoid, the calculation is based on the closed-form solution for an
 * axis-aligned ellipsoid given by P. P. Klein (http://dx.doi.org/10.4236/am.2012.311226).
 * The input point on the plane has to be an interior point of the ellipsoid!
 * </p>
 *
 * @author Alessandro Felder
 */
@Plugin(type = Op.class)
public class EllipsoidPlaneIntersection extends AbstractBinaryFunctionOp<Ellipsoid, ValuePair<Vector3d,Vector3d>, List<Vector3d>>
{

    @Override
    public List<Vector3d> calculate(final Ellipsoid ellipsoid, final ValuePair<Vector3d, Vector3d> plane) {

        Vector3d interiorPointOnPlane = plane.getA();
        Vector3d planeNormal = plane.getB();
        planeNormal.normalize();

        //transform to axis-aligned coordinates
        Matrix3d rotation = new Matrix3d();
        ellipsoid.getOrientation().getRotationScale(rotation);
        if(rotation.determinant()!=1.0)
        {
            rotation.setColumn(2, -rotation.m02, -rotation.m12, -rotation.m22);
        }
        rotation.transpose();

        Vector3d translation = ellipsoid.getCentroid();
        translation.scale(-1.0);

        interiorPointOnPlane.add(translation);

        rotation.transform(interiorPointOnPlane);
        rotation.transform(planeNormal);

        //find intersections
        Vector3d semiAxisLengths = new Vector3d(ellipsoid.getA(), ellipsoid.getB(), ellipsoid.getC());
        List<Vector3d> ellipse3D = findAxisAlignedIntersectionEllipse(semiAxisLengths, new ValuePair<>(interiorPointOnPlane, planeNormal));

        //tranform back
        rotation.transpose();
        ellipse3D.forEach(rotation::transform);

        translation = ellipsoid.getCentroid();
        ellipse3D.get(0).add(translation);

        return ellipse3D;
    }

    public List<Vector3d> findAxisAlignedIntersectionEllipse(final Vector3d semiAxisLengths, final ValuePair<Vector3d, Vector3d> transformedPlane) {
        Vector3d n = transformedPlane.getB();
        List<Vector3d> basis = completeBasis(semiAxisLengths, n);

        Vector3d q = transformedPlane.getA();
        Vector2d tuCentre = getParametricCentre(semiAxisLengths,basis,q);
        ValuePair<Vector2d,Vector2d> tuAxes = getParametricAxes(semiAxisLengths, basis, q);

        Vector3d ellipseCentre = toWorldCoordinates(tuCentre,basis,q);
        Vector3d ellipseFirstAxis = toWorldCoordinates(tuAxes.getA(),basis,q);
        Vector3d ellipseSecondAxis = toWorldCoordinates(tuAxes.getB(),basis,q);

        return Stream.of(ellipseCentre, ellipseFirstAxis, ellipseSecondAxis).collect(Collectors.toList());
    }

    public ValuePair<Vector2d, Vector2d> getParametricAxes(final Vector3d semiAxisLengths, final List<Vector3d> basis, final Vector3d q) {
        Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);

        Vector3d diagonalTimesR = new Vector3d();
        diagonal.transform(basis.get(0),diagonalTimesR);

        Vector3d diagonalTimesS = new Vector3d();
        diagonal.transform(basis.get(1),diagonalTimesS);

        Vector3d diagonalTimesQ = new Vector3d();
        diagonal.transform(q,diagonalTimesQ);

        double oneMinusD = 1.0-calculateD(diagonalTimesQ,diagonalTimesR,diagonalTimesS);
        double a = Math.sqrt(oneMinusD/diagonalTimesR.dot(diagonalTimesR));
        double b = Math.sqrt(oneMinusD/diagonalTimesS.dot(diagonalTimesS));
        return new ValuePair<>(new Vector2d(a,0.0), new Vector2d(0.0, b));
    }

    private double calculateD(final Vector3d d1Q, final Vector3d d1R, final Vector3d d1S) {
        double ratio1 = d1Q.dot(d1R)*d1Q.dot(d1R)/d1R.dot(d1R);
        double ratio2 = d1Q.dot(d1S)*d1Q.dot(d1S)/d1S.dot(d1S);
        return d1Q.dot(d1Q)-ratio1-ratio2;
    }

    private Vector3d toWorldCoordinates(final Vector2d tuCentre, final List<Vector3d> basis, final Vector3d q) {
        Vector3d tR = new Vector3d(basis.get(0));
        tR.scale(tuCentre.getX());

        Vector3d uS = new Vector3d(basis.get(1));
        uS.scale(tuCentre.getY());

        Vector3d world = new Vector3d(q);
        world.add(tR);
        world.add(uS);

        return world;
    }

    private Vector2d getParametricCentre(final Vector3d semiAxisLengths, final List<Vector3d> basis, final Vector3d q) {
        Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);

        Vector3d diagonalTimesR = new Vector3d();
        diagonal.transform(basis.get(0),diagonalTimesR);

        Vector3d diagonalTimesS = new Vector3d();
        diagonal.transform(basis.get(1),diagonalTimesS);

        Vector3d diagonalTimesQ = new Vector3d();
        diagonal.transform(q,diagonalTimesQ);

        double tCoordinate = -diagonalTimesQ.dot(diagonalTimesR)/(diagonalTimesR.dot(diagonalTimesR));
        double uCoordinate = -diagonalTimesQ.dot(diagonalTimesS)/(diagonalTimesS.dot(diagonalTimesS));

        return new Vector2d(tCoordinate, uCoordinate);
    }

    public static List<Vector3d> completeBasis(final Vector3d semiAxisLengths, final Vector3d n) {
        Vector3d r = getOrthogonalUnitVector(n);
        Vector3d s = new Vector3d();
        s.cross(r,n);
        s.normalize();

        Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);

        Vector3d diagonalTimesR = new Vector3d();
        diagonal.transform(r,diagonalTimesR);

        Vector3d diagonalTimesS = new Vector3d();
        diagonal.transform(s,diagonalTimesS);

        double omega;
        double d1xRSquaredMinusD1xSSquared = diagonalTimesR.dot(diagonalTimesR)-diagonalTimesS.dot(diagonalTimesS);

        if(Math.abs(d1xRSquaredMinusD1xSSquared)!=0.0){
            omega = 0.5*Math.atan(2.0*diagonalTimesR.dot(diagonalTimesS)/d1xRSquaredMinusD1xSSquared);
        }
        else {
            omega = Math.PI/4.0;
        }

        Vector3d rWiggle =  getRWiggle(omega,r,s);
        Vector3d sWiggle =  getSWiggle(omega,r,s);

        return Stream.of(rWiggle,sWiggle).collect(Collectors.toList());
    }

    private static Matrix3d getDiagonalMatrix(final Vector3d semiAxisLengths) {
        Matrix3d diagonal = new Matrix3d();
        diagonal.setM00(1.0/semiAxisLengths.getX());
        diagonal.setM11(1.0/semiAxisLengths.getY());
        diagonal.setM22(1.0/semiAxisLengths.getZ());
        return diagonal;
    }


    private static Vector3d getRWiggle(final double omega, final Vector3d r, final Vector3d s) {

        Vector3d cosOmegaR = new Vector3d(r);
        cosOmegaR.scale(Math.cos(omega));
        Vector3d sinOmegaS = new Vector3d(s);
        sinOmegaS.scale(Math.sin(omega));

        cosOmegaR.add(sinOmegaS);
        return cosOmegaR;
    }

    private static Vector3d getSWiggle(final double omega, final Vector3d r, final Vector3d s) {
        Vector3d minusSinOmegaR = new Vector3d(r);
        minusSinOmegaR.scale(-Math.sin(omega));
        Vector3d cosOmegaS = new Vector3d(s);
        cosOmegaS.scale(Math.cos(omega));

        cosOmegaS.add(minusSinOmegaR);
        return cosOmegaS;
    }

    private static Vector3d getOrthogonalUnitVector(final Vector3d q) {
        Vector3d orthogonal  = new Vector3d();
        if(Math.abs(q.getY())>1.0e-12 || Math.abs(q.getZ())>1.0e-12)
        {
            orthogonal = new Vector3d(0.0, -q.getZ(), q.getY());
        }
        else if(Math.abs(q.getX())>1.0e-12)
        {
            orthogonal = new Vector3d(-q.getZ(), 0.0, q.getX());
        }
        orthogonal.normalize();
        return orthogonal;
    }
}