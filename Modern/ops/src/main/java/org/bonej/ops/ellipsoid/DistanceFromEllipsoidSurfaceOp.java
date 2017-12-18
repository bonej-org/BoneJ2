package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Vector2d;
import org.scijava.vecmath.Vector3d;

/**
 * An Op that calculates the distance between a point and an ellipsoid surface
 * <p>
 * Uses a hard-coded bivariate Newton-Raphson solver to efficiently find values of theta and phi that solve
 * the orthogonality condition F(theta,phi)=0 for the vector between the surface and the point.
 * Then converts into Cartesian coordinates to calculate the distance. Derivation of required terms courtesy
 * of Robert Nürnberg, Imperial College London (http://wwwf.imperial.ac.uk/~rn/distance2ellipse.pdf).
 * </p>
 *
 * @author Alessandro Felder
 */

@Plugin(name = "Distance from Point to Ellipsoid Surface", type = Op.class)
public class DistanceFromEllipsoidSurfaceOp extends AbstractBinaryFunctionOp<Ellipsoid, Point3d, DoubleType>
{

    @Parameter(required = false, persist = false)
    private double tolerance = 1.0e-12;

    @Parameter(required = false, persist = false)
    private long maxIterations = 100;

    @Override
    public DoubleType calculate(final Ellipsoid ellipsoid, final Point3d point) {

        double a = ellipsoid.getA();
        double b = ellipsoid.getB();
        double c = ellipsoid.getC();

        Point3d pointInEllipsoidCoordinates = ToEllipsoidCoordinates(point,ellipsoid);
        
        double rootTerm = Math.sqrt(pointInEllipsoidCoordinates.x*pointInEllipsoidCoordinates.x/(a*a)+pointInEllipsoidCoordinates.y*pointInEllipsoidCoordinates.y/(b*b));
        Vector2d anglesK = new Vector2d(Math.atan2(a*pointInEllipsoidCoordinates.y,b*pointInEllipsoidCoordinates.x),Math.atan2(pointInEllipsoidCoordinates.z, c*rootTerm));
        Vector2d anglesKPlus1 = new Vector2d(0.0,0.0);
        long iterations = 0;
        while(iterations<maxIterations)
        {
            anglesKPlus1 = new Vector2d(anglesK.x, anglesK.y);
            anglesKPlus1.add(DFInverseTimesF(anglesK,ellipsoid,pointInEllipsoidCoordinates));
            if(getDifference(anglesK, anglesKPlus1)<tolerance) break;

            anglesK = new Vector2d(anglesKPlus1.x, anglesKPlus1.y);
            iterations++;
        }

        Vector3d closestPointOnEllipsoidSurface = getCartesianCoordinates(anglesKPlus1,ellipsoid);
        closestPointOnEllipsoidSurface.scaleAdd(-1.0,pointInEllipsoidCoordinates);
        return new DoubleType(closestPointOnEllipsoidSurface.length());
    }

    static Point3d ToEllipsoidCoordinates(final Point3d point, final Ellipsoid ellipsoid) {
        Point3d translated = new Point3d(ellipsoid.getCentroid());
        translated.scale(-1.0);
        translated.add(point);
        Point3d rotated = new Point3d(0,0,0);
        Matrix4d orientation = ellipsoid.getOrientation();
        rotated.x = orientation.m00*translated.x+ orientation.m10*translated.y+orientation.m20*translated.z;
        rotated.y = orientation.m01*translated.x+ orientation.m11*translated.y+orientation.m21*translated.z;
        rotated.z = orientation.m02*translated.x+ orientation.m12*translated.y+orientation.m22*translated.z;
        return rotated;
    }

    private static Vector3d getCartesianCoordinates(final Vector2d angles, final Ellipsoid ellipsoid) {
        double theta = angles.x;
        double phi = angles.y;

        double x = ellipsoid.getA()*Math.cos(phi)*Math.cos(theta);
        double y = ellipsoid.getB()*Math.cos(phi)*Math.sin(theta);
        double z = ellipsoid.getC()*Math.sin(phi);

        return new Vector3d(x,y,z);
    }

    static private Vector2d DFInverseTimesF(final Vector2d angles, final Ellipsoid ellipsoid, final Point3d point) {
        double a = ellipsoid.getA();
        double b = ellipsoid.getB();
        double c = ellipsoid.getC();
        double a2mb2 = (a*a-b*b);

        double x = point.x;
        double y = point.y;
        double z = point.z;

        double theta = angles.x;
        double sinTh = Math.sin(theta);
        double sin2Th = sinTh*sinTh;
        double cosTh = Math.cos(theta);
        double cos2Th = cosTh*cosTh;

        double phi = angles.y;
        double sinPh = Math.sin(phi);
        double cosPh = Math.cos(phi);

        double a11 = a2mb2*(cos2Th-sin2Th)*cosPh-x*a*cosTh-y*b*sinTh;
        double a12 = -a2mb2*cosTh*sinTh*sinPh;
        double a21 = -2.0*a2mb2*cosTh*sinTh*sinPh*cosPh+x*a*sinPh*sinTh-y*b*sinPh*cosTh;
        double a22 = (a*a*cos2Th+b*b*sin2Th-c*c)*(cos2Th-sin2Th)-x*a*cosPh*cosTh-y*b*cosPh*sinTh-z*c*sinPh;

        double f1 = a2mb2*cosTh*sinTh*cosPh-x*a*sinTh+y*b*cosTh;
        double f2 = (a*a*cos2Th+b*b*sin2Th-c*c)*sinPh*cosPh-x*a*sinPh*cosTh-y*b*sinPh*sinTh+z*c*cosPh;

        double out1 = a22*f1-a12*f2;
        double out2 = -a21*f1+a11*f2;
        
        double determinant = a11*a22-a12*a21;

        if(determinant==0.0)
        {
            throw new ArithmeticException("Solution is not unique.");
        }
        return new Vector2d(out1/determinant, out2/determinant);
    }

    static private double getDifference(final Vector2d angles1, final Vector2d angles2) {
        Vector2d difference = new Vector2d(angles1.x-angles2.x, angles1.y-angles2.y);
        return difference.length();
    }
}

