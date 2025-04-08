/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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
package org.bonej.ops.skeletonize;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.AbstractOpTest;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class FindRidgePointsTest extends AbstractOpTest {

    @Test
    public void testSphereRidge() {
        //SET UP
        final Img<BitType> sphereImage = getSphereImage();
        final Vector3dc expectedSingleRidgePoint = new Vector3d(50.5, 50.5, 50.5);

        //EXECUTE
        final List<?> ridgePointList = (List<?>) ops.run(FindRidgePoints.class, new ImgPlus<>(sphereImage,
                "Sphere test image", new AxisType[] { Axes.X, Axes.Y, Axes.Z },
                new double[] { 1.0, 1.0, 1.0 }, new String[] { "", "", "" }));

        //VERIFY
        assertEquals(1, ridgePointList.size());
        final Vector3dc point = (Vector3dc) ridgePointList.get(0);
        assertEquals("Ridge point x-coordinate is wrong", expectedSingleRidgePoint.x(), point.x(),0);
        assertEquals("Ridge point y-coordinate is wrong", expectedSingleRidgePoint.y(), point.y(),0);
        assertEquals("Ridge point z-coordinate is wrong", expectedSingleRidgePoint.z(), point.z(),0);
    }


    //TODO move to somewhere where all tests can find this.
    private static Img<BitType> getSphereImage() {
        final long[] imageDimensions = { 101, 101, 101 };
        final Vector3dc centre = new Vector3d(Math.floor(imageDimensions[0] / 2.0),
                Math.floor(imageDimensions[1] / 2.0), Math.floor(imageDimensions[2] /
                2.0));
        final int radius = 10;
        final Img<BitType> sphereImg = ArrayImgs.bits(imageDimensions[0],
                imageDimensions[1], imageDimensions[2]);
        final Cursor<BitType> cursor = sphereImg.localizingCursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            final long[] coordinates = new long[3];
            cursor.localize(coordinates);
            final Vector3d v = centre.add(-coordinates[0], -coordinates[1],
                    -coordinates[2], new Vector3d());
            if (v.lengthSquared() <= radius * radius) {
                cursor.get().setOne();
            }
        }
        return sphereImg;
    }
}
