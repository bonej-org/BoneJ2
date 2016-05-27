package org.bonej.utilities;

import ij.ImagePlus;
import ij.measure.Calibration;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Utility methods for checking ImagePlus properties
 *
 * @author Richard Domander 
 */
public class ImagePlusCheck {

    @Contract("null -> false")
    public static boolean is3D(@Nullable final ImagePlus image) {
        return image != null && image.getNSlices() > 1;
    }

    /**
     * Check if the image's voxels are isotropic in all 3 dimensions (i.e. are
     * placed on a cubic grid)
     *
     * @param image     image to test
     * @param tolerance tolerated relative deviation in voxel dimensions [0.0, 1.0]
     * @return true if voxel width == height and height == depth (within tolerance),
     *         false if not or if image == null
     */
    @Contract("null, _ -> false")
    public static boolean isIsotropic(@Nullable final ImagePlus image, double tolerance) {
        if (image == null) {
            return false;
        }

        if (tolerance < 0.0) {
            tolerance = 0.0;
        } else if (tolerance > 1.0) {
            tolerance = 1.0;
        }

        final Calibration cal = image.getCalibration();
        final double vW = cal.pixelWidth;
        final double vH = cal.pixelHeight;
        final double tLow = 1.0 - tolerance;
        final double tHigh = 1.0 + tolerance;
        final double widthHeightRatio = vW > vH ? vW / vH : vH / vW;

        if (widthHeightRatio < tLow || widthHeightRatio > tHigh) {
            return false;
        }

        if (!is3D(image)) {
            return true;
        }

        final double vD = cal.pixelDepth;
        final double widthDepthRatio = vW > vD ? vW / vD : vD / vW;

        return (widthDepthRatio >= tLow && widthDepthRatio <= tHigh);
    }

    @Contract("null -> false")
    public static boolean isBinaryColour(@Nullable final ImagePlus image) {
        return image != null && Arrays.stream(image.getStatistics().histogram).filter(p -> p > 0).count() <= 2;
    }
}
