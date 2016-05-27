package org.bonej.wrapperPlugins;

import com.google.common.base.Strings;
import ij.IJ;
import ij.ImagePlus;
import ij.process.StackStatistics;
import net.imagej.patcher.LegacyInjector;
import org.bonej.utilities.ImagePlusCheck;
import org.bonej.utilities.ResultsInserter;
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
import sc.fiji.localThickness.LocalThicknessWrapper;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.*;

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
    @Parameter(initializer = "initializeImage")
    private ImagePlus inputImage;

    @Parameter(type = ItemIO.OUTPUT)
    private ImagePlus thicknessMap;

    @Parameter(type = ItemIO.OUTPUT)
    private ImagePlus spacingMap;

    @Parameter(label = "Calculate:", description = "Which thickness measures to calculate",
            style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE,
            choices = {"Trabecular thickness", "Trabecular spacing", "Both"})
    private String maps;

    @Parameter(label = "Show thickness maps", description = "Show resulting map images after calculations")
    private boolean showMaps = true;

    @Parameter(label = "Mask thickness maps", description = "Remove pixel artifacts from the thickness maps")
    private boolean maskArtefacts = true;

    @Parameter(label = "Help", description = "Open help web page", callback = "openHelpPage")
    private Button helpButton;

    @Parameter
    private LogService logService;

    @Parameter
    private PlatformService platformService;

    @Parameter
    private UIService uiService;

    private static void showMapStatistics(final ImagePlus map, final boolean foreground) {
        final String unitHeader = getUnitHeader(map);
        final String label = map.getTitle();
        final String prefix = foreground ? "Tb.Th" : "Tb.Sp";
        final StackStatistics resultStats = new StackStatistics(map);

        ResultsInserter inserter = new ResultsInserter();
        inserter.setMeasurementInFirstFreeRow(label, prefix + " Mean" + unitHeader, resultStats.mean);
        inserter.setMeasurementInFirstFreeRow(label, prefix + " Std Dev" + unitHeader, resultStats.stdDev);
        inserter.setMeasurementInFirstFreeRow(label, prefix + " Max" + unitHeader, resultStats.max);
        inserter.updateResults();
    }

    private static String getUnitHeader(final ImagePlus map) {
        final String unit = map.getCalibration().getUnit();
        if (Strings.isNullOrEmpty(unit) || "pixel".equalsIgnoreCase(unit) || "unit".equalsIgnoreCase(unit)) {
            return "";
        }

        return " (" + unit + ")";
    }

    @Override
    public void run() {
        switch (maps) {
            case "Trabecular thickness":
                thicknessMap = createMap(true);
                showMapStatistics(thicknessMap, true);
                break;
            case "Trabecular spacing":
                spacingMap = createMap(false);
                showMapStatistics(spacingMap, false);
                break;
            case "Both":
                thicknessMap = createMap(true);
                showMapStatistics(thicknessMap, true);
                spacingMap = createMap(false);
                showMapStatistics(spacingMap, false);
                break;
            default:
                throw new RuntimeException("Unexpected map choice");
        }
    }

    //region -- Helper methods --
    private ImagePlus createMap(final boolean foreground) {
        final String suffix = foreground ? "_Tb.Th" : "_Tb.Sp";

        final LocalThicknessWrapper localThickness = new LocalThicknessWrapper();
        localThickness.setSilence(true);
        localThickness.inverse = !foreground;
        localThickness.setShowOptions(false);
        localThickness.maskThicknessMap = maskArtefacts;
        localThickness.setTitleSuffix(suffix);
        localThickness.calibratePixels = true;
        final ImagePlus map = localThickness.processImage(inputImage);

        if (showMaps) {
            map.show();
            IJ.run("Fire");
        }

        return map;
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        if (!ImagePlusCheck.is3D(inputImage)) {
            cancel(NOT_3D_IMAGE);
            return;
        }

        if (inputImage.getBitDepth() != 8 || !ImagePlusCheck.isBinaryColour(inputImage)) {
            cancel(NOT_8_BIT_BINARY_IMAGE);
            return;
        }

        if (!ImagePlusCheck.isIsotropic(inputImage, 1E-3)) {
            final String difference = "";
            final Result result =
                    uiService.showDialog("The image is anisotropic" + difference + ". Continue anyway?",
                            MessageType.WARNING_MESSAGE, OptionType.OK_CANCEL_OPTION);
            if (result == Result.CANCEL_OPTION) {
                cancel(null);
            }
        }
    }

    @SuppressWarnings("unused")
    private void openHelpPage() {
        Help.openHelpPage("http://bonej.org/thickness", platformService, uiService, logService);
    }
    //endregion
}
