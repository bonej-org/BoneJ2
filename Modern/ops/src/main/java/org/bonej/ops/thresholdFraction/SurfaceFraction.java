package org.bonej.ops.thresholdFraction;

import net.imagej.ImgPlus;
import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.OpEnvironment;
import net.imagej.ops.geom.geom3d.mesh.Mesh;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import org.bonej.utilities.AxisUtils;
import org.scijava.plugin.Plugin;

/**
 * An Op which calculates the volume of the thresholded elements, the total volume of the image, and their ratio.
 * It calculates the volumes by creating surfaces from the elements with a marching cubes Op.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class, name = "thresholdSurfaceFraction")
public class SurfaceFraction<T extends NativeType<T> & RealType<T>> extends
        AbstractBinaryFunctionOp<ImgPlus<T>, SurfaceFraction.Settings, SurfaceFraction.Results> implements Contingent {

    /** 3D surfaces are only defined for images with three spatial dimensions  */
    @Override
    public boolean conforms() {
        return AxisUtils.countSpatialDimensions(in1()) == 3;
    }

    /** TODO Channel & time dimensions */
    @Override
    public Results compute2(final ImgPlus<T> imgPlus, final Settings settings) {
        final int[] indices = AxisUtils.getXYZIndices(imgPlus).get();
        final int xIndex = indices[0];
        final int yIndex = indices[1];
        final int zIndex = indices[2];

        final Img<BitType> thresholdMask =
                createSurfaceMask(ops(), imgPlus, xIndex, yIndex, zIndex, settings.minThreshold, settings.maxThreshold);
        final Mesh thresholdSurface = ops().geom().marchingCubes(thresholdMask);
        final double thresholdSurfaceVolume = ops().geom().size(thresholdSurface).get();

        final double min = Double.NEGATIVE_INFINITY;
        final double max = Double.POSITIVE_INFINITY;
        final Img<BitType> totalMask = createSurfaceMask(ops(), imgPlus, xIndex, yIndex, zIndex, min, max);
        final Mesh totalSurface = ops().geom().marchingCubes(totalMask);
        final double totalSurfaceVolume = ops().geom().size(totalSurface).get();

        return new Results(thresholdSurface, totalSurface, thresholdSurfaceVolume, totalSurfaceVolume);
    }

    //TODO Make an independent Op?
    public static <T extends NativeType<T> & RealType<T>> Img<BitType> createSurfaceMask(final OpEnvironment ops,
            final ImgPlus<T> imgPlus, final int xIndex, final int yIndex, final int zIndex, final double minThreshold,
            final double maxThreshold) {
        final long width = imgPlus.dimension(xIndex);
        final long height = imgPlus.dimension(yIndex);
        final long depth = imgPlus.dimension(zIndex);
        final Dimensions dimensions = new FinalDimensions(width, height, depth);
        final Img<BitType> mask = ops.create().img(dimensions, new BitType());
        final RandomAccess<BitType> maskAccess = mask.randomAccess();
        final RandomAccess<T> imgAccess = imgPlus.randomAccess();
        final T min = imgPlus.firstElement().createVariable();
        min.setReal(minThreshold);
        final T max = imgPlus.firstElement().createVariable();
        max.setReal(maxThreshold);

        for (long z = 0; z < depth; z++) {
            imgAccess.setPosition(z, zIndex);
            maskAccess.setPosition(z, 2);
            for (long y = 0; y < height; y++) {
                imgAccess.setPosition(y, yIndex);
                maskAccess.setPosition(y, 1);
                for (int x = 0; x < width; x++) {
                    imgAccess.setPosition(x, xIndex);
                    maskAccess.setPosition(x, 0);
                    final T element = imgAccess.get();
                    if (element.compareTo(min) >= 0 && element.compareTo(max) <= 0) {
                        maskAccess.get().setOne();
                    }
                }
            }
        }

        return mask;
    }

    //region -- Helper classes --

    /** A helper class to pass inputs while keeping the Op binary */
    public static final class Settings {
        /** Minimum value for elements within threshold */
        public final double minThreshold;
        /** Maximum value for elements within threshold */
        public final double maxThreshold;

        public Settings(final double minThreshold, final double maxThreshold) {
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }
    }

    /** A helper class for passing outputs */
    public static final class Results {
        /** A surface mesh created from the elements within the thresholds */
        public final Mesh thresholdSurface;
        /** A surface mesh created from all the elements */
        public final Mesh totalSurface;
        public final double thresholdSurfaceVolume;
        public final double totalSurfaceVolume;
        /** Ratio of threshold / total surface volumes */
        public final double ratio;

        public Results(final Mesh thresholdSurface, final Mesh totalSurface, final double thresholdSurfaceVolume,
                final double totalSurfaceVolume) {
            this.thresholdSurface = thresholdSurface;
            this.totalSurface = totalSurface;
            this.thresholdSurfaceVolume = thresholdSurfaceVolume;
            this.totalSurfaceVolume = totalSurfaceVolume;
            this.ratio = thresholdSurfaceVolume / totalSurfaceVolume;
        }
    }
    //endregion
}
