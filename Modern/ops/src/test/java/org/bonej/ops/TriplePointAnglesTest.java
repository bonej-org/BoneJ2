package org.bonej.ops;

import ij.ImagePlus;
import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.skeletonize3D.Skeletonize3D_;

import java.util.List;

import static java.util.Arrays.asList;
import static org.bonej.ops.TriplePointAngles.TriplePoint;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the TriplePointAngles class
 *
 * @author Richard Domander
 */
public class TriplePointAnglesTest {
    private static final double HALF_PI = Math.PI / 2.0;
    private static final ImageJ IMAGE_J = new ImageJ();
    private static Graph[] cuboidGraphs;
    private static BinaryFunctionOp<Graph[], Integer, List<List<TriplePoint>>> triplePointAnglesOp;

    @BeforeClass
    public static void oneTimeSetup() {
        // Generate test image
        final ImagePlus wireFrameCuboid = TestImagePlusGenerator.wireFrameCuboid(5, 5, 5, 1);

        // Skeletonize image
        final Skeletonize3D_ skeletonize3D = new Skeletonize3D_();
        skeletonize3D.setup("", wireFrameCuboid);
        skeletonize3D.run(null);

        // Get skeleton graphs
        AnalyzeSkeleton_ analyzeSkeleton = new AnalyzeSkeleton_();
        analyzeSkeleton.setup("", wireFrameCuboid);
        analyzeSkeleton.run(AnalyzeSkeleton_.NONE, false, false, null, true, false);

        cuboidGraphs = analyzeSkeleton.getGraphs();

        // Match op
        triplePointAnglesOp = (BinaryFunctionOp) Functions.binary(IMAGE_J.op(), TriplePointAngles.class,
                                                                  List.class, Graph.class, 0);
    }

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    /** Regression test */
    @Test
    public void testTriplePointAnglesNthPoint() throws AssertionError {
        final int nthPoint = 4;

        final List<List<TriplePoint>> graphs = triplePointAnglesOp.compute2(cuboidGraphs, nthPoint);

        assertEquals("Wrong number of skeletons (graphs)", 1, graphs.size());
        final List<TriplePoint> triplePoints = graphs.get(0);
        assertEquals("Wrong number of triple points", 8, triplePoints.size());
        for (int t = 0; t < triplePoints.size(); t++) {
            final TriplePoint triplePoint = triplePoints.get(t);
            assertEquals("A triple point has wrong graph number", 0, triplePoint.getGraphNumber());
            assertEquals("A triple point has the wrong number", t, triplePoint.getTriplePointNumber());
            final List<Double> angles = triplePoint.getAngles();
            assertEquals("Wrong number of angles", angles.size(), 3);
            angles.forEach(a -> assertEquals("Triple point angle should be a right angle", HALF_PI, a, 1e-12));
        }
    }

    /** Regression test */
    @Test
    public void testTriplePointAnglesVertexToVertex() throws AssertionError {
        final List<List<TriplePoint>> graphs = triplePointAnglesOp.compute2(
                cuboidGraphs,
                TriplePointAngles.VERTEX_TO_VERTEX);

        assertEquals("Wrong number of skeletons (graphs)", 1, graphs.size());
        final List<TriplePoint> triplePoints = graphs.get(0);
        assertEquals("Wrong number of triple points", 8, triplePoints.size());
        for (int t = 0; t < triplePoints.size(); t++) {
            final TriplePoint triplePoint = triplePoints.get(t);
            assertEquals("A triple point has wrong graph number", 0, triplePoint.getGraphNumber());
            assertEquals("A triple point has the wrong number", t, triplePoint.getTriplePointNumber());
            final List<Double> angles = triplePoint.getAngles();
            assertEquals("Wrong number of angles", angles.size(), 3);
            angles.forEach(a -> assertEquals("Triple point angle should be a right angle", HALF_PI, a, 1e-12));
        }
    }

    @Test(expected = NullPointerException.class)
    public void testTriplePointConstructorThrowsNPEIfAnglesNull() {
        new TriplePoint(1, 1, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTriplePointConstructorThrowsIAEIfNotThreeAngles() {
        final List<Double> angles = asList(0.0, 1.0);
        new TriplePoint(1, 1, angles);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTriplePointConstructorThrowsIAEIfAngleNull() {
        final List<Double> angles = asList(0.0, 1.0, null);
        new TriplePoint(1, 1, angles);
    }
}