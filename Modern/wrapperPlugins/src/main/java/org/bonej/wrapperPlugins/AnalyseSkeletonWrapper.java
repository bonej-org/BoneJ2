
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.GOT_SKELETONISED;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_CHANNEL_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_TIME_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.CommonMessages.NO_SKELETONS;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;

import ij.IJ;
import io.scif.FormatException;
import io.scif.services.FormatService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imagej.patcher.LegacyInjector;
import net.imagej.table.DefaultColumn;
import net.imagej.table.DefaultGenericTable;
import net.imagej.table.DoubleColumn;
import net.imagej.table.IntColumn;
import net.imagej.table.PrimitiveColumn;
import net.imagej.table.Table;

import org.apache.commons.math3.util.MathArrays;
import org.bonej.utilities.ImagePlusCheck;
import org.bonej.utilities.SharedTable;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.convert.ConvertService;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.FileWidget;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
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
public class AnalyseSkeletonWrapper extends ContextCommand {

	static {
		LegacyInjector.preinit();
	}

	/**
	 * @implNote Use ImagePlus because of conversion issues of composite images
	 */
	@Parameter(label = "Input image", validater = "validateImage",
		persist = false)
	private ImagePlus inputImage;

	@Parameter(visibility = ItemVisibility.MESSAGE, columns = 1)
	private String loopSection = "-- LOOPS --";

	@Parameter(label = "Prune cycle method", required = false,
		style = ChoiceWidget.LIST_BOX_STYLE, choices = { "None", "Shortest branch",
			"Lowest intensity voxel", "Lowest intensity branch" })
	private String pruneCycleMethod = "None";

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String endPointSection = "-- END-POINTS --";

	@Parameter(label = "Prune ends", required = false)
	private boolean pruneEnds = false;

	@Parameter(label = "Exclude ROI from pruning", required = false,
		visibility = ItemVisibility.INVISIBLE)
	private boolean excludeRoi = false;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String resultSection = "-- RESULTS AND OUTPUT --";

	@Parameter(label = "Calculate largest shortest path", required = false)
	private boolean calculateShortestPath = false;

	@Parameter(label = "Show detailed info", required = false)
	private boolean verbose = false;

	@Parameter(label = "Display labelled skeletons", required = false)
	private boolean displaySkeletons = false;

	/**
	 * The skeleton results in a {@link Table}, null if there are no results
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;

	/**
	 * Additional analysis details in a {@link DefaultGenericTable}, null if
	 * {@link #verbose} is false, or there are no results.
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Branch information")
	private DefaultGenericTable verboseTable;

	/**
	 * The labelled skeletons image, null if user didn't check the
	 * {@link #displaySkeletons} option
	 */
	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus labelledSkeleton;

	/**
	 * The shortest paths image, null if user didn't check the
	 * {@link #displaySkeletons} option
	 */
	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus shortestPaths;

	@Parameter(label = "Help", description = "Open help web page",
		callback = "openHelpPage")
	private Button helpButton;

	@Parameter
	private UIService uiService;

	@Parameter
	private PlatformService platformService;

	@Parameter
	private IOService ioService;

	@Parameter
	private ConvertService convertService;

	@Parameter
	private FormatService formatService;

	@Parameter
	private LogService logService;

	private ImagePlus intensityImage = null;

	@Override
	public void run() {
		if (isIntensityNeeded()) {
			openIntensityImage();
			if (intensityImage == null) {
				return;
			}
		}
		final ImagePlus skeleton = skeletonise(inputImage);
		final int pruneIndex = mapPruneCycleMethod(pruneCycleMethod);
		final AnalyzeSkeleton_ analyzeSkeleton_ = new AnalyzeSkeleton_();
		final Roi roi = excludeRoi ? inputImage.getRoi() : null;
		analyzeSkeleton_.setup("", skeleton);
		// "Silent" parameter cannot be controlled by the user in the original
		// plugin. We set it "true" so that no images pop open explicitly
		final SkeletonResult results = analyzeSkeleton_.run(pruneIndex, pruneEnds,
			calculateShortestPath, intensityImage, true, verbose, roi);
		if (hasNoSkeletons(analyzeSkeleton_)) {
			cancel(NO_SKELETONS);
			return;
		}
		showResults(results);
		showAdditionalResults(results);
		if (displaySkeletons) {
			final ImageStack labelledStack = analyzeSkeleton_.getResultImage(false);
			labelledSkeleton = new ImagePlus(inputImage.getTitle() +
				"-labelled-skeletons", labelledStack);
			labelledSkeleton.setCalibration(inputImage.getCalibration());
			if (calculateShortestPath) {
				final ImageStack stack = analyzeSkeleton_.getResultImage(true);
				final String title = inputImage.getShortTitle() + "-shortest-paths";
				shortestPaths = new ImagePlus(title, stack);
				shortestPaths.setCalibration(inputImage.getCalibration());
			}
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
			formatService.getFormat(file.getAbsolutePath());
			final Object o = ioService.open(file.getAbsolutePath());
			if (!convertService.supports(o, ImagePlus.class)) {
				cancel("Intensity image could not be converted into an ImagePlus");
				return;
			}
			intensityImage = convertService.convert(o, ImagePlus.class);
		} catch (FormatException e) {
			cancel("Image format is not recognized");
			logService.trace(e);
			return;
		}
		catch (IOException | NullPointerException e) {
			cancel("An error occurred while opening the image");
			logService.trace(e);
			return;
		}
		if (intensityImage.getType() != ImagePlus.GRAY8) {
			//TODO FIX - type always GRAY8
			// AnalyzeSkeleton_ casts to byte[], anything else than 8-bit will crash
			intensityImage = null;
			cancel("The intensity image needs to be 8-bit greyscale");
		}
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
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	private boolean hasNoSkeletons(final AnalyzeSkeleton_ analyzeSkeleton_) {
		final Graph[] graphs = analyzeSkeleton_.getGraphs();
		return graphs == null || graphs.length == 0;
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
		final ImagePlus skeleton = inputImage.duplicate();
		final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);
		showSkeletonisationInfo(skeletoniser);
		return skeleton;
	}

	private void showSkeletonisationInfo(final Skeletonize3D_ skeletoniser) {
		final int iterations = skeletoniser.getThinningIterations();
		if (iterations > 1) {
			uiService.showDialog(GOT_SKELETONISED, INFORMATION_MESSAGE);
		}
	}

	private void showAdditionalResults(final SkeletonResult results) {
		if (!verbose) {
			return;
		}
		final DefaultGenericTable table = new DefaultGenericTable();
		final List<PrimitiveColumn<?, ?>> columns = Arrays.asList(new IntColumn(
			"# Skeleton"), new IntColumn("# Branch"), new DoubleColumn(
				"Branch length"), new IntColumn("V1 x"), new IntColumn("V1 y"),
			new IntColumn("V1 z"), new IntColumn("V2 x"), new IntColumn("V2 y"),
			new IntColumn("V2 z"), new DoubleColumn("Euclidean distance"),
			new DoubleColumn("running average length"), new DoubleColumn(
				"average intensity (inner 3rd)"), new DoubleColumn(
					"average intensity"));
		final Graph[] graphs = results.getGraph();
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
				((IntColumn) columns.get(3)).add((point.x));
				((IntColumn) columns.get(4)).add((point.y));
				((IntColumn) columns.get(5)).add((point.z));
				final Point point2 = edge.getV2().getPoints().get(0);
				((IntColumn) columns.get(6)).add((point2.x));
				((IntColumn) columns.get(7)).add((point2.y));
				((IntColumn) columns.get(8)).add((point2.z));
				final double distance = MathArrays.distance(new double[] { point.x,
					point.y, point.z }, new double[] { point2.x, point2.y, point2.z });
				((DoubleColumn) columns.get(9)).add((distance));
				((DoubleColumn) columns.get(10)).add((edge.getLength_ra()));
				((DoubleColumn) columns.get(11)).add((edge.getColor3rd()));
				((DoubleColumn) columns.get(12)).add((edge.getColor()));
			}
		}
		table.addAll(columns);
		if (table.size() > 0) {
			verboseTable = table;
		}
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

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}

		if (inputImage.getType() != ImagePlus.GRAY8 || !ImagePlusCheck
			.isBinaryColour(inputImage))
		{
			// AnalyzeSkeleton_ and Skeletonize_ cast to byte[], anything else than
			// 8-bit will crash
			cancel(NOT_8_BIT_BINARY_IMAGE);
			return;
		}

		if (inputImage.isComposite()) {
			cancel(HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
		}
		else if (inputImage.isHyperStack()) {
			cancel(HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
		}
	}

	private boolean isIntensityNeeded() {
		final int i = mapPruneCycleMethod(pruneCycleMethod);
		return i == AnalyzeSkeleton_.LOWEST_INTENSITY_BRANCH ||
			i == AnalyzeSkeleton_.LOWEST_INTENSITY_VOXEL;
	}

	@SuppressWarnings("unused")
	private void openHelpPage() {
		Help.openHelpPage("http://fiji.sc/wiki/index.php/AnalyzeSkeleton",
			platformService, uiService, logService);
	}
	// endregion
}
