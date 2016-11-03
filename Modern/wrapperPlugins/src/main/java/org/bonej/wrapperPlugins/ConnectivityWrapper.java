
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.util.List;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.units.UnitService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.ResultsInserter;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils.SpatialView;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

/**
 * A wrapper UI class for the Connectivity Ops
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Connectivity",
	headless = true)
public class ConnectivityWrapper extends ContextCommand {

	public static final String NEGATIVE_CONNECTIVITY =
		"Connectivity is negative.\nThis usually happens if there are multiple particles or enclosed cavities.\n" +
			"Try running Purify prior to Connectivity.\n";

	@Parameter(initializer = "initializeImage")
	private ImgPlus<UnsignedByteType> inputImage;

	@Parameter
	private OpService opService;

	@Parameter
	private UIService uiService;

	@Parameter
	private UnitService unitService;

	private boolean negativityWarned = false;
	private boolean calibrationWarned = false;

	@Override
	public void run() {
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService,
			inputImage);
		final String name = inputImage.getName();

		final List<SpatialView<BitType>> views = ViewUtils.createSpatialViews(
			bitImgPlus);
		for (SpatialView view : views) {
			final String label = name + view.hyperPosition;
			viewConnectivity(label, view.view);
		}
	}

	// region -- Helper methods --

	/** Process connectivity for one spatial view */
	private void viewConnectivity(final String label,
		final RandomAccessibleInterval view)
	{
		final double eulerCharacteristic = opService.topology()
			.eulerCharacteristic26NFloating(view).get();
		final double edgeCorrection = opService.topology().eulerCorrection(view)
			.get();
		final double correctedEuler = eulerCharacteristic - edgeCorrection;
		final double connectivity = 1 - correctedEuler;
		final double connectivityDensity = calculateConnectivityDensity(view,
			connectivity);

		showResults(label, eulerCharacteristic, correctedEuler, connectivity,
			connectivityDensity);
	}

	private void showResults(String label, final double eulerCharacteristic,
		final double deltaEuler, final double connectivity,
		final double connectivityDensity)
	{
		final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService,
			'³');

		if (connectivity < 0 && !negativityWarned) {
			uiService.showDialog(NEGATIVE_CONNECTIVITY, INFORMATION_MESSAGE);
			negativityWarned = true;
		}

		if (unitHeader.isEmpty() && !calibrationWarned) {
			uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
			calibrationWarned = true;
		}

		final ResultsInserter inserter = ResultsInserter.getInstance();
		inserter.setMeasurementInFirstFreeRow(label, "Euler char. (χ)",
			eulerCharacteristic);
		inserter.setMeasurementInFirstFreeRow(label, "Corrected Euler (Δχ)",
			deltaEuler);
		inserter.setMeasurementInFirstFreeRow(label, "Connectivity", connectivity);
		inserter.setMeasurementInFirstFreeRow(label, "Conn. density " + unitHeader,
			connectivityDensity);
		inserter.updateResults();
	}

	private double calculateConnectivityDensity(
		final RandomAccessibleInterval view, final double connectivity)
	{
		final double elements = ((IterableInterval) view).size();
		final double elementSize = ElementUtil.calibratedSpatialElementSize(
			inputImage, unitService);
		return connectivity / (elements * elementSize);
	}

	@SuppressWarnings("unused")
	private void initializeImage() {
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
		}
	}
	// endregion
}
