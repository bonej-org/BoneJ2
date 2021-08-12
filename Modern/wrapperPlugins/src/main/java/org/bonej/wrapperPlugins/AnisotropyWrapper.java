/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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
import static java.util.stream.Stream.generate;
import static org.bonej.utilities.AxisUtils.isSpatialCalibrationsIsotropic;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imagej.ops.stats.regression.leastSquares.Quadric;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

import org.apache.commons.math3.random.RandomVectorGenerator;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.QuadricToEllipsoid;
import org.bonej.ops.mil.ParallelLineGenerator;
import org.bonej.ops.mil.ParallelLineMIL;
import org.bonej.ops.mil.PlaneParallelLineGenerator;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.utilities.Visualiser;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.NumberWidget;

/**
 * A command that analyses the degree of anisotropy in an image.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Anisotropy")
public class AnisotropyWrapper<T extends RealType<T> & NativeType<T>> extends BoneJCommand
{

	/**
	 * Generates four normally distributed values between [0, 1] that describe a
	 * unit quaternion. These can be used to create isotropically distributed
	 * rotations.
	 */
	private static final RandomVectorGenerator qGenerator =
		new UnitSphereRandomVectorGenerator(4);

	/**
	 * Default directions is 2_000 since that's roughly the number of points in
	 * Poisson distributed sampling that'd give points about 5 degrees apart).
	 */
	private static final int DEFAULT_DIRECTIONS = 2_000;
	// The default number of lines was found to be sensible after experimenting
	// with data at hand. Other data may need a different number.
	private static final int DEFAULT_LINES = 10_000;
	private static BinaryFunctionOp<RandomAccessibleInterval<BitType>, ParallelLineGenerator, Vector3d> milOp;
	private static UnaryFunctionOp<Matrix4dc, Optional<Ellipsoid>> quadricToEllipsoidOp;
	private static UnaryFunctionOp<List<Vector3dc>, Matrix4dc> solveQuadricOp;
	private final Function<Ellipsoid, Double> degreeOfAnisotropy =
			ellipsoid -> 1.0 - (1.0/(ellipsoid.getC() * ellipsoid.getC())) / (1.0/(ellipsoid.getA() * ellipsoid.getA()));
	@SuppressWarnings("unused")
	
	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;
	
	@Parameter(label = "Directions",
		description = "The number of times sampling is performed from different directions",
		min = "9", style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "applyMinimum")
	private Integer directions = DEFAULT_DIRECTIONS;
	
	@Parameter(label = "Lines per direction",
		description = "How many lines are sampled per direction",
		min = "1", style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "applyMinimum")
	private Integer lines = DEFAULT_LINES;
	private long sections;
	
	@Parameter(label = "Sampling increment", persist = false,
		description = "Distance between sampling points (in voxels)",
		style = NumberWidget.SPINNER_STYLE, required = false, stepSize = "0.1",
		callback = "incrementChanged", initializer = "initializeIncrement")
	private Double samplingIncrement;
	private double minIncrement;

	@Parameter(label = "Recommended minimums",
		description = "Apply minimum recommended values to directions, lines, and increment",
		persist = false, required = false, callback = "applyMinimum")
	private boolean recommendedMin;
	
	private boolean calibrationWarned;
	
	@Parameter(label = "Show radii",
		description = "Show the radii of the fitted ellipsoid in the results",
		required = false)
	private boolean printRadii;
	
	@Parameter(label = "Show Eigens",
		description = "Show the eigenvectors and eigenvalues of the fitted ellipsoid in the results",
		required = false)
	private boolean printEigens;
	
	@Parameter(label = "Display MIL vectors",
			description = "Show the vectors of the mean intercept lengths",
			required = false)
	private boolean displayMILVectors;
	
	@Parameter(label = "Help", description = "More about Anisotropy", callback = "showHelpPage")
	private Button button;

	@Parameter
	private LogService logService;
	@Parameter
	private StatusService statusService;
	@Parameter
	private OpService opService;
	@Parameter
	private UIService uiService;
	@Parameter
	private UnitService unitService;
	@Parameter
	private PlatformService platformService;
	private static BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;
	private double milLength;

	@Override
	public void run() {
		sections = (long) Math.sqrt(lines);
		statusService.showStatus("Anisotropy: initialising");
		subspaces = find3DSubspaces(inputImage);
		calculateMILLength(subspaces.get(0).interval);
		matchOps(subspaces.get(0).interval);
		final List<Ellipsoid> ellipsoids = new ArrayList<>();
		for (int i = 0; i < subspaces.size(); i++) {
			statusService.showStatus("Anisotropy: sampling subspace #" + (i + 1));
			final RandomAccessibleInterval<BitType> interval = subspaces.get(i).interval;
			final Ellipsoid ellipsoid = milEllipsoid(interval);
			if (ellipsoid == null) {
				return;
			}
			ellipsoids.add(ellipsoid);
		}
		addResults(subspaces, ellipsoids);
		resultsTable = SharedTable.getTable();
		reportUsage();
	}
	
	@SuppressWarnings("unused")
	private void showHelpPage() {
		Common.showHelpPage("#anisotropy", platformService, uiService, logService);
	}

	// region -- Helper methods --
	private void calculateMILLength(final RandomAccessibleInterval<BitType> interval) {
		final long[] dimensions = new long[interval.numDimensions()];
		interval.dimensions(dimensions);
		final double diagonal = Math.sqrt(Arrays.stream(dimensions).map(x -> x * x).sum());
		milLength = lines * diagonal;
	}

	private void addResult(final Subspace<BitType> subspace,
		final double anisotropy, final Ellipsoid ellipsoid)
	{
		final String imageName = inputImage.getName();
		final String suffix = subspace.toString();
		final String label = suffix.isEmpty() ? imageName : imageName + " " +
			suffix;
		SharedTable.add(label, "DA", anisotropy);
		if (printRadii) {
			SharedTable.add(label, "Radius a", ellipsoid.getA());
			SharedTable.add(label, "Radius b", ellipsoid.getB());
			SharedTable.add(label, "Radius c", ellipsoid.getC());
		}
		if (printEigens) {
			Matrix3d eigenVectors = new Matrix3d();
			ellipsoid.getOrientation().get3x3(eigenVectors);
			SharedTable.add(label, "m00", eigenVectors.m00);
			SharedTable.add(label, "m01", eigenVectors.m01);
			SharedTable.add(label, "m02", eigenVectors.m02);
			SharedTable.add(label, "m10", eigenVectors.m10);
			SharedTable.add(label, "m11", eigenVectors.m11);
			SharedTable.add(label, "m12", eigenVectors.m12);
			SharedTable.add(label, "m20", eigenVectors.m20);
			SharedTable.add(label, "m21", eigenVectors.m21);
			SharedTable.add(label, "m22", eigenVectors.m22);
			final double d1 = 1/(ellipsoid.getC() * ellipsoid.getC());
			final double d2 = 1/(ellipsoid.getB() * ellipsoid.getB());
			final double d3 = 1/(ellipsoid.getA() * ellipsoid.getA());
			SharedTable.add(label, "D1", d1);
			SharedTable.add(label, "D2", d2);
			SharedTable.add(label, "D3", d3);
		}
	}

	private void addResults(final List<Subspace<BitType>> subspaces,
		final List<Ellipsoid> ellipsoids)
	{
		statusService.showStatus("Anisotropy: showing results");
		for (int i = 0; i < subspaces.size(); i++) {
			final Subspace<BitType> subspace = subspaces.get(i);
			final Ellipsoid ellipsoid = ellipsoids.get(i);
			final double anisotropy = degreeOfAnisotropy.apply(ellipsoid);
			addResult(subspace, anisotropy, ellipsoid);
		}
	}

	@SuppressWarnings("unused")
	private void initializeIncrement() {
		/* TODO: Get calibration from inputimage.axis(int)
		   NB: you can't assume that 0, 1, 2 are X, Y, Z axes!
		   NB: axes can have different units of calibration
		 */
		final double px = 1.0;
		final double py = 1.0;
		final double pz = 1.0;
		final double diagonal = px * px + py * py + pz * pz;
		// Round to 2 decimal places
		minIncrement = Math.round(Math.sqrt(diagonal) * 100.0) / 100.0;
		if (samplingIncrement < minIncrement) {
			// Allow calling through commandService with a greater explicit parameter value,
			// e.g. commandService.run(AnisotropyWrapper.class, ... "samplingIncrement", 5.0)
			samplingIncrement = minIncrement;
		}
	}

	@SuppressWarnings("unused")
	private void incrementChanged() {
		if (recommendedMin || samplingIncrement < minIncrement) {
			samplingIncrement = minIncrement;
		}
	}

	@SuppressWarnings("unused")
	private void applyMinimum() {
		if (recommendedMin) {
			lines = DEFAULT_LINES;
			directions = DEFAULT_DIRECTIONS;
			samplingIncrement = minIncrement;
		}
	}

	private Optional<Ellipsoid> fitEllipsoid(final List<Vector3dc> pointCloud) {
		statusService.showStatus("Anisotropy: solving quadric equation");
		final Matrix4dc quadric = solveQuadricOp.calculate(pointCloud);
		statusService.showStatus("Anisotropy: fitting ellipsoid");
		return quadricToEllipsoidOp.calculate(quadric);
	}

	@SuppressWarnings("unchecked")
	private void matchOps(final RandomAccessibleInterval<BitType> interval) {
		final List<Vector3dc> tmpPoints = generate(Vector3d::new).limit(
			Quadric.MIN_DATA).collect(toList());
		solveQuadricOp = Functions.unary(opService, Quadric.class, Matrix4dc.class,
			tmpPoints);
		final Matrix4dc matchingMock = new Matrix4d();
		quadricToEllipsoidOp = (UnaryFunctionOp) Functions.unary(opService,
			QuadricToEllipsoid.class, Optional.class, matchingMock);
		rotateOp = Hybrids.binaryCFI1(opService, Rotate3d.class, Vector3d.class,
				new Vector3d(), new Quaterniond());
		ParallelLineGenerator generator =
				new PlaneParallelLineGenerator(interval, new Quaterniond(), rotateOp, sections);
		milOp = Functions.binary(opService, ParallelLineMIL.class, Vector3d.class,
				interval, generator, milLength, samplingIncrement);
	}

	private Ellipsoid milEllipsoid(final RandomAccessibleInterval<BitType> interval) {
		final List<Vector3dc> pointCloud;
		try {
			pointCloud = runDirectionsInParallel(interval);
			if (pointCloud.size() < Quadric.MIN_DATA) {
				cancelMacroSafe(this, "Anisotropy could not be calculated - too few points");
				return null;
			}
			final Optional<Ellipsoid> ellipsoid = fitEllipsoid(pointCloud);
			if (!ellipsoid.isPresent()) {
				cancelMacroSafe(this, "Anisotropy could not be calculated - ellipsoid fitting failed");
				return null;
			}
			if (displayMILVectors) {
				Visualiser.display3DPoints(pointCloud, "MIL points");
			}
			return ellipsoid.get();
		}
		catch (final ExecutionException | InterruptedException e) {
			logService.trace(e.getMessage());
			cancelMacroSafe(this, "The plug-in was interrupted");
		}
		return null;
	}

	private Callable<Vector3d> createMILTask(final RandomAccessibleInterval<BitType> interval) {
		// A random isotropically distributed quaternion
		final double[] v = qGenerator.nextVector();
		final Quaterniond quaternion = new Quaterniond(v[0], v[1], v[2], v[3]);
		final PlaneParallelLineGenerator generator =
				new PlaneParallelLineGenerator(interval, quaternion, rotateOp, sections);
		return () -> milOp.calculate(interval, generator);
	}

	private List<Vector3dc> runDirectionsInParallel(
		final RandomAccessibleInterval<BitType> interval) throws ExecutionException,
		InterruptedException
	{
		final int cores = Runtime.getRuntime().availableProcessors();
		// Anisotropy starts to slow down after more than n threads.
		// The 8 here is a magic number, but some upper bound is better than none.
		final int nThreads = Math.min(cores, 8);
		// I've tried running milOp with a parallel Stream, but for whatever reason it's slower.
		final ExecutorService executor = Executors.newFixedThreadPool(nThreads);
		final List<Future<Vector3d>> futures = generate(() -> createMILTask(interval)).limit(
			directions).map(executor::submit).collect(toList());
		final List<Vector3dc> pointCloud = new ArrayList<>(directions);
		int progress = 0;
		for (final Future<Vector3d> future : futures) {
			statusService.showProgress(progress, directions);
			pointCloud.add(future.get());
			progress++;
		}
		shutdownAndAwaitTermination(executor);
		return pointCloud;
	}

	// Shuts down an ExecutorService as per recommended by Oracle
	private void shutdownAndAwaitTermination(final ExecutorService executor) {
		executor.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				executor.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
					logService.trace("Pool did not terminate");
				}
			}
		}
		catch (final InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			executor.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
			logService.trace(ie);
		}
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}
		if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
			cancelMacroSafe(this, NOT_3D_IMAGE);
			return;
		}
		if (!ElementUtil.isBinary(inputImage)) {
			cancelMacroSafe(this, NOT_BINARY);
			return;
		}
		if (!isSpatialCalibrationsIsotropic(inputImage, 0.01, unitService) &&
			!calibrationWarned)
		{
			final Result result = uiService.showDialog(
				"The voxels in the image are anisotropic, which may affect results. Continue anyway?",
				WARNING_MESSAGE, OK_CANCEL_OPTION);
			// Avoid showing warning more than once (validator gets called before and
			// after dialog pops up..?)
			calibrationWarned = true;
			if (result != OK_OPTION) {
				cancel(null);
			}
		}
	}
	// endregion
}
