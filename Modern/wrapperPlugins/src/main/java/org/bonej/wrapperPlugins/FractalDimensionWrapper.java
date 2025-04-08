/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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

import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops.Morphology.Outline;
import net.imagej.ops.Ops.Topology.BoxCount;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCF;
import net.imagej.ops.special.hybrid.Hybrids;
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
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultGenericTable;
import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericTable;
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
public class FractalDimensionWrapper<T extends RealType<T> & NativeType<T>> extends BoneJCommand
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

	@Parameter(label = "Automatic parameters",
		description = "Let the computer decide values for the parameters",
		required = false, callback = "enforceAutoParam", persist = false,
		initializer = "initAutoParam")
	private boolean autoParam;

	@Parameter(label = "Show points",
		description = "Show (log(size), -log(count)) points", required = false)
	private boolean showPoints;

	/**
	 * Table containing the (-log(size), log(count)) points for each 3D subspace
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "Subspace points")
	private GenericTable pointsTable;

	@Parameter
	private OpService opService;
	@Parameter
	private StatusService statusService;

	private BinaryHybridCF<RandomAccessibleInterval<BitType>, Boolean, RandomAccessibleInterval<BitType>> hollowOp;
	private UnaryFunctionOp<RandomAccessibleInterval<BitType>, List<ValuePair<DoubleType, DoubleType>>> boxCountOp;
	private long autoMax;

	@Override
	public void run() {
		statusService.showStatus("Fractal dimension: initialising");

		final List<Double> dimensions = new ArrayList<>();
		final List<Double> rSquared = new ArrayList<>();
		subspaces = find3DSubspaces(inputImage);
		matchOps(subspaces.get(0).interval);
		subspaces.forEach(subspace -> {
			final RandomAccessibleInterval<BitType> interval = subspace.interval;
			statusService.showProgress(0, 3);
			statusService.showStatus("Fractal dimension: hollowing bone");
			final RandomAccessibleInterval<BitType> outlines = hollowOp.calculate(
				interval);
			statusService.showProgress(1, 3);
			statusService.showStatus("Fractal dimension: counting boxes");
			final List<ValuePair<DoubleType, DoubleType>> pairs = boxCountOp
				.calculate(outlines);
			statusService.showProgress(2, 3);
			statusService.showStatus("Fractal dimension: fitting curve");
			dimensions.add(fitCurve(pairs)[1]);
			rSquared.add(getRSquared(pairs));
			if (showPoints) {
				writePoints(subspace.toString(), pairs);
			}
			statusService.showProgress(3, 3);
		});
		fillResultsTable(subspaces, dimensions, rSquared);
		resultsTable = SharedTable.getTable();
	}

	// region -- Helper methods --
	private void writePoints(final String headerSuffix,
							 final Collection<ValuePair<DoubleType, DoubleType>> points)
	{
		if (pointsTable == null) {
			pointsTable = new DefaultGenericTable();
		}
		String sizeHeader = "-log(size)";
		String countHeader= "log(count)";
		if (!headerSuffix.isEmpty()) {
			sizeHeader = sizeHeader + " " + headerSuffix;
			countHeader = countHeader + " " + headerSuffix;
		}
		final DoubleColumn xColumn = new DoubleColumn(sizeHeader);
		final DoubleColumn yColumn = new DoubleColumn(countHeader);
		points.stream().map(p -> p.a.get()).forEach(xColumn::add);
		points.stream().map(p -> p.b.get()).forEach(yColumn::add);
		pointsTable.add(xColumn);
		pointsTable.add(yColumn);
	}

	private boolean allValuesFinite(
		final Collection<ValuePair<DoubleType, DoubleType>> pairs)
	{
		final Stream<Double> xValues = pairs.stream().map(p -> p.a.get());
		final Stream<Double> yValues = pairs.stream().map(p -> p.b.get());
		return Stream.concat(xValues, yValues).allMatch(Double::isFinite);
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
		final Collection<ValuePair<DoubleType, DoubleType>> pairs)
	{
		if (!allValuesFinite(pairs)) {
			return new double[2];
		}
		final WeightedObservedPoints points = toWeightedObservedPoints(pairs);
		final PolynomialCurveFitter curveFitter = PolynomialCurveFitter.create(1);
		return curveFitter.fit(points.toList());
	}

	private double getRSquared(
		final Iterable<ValuePair<DoubleType, DoubleType>> pairs)
	{
		final SimpleRegression regression = new SimpleRegression();
		pairs.forEach(pair -> regression.addData(pair.a.get(), pair.b.get()));
		return regression.getRSquare();
	}

	@SuppressWarnings("unused")
	private void initAutoParam() {
		if (inputImage == null) {
			return;
		}
		final long[] dimensions = new long[inputImage.numDimensions()];
		inputImage.dimensions(dimensions);
		final long maxDimension = Arrays.stream(dimensions).max().orElse(0);
		autoMax = maxDimension / 4;
	}

	@SuppressWarnings("unchecked")
	private void matchOps(final RandomAccessibleInterval<BitType> input) {
		hollowOp = (BinaryHybridCF) Hybrids.binaryCF(opService, Outline.class,
			RandomAccessibleInterval.class, input, true);
		boxCountOp = (UnaryFunctionOp) Functions.unary(opService, BoxCount.class,
			List.class, input, startBoxSize, smallestBoxSize, scaleFactor,
			translations);
	}

	private WeightedObservedPoints toWeightedObservedPoints(
		final Iterable<ValuePair<DoubleType, DoubleType>> pairs)
	{
		final WeightedObservedPoints points = new WeightedObservedPoints();
		pairs.forEach(pair -> points.add(pair.a.get(), pair.b.get()));
		return points;
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}
		if (!ElementUtil.isBinary(inputImage)) {
			cancelMacroSafe(this, NOT_BINARY);
		}
	}

	// endregion
}
