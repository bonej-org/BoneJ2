/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.wrapperPlugins;

import static org.bonej.utilities.ImagePlusUtil.cleanDuplicate;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_CHANNEL_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_TIME_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.CommonMessages.NO_SKELETONS;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;

import io.scif.FormatException;
import io.scif.services.FormatService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imagej.Dataset;
import net.imagej.patcher.LegacyInjector;

import org.apache.commons.math3.util.MathArrays;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ImagePlusUtil;
import org.bonej.utilities.SharedTable;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.io.IOService;
import org.scijava.io.location.FileLocation;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.DoubleColumn;
import org.scijava.table.IntColumn;
import org.scijava.table.PrimitiveColumn;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.FileWidget;

import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * A wrapper plugin to bundle AnalyzeSkeleton into BoneJ2
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Analyse Skeleton")
public class AnalyseSkeletonWrapper extends BoneJCommand {

	static {
		LegacyInjector.preinit();
	}

	@Parameter(label = "Input image", validater = "validateImage",
		persist = false)
	private ImagePlus inputImage;

	@Parameter(label = "Cycle pruning method",
		description = "Which method is used to prune cycles in the skeleton graph",
		required = false, style = ChoiceWidget.LIST_BOX_STYLE, choices = { "None",
			"Shortest branch", "Lowest intensity voxel", "Lowest intensity branch" })
	private String pruneCycleMethod = "None";

	@Parameter(label = "Prune ends",
		description = "Prune very short edges with no slabs", required = false)
	private boolean pruneEnds;

	@Parameter(label = "Exclude ROI from pruning",
		description = "Exclude the current selection from pruning",
		required = false, visibility = ItemVisibility.INVISIBLE, persist = false)
	private boolean excludeRoi;

	@Parameter(label = "Calculate largest shortest paths",
		description = "Calculate and display the largest shortest skeleton paths",
		required = false)
	private boolean calculateShortestPaths;

	@Parameter(label = "Show detailed info",
		description = "Show detailed branch info in an additional table",
		required = false)
	private boolean verbose;

	@Parameter(label = "Display labelled skeletons",
		description = "Show skeleton images labelled with their IDs",
		required = false)
	private boolean displaySkeletons;

	/**
	 * Additional analysis details in a {@link DefaultGenericTable}, null if
	 * {@link #verbose} is false, or there are no results.
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Branch information")
	private DefaultGenericTable verboseTable;

	/**
	 * The labelled skeletons image, null if user didn't check the
	 * {@link #displaySkeletons} option.
	 */
	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus labelledSkeleton;

	/**
	 * The shortest paths image, null if user didn't check the
	 * {@link #displaySkeletons} option.
	 */
	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus shortestPaths;

	@Parameter
	private UIService uiService;

	@Parameter
	private IOService ioService;

	@Parameter
	private ConvertService convertService;

	@Parameter
	private FormatService formatService;

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	private ImagePlus intensityImage;

	@Override
	public void run() {
		if (isIntensityNeeded()) {
			openIntensityImage();
			if (intensityImage == null) {
				return;
			}
		}
		statusService.showStatus("Analyse skeleton: skeletonising");
		final ImagePlus skeleton = skeletonise(inputImage);
		final int pruneIndex = mapPruneCycleMethod(pruneCycleMethod);
		final AnalyzeSkeleton_ analyzeSkeleton_ = new AnalyzeSkeleton_();
		final Roi roi = excludeRoi ? inputImage.getRoi() : null;
		analyzeSkeleton_.setup("", skeleton);
		statusService.showStatus("Analyse skeleton: analysing skeletons");
		// "Silent" parameter cannot be controlled by the user in the original
		// plugin. We set it "true" so that no images pop open
		final SkeletonResult results = analyzeSkeleton_.run(pruneIndex, pruneEnds,
				calculateShortestPaths, intensityImage, true, verbose, roi);
		if (hasNoSkeletons(analyzeSkeleton_)) {
			cancelMacroSafe(this, NO_SKELETONS);
			return;
		}
		showResults(results);
		showAdditionalResults(results);
		if (displaySkeletons) {
			final ImageStack labelledStack = analyzeSkeleton_.getResultImage(false);
			labelledSkeleton = new ImagePlus(inputImage.getTitle() +
				"-labelled-skeletons", labelledStack);
			labelledSkeleton.setCalibration(inputImage.getCalibration());
			if (calculateShortestPaths) {
				final ImageStack stack = analyzeSkeleton_.getResultImage(true);
				final String title = inputImage.getShortTitle() + "-shortest-paths";
				shortestPaths = new ImagePlus(title, stack);
				shortestPaths.setCalibration(inputImage.getCalibration());
			}
		}
		reportUsage();
	}

	private boolean hasNoSkeletons(final AnalyzeSkeleton_ analyzeSkeleton_) {
		final Graph[] graphs = analyzeSkeleton_.getGraphs();
		return graphs == null || graphs.length == 0;
	}

	private boolean isIntensityNeeded() {
		final int i = mapPruneCycleMethod(pruneCycleMethod);
		return i == AnalyzeSkeleton_.LOWEST_INTENSITY_BRANCH ||
			i == AnalyzeSkeleton_.LOWEST_INTENSITY_VOXEL;
	}

	private boolean isValidIntensityImage(final Dataset dataset) {
		// NB Composite channel count is a hacky way to check if the image is
		// greyscale. It doesn't not correspond with the number of channels in the
		// image
		final int compositeChannelCount = dataset.getCompositeChannelCount();
		if (compositeChannelCount != 1 || dataset.getValidBits() != 8) {
			cancelMacroSafe(this, "The intensity image needs to be 8-bit greyscale");
			return false;
		}
		if (AxisUtils.hasTimeDimensions(dataset)) {
			cancelMacroSafe(this, "The intensity image can't have a time dimension");
			return false;
		}
		if (AxisUtils.hasChannelDimensions(dataset)) {
			cancelMacroSafe(this, "The intensity image can't have a channel dimension");
			return false;
		}
		if (AxisUtils.countSpatialDimensions(dataset) != inputImage
			.getNDimensions())
		{
			cancelMacroSafe(this,
				"The intensity image should match the dimensionality of the input image");
			return false;
		}
		return true;
	}

	private int mapPruneCycleMethod(final String pruneCycleMethod) {
		switch (pruneCycleMethod) {
			case "None":
				return AnalyzeSkeleton_.NONE;
			case "Shortest branch":
				return AnalyzeSkeleton_.NONE;
			case "Lowest intensity voxel":
				return AnalyzeSkeleton_.LOWEST_INTENSITY_VOXEL;
			case "Lowest intensity branch":
				return AnalyzeSkeleton_.LOWEST_INTENSITY_BRANCH;
			default:
				throw new IllegalArgumentException("Unexpected prune cycle method");
		}
	}

	// region -- Helper methods --
	private void openIntensityImage() {
		// (String, File, String) variant throws UnsupportedOperationException
		final File file = uiService.chooseFile(new File(inputImage.getTitle()),
			FileWidget.OPEN_STYLE);
		if (file == null) {
			// User pressed cancel on dialog
			return;
		}
		try {
			FileLocation location = new FileLocation(file.getAbsolutePath()); 
			formatService.getFormat(location);
			final Dataset dataset = (Dataset) ioService.open(file.getAbsolutePath());
			if (!isValidIntensityImage(dataset)) {
				return;
			}
			if (!convertService.supports(dataset, ImagePlus.class)) {
				cancelMacroSafe(this, "Intensity image could not be converted into an ImagePlus");
				return;
			}
			intensityImage = convertService.convert(dataset, ImagePlus.class);
		}
		catch (final FormatException e) {
			cancelMacroSafe(this, "Image format is not recognized");
			logService.trace(e);
		}
		catch (final IOException | NullPointerException e) {
			cancelMacroSafe(this, "An error occurred while opening the image");
			logService.trace(e);
		}
	}

	private void showAdditionalResults(final SkeletonResult results) {
		if (!verbose) {
			return;
		}
		final DefaultGenericTable table = new DefaultGenericTable();
		final List<PrimitiveColumn<?, ?>> columns = Arrays.asList(new IntColumn(
			"# Skeleton"), new IntColumn("# Branch"), new DoubleColumn(
				"Branch length"), new DoubleColumn("V1 x"), new DoubleColumn("V1 y"),
			new DoubleColumn("V1 z"), new DoubleColumn("V2 x"), new DoubleColumn("V2 y"),
			new DoubleColumn("V2 z"), new DoubleColumn("Euclidean distance"),
			new DoubleColumn("running average length"), new DoubleColumn(
				"average intensity (inner 3rd)"), new DoubleColumn(
					"average intensity"));
		final Graph[] graphs = results.getGraph();
		final Calibration cal = inputImage.getCalibration();
		for (int i = 0; i < graphs.length; i++) {
			final ArrayList<Edge> edges = graphs[i].getEdges();
			// Sort into descending order by length
			edges.sort((a, b) -> -Double.compare(a.getLength(), b.getLength()));
			for (int j = 0; j < edges.size(); j++) {
				final Edge edge = edges.get(j);
				((IntColumn) columns.get(0)).add((i + 1));
				((IntColumn) columns.get(1)).add((j + 1));
				((DoubleColumn) columns.get(2)).add((edge.getLength()));
				final Point point = edge.getV1().getPoints().get(0);
				((DoubleColumn) columns.get(3)).add((point.x) * cal.pixelWidth);
				((DoubleColumn) columns.get(4)).add((point.y) * cal.pixelHeight);
				((DoubleColumn) columns.get(5)).add((point.z) * cal.pixelDepth);
				final Point point2 = edge.getV2().getPoints().get(0);
				((DoubleColumn) columns.get(6)).add((point2.x) * cal.pixelWidth);
				((DoubleColumn) columns.get(7)).add((point2.y) * cal.pixelHeight);
				((DoubleColumn) columns.get(8)).add((point2.z) * cal.pixelDepth);
				final double distance = MathArrays.distance(
					new double[] { point.x * cal.pixelWidth,
						point.y * cal.pixelHeight, point.z * cal.pixelDepth },
					new double[] { point2.x * cal.pixelWidth, 
						point2.y * cal.pixelHeight, point2.z * cal.pixelDepth });
				((DoubleColumn) columns.get(9)).add((distance));
				((DoubleColumn) columns.get(10)).add((edge.getLength_ra()));
				((DoubleColumn) columns.get(11)).add((edge.getColor3rd()));
				((DoubleColumn) columns.get(12)).add((edge.getColor()));
			}
		}
		table.addAll(columns);
		verboseTable = table;
	}

	private void showResults(final SkeletonResult results) {
		final String[] headers = { "# Skeleton", "# Branches", "# Junctions",
			"# End-point voxels", "# Junction voxels", "# Slab voxels",
			"Average Branch Length", "# Triple points", "# Quadruple points",
			"Maximum Branch Length", "Longest Shortest Path", "spx", "spy", "spz" };

		final String label = inputImage.getTitle();
		for (int i = 0; i < results.getNumOfTrees(); i++) {
			SharedTable.add(label, headers[0], i + 1);
			SharedTable.add(label, headers[1], results.getBranches()[i]);
			SharedTable.add(label, headers[2], results.getJunctions()[i]);
			SharedTable.add(label, headers[3], results.getEndPoints()[i]);
			SharedTable.add(label, headers[4], results.getJunctions()[i]);
			SharedTable.add(label, headers[5], results.getSlabs()[i]);
			SharedTable.add(label, headers[6], results.getAverageBranchLength()[i]);
			SharedTable.add(label, headers[7], results.getTriples()[i]);
			SharedTable.add(label, headers[8], results.getQuadruples()[i]);
			SharedTable.add(label, headers[9], results.getMaximumBranchLength()[i]);
			if (results.getShortestPathList() == null) {
				continue;
			}
			SharedTable.add(label, headers[10], results.getShortestPathList().get(i));
			SharedTable.add(label, headers[11], results.getSpStartPosition()[i][0]);
			SharedTable.add(label, headers[12], results.getSpStartPosition()[i][1]);
			SharedTable.add(label, headers[13], results.getSpStartPosition()[i][2]);
		}
		resultsTable = SharedTable.getTable();
	}

	private void showSkeleton(final Skeletonize3D_ skeletoniser,
		final ImagePlus skeleton)
	{
		final int iterations = skeletoniser.getThinningIterations();
		if (iterations > 1) {
			skeleton.setTitle("Skeleton of " + inputImage.getTitle());
			uiService.show(skeleton);
		}
	}

	/**
	 * Run {@link Skeletonize3D_} on the input image
	 * <p>
	 * I know of no way to check if the given image is already a skeleton, and
	 * {@link AnalyzeSkeleton_} runs for a very long time if it's not. Thus we
	 * skeletonise just in case.
	 * </p>
	 */
	private ImagePlus skeletonise(final ImagePlus inputImage) {
		final ImagePlus skeleton = cleanDuplicate(inputImage);
		final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);
		showSkeleton(skeletoniser, skeleton);
		return skeleton;
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}

		if (!ImagePlusUtil.isBinaryColour(inputImage) || inputImage
			.getBitDepth() != 8)
		{
			// AnalyzeSkeleton_ and Skeletonize_ cast to byte[], anything else than
			// 8-bit will crash
			cancelMacroSafe(this, NOT_8_BIT_BINARY_IMAGE);
			return;
		}

		if (inputImage.getNChannels() > 1) {
			cancelMacroSafe(this, HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
			return;
		}
		if (inputImage.getNFrames() > 1) {
			cancelMacroSafe(this, HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
		}
	}

	// endregion
}
