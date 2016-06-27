package org.bonej.testImages;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.OpEnvironment;
import net.imglib2.FinalDimensions;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;

/**
 * A utility class to create an ImgPlus that follows the conventions of a hyperstack in ImageJ1.
 * Needed so that the image display correctly (at least in the legacy ui).
 *
 * @author Richard Domander
 */
public class IJ1ImgPlus {
    public static final int DIMENSIONS = 5;
    public static final int X_DIM = 0;
    public static final int Y_DIM = 1;
    public static final int CHANNEL_DIM = 2;
    public static final int Z_DIM = 3;
    public static final int TIME_DIM = 4;
    public static final AxisType[] IJ1_AXES;

    static {
        IJ1_AXES = new AxisType[DIMENSIONS];
        IJ1_AXES[X_DIM] = Axes.X;
        IJ1_AXES[Y_DIM] = Axes.Y;
        IJ1_AXES[Z_DIM] = Axes.Z;
        IJ1_AXES[CHANNEL_DIM] = Axes.CHANNEL;
        IJ1_AXES[TIME_DIM] = Axes.TIME;
    }

    private IJ1ImgPlus() {}


    /**
     * Creates a 5-dimensional ImgPlus with no calibration or padding
     * @see IJ1ImgPlus#createIJ1ImgPlus(OpEnvironment, String, long, long, long, long, long, long, double, String) createIJ1ImgPlus
     */
    public static ImgPlus<BitType> createIJ1ImgPlus(final OpEnvironment ops, String title, final long width,
                                                    final long height, final long depth, final long channels,
                                                    final long frames) {
        return createIJ1ImgPlus(ops, title, width, height, depth, channels, frames, 0, 1.0, "");
    }

    /**
     * Creates a 5-dimensional ImgPlus
     *
     * @param title     Name of the image
     * @param channels  Number of colour channels
     * @param frames    Number of frames
     * @param padding   Padding added to width, height & depth (final width = width + 2 * padding)
     * @param scale     Scale of calibration in x, y & z
     * @param unit      Unit of calibration in x, y & z
     * @return An empty ImgPlus
     */
    public static ImgPlus<BitType> createIJ1ImgPlus(final OpEnvironment ops, String title, final long width,
            final long height, final long depth, final long channels, final long frames, final long padding,
            final double scale, final String unit) {
        final long totalPadding = 2 * padding;
        final Img<BitType> img = ops.create().img(
                new FinalDimensions(width + totalPadding, height + totalPadding,
                                    channels, depth + totalPadding, frames), new BitType());
        double[] calibration = new double[]{scale, scale, 1.0, scale, 1.0};
        String[] units = new String[]{unit, unit, "", unit, ""};
        return new ImgPlus<>(img, title, IJ1_AXES, calibration, units);
    }
}
