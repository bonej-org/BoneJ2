package org.bonej.ops;

import java.util.ArrayList;
import java.util.List;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;

import org.bonej.ops.NPoint.VectorsAngle;
import org.bonej.utilities.GraphUtil;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Vector3d;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Vertex;

@Plugin(name = "nPointAngles", type = Op.class)
public class NPointAngles extends AbstractBinaryFunctionOp<List<Vertex>, Integer, List<NPoint>> {

	/**
	 * A special value for measurementPoint
	 * 
	 * @see #getMeasurementPoint(Vertex, Edge, int)
	 */
	public static final int VERTEX_TO_VERTEX = -1;
	private UnaryFunctionOp<List, Vector3d> centroidOp;

	/**
	 * pre-match op to efficiently find centroids of vertices
	 * 
	 */
	@Override
	public void initialize() {
		centroidOp = Functions.unary(ops(), CentroidLinAlg3d.class, Vector3d.class, List.class);
	}

	/**
	 * This function computes the angles between outgoing edges for each vertex
	 * in a list.
	 * 
	 * @param vertices
	 *            We want to compute angles between connected edges for each
	 *            {@link Vertex} in this list.
	 * @param measurementMode
	 *            specifies the number of slabs away from the central vertex we
	 *            want to measure the angle to -1 signifies using the vertex at
	 *            the other end of the edge.
	 * @return a list of {@link NPoint} which represent collections of angles
	 *         around a vertex
	 */
	@Override
	public List<NPoint> calculate(final List<Vertex> vertices, final Integer measurementMode) {
		final List<NPoint> nPoints = new ArrayList<>();
		for (final Vertex v : vertices) {
			final List<VectorsAngle> vectorAngles = computeVectorAngles(v, measurementMode);
			final NPoint npoint = new NPoint(v, vectorAngles);
			nPoints.add(npoint);
		}
		return nPoints;
	}

	private List<VectorsAngle> computeVectorAngles(final Vertex centre, final int measurementPoint) {
		final List<Edge> edges = centre.getBranches();
		final List<VectorsAngle> vectorAngles = new ArrayList<>();
		for (int i = 0; i < edges.size() - 1; i++) {
			for (int j = i + 1; j < edges.size(); j++) {
				final Vector3d centreVector = centroidOp.calculate(GraphUtil.toVector3d(centre.getPoints()));
				final Vector3d endVectorI = getMeasurementPoint(centre, edges.get(i), measurementPoint);
				final Vector3d endVectorJ = getMeasurementPoint(centre, edges.get(j), measurementPoint);

				endVectorI.sub(centreVector);
				endVectorJ.sub(centreVector);

				final VectorsAngle vectorsAngle = new VectorsAngle(endVectorI, endVectorJ);
				vectorAngles.add(vectorsAngle);
			}
		}
		return vectorAngles;
	}

	/**
	 * Returns the point from which the angle of the given edge is measured
	 *
	 * @param vertex
	 *            Point where the edge meets another edge (triple point)
	 * @param edge
	 *            Edge in the graph
	 * @param measurementPoint
	 *            if >= 0, then measure angle from the nth slab of the edge if
	 *            == VERTEX_TO_VERTEX, then measure angle from the of the
	 *            opposite centroid
	 * @see #getNthSlabOfEdge(Vertex, Edge, int)
	 * @see #getOppositeCentroid(Vertex, Edge)
	 * @see #VERTEX_TO_VERTEX
	 */
	private Vector3d getMeasurementPoint(final Vertex vertex, final Edge edge, final int measurementPoint) {
		if (measurementPoint == VERTEX_TO_VERTEX || edge.getSlabs() == null
				|| measurementPoint > edge.getSlabs().size() - 1) {
			return getOppositeCentroid(vertex, edge);
		}

		return getNthSlabOfEdge(vertex, edge, measurementPoint);
	}

	/**
	 * Returns the centroid of the vertex opposite to the given vertex along the
	 * given edge
	 */
	private Vector3d getOppositeCentroid(final Vertex vertex, final Edge edge) {
		final Vertex oppositeVertex = edge.getOppositeVertex(vertex);
		final List<Vector3d> oppositeVertexVectors = GraphUtil.toVector3d(oppositeVertex.getPoints());
		return centroidOp.calculate(oppositeVertexVectors);
	}

	/**
	 * Gets the nth slab of the given edge
	 *
	 * @param vertex
	 *            Starting point of the edge slabs
	 * @param edge
	 *            Edge that contains the slab
	 * @param measurementPoint
	 *            Ordinal of the slab (starts from 0)
	 * @return The nth slab/segment of the edge
	 */
	private Vector3d getNthSlabOfEdge(final Vertex vertex, final Edge edge, final int measurementPoint) {
		final List<Vector3d> slabs = GraphUtil.toVector3d(edge.getSlabs());
		final Vector3d firstSlab = slabs.get(0);
		final List<Vector3d> vertexPoints = GraphUtil.toVector3d(vertex.getPoints());

		// Check if the edge starts from the given vertex, or its opposite
		// vertex
		final boolean startsAtVertex = vertexPoints.stream().anyMatch(p -> isInNeighbourhood(firstSlab, p));

		final int slabIndex = Math.min(Math.max(0, measurementPoint), slabs.size() - 1);

		if (startsAtVertex) {
			return slabs.get(slabIndex);
		}
		final int slabIndexFromEnd = slabs.size() - slabIndex - 1;
		return slabs.get(slabIndexFromEnd);
	}

	/**
	 * Returns true if the distance of the two vectors in each dimension is less
	 * than one. Can be used to check if vector p1 is in the 27-neighborhood of
	 * p0.
	 */
	private boolean isInNeighbourhood(final Vector3d p0, final Vector3d p1) {
		final double xDiff = Math.abs(p0.getX() - p1.getX());
		final double yDiff = Math.abs(p0.getY() - p1.getY());
		final double zDiff = Math.abs(p0.getZ() - p1.getZ());

		return xDiff <= 1 && yDiff <= 1 && zDiff <= 1;
	}
}
