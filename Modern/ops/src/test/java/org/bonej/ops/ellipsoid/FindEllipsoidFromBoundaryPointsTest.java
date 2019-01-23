/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.ops.ellipsoid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.util.ValuePair;

import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4d;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Tests for {@link FindEllipsoidFromBoundaryPoints}
 * <p>
 * A lot of the expected values were calculated with pen &amp; paper and sympy.
 * </p>
 *
 * @author Alessandro Felder
 */
// TODO: put calculations in javadoc and user documentation
public class FindEllipsoidFromBoundaryPointsTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testFittingEllipsoidToEquidistantCollisionPoints() {
        final Vector3d vertexP = new Vector3d(-2, 0, 0);
        final Vector3d normalP = new Vector3d(1, 0, 0);
        final ValuePair<Vector3dc,Vector3dc> p = new ValuePair<>(vertexP,normalP);

        final Vector3d vertexQ = new Vector3d(2, 0, 0);
        final Vector3d normalQ = new Vector3d(-1, 0, 0);
        final ValuePair<Vector3dc,Vector3dc> q = new ValuePair<>(vertexQ,normalQ);

        final Vector3d vertexR = new Vector3d(0, 2, 0);
        final Vector3d normalR = new Vector3d(0, -1, 0);
        final ValuePair<Vector3dc,Vector3dc> r = new ValuePair<>(vertexR,normalR);

        final Vector3d vertexS = new Vector3d(0, -2, 0);
        final Vector3d normalS = new Vector3d(0, 1, 0);
        final ValuePair<Vector3dc,Vector3dc> s = new ValuePair<>(vertexS,normalS);

        final Vector3dc vertexTooFarAway = new Vector3d(10, -20, 4);

        final List<ValuePair<Vector3dc, Vector3dc>> fourVertices = Arrays.asList(p, q, r, s);

        final Optional<Ellipsoid> ellipsoid = FindEllipsoidFromBoundaryPoints.tryToFindEllipsoid(new Vector3d(0,0,0), fourVertices);

        assertTrue(ellipsoid.isPresent());
        assertTrue(testPointIsOnEllipsoidSurface(vertexP, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexR, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexS, ellipsoid.get()));
        assertTrue(!testPointIsOnEllipsoidSurface(vertexTooFarAway, ellipsoid.get()));
    }

    @Test
    public void testFittingEllipsoidToFiveInputPointsEasy() {
        final Vector3dc vertexP = new Vector3d(0, 2, 0);
        final Vector3dc normalP = new Vector3d(0, 1, 0);
        final ValuePair<Vector3dc,Vector3dc> p = new ValuePair<>(vertexP,normalP);

        final Vector3dc vertexQ = new Vector3d(0, 4, 0);
        final Vector3dc normalQ = new Vector3d(-1, 0, 0);
        final ValuePair<Vector3dc,Vector3dc> q = new ValuePair<>(vertexQ,normalQ);

        final Vector3dc vertexR = new Vector3d(3, 3, 0);
        final Vector3dc normalR = new Vector3d(0, -1, 0);
        final ValuePair<Vector3dc,Vector3dc> r = new ValuePair<>(vertexR,normalR);

        final Vector3dc vertexS = new Vector3d(-3, 3, 0);
        final Vector3dc normalS = new Vector3d(0, 1, 0);
        final ValuePair<Vector3dc,Vector3dc> s = new ValuePair<>(vertexS,normalS);

        final Vector3dc vertexTooFarAway = new Vector3d(10, -20, 4);

        final List<ValuePair<Vector3dc, Vector3dc>> fourVertices = Arrays.asList(p, q, r, s);

        final Optional<Ellipsoid> ellipsoid = FindEllipsoidFromBoundaryPoints.tryToFindEllipsoid(new Vector3d(0,3,0), fourVertices);

        assertTrue(ellipsoid.isPresent());
        assertTrue(testPointIsOnEllipsoidSurface(vertexP, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexR, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexS, ellipsoid.get()));
        assertTrue(!testPointIsOnEllipsoidSurface(vertexTooFarAway, ellipsoid.get()));
    }

    @Test
    public void testFittingEllipsoidToFiveInputPointsDifficult() {
        final Vector3d vertexP = new Vector3d(0, 0, 0);
        final Vector3d normalP = new Vector3d(0, 1, 0);
        final ValuePair<Vector3dc,Vector3dc> p = new ValuePair<>(vertexP,normalP);

        final Vector3d vertexQ = new Vector3d(1, 3, 0);
        final Vector3d normalQ = new Vector3d(-1, 0, 0);
        final ValuePair<Vector3dc,Vector3dc> q = new ValuePair<>(vertexQ,normalQ);

        final Vector3d vertexR = new Vector3d(-4, 2, 0);
        final Vector3d normalR = new Vector3d(0, -1, 0);
        final ValuePair<Vector3dc,Vector3dc> r = new ValuePair<>(vertexR,normalR);

        final Vector3d vertexS = new Vector3d(-1.5, 3, 8);
        final Vector3d normalS = new Vector3d(0, 0, -1);
        final ValuePair<Vector3dc,Vector3dc> s = new ValuePair<>(vertexS,normalS);

        final Vector3dc vertexTooFarAway = new Vector3d(10, -20, 4);

        final List<ValuePair<Vector3dc, Vector3dc>> fourVertices = Arrays.asList(p, q, r, s);

        final Optional<Ellipsoid> ellipsoid = FindEllipsoidFromBoundaryPoints.tryToFindEllipsoid(new Vector3d(-1,2,0),fourVertices);

        assertTrue(ellipsoid.isPresent());
        assertTrue(testPointIsOnEllipsoidSurface(vertexP, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexR, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexS, ellipsoid.get()));
        assertTrue(!testPointIsOnEllipsoidSurface(vertexTooFarAway, ellipsoid.get()));
    }

    private boolean testPointIsOnEllipsoidSurface(final Vector3dc point, final Ellipsoid ellipsoid) {
        final Vector3d xminusC = ellipsoid.getCentroid();
        xminusC.mul(-1);
        xminusC.add(point);

        final Matrix3d rotationFromAxisAligned = new Matrix3d();
        ellipsoid.getOrientation().get3x3(rotationFromAxisAligned);

        final Matrix3d rotationToAxisAligned = new Matrix3d(rotationFromAxisAligned);
        rotationToAxisAligned.transpose();

        final List<Vector3d> ellipsoidSemiAxes = ellipsoid.getSemiAxes();
        final Matrix3d scale = new Matrix3d();
        scale.m00 = 1.0 / (ellipsoidSemiAxes.get(0).lengthSquared());
        scale.m11 = 1.0 / (ellipsoidSemiAxes.get(1).lengthSquared());
        scale.m22 = 1.0 / (ellipsoidSemiAxes.get(2).lengthSquared());

        final Matrix3d SR = new Matrix3d(scale);
        SR.mul(rotationToAxisAligned);
        final Matrix3d A = new Matrix3d();
        rotationFromAxisAligned.mul(SR, A);

        final Vector3d Ax = new Vector3d(xminusC);
        A.transform(Ax);

        final double shouldBeOne = xminusC.dot(Ax);

        return Math.abs(shouldBeOne - 1.0) < 1.0e-12;
    }

    @Test
    public void testQ1() {
        final Vector3dc sphereCentre = new Vector3d(3, 4, 5);
        final double radius = 7.77;

        final Matrix4d q1 = FindEllipsoidFromBoundaryPoints.getQ1(new ValuePair<>(sphereCentre, new Vector3d()), radius);

        Matrix3d identity = new Matrix3d();
        identity = identity.identity();
        final Matrix3d q1Rotation = new Matrix3d();
        q1.get3x3(q1Rotation);
        assertEquals(identity, q1Rotation);

        final Vector4d expected = new Vector4d(-3, -4, -5, 50 - 7.77 * 7.77);

        final Vector4d bottomRow = new Vector4d();
        q1.getRow(3, bottomRow);
        assertEquals(expected, bottomRow);

        final Vector4d rightColumn = new Vector4d();
        q1.getColumn(3, rightColumn);
        assertEquals(expected, rightColumn);

    }

    @Test
    public void testQ2() {
        final Vector3dc p = new Vector3d(4, 4, 1);
        final Vector3dc q = new Vector3d(2, 2, 1);
        final Vector3dc np = new Vector3d(-Math.sqrt(2) / 2.0, -Math.sqrt(2) / 2.0, 0);
        final Vector3dc nq = new Vector3d(Math.sqrt(2) / 2.0, Math.sqrt(2) / 2.0, 0);


        // @formatter:off
        final Matrix4dc expected = new Matrix4d(
                0.5, 0.5, 0.0, -3.0,
                0.5, 0.5, 0.0, -3.0,
                0.0, 0.0, 0.0, 0.0,
                -3.0, -3.0, 0.0, 16.0
        );
        // @formatter:on

        final Matrix4d q2 = FindEllipsoidFromBoundaryPoints.getQ2(new ValuePair<>(p, np), new ValuePair<>(q, nq));

        assertEquals(q2.m00(),expected.m00(),1.0e-12);
        assertEquals(q2.m01(),expected.m01(),1.0e-12);
        assertEquals(q2.m02(),expected.m02(),1.0e-12);
        assertEquals(q2.m03(),expected.m03(),1.0e-12);

        assertEquals(q2.m10(),expected.m10(),1.0e-12);
        assertEquals(q2.m11(),expected.m11(),1.0e-12);
        assertEquals(q2.m12(),expected.m12(),1.0e-12);
        assertEquals(q2.m13(),expected.m13(),1.0e-12);

        assertEquals(q2.m20(),expected.m20(),1.0e-12);
        assertEquals(q2.m21(),expected.m21(),1.0e-12);
        assertEquals(q2.m22(),expected.m22(),1.0e-12);
        assertEquals(q2.m23(),expected.m23(),1.0e-12);

        assertEquals(q2.m30(),expected.m30(),1.0e-12);
        assertEquals(q2.m31(),expected.m31(),1.0e-12);
        assertEquals(q2.m32(),expected.m32(),1.0e-12);
        assertEquals(q2.m33(),expected.m33(),1.0e-12);
    }
}
