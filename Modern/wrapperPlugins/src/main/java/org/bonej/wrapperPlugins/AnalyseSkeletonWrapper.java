/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2026 Michael Doube, BoneJ developers
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
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.CommonMessages.NO_SKELETONS;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;

import io.scif.FormatException;
import io.scif.services.FormatService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imagej.Dataset;
import net.imagej.axis.Axes;
import net.imagej.display.ColorTables;
import net.imagej.legacy.LegacyService;
import net.imagej.patcher.LegacyInjector;

import org.apache.commons.math3.util.MathArrays;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
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

	/** The binary 3D image to skeletonize and analyze. */
	@Parameter(label = "Input image", validater = "validateImage",
		persist = false)
	private Dataset inputDataset;

	/**
	 * Cycle pruning method for the skeleton graph.
	 * <ul>
	 *   <li>"None" — no pruning</li>
	 *   <li>"Shortest branch" — remove the shortest branch in each cycle</li>
	 *   <li>"Lowest intensity voxel" — break cycle at the darkest voxel</li>
	 *   <li>"Lowest intensity branch" — break cycle at the darkest branch</li>
	 * </ul>
	 * The latter two require an additional grayscale intensity image.
	 */
	@Parameter(label = "Cycle pruning method",
		description = "Which method is used to prune cycles in the skeleton graph",
		required = false, style = ChoiceWidget.LIST_BOX_STYLE, choices = { "None",
			"Shortest branch", "Lowest intensity voxel", "Lowest intensity branch" })
	private String pruneCycleMethod = "None";

	/** If true, prune terminal branches (very short edges with no slab voxels). */
	@Parameter(label = "Prune ends",
		description = "Prune very short edges with no slabs", required = false)
	private boolean pruneEnds;

	/**
	 * If true, the current ROI is excluded from end-branch pruning.
	 * Voxels inside the ROI are spared even if they are end-points.
	 * Ignored in headless/batch mode.
	 */
	@Parameter(label = "Exclude ROI from pruning",
		description = "Exclude the current selection from pruning",
		required = false, visibility = ItemVisibility.INVISIBLE, persist = false)
	private boolean excludeRoi;

	/**
	 * If true, compute the longest shortest path (graph diameter) for each
	 * skeleton tree using the Floyd–Warshall algorithm.
	 * Populates the {@link #shortestPaths} output image and the
	 * "Longest Shortest Path" / "spx" / "spy" / "spz" columns in the results table.
	 */
	@Parameter(label = "Calculate largest shortest paths",
		description = "Calculate and display the largest shortest skeleton paths",
		required = false)
	private boolean calculateShortestPaths;

	/**
	 * If true, populate the {@link #verboseTable} with per-branch details
	 * (lengths, vertex coordinates, intensities, Euclidean distances).
	 */
	@Parameter(label = "Show detailed info",
		description = "Show detailed branch info in an additional table",
		required = false)
	private boolean verbose;

	/**
	 * If true, populate the {@link #taggedImage} and {@link #treeLabeledImage}
	 * output images. If false, both will be null (saving memory for batch jobs
	 * that only need numeric results).
	 */
	@Parameter(label = "Display skeleton images",
		description = "Output topology-tagged and tree-labeled skeleton images",
		required = false)
	private boolean displaySkeletons;

	/**
	 * Topology-tagged skeleton image (8-bit).
	 * <p>
	 * Each foreground voxel is labeled with its topological role in the
	 * skeleton graph:
	 * </p>
	 * <ul>
	 *   <li>{@code 0} — background</li>
	 *   <li>{@code 30} ({@link AnalyzeSkeleton_#END_POINT END_POINT}) —
	 *       terminal endpoint (1 neighbor)</li>
	 *   <li>{@code 70} ({@link AnalyzeSkeleton_#JUNCTION JUNCTION}) —
	 *       junction vertex (3+ neighbors)</li>
	 *   <li>{@code 127} ({@link AnalyzeSkeleton_#SLAB SLAB}) —
	 *       slab voxel (exactly 2 neighbors)</li>
	 * </ul>
	 * <p>
	 * Source: {@code AnalyzeSkeleton_.getResultImage(false)} which returns
	 * the internal {@code taggedImage} stack.
	 * </p>
	 * <p>
	 * Null if {@link #displaySkeletons} is false.
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Tagged topology skeleton")
	private Dataset taggedImage;

	/**
	 * Tree-labeled skeleton image (float32).
	 * <p>
	 * Each foreground voxel is assigned the integer ID of the skeleton tree
	 * (connected component) it belongs to. Background is 0.
	 * </p>
	 * <ul>
	 *   <li>{@code 0.0} — background</li>
	 *   <li>{@code 1.0} — first skeleton tree</li>
	 *   <li>{@code 2.0} — second skeleton tree</li>
	 *   <li>{@code N.0} — N-th skeleton tree</li>
	 * </ul>
	 * <p>
	 * Stored as float32 (not uint8) because the number of distinct trees
	 * can exceed 255. Float can exactly represent integer IDs up to
	 * 2&#x2074;&#x00B2; without loss of precision.
	 * </p>
	 * <p>
	 * Source: {@code AnalyzeSkeleton_.getLabeledSkeletons()} which returns
	 * the internal {@code labeledSkeletons} stack.
	 * </p>
	 * <p>
	 * <b>Naming note:</b> This corresponds to the field called
	 * {@code labeledSkeletons} inside {@code AnalyzeSkeleton_}. It is
	 * <em>not</em> the same as the tagged image (confusingly, the original
	 * wrapper named its output {@code labelledSkeleton} but it actually held
	 * the tagged image). This field is now renamed to avoid that ambiguity.
	 * </p>
	 * <p>
	 * Null if {@link #displaySkeletons} is false.
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Tree-labeled skeleton")
	private Dataset treeLabeledImage;

	/**
	 * Longest-shortest-path image (8-bit).
	 * <p>
	 * A copy of the input skeleton stack where voxels lying on the
	 * longest shortest path (graph diameter) of each tree are overwritten
	 * with value {@code 96} ({@link AnalyzeSkeleton_#SHORTEST_PATH SHORTEST_PATH}).
	 * All other skeleton voxels retain their original intensity.
	 * </p>
	 * <p>
	 * Source: {@code AnalyzeSkeleton_.getResultImage(true)} which returns
	 * the internal {@code shortPathImage} stack.
	 * </p>
	 * <p>
	 * Null if {@link #calculateShortestPaths} is false.
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Shortest paths")
	private Dataset shortestPaths;

	/**
	 * Per-branch detail table (only populated if {@link #verbose} is true).
	 * <p>
	 * Columns: {@code # Skeleton}, {@code # Branch}, {@code Branch length},
	 * {@code V1 x}, {@code V1 y}, {@code V1 z}, {@code V2 x}, {@code V2 y},
	 * {@code V2 z}, {@code Euclidean distance}, {@code running average length},
	 * {@code average intensity (inner 3rd)}, {@code average intensity}.
	 * </p>
	 * <p>
	 * Null if {@link #verbose} is false.
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Branch information")
	private DefaultGenericTable verboseTable;

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

	@Parameter
	private LegacyService legacyService;

	private ImagePlus intensityImage;

	@Override
	public void run() {
		//stop if validation failed and plugin was cancelled
		if (isCanceled()) return;
		if (isIntensityNeeded()) {
			openIntensityImage();
			if (intensityImage == null) {
				return;
			}
		}
		
		ImagePlus imp = convertService.convert(inputDataset, ImagePlus.class);
		
		if (imp == null) {
			logService.error("AnalyseSkeleton: Failed to convert Dataset to ImagePlus.");
			return;
		}
		
		statusService.showStatus("Analyse skeleton: skeletonising");
		final ImagePlus skeleton = skeletonise(imp);
		final int pruneIndex = mapPruneCycleMethod(pruneCycleMethod);
		final AnalyzeSkeleton_ analyzeSkeleton_ = new AnalyzeSkeleton_();
		final Roi roi = excludeRoi ? imp.getRoi() : null;
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
			// --- Tree-labeled image (float32, IDs 1, 2, 3...) ---
			final ImageStack treeLabeledStack = analyzeSkeleton_.getLabeledSkeletons();
			if (treeLabeledStack != null) {
				ImagePlus treeLabeledImp = new ImagePlus(imp.getTitle() +
					"-tree-labels", treeLabeledStack);
				treeLabeledImp.setCalibration(imp.getCalibration());
				treeLabeledImage = convertService.convert(treeLabeledImp, Dataset.class);
				treeLabeledImp.close();
				treeLabeledImage.getImgPlus().setColorTable(ColorTables.FIRE, 0);
				if (uiService != null && uiService.isVisible())
					uiService.show(treeLabeledImage);
			}

			// --- Tagged topology image (8-bit, values 0, 30, 70, 127) ---
			final ImageStack taggedStack = analyzeSkeleton_.getResultImage(false);
			ImagePlus taggedSkeleton = new ImagePlus(imp.getTitle() +
				"-tagged-skeletons", taggedStack);
			taggedSkeleton.setCalibration(imp.getCalibration());
			taggedImage = convertService.convert(taggedSkeleton, Dataset.class);
			taggedSkeleton.close();
			taggedImage.getImgPlus().setColorTable(ColorTables.FIRE, 0);
			if (uiService != null && uiService.isVisible())
				uiService.show(taggedImage);

			if (calculateShortestPaths) {
				// --- Shortest path image (8-bit, path voxels = 96) ---
				final ImageStack stack = analyzeSkeleton_.getResultImage(true);
				final String title = imp.getShortTitle() + "-shortest-paths";
				ImagePlus shortPaths = new ImagePlus(title, stack);
				shortPaths.setCalibration(imp.getCalibration());
				shortestPaths = convertService.convert(shortPaths, Dataset.class);
				shortPaths.close();
				shortestPaths.getImgPlus().setColorTable(ColorTables.FIRE, 0);
				if (uiService != null && uiService.isVisible())
					uiService.show(shortestPaths);
			}
		}
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
		if (AxisUtils.countSpatialDimensions(dataset) != AxisUtils.countSpatialDimensions(inputDataset))
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
	    // 1. Determine the initial directory
	    File initialDirectory = null;
	    String sourceString = inputDataset.getSource();

	    if (sourceString != null && !sourceString.isEmpty()) {
	        try {
	            // Try to interpret the source string as a file path
	            File sourceFile = new File(sourceString);
	            
	            // If it's a valid file, use its parent directory
	            if (sourceFile.exists() && sourceFile.isFile()) {
	                initialDirectory = sourceFile.getParentFile();
	            } 
	            // If it's a directory itself (rare for a dataset source, but possible)
	            else if (sourceFile.isDirectory()) {
	                initialDirectory = sourceFile;
	            }
	        } catch (Exception e) {
	            // If parsing fails (e.g., it's a URL like "http://..."), ignore
	            logService.debug("Source string is not a local file path: " + sourceString);
	        }
	    }

	    // Fallback: If no source or parsing failed, use user home
	    if (initialDirectory == null) {
	        initialDirectory = new File(System.getProperty("user.home"));
	    }
	    
	    final File file = uiService.chooseFile(initialDirectory, FileWidget.OPEN_STYLE);
	    
		// (String, File, String) variant throws UnsupportedOperationException
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
		final double pixelWidth = AxisUtils.getScale(inputDataset, Axes.X);
		final double pixelHeight = AxisUtils.getScale(inputDataset, Axes.Y);
		final double pixelDepth = AxisUtils.getScale(inputDataset, Axes.Z);
		
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
				((DoubleColumn) columns.get(3)).add((point.x) * pixelWidth);
				((DoubleColumn) columns.get(4)).add((point.y) * pixelHeight);
				((DoubleColumn) columns.get(5)).add((point.z) * pixelDepth);
				final Point point2 = edge.getV2().getPoints().get(0);
				((DoubleColumn) columns.get(6)).add((point2.x) * pixelWidth);
				((DoubleColumn) columns.get(7)).add((point2.y) * pixelHeight);
				((DoubleColumn) columns.get(8)).add((point2.z) * pixelDepth);
				final double distance = MathArrays.distance(
					new double[] { point.x * pixelWidth,
						point.y * pixelHeight, point.z * pixelDepth },
					new double[] { point2.x * pixelWidth, 
						point2.y * pixelHeight, point2.z * pixelDepth });
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

		final String label = inputDataset.getName();
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
			skeleton.setTitle("Skeleton of " + inputDataset.getName());
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
		if (inputDataset == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}
		
		if (!ElementUtil.isIJ1Binary(inputDataset, 10000))
		{
			// AnalyzeSkeleton_ and Skeletonize_ cast to byte[], anything else than
			// 8-bit will crash
			cancelMacroSafe(this, NOT_BINARY);
			return;
		}

		if (inputDataset.dimension(Axes.CHANNEL) > 1) {
			cancelMacroSafe(this, HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
			return;
		}
		if (inputDataset.dimension(Axes.TIME) > 1) {
			cancelMacroSafe(this, HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
		}
	}
	// endregion
}
