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

    @Contract("null -> false")
    public static boolean isBinaryColour(@Nullable final ImagePlus image) {
        return image != null && Arrays.stream(image.getStatistics().histogram).filter(p -> p > 0).count() <= 2;
    }


    /**
     * Calculates the degree of anisotropy in the image, i.e. the maximum difference in the ratios of the dimensions
     *
     * @return Anisotropy fraction [0.0, Double.MAX_VALUE], an isotropic image returns 0.0
     *         Returns Double.NaN if image == null
     */
    public static double anisotropy(@Nullable final ImagePlus image) {
        if (image == null) {
            return Double.NaN;
        }

        final Calibration cal = image.getCalibration();
        final double w = cal.pixelWidth;
        final double h = cal.pixelHeight;
        final double widthHeightRatio = w > h ? w / h : h / w;
        final double widthHeightAnisotropy = Math.abs(1.0 - widthHeightRatio);

        if (!is3D(image)) {
            return widthHeightAnisotropy;
        }

        final double d = cal.pixelDepth;
        final double widthDepthRatio = w > d ? w / d : d / w;
        final double heightDepthRatio = h > d ? h / d : d / h;
        final double widthDepthAnisotropy = Math.abs(1.0 - widthDepthRatio);
        final double heightDepthAnisotropy = Math.abs(1.0 - heightDepthRatio);

        return Math.max(widthHeightAnisotropy, Math.max(widthDepthAnisotropy, heightDepthAnisotropy));
    }
}
