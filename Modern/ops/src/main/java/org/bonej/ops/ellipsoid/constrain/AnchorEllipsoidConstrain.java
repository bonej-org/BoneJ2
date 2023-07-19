/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.bonej.ops.ellipsoid.constrain;

import org.bonej.ops.ellipsoid.Ellipsoid;
import org.joml.Vector3d;

import java.util.Random;

public class AnchorEllipsoidConstrain implements EllipsoidConstrainStrategy {
    private static Random rng = new Random();
    private double[] surfacePointBefore;
    private Vector3d direction;

    @Override
    public void preConstrain(Ellipsoid ellipsoid, Vector3d fixedPoint) {
        double[] centre = ellipsoid.getCentre();

        direction = new Vector3d(fixedPoint.x - centre[0], fixedPoint.y - centre[1], fixedPoint.z - centre[2]);
        if (direction.length() <= 1.e-12) {
            centre[0] = fixedPoint.x + rng.nextGaussian() * 0.1;
            centre[1] = fixedPoint.y + rng.nextGaussian() * 0.1;
            centre[2] = fixedPoint.z + rng.nextGaussian() * 0.1;
            direction = new Vector3d(fixedPoint.x - centre[0], fixedPoint.y - centre[1], fixedPoint.z - centre[2]);
        }
        direction.normalize();
        surfacePointBefore = ellipsoid.getSurfacePoints(new double[][]{{direction.x, direction.y, direction.z}})[0];
    }

    @Override
    public void postConstrain(Ellipsoid ellipsoid) {
        double[] centre = ellipsoid.getCentre();
        final double[] surfacePointAfter = ellipsoid
                .getSurfacePoints(new double[][]{{direction.x, direction.y, direction.z}})[0];
        for (int i = 0; i < 3; i++) {
            centre[i] += surfacePointBefore[i] - surfacePointAfter[i];
        }
        ellipsoid.setCentroid(centre[0], centre[1], centre[2]);
    }
}
