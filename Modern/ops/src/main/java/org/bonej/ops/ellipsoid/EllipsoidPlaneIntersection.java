package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.joml.Matrix3d;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.plugin.Plugin;

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
public class EllipsoidPlaneIntersection extends AbstractBinaryFunctionOp<Ellipsoid, ValuePair<Vector3dc,Vector3dc>, List<Vector3d>>
{

    @Override
    public List<Vector3d> calculate(final Ellipsoid ellipsoid, final ValuePair<Vector3dc, Vector3dc> plane) {

        final Vector3d interiorPointOnPlane = new Vector3d(plane.getA());
        final Vector3d planeNormal = new Vector3d(plane.getB());
        planeNormal.normalize();

        //transform to axis-aligned coordinates
        Matrix3d toAxisAlignedRotation = new Matrix3d();
        ellipsoid.getOrientation().get3x3(toAxisAlignedRotation);
        if(Math.abs(toAxisAlignedRotation.determinant()-1.0) > 1e-12)
        {
            toAxisAlignedRotation.setColumn(2, -toAxisAlignedRotation.m02, -toAxisAlignedRotation.m12, -toAxisAlignedRotation.m22);
        }
        toAxisAlignedRotation = toAxisAlignedRotation.transpose();

        Vector3d translation = ellipsoid.getCentroid();
        translation.mul(-1.0);
        interiorPointOnPlane.add(translation);

        final Vector3d transformedInteriorPointOnPlane = toAxisAlignedRotation.transform(interiorPointOnPlane);
        final Vector3d transformedPlaneNormal = toAxisAlignedRotation.transform(planeNormal);

        //find intersections
        final Vector3dc semiAxisLengths = new Vector3d(ellipsoid.getA(), ellipsoid.getB(), ellipsoid.getC());
        final List<Vector3d> ellipse3D = findAxisAlignedCentredIntersectionEllipse(semiAxisLengths, new ValuePair<>(transformedInteriorPointOnPlane, transformedPlaneNormal));

        //transform back
        final Matrix3d rotationBack = new Matrix3d();
        ellipsoid.getOrientation().get3x3(rotationBack);
        ellipse3D.forEach(v -> v=rotationBack.transform(v));

        translation = ellipsoid.getCentroid();
        ellipse3D.get(0).add(translation);

        return ellipse3D;
    }

    public List<Vector3d> findAxisAlignedCentredIntersectionEllipse(final Vector3dc semiAxisLengths, final ValuePair<Vector3dc, Vector3dc> transformedPlane) {
        final Vector3dc n = transformedPlane.getB();
        final List<Vector3dc> basis = completeBasis(semiAxisLengths, n);

        final Vector3dc q = transformedPlane.getA();
        final Vector2d tuCentre = getParametricCentre(semiAxisLengths,basis,q);
        final ValuePair<Vector2d,Vector2d> tuAxes = getParametricAxes(semiAxisLengths, basis, q);

        final Vector3d ellipseCentre = toWorldCoordinates(tuCentre,basis,q);
        final Vector3d ellipseFirstAxis = toWorldCoordinates(tuAxes.getA(),basis,new Vector3d());
        final Vector3d ellipseSecondAxis = toWorldCoordinates(tuAxes.getB(),basis,new Vector3d());

        return Stream.of(ellipseCentre, ellipseFirstAxis, ellipseSecondAxis).collect(Collectors.toList());
    }

    public ValuePair<Vector2d, Vector2d> getParametricAxes(final Vector3dc semiAxisLengths, final List<Vector3dc> basis, final Vector3dc q) {
        final Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);

        final Vector3d diagonalTimesR = new Vector3d();
        diagonal.transform(basis.get(0),diagonalTimesR);

        final Vector3d diagonalTimesS = new Vector3d();
        diagonal.transform(basis.get(1),diagonalTimesS);

        final Vector3d diagonalTimesQ = new Vector3d();
        diagonal.transform(q,diagonalTimesQ);

        final double oneMinusD = 1.0-calculateD(diagonalTimesQ,diagonalTimesR,diagonalTimesS);
        final double a = Math.sqrt(oneMinusD/diagonalTimesR.dot(diagonalTimesR));
        final double b = Math.sqrt(oneMinusD/diagonalTimesS.dot(diagonalTimesS));
        return new ValuePair<>(new Vector2d(a,0.0), new Vector2d(0.0, b));
    }

    private double calculateD(final Vector3dc d1Q, final Vector3dc d1R, final Vector3dc d1S) {
        final double ratio1 = d1Q.dot(d1R)*d1Q.dot(d1R)/d1R.dot(d1R);
        final double ratio2 = d1Q.dot(d1S)*d1Q.dot(d1S)/d1S.dot(d1S);
        return d1Q.dot(d1Q)-ratio1-ratio2;
    }

    private Vector3d toWorldCoordinates(final Vector2dc tuCentre, final List<Vector3dc> basis, final Vector3dc q) {
        final Vector3d tR = new Vector3d(basis.get(0));
        tR.mul(tuCentre.x());

        final Vector3d uS = new Vector3d(basis.get(1));
        uS.mul(tuCentre.y());

        final Vector3d world = new Vector3d(q);
        world.add(tR);
        world.add(uS);

        return world;
    }

    private Vector2d getParametricCentre(final Vector3dc semiAxisLengths, final List<Vector3dc> basis, final Vector3dc q) {
        final Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);

        final Vector3d diagonalTimesR = new Vector3d();
        diagonal.transform(basis.get(0),diagonalTimesR);

        final Vector3d diagonalTimesS = new Vector3d();
        diagonal.transform(basis.get(1),diagonalTimesS);

        final Vector3d diagonalTimesQ = new Vector3d();
        diagonal.transform(q,diagonalTimesQ);

        final double tCoordinate = -diagonalTimesQ.dot(diagonalTimesR)/(diagonalTimesR.dot(diagonalTimesR));
        final double uCoordinate = -diagonalTimesQ.dot(diagonalTimesS)/(diagonalTimesS.dot(diagonalTimesS));

        return new Vector2d(tCoordinate, uCoordinate);
    }

    public static List<Vector3dc> completeBasis(final Vector3dc semiAxisLengths, final Vector3dc n) {
        final Vector3d r = getOrthogonalUnitVector(n);
        final Vector3d s = new Vector3d();
        r.cross(n,s);
        s.normalize();

        final Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);

        final Vector3d diagonalTimesR = new Vector3d();
        diagonal.transform(r,diagonalTimesR);

        final Vector3d diagonalTimesS = new Vector3d();
        diagonal.transform(s,diagonalTimesS);

        final double omega;
        final double d1xRSquaredMinusD1xSSquared = diagonalTimesR.dot(diagonalTimesR)-diagonalTimesS.dot(diagonalTimesS);

        if(Math.abs(d1xRSquaredMinusD1xSSquared)!=0.0){
            omega = 0.5*Math.atan(2.0*diagonalTimesR.dot(diagonalTimesS)/d1xRSquaredMinusD1xSSquared);
        }
        else {
            omega = Math.PI/4.0;
        }

        final Vector3d rWiggle =  getRWiggle(omega,r,s);
        final Vector3d sWiggle =  getSWiggle(omega,r,s);

        return Stream.of(rWiggle,sWiggle).collect(Collectors.toList());
    }

    private static Matrix3d getDiagonalMatrix(final Vector3dc semiAxisLengths) {
        final Matrix3d diagonal = new Matrix3d();
        return diagonal.scaling(1.0/semiAxisLengths.x(),1.0/semiAxisLengths.y(),1.0/semiAxisLengths.z());
    }


    private static Vector3d getRWiggle(final double omega, final Vector3dc r, final Vector3dc s) {

        final Vector3d cosOmegaR = new Vector3d(r);
        cosOmegaR.mul(Math.cos(omega));
        final Vector3d sinOmegaS = new Vector3d(s);
        sinOmegaS.mul(Math.sin(omega));

        cosOmegaR.add(sinOmegaS);
        return cosOmegaR;
    }

    private static Vector3d getSWiggle(final double omega, final Vector3dc r, final Vector3dc s) {
        final Vector3d minusSinOmegaR = new Vector3d(r);
        minusSinOmegaR.mul(-Math.sin(omega));
        final Vector3d cosOmegaS = new Vector3d(s);
        cosOmegaS.mul(Math.cos(omega));

        cosOmegaS.add(minusSinOmegaR);
        return cosOmegaS;
    }

    private static Vector3d getOrthogonalUnitVector(final Vector3dc q) {
        Vector3d orthogonal  = new Vector3d();
        if(Math.abs(q.y())>1.0e-12 || Math.abs(q.z())>1.0e-12)
        {
            orthogonal = new Vector3d(0.0, -q.z(), q.y());
        }
        else if(Math.abs(q.x())>1.0e-12)
        {
            orthogonal = new Vector3d(-q.z(), 0.0, q.x());
        }
        orthogonal.normalize();
        return orthogonal;
    }
}