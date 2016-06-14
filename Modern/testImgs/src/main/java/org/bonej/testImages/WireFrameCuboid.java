package org.bonej.testImages;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.Op;
import net.imagej.ops.OpService;
import net.imagej.ops.special.hybrid.AbstractNullaryHybridCF;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Creates an ImgPlus<BitType> of a wire-frame cuboid.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class, name = "wireFrameCuboid", menuPath = "Plugins>Test Images>Wire-frame cuboid")
public class WireFrameCuboid extends AbstractNullaryHybridCF<ImgPlus<BitType>> {
    private static final int X_DIM = 0;
    private static final int Y_DIM = 1;
    private static final int Z_DIM = 3;
    private final long[] cuboidLocation = new long[5];

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
    private long padding;

    @Parameter(label = "Scale", description = "The scale calibration", min = "0.0", required = false)
    private double scale = 1.0;

    @Parameter(label = "Unit", description = "The unit of calibration", required = false)
    private String unit = "";


    @Parameter
    private OpService opService;

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
        return new ImgPlus<>(img, "Wire-frame cuboid", AXIS_TYPES, calibration, units);
    }

    @Override
    public void compute0(final ImgPlus<BitType> output) {
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) {
                drawCuboidEdges(output, c, f);
            }
        }
    }

    //region -- Helper methods --
    private void drawCuboidEdges(final ImgPlus<BitType> cuboid, final long channel, final long frame) {
        final long x0 = padding;
        final long x1 = padding + xSize - 1;
        final long y0 = padding;
        final long y1 = padding + ySize - 1;
        final long z0 = padding;
        final long z1 = padding + zSize - 1;

        setCuboidLocation(x0, y0, z0, channel, frame);
        drawLine(cuboid, X_DIM, xSize);
        drawLine(cuboid, Y_DIM, ySize);
        drawLine(cuboid, Z_DIM, zSize);
        setCuboidLocation(x1, y0, z0, channel, frame);
        drawLine(cuboid, Y_DIM, ySize);
        drawLine(cuboid, Z_DIM, zSize);
        setCuboidLocation(x1, y1, z0, channel, frame);
        drawLine(cuboid, Z_DIM, zSize);
        setCuboidLocation(x0, y1, z0, channel, frame);
        drawLine(cuboid, X_DIM, ySize);
        drawLine(cuboid, Z_DIM, zSize);
        setCuboidLocation(x0, y0, z1, channel, frame);
        drawLine(cuboid, X_DIM, xSize);
        drawLine(cuboid, Y_DIM, ySize);
        setCuboidLocation(x1, y0, z1, channel, frame);
        drawLine(cuboid, Y_DIM, ySize);
        setCuboidLocation(x0, y1, z1, channel, frame);
        drawLine(cuboid, X_DIM, xSize);
    }

    private void drawLine(final ImgPlus<BitType> cuboid, final int dim, final long length) {
        final RandomAccess<BitType> randomAccess = cuboid.randomAccess();
        randomAccess.setPosition(cuboidLocation);

        for (int i = 0; i < length; i++) {
            randomAccess.get().setOne();
            randomAccess.fwd(dim);
        }
    }

    private void setCuboidLocation(final long x, final long y, final long z, final long channel, final long frame) {
        cuboidLocation[X_DIM] = x;
        cuboidLocation[Y_DIM] = y;
        cuboidLocation[2] = channel;
        cuboidLocation[Z_DIM] = z;
        cuboidLocation[4] = frame;
    }
    //endregion

    public static void main(String... args) {
        final ImageJ ij = net.imagej.Main.launch(args);
        // Call the hybrid op without a ready buffer (null)
        Object cuboid = ij.op().run(WireFrameCuboid.class, null, 100L, 100L, 10L, 3L, 10L, 5L);
        ij.ui().show(cuboid);
    }
}
