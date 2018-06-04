
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.BAD_CALIBRATION;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.util.List;
import java.util.stream.Collectors;

import net.imagej.ImgPlus;
import net.imagej.mesh.Mesh;
import net.imagej.mesh.naive.NaiveFloatMesh;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops.Copy.RAI;
import net.imagej.ops.Ops.Geometric.MarchingCubes;
import net.imagej.ops.Ops.Geometric.Size;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

/**
 * First this command creates a surface mesh from both all foreground voxels
 * (bone) and the whole image stack. Then it calculates the surfaces' volumes,
 * their ratio, and shows the results. Results are shown in calibrated units, if
 * possible.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BoneJ>Fraction>Surface fraction", headless = true)
public class SurfaceFractionWrapper<T extends RealType<T> & NativeType<T>>
	extends ContextCommand
{

	/** Header of ratio column in the results table */
	private static final String ratioHeader = "Volume ratio";
	private static UnaryFunctionOp<RandomAccessibleInterval, Mesh> marchingCubes;
	private static UnaryFunctionOp<Mesh, DoubleType> meshVolume;
	private static UnaryFunctionOp<RandomAccessibleInterval, RandomAccessibleInterval> raiCopy;

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	/**
	 * The surface faction results in a {@link Table}
	 * <p>
	 * Null if there are no results
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;

	@Parameter
	private OpService opService;

	@Parameter
	private UIService uiService;

	@Parameter
	private UnitService unitService;

	@Parameter
	private StatusService statusService;

	/** Header of the thresholded volume column in the results table */
	private String bVHeader;
	/** Header of the total volume column in the results table */
	private String tVHeader;
	/** The calibrated size of an element in the image */
	private double elementSize;

	@Override
	public void run() {
		statusService.showStatus("Surface fraction: initializing");
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService,
			inputImage);
		final List<Subspace<BitType>> subspaces = HyperstackUtils.split3DSubspaces(
			bitImgPlus).collect(Collectors.toList());
		matchOps(subspaces.get(0).interval);
		prepareResultDisplay();
		final String name = inputImage.getName();
		subspaces.forEach(subspace -> {
			final double[] results = subSpaceFraction(subspace.interval);
			final String suffix = subspace.toString();
			final String label = suffix.isEmpty() ? name : name + " " + suffix;
			addResults(label, results);
		});
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	// region -- Helper methods --

	private void addResults(final String label, final double[] results) {
		SharedTable.add(label, bVHeader, results[0]);
		SharedTable.add(label, tVHeader, results[1]);
		SharedTable.add(label, ratioHeader, results[2]);
	}

	private void matchOps(final RandomAccessibleInterval<BitType> subspace) {
		raiCopy = Functions.unary(opService, RAI.class,
			RandomAccessibleInterval.class, subspace);
		marchingCubes = Functions.unary(opService, MarchingCubes.class, Mesh.class,
			subspace);
		// Create a dummy object to make op matching happy
		meshVolume = Functions.unary(opService, Size.class, DoubleType.class,
			new NaiveFloatMesh());
	}

	private void prepareResultDisplay() {
		final char exponent = ResultUtils.getExponent(inputImage);
		final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService,
			exponent);
		if (unitHeader.isEmpty()) {
			uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
		}
		bVHeader = "Bone volume " + unitHeader;
		tVHeader = "Total volume " + unitHeader;
		elementSize = ElementUtil.calibratedSpatialElementSize(inputImage,
			unitService);
	}

	/** Process surface fraction for one 3D subspace in the n-dimensional image */
	@SuppressWarnings("unchecked")
	private double[] subSpaceFraction(
		final RandomAccessibleInterval<BitType> subSpace)
	{
		statusService.showStatus("Surface fraction: creating surface");
		// Create masks for marching cubes
		final RandomAccessibleInterval totalMask = raiCopy.calculate(subSpace);
		// Because we want to create a surface from the whole image, set everything
		// in the mask to foreground
		((Iterable<BitType>) totalMask).forEach(BitType::setOne);

		// Create surface meshes and calculate their volume. If the input interval
		// wasn't binary, we'd have to threshold it before these calls.
		final Mesh thresholdMesh = marchingCubes.calculate(subSpace);
		statusService.showStatus("Surface fraction: calculating volume");
		final double rawThresholdVolume = meshVolume.calculate(thresholdMesh).get();
		final Mesh totalMesh = marchingCubes.calculate(totalMask);
		final double rawTotalVolume = meshVolume.calculate(totalMesh).get();

		final double thresholdVolume = rawThresholdVolume * elementSize;
		final double totalVolume = rawTotalVolume * elementSize;
		final double ratio = thresholdVolume / totalVolume;

		return new double[] { thresholdVolume, totalVolume, ratio };
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}

		if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
			cancel(NOT_3D_IMAGE);
		}

		if (!ElementUtil.isColorsBinary(inputImage)) {
			cancel(NOT_BINARY);
		}
	}
	// endregion
}
