package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.RandomAccess;
import net.imglib2.type.BooleanType;
import net.imglib2.view.Views;
import org.bonej.utilities.AxisUtils;
import org.scijava.plugin.Plugin;

import java.util.Optional;

/**
 * An Op which calculates the edge correction needed for the Euler characteristic of the image to approximate its
 * contribution to the whole image. That is, it's assumed that the image is a small part cut from a larger image.
 * <p>
 * Based on the article
 * Odgaard A, Gundersen HJG (1993) Quantification of connectivity in cancellous bone,
 * with special emphasis on 3-D reconstructions.
 * Bone 14: 173-182.
 * <a href="http://dx.doi.org/10.1016/8756-3282(93)90245-6">doi:10.1016/8756-3282(93)90245-6</a>
 *
 * @author Michael Doube
 * @author Richard Domander
 * @implNote Methods are written statically to help testing
 */
@Plugin(type = Op.class, name = "eulerContribution")
public class EulerContribution<B extends BooleanType<B>> extends AbstractUnaryFunctionOp<ImgPlus<B>, Double> implements
        Contingent {
    /** The algorithm is only defined for 3D images  */
    @Override
    public boolean conforms() {
        return AxisUtils.countSpatialDimensions(in()) == 3;
    }

    @Override
    public Double compute1(final ImgPlus<B> imgPlus) {
        final Traverser<B> traverser = new Traverser<>(imgPlus);
        final int chiZero = cornerVertices(traverser);

        return chiZero / 8.0;
    }

    /**
     * Calculates χ_0 from Odgaard and Gundersen, i.e. counts the number of foreground voxels in stack corners
     * @implNote Public and static for testing purposes
     */
    public static <B extends BooleanType<B>> int cornerVertices(final Traverser<B> traverser) {
        int foreground = 0;
        foreground += getAtLocation(traverser, traverser.x0, traverser.y0, traverser.z0);
        foreground += getAtLocation(traverser, traverser.x1, traverser.y0, traverser.z0);
        foreground += getAtLocation(traverser, traverser.x1, traverser.y1, traverser.z0);
        foreground += getAtLocation(traverser, traverser.x0, traverser.y1, traverser.z0);
        foreground += getAtLocation(traverser, traverser.x0, traverser.y0, traverser.z1);
        foreground += getAtLocation(traverser, traverser.x1, traverser.y0, traverser.z1);
        foreground += getAtLocation(traverser, traverser.x1, traverser.y1, traverser.z1);
        foreground += getAtLocation(traverser, traverser.x0, traverser.y1, traverser.z1);
        return foreground;
    }

    //region -- Helper methods --
    private static <B extends BooleanType<B>> int getAtLocation(final Traverser<B> traverser, final long x,
            final long y, final long z) {
        traverser.access.setPosition(x, traverser.xIndex);
        traverser.access.setPosition(y, traverser.yIndex);
        traverser.access.setPosition(z, traverser.zIndex);
        return (int) traverser.access.get().getRealDouble();
    }
    //endregion

    /** A convenience class for passing parameters */
    public static class Traverser<B extends BooleanType<B>> {
        public final long x0 = 0;
        public final long y0 = 0;
        public final long z0 = 0;
        public final long x1;
        public final long y1;
        public final long z1;
        public final int xIndex;
        public final int yIndex;
        public final int zIndex;
        public final long xSize;
        public final long ySize;
        public final long zSize;
        public final RandomAccess<B> access;

        public Traverser(ImgPlus<B> imgPlus) {
            final Optional<int[]> optional = AxisUtils.getXYZIndices(imgPlus);
            final int[] indices = optional.get();

            xIndex = indices[0];
            yIndex = indices[1];
            zIndex = indices[2];
            xSize = imgPlus.dimension(xIndex);
            ySize = imgPlus.dimension(yIndex);
            zSize = imgPlus.dimension(zIndex);
            x1 = xSize - 1;
            y1 = ySize - 1;
            z1 = zSize - 1;
            access = Views.extendZero(imgPlus).randomAccess();
        }
    }
}
