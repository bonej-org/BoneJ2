
package org.bonej.ops;

import static java.util.Arrays.asList;
import static org.bonej.ops.TriplePointAngles.TriplePoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ij.ImagePlus;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Vertex;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * Unit tests for the TriplePointAngles class
 *
 * @author Richard Domander
 */
public class TriplePointAnglesTest {

	/**
	 * When measuring angles from the nth slab of the edges you get an error
	 * because the vertex centroid doesn't align with them. The error is the
	 * smaller the further away from the vertex you measure.
	 */
	public static final double HALF_PI_W_ERROR = 1.9106332362490184;
	private static final double HALF_PI = Math.PI / 2.0;
	private static final ImageJ IMAGE_J = new ImageJ();
	private static Graph[] cuboidGraphs;
	private static BinaryFunctionOp<Graph[], Integer, List<List<TriplePoint>>> triplePointAnglesOp;

	@BeforeClass
	public static void oneTimeSetup() {
		// Generate test image
		final ImagePlus wireFrameCuboid = TestImagePlusGenerator.wireFrameCuboid(5,
			5, 5, 1);

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
		triplePointAnglesOp = (BinaryFunctionOp) Functions.binary(IMAGE_J.op(),
			TriplePointAngles.class, List.class, Graph.class, 0);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	/** Regression test to check that results don't change */
	@Test
	public void testTriplePointAnglesNthPoint() throws AssertionError {
		final int nthPoint = 4;

		final List<List<TriplePoint>> graphs = triplePointAnglesOp.compute2(
			cuboidGraphs, nthPoint);

		assertEquals("Wrong number of skeletons (graphs)", 1, graphs.size());
		final List<TriplePoint> triplePoints = graphs.get(0);
		assertEquals("Wrong number of triple points", 8, triplePoints.size());
		int triplePointNumber = 1;
		for (final TriplePoint triplePoint : triplePoints) {
			assertEquals("A triple point has wrong graph number", 1, triplePoint
				.getGraphNumber());
			assertEquals("A triple point has the wrong number", triplePointNumber,
				triplePoint.getTriplePointNumber());
			final List<Double> angles = triplePoint.getAngles();
			assertEquals("Wrong number of angles", angles.size(), 3);
			angles.forEach(a -> assertEquals(
				"Triple point angle should be a right angle", HALF_PI_W_ERROR, a,
				1e-12));
			triplePointNumber++;
		}
	}

	/** Regression test to check that results don't change */
	@Test
	public void testTriplePointAnglesVertexToVertex() throws AssertionError {
		final List<List<TriplePoint>> graphs = triplePointAnglesOp.compute2(
			cuboidGraphs, TriplePointAngles.VERTEX_TO_VERTEX);

		assertEquals("Wrong number of skeletons (graphs)", 1, graphs.size());
		final List<TriplePoint> triplePoints = graphs.get(0);
		assertEquals("Wrong number of triple points", 8, triplePoints.size());
		int triplePointNumber = 1;
		for (final TriplePoint triplePoint : triplePoints) {
			assertEquals("A triple point has wrong graph number", 1, triplePoint
				.getGraphNumber());
			assertEquals("A triple point has the wrong number", triplePointNumber,
				triplePoint.getTriplePointNumber());
			final List<Double> angles = triplePoint.getAngles();
			assertEquals("Wrong number of angles", angles.size(), 3);
			angles.forEach(a -> assertEquals(
				"Triple point angle should be a right angle", HALF_PI, a, 1e-12));
			triplePointNumber++;
		}
	}

	@Test
	public void testHasCircularEdgesReturnsFalseIfGraphsNull() throws Exception {
		final boolean result = TriplePointAngles.hasCircularEdges(null);

		assertFalse("Null Graphs should return false", result);
	}

	@Test
	public void testHasCircularEdgesReturnsFalse() throws Exception {
		// Mock a Graph which has a triple point without a circular edge
		final Vertex vertex1 = mock(Vertex.class);
		final Vertex vertex2 = mock(Vertex.class);
		final Edge edge = mock(Edge.class);
		when(edge.getV1()).thenReturn(vertex1);
		when(edge.getV2()).thenReturn(vertex2);
		final ArrayList<Edge> edges = new ArrayList<>();
		edges.add(edge);
		edges.add(edge);
		edges.add(edge);
		when(vertex1.getBranches()).thenReturn(edges);
		final Graph graph = mock(Graph.class);
		final ArrayList<Vertex> vertices = new ArrayList<>();
		vertices.add(vertex1);
		when(graph.getVertices()).thenReturn(vertices);

		final boolean result = TriplePointAngles.hasCircularEdges(new Graph[] {
			graph });

		assertFalse("The graphs should not have circular edges", result);
	}

	@Test
	public void testHasCircularEdges() throws Exception {
		// Mock a Graph which has a triple point with a circular edge
		final Vertex vertex = mock(Vertex.class);
		final Edge edge = mock(Edge.class);
		when(edge.getV1()).thenReturn(vertex);
		when(edge.getV2()).thenReturn(vertex);
		final ArrayList<Edge> edges = new ArrayList<>();
		edges.add(edge);
		edges.add(edge);
		edges.add(edge);
		when(vertex.getBranches()).thenReturn(edges);
		final Graph graph = mock(Graph.class);
		final ArrayList<Vertex> vertices = new ArrayList<>();
		vertices.add(vertex);
		when(graph.getVertices()).thenReturn(vertices);

		final boolean result = TriplePointAngles.hasCircularEdges(new Graph[] {
			graph });

		assertTrue("The graphs should have circular edges", result);
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
