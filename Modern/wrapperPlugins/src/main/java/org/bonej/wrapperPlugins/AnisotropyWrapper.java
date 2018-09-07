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
import static org.bonej.utilities.AxisUtils.getSpatialUnit;
import static org.bonej.utilities.Streamers.spatialAxisStream;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.ops.stats.regression.leastSquares.Quadric;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

import org.apache.commons.math3.random.RandomVectorGenerator;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.QuadricToEllipsoid;
import org.bonej.ops.mil.MILPlane;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

/**
 * A command that analyses the degree of anisotropy in an image.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Anisotropy")
public class AnisotropyWrapper<T extends RealType<T> & NativeType<T>> extends
	ContextCommand
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
	private static final int DEFAULT_LINES = 100;
	private static final double DEFAULT_INCREMENT = 1.0;
	private static BinaryFunctionOp<RandomAccessibleInterval<BitType>, Quaterniondc, Vector3d> milOp;
	private static UnaryFunctionOp<Matrix4dc, Optional<Ellipsoid>> quadricToEllipsoidOp;
	private static UnaryFunctionOp<List<Vector3d>, Matrix4dc> solveQuadricOp;
	private final Function<Ellipsoid, Double> degreeOfAnisotropy =
		ellipsoid -> 1.0 - ellipsoid.getA() / ellipsoid.getC();
	@SuppressWarnings("unused")
	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;
	@Parameter(label = "Directions",
		description = "The number of times sampling is performed from different directions",
		min = "9", style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "applyMinimum")
	private Integer directions = DEFAULT_DIRECTIONS;
	@Parameter(label = "Lines per dimension",
		description = "How many sampling lines are projected in both 2D directions (this number squared)",
		min = "1", style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "applyMinimum")
	private Integer lines = DEFAULT_LINES;
	@Parameter(label = "Sampling increment", min = "0.01",
		description = "Distance between sampling points (in voxels)",
		style = NumberWidget.SPINNER_STYLE, required = false, stepSize = "0.1",
		callback = "applyMinimum")
	private Double samplingIncrement = DEFAULT_INCREMENT;
	@Parameter(label = "Recommended minimum",
		description = "Apply minimum recommended values to directions, lines, and increment",
		persist = false, required = false, callback = "applyMinimum")
	private boolean recommendedMin;
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String instruction =
		"NB parameter values can affect results significantly";
	private boolean calibrationWarned;
	@Parameter(label = "Show radii",
		description = "Show the radii of the fitted ellipsoid in the results",
		required = false)
	private boolean printRadii;

	/**
	 * The anisotropy results in a {@link Table}.
	 * <p>
	 * Null if there are no results.
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;
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

	@Override
	public void run() {
		statusService.showStatus("Anisotropy: initialising");
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService,
			inputImage);
		final List<Subspace<BitType>> subspaces = HyperstackUtils.split3DSubspaces(
			bitImgPlus).collect(toList());
		matchOps(subspaces.get(0));
		final List<Ellipsoid> ellipsoids = new ArrayList<>();
		for (int i = 0; i < subspaces.size(); i++) {
			statusService.showStatus("Anisotropy: sampling subspace #" + (i + 1));
			final Subspace<BitType> subspace = subspaces.get(i);
			final Ellipsoid ellipsoid = milEllipsoid(subspace);
			if (ellipsoid == null) {
				return;
			}
			ellipsoids.add(ellipsoid);
		}
		addResults(subspaces, ellipsoids);
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	// region -- Helper methods --

	private void addResult(final Subspace<BitType> subspace,
		final double anisotropy, final Ellipsoid ellipsoid)
	{
		final String imageName = inputImage.getName();
		final String suffix = subspace.toString();
		final String label = suffix.isEmpty() ? imageName : imageName + " " +
			suffix;
		SharedTable.add(label, "Degree of anisotropy", anisotropy);
		if (printRadii) {
			SharedTable.add(label, "Radius a", String.format("%.2f", ellipsoid
				.getA()));
			SharedTable.add(label, "Radius b", String.format("%.2f", ellipsoid
				.getB()));
			SharedTable.add(label, "Radius c", String.format("%.2f", ellipsoid
				.getC()));
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
	private void applyMinimum() {
		if (recommendedMin) {
			lines = DEFAULT_LINES;
			directions = DEFAULT_DIRECTIONS;
			samplingIncrement = DEFAULT_INCREMENT;
		}
	}

	private Optional<Ellipsoid> fitEllipsoid(final List<Vector3d> pointCloud) {
		statusService.showStatus("Anisotropy: solving quadric equation");
		final Matrix4dc quadric = solveQuadricOp.calculate(pointCloud);
		statusService.showStatus("Anisotropy: fitting ellipsoid");
		return quadricToEllipsoidOp.calculate(quadric);
	}

	// TODO Refactor into a static utility method with unit tests
	private boolean isCalibrationIsotropic() {
		final Optional<String> commonUnit = getSpatialUnit(inputImage, unitService);
		if (!commonUnit.isPresent()) {
			return false;
		}
		final String unit = commonUnit.get();
		return spatialAxisStream(inputImage).map(axis -> unitService.value(axis
			.averageScale(0, 1), axis.unit(), unit)).distinct().count() == 1;
	}

	@SuppressWarnings("unchecked")
	private void matchOps(final Subspace<BitType> subspace) {
		milOp = Functions.binary(opService, MILPlane.class, Vector3d.class,
			subspace.interval, new Quaterniond(), lines, samplingIncrement);
		final List<Vector3d> tmpPoints = generate(Vector3d::new).limit(
			Quadric.MIN_DATA).collect(toList());
		solveQuadricOp = Functions.unary(opService, Quadric.class, Matrix4dc.class,
			tmpPoints);
		final Matrix4dc matchingMock = new Matrix4d();
		quadricToEllipsoidOp = (UnaryFunctionOp) Functions.unary(opService,
			QuadricToEllipsoid.class, Optional.class, matchingMock);
	}

	private Ellipsoid milEllipsoid(final Subspace<BitType> subspace) {
		final List<Vector3d> pointCloud;
		try {
			pointCloud = runDirectionsInParallel(subspace.interval);
			if (pointCloud.size() < Quadric.MIN_DATA) {
				cancel("Anisotropy could not be calculated - too few points");
				return null;
			}
			final Optional<Ellipsoid> ellipsoid = fitEllipsoid(pointCloud);
			if (!ellipsoid.isPresent()) {
				cancel("Anisotropy could not be calculated - ellipsoid fitting failed");
				return null;
			}
			return ellipsoid.get();
		}
		catch (final ExecutionException | InterruptedException e) {
			logService.trace(e.getMessage());
			cancel("The plug-in was interrupted");
		}
		return null;
	}

	/**
	 * Creates a random isotropically distributed quaternion.
	 *
	 * @return a (rotation) quaternion which can be used as a parameter for the
	 *         op.
	 */
	private static synchronized Quaterniondc randomQuaternion() {
		final double[] v = qGenerator.nextVector();
		return new Quaterniond(v[0], v[1], v[2], v[3]);
	}

	private List<Vector3d> runDirectionsInParallel(
		final RandomAccessibleInterval<BitType> interval) throws ExecutionException,
		InterruptedException
	{
		final int cores = Runtime.getRuntime().availableProcessors();
		// The parallellization of the the MILPlane algorithm is a memory bound
		// problem, which is why speed gains start to drop after 5 cores. With much
		// larger 'nThreads' it slows down due to overhead. Of course '5' here is a
		// bit of a magic number, which might not hold true for all environments,
		// but we need some kind of upper bound
		final int nThreads = Math.max(5, cores);
		final ExecutorService executor = Executors.newFixedThreadPool(nThreads);
		final Callable<Vector3d> milTask = () -> milOp.calculate(interval,
			randomQuaternion());
		final List<Future<Vector3d>> futures = generate(() -> milTask).limit(
			directions).map(executor::submit).collect(toList());
		final List<Vector3d> pointCloud = Collections.synchronizedList(
			new ArrayList<>(directions));
		final int futuresSize = futures.size();
		final AtomicInteger progress = new AtomicInteger();
		for (final Future<Vector3d> future : futures) {
			statusService.showProgress(progress.getAndIncrement(), futuresSize);
			pointCloud.add(future.get());
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
		}
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}
		if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
			cancel(NOT_3D_IMAGE);
			return;
		}
		if (!ElementUtil.isColorsBinary(inputImage)) {
			cancel(NOT_BINARY);
			return;
		}
		if (!isCalibrationIsotropic() && !calibrationWarned) {
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
