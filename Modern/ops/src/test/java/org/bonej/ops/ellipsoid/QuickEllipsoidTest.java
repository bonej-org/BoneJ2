/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
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
package org.bonej.ops.ellipsoid;

import org.junit.Test;

import static org.junit.Assert.*;

public class QuickEllipsoidTest {
    /**
     * Test for {@link QuickEllipsoid#getSurfacePoints(double[][])}
     *
     * searches in positive y and positive z directions for the surface point of an axis-aligned ellipsoid
     * with axis lengths (1,2,3) and centre (1,1,1)
     *
     *
     * ASCII sketch of situation in the x==1 plane:
     * '*' denotes ellipsoid centre
     * 'o' denotes a surface point that should be found, and its coordinates are noted as (x,y,z)
     *  the y and z axes, and the ellipsoid cross-section in the (x=1)-plane are sketched too
     *
     *   z
     *   ^  (1,1,4)
     *   | -o-
     *   |/ | \
     *   /  |  \
     *  ||  *--o (1,3,1)
     *   \-----/---> y
     *    \__ /
     */
    @Test
    public void testSurfacePoints() {
        QuickEllipsoid e = new QuickEllipsoid(new double[]{1,2,3}, new double[]{1,1,1},new double[][]{{1,0,0},{0,1,0},{0,0,1}});

        double [][] vectors = new double[2][3];
        vectors[0][2] = 1; //z-direction
        vectors[1][1] = 1; //y-direction

        final double[][] surfacePoints = e.getSurfacePoints(vectors);

        double [] surfacePoint0 = surfacePoints[0];
        assertEquals(1, surfacePoint0[0], 0.0);
        assertEquals(1, surfacePoint0[1], 0.0);
        assertEquals(4, surfacePoint0[2], 0.0);

        double [] surfacePoint1 = surfacePoints[1];
        assertEquals(1, surfacePoint1[0], 0.0);
        assertEquals(3, surfacePoint1[1], 0.0);
        assertEquals(1, surfacePoint1[2], 0.0);
    }

    /**
     * Test for {@link QuickEllipsoid#contains(double, double, double)}
     *
     * ASCII sketch of situation in the x==1 plane:
     * '*' denotes ellipsoid centre
     * 'o' denotes points that are tested for correct classification as inside/outside
     *  the ellipsoid cross-section in the (x=1)-plane are sketched too
     *
     *   z
     *   ^  (1,1,2)
     *   | ---
     *   |/ o \
     *   /     \
     *  ||  *   | o (1,4,1)
     *   \-----/---> y
     *    \__ /
     */

    @Test
    public void testContains() {
        QuickEllipsoid e = new QuickEllipsoid(new double[]{1,2,3}, new double[]{1,1,1},new double[][]{{1,0,0},{0,1,0},{0,0,1}});
        assertFalse(e.contains(1,4,1));
        assertTrue(e.contains(1,1,2));
    }
}
