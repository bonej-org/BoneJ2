package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Cursor;
import net.imglib2.type.BooleanType;
import org.scijava.plugin.Plugin;

import java.util.Arrays;

/**
 * An Op which calculates the euler characteristic (χ) of the given image.
 * Euler characteristic is a number that describes a topological space's structure.
 * <p>
 * The algorithm here are based on the following articles:
 * Toriwaki J, Yonekura T (2002) Euler Number and Connectivity Indexes of a Three Dimensional Digital Picture.
 * Forma 17: 183-209.
 * <a href="http://www.scipress.org/journals/forma/abstract/1703/17030183.html">
 * http://www.scipress.org/journals/forma/abstract/1703/17030183.html</a>
 *
 * @implNote Assuming that there's only one continuous foreground particle in the image
 * @author Michael Doube
 * @author Richard Domander 
 */
@Plugin(type = Op.class, name = "eulerCharacteristic")
public class EulerCharacteristic<B extends BooleanType> extends AbstractUnaryFunctionOp<ImgPlus<B>, Integer> {
    // TODO Determine indices of spatial dimensions
    private static final int U_INDEX = 0;
    private static final int V_INDEX = 1;
    private static final int W_INDEX = 2;
    private static final int[] EULER_LUT = new int[256];

    //region fill EULER_LUT
    static {
        EULER_LUT[1] = 1;
        EULER_LUT[7] = -1;
        EULER_LUT[9] = -2;
        EULER_LUT[11] = -1;
        EULER_LUT[13] = -1;

        EULER_LUT[19] = -1;
        EULER_LUT[21] = -1;
        EULER_LUT[23] = -2;
        EULER_LUT[25] = -3;
        EULER_LUT[27] = -2;

        EULER_LUT[29] = -2;
        EULER_LUT[31] = -1;
        EULER_LUT[33] = -2;
        EULER_LUT[35] = -1;
        EULER_LUT[37] = -3;

        EULER_LUT[39] = -2;
        EULER_LUT[41] = -1;
        EULER_LUT[43] = -2;
        EULER_LUT[47] = -1;
        EULER_LUT[49] = -1;

        EULER_LUT[53] = -2;
        EULER_LUT[55] = -1;
        EULER_LUT[59] = -1;
        EULER_LUT[61] = 1;
        EULER_LUT[65] = -2;

        EULER_LUT[67] = -3;
        EULER_LUT[69] = -1;
        EULER_LUT[71] = -2;
        EULER_LUT[73] = -1;
        EULER_LUT[77] = -2;

        EULER_LUT[79] = -1;
        EULER_LUT[81] = -1;
        EULER_LUT[83] = -2;
        EULER_LUT[87] = -1;
        EULER_LUT[91] = 1;

        EULER_LUT[93] = -1;
        EULER_LUT[97] = -1;
        EULER_LUT[103] = 1;
        EULER_LUT[105] = 4;
        EULER_LUT[107] = 3;

        EULER_LUT[109] = 3;
        EULER_LUT[111] = 2;
        EULER_LUT[113] = -2;
        EULER_LUT[115] = -1;
        EULER_LUT[117] = -1;
        EULER_LUT[121] = 3;

        EULER_LUT[123] = 2;
        EULER_LUT[125] = 2;
        EULER_LUT[127] = 1;
        EULER_LUT[129] = -6;
        EULER_LUT[131] = -3;

        EULER_LUT[133] = -3;
        EULER_LUT[137] = -3;
        EULER_LUT[139] = -2;
        EULER_LUT[141] = -2;
        EULER_LUT[143] = -1;

        EULER_LUT[145] = -3;
        EULER_LUT[151] = 3;
        EULER_LUT[155] = 1;
        EULER_LUT[157] = 1;
        EULER_LUT[159] = 2;

        EULER_LUT[161] = -3;
        EULER_LUT[163] = -2;
        EULER_LUT[167] = 1;
        EULER_LUT[171] = -1;
        EULER_LUT[173] = 1;

        EULER_LUT[177] = -2;
        EULER_LUT[179] = -1;
        EULER_LUT[181] = 1;
        EULER_LUT[183] = 2;
        EULER_LUT[185] = 1;

        EULER_LUT[189] = 2;
        EULER_LUT[191] = 1;
        EULER_LUT[193] = -3;
        EULER_LUT[197] = -2;
        EULER_LUT[199] = 1;

        EULER_LUT[203] = 1;
        EULER_LUT[205] = -1;
        EULER_LUT[209] = -2;
        EULER_LUT[211] = 1;
        EULER_LUT[213] = -1;

        EULER_LUT[215] = 2;
        EULER_LUT[217] = 1;
        EULER_LUT[219] = 2;
        EULER_LUT[223] = 1;
        EULER_LUT[227] = 1;

        EULER_LUT[229] = 1;
        EULER_LUT[231] = 2;
        EULER_LUT[233] = 3;
        EULER_LUT[235] = 2;
        EULER_LUT[237] = 2;

        EULER_LUT[239] = 1;
        EULER_LUT[241] = -1;
        EULER_LUT[247] = 1;
        EULER_LUT[249] = 2;
        EULER_LUT[251] = 1;

        EULER_LUT[253] = 1;
    }
    //endregion

    @Override
    public Integer compute1(final ImgPlus<B> imgPlus) {
        final int[] eulerSums = new int[(int) imgPlus.dimension(W_INDEX)];
        final Cursor<B> cursor = imgPlus.localizingCursor();
        final Octant<B> octant = new Octant<>(imgPlus);

        cursor.forEachRemaining(c -> {
            long u = cursor.getLongPosition(U_INDEX);
            long v = cursor.getLongPosition(V_INDEX);
            long w = cursor.getLongPosition(W_INDEX);
            octant.setNeighborhood(u, v, w);
            eulerSums[(int) w] += getDeltaEuler(octant);
        });

        return (int) Math.round(Arrays.stream(eulerSums).sum() / 8.0);
    }

    private static int getDeltaEuler(final Octant octant) {
        if (octant.isNeighborhoodEmpty()) {
            return 0;
        }

        int index = 1;
        if (octant.isNeighborForeground(8)) {
            if (octant.isNeighborForeground(1)) { index |= 128; }
            if (octant.isNeighborForeground(2)) { index |= 64; }
            if (octant.isNeighborForeground(3)) { index |= 32; }
            if (octant.isNeighborForeground(4)) { index |= 16; }
            if (octant.isNeighborForeground(5)) { index |= 8; }
            if (octant.isNeighborForeground(6)) { index |= 4; }
            if (octant.isNeighborForeground(7)) { index |= 2; }
        } else if (octant.isNeighborForeground(7)) {
            if (octant.isNeighborForeground(2)) { index |= 128; }
            if (octant.isNeighborForeground(4)) { index |= 64; }
            if (octant.isNeighborForeground(1)) { index |= 32; }
            if (octant.isNeighborForeground(3)) { index |= 16; }
            if (octant.isNeighborForeground(6)) { index |= 8; }
            if (octant.isNeighborForeground(5)) { index |= 2; }
        } else if (octant.isNeighborForeground(6)) {
            if (octant.isNeighborForeground(3)) { index |= 128; }
            if (octant.isNeighborForeground(1)) { index |= 64; }
            if (octant.isNeighborForeground(4)) { index |= 32; }
            if (octant.isNeighborForeground(2)) { index |= 16; }
            if (octant.isNeighborForeground(5)) { index |= 4; }
        } else if (octant.isNeighborForeground(5)) {
            if (octant.isNeighborForeground(4)) { index |= 128; }
            if (octant.isNeighborForeground(3)) { index |= 64; }
            if (octant.isNeighborForeground(2)) { index |= 32; }
            if (octant.isNeighborForeground(1)) { index |= 16; }
        } else if (octant.isNeighborForeground(4)) {
            if (octant.isNeighborForeground(1)) { index |= 8; }
            if (octant.isNeighborForeground(3)) { index |= 4; }
            if (octant.isNeighborForeground(2)) { index |= 2; }
        } else if (octant.isNeighborForeground(3)) {
            if (octant.isNeighborForeground(2)) { index |= 8; }
            if (octant.isNeighborForeground(1)) { index |= 4; }
        } else if (octant.isNeighborForeground(2)) {
            if (octant.isNeighborForeground(1)) { index |= 2; }
        }

        return EULER_LUT[index];
    }
}
