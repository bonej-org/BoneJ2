package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.BooleanType;
import net.imglib2.view.Views;
import org.bonej.utilities.AxisUtils;
import org.scijava.plugin.Plugin;

import java.util.Optional;

/**
 * An Op which calculates the euler characteristic (χ) of the given image.
 * Here Euler characteristic is defined as χ = β_0 - β_1 + β_2, where β_i are so called Betti numbers.
 * β_0 = number of separate particles
 * β_1 = number of handles
 * β_2 = number enclosed cavities
 * <p>
 * The Op calculates χ by using the triangulation algorithm described by Toriwaki & Yonekura (see below).
 * There it's calculated X = ∑Δχ(V), where V is a 2x2x2 neighborhood around each point in the 3D space.
 * We are using the 26-neighborhood version of the algorithm. The Δχ(V) values here are predetermined.
 * <p>
 * For the algorithm see
 * Toriwaki J, Yonekura T (2002) Euler Number and Connectivity Indexes of a Three Dimensional Digital Picture.
 * Forma 17: 183-209.
 * <a href="http://www.scipress.org/journals/forma/abstract/1703/17030183.html">
 * http://www.scipress.org/journals/forma/abstract/1703/17030183.html</a>
 * <p>
 * For the Betti number definition of Euler characteristic see
 * Odgaard A, Gundersen HJG (1993) Quantification of connectivity in cancellous bone,
 * with special emphasis on 3-D reconstructions.
 * Bone 14: 173-182.
 * <a href="http://dx.doi.org/10.1016/8756-3282(93)90245-6">doi:10.1016/8756-3282(93)90245-6</a>
 *
 * @author Richard Domander
 * @author Michael Doube
 * //TODO Fix generic signature
 */
@Plugin(type = Op.class, name = "eulerCharacteristic")
public class EulerCharacteristic<B extends BooleanType> extends AbstractUnaryFunctionOp<ImgPlus<B>, Integer> implements
        Contingent {
    /**Δχ(v) for all 256 possible configurations of a 2x2x2 voxel neighborhood  */
    private static final int[] EULER_LUT = {
             0,  1,  1,  0,  1,  0, -2, -1,  1, -2,  0, -1,  0, -1, -1,  0,
             1,  0, -2, -1, -2, -1, -1, -2, -6, -3, -3, -2, -3, -2,  0, -1,
             1, -2,  0, -1, -6, -3, -3, -2, -2, -1, -1, -2, -3,  0, -2, -1,
             0, -1, -1,  0, -3, -2,  0, -1, -3,  0, -2, -1,  0,  1,  1,  0,
             1, -2, -6, -3,  0, -1, -3, -2, -2, -1, -3,  0, -1, -2, -2, -1,
             0, -1, -3, -2, -1,  0,  0, -1, -3,  0,  0,  1, -2, -1,  1,  0,
            -2, -1, -3,  0, -3,  0,  0,  1, -1,  4,  0,  3,  0,  3,  1,  2,
            -1, -2, -2, -1, -2, -1,  1,  0,  0,  3,  1,  2,  1,  2,  2,  1,
             1, -6, -2, -3, -2, -3, -1,  0,  0, -3, -1, -2, -1, -2, -2, -1,
            -2, -3, -1,  0, -1,  0,  4,  3, -3,  0,  0,  1,  0,  1,  3,  2,
             0, -3, -1, -2, -3,  0,  0,  1, -1,  0,  0, -1, -2,  1, -1,  0,
            -1, -2, -2, -1,  0,  1,  3,  2, -2,  1, -1,  0,  1,  2,  2,  1,
             0, -3, -3,  0, -1, -2,  0,  1, -1,  0, -2,  1,  0, -1, -1,  0,
            -1, -2,  0,  1, -2, -1,  3,  2, -2,  1,  1,  2, -1,  0,  2,  1,
            -1,  0, -2,  1, -2,  1,  1,  2, -2,  3, -1,  2, -1,  2,  0,  1,
             0, -1, -1,  0, -1,  0,  2,  1, -1,  2,  0,  1,  0,  1,  1,  0
    };

    /** The algorithm is only defined for 3D images  */
    @Override
    public boolean conforms() {
        return AxisUtils.countSpatialDimensions(in()) == 3;
    }

    @Override
    public Integer compute1(final ImgPlus<B> imgPlus) {
        final RandomAccess<B> access = Views.extendZero(imgPlus).randomAccess();
        final Cursor<B> cursor = imgPlus.localizingCursor();
        final Optional<int[]> optional = AxisUtils.getXYZIndices(imgPlus);
        final int[] indices = optional.get();
        final int xIndex = indices[0];
        final int yIndex = indices[1];
        final int zIndex = indices[2];
        final int[] sumDeltaEuler = {0};

        cursor.forEachRemaining(e -> {
            final long x = cursor.getLongPosition(xIndex);
            final long y = cursor.getLongPosition(yIndex);
            final long z = cursor.getLongPosition(zIndex);
            int index = neighborhoodEulerIndex(access, x, y, z, xIndex, yIndex, zIndex);
            sumDeltaEuler[0] += EULER_LUT[index];
        });

        return (int) Math.round(sumDeltaEuler[0] / 8.0);
    }

    /**
     * Determines the LUT index for this 2x2x2 neighborhood
     *
     * @implNote Public and static only for testing purposes
     * @param access    The space where the neighborhood is
     * @param x         Location of the neighborhood in the 1st spatial dimension (x)
     * @param y         Location of the neighborhood in the 2nd spatial dimension (y)
     * @param z         Location of the neighborhood in the 3rd spatial dimension (z)
     * @return index of the Δχ value of for configuration of voxels
     */
    public static <B extends BooleanType> int neighborhoodEulerIndex(final RandomAccess<B> access, final long x,
            final long y, final long z, final int xIndex, final int yIndex, final int zIndex) {
        int index = 0;

        index += getAtLocation(access, x, y, z, xIndex, yIndex, zIndex);
        index += getAtLocation(access, x + 1, y, z, xIndex, yIndex, zIndex)         << 1;
        index += getAtLocation(access, x, y + 1, z, xIndex, yIndex, zIndex)         << 2;
        index += getAtLocation(access, x + 1, y + 1, z, xIndex, yIndex, zIndex)     << 3;
        index += getAtLocation(access, x, y, z + 1, xIndex, yIndex, zIndex)         << 4;
        index += getAtLocation(access, x + 1, y, z + 1, xIndex, yIndex, zIndex)     << 5;
        index += getAtLocation(access, x, y + 1, z + 1, xIndex, yIndex, zIndex)     << 6;
        index += getAtLocation(access, x + 1, y + 1, z + 1, xIndex, yIndex, zIndex) << 7;

        return index;
    }

    private static <B extends BooleanType> int getAtLocation(final RandomAccess<B> access, final long x, final long y,
            final long z, final int xIndex, final int yIndex, final int zIndex) {
        access.setPosition(x, xIndex);
        access.setPosition(y, yIndex);
        access.setPosition(z, zIndex);
        return (int) access.get().getRealDouble();
    }
}
