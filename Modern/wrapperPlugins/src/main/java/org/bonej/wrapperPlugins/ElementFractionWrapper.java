
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.BAD_CALIBRATION;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.CommonMessages.WEIRD_SPATIAL;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imagej.units.UnitService;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

/**
 * This command estimates the size of the given sample by counting its
 * foreground elements, and the whole stack by counting all the elements (bone)
 * and the whole image stack. In the case of a 2D image "size" refers to areas,
 * and in 3D volumes. The plugin displays the sizes and their ratio in the
 * results table. Results are shown in calibrated units, if possible.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BoneJ>Fraction>Area/Volume fraction", headless = true)
public class ElementFractionWrapper<T extends RealType<T> & NativeType<T>>
	extends ContextCommand
{

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	/**
	 * The element faction results in a {@link Table}
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

	/** Header of the foreground (bone) volume column in the results table */
	private String boneSizeHeader;
	/** Header of the total volume column in the results table */
	private String totalSizeHeader;
	/** Header of the size ratio column in the results table */
	private String ratioHeader;
	/** The calibrated size of an element in the image */
	private double elementSize;

	//TODO Split hyperstacks to 2D/3D?
	@Override
	public void run() {
		// Our image has binary values, but convert to actual binary type
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService,
			inputImage);
		prepareResultDisplay();
		// The value of each foreground element in a bit type image is 1, so we can
		// count their number just by summing
		final double foregroundSize = opService.stats().sum(bitImgPlus)
			.getRealDouble() * elementSize;
		final double totalSize = bitImgPlus.size() * elementSize;
		final double ratio = foregroundSize / totalSize;
		addResults(foregroundSize, totalSize, ratio);
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	// region -- Helper methods --
	private void prepareResultDisplay() {
		final char exponent = ResultUtils.getExponent(inputImage);
		final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService,
			exponent);
		if (unitHeader.isEmpty()) {
			uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
		}
		final String sizeDescription = ResultUtils.getSizeDescription(inputImage);

		boneSizeHeader = "Bone " + sizeDescription.toLowerCase() + " " + unitHeader;
		totalSizeHeader = "Total " + sizeDescription.toLowerCase() + " " + unitHeader;
		ratioHeader = sizeDescription + " Ratio";
		elementSize = ElementUtil.calibratedSpatialElementSize(inputImage,
			unitService);

	}

	private void addResults(final double foregroundSize, final double totalSize,
		final double ratio)
	{
		final String label = inputImage.getName();
		SharedTable.add(label, boneSizeHeader, foregroundSize);
		SharedTable.add(label, totalSizeHeader, totalSize);
		SharedTable.add(label, ratioHeader, ratio);
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

		final long spatialDimensions = AxisUtils.countSpatialDimensions(inputImage);
		if (spatialDimensions < 2 || spatialDimensions > 3) {
			cancel(WEIRD_SPATIAL);
		}
	}
	// endregion
}
