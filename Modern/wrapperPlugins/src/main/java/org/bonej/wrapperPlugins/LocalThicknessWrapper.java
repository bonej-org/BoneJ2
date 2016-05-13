package org.bonej.wrapperPlugins;

import ij.ImagePlus;
import net.imagej.Dataset;
import net.imglib2.IterableInterval;
import org.bonej.utilities.ImageCheck;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;

import static org.bonej.wrapperPlugins.ErrorMessages.*;

/**
 * A GUI wrapper class for the LocalThickness plugin
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Thickness")
public class LocalThicknessWrapper extends ContextCommand {
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
    private LogService logService;

    @Parameter
    private PlatformService platformService;

    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        if (!doThickness && !doSpacing) {
            cancel("Select either \"Trabecular thickness\" or \"Trabecular spacing\"");
            return;
        }

        showResults();
    }

    private void showResults() {
        //TODO warning from bad calibration if necessary

        //TODO show thickness std dev etc with ResultsInserter
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        if (ImageCheck.countSpatialDimensions(inputImage) != 3) {
            cancel(NOT_2D_OR_3D_IMAGE);
            return;
        }

        IterableInterval interval = inputImage;
        if (inputImage.getValidBits() != 8 || !ImageCheck.isColorsBinary(interval)) {
            cancel(NOT_8_BIT_BINARY_IMAGE);
            return;
        }

        if (!convertService.supports(inputImage, ImagePlus.class)) {
            cancel(CANNOT_CONVERT_TO_IMAGE_PLUS);
            return;
        }

        //TODO: check pixel isotropy
    }

    @SuppressWarnings("unused")
    private void openHelpPage() {
        Help.openHelpPage("http://bonej.org/thickness", platformService, uiService, logService);
    }
}
