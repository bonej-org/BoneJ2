
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
import java.util.function.Function;
import java.util.stream.Stream;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

import org.bonej.ops.SolveQuadricEq;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.QuadricToEllipsoid;
import org.bonej.ops.mil.MILGrid;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;
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

	private static BinaryFunctionOp<RandomAccessibleInterval<BitType>, AxisAngle4d, List<Vector3d>> milOp;
	private static UnaryFunctionOp<Matrix4d, Ellipsoid> quadricToEllipsoidOp;
	private static UnaryFunctionOp<List<Vector3d>, Matrix4d> solveQuadricOp;
	private static int ROTATIONS = 1_000;
	private static int DEFAULT_LINES = 50;
	private static double DEFAULT_INCREMENT = 1.0;
	/** Assumes that the longest radius of the ellipsoid is 1.0 */
	private final Function<Ellipsoid, Double> degreeOfAnisotropy =
		ellipsoid -> 1.0 - ellipsoid.getA() / ellipsoid.getC();
	@SuppressWarnings("unused")
	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;
	@Parameter(label = "Auto parameters", required = false, persist = false,
		callback = "setAutoParam")
	private boolean autoParameters = false;
	@Parameter(label = "Rotations",
		description = "The number of times sampling is performed from different directions",
		min = "1", style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "setAutoParam")
	private Integer rotations = ROTATIONS;
	@Parameter(label = "Sampling lines",
		description = "Number of sampling lines drawn. The number is squared and multiplied by three",
		min = "1", style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "setAutoParam")
	private Integer lines = DEFAULT_LINES;
	@Parameter(label = "Sampling increment", min = "0.01",
		description = "Distance between sampling points (in pixels)",
		style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "setAutoParam", stepSize = "0.1")
	private Double samplingIncrement = DEFAULT_INCREMENT;
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String instruction =
		"NB parameter values can affect results significantly";
	private boolean calibrationWarned;
	@Parameter(label = "Print ellipsoids",
		description = "Print axes of the fitted ellipsoids", required = false)
	private boolean printEllipsoids;
	private static Long seed = null;

	public static void setSeed(long seed) {
		AnisotropyWrapper.seed = seed;
	}

	// TODO add @Parameter to align the image to the ellipsoid
	// Create a rotated view from the ImgPlus and pop that into an output
	// @Parameter

	// TODO add help button
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String divider = "- - -";
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
		// TODO Does it make more sense to collect all the results we can (without
		// cancelling) and then report errors at the end?
		for (int i = 0; i < subspaces.size(); i++) {
			final Subspace<BitType> subspace = subspaces.get(i);
			statusService.showStatus("Anisotropy: sampling subspace #" + (i + 1));
			final List<Vector3d> pointCloud;
			try {
				pointCloud = runRotationsInParallel(subspace.interval);
			}
			catch (ExecutionException | InterruptedException e) {
				logService.trace(e.getMessage());
				cancel("Parallel execution got interrupted");
				return;
			}
			applyCalibration(pointCloud);
			if (pointCloud.size() < SolveQuadricEq.QUADRIC_TERMS) {
				cancel("Anisotropy could not be calculated - too few points");
				return;
			}
			final Ellipsoid ellipsoid = fitEllipsoid(pointCloud);
			if (ellipsoid == null) {
				cancel("Anisotropy could not be calculated - ellipsoid fitting failed");
				return;
			}
			statusService.showStatus("Determining anisotropy");
			final double anisotropy = degreeOfAnisotropy.apply(ellipsoid);
			addResults(subspace, anisotropy, ellipsoid);
		}
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	// region -- Helper methods --

	private void addResults(final Subspace<BitType> subspace,
		final double anisotropy, final Ellipsoid ellipsoid)
	{
		final String imageName = inputImage.getName();
		final String suffix = subspace.toString();
		final String label = suffix.isEmpty() ? imageName : imageName + " " +
			suffix;
		SharedTable.add(label, "Degree of anisotropy", anisotropy);
		if (printEllipsoids) {
			final List<Vector3d> semiAxes = ellipsoid.getSemiAxes();
			int ordinal = 1;
			for (final Vector3d axis : semiAxes) {
				SharedTable.add(label, "Ellipsoid axis #" + ordinal, axis.toString());
				ordinal++;
			}
		}
	}

	private void applyCalibration(final List<Vector3d> pointCloud) {
		final double[] scales = spatialAxisStream(inputImage).mapToDouble(
			axis -> axis.averageScale(0, 1)).toArray();
		final String[] units = spatialAxisStream(inputImage).map(
			CalibratedAxis::unit).toArray(String[]::new);
		final double yxConversion = unitService.value(1.0, units[1], units[0]);
		final double zxConversion = unitService.value(1.0, units[2], units[0]);
		final double xScale = scales[0];
		final double yScale = scales[1] * yxConversion;
		final double zScale = scales[2] * zxConversion;
		pointCloud.forEach(p -> {
			p.setX(p.x * xScale);
			p.setY(p.y * yScale);
			p.setZ(p.z * zScale);
		});
	}

	private Ellipsoid fitEllipsoid(final List<Vector3d> pointCloud) {
		if (pointCloud.size() < SolveQuadricEq.QUADRIC_TERMS) {
			return null;
		}
		statusService.showStatus("Anisotropy: solving quadric equation");
		final Matrix4d quadric = solveQuadricOp.calculate(pointCloud);
		if (!QuadricToEllipsoid.isEllipsoid(quadric)) {
			return null;
		}
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
		milOp = (BinaryFunctionOp) Functions.binary(opService, MILGrid.class,
			List.class, subspace.interval, new AxisAngle4d(), lines,
			samplingIncrement, seed);
		final List<Vector3d> tmpPoints = generate(Vector3d::new).limit(
			SolveQuadricEq.QUADRIC_TERMS).collect(toList());
		solveQuadricOp = Functions.unary(opService, SolveQuadricEq.class,
			Matrix4d.class, tmpPoints);
		final Matrix4d matchingMock = new Matrix4d();
		matchingMock.setIdentity();
		quadricToEllipsoidOp = Functions.unary(opService, QuadricToEllipsoid.class,
			Ellipsoid.class, matchingMock);
	}

	private List<Vector3d> runRotationsInParallel(
		final RandomAccessibleInterval<BitType> interval) throws ExecutionException,
		InterruptedException
	{
		final ExecutorService executor = Executors.newFixedThreadPool(5);
		final Callable<List<Vector3d>> milTask = () -> milOp.calculate(interval);
		final List<Future<List<Vector3d>>> futures = Stream.generate(() -> milTask)
			.limit(rotations).map(executor::submit).collect(toList());
		final List<Vector3d> pointCloud = Collections.synchronizedList(
			new ArrayList<>(rotations * 3));
		final int futuresSize = futures.size();
		for (int j = 0; j < futuresSize; j++) {
			statusService.showProgress(j, futuresSize);
			final List<Vector3d> points = futures.get(j).get();
			pointCloud.addAll(points);

		}
		executor.shutdown();
		return pointCloud;
	}

	@SuppressWarnings("unused")
	private void setAutoParam() {
		if (!autoParameters) {
			return;
		}
		rotations = ROTATIONS;
		lines = DEFAULT_LINES;
		samplingIncrement = DEFAULT_INCREMENT;
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
			final DialogPrompt.Result result = uiService.showDialog(
				"The image calibration is anisotropic and may affect results. Continue anyway?",
				WARNING_MESSAGE, OK_CANCEL_OPTION);
			// Avoid showing warning more than once (validator gets called before and
			// after dialog pops up..?)
			calibrationWarned = true;
			if (result != OK_OPTION) {
				cancel(null);
			}
		}
		// TODO Is the 5 slice minimum necessary?
	}
	// endregion
}
