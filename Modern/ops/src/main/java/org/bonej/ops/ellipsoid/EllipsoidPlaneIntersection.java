package org.bonej.ops.ellipsoid;

import java.util.Arrays;
import java.util.List;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.util.ValuePair;

import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.plugin.Plugin;

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
        final Matrix3dc axisAlignedRotation = getInverseRotation(ellipsoid.getOrientation());
        final List<Vector3d> ellipse3D = intersectionEllipse(ellipsoid, plane, axisAlignedRotation);
        return alignToEllipsoid(ellipse3D, ellipsoid);
    }

	private List<Vector3d> alignToEllipsoid(final List<Vector3d> ellipse3D,
		final Ellipsoid ellipsoid)
	{
		final Matrix4d orientation = ellipsoid.getOrientation();
		ellipse3D.forEach(orientation::transformDirection);
		ellipse3D.get(0).add(ellipsoid.getCentroid());
		return ellipse3D;
	}

	private Matrix3d getInverseRotation(final Matrix4dc orientation) {
		final Matrix3d rotation = new Matrix3d(orientation);
		if (Math.abs(rotation.determinant() - 1.0) > 1e-12) {
			rotation.setColumn(2, -rotation.m02, -rotation.m12, -rotation.m22);
		}
		return rotation.transpose();
	}

	private List<Vector3d> intersectionEllipse(final Ellipsoid ellipsoid,
		final ValuePair<Vector3dc, Vector3dc> plane, final Matrix3dc rotation)
	{
		final Vector3d translation = ellipsoid.getCentroid();
		translation.negate();
		final Vector3d interiorPointOnPlane = new Vector3d(plane.getA());
		interiorPointOnPlane.add(translation);
		final Vector3d transformedInteriorPointOnPlane = rotation.transform(
			interiorPointOnPlane);
		final Vector3d planeNormal = new Vector3d(plane.getB());
		planeNormal.normalize();
		final Vector3d transformedPlaneNormal = rotation.transform(planeNormal);
		final Vector3dc semiAxisLengths = new Vector3d(ellipsoid.getA(), ellipsoid
			.getB(), ellipsoid.getC());
		return findAxisAlignedCentredIntersectionEllipse(semiAxisLengths,
			new ValuePair<>(transformedInteriorPointOnPlane, transformedPlaneNormal));
	}

    List<Vector3d> findAxisAlignedCentredIntersectionEllipse(final Vector3dc semiAxisLengths, final ValuePair<Vector3dc, Vector3dc> transformedPlane) {
        final Vector3dc n = transformedPlane.getB();
        final List<Vector3dc> basis = completeBasis(semiAxisLengths, n);

        final Vector3dc q = transformedPlane.getA();
        final Vector2d tuCentre = getParametricCentre(semiAxisLengths,basis,q);
        final ValuePair<Vector2d,Vector2d> tuAxes = getParametricAxes(semiAxisLengths, basis, q);

        final Vector3d ellipseCentre = toWorldCoordinates(tuCentre,basis,q);
        final Vector3d ellipseFirstAxis = toWorldCoordinates(tuAxes.getA(),basis,new Vector3d());
        final Vector3d ellipseSecondAxis = toWorldCoordinates(tuAxes.getB(),basis,new Vector3d());

        return Arrays.asList(ellipseCentre, ellipseFirstAxis, ellipseSecondAxis);
    }

    private ValuePair<Vector2d, Vector2d> getParametricAxes(final Vector3dc semiAxisLengths, final List<Vector3dc> basis, final Vector3dc q) {
        final Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);

        final Vector3d diagonalTimesR = new Vector3d(basis.get(0));
        diagonal.transform(diagonalTimesR);
        final Vector3d diagonalTimesS = new Vector3d(basis.get(1));
        diagonal.transform(diagonalTimesS);
        final Vector3d diagonalTimesQ = new Vector3d(q);
        diagonal.transform(diagonalTimesQ);

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

    private Vector3d toWorldCoordinates(final Vector2dc tuCentre, final List<Vector3dc> basis, final Vector3dc origin) {
        final Vector3d tR = new Vector3d(basis.get(0));
        tR.mul(tuCentre.x());

        final Vector3d uS = new Vector3d(basis.get(1));
        uS.mul(tuCentre.y());

        final Vector3d world = new Vector3d(origin);
        world.add(tR);
        world.add(uS);

        return world;
    }

    private Vector2d getParametricCentre(final Vector3dc semiAxisLengths, final List<Vector3dc> basis, final Vector3dc q) {
        final Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);

        final Vector3d diagonalTimesR = new Vector3d(basis.get(0));
        diagonal.transform(diagonalTimesR);
        final Vector3d diagonalTimesS = new Vector3d(basis.get(1));
        diagonal.transform(diagonalTimesS);
        final Vector3d diagonalTimesQ = new Vector3d(q);
        diagonal.transform(diagonalTimesQ);

        final double tCoordinate = -diagonalTimesQ.dot(diagonalTimesR)/(diagonalTimesR.dot(diagonalTimesR));
        final double uCoordinate = -diagonalTimesQ.dot(diagonalTimesS)/(diagonalTimesS.dot(diagonalTimesS));

        return new Vector2d(tCoordinate, uCoordinate);
    }

    static List<Vector3dc> completeBasis(final Vector3dc semiAxisLengths, final Vector3dc n) {
        final Vector3d r = new Vector3d();
        n.orthogonalizeUnit(n, r);
        final Vector3d s = new Vector3d();
        r.cross(n,s);
        s.normalize();

        final Matrix3d diagonal = getDiagonalMatrix(semiAxisLengths);
        final Vector3d diagonalTimesR = new Vector3d(r);
        diagonal.transform(diagonalTimesR);
        final Vector3d diagonalTimesS = new Vector3d(s);
        diagonal.transform(diagonalTimesS);

        final double d1xRSquaredMinusD1xSSquared = diagonalTimesR.dot(diagonalTimesR)-diagonalTimesS.dot(diagonalTimesS);
        final double omega;
		if (Math.abs(d1xRSquaredMinusD1xSSquared) == 0.0) {
			omega = Math.PI / 4.0;
		}
		else {
			omega = 0.5 * Math.atan(2.0 * diagonalTimesR.dot(diagonalTimesS) /
				d1xRSquaredMinusD1xSSquared);
		}

        final Vector3d rWiggle =  getRWiggle(omega,r,s);
        final Vector3d sWiggle =  getSWiggle(omega,r,s);
        return Arrays.asList(rWiggle,sWiggle);
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
        return cosOmegaR.add(sinOmegaS);
    }

    private static Vector3d getSWiggle(final double omega, final Vector3dc r, final Vector3dc s) {
        final Vector3d minusSinOmegaR = new Vector3d(r);
        minusSinOmegaR.mul(-Math.sin(omega));
        final Vector3d cosOmegaS = new Vector3d(s);
        cosOmegaS.mul(Math.cos(omega));
        return cosOmegaS.add(minusSinOmegaR);
    }
}