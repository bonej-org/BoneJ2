
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCF;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imagej.table.DefaultColumn;
import net.imagej.table.DefaultGenericTable;
import net.imagej.table.DoubleColumn;
import net.imagej.table.GenericColumn;
import net.imagej.table.GenericTable;
import net.imagej.table.Table;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;

import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.NumberWidget;

/**
 * This command estimates the fractal dimension of a binary image with the
 * box-counting algorithm. Boxes of diminishing size are scanned over the image
 * and the number of boxes of each size containing foreground (bone) is counted.
 * As the box size decreases, the proportion of boxes containing foreground
 * increases in a fractal structure.
 * <p>
 * The command returns a table of fractal dimension and R² values. Fractal
 * dimension is the slope of a linear line fit to (-log(size), log(count))
 * points returned by the box count algorithm. R² is the goodness of the fit of
 * the linear line. Optionally the points are also returned in separate
 * table(s).
 * </p>
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Fractal dimension")
public class FractalDimensionWrapper<T extends RealType<T> & NativeType<T>>
	extends ContextCommand
{

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	@Parameter(label = "Starting box size (px)",
		description = "The size of the sampling boxes in the first iteration",
		min = "1", callback = "enforceValidSizes",
		style = NumberWidget.SPINNER_STYLE)
	private long startBoxSize = 48;

	@Parameter(label = "Smallest box size (px)",
		description = "Sampling box size where algorithm stops", min = "1",
		callback = "enforceValidSizes", style = NumberWidget.SPINNER_STYLE)
	private long smallestBoxSize = 6;

	@Parameter(label = "Box scaling factor",
		description = "The scale down factor of the box size after each step",
		min = "1.001", stepSize = "0.01", callback = "enforceAutoParam",
		style = NumberWidget.SPINNER_STYLE)
	private double scaleFactor = 1.2;

	@Parameter(label = "Grid translations",
		description = "How many times box grid is moved to find the best fit",
		min = "0", style = NumberWidget.SPINNER_STYLE,
		callback = "enforceAutoParam", required = false)
	private long translations;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String translationInfo =
		"NB: translations affect runtime significantly";

	// TODO persist = true and parameter enforcement in preview?
	@Parameter(label = "Automatic parameters",
		description = "Let the computer decide values for the parameters",
		required = false, callback = "enforceAutoParam", persist = false,
		initializer = "initAutoParam")
	private boolean autoParam = false;

	@Parameter(label = "Show points",
		description = "Show (log(size), -log(count)) points", required = false)
	private boolean showPoints = false;

	@Parameter(label = "Help", description = "Open help web page",
		callback = "openHelpPage")
	private Button helpButton;

	/**
	 * The fractal dimension and R² values for each 3D subspace in a table
	 * <p>
	 * Null if there are no results
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;

	/**
	 * Tables containing the (-log(size), log(count)) points for each 3D subspace
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Subspace points")
	private List<GenericTable> subspaceTables;

	@Parameter
	private OpService opService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private UIService uiService;

	@Parameter
	private PlatformService platformService;

	private BinaryHybridCF<RandomAccessibleInterval<BitType>, Boolean, RandomAccessibleInterval<BitType>> hollowOp;
	private UnaryFunctionOp<RandomAccessibleInterval<BitType>, List<ValuePair<DoubleType, DoubleType>>> boxCountOp;
	private long autoMax;

	@Override
	public void run() {
		statusService.showStatus("Fractal dimension: initialising");
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService,
			inputImage);
		matchOps(bitImgPlus);
		final List<Subspace<BitType>> subspaces = HyperstackUtils.split3DSubspaces(
			bitImgPlus).collect(Collectors.toList());
		final ArrayList<Double> dimensions = new ArrayList<>();
		final ArrayList<Double> rSquared = new ArrayList<>();
		subspaceTables = new ArrayList<>();
		subspaces.forEach(subspace -> {
			final RandomAccessibleInterval<BitType> interval = subspace.interval;
			statusService.showStatus("Fractal dimension: hollowing bone");
			final RandomAccessibleInterval<BitType> outlines = hollowOp.calculate(
				interval);
			statusService.showStatus("Fractal dimension: counting boxes");
			final List<ValuePair<DoubleType, DoubleType>> pairs = boxCountOp
				.calculate(outlines);
			statusService.showStatus("Fractal dimension: fitting curve");
			dimensions.add(fitCurve(pairs)[1]);
			rSquared.add(getRSquared(pairs));
			if (showPoints) {
				addSubspaceTable(subspace, pairs);
			}
		});
		fillResultsTable(subspaces, dimensions, rSquared);
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	// region -- Helper methods --

	private void addSubspaceTable(final Subspace<BitType> subspace,
		final List<ValuePair<DoubleType, DoubleType>> pairs)
	{
		final String label = inputImage.getName() + " " + subspace.toString();
		final GenericColumn labelColumn = ResultUtils.createLabelColumn(label, pairs
			.size());
		final DoubleColumn xColumn = new DoubleColumn("-log(size)");
		final DoubleColumn yColumn = new DoubleColumn("log(count)");
		pairs.stream().map(p -> p.a.get()).forEach(xColumn::add);
		pairs.stream().map(p -> p.b.get()).forEach(yColumn::add);
		final GenericTable resultsTable = new DefaultGenericTable();
		resultsTable.add(labelColumn);
		resultsTable.add(xColumn);
		resultsTable.add(yColumn);
		subspaceTables.add(resultsTable);
	}

	private void fillResultsTable(final List<Subspace<BitType>> subspaces,
		final List<Double> fractalDimensions, final List<Double> rSquared)
	{
		final String imageName = inputImage.getName();
		final int results = fractalDimensions.size();
		for (int i = 0; i < results; i++) {
			final String suffix = subspaces.get(i).toString();
			final String label = suffix.isEmpty() ? imageName : imageName + " " +
				suffix;
			SharedTable.add(label, "Fractal dimension", fractalDimensions.get(i));
			SharedTable.add(label, "R²", rSquared.get(i));
		}
	}

	private double[] fitCurve(
		final List<ValuePair<DoubleType, DoubleType>> pairs)
	{
		if (!allValuesFinite(pairs)) {
			return new double[2];
		}
		final WeightedObservedPoints points = toWeightedObservedPoints(pairs);
		final PolynomialCurveFitter curveFitter = PolynomialCurveFitter.create(1);
		return curveFitter.fit(points.toList());
	}

	private boolean allValuesFinite(
		final List<ValuePair<DoubleType, DoubleType>> pairs)
	{
		final Stream<Double> xValues = pairs.stream().map(p -> p.a.get());
		final Stream<Double> yValues = pairs.stream().map(p -> p.b.get());
		return Stream.concat(xValues, yValues).allMatch(Double::isFinite);
	}

	private double getRSquared(List<ValuePair<DoubleType, DoubleType>> pairs) {
		SimpleRegression regression = new SimpleRegression();
		pairs.forEach(pair -> regression.addData(pair.a.get(), pair.b.get()));
		return regression.getRSquare();
	}

	private WeightedObservedPoints toWeightedObservedPoints(
		final List<ValuePair<DoubleType, DoubleType>> pairs)
	{
		WeightedObservedPoints points = new WeightedObservedPoints();
		pairs.forEach(pair -> points.add(pair.a.get(), pair.b.get()));
		return points;
	}

    @SuppressWarnings("unused")
	private void initAutoParam() {
		if (inputImage == null) {
			return;
		}
		final long[] dimensions = new long[inputImage.numDimensions()];
		inputImage.dimensions(dimensions);
		final long maxDimension = Arrays.stream(dimensions).max().getAsLong();
		autoMax = maxDimension / 4;
	}

	@SuppressWarnings("unchecked")
	private void matchOps(final RandomAccessibleInterval<BitType> input) {
		hollowOp = (BinaryHybridCF) Hybrids.binaryCF(opService, Ops.Morphology.Outline.class,
			RandomAccessibleInterval.class, input, true);
		boxCountOp = (UnaryFunctionOp) Functions.unary(opService, Ops.Topology.BoxCount.class,
			List.class, input, startBoxSize, smallestBoxSize, scaleFactor,
			translations);
	}

    @SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}
		if (!ElementUtil.isColorsBinary(inputImage)) {
			cancel(NOT_BINARY);
		}
	}

	private void enforceAutoParam() {
		if (!autoParam) {
			return;
		}
		startBoxSize = autoMax;
		smallestBoxSize = Math.min(startBoxSize, 6L);
		scaleFactor = 1.2;
		translations = 0;
	}

	@SuppressWarnings("unused")
	private void enforceValidSizes() {
		if (smallestBoxSize > startBoxSize) {
			smallestBoxSize = startBoxSize;
		}
		enforceAutoParam();
	}

	@SuppressWarnings("unused")
	private void openHelpPage() {
		Help.openHelpPage("http://bonej.org/fractal", platformService, uiService,
			null);
	}
	// endregion
}
