package org.bonej.ops;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;

import org.bonej.utilities.GraphUtil;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Vector3d;

import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Vertex;

@Plugin(name = "cleanShortEdges", type = Op.class)
public class CleanShortEdges extends AbstractBinaryFunctionOp<Graph, Double, Graph> {

	@Parameter(persist = false, required = false)
	private List<Double> calibration3d = Arrays.asList(1.0, 1.0, 1.0);

	@Parameter(persist = false, required = false)
	private boolean iterativePruning = false;

	@Parameter(persist = false, required = false)
	private boolean useClusters = true;

	private UnaryFunctionOp<List, Vector3d> centroidOp;

	private PercentagesOfCulledEdges percentages;

	/** Pre-match op to efficiently find centroids of vertices */
	@Override
	public void initialize() {
		centroidOp = Functions.unary(ops(), CentroidLinAlg3d.class, Vector3d.class, List.class);
		percentages = new PercentagesOfCulledEdges();
	}

	/**
	 * Cleans short edges from an {@link AnalyzeSkeleton_} {@link Graph}
	 * Distinguish between "dead end" short edges (defined by having exactly one
	 * {@link Vertex} with just one branch) and "cluster" short edges (defined
	 * by both ends having at least two branches)
	 *
	 * @param input
	 *            the graph to clean
	 * @param tolerance
	 *            edges shorter than tolerance will be cleaned
	 * @return a cleaned graph
	 */
	@Override
	public Graph calculate(final Graph input, final Double tolerance) {
		if (calibration3d == null || calibration3d.size() < 3) {
			calibration3d = Arrays.asList(1.0, 1.0, 1.0);
		}

		Graph graph = cloneGraph(input);
		percentages.totalEdges = graph.getEdges().size();
		percentages.setLoopPercentage(removeLoops(graph));
		percentages.setParallelPercentage(removeParallelEdges(graph));
		graph.getEdges().forEach(this::euclideanDistance);
		int deadEnds = 0;
		int clusterEdges = 0;
		while (true) {
			final int startSize = graph.getVertices().size();
			deadEnds = deadEnds + pruneDeadEnds(graph, tolerance);
			final int edges = graph.getEdges().size();
			final Graph cleanGraph;
			if (useClusters) {
				cleanGraph = cleaningStep(graph, tolerance);
			} else {
				cleanGraph = cleaningStepUsingEdges(graph, tolerance);
			}
			removeParallelEdges(cleanGraph);
			clusterEdges = clusterEdges + (edges - cleanGraph.getEdges().size());
			final int cleanSize = cleanGraph.getVertices().size();
			graph = cleanGraph;
			if (!iterativePruning || startSize == cleanSize) {
				break;
			}
		}
		percentages.setDeadEndPercentage(deadEnds);
		percentages.setClusterPercentage(clusterEdges);
		return graph;
	}

	public PercentagesOfCulledEdges getPercentages() {
		return percentages;
	}

	// TODO Remove when new version of AnalyzeSkeleton_ released
	public static Graph cloneGraph(final Graph graph) {
		final Map<Vertex, Vertex> vertexMap = graph.getVertices().stream()
				.collect(toMap(Function.identity(), CleanShortEdges::cloneVertex));
		final Map<Edge, Edge> edgeMap = graph.getEdges().stream()
				.collect(toMap(Function.identity(), e -> cloneEdge(e, vertexMap)));
		graph.getVertices().forEach(v -> vertexMap.get(v).setPredecessor(edgeMap.get(v.getPredecessor())));
		final Graph clonedGraph = new Graph();
		// Iterate in the order of the originals to preserve order in the cloned
		// graph
		graph.getEdges().stream().map(edgeMap::get).forEach(clonedGraph::addEdge);
		graph.getVertices().stream().map(vertexMap::get).forEach(clonedGraph::addVertex);
		return clonedGraph;
	}

	private static Vertex cloneVertex(final Vertex vertex) {
		final List<Point> clonedPoints = vertex.getPoints().stream().map(CleanShortEdges::clonePoint).collect(toList());
		final Vertex clonedVertex = new Vertex();
		clonedVertex.setVisited(vertex.isVisited(), vertex.getVisitOrder());
		clonedVertex.getPoints().addAll(clonedPoints);
		return clonedVertex;
	}

	private static Edge cloneEdge(final Edge edge, final Map<Vertex, Vertex> vertexMap) {
		final Vertex v1 = vertexMap.get(edge.getV1());
		final Vertex v2 = vertexMap.get(edge.getV2());
		final ArrayList<Point> oldSlabs = edge.getSlabs();
		final ArrayList<Point> slabs;
		if (oldSlabs != null) {
			slabs = oldSlabs.stream().map(CleanShortEdges::clonePoint).collect(Collectors.toCollection(ArrayList::new));
		} else {
			slabs = null;
		}
		final Edge clonedEdge = new Edge(v1, v2, slabs, edge.getLength());
		clonedEdge.setColor(edge.getColor());
		clonedEdge.setColor3rd(edge.getColor3rd());
		clonedEdge.setType(edge.getType());
		return clonedEdge;
	}

	private static Point clonePoint(final Point point) {
		return new Point(point.x, point.y, point.z);
	}

	/**
	 * Removes parallel edges from the graph, leaving at most one connection
	 * between each vertex pair
	 * <p>
	 * An edge is parallel, if there's another edge between its endpoint vertices.
	 * </p>
	 * <p>
	 * NB non-deterministic in choosing which of the parallel edges is kept.
	 * </p>
	 *
	 * @param graph A {@link Graph} that's assumed undirected
	 * @return Number of parallel edges removed
	 */
	public static int removeParallelEdges(final Graph graph) {
		final ArrayList<Vertex> vertices = graph.getVertices();
		final Map<Vertex, Integer> idMap = mapVertexIds(vertices);
		final Set<Long> connections = new HashSet<>();
		final List<Edge> parallelEdges = new ArrayList<>();
		graph.getEdges().forEach(edge -> {
			final long hash = connectionHash(edge, idMap);
			if (!connections.add(hash)) {
				parallelEdges.add(edge);
			}
		});
		parallelEdges.forEach(CleanShortEdges::removeBranchFromEndpoints);
		graph.getEdges().removeAll(parallelEdges);
		return parallelEdges.size();
	}

	private static Map<Vertex, Integer> mapVertexIds(final List<Vertex> vertices) {
		return IntStream.range(0, vertices.size()).boxed()
				.collect(Collectors.toMap(vertices::get, Function.identity()));
	}

	private static void removeBranchFromEndpoints(final Edge branch) {
		branch.getV1().getBranches().remove(branch);
		branch.getV2().getBranches().remove(branch);
	}

	private static long connectionHash(final Edge e, final Map<Vertex, Integer> idMap) {
		final int nVertices = idMap.size();
		final int a = idMap.get(e.getV1());
		final int b = idMap.get(e.getV2());
		return a < b ? a * nVertices + b : b * nVertices + a;
	}

	private static int removeLoops(final Graph graph) {
		final List<Edge> loops = graph.getEdges().stream().filter(GraphUtil::isLoop).collect(toList());
		loops.forEach(CleanShortEdges::removeBranchFromEndpoints);
		graph.getEdges().removeAll(loops);
		return loops.size();
	}

	/**
	 * Prunes clusters and changes the affected edges. Preserves topology not
	 * directly connected to the clusters.
	 * <p>
	 * Clusters are pruned to single centroid vertices. The centroids connect
	 * all the edges that lead outside their clusters.
	 * </p>
	 * 
	 * @see #findClusters(Graph, Double)
	 * @see #mapReplacementEdges(Map, Set, Vertex)
	 * @see #findEdgesWithOneEndInCluster(Set)
	 * @see #getClusterCentre(Set)
	 */
	private Graph cleaningStep(final Graph graph, final Double tolerance) {
		final List<Set<Vertex>> clusters = findClusters(graph, tolerance);
		final Map<Set<Vertex>, Vertex> clusterCentres = clusters.stream()
				.collect(Collectors.toMap(Function.identity(), this::getClusterCentre));

		final Map<Edge, Edge> replacementMap = new HashMap<>();
		clusterCentres.forEach((cluster, centre) -> mapReplacementEdges(replacementMap, cluster, centre));
		final Collection<Edge> clusterConnectingEdges = replacementMap.values();
		clusterConnectingEdges.forEach(this::euclideanDistance);

		final List<Edge> nonClusterEdges = graph.getEdges().stream()
				.filter(e -> !replacementMap.containsKey(e) && !isClusterEdge(e, clusters)).collect(toList());

		final Graph cleanGraph = new Graph();
		final Set<Edge> cleanEdges = new HashSet<>();
		cleanEdges.addAll(nonClusterEdges);
		cleanEdges.addAll(clusterConnectingEdges);
		cleanGraph.getEdges().addAll(cleanEdges);
		clusterCentres.values().forEach(cleanGraph::addVertex);
		endpointStream(nonClusterEdges).forEach(cleanGraph::addVertex);
		endpointStream(clusterConnectingEdges).forEach(cleanGraph::addVertex);

		copyLonelyVertices(graph, cleanGraph);
		removeDanglingEdges(cleanGraph);
		return cleanGraph;
	}

	private Graph cleaningStepUsingEdges(final Graph graph, final Double tolerance) {
		Graph currentGraph = cloneGraph(graph);
		final List<Edge> shortInnerEdges = currentGraph.getEdges().stream()
				.filter(e -> isShortEdge(e, tolerance) && !isDeadEndEdge(e)).collect(toList());

		for (int i = 0; i < shortInnerEdges.size(); i++) {
			final Edge currentShortEdge = shortInnerEdges.get(i);
			final List<Set<Vertex>> clusters = new ArrayList<>();
			final Set<Vertex> currentCluster = new HashSet<>();
			currentCluster.add(currentShortEdge.getV1());
			currentCluster.add(currentShortEdge.getV2());
			clusters.add(currentCluster);

			final Map<Set<Vertex>, Vertex> clusterCentres = clusters.stream()
					.collect(Collectors.toMap(Function.identity(), this::getClusterCentre));

			final Map<Edge, Edge> replacementMap = new HashMap<>();
			clusterCentres.forEach((cluster, centre) -> mapReplacementEdges(replacementMap, cluster, centre));
			final Collection<Edge> clusterConnectingEdges = replacementMap.values();
			clusterConnectingEdges.forEach(this::euclideanDistance);

			final List<Edge> nonClusterEdges = currentGraph.getEdges().stream()
					.filter(e -> !replacementMap.containsKey(e) && !isClusterEdge(e, clusters)).collect(toList());

			final Graph cleanGraph = new Graph();
			final Set<Edge> cleanEdges = new HashSet<>();
			cleanEdges.addAll(nonClusterEdges);
			cleanEdges.addAll(clusterConnectingEdges);
			cleanGraph.getEdges().addAll(cleanEdges);
			clusterCentres.values().forEach(cleanGraph::addVertex);
			endpointStream(nonClusterEdges).forEach(cleanGraph::addVertex);
			endpointStream(clusterConnectingEdges).forEach(cleanGraph::addVertex);

			copyLonelyVertices(currentGraph, cleanGraph);
			removeDanglingEdges(cleanGraph);

			shortInnerEdges.forEach(e -> {
				if (replacementMap.containsKey(e)) {
					final int index = shortInnerEdges.indexOf(e);
					shortInnerEdges.set(index, replacementMap.get(e));
				}
			});
			currentGraph = cleanGraph;
		}
		return currentGraph;
	}

	private static Stream<Vertex> endpointStream(final Collection<Edge> edges) {
		return edges.stream().flatMap(e -> Stream.of(e.getV1(), e.getV2())).distinct();
	}

	/** Removes edges that connect to vertices no longer in the given graph */
	private static void removeDanglingEdges(final Graph graph) {
		graph.getVertices().forEach(v -> v.getBranches().removeIf(e -> !graph.getEdges().contains(e)));
	}

	/**
	 * Maps new replacement edges for the cluster centroid
	 * <p>
	 * When a cluster is pruned, that is, condensed to a single centroid vertex,
	 * all the edges that the cluster vertices had need to be connected to the
	 * new centroid. Instead of altering the edges, we create new ones to
	 * replace them, because {@link Edge} objects are immutable.
	 * <p>
	 * </p>
	 * The method creates a map from old edges to their new replacements. Note
	 * that both endpoints of an edge in the mapping can change, when it's an
	 * edge connecting two clusters to each other.
	 * </p>
	 */

	private static void mapReplacementEdges(final Map<Edge, Edge> replacementMapping, final Set<Vertex> cluster,
			final Vertex centroid) {
		final Set<Edge> outerEdges = findEdgesWithOneEndInCluster(cluster);
		outerEdges.forEach(outerEdge -> {
			final Edge oldEdge = replacementMapping.getOrDefault(outerEdge, outerEdge);
			final Edge replacement = replaceEdge(oldEdge, cluster, centroid);
			replacementMapping.put(outerEdge, replacement);
		});
	}

	private static Edge replaceEdge(final Edge edge, final Set<Vertex> cluster, final Vertex centre) {
		final Vertex v1 = edge.getV1();
		final Vertex v2 = edge.getV2();
		final Edge replacement;
		if (cluster.contains(v1)) {
			replacement = new Edge(centre, v2, null, 0.0);
			replacement.getV1().setBranch(replacement);
			replacement.getV2().setBranch(replacement);
		} else if (cluster.contains(v2)) {
			replacement = new Edge(v1, centre, null, 0.0);
			replacement.getV1().setBranch(replacement);
			replacement.getV2().setBranch(replacement);
		} else {
			replacement = null;
		}
		return replacement;
	}

	/**
	 * Sets the length of the {@link Edge} to the calibrated euclidean distance
	 * between its endpoints
	 */
	private void euclideanDistance(final Edge e) {
		final Vector3d centre1 = centroidOp.calculate(GraphUtil.toVector3d(e.getV1().getPoints()));
		final Vector3d centre2 = centroidOp.calculate(GraphUtil.toVector3d(e.getV2().getPoints()));
		centre1.sub(centre2);
		final double length = calibratedLength(centre1);
		e.setLength(length);
	}

	private double calibratedLength(final Vector3d centre1) {
		return Math.sqrt(Math.pow(centre1.x * calibration3d.get(0), 2) + Math.pow(centre1.y * calibration3d.get(1), 2)
				+ Math.pow(centre1.z * calibration3d.get(2), 2));
	}

	private static boolean isClusterEdge(final Edge e, final List<Set<Vertex>> clusters) {
		return clusters.stream().anyMatch(c -> c.contains(e.getV1()) && c.contains(e.getV2()));
	}

	/**
	 * Creates a centroid vertex of all the vertices in a cluster.
	 *
	 * @param cluster a collection of directly connected vertices.
	 * @return A vertex at the geometric center of the cluster.
	 */
	public Vertex getClusterCentre(final Set<Vertex> cluster) {
		final List<Vector3d> clusterVectors = getClusterVectors(cluster);
		final Vector3d clusterCentroid = centroidOp.calculate(clusterVectors);
		return GraphUtil.vectorToVertex(clusterCentroid);
	}

	private static List<Vector3d> getClusterVectors(final Set<Vertex> cluster) {
		final Stream<Point> clusterPoints = cluster.stream().flatMap(v -> v.getPoints().stream());
		return clusterPoints.map(GraphUtil::toVector3d).collect(Collectors.toList());
	}

	private static int pruneDeadEnds(final Graph graph, final Double tolerance) {
		final List<Edge> deadEnds = graph.getEdges().stream().filter(e -> isDeadEndEdge(e) && isShortEdge(e, tolerance))
				.collect(toList());
		final List<Vertex> terminals = deadEnds.stream().flatMap(e -> Stream.of(e.getV1(), e.getV2()))
				.filter(v -> v.getBranches().size() == 1).collect(toList());
		graph.getVertices().removeAll(terminals);
		deadEnds.forEach(CleanShortEdges::removeBranchFromEndpoints);
		graph.getEdges().removeAll(deadEnds);
		return deadEnds.size();
	}

	public static boolean isShortEdge(final Edge e, final Double tolerance) {
		return (e.getLength() < tolerance);
	}

	private static boolean isDeadEndEdge(final Edge e) {
		return Stream.of(e.getV1(), e.getV2()).filter(v -> v.getBranches().size() == 1).count() == 1;
	}

	/**
	 * Finds all the vertices in the cluster that has the given vertex
	 * <p>
	 * A vertex is in the cluster if its connected to the start directly or
	 * indirectly via edges that have length less than the given tolerance
	 * </p>
	 */
	private static Set<Vertex> fillCluster(final Vertex start, final Double tolerance) {
		final Set<Vertex> cluster = new HashSet<>();
		final Stack<Vertex> stack = new Stack<>();
		stack.push(start);
		while (!stack.isEmpty()) {
			final Vertex vertex = stack.pop();
			cluster.add(vertex);
			final Set<Vertex> freeNeighbours = vertex.getBranches().stream().filter(e -> isShortEdge(e, tolerance))
					.map(e -> e.getOppositeVertex(vertex)).filter(v -> !cluster.contains(v)).collect(toSet());
			stack.addAll(freeNeighbours);
		}
		return cluster;
	}

	/**
	 * Find all the vertex clusters in the graph
	 * <p>
	 * A cluster is a set of vertices connected to each other by edges whose
	 * length is less than tolerance
	 * </p>
	 *
	 * @param graph
	 *            An undirectioned graph
	 * @param tolerance
	 *            Maximum length for cluster edges
	 * @return A set of all the clusters found
	 */
	public static List<Set<Vertex>> findClusters(final Graph graph, final Double tolerance) {
		final List<Set<Vertex>> clusters = new ArrayList<>();
		final List<Vertex> clusterVertices = findClusterVertices(graph, tolerance);
		while (clusterVertices.size() > 0) {
			final Vertex start = clusterVertices.get(0);
			final Set<Vertex> cluster = fillCluster(start, tolerance);
			clusters.add(cluster);
			clusterVertices.removeAll(cluster);
		}
		return clusters;
	}

	/** Finds all the vertices that are in one of the graph's clusters */
	private static List<Vertex> findClusterVertices(final Graph graph, final Double tolerance) {
		return graph.getEdges().stream().filter(e -> isShortEdge(e, tolerance))
				.flatMap(e -> Stream.of(e.getV1(), e.getV2())).distinct().collect(Collectors.toList());
	}

	private static void copyLonelyVertices(final Graph originalGraph, final Graph targetGraph) {
		final Set<Vertex> lonelyVertices = originalGraph.getVertices().stream().filter(v -> v.getBranches().size() == 0)
				.collect(toSet());
		targetGraph.getVertices().addAll(lonelyVertices);
	}

	/**
	 * Finds the edges that connect the cluster vertices to outside the cluster.
	 *
	 * @param cluster a collection of directly connected vertices.
	 * @return the edges that originate from the cluster but terminate outside it.
	 */
	public static Set<Edge> findEdgesWithOneEndInCluster(final Set<Vertex> cluster) {
		final Map<Edge, Long> edgeCounts = cluster.stream().flatMap(v -> v.getBranches().stream())
				.collect(groupingBy(Function.identity(), Collectors.counting()));
		return edgeCounts.keySet().stream().filter(e -> edgeCounts.get(e) == 1).collect(toSet());
	}

	public static final class PercentagesOfCulledEdges {
		private double percentageDeadEnds = Double.NaN;
		private double percentageParallelEdges = Double.NaN;
		private double percentageLoopEdges = Double.NaN;
		private double percentageClusterEdges = Double.NaN;
		private long totalEdges = -1;

		public double getPercentageDeadEnds() {
			return percentageDeadEnds;
		}

		public double getPercentageParallelEdges() {
			return percentageParallelEdges;
		}

		public double getPercentageLoopEdges() {
			return percentageLoopEdges;
		}

		public double getPercentageClusterEdges() {
			return percentageClusterEdges;
		}

		public long getTotalEdges() {
			return totalEdges;
		}

		private void setClusterPercentage(final int clusterEdges) {
			percentageClusterEdges = clusterEdges / ((double) totalEdges) * 100.0;
		}

		private void setDeadEndPercentage(final int deadEndEdges) {
			percentageDeadEnds = deadEndEdges / ((double) totalEdges) * 100.0;
		}

		private void setParallelPercentage(final int parallelEdges) {
			percentageParallelEdges = parallelEdges / ((double) totalEdges) * 100.0;
		}

		private void setLoopPercentage(final int loopEdges) {
			percentageLoopEdges = loopEdges / ((double) totalEdges) * 100.0;
		}
	}
}
