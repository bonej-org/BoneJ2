
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.HAS_CHANNEL_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_TIME_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.scijava.ui.DialogPrompt.MessageType;
import static org.scijava.ui.DialogPrompt.OptionType;
import static org.scijava.ui.DialogPrompt.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.imagej.patcher.LegacyInjector;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;

import org.bonej.utilities.ImagePlusCheck;
import org.bonej.utilities.RoiManagerUtil;
import org.bonej.utilities.SharedTable;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.frame.RoiManager;
import ij.process.StackStatistics;
import sc.fiji.localThickness.LocalThicknessWrapper;

/**
 * A GUI wrapper class for the LocalThickness plugin
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Thickness")
public class ThicknessWrapper extends ContextCommand {

	static {
		LegacyInjector.preinit();
	}

	/**
	 * @implNote Use ImagePlus because of conversion issues of composite images
	 */
	@Parameter(validater = "validateImage")
	private ImagePlus inputImage;

	@Parameter(label = "Calculate:",
		description = "Which thickness measures to calculate",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = {
			"Trabecular thickness", "Trabecular spacing", "Both" })
	private String maps;

	@Parameter(label = "Show thickness maps",
		description = "Show resulting map images after calculations")
	private boolean showMaps = true;

	@Parameter(label = "Mask thickness maps",
		description = "Remove pixel artifacts from the thickness maps")
	private boolean maskArtefacts = true;

	@Parameter(label = "Crop to ROI manager",
		description = "Limit the maps to the ROIs in the ROI manager",
		persist = false)
	private boolean cropToRois = false;

	@Parameter(label = "Help", description = "Open help web page",
		callback = "openHelpPage")
	private Button helpButton;

	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus thicknessMap;

	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus spacingMap;

	/**
	 * The calculated thickness statistics in a {@link Table}
	 * <p>
	 * Null if there are no results
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;

	@Parameter
	private LogService logService;

	@Parameter
	private PlatformService platformService;

	@Parameter
	private UIService uiService;

	@Override
	public void run() {
		final List<Boolean> mapOptions = new ArrayList<>();
		if ("Trabecular thickness".equals(maps) || "Both".equals(maps)) {
			mapOptions.add(true);
		}
		if ("Trabecular spacing".equals(maps) || "Both".equals(maps)) {
			mapOptions.add(false);
		}
		mapOptions.forEach(foreground -> {
			final ImagePlus map = createMap(foreground);
			if (map == null) {
				return;
			}
			addMapResults(map, foreground);
			if (foreground) {
				thicknessMap = map;
			}
			else {
				spacingMap = map;
			}
		});
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	// region -- Helper methods --
	private ImagePlus createMap(final boolean foreground) {
		final String suffix = foreground ? "_Tb.Th" : "_Tb.Sp";
		ImagePlus image;

		if (cropToRois) {
			final RoiManager roiManager = RoiManager.getInstance2();

			Optional<ImageStack> stackOptional = RoiManagerUtil.cropToRois(roiManager,
				inputImage.getStack(), true, 0x00);

			if (!stackOptional.isPresent()) {
				uiService.showDialog("There are no ROIs in the ROI Manager",
					MessageType.ERROR_MESSAGE);
				return null;
			}
			image = new ImagePlus(inputImage.getTitle(), stackOptional.get());
		}
		else {
			image = inputImage.duplicate();
			image.setTitle(inputImage.getTitle());
		}

		final LocalThicknessWrapper localThickness = new LocalThicknessWrapper();
		localThickness.setSilence(true);
		localThickness.inverse = !foreground;
		localThickness.setShowOptions(false);
		localThickness.maskThicknessMap = maskArtefacts;
		localThickness.setTitleSuffix(suffix);
		localThickness.calibratePixels = true;
		final ImagePlus map = localThickness.processImage(image);

		if (showMaps) {
			map.show();
			IJ.run("Fire");
		}

		return map;
	}

	private void addMapResults(final ImagePlus map, final boolean foreground)
	{
		final String unitHeader = getUnitHeader(map);
		final String label = map.getTitle();
		final String prefix = foreground ? "Tb.Th" : "Tb.Sp";
		final StackStatistics resultStats = new StackStatistics(map);
		double mean = resultStats.mean;
		double stdDev = resultStats.stdDev;
		double max = resultStats.max;

		if (resultStats.pixelCount == 0) {
			// All pixels are background (NaN), stats not applicable
			mean = Double.NaN;
			stdDev = Double.NaN;
			max = Double.NaN;
		}

		SharedTable.add(label, prefix + " Mean" + unitHeader, mean);
		SharedTable.add(label, prefix + " Std Dev" + unitHeader, stdDev);
		SharedTable.add(label, prefix + " Max" + unitHeader, max);
	}

	private static String getUnitHeader(final ImagePlus map) {
		final String unit = map.getCalibration().getUnit();
		// TODO replace with StringUtils.nullOrEmtpy
		if (unit == null || unit.isEmpty() || "pixel".equalsIgnoreCase(unit) ||
			"unit".equalsIgnoreCase(unit))
		{
			return "";
		}

		return " (" + unit + ")";
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}

		if (!ImagePlusCheck.is3D(inputImage)) {
			cancel(NOT_3D_IMAGE);
			return;
		}

		if (inputImage.isComposite()) {
			cancel(HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
			return;
		}
		else if (inputImage.isHyperStack()) {
			cancel(HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
			return;
		}

		if (inputImage.getBitDepth() != 8 || !ImagePlusCheck.isBinaryColour(
			inputImage))
		{
			cancel(NOT_8_BIT_BINARY_IMAGE);
			return;
		}

		final double anisotropy = ImagePlusCheck.anisotropy(inputImage);
		if (anisotropy > 1E-3) {
			final String anisotropyPercent = String.format(" (%.1f %%)", anisotropy *
				100.0);
			final Result result = uiService.showDialog("The image is anisotropic" +
				anisotropyPercent + ". Continue anyway?", MessageType.WARNING_MESSAGE,
				OptionType.OK_CANCEL_OPTION);
			if (result == Result.CANCEL_OPTION) {
				cancel(null);
			}
		}
	}

	@SuppressWarnings("unused")
	private void openHelpPage() {
		Help.openHelpPage("http://bonej.org/thickness", platformService, uiService,
			logService);
	}
	// endregion
}
