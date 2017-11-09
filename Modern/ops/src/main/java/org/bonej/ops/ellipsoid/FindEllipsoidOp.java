package org.bonej.ops.ellipsoid;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.imagej.ops.Contingent;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;

import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.bonej.ops.RotateAboutAxis;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Vector3d;


public class FindEllipsoidOp<B extends BooleanType<B>> extends AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, Vector3d, Ellipsoid> implements Contingent {

    private final static UnitSphereRandomVectorGenerator rvg = new UnitSphereRandomVectorGenerator(4);
    private BinaryHybridCFI1<Vector3d, AxisAngle4d, Vector3d> rotateVectorOp;


    @Override
    public Ellipsoid calculate(final RandomAccessibleInterval<B> binaryImage, final Vector3d seedPoint) {
        rotateVectorOp = Hybrids.binaryCFI1(ops(), RotateAboutAxis.class, Vector3d.class,
                Vector3d.class, AxisAngle4d.class);

        double maxSamplingRadius = 25;
        double samplingWidth = 1.0;

        int nSphere = estimateNSpiralPointsRequired(maxSamplingRadius,samplingWidth);
        int nPlane = (int) Math.ceil(2*Math.PI*maxSamplingRadius/samplingWidth);
        List<Vector3d> sphereSamplingDirections = getGeneralizedSpiralSetOnSphere(nSphere);

        List<Ellipsoid> ellipsoids = sphereSamplingDirections.stream().map(dir -> getEllipsoidFromInitialAxis(seedPoint, nPlane, dir)).collect(Collectors.toList());

        ellipsoids.sort((o1,o2) -> {
                if(o1.getVolume()<o2.getVolume())
                {
                    return 1;
                }
                if(o1.getVolume()>o2.getVolume())
                {
                    return -1;
                }
                return 0;
            });

        return ellipsoids.get(0);
    }

    public Ellipsoid getEllipsoidFromInitialAxis(Vector3d seedPoint, int nPlane, Vector3d initialAxis) {
        List<Vector3d> firstDirectionToTry = new ArrayList<>();
        firstDirectionToTry.add(initialAxis);

        Vector3d firstAxis = findClosestContact(seedPoint, firstDirectionToTry);

        List<Vector3d> orthogonalSearchDirections = getSearchDirectionsInPlane(getFlooredVector3d(firstAxis), nPlane);

        Vector3d secondAxis = findClosestContact(seedPoint,orthogonalSearchDirections);

        List<Vector3d> thirdAxisSearchDirections = new ArrayList<>();

        Vector3d thirdAxisSearchDirection = new Vector3d();
        thirdAxisSearchDirection.cross(secondAxis,firstAxis);
        thirdAxisSearchDirection.normalize();
        Vector3d negativeThirdAxisSearchDirection = new Vector3d(thirdAxisSearchDirection);
        negativeThirdAxisSearchDirection.scale(-1.0);

        thirdAxisSearchDirections.add(thirdAxisSearchDirection);
        thirdAxisSearchDirections.add(negativeThirdAxisSearchDirection);

        Vector3d thirdAxis = findClosestContact(seedPoint,thirdAxisSearchDirections);

        double a = Math.max(firstAxis.length()-1,1.0);
        double b = Math.max(secondAxis.length()-1,1.0);
        double c = Math.max(thirdAxis.length()-1,1.0);

        Ellipsoid ellipsoid = new Ellipsoid(a,b,c);

        //ellipsoid.setOrientation(new Matrix3d(firstAxis.getX(),secondAxis.getX(),thirdAxis.getX()));

        return ellipsoid;
    }

    static Vector3d getFlooredVector3d(Vector3d vector) {
        return new Vector3d(Math.floor(vector.getX()), Math.floor(vector.getY()), Math.floor(vector.getZ()));
    }

    private List<Vector3d> getSearchDirectionsInPlane(Vector3d planeNormalAxis, int n) {
        AxisAngle4d axisAngle = new AxisAngle4d(planeNormalAxis, 2*Math.PI/n);

        double [] searchDirection = rvg.nextVector();
        Vector3d inPlaneVector = new Vector3d(searchDirection[0],searchDirection[1],searchDirection[2]);
        inPlaneVector.cross(planeNormalAxis, inPlaneVector);
        inPlaneVector.normalize();

        List<Vector3d> inPlaneSearchDirections = new ArrayList<>();

        for(int k=0; k<n-1; k++)
        {
            inPlaneSearchDirections.add(inPlaneVector);
            inPlaneVector = rotateVectorOp.calculate(inPlaneVector,axisAngle);
        }
        return inPlaneSearchDirections;
    }

    private Vector3d findClosestContact(Vector3d seedPoint, List<Vector3d> samplingDirections) {
            List<Vector3d> contactPoints = new ArrayList<>();

            samplingDirections.forEach(d -> {
                d.normalize();
                contactPoints.add(findFirstPointInBGAlongRay(d, seedPoint));});

            contactPoints.sort((o1, o2) -> {
                o1.scaleAdd(-1.0,seedPoint);
                o2.scaleAdd(-1.0,seedPoint);
                if (o1.length() < o2.length()) {
                    return -1;
                }
                if (o1.length() > o2.length()) {
                    return 1;
                }
                return 0;
            });

            Vector3d closestContact = contactPoints.get(0);
            closestContact.scaleAdd(-1.0,seedPoint);
            return closestContact;
    }

    static List<Vector3d> getGeneralizedSpiralSetOnSphere(int n){
        //follows nomenclature of Saff and Kuijlaars, 1997 describing the work of Rakhmanov et al, 1994
        //k is shifted to the left for convenient indexing.
        //n needs to be greater than 2.
        List<Vector3d> spiralSet = new ArrayList<>();

        List<Double> phi = new ArrayList<>();
        phi.add(0.0);
        for (int k=1; k<n-1;k++)
        {
            double h = -1.0+2.0*((double)k)/(n-1);
            phi.add(getPhiByRecursion(n, phi.get(k - 1), h));
        }
        phi.add(0.0);

        for(int k=0; k<n; k++)
        {
            double h = -1.0+2.0*((double)k)/(n-1);
            double theta = Math.acos(h);
             spiralSet.add(new Vector3d(Math.sin(theta)*Math.cos(phi.get(k)), Math.sin(theta)*Math.sin(phi.get(k)), Math.cos(theta)));

        }

        return spiralSet;
    }


    private static double getPhiByRecursion(double n, double phiKMinus1, double hk) {
        double phiK = phiKMinus1+3.6/Math.sqrt(n)*1.0/Math.sqrt(1-hk*hk);
        //modulo 2pi calculation works for positive numbers only, which is not a problem in this case.
        return phiK - Math.floor(phiK/(2*Math.PI))*2*Math.PI;
    }

    private static int estimateNSpiralPointsRequired(double searchRadius, double pixelWidth)
    {
        return (int) Math.ceil(Math.pow(searchRadius*3.809/pixelWidth,2));
    }

    Vector3d findFirstPointInBGAlongRay(final Vector3d rayIncrement, final Vector3d start)
    {
        RandomAccess<B> randomAccess = in1().randomAccess();

        Vector3d currentRealPosition = start;
        long[] currentPixelPosition = vectorToPixelGrid(start);
        randomAccess.setPosition(currentPixelPosition);

        while(randomAccess.get().get())
        {
            currentRealPosition.add(rayIncrement);
            currentPixelPosition = vectorToPixelGrid(currentRealPosition);
            if(!isInBounds(currentPixelPosition)) break;
            randomAccess.setPosition(currentPixelPosition);
        }
        return currentRealPosition;
    }

    private boolean isInBounds(long[] currentPixelPosition) {
        long width = in1().max(0);
        long height = in1().max(1);
        long depth = in1().max(2);
        return currentPixelPosition[0]>0 && currentPixelPosition[0]<width && currentPixelPosition[1]>0 && currentPixelPosition[1]<height && currentPixelPosition[2]>0 && currentPixelPosition[2]<depth;
    }

    private long[] vectorToPixelGrid(Vector3d currentPosition) {
        double[] dArray = {currentPosition.getX(), currentPosition.getY(), currentPosition.getZ()};
        long[] lArray = new long[3];
        lArray[0] = (long) dArray[0];
        lArray[1] = (long) dArray[1];
        lArray[2] = (long) dArray[2];
        return lArray;
    }

    @Override
    public boolean conforms() {
        return in1().numDimensions() == 3;
    }
}
