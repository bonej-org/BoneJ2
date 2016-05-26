package org.bonej.utilities;

import ij.ImagePlus;
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
    public static boolean isBinaryColour(@Nullable final ImagePlus image) {
        return image != null && Arrays.stream(image.getStatistics().histogram).filter(p -> p > 0).count() <= 2;
    }
}
