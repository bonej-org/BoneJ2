
package org.bonej.ops.ita;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;

import org.bonej.ops.ita.NPoint.VectorsAngle;
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

	@Test
	public void testFrameAngles() {
		final Graph frameGraph = createFrameGraph();

		final List<NPoint> npoints = nPointAnglesOp.calculate(frameGraph
			.getVertices(), -1);

		assertEquals(4, npoints.size());
		final List<VectorsAngle> vectorsAngles = npoints.get(0).angles;
		assertEquals(3, vectorsAngles.size());
		for (final VectorsAngle vectorsAngle : vectorsAngles) {
			assertEquals(Math.PI / 2.0, vectorsAngle.getAngle(), 1e-12);
		}
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
	public void testMeasurementModeLargerThanNSlabs() {
		final Graph poundShapedGraph = createGBPShapedGraph();

		final List<NPoint> npoints = nPointAnglesOp.calculate(poundShapedGraph
			.getVertices(), 5);
		final List<VectorsAngle> vectorsAngles = npoints.get(0).angles;

		assertEquals(Math.PI / 4.0, vectorsAngles.get(0).getAngle(), 1e-12);
	}

	@Test
	public void testMeasurementModeUsingSlabs() {
		final Graph poundShapedGraph = createGBPShapedGraph();

		final List<NPoint> npoints = nPointAnglesOp.calculate(poundShapedGraph
			.getVertices(), 1);
		final List<VectorsAngle> vectorsAngles = npoints.get(0).angles;

		assertEquals(Math.PI / 2.0, vectorsAngles.get(0).getAngle(), 1e-12);
	}

	@Test
	public void testOneEdge() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(2).collect(
			Collectors.toList());

		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(1, 0, 0));

		final Edge edge = new Edge(vertices.get(0), vertices.get(1), null, 1.0);

		final Graph oneEdgeGraph = GraphUtil.createGraph(edge, vertices);

		final List<NPoint> npoints = nPointAnglesOp.calculate(oneEdgeGraph
			.getVertices(), -1);

		assertEquals(2, npoints.size());
		assertNotNull(npoints.get(0).angles);
		assertNotNull(npoints.get(1).angles);
		assertTrue(npoints.get(0).angles.isEmpty());
		assertTrue(npoints.get(1).angles.isEmpty());

	}

	/**
	 * Structure of the frame graph example ("frame" as in "coordinate frame")
	 *
	 * <pre>
	 * z
	 * |  y
	 * | /
	 * |/
	 * o-----x
	 * </pre>
	 */
	private static Graph createFrameGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(4).collect(
			Collectors.toList());

		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(1, 0, 0));
		vertices.get(2).addPoint(new Point(0, 1, 0));
		vertices.get(3).addPoint(new Point(0, 0, 1));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices
			.get(1), null, 1.0), new Edge(vertices.get(0), vertices.get(2), null,
				1.0), new Edge(vertices.get(0), vertices.get(3), null, 1.0));

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Structure of the Great British pound shaped graph example v: vertex s: slab
	 *
	 * <pre>
	 *
	 *   s v
	 * s
	 * s
	 * v s v
	 * </pre>
	 */
	private static Graph createGBPShapedGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(3).collect(
			Collectors.toList());

		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(2, 0, 0));
		vertices.get(2).addPoint(new Point(2, 2, 0));

		final ArrayList<Point> slabs1 = new ArrayList<>();
		slabs1.add(new Point(1, 0, 0));

		final ArrayList<Point> slabs2 = new ArrayList<>();
		slabs2.add(new Point(0, 1, 0));
		slabs2.add(new Point(0, 2, 0));
		slabs2.add(new Point(1, 3, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices
			.get(1), slabs1, 2.0), new Edge(vertices.get(0), vertices.get(2), slabs2,
				3.83));

		return GraphUtil.createGraph(edges, vertices);
	}

	@SuppressWarnings("unchecked")
	@BeforeClass
	public static void setUpBeforeClass() {
		nPointAnglesOp = (BinaryFunctionOp) Functions.binary(IMAGE_J.op(),
			NPointAngles.class, List.class, List.class, Integer.class);
	}

	@AfterClass
	public static void tearDownAfterClass() {
		IMAGE_J.context().dispose();
	}
}
