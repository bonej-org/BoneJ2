package org.bonej.ops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bonej.utilities.GraphUtil;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.Vertex;

public class TestGraphs {

    /**
	 * Structure of sail graph example
	 * 
	 * <pre>
	 * 2
	 * |\
	 * | |
	 * | \
	 * 0--1
	 * |
	 * 3
	 * </pre>
	 */
	public static Graph createSailGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(4).collect(Collectors.toList());
		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(2, 0, 0));
		vertices.get(2).addPoint(new Point(0, 3, 0));
		vertices.get(3).addPoint(new Point(0, -1, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, 2.0),
				new Edge(vertices.get(0), vertices.get(2), null, 3.0),
				new Edge(vertices.get(1), vertices.get(2), null, Math.sqrt(13.0)),
				new Edge(vertices.get(0), vertices.get(3), null, 1.0));

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Structure of the opposing wedges graph example
	 * <p>
	 * Vertices 1 and 3 have same point coordinate, but are not connected
	 * </p>
	 * 
	 * <pre>
	 * 2-------5
	 * |\_   _/|
	 * 0--1 3--4
	 * </pre>
	 */
	public static Graph createOpposingWedgesGraph() {

		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(6).collect(Collectors.toList());
		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(2, 0, 0));
		vertices.get(2).addPoint(new Point(0, 1, 0));
		vertices.get(3).addPoint(new Point(2, 0, 0));
		vertices.get(4).addPoint(new Point(4, 0, 0));
		vertices.get(5).addPoint(new Point(4, 1, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, 2.0),
				new Edge(vertices.get(0), vertices.get(2), null, 1.0),
				new Edge(vertices.get(1), vertices.get(2), null, Math.sqrt(5.0)),
				new Edge(vertices.get(2), vertices.get(5), null, 4),
				new Edge(vertices.get(5), vertices.get(3), null, Math.sqrt(5.0)),
				new Edge(vertices.get(5), vertices.get(4), null, 1.0),
				new Edge(vertices.get(3), vertices.get(4), null, 2.0));

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Creates a triangle graph with square cluster
	 * 
	 * <pre>
	 *             4
	 *           _/|
	 *     3--2_/  |
	 *     |\_|	   |
	 *    _0--1	   |
	 *  _/         |	
	 * /           |
	 *5------------6
	 * </pre>
	 */
	public static Graph createTriangleWithSquareCluster() {

		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(7).collect(Collectors.toList());
		vertices.get(0).addPoint(new Point(-1, -1, 0));
		vertices.get(1).addPoint(new Point(-1, 1, 0));
		vertices.get(2).addPoint(new Point(1, 1, 0));
		vertices.get(3).addPoint(new Point(1, -1, 0));
		vertices.get(4).addPoint(new Point(5, 4, 0));
		vertices.get(5).addPoint(new Point(-4, -5, 0));
		vertices.get(6).addPoint(new Point(5, -5, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, 2.0),
				new Edge(vertices.get(1), vertices.get(2), null, 2.0),
				new Edge(vertices.get(2), vertices.get(3), null, 2.0),
				new Edge(vertices.get(3), vertices.get(0), null, 2.0),
				new Edge(vertices.get(1), vertices.get(3), null, 2.0 * Math.sqrt(2.0)),
				new Edge(vertices.get(2), vertices.get(4), null, 5.0),
				new Edge(vertices.get(0), vertices.get(5), null, 5.0),
				new Edge(vertices.get(4), vertices.get(6), null, 9.0),
				new Edge(vertices.get(5), vertices.get(6), null, 9.0));

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Creates a triangle graph with square cluster, three loops ("o"), two
	 * parallel edges and a dead end edge
	 * 
	 * <pre>
	 *             o				
	 *             4
	 *           _/||
	 *     3--2_/  ||
	 *     |\_|	   ||
	 *    _0--1	   ||
	 *  _/         ||	
	 * /           ||
	 *5------------6------------7
	 *o------------o
	 * </pre>
	 */
	public static Graph createTriangleWithSquareClusterAndArtefacts() {

		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(8).collect(Collectors.toList());
		vertices.get(0).addPoint(new Point(-1, -1, 0));
		vertices.get(1).addPoint(new Point(-1, 1, 0));
		vertices.get(2).addPoint(new Point(1, 1, 0));
		vertices.get(3).addPoint(new Point(1, -1, 0));
		vertices.get(4).addPoint(new Point(5, 4, 0));
		vertices.get(5).addPoint(new Point(-4, -5, 0));
		vertices.get(6).addPoint(new Point(5, -5, 0));
		vertices.get(7).addPoint(new Point(7, -5, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, 2.0),
				new Edge(vertices.get(1), vertices.get(2), null, 2.0),
				new Edge(vertices.get(2), vertices.get(3), null, 2.0),
				new Edge(vertices.get(3), vertices.get(0), null, 2.0),
				new Edge(vertices.get(1), vertices.get(3), null, 2.0 * Math.sqrt(2.0)),
				new Edge(vertices.get(2), vertices.get(4), null, 5.0),
				new Edge(vertices.get(0), vertices.get(5), null, 5.0),
				new Edge(vertices.get(4), vertices.get(6), null, 9.0),
				new Edge(vertices.get(6), vertices.get(4), null, 9.0), // opposite-way-parallel-edge
				new Edge(vertices.get(5), vertices.get(6), null, 9.0),
				new Edge(vertices.get(5), vertices.get(6), null, 9.0), // same-way-parallel-edge
				new Edge(vertices.get(6), vertices.get(7), null, 2.0), // dead-end
				new Edge(vertices.get(5), vertices.get(5), null, 9.0), // loop
				new Edge(vertices.get(6), vertices.get(6), null, 9.0), // loop
				new Edge(vertices.get(4), vertices.get(4), null, 9.0) // loop
		);

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Structure of loop graph example
	 * <p>
	 * "o" denotes a zero length loop edge
	 * </p>
	 * 
	 * <pre>
	 * 	 o
	 *   0
	 *  / \
	 * 1---2
	 * </pre>
	 */
	public static Graph createLoopGraph() {

		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(3).collect(Collectors.toList());
		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(-1, -1, 0));
		vertices.get(2).addPoint(new Point(1, -1, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(0), null, 0.0),
				new Edge(vertices.get(0), vertices.get(1), null, 1.0),
				new Edge(vertices.get(0), vertices.get(2), null, 1.0),
				new Edge(vertices.get(1), vertices.get(2), null, 2.0));

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Structure of arch with slab graph example
	 * <p>
	 * v: vertex point s: slab point of single edge in graph
	 * </p>
	 * 
	 * <pre>
	 *   ss
	 * 	s  s
	 * v	v
	 * </pre>
	 */
	public static Graph createArchWithSlabsGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(2).collect(Collectors.toList());
		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(5, 0, 0));

		final List<Point> slabPoints = Arrays.asList(new Point(1, 1, 0), new Point(2, 2, 0), new Point(3, 2, 0),
				new Point(4, 1, 0));

		final ArrayList<Point> slabs = new ArrayList<>();
		slabs.addAll(slabPoints);

		final Edge edge = new Edge(vertices.get(0), vertices.get(1), slabs, 4 * Math.sqrt(2.0) + 1);

		return GraphUtil.createGraph(edge, vertices);
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
	public static Graph createDownWardFacingFishGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(5).collect(Collectors.toList());
		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, 0.0),
				new Edge(vertices.get(1), vertices.get(2), null, 0.0),
				new Edge(vertices.get(2), vertices.get(3), null, 0.0),
				new Edge(vertices.get(1), vertices.get(3), null, 0.0),
				new Edge(vertices.get(1), vertices.get(4), null, 0.0));
		return GraphUtil.createGraph(edges, vertices);
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
	public static Graph createFrameGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(4).collect(Collectors.toList());

		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(1, 0, 0));
		vertices.get(2).addPoint(new Point(0, 1, 0));
		vertices.get(3).addPoint(new Point(0, 0, 1));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, 1.0),
				new Edge(vertices.get(0), vertices.get(2), null, 1.0),
				new Edge(vertices.get(0), vertices.get(3), null, 1.0));

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Structure of the Great British pound shaped graph example
	 * 
	 * v: vertex s: slab
	 * 
	 * <pre>
	 *  
	 *   s v
	 * s
	 * s
	 * v s v
	 * </pre>
	 */
	public static Graph createGBPShapedGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(3).collect(Collectors.toList());

		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(2, 0, 0));
		vertices.get(2).addPoint(new Point(2, 2, 0));

		final ArrayList<Point> slabs1 = new ArrayList<>();
		slabs1.add(new Point(1, 0, 0));

		final ArrayList<Point> slabs2 = new ArrayList<>();
		slabs2.add(new Point(0, 1, 0));
		slabs2.add(new Point(0, 2, 0));
		slabs2.add(new Point(1, 3, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), slabs1, 2.0),
				new Edge(vertices.get(0), vertices.get(2), slabs2, 3.83));

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Structure of the dumbbell graph example
	 * 
	 * <pre>
	 * 2      5
	 *  \    /
	 *   1--3
	 *  /    \
	 * 0      4
	 * </pre>
	 */
	public static Graph createDumbbellGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(6).collect(Collectors.toList());

		vertices.get(0).addPoint(new Point(0, -1, 0));
		vertices.get(1).addPoint(new Point(1, 0, 0));
		vertices.get(2).addPoint(new Point(0, 1, 0));
		vertices.get(3).addPoint(new Point(4, 0, 0));
		vertices.get(4).addPoint(new Point(5, -1, 0));
		vertices.get(5).addPoint(new Point(5, 1, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, Math.sqrt(2.0)),
				new Edge(vertices.get(0), vertices.get(2), null, 2.0),
				new Edge(vertices.get(1), vertices.get(2), null, Math.sqrt(2.0)),
				new Edge(vertices.get(3), vertices.get(4), null, Math.sqrt(2.0)),
				new Edge(vertices.get(4), vertices.get(5), null, 2.0),
				new Edge(vertices.get(5), vertices.get(3), null, Math.sqrt(2.0)),
				new Edge(vertices.get(1), vertices.get(3), null, 3.0));

		return GraphUtil.createGraph(edges, vertices);
	}

	/**
	 * Structure of the kite graph example
	 * 
	 * <pre>
	 *       ______4
	 *     _/     _/
	 *    /      /
	 *  _/     _/   
	 * 2     _3
	 * |   _/
	 * 0--1
	 * </pre>
	 */
	public static Graph createKiteGraph() {
		final List<Vertex> vertices = Stream.generate(Vertex::new).limit(5).collect(Collectors.toList());

		vertices.get(0).addPoint(new Point(0, 0, 0));
		vertices.get(1).addPoint(new Point(1, 0, 0));
		vertices.get(2).addPoint(new Point(0, 1, 0));
		vertices.get(3).addPoint(new Point(2, 2, 0));
		vertices.get(4).addPoint(new Point(5, 5, 0));

		final List<Edge> edges = Arrays.asList(new Edge(vertices.get(0), vertices.get(1), null, 1.0),
				new Edge(vertices.get(0), vertices.get(2), null, 1.0),
				new Edge(vertices.get(1), vertices.get(3), null, Math.sqrt(3.0)),
				new Edge(vertices.get(2), vertices.get(3), null, Math.sqrt(3.0)),
				new Edge(vertices.get(3), vertices.get(4), null, 3 * Math.sqrt(2.0)));

		return GraphUtil.createGraph(edges, vertices);
	}
}
