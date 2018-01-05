package org.bonej.ops.ellipsoid;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.junit.Test;
import org.scijava.vecmath.Vector3d;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

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
}
