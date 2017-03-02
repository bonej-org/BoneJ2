
package org.bonej.ops;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;

import org.jetbrains.annotations.Contract;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Vertex;

/**
 * An Op which calculates the angles at each triple point in the given Graph
 * array. A triple point is a point where three edges in meet. The Graph array
 * is an output of AnalyzeSkeleton_ plugin. The second option of the Op controls
 * the point from which the angle is measured. Measuring the angle from the
 * opposite vertices of the triple point may be misleading if the edges are
 * highly curved. NB "edge" and "vertex" have a special meaning in this class,
 * they refer to AnalyzeSkeleton_.Edge and AnalyzeSkeleton_.Vertex
 *
 * @author Michael Doube
 * @author Richard Domander
 * @see sc.fiji.analyzeSkeleton.AnalyzeSkeleton_
 */
@Plugin(type = Op.class)
public class TriplePointAngles extends
	AbstractBinaryFunctionOp<Graph[], Integer, List<List<TriplePointAngles.TriplePoint>>>
	implements Contingent
{

	/**
	 * A special value for measurementPoint
	 * 
	 * @see #getMeasurementPoint(Vertex, Edge, int)
	 */
	public static final int VERTEX_TO_VERTEX = -1;
	private UnaryFunctionOp<List<Vector3d>, Tuple3d> centroidOp;

	@Override
	public boolean conforms() {
		return in2() >= VERTEX_TO_VERTEX;
	}

	@Override
	public void initialize() {
		centroidOp = (UnaryFunctionOp) Functions.unary(ops(),
			CentroidLinAlg3d.class, Tuple3d.class, List.class);
	}

	/**
	 * Calculates the angles at the triple points in the given graphs
	 *
	 * @param graphs An array of Graphs produced by the AnalyzeSkeleton_ plugin
	 * @param measurementPoint if >= 0, then measure angle from the nth slab of
	 *          the edge if == VERTEX_TO_VERTEX, then measure angle from the
	 *          opposite centroid
	 * @return A list of triple points for each graph
	 * @see #getMeasurementPoint(Vertex, Edge, int)
	 * @see #VERTEX_TO_VERTEX
	 * @see TriplePoint
	 */
	@Override
	public List<List<TriplePoint>> calculate(final Graph[] graphs,
		final Integer measurementPoint)
	{
		final List<List<TriplePoint>> skeletons = new ArrayList<>();
		int graphNumber = 1;

		for (Graph graph : graphs) {
			int triplePointNumber = 1;
			final List<Vertex> vertices = graph.getVertices().stream().filter(
				TriplePointAngles::isTriplePoint).collect(toList());
			final List<TriplePoint> triplePoints = new ArrayList<>(vertices.size());
			for (final Vertex vertex : vertices) {
				List<Double> angles = triplePointAngles(vertex, measurementPoint);
				triplePoints.add(new TriplePoint(graphNumber, triplePointNumber,
					angles));
				triplePointNumber++;
			}
			skeletons.add(triplePoints);
			graphNumber++;
		}

		return skeletons;
	}

	/**
	 * Checks whether the graphs contain triple points that have circular edges
	 *
	 * @see TriplePointAngles#hasCircularEdges(Vertex) hasCircularEdges
	 * @see TriplePointAngles#isTriplePoint(Vertex) isTriplePoint
	 */
	@Contract("null -> false")
	public static boolean hasCircularEdges(@Nullable final Graph[] graphs) {
		if (graphs == null) {
			return false;
		}

		for (Graph graph : graphs) {
			if (graph.getVertices().stream().anyMatch(v -> isTriplePoint(v) &&
				hasCircularEdges(v)))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Checks if the vertex has circular edges
	 * <p>
	 * For a circular edge e.getV1() == e.getV2()
	 */
	// region -- Helper methods --
	private static boolean hasCircularEdges(final Vertex vertex) {
		return vertex.getBranches().stream().anyMatch(e -> e.getV1() == e.getV2());
	}

	/** Checks if the vertex has three branches */
	private static boolean isTriplePoint(final Vertex vertex) {
		return vertex.getBranches().size() == 3;
	}

	/**
	 * Calculates the angles of the triple point
	 *
	 * @param vertex A triple point in a Graph - must have three branches
	 * @param measurementPoint if >= 0, then measure angle from the nth slab of
	 *          the edge if == VERTEX_TO_VERTEX, then measure angle from the
	 *          opposite centroid
	 * @return The three angles between the edges of the triple point
	 * @see #getMeasurementPoint(Vertex, Edge, int)
	 * @see #VERTEX_TO_VERTEX
	 */
	private List<Double> triplePointAngles(final Vertex vertex,
		final Integer measurementPoint)
	{
		List<Edge> edges = vertex.getBranches();
		Edge edge0 = edges.get(0);
		Edge edge1 = edges.get(1);
		Edge edge2 = edges.get(2);

		return asList(measureAngle(vertex, edge0, edge1, measurementPoint),
			measureAngle(vertex, edge0, edge2, measurementPoint), measureAngle(vertex,
				edge1, edge2, measurementPoint));
	}

	/**
	 * Calculates the angle between the given edges at the given vertex
	 *
	 * @param vertex The meeting point of the edges. A triple point
	 * @param edge0 One of the edges at the vertex
	 * @param edge1 Another edge at the vertex
	 * @param measurementPoint if >= 0, then measure angle from the nth slab of
	 *          the edge if == VERTEX_TO_VERTEX, then measure angle from the
	 *          opposite centroid
	 * @return Angle in radians
	 * @see #getMeasurementPoint(Vertex, Edge, int)
	 * @see #VERTEX_TO_VERTEX
	 */
	private double measureAngle(final Vertex vertex, final Edge edge0,
		final Edge edge1, final int measurementPoint)
	{
		final Vector3d anglePoint = (Vector3d) centroidOp.calculate(toVector3d(vertex
			.getPoints()));
		final Vector3d oppositePoint0 = getMeasurementPoint(vertex, edge0,
			measurementPoint);
		final Vector3d oppositePoint1 = getMeasurementPoint(vertex, edge1,
			measurementPoint);

		return vectorAngleAtPoint(oppositePoint0, oppositePoint1, anglePoint);
	}

	private List<Vector3d> toVector3d(final List<Point> points) {
		return points.stream().map(this::toVector3d).collect(toList());
	}

	private Vector3d toVector3d(final Point point) {
		return new Vector3d(point.x, point.y, point.z);
	}

	/**
	 * Returns the point from which the angle of the given edge is measured
	 *
	 * @param vertex Point where the edge meets another edge (triple point)
	 * @param edge Edge in the graph
	 * @param measurementPoint if >= 0, then measure angle from the nth slab of
	 *          the edge if == VERTEX_TO_VERTEX, then measure angle from the of
	 *          the opposite centroid
	 * @see #getNthSlabOfEdge(Vertex, Edge, int)
	 * @see #getOppositeCentroid(Vertex, Edge)
	 * @see #VERTEX_TO_VERTEX
	 */
	private Vector3d getMeasurementPoint(final Vertex vertex, final Edge edge,
		final int measurementPoint)
	{
		if (measurementPoint == VERTEX_TO_VERTEX || edge.getSlabs().isEmpty()) {
			return getOppositeCentroid(vertex, edge);
		}

		return getNthSlabOfEdge(vertex, edge, measurementPoint);
	}

	/**
	 * Gets the nth slab of the given edge
	 *
	 * @param vertex Starting point of the edge slabs
	 * @param edge Edge that contains the slab
	 * @param measurementPoint Ordinal of the slab (starts from 0)
	 * @return The nth slab/segment of the edge
	 */
	private Vector3d getNthSlabOfEdge(final Vertex vertex, final Edge edge,
		final int measurementPoint)
	{
		final List<Vector3d> slabs = toVector3d(edge.getSlabs());
		final Vector3d firstSlab = slabs.get(0);
		final List<Vector3d> vertexPoints = toVector3d(vertex.getPoints());

		// Check if the edge starts from the given vertex, or its opposite vertex
		final boolean startsAtVertex = vertexPoints.stream().anyMatch(
			p -> isAxesDistancesOne(firstSlab, p));

		final int slabIndex = Math.min(Math.max(0, measurementPoint), slabs.size() -
			1);

		if (startsAtVertex) {
			return slabs.get(slabIndex);
		}
		else {
			final int slabIndexFromEnd = slabs.size() - slabIndex - 1;
			return slabs.get(slabIndexFromEnd);
		}
	}

	/**
	 * Returns true if the distance of the two vectors in each dimension is less
	 * than one. Can be used to check if vector p1 is in the 27-neighborhood of
	 * p0.
	 */
	private boolean isAxesDistancesOne(final Vector3d p0, final Vector3d p1) {
		final double xDiff = Math.abs(p0.getX() - p1.getX());
		final double yDiff = Math.abs(p0.getY() - p1.getY());
		final double zDiff = Math.abs(p0.getZ() - p1.getZ());

		return xDiff <= 1 && yDiff <= 1 && zDiff <= 1;
	}

	/**
	 * Returns the centroid of the vertex opposite to the given vertex along the
	 * given edge
	 */
	private Vector3d getOppositeCentroid(final Vertex vertex, final Edge edge) {
		final Vertex oppositeVertex = edge.getOppositeVertex(vertex);
		final List<Vector3d> oppositeVertexVectors = toVector3d(oppositeVertex
			.getPoints());
		return (Vector3d) centroidOp.calculate(oppositeVertexVectors);
	}

	/** Calculates the angle between u and v at the given point p */
	private double vectorAngleAtPoint(final Vector3d u, final Vector3d v,
		final Vector3d p)
	{
		u.sub(p);
		v.sub(p);

		return u.angle(v);
	}
	// endregion

	// region -- Helper classes

	/** A simple helper class to present the results of the Op */
	public static class TriplePoint {

		private final int graphNumber;
		private final int triplePointNumber;
		private final List<Double> angles;

		/**
		 * Constructs and validates a new Triple Point result
		 *
		 * @throws NullPointerException if angles == null
		 * @throws IllegalArgumentException if angles.size() != 3 || angles.get(i)
		 *           == null
		 */
		public TriplePoint(final int graphNumber, final int triplePointNumber,
			final List<Double> angles) throws NullPointerException,
			IllegalArgumentException
		{
			checkNotNull(angles, "Angles list cannot be null");
			checkArgument(angles.size() == 3, "There must be three angles");
			checkArgument(angles.stream().allMatch(a -> a != null),
				"An angle must not be null");

			this.graphNumber = graphNumber;
			this.triplePointNumber = triplePointNumber;
			this.angles = angles;
		}

		/** Returns a deep copy of the angles at the triple point in radians */
		public List<Double> getAngles() {
			return angles.stream().map(Double::new).collect(toList());
		}

		/** Returns the number of the graph/skeleton in the image */
		public int getGraphNumber() {
			return graphNumber;
		}

		/** Returns the number of the triple point in the graph */
		public int getTriplePointNumber() {
			return triplePointNumber;
		}
	}
	// endregion
}
