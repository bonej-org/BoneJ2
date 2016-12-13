
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType;
import static org.scijava.ui.DialogPrompt.OptionType;
import static org.scijava.ui.DialogPrompt.Result;

import com.google.common.base.Strings;

import java.util.Optional;

import net.imagej.patcher.LegacyInjector;

import org.bonej.utilities.ImagePlusCheck;
import org.bonej.utilities.ResultsInserter;
import org.bonej.utilities.RoiManagerUtil;
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

	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus thicknessMap;

	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus spacingMap;

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

	@Parameter
	private LogService logService;

	@Parameter
	private PlatformService platformService;

	@Parameter
	private UIService uiService;

	@Override
	public void run() {
		switch (maps) {
			case "Trabecular thickness":
				thicknessMap = createMap(true);
				if (thicknessMap == null) {
					return;
				}
				showMapStatistics(thicknessMap, true);
				break;
			case "Trabecular spacing":
				spacingMap = createMap(false);
				if (spacingMap == null) {
					return;
				}
				showMapStatistics(spacingMap, false);
				break;
			case "Both":
				thicknessMap = createMap(true);
				if (thicknessMap == null) {
					return;
				}
				showMapStatistics(thicknessMap, true);
				spacingMap = createMap(false);
				showMapStatistics(spacingMap, false);
				break;
			default:
				throw new RuntimeException("Unexpected map choice");
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

	private static void showMapStatistics(final ImagePlus map,
		final boolean foreground)
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

		ResultsInserter inserter = ResultsInserter.getInstance();
		inserter.setMeasurementInFirstFreeRow(label, prefix + " Mean" + unitHeader,
			mean);
		inserter.setMeasurementInFirstFreeRow(label, prefix + " Std Dev" +
			unitHeader, stdDev);
		inserter.setMeasurementInFirstFreeRow(label, prefix + " Max" + unitHeader,
			max);
		inserter.updateResults();
	}

	private static String getUnitHeader(final ImagePlus map) {
		final String unit = map.getCalibration().getUnit();
		if (Strings.isNullOrEmpty(unit) || "pixel".equalsIgnoreCase(unit) || "unit"
			.equalsIgnoreCase(unit))
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
