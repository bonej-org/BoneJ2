package org.bonej.wrapperPlugins;

import static java.util.stream.Collectors.toList;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_CHANNEL_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_TIME_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_SKELETONS;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.CANCEL_OPTION;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.DoubleStream;

import net.imagej.ops.OpService;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.patcher.LegacyInjector;
import net.imagej.table.DefaultColumn;
import net.imagej.table.DefaultResultsTable;
import net.imagej.table.DoubleColumn;
import net.imagej.table.ResultsTable;
import net.imagej.table.Table;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

import org.bonej.ops.CentroidLinAlg3d;
import org.bonej.ops.CleanShortEdges;
import org.bonej.ops.NPoint;
import org.bonej.ops.NPoint.VectorsAngle;
import org.bonej.ops.NPointAngles;
import org.bonej.ops.VertexValenceSorter;
import org.bonej.utilities.GraphUtil;
import org.bonej.utilities.ImagePlusUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.vecmath.Vector3d;
import org.scijava.widget.NumberWidget;

import ij.ImagePlus;
import ij.measure.Calibration;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Vertex;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * <p>
 * A wrapper UI class to calculate the inter-trabecular angles based on the
 * study by Reznikov et al (2016): (<a href=
 * "http://dx.doi.org/10.1016/j.actbio.2016.08.040">http://dx.doi.org/10.1016/j.actbio.2016.08.040
 * </a>). The original code used in that study can be found here: <a href=
 * "http://www.weizmann.ac.il/Structural_Biology/Weiner/ita-app">http://www.weizmann.ac.il/Structural_Biology/Weiner/ita-app</a>
 * </p>
 *
 * @author Alessandro Felder
 * @author Richard Domander
 * @see sc.fiji.analyzeSkeleton.AnalyzeSkeleton_
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Inter-trabecular Angle", initializer = "commandInit")
public class IntertrabecularAngleWrapper extends ContextCommand {

	public static final String NO_RESULTS_MSG = "There were no results - try changing valence range or minimum trabecular length";

	static {
		// NB: Needed if you mix-and-match IJ1 and IJ2 classes.
		// And even then: do not use IJ1 classes in the API!
		LegacyInjector.preinit();
	}

	@Parameter(validater = "imageValidater")
	private ImagePlus inputImage;

	@Parameter(label = "Minimum valence", min = "3", max = "50", stepSize = "1", description = "Minimum number of outgoing branches needed for a trabecular node to be included in analysis", style = NumberWidget.SLIDER_STYLE, persistKey = "ITA_min_valence", callback = "enforceValidRange")
	private int minimumValence = 3;

	@Parameter(label = "Maximum valence", min = "3", max = "50", stepSize = "1", description = "Maximum number of outgoing branches needed for a trabecular node to be included in analysis", style = NumberWidget.SLIDER_STYLE, persistKey = "ITA_max_valence", callback = "enforceValidRange")
	private int maximumValence = 3;

	@Parameter(label = "Minimum trabecular length (px)", min = "0", stepSize = "1", description = "Minimum length for a trabecula to be kept from being fused into a node", style = NumberWidget.SPINNER_STYLE, callback = "calculateRealLength")
	private int minimumTrabecularLength = 0;

    @Parameter(label = "Calibrated minimum length", visibility = ItemVisibility.MESSAGE, persist = false)
	private String realLength = "";

	@Parameter(label = "Iterate pruning", description = "Iterate pruning as long as short edges remain, or stop after a single pass", required = false, persistKey = "ITA_iterate")
	private boolean iteratePruning = false;

	@Parameter(label = "Print centroids", description = "Print the centroids of vertices at either end of each edge", required = false, persistKey = "ITA_print_centroids")
	private boolean printCentroids = false;

	@Parameter(label = "Print % culled edges", description = "Print the percentage of each of the type of edges that were culled after calling analyseSkeleton", required = false, persistKey = "ITA_print_culled_edges")
	private boolean printCulledEdgePercentages = false;

	/** The ITA angles in a {@link Table}, null if there are no results */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> anglesTable;

	/**
	 * The ITA edge-end coordinates in a {@link Table}, null if there are no
	 * results
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Edge endpoints")
	private ResultsTable centroidTable;

	@Parameter(type = ItemIO.OUTPUT, label = "Edge culling percentages")
	private ResultsTable culledEdgePercentagesTable;

	@SuppressWarnings("unused")
	@Parameter
	private OpService opService;

	@SuppressWarnings("unused")
	@Parameter
	private StatusService statusService;

	@SuppressWarnings("unused")
	@Parameter
	private UIService uiService;

	@SuppressWarnings("unused")
	@Parameter
	private PrefService prefService;

	private UnaryFunctionOp<List, Vector3d> centroidOp;
	private BinaryFunctionOp<Graph, Double, Graph> cleanShortEdgesOp;
	private BinaryFunctionOp<Graph, Pair<Integer, Integer>, Map<Integer, List<Vertex>>> valenceSorterOp;
	private BinaryFunctionOp<List<Vertex>, Integer, List<NPoint>> nPointAnglesOp;
	private ValuePair<Integer, Integer> range;
	private CleanShortEdges.PercentagesOfCulledEdges percentages;
	private List<Double> coefficients;
    private double calibratedMinimumLength;

    @Override
	public void run() {
		statusService.showStatus("Intertrabecular angles: Initialising...");
		matchOps();
		statusService.showStatus("Intertrabecular angles: skeletonising");
		final ImagePlus skeleton = skeletonise();
		statusService.showStatus("Intertrabecular angles: analysing skeletons");
		final Graph[] graphs = analyzeSkeleton(skeleton);
		if (graphs == null || graphs.length == 0) {
			cancel(NO_SKELETONS);
			return;
		}
		warnMultipleGraphs(graphs);
		final Graph largestGraph = Arrays.stream(graphs).max(Comparator.comparingInt(a -> a.getVertices().size()))
				.orElse(new Graph());
		statusService.showStatus("Intertrabecular angles: pruning graph");
		final Graph cleanGraph = cleanShortEdgesOp.calculate(largestGraph, calibratedMinimumLength);
		statusService.showStatus("Intertrabecular angles: valence sorting trabeculae");
		final Map<Integer, List<Vertex>> valenceMap = valenceSorterOp.calculate(cleanGraph, range);
		statusService.showStatus("Intertrabecular angles: calculating angles");
		final Map<Integer, DoubleStream> radianMap = createRadianMap(valenceMap);
		addResults(radianMap);
		printEdgeCentroids(cleanGraph.getEdges());
		printCulledEdgePercentages();
	}

    private void printEdgeCentroids(final List<Edge> edges) {
		if (!printCentroids || edges == null || edges.isEmpty()) {
			return;
		}

		final List<DoubleColumn> columns = Arrays.asList(new DoubleColumn("V1x"), new DoubleColumn("V1y"),
				new DoubleColumn("V1z"), new DoubleColumn("V2x"), new DoubleColumn("V2y"), new DoubleColumn("V2z"));

		final List<Vector3d> v1Centroids = edges.stream().map(e -> e.getV1().getPoints())
				.map(GraphUtil::toVector3d).map(centroidOp::calculate).collect(toList());
		final List<Vector3d> v2Centroids = edges.stream().map(e -> e.getV2().getPoints())
				.map(GraphUtil::toVector3d).map(centroidOp::calculate).collect(toList());
		for (int i = 0; i < v1Centroids.size(); i++) {
			final Vector3d v1centroid = v1Centroids.get(i);
			columns.get(0).add(v1centroid.x);
			columns.get(1).add(v1centroid.y);
			columns.get(2).add(v1centroid.z);
			final Vector3d v2centroid = v2Centroids.get(i);
			columns.get(3).add(v2centroid.x);
			columns.get(4).add(v2centroid.y);
			columns.get(5).add(v2centroid.z);
		}
		centroidTable = new DefaultResultsTable();
		centroidTable.addAll(columns);
	}

	// TODO test (& test that culled with calibrated length)
	private void printCulledEdgePercentages() {
		if (!printCulledEdgePercentages) {
			return;
		}
		percentages = ((CleanShortEdges) cleanShortEdgesOp).getPercentages();
		if (percentages.getTotalEdges() == 0) {
			return;
		}

		final DoubleColumn loopCol = new DoubleColumn("Loop edges (%)");
		final DoubleColumn repeatedCol = new DoubleColumn("Repeated edges (%)");
		final DoubleColumn shortCol = new DoubleColumn("Short edges (%)");
		final DoubleColumn deadEndCol = new DoubleColumn("Dead end edges (%)");

		loopCol.add(percentages.getPercentageLoopEdges());
		repeatedCol.add(percentages.getPercentageParallelEdges());
		shortCol.add(percentages.getPercentageClusterEdges());
		deadEndCol.add(percentages.getPercentageDeadEnds());

		culledEdgePercentagesTable = new DefaultResultsTable();
		culledEdgePercentagesTable.add(loopCol);
		culledEdgePercentagesTable.add(repeatedCol);
		culledEdgePercentagesTable.add(shortCol);
		culledEdgePercentagesTable.add(deadEndCol);
	}

	private void warnMultipleGraphs(final Graph[] graphs) {
		if (graphs.length < 2) {
			return;
		}
		uiService.showDialog("Image has multiple skeletons - processing the largest", WARNING_MESSAGE);
	}

	private TreeMap<Integer, DoubleStream> createRadianMap(final Map<Integer, List<Vertex>> valenceMap) {
		final TreeMap<Integer, DoubleStream> radianMap = new TreeMap<>();
		valenceMap.forEach((valence, vertices) -> {
			final List<Vertex> centreVertices = filterBoundaryVertices(vertices);
			final List<NPoint> nPoints = nPointAnglesOp.calculate(centreVertices, -1);
			final DoubleStream radians = nPoints.stream().flatMap(p -> p.angles.stream())
					.mapToDouble(VectorsAngle::getAngle);
			radianMap.put(valence, radians);
		});
		return radianMap;
	}

	private List<Vertex> filterBoundaryVertices(final List<Vertex> vertices) {
		return vertices.stream().filter(v -> !isCloseToBoundary(v)).collect(toList());
	}

	@SuppressWarnings("unchecked")
	private void matchOps() {
		centroidOp = Functions.unary(opService, CentroidLinAlg3d.class, Vector3d.class, List.class);
		cleanShortEdgesOp = Functions.binary(opService, CleanShortEdges.class, Graph.class, Graph.class, Double.class,
				coefficients, iteratePruning);
		range = new ValuePair<>(minimumValence, maximumValence);
		valenceSorterOp = (BinaryFunctionOp) Functions.binary(opService, VertexValenceSorter.class, Map.class,
				Graph.class, range);
		nPointAnglesOp = (BinaryFunctionOp) Functions.binary(opService, NPointAngles.class, List.class, List.class,
				Integer.class);
	}

	private boolean isCloseToBoundary(final Vertex v) {
		final List<Vector3d> pointVectors = GraphUtil.toVector3d(v.getPoints());
		final Vector3d centroid = centroidOp.calculate(pointVectors);
		final int width = inputImage.getWidth();
		final int height = inputImage.getHeight();
		final int depth = inputImage.getNSlices();
		return centroid.x < minimumTrabecularLength || centroid.x > width - minimumTrabecularLength
				|| centroid.y < minimumTrabecularLength || centroid.y > height - minimumTrabecularLength
				|| depth != 1 && (centroid.z < minimumTrabecularLength || centroid.z > depth - minimumTrabecularLength);
	}

	private Graph[] analyzeSkeleton(final ImagePlus skeleton) {
		// Analyse skeleton
		final AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();
		analyser.setup("", skeleton);
		analyser.run();
		return analyser.getGraphs();
	}

	private ImagePlus skeletonise() {
		// Skeletonise input image
		final ImagePlus skeleton = Common.cleanDuplicate(inputImage);
		final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);

		// check whether input image was skeletonised already
		final int iterations = skeletoniser.getThinningIterations();
		if (iterations > 1) {
			skeleton.setTitle("Skeleton of " + inputImage.getTitle());
			uiService.show(skeleton);
		}
		return skeleton;
	}

	private void addResults(final Map<Integer, DoubleStream> anglesMap) {
		// TODO remove table reset when merged with current master
		SharedTable.reset();
		final String label = inputImage.getTitle();
		anglesMap.forEach((valence, angles) -> {
			final String heading = valence.toString();
			angles.forEach(angle -> SharedTable.add(label, heading, angle));
		});
		if (SharedTable.hasData()) {
			anglesTable = SharedTable.getTable();
		} else {
			cancel(NO_RESULTS_MSG);
		}
	}

	@SuppressWarnings("unused")
	private void imageValidater() {
		if (inputImage == null) {
			cancel(CommonMessages.NO_IMAGE_OPEN);
			return;
		}
		if (inputImage.getBitDepth() != 8 || !ImagePlusUtil.isBinaryColour(inputImage)) {
			cancel(NOT_8_BIT_BINARY_IMAGE);
			return;
		}

		if (inputImage.isComposite()) {
			cancel(HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
			return;
		}

		if (inputImage.isHyperStack()) {
			cancel(HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
		}

		warnAnisotropy();
	}

	@SuppressWarnings("unused")
	private void commandInit() {
		final String s = prefService.get(getClass(), "minimumTrabecularLength");
		minimumTrabecularLength = s == null ? 0 : Integer.valueOf(s);
		initRealLength();
	}

	@SuppressWarnings("unused")
	private void initRealLength() {
		if (inputImage == null || inputImage.getCalibration() == null) {
			coefficients = Arrays.asList(1.0, 1.0, 1.0);
            realLength = String.join(" ", String.format("%.2g", (double)minimumTrabecularLength));
		} else {
			final Calibration calibration = inputImage.getCalibration();
			coefficients = Arrays.asList(calibration.pixelWidth, calibration.pixelHeight, calibration.pixelDepth);
            calculateRealLength();
		}
	}
    
	@SuppressWarnings("unused")
	private void calculateRealLength() {
        calibratedMinimumLength = minimumTrabecularLength * coefficients.get(0);
		final String unit = inputImage.getCalibration().getUnit();
		realLength = String.join(" ", String.format("%.2g", calibratedMinimumLength), unit);
	}

	@SuppressWarnings("unused")
	private void enforceValidRange() {
		if (minimumValence > maximumValence) {
			minimumValence = maximumValence;
		}
	}

	// TODO make util
	private void warnAnisotropy() {
		final double anisotropy = ImagePlusUtil.anisotropy(inputImage);
		if (anisotropy > 1E-3) {
			final String anisotropyPercent = String.format(" (%.1f %%)", anisotropy * 100.0);
			final DialogPrompt.Result result = uiService.showDialog(
					"The image is anisotropic" + anisotropyPercent + ". Continue anyway?", WARNING_MESSAGE,
					OK_CANCEL_OPTION);
			if (result == CANCEL_OPTION) {
				cancel(null);
			}
		}
	}
}