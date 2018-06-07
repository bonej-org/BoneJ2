
package org.bonej.utilities;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.scijava.vecmath.Vector3d;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Vertex;

/**
 * Utilities for the {@link Graph} class from
 * {@link sc.fiji.analyzeSkeleton.AnalyzeSkeleton_}
 *
 * @author Richard Domander
 */
public final class GraphUtil {

	private GraphUtil() {}

	public static List<Vector3d> toVector3d(final List<Point> points) {
		if (points.isEmpty()) {
			return Collections.singletonList(new Vector3d(Double.NaN, Double.NaN,
				Double.NaN));
		}

        return points.stream().map(GraphUtil::toVector3d).collect(toList());
	}

	public static Vector3d toVector3d(final Point point) {
		return new Vector3d(point.x, point.y, point.z);
	}

	public static Vertex vectorToVertex(final Vector3d centroid) {
        final Vertex vertex = new Vertex();
	    if (centroid == null) {
            vertex.addPoint(new Point(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        } else {
            final int x = doubleToIntCoordinate(centroid.x);
            final int y = doubleToIntCoordinate(centroid.y);
            final int z = doubleToIntCoordinate(centroid.z);
            vertex.addPoint(new Point(x, y, z));
        }
		return vertex;
	}

	private static int doubleToIntCoordinate(final double d) {
		return !Double.isNaN(d) ? (int) Math.round(d) : Integer.MAX_VALUE;
	}

	/**
	 * Checks if the edge forms a loop.
	 *
	 * @param edge an edge in a graph.
	 * @return true if both endpoints of the edge is the same vertex.
	 */
	public static boolean isLoop(final Edge edge) {
		return !(edge.getV1() == null && edge.getV2() == null) && edge
			.getV1() == edge.getV2();
	}

	/**
	 * Creates a {@link Graph} with a single edge.
	 * <p>
	 * NB Adds the edge as a branch of its end points. The connections between the
	 * vertices are defined in the {@link Edge} and {@link Vertex} classes.
	 * </p>
	 * 
	 * @see Edge#getV1()
	 * @see Edge#getV2()
	 * @see Vertex#getBranches()
	 * @param edge the edge of the graph.
	 * @param vertices vertices of the graph.
	 * @return A graph where the vertices are connected by the edges.
	 */
	public static Graph createGraph(final Edge edge,
		final Collection<Vertex> vertices)
	{
		return createGraph(Collections.singletonList(edge), vertices);
	}

	/**
	 * Creates a {@link Graph}, and adds the given edges and vertices to it.
	 * <p>
	 * NB Adds edges as branches of their end points. The connections between the
	 * vertices are defined in the {@link Edge} and {@link Vertex} classes.
	 * </p>
	 * 
	 * @see Edge#getV1()
	 * @see Edge#getV2()
	 * @see Vertex#getBranches()
	 * @param edges edges of the graph.
	 * @param vertices vertices of the graph.
	 * @return A graph that contains the vertices and the edge.
	 */
	public static Graph createGraph(final Collection<Edge> edges,
		final Collection<Vertex> vertices)
	{
		final Graph graph = new Graph();
		edges.forEach(graph::addEdge);
		vertices.forEach(graph::addVertex);
		return graph;
	}
}
