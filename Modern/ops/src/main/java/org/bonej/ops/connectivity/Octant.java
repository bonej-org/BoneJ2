package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imglib2.RandomAccess;
import net.imglib2.type.BooleanType;
import net.imglib2.view.Views;

import java.util.Arrays;

/**
 * A convenience class for storing a 2x2x2 voxel neighborhood
 *
 * @author Richard Domander
 * @author Mark Hiner
 */
public class Octant<B extends BooleanType<B>> {
    private final boolean[] neighborhood = new boolean[8];
    private int foregroundNeighbors;
    private RandomAccess<B> access;
    private int xIndex;
    private int yIndex;
    private int zIndex;

    public Octant(final ImgPlus<B> imgPlus, final int xIndex, final int yIndex, final int zIndex) {
        setInterval(imgPlus, xIndex, yIndex, zIndex);
    }

    public int getNeighborCount() {
        return foregroundNeighbors;
    }

    /**
     * Check if the nth neighbor in the 8-neighborhood is foreground
     *
     * @param n number of neighbor, 1 <= n <= 8
     */
    public boolean isNeighborForeground(final int n) {
        return neighborhood[n - 1];
    }

    /** True if none of the elements in the neighborhood are foreground (true) */
    public boolean isNeighborhoodEmpty() {
        return foregroundNeighbors == 0;
    }

    /**
     * Sets the imgPlus where the neighborhood is located
     *
     * @param xIndex Index of the 1st spatial axis in the imgPlus
     * @param yIndex Index of the 2nd spatial axis in the imgPlus
     * @param zIndex Index of the 3rd spatial axis in the imgPlus
     * @implNote Copies reference
     */
    public void setInterval(ImgPlus<B> imgPlus, final int xIndex, final int yIndex, final int zIndex)
            throws NullPointerException, IllegalArgumentException {
        this.xIndex = xIndex;
        this.yIndex = yIndex;
        this.zIndex = zIndex;
        access = Views.extendZero(imgPlus).randomAccess();
    }

    /** Set the starting coordinates of the neighborhood in the interval */
    public void setNeighborhood(final long x, final long y, final long z) {
        Arrays.fill(neighborhood, false);

        neighborhood[0] = getAtLocation(access, x - 1, y - 1, z - 1);
        neighborhood[1] = getAtLocation(access, x - 1, y, z - 1);
        neighborhood[2] = getAtLocation(access, x, y - 1, z - 1);
        neighborhood[3] = getAtLocation(access, x, y, z - 1);
        neighborhood[4] = getAtLocation(access, x - 1, y - 1, z);
        neighborhood[5] = getAtLocation(access, x - 1, y, z);
        neighborhood[6] = getAtLocation(access, x, y - 1, z);
        neighborhood[7] = getAtLocation(access, x, y, z);

        countForegroundNeighbors();
    }

    private void countForegroundNeighbors() {
        foregroundNeighbors = 0;
        for (boolean neighbor : neighborhood) {
            if (neighbor) {
                foregroundNeighbors++;
            }
        }
    }

    private boolean getAtLocation(final RandomAccess<B> access, final long x, final long y, final long z) {
        access.setPosition(x, xIndex);
        access.setPosition(y, yIndex);
        access.setPosition(z, zIndex);
        return access.get().get();
    }
}
