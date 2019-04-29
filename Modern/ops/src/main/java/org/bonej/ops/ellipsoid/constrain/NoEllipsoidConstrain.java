package org.bonej.ops.ellipsoid.constrain;

import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.joml.Vector3d;

public class NoEllipsoidConstrain implements EllipsoidConstrainStrategy {

    @Override
    public void preConstrain(QuickEllipsoid e, Vector3d fixed) {
        // do nothing
    }

    @Override
    public void postConstrain(QuickEllipsoid e) {
        // do nothing
    }
}
