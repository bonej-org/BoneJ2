package org.bonej.wrapperPlugins;

import ij.ImagePlus;
import ij.ImageStack;
import net.imagej.Dataset;
import net.imagej.Main;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.IterableInterval;
import org.bonej.utilities.ImageCheck;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Graph;

import static org.bonej.wrapperPlugins.ErrorMessages.*;

/**
 * A wrapper plugin to bundle AnalyzeSkeleton into BoneJ2
 *
 * @author Richard Domander 
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Analyse Skeleton")
public class AnalyseSkeletonWrapper extends ContextCommand {
    static {
        LegacyInjector.preinit();
    }

    /** @implNote Use Dataset because it has a conversion to ImagePlus */
    @Parameter(initializer = "initializeImage")
    private Dataset inputImage;

    @Parameter
    private ConvertService convertService;

    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        final ImagePlus imagePlus = convertService.convert(inputImage, ImagePlus.class);
        final AnalyzeSkeleton_ skeletonAnalyser = new AnalyzeSkeleton_();

        skeletonAnalyser.setup("", imagePlus);
        skeletonAnalyser.run(null);
        final Graph[] graphs = skeletonAnalyser.getGraphs();
        // Get resultImage to check that the plugin actually ran, and wasn't cancelled by the user
        final ImageStack resultImage = skeletonAnalyser.getResultImage(false);

        if ((graphs == null || graphs.length == 0) && resultImage != null) {
            uiService.showDialog(NO_SKELETONS, DialogPrompt.MessageType.INFORMATION_MESSAGE);
        }
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        final long spatialDimensions = ImageCheck.countSpatialDimensions(inputImage);
        if (spatialDimensions < 2 || spatialDimensions > 3) {
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
        }
    }
}
