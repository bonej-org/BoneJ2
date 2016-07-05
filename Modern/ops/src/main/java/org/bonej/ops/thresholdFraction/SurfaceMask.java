package org.bonej.ops.thresholdFraction;

import net.imagej.ImgPlus;
import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
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
 * An Op which creates an Img<BitType> mask of the given image. The mask can be used to create a surface mesh
 * with a marching cubes Op.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class, name = "surfaceMask")
public class SurfaceMask<T extends NativeType<T> & RealType<T>> extends
        AbstractBinaryFunctionOp<ImgPlus<T>, Thresholds<T>, Img<BitType>> implements Contingent {

    /** Marching cubes is only defined for 3D images */
    @Override
    public boolean conforms() {
        return AxisUtils.countSpatialDimensions(in1()) == 3;
    }

    @Override
    public Img<BitType> compute2(final ImgPlus<T> imgPlus, final Thresholds<T> thresholds) {
        final int[] indices = AxisUtils.getXYZIndices(imgPlus).get();
        final int xIndex = indices[0];
        final int yIndex = indices[1];
        final int zIndex = indices[2];
        final long width = imgPlus.dimension(xIndex);
        final long height = imgPlus.dimension(yIndex);
        final long depth = imgPlus.dimension(zIndex);

        final Dimensions dimensions = new FinalDimensions(width, height, depth);
        final Img<BitType> mask = ops().create().img(dimensions, new BitType());
        final RandomAccess<BitType> maskAccess = mask.randomAccess();
        final RandomAccess<T> imgAccess = imgPlus.randomAccess();

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
                    if (element.compareTo(thresholds.min) >= 0 && element.compareTo(thresholds.max) <= 0) {
                        maskAccess.get().setOne();
                    }
                }
            }
        }

        return mask;
    }
}
