package org.bonej.ops.ellipsoids;

import net.imagej.ops.Contingent;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;

import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

public class findEllipsoidOp <B extends BooleanType<B>> extends AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, Vector3D, Ellipsoid> implements Contingent {


    @Override
    public Ellipsoid calculate(final RandomAccessibleInterval<B> binaryImage, final Vector3D seedPoint) {
        findLastFGVoxelAlongRay(new Vector3D(1,0,0), seedPoint);
        return null;
    }

    Vector3D findLastFGVoxelAlongRay(Vector3D direction, Vector3D start)
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
