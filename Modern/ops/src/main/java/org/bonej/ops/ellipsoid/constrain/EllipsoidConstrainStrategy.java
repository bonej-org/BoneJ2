package org.bonej.ops.ellipsoid.constrain;

import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.joml.Vector3d;

public interface EllipsoidConstrainStrategy {
    void preConstrain(QuickEllipsoid e, Vector3d fixed);
    void postConstrain(QuickEllipsoid e);
}