
package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.scijava.vecmath.Vector3d;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Vertex;

/**
 * Tests for {@link GraphUtil}
 *
 * @author Richard Domander
 */
public class GraphUtilTest {

    @Test
    public void testToVector3dEmptyList() {
        final List<Vector3d> vectors = GraphUtil.toVector3d(Collections.emptyList());

        assertEquals(1, vectors.size());
        final Vector3d v = vectors.get(0);
        assertTrue(Double.isNaN(v.x));
        assertTrue(Double.isNaN(v.y));
        assertTrue(Double.isNaN(v.z));
    }
    
    @Test
    public void testToVector3dList() {
        final List<Point> points = Arrays.asList(new Point(1, 2, 3), new Point(7, 8, 9));

        final List<Vector3d> vectors = GraphUtil.toVector3d(points);

        assertEquals(points.size(), vectors.size());
        for (int i = 0; i < points.size(); i++) {
            assertVector3d(points.get(i), vectors.get(i));
        }
    }

    @Test
    public void testToVector3d() {
        final Point p = new Point(1, 2, 3);

        final Vector3d v = GraphUtil.toVector3d(p);

        assertVector3d(p, v);
    }

    @Test
	public void testIsLoop() {
		final List<Vertex> vertices = Arrays.asList(new Vertex(), new Vertex());
		final Vertex v0 = vertices.get(0);

		assertTrue(GraphUtil.isLoop(new Edge(v0, v0, null, 0.0)));
		assertFalse(GraphUtil.isLoop(new Edge(v0, vertices.get(1), null, 0.0)));
		assertFalse(GraphUtil.isLoop(new Edge(null, null, null, 0.0)));
		assertFalse(GraphUtil.isLoop(new Edge(null, v0, null, 0.0)));
		assertFalse(GraphUtil.isLoop(new Edge(v0, null, null, 0.0)));
	}

	@Test
    public void testCreateGraph() {
        final List<Vertex> vertices = Arrays.asList(new Vertex(), new Vertex());
        final Vertex v0 = vertices.get(0);
        final Vertex v1 = vertices.get(1);
        final List<Edge> edges = Arrays.asList(new Edge(v0, v1, null, 0), new Edge(v1, v1, null, 0));

        final Graph graph = GraphUtil.createGraph(edges, vertices);

        assertEquals(vertices.size(), graph.getVertices().size());
        for (int i = 0; i < vertices.size(); i++) {
            assertEquals(vertices.get(i), graph.getVertices().get(i));
        }
    }

    @Test
    public void testVectorToVertexNullVector() {
        final Point point = GraphUtil.vectorToVertex(null).getPoints().get(0);

        assertEquals(Integer.MAX_VALUE, point.x);
        assertEquals(Integer.MAX_VALUE, point.y);
        assertEquals(Integer.MAX_VALUE, point.z);
    }

    @Test
    public void testVectorToVertex() {
        final Vector3d vector = new Vector3d(Double.NaN, 1.4, 1.5);

        final Point point = GraphUtil.vectorToVertex(vector).getPoints().get(0);

        assertEquals(Integer.MAX_VALUE, point.x);
        assertEquals(1, point.y);
        assertEquals(2, point.z);
    }

    private void assertVector3d(final Point expected, final Vector3d actual) {
        assertEquals(expected.x, actual.x, 1e-12);
        assertEquals(expected.y, actual.y, 1e-12);
        assertEquals(expected.z, actual.z, 1e-12);
    }
}
