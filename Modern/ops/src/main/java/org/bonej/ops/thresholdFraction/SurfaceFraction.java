package org.bonej.ops.thresholdFraction;

import net.imagej.ImgPlus;
import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.geom.geom3d.mesh.Mesh;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
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
        AbstractBinaryFunctionOp<RandomAccessibleInterval<T>, Thresholds<T>, SurfaceFraction.Results> implements Contingent {

    /** (Default) marching cubes only supports RAIs with three dimensions  */
    @Override
    public boolean conforms() { return in1().numDimensions() == 3; }

    /** TODO Channel & time dimensions */
    @Override
    public Results compute2(final RandomAccessibleInterval<T> interval, final Thresholds<T> thresholds) {
        final Img thresholdMask = (Img) ops().run(SurfaceMask.class, interval, thresholds);
        final Mesh thresholdSurface = ops().geom().marchingCubes(thresholdMask);
        final double thresholdSurfaceVolume = ops().geom().size(thresholdSurface).get();

        final Thresholds<T> totalThresholds =
                new Thresholds<>(interval, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        final Img totalMask = (Img) ops().run(SurfaceMask.class, interval, totalThresholds);
        final Mesh totalSurface = ops().geom().marchingCubes(totalMask);
        final double totalSurfaceVolume = ops().geom().size(totalSurface).get();

        return new Results(thresholdSurface, totalSurface, thresholdSurfaceVolume, totalSurfaceVolume);
    }

    //region -- Helper classes --

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
