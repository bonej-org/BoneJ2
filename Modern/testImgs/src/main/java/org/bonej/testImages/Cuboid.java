package org.bonej.testImages;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.Op;
import net.imagej.ops.special.hybrid.AbstractNullaryHybridCF;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Creates an ImgPlus<BitType> of a solid cuboid.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class, name = "cuboid", menuPath = "Plugins>Test Images>Cuboid")
public class Cuboid extends AbstractNullaryHybridCF<ImgPlus<BitType>> {
    private static final int X_DIM = 0;
    private static final int Y_DIM = 1;
    private static final int CHANNEL_DIM = 2;
    private static final int Z_DIM = 3;
    private static final int TIME_DIM = 4;

    @Parameter(label = "X-size", description = "Cuboid width", min = "1")
    private long xSize = 50;

    @Parameter(label = "Y-size", description = "Cuboid height", min = "1")
    private long ySize = 50;

    @Parameter(label = "Z-size", description = "Cuboid depth", min = "1")
    private long zSize = 50;

    @Parameter(label = "Channels", description = "Colour channels", min = "1", max = "4", required = false)
    private long channels = 1;

    @Parameter(label = "Frames", description = "Size in time dimension", min = "1", required = false)
    private long frames = 1;

    @Parameter(label = "Padding", description = "Empty space around the cuboid", min = "0", required = false)
    private long padding = 5;

    @Parameter(label = "Scale", description = "The scale calibration", min = "0.0", required = false)
    private double scale = 1.0;

    @Parameter(label = "Unit", description = "The unit of calibration", required = false)
    private String unit = "";

    /** The correct order for axis types. Without it the image won't display correctly at least in legacy ui */
    private static final AxisType[] AXIS_TYPES = new AxisType[]{Axes.X, Axes.Y, Axes.CHANNEL, Axes.Z, Axes.TIME};

    @Override
    public ImgPlus<BitType> createOutput() {
        final long totalPadding = 2 * padding;
        final Img<BitType> img =
                ops().create().img(new FinalDimensions(
                        xSize + totalPadding,
                        ySize + totalPadding,
                        channels,
                        zSize + totalPadding,
                        frames), new BitType());
        double[] calibration = new double[]{scale, scale, 1.0, scale, 1.0};
        String[] units = new String[]{unit, unit, "", unit, ""};
        return new ImgPlus<>(img, "Cuboid", AXIS_TYPES, calibration, units);
    }

    @Override
    public void compute0(ImgPlus<BitType> image) {
        final long x0 = padding;
        final long x1 = padding + xSize;
        final long y0 = padding;
        final long y1 = padding + ySize;
        final long z0 = padding;
        final long z1 = padding + zSize;
        final RandomAccess<BitType> access = image.randomAccess();

        for (long f = 0; f < frames; f++) {
            access.setPosition(f, TIME_DIM);
            for (long c = 0; c < channels; c++) {
                access.setPosition(c, CHANNEL_DIM);
                for (long z = z0; z < z1; z++) {
                    access.setPosition(z, Z_DIM);
                    for (long y = y0; y < y1; y++) {
                        access.setPosition(y, Y_DIM);
                        for (long x = x0; x < x1; x++) {
                            access.setPosition(x, X_DIM);
                            access.get().setOne();
                        }
                    }
                }
            }
        }
    }

    public static void main(String... args) {
        final ImageJ ij = net.imagej.Main.launch(args);
        // Call the hybrid op without a ready buffer (null)
        Object cuboid = ij.op().run(Cuboid.class, null, 100L, 100L, 10L, 1L, 1L, 5L);
        ij.ui().show(cuboid);
    }
}
