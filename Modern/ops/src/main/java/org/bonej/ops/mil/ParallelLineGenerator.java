package org.bonej.ops.mil;

import org.joml.Quaterniondc;
import org.joml.Vector3dc;

/**
 * An interface for random parallel line generators.
 */
public interface ParallelLineGenerator {
    Line nextLine();

    Vector3dc getDirection();

    void rotateDirection(final Quaterniondc rotation);

    /**
     * A line defined as a direction and point it passes through.
     */
    final class Line {
        final Vector3dc point;
        final Vector3dc direction;

        public Line(Vector3dc point, Vector3dc direction) {
            this.point = point;
            this.direction = direction;
        }
    }
}
