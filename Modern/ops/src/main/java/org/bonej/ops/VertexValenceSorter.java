package org.bonej.ops;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.util.Pair;

import org.scijava.plugin.Plugin;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Vertex;

/**
 * This Op takes an input {@link Graph} and returns a {@link Map} where each key
 * contains a list of vertices that have valence key.
 *
 * <p>
 * A graph consists of a number of vertices, and some of them are possibly
 * connected to each other by edges. The graph is undirected, and the valence of
 * a vertex designates the number of edges that touch that vertex.
 * </p>
 *
 * @author Alessandro Felder
 * @author Richard Domander
 *
 *
 */
@Plugin(name = "vertexValenceSorter", type = Op.class)
public class VertexValenceSorter extends
		AbstractBinaryFunctionOp<Graph, Pair<Integer, Integer>, Map<Integer, List<Vertex>>> implements Contingent {

	/**
	 * This function iterates through all vertices in a {@link Graph} and
	 * returns a data structure (a map of lists of vertices} sorting the
	 * vertices that have valence within the specified valence range.
	 * 
	 * @param graph
	 *            the {@link Graph} from the AnalyzeSkeleton tool whose vertices
	 *            will be mapped by this Op.
	 * @param valenceRange
	 *            the range of valences that should be kept. The range includes
	 *            both values passed.
	 * @return a map of lists of vertices, the keys of the map specify the
	 *         valence of all the vertices in each list
	 */
	@Override
	public Map<Integer, List<Vertex>> calculate(final Graph graph, final Pair<Integer, Integer> valenceRange) {
		final Map<Integer, List<Vertex>> valenceMap = new HashMap<>();
		final ArrayList<Vertex> vertices = graph.getVertices();

		vertices.stream().filter(v -> isInValenceRange(v, valenceRange)).forEach(v -> {
			final ArrayList<Edge> branches = v.getBranches();
			final int valence = branches.size();
			valenceMap.computeIfAbsent(valence, k -> new ArrayList<>());
			valenceMap.get(valence).add(v);
		});

		return valenceMap;
	}

	private boolean isInValenceRange(final Vertex v, final Pair<Integer, Integer> range) {
		final int valence = v.getBranches().size();
		return valence >= range.getA() && valence <= range.getB();
	}

	@Override
	public boolean conforms() {
		final Pair<Integer, Integer> range = in2();
		final Integer a = range.getA();
		final Integer b = range.getB();
		return a != null && b != null && a <= b && a >= 0;
	}
}
