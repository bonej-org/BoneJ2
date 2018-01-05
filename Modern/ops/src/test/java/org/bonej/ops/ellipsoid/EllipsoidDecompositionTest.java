package org.bonej.ops.ellipsoid;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.junit.Test;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;
import org.scijava.vecmath.Vector4d;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

//lots of expected values calculated with pen and paper.
public class EllipsoidDecompositionTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @SuppressWarnings("unchecked")
    private static UnaryFunctionOp<List<ValuePair<Vector3d, Vector3d>>, List<Ellipsoid>> ellipsoidDecomposition =
            (UnaryFunctionOp) Functions.unary(IMAGE_J.op(), EllipsoidDecomposition.class,
                    List.class, List.class);

    @Test
    public void testRadiusCalculation() {
        Vector3d vertexA = new Vector3d(0,0,0);
        Vector3d normalA = new Vector3d(0,-1,0);
        ValuePair<Vector3d,Vector3d> A = new ValuePair<>(vertexA, normalA);

        Vector3d vertexB = new Vector3d(0,2,0);
        Vector3d normalB = new Vector3d(0,1,0);
        ValuePair<Vector3d,Vector3d> B = new ValuePair<>(vertexB, normalB);

        Vector3d vertexC = new Vector3d(0,6,0);
        Vector3d normalC = new Vector3d(0,-1,0);
        ValuePair<Vector3d,Vector3d> C = new ValuePair<>(vertexC, normalC);

        Vector3d vertexD = new Vector3d(2,6,0);
        Vector3d normalD = new Vector3d(-Math.sqrt(2)/2.0,-Math.sqrt(2)/2.0,0);
        ValuePair<Vector3d,Vector3d> D = new ValuePair<>(vertexD, normalD);

        List<ValuePair<Vector3d,Vector3d>> ABCD = Arrays.asList(A,B,C,D);

        final List<Ellipsoid> ellipsoids = ellipsoidDecomposition.calculate(ABCD);

        assertEquals(3, ellipsoids.size());
        assertEquals(2, ellipsoids.stream().filter(e -> e.getA()==2.0).collect(Collectors.toList()).size());
        assertEquals(1, ellipsoids.stream().filter(e -> Math.abs(e.getA()-Math.sqrt(2.0))<1.0e-12).collect(Collectors.toList()).size());

    }

    @Test
    public void testQuadric1() {
        Vector3d sphereCentre = new Vector3d(3,4,5);
        double radius = 7.77;

        Matrix4d q1 = EllipsoidDecomposition.getQuadric1(sphereCentre,radius);

        Matrix3d identity = new Matrix3d();
        identity.setIdentity();
        Matrix3d q1Rotation = new Matrix3d();
        q1.getRotationScale(q1Rotation);
        assertEquals(identity, q1Rotation);

        Vector4d expected = new Vector4d(-3,-4,-5, 50-7.77*7.77);

        Vector4d bottomRow = new Vector4d();
        q1.getRow(3, bottomRow);
        assertEquals(expected, bottomRow);

        Vector4d rightColumn = new Vector4d();
        q1.getColumn(3, rightColumn);
        assertEquals(expected, rightColumn);

    }

    @Test
    public void testQuadric2() {

        Vector3d p = new Vector3d(4,4,1);
        Vector3d q = new Vector3d(2,2,1);
        Vector3d np = new Vector3d(-Math.sqrt(2)/2.0,-Math.sqrt(2)/2.0,0);
        Vector3d nq = new Vector3d(Math.sqrt(2)/2.0,Math.sqrt(2)/2.0,0);


        // @formatter:off
        final Matrix4d expected = new Matrix4d(
                0.5,0.5,0.0,-3.0,
                0.5,0.5,0.0,-3.0,
                0.0,0.0,0.0,0.0,
                -3.0,-3.0,0.0,16.0
        );
        // @formatter:on

        Matrix4d q2 = EllipsoidDecomposition.getQuadric2(p,q,np,nq);

        assertTrue(q2.epsilonEquals(expected,1.0e-12));


    }
}
