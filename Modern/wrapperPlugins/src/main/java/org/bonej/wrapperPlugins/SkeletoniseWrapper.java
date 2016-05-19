package org.bonej.wrapperPlugins;

import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.Main;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.IterableInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.bonej.utilities.ImageCheck;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import sc.fiji.skeletonize3D.Skeletonize3D_;

import static org.bonej.wrapperPlugins.ErrorMessages.*;

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

    /** @implNote Use Dataset because it has a conversion to ImagePlus */
    @Parameter(initializer = "initializeImage")
    private Dataset inputImage;

    @Parameter(type = ItemIO.OUTPUT)
    private Dataset outputImage;

    @Parameter
    private ConvertService convertService;

    @Parameter
    private DatasetService datasetService;

    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        final String inputImageName = inputImage.getName();
        final ImagePlus skeleton = convertService.convert(inputImage.duplicate(), ImagePlus.class);
        final Skeletonize3D_ skeletoniser = new Skeletonize3D_();

        skeletoniser.setup("", skeleton);
        skeletoniser.run(null);

        final ImgPlus<UnsignedByteType> imgPlus = ImagePlusAdapter.wrapImgPlus(skeleton);
        outputImage = datasetService.create(imgPlus);
        outputImage.setName("Skeleton of " + inputImageName);
        uiService.show(outputImage);
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

    public static void main(String... args) {
        Main.launch(args);
    }
}
