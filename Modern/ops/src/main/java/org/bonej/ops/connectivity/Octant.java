package org.bonej.ops.connectivity;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;
import net.imglib2.view.Views;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A convenience class for storing a special 8-neighborhood in a 3D BitType interval
 *
 * @author Richard Domander
 * @author Mark Hiner
 */
public final class Octant<B extends BooleanType> {
    private final boolean[] neighborhood = new boolean[8];
    private int foregroundNeighbors;
    private RandomAccess<B> access;

    public Octant(final RandomAccessibleInterval<B> interval) {
        setInterval(interval);
    }

    public int getNeighborCount() {
        return foregroundNeighbors;
    }

    /**
     * Check if the nth neighbor in the 8-neighborhood is foreground
     *
     * @param n 1 <= n <= 8
     * @throws ArrayIndexOutOfBoundsException if n < 1 || n > 8
     */
    public boolean isNeighborForeground(final int n) throws ArrayIndexOutOfBoundsException {
        return neighborhood[n - 1];
    }

    /** True if none of the elements in the neighborhood are foreground (true) */
    public boolean isNeighborhoodEmpty() {
        return foregroundNeighbors == 0;
    }

    /**
     * Sets the interval where the neighborhood is located
     *
     * @throws NullPointerException     if interval == null
     * @throws IllegalArgumentException if interval has less than three dimensions
     * @implNote Copies reference
     */
    public void setInterval(RandomAccessibleInterval<B> interval)
            throws NullPointerException, IllegalArgumentException {
        checkNotNull(interval, "Interval cannot be set null");
        // TODO Check for *spatial* dimensions
        checkArgument(interval.numDimensions() >= 3, "Interval must have at least three dimensions");

        access = Views.extendZero(interval).randomAccess();
    }

    /** Set the starting coordinates of the neighborhood in the interval */
    public void setNeighborhood(final long u, final long v, final long w) {
        neighborhood[0] = getAtLocation(access, u - 1, v - 1, w - 1);
        neighborhood[1] = getAtLocation(access, u - 1, v, w - 1);
        neighborhood[2] = getAtLocation(access, u, v - 1, w - 1);
        neighborhood[3] = getAtLocation(access, u, v, w - 1);
        neighborhood[4] = getAtLocation(access, u - 1, v - 1, w);
        neighborhood[5] = getAtLocation(access, u - 1, v, w);
        neighborhood[6] = getAtLocation(access, u, v - 1, w);
        neighborhood[7] = getAtLocation(access, u, v, w);

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

    private boolean getAtLocation(final RandomAccess<B> access, final long u, final long v, final long w) {
        access.setPosition(u, 0);
        access.setPosition(v, 1);
        access.setPosition(w, 2);
        return access.get().get();
    }
}
