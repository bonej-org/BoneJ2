
package org.bonej.ops.ita;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

import org.bonej.utilities.GraphUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Vertex;

public class VertexValenceSorterTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static BinaryFunctionOp<Graph, Pair<Integer, Integer>, Map<Integer, List<Vertex>>> valenceSorterOp;

	/**
	 * Test that conforms fails when the valence range has arguments in wrong
	 * order
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testInvalidRange() {
		final Graph emptyGraph = new Graph();
		final Pair<Integer, Integer> range = new ValuePair<>(5, 1);

		IMAGE_J.op().op(VertexValenceSorter.class, emptyGraph, range);
	}

	@Test
	public void testMissingValenceExitsCorrectly() {
		final Graph stickFigureGraph = createStickFigureGraph();

		final Pair<Integer, Integer> range = new ValuePair<>(3, 5);

		final Map<Integer, List<Vertex>> map = valenceSorterOp.calculate(
			stickFigureGraph, range);
		final Set<Integer> keySet = map.keySet();

		assertEquals(2, keySet.size());
		assertTrue(keySet.contains(3));
		assertTrue(keySet.contains(5));
	}

	/**
	 * Test that conforms fails when the valence range has negative arguments
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testNegativeRange() {
		final Graph emptyGraph = new Graph();
		final Pair<Integer, Integer> range = new ValuePair<>(-5, -1);

		IMAGE_J.op().op(VertexValenceSorter.class, emptyGraph, range);
	}

	/** Test that conforms fails when the valence range has null arguments */
	@Test(expected = IllegalArgumentException.class)
	public void testNullRange() {
		final Graph emptyGraph = new Graph();
		final Pair<Integer, Integer> range = new ValuePair<>(null, null);

		IMAGE_J.op().op(VertexValenceSorter.class, emptyGraph, range);
	}

	@Test
	public void testRangeOnFishGraph() {
		final Graph fishGraph = createDownWardFacingFishGraph();

		final Pair<Integer, Integer> range = new ValuePair<>(2, 3);

		final Map<Integer, List<Vertex>> map = valenceSorterOp.calculate(fishGraph,
			range);
		final Set<Integer> keySet = map.keySet();
		final int expectedCount = 2;
		final int expectedValence = 2;

		assertEquals("Wrong number of valences", 1, keySet.size());
		assertTrue("Expected valence missing", map.containsKey(expectedValence));
		final List<Vertex> vertices = map.get(expectedValence);
		assertEquals("Wrong number of vertices with valence " + expectedValence,
			expectedCount, vertices.size());
		assertTrue("A vertex has wrong number of branches (valence)", vertices
			.stream().allMatch(v -> expectedValence == v.getBranches().size()));

	}

	@Test
	public void testValenceSorterOnFishGraph() {
		final Graph fishGraph = createDownWardFacingFishGraph();

		final Pair<Integer, Integer> range = new ValuePair<>(0, Integer.MAX_VALUE);

		final Map<Integer, List<Vertex>> map = valenceSorterOp.calculate(fishGraph,
			range);
		final Set<Integer> keySet = map.keySet();
		final int[] expectedCounts = { 2, 2, 1 };
		final int[] expectedValences = { 1, 2, 4 };

		assertEquals("Wrong number of valences", 3, keySet.size());

		for (int i = 0; i < expectedCounts.length; i++) {
			assertTrue("Expected valence missing", map.containsKey(
				expectedValences[i]));
			final List<Vertex> vertices = map.get(expectedValences[i]);
			final int valence = expectedValences[i];
			assertEquals("Wrong number of vertices with valence " + valence,
				expectedCounts[i], vertices.size());
			assertTrue("A vertex has wrong number of branches (valence)", vertices
				.stream().allMatch(v -> valence == v.getBranches().size()));
		}
	}

	@Test
	public void testValenceSorterWithEmptyGraph() {
		final Graph emptyGraph = new Graph();
		final Pair<Integer, Integer> range = new ValuePair<>(3, 5);
		final Map<Integer, List<Vertex>> map = valenceSorterOp.calculate(emptyGraph,
			range);
		assertNotNull(map);
		assertTrue("Empty graph should return an empty map.", map.isEmpty());
	}

	/**
	 * Structure of the downward facing fish graph example
	 *
	 * <pre>
	 * 1   5
	 *  \ /
	 *   2
	 *  / \
	 * 4---3
	 * </pre>
	 */
	private static Graph createDownWardFacingFishGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(5).collect(
			Collectors.toList());
		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices
			.get(1), null, 0.0), new Edge(vertices.get(1), vertices.get(2), null,
				0.0), new Edge(vertices.get(2), vertices.get(3), null, 0.0), new Edge(
					vertices.get(1), vertices.get(3), null, 0.0), new Edge(vertices.get(
						1), vertices.get(4), null, 0.0));
		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Structure of the stick figure graph example
	 *
	 * <pre>
	 * 0     1
	 *  \   /
	 *   \ /
	 * 6--2--7
	 *    |
	 *    |
	 *    |
	 *    |
	 *    3
	 *   / \
	 *  /   \
	 * 4     5
	 * </pre>
	 */
	private static Graph createStickFigureGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(8).collect(
			Collectors.toList());

		vertices.get(0).addPoint(new Point(-2, 4, 0));
		vertices.get(1).addPoint(new Point(2, 4, 0));
		vertices.get(2).addPoint(new Point(0, 3, 0));
		vertices.get(3).addPoint(new Point(0, -3, 0));
		vertices.get(4).addPoint(new Point(-2, -4, 0));
		vertices.get(5).addPoint(new Point(-2, -4, 0));
		vertices.get(5).addPoint(new Point(2, 3, 0));
		vertices.get(5).addPoint(new Point(2, 3, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices
			.get(2), null, Math.sqrt(5.0)), new Edge(vertices.get(1), vertices.get(2),
				null, Math.sqrt(5.0)), new Edge(vertices.get(2), vertices.get(3), null,
					6.0), new Edge(vertices.get(3), vertices.get(4), null, Math.sqrt(
						5.0)), new Edge(vertices.get(3), vertices.get(5), null, Math.sqrt(
							5.0)), new Edge(vertices.get(6), vertices.get(2), null, 2.0),
			new Edge(vertices.get(7), vertices.get(2), null, 2.0));

		return GraphUtil.createGraph(edges, vertices);
	}

	@SuppressWarnings("unchecked")
	@BeforeClass
	public static void setUpBeforeClass() {
		valenceSorterOp = (BinaryFunctionOp) Functions.binary(IMAGE_J.op(),
			VertexValenceSorter.class, Map.class, Graph.class, new ValuePair(0, 0));
	}

	@AfterClass
	public static void tearDownAfterClass() {
		IMAGE_J.context().dispose();
	}
}
