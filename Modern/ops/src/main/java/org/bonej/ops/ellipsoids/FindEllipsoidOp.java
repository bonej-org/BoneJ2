package org.bonej.ops.ellipsoids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.imagej.ops.Contingent;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import static java.util.stream.Collectors.toList;

public class FindEllipsoidOp<B extends BooleanType<B>> extends AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, Vector3D, Ellipsoid> implements Contingent {


    @Override
    public Ellipsoid calculate(final RandomAccessibleInterval<B> binaryImage, final Vector3D seedPoint) {

        int n = estimateNSpiralPointsRequired(5,1.0);
        List<Vector3D> samplingDirections = generalizedSpiralSetOnSphere(n);
        List<Vector3D> contactPoints = new ArrayList<>();
        samplingDirections.forEach(d -> contactPoints.add(findLastFGVoxelAlongRay(d, seedPoint)));
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
        double a = seedPoint.subtract(contactPoints.get(0)).getNorm();
        return new Ellipsoid(a,a,a);
    }



    static List<Vector3D> generalizedSpiralSetOnSphere(int n){
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

    Vector3D findLastFGVoxelAlongRay(final Vector3D direction, final Vector3D start)
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
