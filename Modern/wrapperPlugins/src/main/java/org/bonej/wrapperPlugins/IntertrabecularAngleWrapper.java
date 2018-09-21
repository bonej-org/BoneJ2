/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.wrapperPlugins;

import static java.util.stream.Collectors.toList;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_CHANNEL_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_TIME_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_SKELETONS;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.DoubleStream;

import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imagej.table.DefaultColumn;
import net.imagej.table.DefaultResultsTable;
import net.imagej.table.DoubleColumn;
import net.imagej.table.ResultsTable;
import net.imagej.table.Table;
import net.imglib2.util.ValuePair;

import org.bonej.utilities.ImagePlusUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.bonej.wrapperPlugins.wrapperUtils.UsageReporter;
import org.joml.Vector3d;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

import ij.ImagePlus;
import ij.measure.Calibration;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Vertex;
import sc.fiji.analyzeSkeleton.ita.GraphPruning;
import sc.fiji.analyzeSkeleton.ita.PointUtils;
import sc.fiji.analyzeSkeleton.ita.VertexUtils;
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

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Inter-trabecular Angle")
public class IntertrabecularAngleWrapper extends ContextCommand {

	public static final String NO_RESULTS_MSG =
		"There were no results - try changing valence range or minimum trabecular length";

	static {
		// NB: Needed if you mix-and-match IJ1 and IJ2 classes.
		// And even then: do not use IJ1 classes in the API!
		LegacyInjector.preinit();
	}

	@Parameter(validater = "imageValidater")
	private ImagePlus inputImage;

	@Parameter(label = "Minimum valence", min = "3", max = "50", stepSize = "1",
		description = "Minimum number of outgoing branches needed for a trabecular node to be included in analysis",
		style = NumberWidget.SLIDER_STYLE, persistKey = "ITA_min_valence",
		callback = "enforceValidRange")
	private int minimumValence = 3;

	@Parameter(label = "Maximum valence", min = "3", max = "50", stepSize = "1",
		description = "Maximum number of outgoing branches needed for a trabecular node to be included in analysis",
		style = NumberWidget.SLIDER_STYLE, persistKey = "ITA_max_valence",
		callback = "enforceValidRange")
	private int maximumValence = 3;

	@Parameter(label = "Minimum trabecular length (px)", min = "0",
		stepSize = "1",
		description = "Minimum length for a trabecula to be kept from being fused into a node",
		style = NumberWidget.SPINNER_STYLE, callback = "calculateRealLength",
		persist = false, initializer = "initRealLength")
	private int minimumTrabecularLength;

	@Parameter(label = "Margin (px)", min = "0", stepSize = "1",
		description = "Nodes with centroids closer than this value to any image boundary will not be included in results",
		style = NumberWidget.SPINNER_STYLE)
	private int marginCutOff;

	@Parameter(label = "Calibrated minimum length",
		visibility = ItemVisibility.MESSAGE, persist = false)
	private String realLength = "";

	@Parameter(label = "Iterate pruning",
		description = "If true, iterate pruning as long as short edges remain, or stop after a single pass",
		required = false, persistKey = "ITA_iterate")
	private boolean iteratePruning;

	@Parameter(label = "Use clusters",
		description = "If true, considers connected components together as a cluster, otherwise only looks at single short edges (order-dependent!)",
		required = false, persistKey = "ITA_useClusters")
	private boolean useClusters = true;

	// TODO Fix typo
	@Parameter(label = "Print centroids",
		description = "Print the centroids of vertices at both ends of each edge",
		required = false, persistKey = "ITA_print_centroids")
	private boolean printCentroids;

	@Parameter(label = "Print % culled edges",
		description = "Print the percentage of each of the type of edges that were culled after calling analyseSkeleton",
		required = false, persistKey = "ITA_print_culled_edges")
	private boolean printCulledEdgePercentages;

	/** The ITA angles in a {@link Table}, null if there are no results */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<Double>, Double> anglesTable;

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
	private PrefService prefs;
	@Parameter
	private LogService logService;

	private double[] coefficients;
	private double calibratedMinimumLength;
	private boolean anisotropyWarned;
	private static UsageReporter reporter;

	@Override
	public void run() {
		statusService.showStatus("Intertrabecular angles: Initialising...");
		statusService.showStatus("Intertrabecular angles: skeletonising");
		final ImagePlus skeleton = skeletonise();
		statusService.showStatus("Intertrabecular angles: analysing skeletons");
		final Graph[] graphs = analyzeSkeleton(skeleton);
		if (graphs == null || graphs.length == 0) {
			cancel(NO_SKELETONS);
			return;
		}
		warnMultipleGraphs(graphs);
		final Graph largestGraph = Arrays.stream(graphs).max(Comparator
			.comparingInt(a -> a.getVertices().size())).orElse(new Graph());
		statusService.showStatus("Intertrabecular angles: pruning graph");
		final ValuePair<Graph, double[]> pruningResult = GraphPruning
			.pruneShortEdges(largestGraph, minimumTrabecularLength, iteratePruning,
				useClusters, coefficients);
		final Graph cleanGraph = pruningResult.a;
		statusService.showStatus(
			"Intertrabecular angles: valence sorting trabeculae");
		final Map<Integer, List<Vertex>> valenceMap = VertexUtils.groupByValence(
			cleanGraph.getVertices(), minimumValence, maximumValence);
		statusService.showStatus("Intertrabecular angles: calculating angles");
		final Map<Integer, DoubleStream> radianMap = createRadianMap(valenceMap);
		addResults(radianMap);
		printEdgeCentroids(cleanGraph.getEdges());
		printCulledEdgePercentages(pruningResult.b);
		if (reporter == null) {
			reporter = UsageReporter.getInstance(prefs);
		}
		reporter.reportEvent(getClass().getName());
	}

	static void setReporter(final UsageReporter reporter) {
		if (reporter == null) {
			throw new NullPointerException("Reporter cannot be null");
		}
		IntertrabecularAngleWrapper.reporter = reporter;
	}

	private void addResults(final Map<Integer, DoubleStream> anglesMap) {
		final String label = inputImage.getTitle();
		anglesMap.forEach((valence, angles) -> {
			final String heading = valence.toString();
			angles.forEach(angle -> SharedTable.add(label, heading, angle));
		});
		if (SharedTable.hasData()) {
			anglesTable = SharedTable.getTable();
		}
		else {
			cancel(NO_RESULTS_MSG);
		}
	}

	private Graph[] analyzeSkeleton(final ImagePlus skeleton) {
		// Analyse skeleton
		final AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();
		analyser.setup("", skeleton);
		analyser.run();
		return analyser.getGraphs();
	}

	@SuppressWarnings("unused")
	private void calculateRealLength() {
		calibratedMinimumLength = minimumTrabecularLength * coefficients[0];
		final String unit = ResultUtils.getUnitHeader(inputImage);
		realLength = String.join(" ", String.format("%.2g",
			calibratedMinimumLength), unit);
	}

	private TreeMap<Integer, DoubleStream> createRadianMap(
		final Map<Integer, List<Vertex>> valenceMap)
	{
		final TreeMap<Integer, DoubleStream> radianMap = new TreeMap<>();
		valenceMap.forEach((valence, vertices) -> {
			final List<Vertex> centreVertices = filterBoundaryVertices(vertices);
			final DoubleStream radians = VertexUtils.getNJunctionAngles(
				centreVertices).stream().flatMap(List::stream).mapToDouble(a -> a);
			radianMap.put(valence, radians);
		});
		return radianMap;
	}

	@SuppressWarnings("unused")
	private void enforceValidRange() {
		if (minimumValence > maximumValence) {
			minimumValence = maximumValence;
		}
	}

	private List<Vertex> filterBoundaryVertices(
		final Collection<Vertex> vertices)
	{
		return vertices.stream().filter(v -> !isCloseToBoundary(v)).collect(
			toList());
	}

	@SuppressWarnings("unused")
	private void imageValidater() {
		if (inputImage == null) {
			cancel(CommonMessages.NO_IMAGE_OPEN);
			return;
		}
		if (inputImage.getBitDepth() != 8 || !ImagePlusUtil.isBinaryColour(
			inputImage))
		{
			cancel(NOT_8_BIT_BINARY_IMAGE);
			return;
		}

		if (inputImage.getNChannels() > 1) {
			cancel(HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
			return;
		}

		if (inputImage.getNFrames() > 1) {
			cancel(HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
		}

		if (!anisotropyWarned) {
			if (!Common.warnAnisotropy(inputImage, uiService)) {
				cancel(null);
			}
			anisotropyWarned = true;
		}
	}

	@SuppressWarnings("unused")
	private void initRealLength() {
		if (inputImage == null || inputImage.getCalibration() == null) {
			coefficients = new double[] { 1.0, 1.0, 1.0 };
			realLength = String.join(" ", String.format("%.2g",
				(double) minimumTrabecularLength));
		}
		else {
			final Calibration calibration = inputImage.getCalibration();
			coefficients = new double[] { calibration.pixelWidth,
				calibration.pixelHeight, calibration.pixelDepth };
			calculateRealLength();
		}
	}

	private boolean isCloseToBoundary(final Vertex v) {
		final Vector3d centroid = PointUtils.centroid(v.getPoints());
		final int width = inputImage.getWidth();
		final int height = inputImage.getHeight();
		final int depth = inputImage.getNSlices();
		return centroid.x < marginCutOff || centroid.x > width - marginCutOff ||
			centroid.y < marginCutOff || centroid.y > height - marginCutOff ||
			depth != 1 && (centroid.z < marginCutOff || centroid.z > depth -
				marginCutOff);
	}

	private void printCulledEdgePercentages(final double[] stats) {
		if (!printCulledEdgePercentages) {
			return;
		}
		if (stats[4] == 0) {
			return;
		}
		final DoubleColumn loopCol = new DoubleColumn("Loop edges (%)");
		final DoubleColumn repeatedCol = new DoubleColumn("Repeated edges (%)");
		final DoubleColumn shortCol = new DoubleColumn("Short edges (%)");
		final DoubleColumn deadEndCol = new DoubleColumn("Dead end edges (%)");

		loopCol.add(stats[0]);
		repeatedCol.add(stats[2]);
		shortCol.add(stats[3]);
		deadEndCol.add(stats[1]);

		culledEdgePercentagesTable = new DefaultResultsTable();
		culledEdgePercentagesTable.add(loopCol);
		culledEdgePercentagesTable.add(repeatedCol);
		culledEdgePercentagesTable.add(shortCol);
		culledEdgePercentagesTable.add(deadEndCol);
	}

	private void printEdgeCentroids(final Collection<Edge> edges) {
		if (!printCentroids || edges == null || edges.isEmpty()) {
			return;
		}

		final List<DoubleColumn> columns = Arrays.asList(new DoubleColumn("V1x"),
			new DoubleColumn("V1y"), new DoubleColumn("V1z"), new DoubleColumn("V2x"),
			new DoubleColumn("V2y"), new DoubleColumn("V2z"));

		final List<Vector3d> v1Centroids = edges.stream().map(e -> e.getV1()
			.getPoints()).map(PointUtils::centroid).collect(toList());
		final List<Vector3d> v2Centroids = edges.stream().map(e -> e.getV2()
			.getPoints()).map(PointUtils::centroid).collect(toList());

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

	private ImagePlus skeletonise() {
		// Skeletonise input image
		final ImagePlus skeleton = ImagePlusUtil.cleanDuplicate(inputImage);
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

	private void warnMultipleGraphs(final Graph[] graphs) {
		if (graphs.length < 2) {
			return;
		}
		uiService.showDialog(
			"Image has multiple skeletons - processing the largest", WARNING_MESSAGE);
	}
}
