package org.bonej.menuWrappers;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import org.doube.util.ImageCheck;
import org.doube.util.UsageReporter;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * A simple wrapper plugin to add the Skeletonize_ plugin under Plugins>BoneJ menu path
 *
 * Displays additional incompatibility warnings to the user
 *
 * NB Does not overwrite the input image. Follows the logic in BoneJ1
 *
 * @author Michael Doube
 * @author Richard Domander
 * @todo Discuss if plugin should overwrite input, like all the others do
 * @todo What should this plugin be called in the menu? Valid for 2D and 3D images...
 */
public class Skeletonise implements PlugIn {
    private final Skeletonize3D_ skeletoniser = new Skeletonize3D_();

    @Override
    public void run(String arg) {
        if (!ImageCheck.checkIJVersion()) {
            return;
        }

        final ImagePlus inputImage;

        try {
            inputImage = IJ.getImage();
        } catch (RuntimeException e) {
            // If no image is open, getImage() throws an exception
            return;
        }

        if (!ImageCheck.isBinary(inputImage)) {
            IJ.error("Skeletonise requires an 8-bit greyscale binary image");
            return;
        }

        // Duplicate input image without changing the title
        final String originalTitle = inputImage.getTitle();
        final ImagePlus outputImage = inputImage.duplicate();
        outputImage.setTitle(originalTitle);

        skeletoniser.setup("", outputImage);
        skeletoniser.run(null);

        if (inputImage.isInvertedLut() != outputImage.isInvertedLut()) {
            // Invert the LUT of the output image to match input image
            IJ.run("Invert LUT");
        }

        outputImage.show();

        UsageReporter.reportEvent(this).send();
    }
}
