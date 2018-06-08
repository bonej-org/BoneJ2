
package org.bonej.ops.ita;

import static org.bonej.ops.ita.CleanShortEdges.findClusters;
import static org.bonej.ops.ita.CleanShortEdges.isShortEdge;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;

import org.bonej.ops.TestGraphs;
import org.bonej.ops.ita.CleanShortEdges;
import org.bonej.utilities.GraphUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Vertex;

public class CleanShortEdgesTest {

	private ImageJ imageJ;
	private BinaryFunctionOp<Graph, Double, Graph> cleanShortEdgesOp;

	@Before
	public void setup() {
		imageJ = new ImageJ();
		cleanShortEdgesOp = Functions.binary(imageJ.op(), CleanShortEdges.class, Graph.class, Graph.class,
				Double.class);
	}

	@After
	public void tearDown() {
		imageJ.context().dispose();
	}

	@Test
	public void testNoParallelEdgesInOutput() throws Exception {
		final Graph graph = TestGraphs.createTriangleWithSquareClusterAndArtefacts();

		final Graph result = cleanShortEdgesOp.calculate(graph, Double.MAX_VALUE);

		assertEquals(0, CleanShortEdges.removeParallelEdges(result));
	}

	@Test
	public void testInputNotAltered() {
		final Graph graph = TestGraphs.createSailGraph();
		final Graph cloneGraph = graph.clone();

		cleanShortEdgesOp.calculate(graph, Double.MAX_VALUE);

		assertGraphsEqual(graph, cloneGraph);
	}

	/**
	 * Tests that graph cloning works, and that the input {@link Graph} is not
	 * altered
	 */
	@Test
	public void testCloning() throws Exception {
		// SETUP
		final Graph graph = TestGraphs.createTriangleWithSquareClusterAndArtefacts();
		final Random random = new Random(0xC0FF33);
		graph.getVertices().forEach(v -> {
			v.setVisited(random.nextBoolean(), random.nextInt());
			final ArrayList<Edge> edges = graph.getEdges();
			v.setPredecessor(edges.get(random.nextInt(edges.size())));
		});
		graph.getEdges().forEach(e -> {
			e.setType(random.nextInt());
			e.setLength(random.nextDouble());
			e.setColor(random.nextDouble());
			e.setColor3rd(random.nextDouble());

		});

		// EXECUTE
		final Graph cloneGraph = graph.clone();

		// VERIFY
		assertGraphsEqual(graph, cloneGraph);
	}

	private void assertGraphsEqual(final Graph expected, final Graph actual) {
		final ArrayList<Vertex> vertices = expected.getVertices();
		final ArrayList<Vertex> cloneVertices = actual.getVertices();
		assertEquals(vertices.size(), cloneVertices.size());
		IntStream.range(0, vertices.size()).forEach(i -> {
			final Vertex vertex = vertices.get(i);
			final Vertex clonedVertex = cloneVertices.get(i);
			assertVerticesEqual(vertex, clonedVertex, expected.getEdges(), actual.getEdges());
		});
		// VERIFY EDGES
		final ArrayList<Edge> clonedEdges = actual.getEdges();
		assertTrue("Vertices have branches that are not listed in the Edge list",
				cloneVertices.stream().flatMap(v -> v.getBranches().stream()).allMatch(clonedEdges::contains));
		final ArrayList<Edge> edges = expected.getEdges();
		assertEquals("Graph has wrong number of edges", edges.size(), clonedEdges.size());
		IntStream.range(0, edges.size()).forEach(i -> {
			final Edge edge = edges.get(i);
			final Edge clonedEdge = clonedEdges.get(i);
			assertEdgeEquals(edge, clonedEdge, expected.getVertices(), actual.getVertices());
		});
	}

	private void assertVerticesEqual(final Vertex expected, final Vertex actual, final List<Edge> expectedEdges,
			final List<Edge> actualEdges) {
		assertEquals(expected.getVisitOrder(), actual.getVisitOrder());
		assertEquals(expected.isVisited(), actual.isVisited());
		assertEquals(expectedEdges.indexOf(expected.getPredecessor()), actualEdges.indexOf(actual.getPredecessor()));
		assertEquals("Vertex has wrong number of branches", expected.getBranches().size(), actual.getBranches().size());
		assertPointsEqual(expected, actual);
	}

	private void assertEdgeEquals(final Edge expected, final Edge actual, final List<Vertex> expectedVertices,
			final List<Vertex> actualVertices) {
		assertEquals(expected.getColor(), actual.getColor(), 1e-12);
		assertEquals(expected.getColor3rd(), actual.getColor3rd(), 1e-12);
		assertEquals(expected.getLength(), actual.getLength(), 1e-12);
		assertEquals(expected.getLength_ra(), actual.getLength_ra(), 1e-12);
		assertEquals(expected.getType(), actual.getType(), 1e-12);
		assertEquals(expectedVertices.indexOf(expected.getV1()), actualVertices.indexOf(actual.getV1()));
		assertEquals(expectedVertices.indexOf(expected.getV2()), actualVertices.indexOf(actual.getV2()));
	}

	private void assertPointsEqual(final Vertex vertex, final Vertex clonedVertex) {
		final ArrayList<Point> points = vertex.getPoints();
		final ArrayList<Point> clonedPoints = clonedVertex.getPoints();
		assertEquals("Cloned vertex has wrong number of points", points.size(), clonedPoints.size());
		IntStream.range(0, points.size()).forEach(j -> {
			final Point point = points.get(j);
			final Point clonedPoint = clonedPoints.get(j);
			assertEquals("Cloned vertex has a point with bad coordinates", point.toString(), clonedPoint.toString());
		});
	}

	@Test
	public void testNoClusterReturnsEmptyList() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(2).collect(Collectors.toList());
		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(1, 0, 0));
		final Edge edge = new Edge(vertices.get(0), vertices.get(1), null, 1.0);
		final Graph lineGraph = GraphUtil.createGraph(edge, vertices);

		final List<Set<Vertex>> clusters = findClusters(lineGraph, 0.5);

		assertTrue(clusters.isEmpty());
	}

	@Test
	public void testLonelyVertexReturnsLonelyVertex() {
		final Vertex vertex = new Vertex();
		final Point randomNonZero = new Point(7, 1, 3);
		vertex.addPoint(randomNonZero);
		final Graph oneVertexGraph = new Graph();
		oneVertexGraph.addVertex(vertex);

		final Graph cleanOneVertexGraph = cleanShortEdgesOp.calculate(oneVertexGraph, Double.MAX_VALUE);

		assertEquals(1, cleanOneVertexGraph.getVertices().size());
		assertEquals(1, cleanOneVertexGraph.getVertices().get(0).getPoints().size());
		assertEquals(randomNonZero, cleanOneVertexGraph.getVertices().get(0).getPoints().get(0));
	}

	@Test
	public void testLinearLength() {
		final Graph arch = TestGraphs.createArchWithSlabsGraph();

		final Graph cleanArch = (Graph) imageJ.op().run(CleanShortEdges.class, arch, 0);

		assertEquals(5, cleanArch.getEdges().get(0).getLength(), 1e-12);
	}

	@Test
	public void testAnisotropicCalibration() {
		final Graph sailGraph = TestGraphs.createSailGraph();

		final List<Double> calibration = Arrays.asList(2.0, 5.0, 3.0);
		final Graph cleanSailGraph = (Graph) imageJ.op().run(CleanShortEdges.class, sailGraph, 0, calibration);

		assertEquals(4, cleanSailGraph.getEdges().size());
		assertTrue(cleanSailGraph.getEdges().stream().anyMatch(e -> e.getLength() == 4.0));
		assertTrue(cleanSailGraph.getEdges().stream().anyMatch(e -> e.getLength() == 15.0));
		assertTrue(cleanSailGraph.getEdges().stream().anyMatch(e -> e.getLength() == Math.sqrt(241)));
		assertTrue(cleanSailGraph.getEdges().stream().anyMatch(e -> e.getLength() == 5.0));
	}

	@Test
	public void testRecursiveIndividualEdgePruning() {
		final Graph segmentGraph = TestGraphs.createFourStraightLineSegmentsGraph();
		final List<Double> dummyIsotropicCalibration = Arrays.asList(1.0, 1.0, 1.0);
		final Graph cleanSegmentGraph = (Graph) imageJ.op().run(CleanShortEdges.class, segmentGraph, 4.01,
				dummyIsotropicCalibration, false, false);

		assertEquals(3, cleanSegmentGraph.getVertices().size());
		assertEquals(2, cleanSegmentGraph.getEdges().size());
		assertTrue(cleanSegmentGraph.getEdges().stream().anyMatch(e -> e.getLength() == 17.0));
		assertTrue(cleanSegmentGraph.getEdges().stream().anyMatch(e -> e.getLength() == 15.0));
	}

	@Test
	public void testOneTimeIndividualEdgePruning() {
		final Graph segmentGraph = TestGraphs.createThreeStraightLineSegmentsGraph();
		final List<Double> dummyIsotropicCalibration = Arrays.asList(1.0, 1.0, 1.0);
		final Graph cleanSegmentGraph = (Graph) imageJ.op().run(CleanShortEdges.class, segmentGraph, 8.01,
				dummyIsotropicCalibration, false, false);

		assertEquals(3, cleanSegmentGraph.getVertices().size());
		assertEquals(2, cleanSegmentGraph.getEdges().size());
		assertTrue(cleanSegmentGraph.getEdges().stream().anyMatch(e -> e.getLength() == 16.0));
		assertTrue(cleanSegmentGraph.getEdges().stream().anyMatch(e -> e.getLength() == 16.0));
	}

	@Test
	public void testFindClusterInKite() {
		final Graph kiteGraph = TestGraphs.createKiteGraph();
		final ArrayList<Vertex> vertices = kiteGraph.getVertices();
		final ArrayList<Edge> edges = kiteGraph.getEdges();

		final List<Set<Vertex>> clusters = findClusters(kiteGraph, 1.01);

		assertNotNull(clusters);
		assertEquals(1, clusters.size());

		final Set<Vertex> cluster = clusters.get(0);
		assertEquals(3, cluster.size());
		assertTrue(vertices.stream().limit(3).allMatch(cluster::contains));

		final Set<Edge> clusterEdgeSet = new HashSet<>();
		cluster.stream().flatMap(v -> v.getBranches().stream()).forEach(clusterEdgeSet::add);
		assertEquals(4, clusterEdgeSet.size());
		assertTrue(edges.stream().limit(4).allMatch(clusterEdgeSet::contains));
	}

	@Test
	public void testClusterCentresHaveOnePoint() {
		final Graph graph = TestGraphs.createDumbbellGraph();
		final List<Set<Vertex>> clusters = findClusters(graph, 2.01);
		final CleanShortEdges cleanShortEdges = new CleanShortEdges();
		cleanShortEdges.setEnvironment(imageJ.op());
		cleanShortEdges.initialize();

		final List<Vertex> centres = clusters.stream().map(cleanShortEdges::getClusterCentre)
				.collect(Collectors.toList());

		assertTrue(centres.stream().allMatch(c -> c.getPoints().size() == 1));
	}

	@Test
	public void testFindClustersInDumbbell() {
		final Graph dumbbellGraph = TestGraphs.createDumbbellGraph();

		final List<Set<Vertex>> clusters = findClusters(dumbbellGraph, 2.01);

		assertNotNull(clusters);
		assertEquals(2, clusters.size());

		final Set<Vertex> firstCluster = clusters.get(0);
		assertEquals(3, firstCluster.size());
		final Set<Vertex> secondCluster = clusters.get(1);
		assertEquals(3, secondCluster.size());

		final ArrayList<Vertex> vertices = dumbbellGraph.getVertices();
		assertTrue(vertices.stream().filter(v -> !firstCluster.contains(v)).allMatch(secondCluster::contains));
		assertTrue(vertices.stream().filter(v -> !secondCluster.contains(v)).allMatch(firstCluster::contains));

		final List<Edge> firstClusterEdges = firstCluster.stream().flatMap(v -> v.getBranches().stream())
				.filter(e -> isShortEdge(e, 2.01)).collect(Collectors.toList());
		final List<Edge> secondClusterEdges = secondCluster.stream().flatMap(v -> v.getBranches().stream())
				.filter(e -> isShortEdge(e, 2.01)).collect(Collectors.toList());
		final List<Edge> allShortEdges = dumbbellGraph.getEdges().subList(0, 6);
		assertTrue(allShortEdges.stream().filter(e -> !firstClusterEdges.contains(e))
				.allMatch(secondClusterEdges::contains));
		final Edge longEdge = dumbbellGraph.getEdges().get(6);
		assertEquals(1, firstCluster.stream().flatMap(v -> v.getBranches().stream()).filter(longEdge::equals).count());
		assertTrue(allShortEdges.stream().filter(e -> !secondClusterEdges.contains(e))
				.allMatch(firstClusterEdges::contains));
		assertEquals(1, secondCluster.stream().flatMap(v -> v.getBranches().stream()).filter(longEdge::equals).count());
	}

	@Test
	public void testLoopsAreRemoved() {
		final Graph loopGraph = TestGraphs.createLoopGraph();

		final Graph cleanLoopGraph = cleanShortEdgesOp.calculate(loopGraph, 0.0);

		assertEquals(3, cleanLoopGraph.getEdges().size());
		assertTrue(cleanLoopGraph.getEdges().stream().noneMatch(GraphUtil::isLoop));
	}

	@Test
	public void testPruning() {
		final Graph sailGraph = TestGraphs.createSailGraph();

		final Graph cleanSailGraph = cleanShortEdgesOp.calculate(sailGraph, 1.01);

		assertEquals(3, cleanSailGraph.getEdges().size());
		assertEquals(3, cleanSailGraph.getVertices().size());
		assertEquals(1, cleanSailGraph.getVertices().stream().flatMap(v -> v.getPoints().stream())
				.filter(p -> p.x == 0 && p.y == 0 && p.z == 0).count());
		assertEquals(1, cleanSailGraph.getVertices().stream().flatMap(v -> v.getPoints().stream())
				.filter(p -> p.x == 0 && p.y == 3 && p.z == 0).count());
		assertEquals(1, cleanSailGraph.getVertices().stream().flatMap(v -> v.getPoints().stream())
				.filter(p -> p.x == 2 && p.y == 0 && p.z == 0).count());
	}

	@Test(expected = NullPointerException.class)
	public void testNullCluster() {
		final CleanShortEdges cleanShortEdges = new CleanShortEdges();
		cleanShortEdges.setEnvironment(imageJ.op());
		cleanShortEdges.initialize();
		cleanShortEdges.getClusterCentre(null);
	}

	@Test
	public void testGetCentreOfEmptyCluster() {
		final CleanShortEdges cleanShortEdges = new CleanShortEdges();
		cleanShortEdges.setEnvironment(imageJ.op());
		cleanShortEdges.initialize();

		final ArrayList<Point> points = cleanShortEdges.getClusterCentre(Collections.emptySet()).getPoints();

		assertEquals(1, points.size());
		assertEquals(Integer.MAX_VALUE, points.get(0).x);
		assertEquals(Integer.MAX_VALUE, points.get(0).y);
		assertEquals(Integer.MAX_VALUE, points.get(0).z);
	}

	@Test
	public void testGetCentreOfPointyTriangle() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(3).collect(Collectors.toList());
		vertices.get(0).addPoint(new Point(2, 0, 0));
		vertices.get(1).addPoint(new Point(4, 0, 0));
		vertices.get(2).addPoint(new Point(3, 6, 0));
		final Set<Vertex> cluster = new HashSet<>(vertices);
		final CleanShortEdges cleanShortEdges = new CleanShortEdges();
		cleanShortEdges.setEnvironment(imageJ.op());
		cleanShortEdges.initialize();

		final Vertex clusterCentre = cleanShortEdges.getClusterCentre(cluster);

		assertTrue(clusterCentre.isVertexPoint(new Point(3, 2, 0)));
	}

	@Test
	public void testFindInnerEdgesOfSquareCluster() {
		final Graph squareWithDiagAndLooseEnds = TestGraphs.createTriangleWithSquareCluster();
		final CleanShortEdges cleanShortEdges = new CleanShortEdges();
		cleanShortEdges.setEnvironment(imageJ.op());
		cleanShortEdges.initialize();

		final List<Set<Vertex>> clusters = findClusters(squareWithDiagAndLooseEnds, 2.01);
		assertEquals(1, clusters.size());
		assertEquals(4, clusters.get(0).size());

		final Set<Edge> allClusterEdges = new HashSet<>();
		clusters.get(0).stream().flatMap(v -> v.getBranches().stream()).forEach(allClusterEdges::add);
		assertEquals(7, allClusterEdges.size());
	}

	@Test
	public void testFindOuterEdgesOfSquareCluster() {
		final Graph squareWithDiagAndLooseEnds = TestGraphs.createTriangleWithSquareCluster();

		final List<Set<Vertex>> clusters = findClusters(squareWithDiagAndLooseEnds, 2.01);
		assertEquals(1, clusters.size());
		assertEquals(4, clusters.get(0).size());

		final Set<Edge> allClusterEdges = new HashSet<>();
		clusters.get(0).stream().flatMap(v -> v.getBranches().stream()).forEach(allClusterEdges::add);
		assertEquals(7, allClusterEdges.size());

		final Set<Edge> outerEdges = CleanShortEdges.findEdgesWithOneEndInCluster(clusters.get(0));
		assertEquals(2, outerEdges.size());
	}

	@Test
	public void testCleaningOfEmptyGraph() {
		final Graph emptyGraph = new Graph();

		final Graph cleanEmptyGraph = cleanShortEdgesOp.calculate(emptyGraph, 2.01);

		assertNotNull(cleanEmptyGraph);
		assertTrue(cleanEmptyGraph.getVertices().isEmpty());
		assertTrue(cleanEmptyGraph.getEdges().isEmpty());
	}

	@Test
	public void testIterativePruning() {
		final Graph doorknob = TestGraphs.createDoorknobGraph();

		final Graph cleanedOnce = (Graph) imageJ.op().run(CleanShortEdges.class, doorknob, 2.01, "iterativePruning", false);
		final Graph cleanedTwice = (Graph) imageJ.op().run(CleanShortEdges.class, doorknob, 2.01, "iterativePruning", true);

		assertEquals(4, cleanedOnce.getVertices().size());
		assertEquals(3, cleanedOnce.getEdges().size());

		assertEquals(3, cleanedTwice.getVertices().size());
		assertEquals(2, cleanedTwice.getEdges().size());

	}

	@Test
	public void testCleaningOfTriangleWithSquareCluster() {
		final Graph squareWithDiagAndLooseEnds = TestGraphs.createTriangleWithSquareCluster();
		final Graph cleaned = cleanShortEdgesOp.calculate(squareWithDiagAndLooseEnds, 2.01);

		assertNotNull(cleaned);
		assertEquals(4, cleaned.getVertices().size());
		assertEquals(4, cleaned.getEdges().size());

		final List<Point> expectedPoints = Arrays.asList(new Point(0, 0, 0), new Point(5, 4, 0), new Point(-4, -5, 0),
				new Point(5, -5, 0));

		expectedPoints.forEach(p -> {
			assertEquals("There should be exactly one vertex with the given point", 1,
					cleaned.getVertices().stream().filter(v -> hasPointNTimes(v, 1, p)).count());
			assertEquals("There should be exactly 2 edges with the given point", 2,
					cleaned.getEdges().stream().filter(e -> hasPoint(e, p)).count());
		});

		final double expectedLength1 = Math.sqrt(41.0);
		final double expectedLength2 = 9.0;

		assertEquals(2, cleaned.getEdges().stream().filter(e -> e.getLength() == expectedLength1).count());
		assertEquals(2, cleaned.getEdges().stream().filter(e -> e.getLength() == expectedLength2).count());
	}

	@Test
	public void testReportingOfPercentages() {
		// SETUP
		final Graph graphWithArtefacts = TestGraphs.createTriangleWithSquareClusterAndArtefacts();
		final CleanShortEdges cleanShortEdges = new CleanShortEdges();
		cleanShortEdges.setEnvironment(imageJ.op());
		cleanShortEdges.initialize();

		// EXECUTE
		cleanShortEdges.calculate(graphWithArtefacts, 2.01);
		final CleanShortEdges.PercentagesOfCulledEdges percentages = cleanShortEdges.getPercentages();

		// VERIFY
		assertEquals(graphWithArtefacts.getEdges().size(), percentages.getTotalEdges());
		assertEquals(1.0 / 15.0 * 100, percentages.getPercentageDeadEnds(), 1e-12);
		assertEquals(2.0 / 15.0 * 100, percentages.getPercentageParallelEdges(), 1e-12);
		assertEquals(3.0 / 15.0 * 100, percentages.getPercentageLoopEdges(), 1e-12);
		assertEquals(5.0 / 15.0 * 100, percentages.getPercentageClusterEdges(), 1e-12);

	}

	@Test
	public void testRemoveParallelEdges() {
		// SETUP
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(2).collect(Collectors.toList());
		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, 0),
				new Edge(vertices.get(1), vertices.get(0), null, 0),
				new Edge(vertices.get(0), vertices.get(1), null, 0));
		final Graph graph = GraphUtil.createGraph(edges, vertices);

		// EXECUTE
		CleanShortEdges.removeParallelEdges(graph);

		// VERIFY
		assertEquals(1, graph.getEdges().size());
		assertTrue(graph.getVertices().stream().map(v -> v.getBranches().size()).allMatch(b -> b == 1));
	}

	private static boolean hasPointNTimes(final Vertex vertex, final long n, final Point point) {
		return vertex.getPoints().stream().filter(p -> p.equals(point)).count() == n;
	}

	private static boolean hasPoint(final Edge edge, final Point point) {
		return edge.getV1().isVertexPoint(point) || edge.getV2().isVertexPoint(point);
	}
}
