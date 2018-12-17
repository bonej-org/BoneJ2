package org.bonej.ops.ellipsoid;


import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.Dimensions;
import net.imglib2.img.Img;
import org.bonej.utilities.VectorUtil;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.List;

@Plugin(type = Op.class)
public class ellipsoidSamplingPointsOutsideRatio extends AbstractBinaryFunctionOp<Img,Ellipsoid,Boolean> {

    @Parameter
    private List<Vector3dc> samplingDirections;

    @Override
    public Boolean calculate(Img img, Ellipsoid ellipsoid) {
        final Matrix3d a = new Matrix3d();
        ellipsoid.getOrientation().get3x3(a);
        final Vector3dc centroid = ellipsoid.getCentroid();
        final long surfacePointsOutside = samplingDirections.stream().filter(dir -> {
            final Vector3dc aTimesDir = a.transform(dir, new Vector3d());
            final double surfaceIntersectionParameter = Math.sqrt(1.0 / dir.dot(
                    aTimesDir));
            final Vector3d intersectionPoint = new Vector3d(dir);
            intersectionPoint.mul(surfaceIntersectionParameter);
            intersectionPoint.add(centroid);
            final long[] pixel = VectorUtil.toPixelGrid(intersectionPoint);
            if (outOfBounds(img, pixel)) {
                return true;
            } else {
                return false;
            }
        }).count();

        return 0.5<((double) surfacePointsOutside/((double) samplingDirections.size()));
    }
    
    // TODO make into a utility method, and see where else needed in BoneJ
    private static boolean outOfBounds(final Dimensions dimensions, final long[] currentPixelPosition) {
        for (int i = 0; i < currentPixelPosition.length; i++) {
            final long position = currentPixelPosition[i];
            if (position < 0 || position >= dimensions.dimension(i)) {
                return true;
            }
        }
        return false;
    }
}
