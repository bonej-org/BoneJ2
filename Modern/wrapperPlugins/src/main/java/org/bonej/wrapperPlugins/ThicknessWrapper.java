package org.bonej.wrapperPlugins;

import ij.IJ;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.Main;
import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.IterableInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.bonej.utilities.ImageCheck;
import org.bonej.utilities.ResultsInserter;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import sc.fiji.localThickness.LocalThicknessWrapper;

import java.util.DoubleSummaryStatistics;
import java.util.Optional;
import java.util.stream.DoubleStream;
import java.util.stream.StreamSupport;

import static org.bonej.wrapperPlugins.ErrorMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType;
import static org.scijava.ui.DialogPrompt.OptionType;
import static org.scijava.ui.DialogPrompt.Result;

/**
 * A GUI wrapper class for the LocalThickness plugin
 *
 * @author Richard Domander
 * TODO Fix display issues with Datasets
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Thickness")
public class ThicknessWrapper extends ContextCommand {
    static {
        LegacyInjector.preinit();
    }

    private boolean anisotropyWarningShown;

    // TODO fix "NaN" value issue (NaNs make Datasets black) so that Dataset can be made outputs
    private Dataset thicknessMap;
    private Dataset spacingMap;

    /** @implNote Use Dataset because it has a conversion to ImagePlus */
    @Parameter(initializer = "initializeImage")
    private Dataset inputImage;

    @Parameter(label = "Trabecular thickness", description = "Calculate the thickness of the trabeculae")
    private boolean doThickness = true;

    @Parameter(label = "Trabecular spacing", description = "Calculate the thickness of the spaces between trabeculae")
    private boolean doSpacing;

    @Parameter(label = "Show thickness maps", description = "Show resulting map images after calculations")
    private boolean showMaps = true;

    @Parameter(label = "Mask thickness maps", description = "Remove pixel artifacts from the thickness maps")
    private boolean maskArtefacts = true;

    @Parameter(label = "Help", description = "Open help web page", callback = "openHelpPage")
    private Button helpButton;

    @Parameter
    private ConvertService convertService;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private LogService logService;

    @Parameter
    private OpService opService;

    @Parameter
    private PlatformService platformService;

    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        anisotropyWarningShown = false;

        if (!doThickness && !doSpacing) {
            cancel("Select either \"Trabecular thickness\" or \"Trabecular spacing\"");
            return;
        }

        if (doThickness) {
            thicknessMap = createMap(true);
            showMapStatistics(thicknessMap, true);
        }

        if (doSpacing) {
            spacingMap = createMap(false);
            showMapStatistics(spacingMap, false);
        }
    }

    //region -- Helper methods --
    private Dataset createMap(final boolean foreground) {
        final ImagePlus imagePlus = convertService.convert(inputImage, ImagePlus.class);
        final String suffix = foreground ? "_Tb.Th" : "_Tb.Sp";

        final LocalThicknessWrapper localThickness = new LocalThicknessWrapper();
        localThickness.setSilence(true);
        localThickness.inverse = !foreground;
        localThickness.setShowOptions(false);
        localThickness.maskThicknessMap = maskArtefacts;
        localThickness.setTitleSuffix(suffix);
        localThickness.calibratePixels = true;
        final ImagePlus map = localThickness.processImage(imagePlus);

        if (showMaps) {
            /* Show ImagePlus because of Dataset display issues:
               1. Dataset doesn't get correctly scaled to pixel values
               2. Dataset doesn't show in "Fire" LUT
               3. NaN values make the Dataset completely black
             */
            map.show();
            IJ.run("Fire");
        }

        final ImgPlus<UnsignedByteType> imgPlus = ImagePlusAdapter.wrapImgPlus(map);

        return datasetService.create(imgPlus);
    }

    private void showMapStatistics(final Dataset map, final boolean foreground) {
        final String unitHeader = getUnitHeader(map);
        final String label = map.getName();
        final String prefix = foreground ? "Tb.Th" : "Tb.Sp";

        // Can't call stats Ops because we have NaN values in the Dataset
        final DoubleSummaryStatistics statistics = getDoubleStream(map).summaryStatistics();
        final double mean = statistics.getAverage();
        final double max = statistics.getMax();
        final double sum = getDoubleStream(map).reduce(0.0, (a, b) -> a + (b - mean) * (b - mean));
        final double stdDev = Math.sqrt(sum / statistics.getCount());

        ResultsInserter inserter = new ResultsInserter();
        inserter.setMeasurementInFirstFreeRow(label, prefix + " Mean " + unitHeader, mean);
        inserter.setMeasurementInFirstFreeRow(label, prefix + " Std Dev " + unitHeader, stdDev);
        inserter.setMeasurementInFirstFreeRow(label, prefix + " Max " + unitHeader, max);
        inserter.updateResults();
    }

    private DoubleStream getDoubleStream(final Dataset dataset) {
        return StreamSupport.stream(dataset.spliterator(), false).mapToDouble(ComplexType::getRealDouble)
                .filter(e -> !Double.isNaN(e));
    }

    private String getUnitHeader(final Dataset map) {
        final Optional<String> unit = ImageCheck.getSpatialUnit(map);
        String unitHeader = "";
        if (!unit.isPresent()) {
            if (!anisotropyWarningShown) {
                uiService.showDialog(
                        "Cannot not determine the unit of calibration - showing plain values",
                        MessageType.WARNING_MESSAGE);
                anisotropyWarningShown = true;
            }
        } else {
            unitHeader = unit.get();
            if ("pixel".equals(unitHeader) || "unit".equals(unitHeader)) {
                // Don't show the default units
                unitHeader = "";
            } else if (!unitHeader.isEmpty()) {
                unitHeader = "(" + unitHeader + "³)";
            }
        }

        return unitHeader;
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        if (ImageCheck.countSpatialDimensions(inputImage) != 3) {
            cancel(NOT_3D_IMAGE);
            return;
        }

        IterableInterval interval = inputImage;
        if (inputImage.getValidBits() != 8 || !ImageCheck.isColoursBinary(interval)) {
            cancel(NOT_8_BIT_BINARY_IMAGE);
            return;
        }

        if (!convertService.supports(inputImage, ImagePlus.class)) {
            cancel(CANNOT_CONVERT_TO_IMAGE_PLUS);
            return;
        }

        if (!ImageCheck.isSpatialCalibrationIsotropic(inputImage)) {
            final Result result = uiService.showDialog("The image is anisotropic. Continue anyway?",
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

    public static void main(String... args) {
        Main.launch(args);
    }
}
