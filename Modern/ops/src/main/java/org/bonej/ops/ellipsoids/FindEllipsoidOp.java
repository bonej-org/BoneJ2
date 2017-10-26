package org.bonej.ops.ellipsoids;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import net.imagej.ops.Contingent;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;

public class FindEllipsoidOp<B extends BooleanType<B>> extends AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, Vector3D, Ellipsoid> implements Contingent {

    private final static UnitSphereRandomVectorGenerator rvg = new UnitSphereRandomVectorGenerator(4);

    @Override
    public Ellipsoid calculate(final RandomAccessibleInterval<B> binaryImage, final Vector3D seedPoint) {

        double maxSamplingRadius = 25.0;

        int nSphere = estimateNSpiralPointsRequired(maxSamplingRadius,1.0);
        List<Vector3D> sphereSamplingDirections = getGeneralizedSpiralSetOnSphere(nSphere);

        Vector3D firstAxis = findClosestContact(seedPoint, sphereSamplingDirections);

        int nPlane = (int) Math.ceil(2*Math.PI*maxSamplingRadius);
        List<Vector3D> orthogonalSearchDirections = getOrthogonalSearchDirections(firstAxis, nPlane);

        Vector3D secondAxis = findClosestContact(seedPoint,orthogonalSearchDirections);

        List<Vector3D> thirdAxisSearchDirections = new ArrayList<>();
        thirdAxisSearchDirections.add(secondAxis.crossProduct(firstAxis));
        thirdAxisSearchDirections.add(secondAxis.crossProduct(firstAxis).scalarMultiply(-1.0));

        Vector3D thirdAxis = findClosestContact(seedPoint,thirdAxisSearchDirections);

        double a = firstAxis.getNorm();
        double b = secondAxis.getNorm();
        double c = thirdAxis.getNorm();

        return new Ellipsoid(a,b,c);
    }

    private static List<Vector3D> getOrthogonalSearchDirections(Vector3D firstAxis, int nPlane) {
        Rotation inPlaneRotation = new Rotation(firstAxis, 2*Math.PI/nPlane);

        double [] searchDirection = rvg.nextVector();
        Vector3D orthogonal = firstAxis.crossProduct(new Vector3D(searchDirection[0],searchDirection[1],searchDirection[2]));

        List<Vector3D> orthogonalSearchDirections = new ArrayList<>();

        for(int k=0; k<nPlane-1; k++)
        {
            orthogonalSearchDirections.add(new Vector3D(1.0,orthogonal));
            orthogonal = inPlaneRotation.applyTo(orthogonal);
        }
        return orthogonalSearchDirections;
    }

    private Vector3D findClosestContact(Vector3D seedPoint, List<Vector3D> samplingDirections) {
        List<Vector3D> contactPoints = new ArrayList<>();
        samplingDirections.forEach(d -> contactPoints.add(findFirstBGVoxelAlongRay(d.normalize(), seedPoint)));
        contactPoints.sort((o1, o2) -> {
            if(o1.subtract(seedPoint).getNorm()<o2.subtract(seedPoint).getNorm())
            {
                return -1;
            }
            if(o1.subtract(seedPoint).getNorm()>o2.subtract(seedPoint).getNorm())
            {
                return 1;
            }
            return 0;
        });

        return seedPoint.subtract(contactPoints.get(0));
    }


    static List<Vector3D> getGeneralizedSpiralSetOnSphere(int n){
        //follows nomenclature of Saff and Kuijlaars, 1997 describing the work of Rakhmanov et al, 1994
        //k is shifted to the left for convenient indexing.
        //n needs to be greater than 2.
        List<Vector3D> spiralSet = new ArrayList<>();

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
             spiralSet.add(new Vector3D(Math.sin(theta)*Math.cos(phi.get(k)), Math.sin(theta)*Math.sin(phi.get(k)), Math.cos(theta)));

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

    Vector3D findFirstBGVoxelAlongRay(final Vector3D direction, final Vector3D start)
    {
        RandomAccess<B> randomAccess = in1().randomAccess();

        Vector3D currentPosition = start;
        randomAccess.setPosition(vectorToPixelGrid(start));
        while(randomAccess.get().get())
        {
            currentPosition = currentPosition.add(direction);
            long[] currentPixelPosition = vectorToPixelGrid(currentPosition);
            if(!isInBounds(currentPixelPosition)) break;
            randomAccess.setPosition(currentPixelPosition);
        }
        return currentPosition;
    }

    private boolean isInBounds(long[] currentPixelPosition) {
        long width = in1().max(0);
        long height = in1().max(1);
        long depth = in1().max(2);
        return currentPixelPosition[0]>0 && currentPixelPosition[0]<width && currentPixelPosition[1]>0 && currentPixelPosition[1]<height && currentPixelPosition[2]>0 && currentPixelPosition[2]<depth;
    }

    private long[] vectorToPixelGrid(Vector3D currentPosition) {
        double[] dArray = currentPosition.toArray();
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
