package org.bonej.wrapperPlugins;

import ij.ImagePlus;
import ij.plugin.LutLoader;
import net.imagej.patcher.LegacyInjector;
import org.bonej.utilities.ImagePlusCheck;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.skeletonize3D.Skeletonize3D_;

import static org.bonej.wrapperPlugins.CommonMessages.*;

/**
 * A wrapper plugin to bundle Skeletonize3D into BoneJ2
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Skeletonise")
public class SkeletoniseWrapper extends ContextCommand {
    static {
        LegacyInjector.preinit();
    }

    /** @implNote Use ImagePlus because of conversion issues of composite images */
    @Parameter(initializer = "initializeImage")
    private ImagePlus inputImage;

    /** @implNote Use ImagePlus because a (converted) Dataset has display issues with a composite image */
    @Parameter(type = ItemIO.OUTPUT)
    private ImagePlus skeleton;

    @Override
    public void run() {
        skeleton = inputImage.duplicate();
        skeleton.setTitle("Skeleton of " + inputImage.getTitle());

        final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
        skeletoniser.setup("", skeleton);
        skeletoniser.run(null);

        skeleton.show();
        if (inputImage.isInvertedLut() != skeleton.isInvertedLut()) {
            // FIXME Does *not* work in headless mode!
            LutLoader lutLoader = new LutLoader();
            lutLoader.run("invert");
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
