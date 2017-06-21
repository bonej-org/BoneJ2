package org.bonej.ops;

import static org.bonej.ops.TestGraphs.createGBPShapedGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;

import org.bonej.ops.NPoint.VectorsAngle;
import org.bonej.utilities.GraphUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Vertex;

public class NPointAnglesTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static BinaryFunctionOp<List<Vertex>, Integer, List<NPoint>> nPointAnglesOp;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		nPointAnglesOp = (BinaryFunctionOp) Functions.binary(IMAGE_J.op(), NPointAngles.class, List.class, List.class,
				Integer.class);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		IMAGE_J.context().dispose();
	}

	@Test
	public void testLonelyVertex() {
		final Vertex lonelyVertex = new Vertex();
		final List<Vertex> lonelyVertexList = new ArrayList<>();
		lonelyVertexList.add(lonelyVertex);

		final List<NPoint> npoints = nPointAnglesOp.calculate(lonelyVertexList, -1);

		assertNotNull(npoints);
		assertEquals(1, npoints.size());
		assertEquals(lonelyVertex, npoints.get(0).centre);

		assertNotNull(npoints.get(0).angles);
		assertTrue(npoints.get(0).angles.isEmpty());
	}

	@Test
	public void testOneEdge() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(2).collect(Collectors.toList());

		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(1, 0, 0));

		final Edge edge = new Edge(vertices.get(0), vertices.get(1), null, 1.0);

		final Graph oneEdgeGraph = GraphUtil.createGraph(edge, vertices);

		final List<NPoint> npoints = nPointAnglesOp.calculate(oneEdgeGraph.getVertices(), -1);

		assertEquals(2, npoints.size());
		assertNotNull(npoints.get(0).angles);
		assertNotNull(npoints.get(1).angles);
		assertTrue(npoints.get(0).angles.isEmpty());
		assertTrue(npoints.get(1).angles.isEmpty());

	}

	@Test
	public void testFrameAngles() {
		final Graph frameGraph = TestGraphs.createFrameGraph();

		final List<NPoint> npoints = nPointAnglesOp.calculate(frameGraph.getVertices(), -1);

		assertEquals(4, npoints.size());
		final List<VectorsAngle> vectorsAngles = npoints.get(0).angles;
		assertEquals(3, vectorsAngles.size());
		for (VectorsAngle vectorsAngle : vectorsAngles) {
			assertEquals(Math.PI / 2.0, vectorsAngle.getAngle(), 1e-12);
		}
	}

	@Test
	public void testMeasurementModeLargerThanNSlabs() {
		final Graph poundShapedGraph = createGBPShapedGraph();

		final List<NPoint> npoints = nPointAnglesOp.calculate(poundShapedGraph.getVertices(), 5);
		final List<VectorsAngle> vectorsAngles = npoints.get(0).angles;

		assertEquals(Math.PI / 4.0, vectorsAngles.get(0).getAngle(), 1e-12);
	}

	@Test
	public void testMeasurementModeUsingSlabs() {
		final Graph poundShapedGraph = createGBPShapedGraph();

		final List<NPoint> npoints = nPointAnglesOp.calculate(poundShapedGraph.getVertices(), 1);
		final List<VectorsAngle> vectorsAngles = npoints.get(0).angles;

		assertEquals(Math.PI / 2.0, vectorsAngles.get(0).getAngle(), 1e-12);
	}
}
