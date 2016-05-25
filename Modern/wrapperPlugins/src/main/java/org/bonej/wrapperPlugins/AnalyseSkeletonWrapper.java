package org.bonej.wrapperPlugins;

import ij.ImagePlus;
import ij.ImageStack;
import net.imagej.patcher.LegacyInjector;
import org.bonej.utilities.ImagePlusCheck;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Graph;
import sc.fiji.skeletonize3D.Skeletonize3D_;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;

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

    /** @implNote Use ImagePlus because of conversion issues of composite images */
    @Parameter(initializer = "initializeImage")
    private ImagePlus inputImage;

    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        final ImagePlus skeleton = inputImage.duplicate();

        final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
        skeletoniser.setup("", skeleton);
        skeletoniser.run(null);

        final int iterations = skeletoniser.getThinningIterations();
        if (iterations > 1) {
            skeleton.setTitle("Skeleton of " + inputImage.getTitle());
            skeleton.show();
            uiService.showDialog(GOT_SKELETONISED, INFORMATION_MESSAGE);
        }

        final AnalyzeSkeleton_ analyser = new AnalyzeSkeleton_();

        analyser.setup("", skeleton);
        analyser.run(null);
        final Graph[] graphs = analyser.getGraphs();
        // Get resultImage to check that the plugin actually ran, and wasn't cancelled by the user
        final ImageStack resultImage = analyser.getResultImage(false);

        if ((graphs == null || graphs.length == 0) && resultImage != null) {
            uiService.showDialog(NO_SKELETONS, INFORMATION_MESSAGE);
        }
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        if (inputImage.getType() != ImagePlus.GRAY8 || !ImagePlusCheck.isBinaryColour(inputImage)) {
            cancel(NOT_8_BIT_BINARY_IMAGE);
        }
    }
}
