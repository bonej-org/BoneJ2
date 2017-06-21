
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
public class GraphUtil {

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

	private static int doubleToIntCoordinate(double d) {
		return !Double.isNaN(d) ? (int) Math.round(d) : Integer.MAX_VALUE;
	}

	/**
	 * Returns true if the edge connects a vertex to itself, that is its endpoints
	 * are the same
	 */
	public static boolean isLoop(final Edge edge) {
		return !(edge.getV1() == null && edge.getV2() == null) && edge
			.getV1() == edge.getV2();
	}

	/** @see #createGraph(Collection, Collection) */
	public static Graph createGraph(final Edge edge,
		final Collection<Vertex> vertices)
	{
		return createGraph(Collections.singletonList(edge), vertices);
	}

	/**
     * Creates a {@link Graph}, and adds the given edges and vertices to it
     *
     * @implNote Adds edges as branches of their end points
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
