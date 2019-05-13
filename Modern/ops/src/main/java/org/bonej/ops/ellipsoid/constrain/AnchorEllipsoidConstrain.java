package org.bonej.ops.ellipsoid.constrain;

import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.joml.Vector3d;

import java.util.Random;

public class AnchorEllipsoidConstrain implements EllipsoidConstrainStrategy {
    private static Random rng = new Random();
    private double[] surfacePointBefore;
    private Vector3d direction;

    @Override
    public void preConstrain(QuickEllipsoid ellipsoid, Vector3d fixedPoint) {
        double[] centre = ellipsoid.getCentre();

        direction = new Vector3d(fixedPoint.x - centre[0], fixedPoint.y - centre[1], fixedPoint.z - centre[2]);
        if (direction.length() > 1.e-12) {
            direction.normalize();
        } else {
            centre[0] = fixedPoint.x + rng.nextGaussian() * 0.1;
            centre[1] = fixedPoint.y + rng.nextGaussian() * 0.1;
            centre[2] = fixedPoint.z + rng.nextGaussian() * 0.1;
            direction = new Vector3d(fixedPoint.x - centre[0], fixedPoint.y - centre[1], fixedPoint.z - centre[2]);
            direction.normalize();
        }
        surfacePointBefore = ellipsoid.getSurfacePoints(new double[][]{{direction.x, direction.y, direction.z}})[0];
    }

    @Override
    public void postConstrain(QuickEllipsoid ellipsoid) {
        double[] centre = ellipsoid.getCentre();
        final double[] surfacePointAfter = ellipsoid
                .getSurfacePoints(new double[][]{{direction.x, direction.y, direction.z}})[0];
        for (int i = 0; i < 3; i++) {
            centre[i] += surfacePointBefore[i] - surfacePointAfter[i];
        }
        ellipsoid.setCentroid(centre[0], centre[1], centre[2]);
    }
}
