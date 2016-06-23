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

import java.util.stream.LongStream;

/**
 * Creates an ImgPlus<BitType> of a hollow cuboid.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class, name = "hollowCuboid", menuPath = "Plugins>Test Images>Hollow cuboid")
public class HollowCuboid extends AbstractNullaryHybridCF<ImgPlus<BitType>> {
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
        return new ImgPlus<>(img, "Hollow cuboid", AXIS_TYPES, calibration, units);
    }

    @Override
    public void compute0(final ImgPlus<BitType> cuboid) {
        final RandomAccess<BitType> access = cuboid.randomAccess();

        drawFaces(access, X_DIM, Y_DIM, Z_DIM, xSize, ySize, zSize);
        drawFaces(access, X_DIM, Z_DIM, Y_DIM, xSize, zSize, ySize);
        drawFaces(access, Y_DIM, Z_DIM, X_DIM, ySize, zSize, xSize);
    }

    /**
     * Draw two faces of the cuboid
     *
     * @param faceDim1              Index of the 1st spatial dimension of the faces in the axes table
     * @param faceDim2              Index of the 2nd spatial dimension of the faces in the axes table
     * @param orthogonalDim         Index of the dimension orthogonal to the faces
     * @param faceSize1             Size of the face in the 1st spatial dimension
     * @param faceSize2             Size of the face in the 2nd spatial dimension
     * @param orthogonalDistance    Spacing between the faces in the orthogonal dimension
     */
    private void drawFaces(RandomAccess<BitType> access, final int faceDim1, final int faceDim2,
            final int orthogonalDim, final long faceSize1, final long faceSize2, final long orthogonalDistance) {
        LongStream.of(padding, padding + orthogonalDistance - 1).forEach(i -> {
            access.setPosition(i, orthogonalDim);
            for (long j = padding; j < padding + faceSize1; j++) {
                access.setPosition(j, faceDim1);
                for (long k = padding; k < padding + faceSize2; k++) {
                    access.setPosition(k, faceDim2);
                    access.get().setOne();
                }
            }
        });

        // Reset positions for next call
        access.setPosition(0, orthogonalDim);
        access.setPosition(0, faceDim1);
        access.setPosition(0, faceDim2);
    }

    public static void main(String... args) {
        final ImageJ ij = net.imagej.Main.launch(args);
        // Call the hybrid op without a ready buffer (null)
        Object cuboid = ij.op().run(HollowCuboid.class, null, 100L, 100L, 10L, 1L, 1L, 5L);
        ij.ui().show(cuboid);
    }
}
