package org.bonej.wrapperPlugins.wrapperUtils;

import net.imagej.ImgPlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.bonej.utilities.AxisUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for creating and modifying Views
 *
 * @author Richard Domander
 */
public class ViewUtils {
    private ViewUtils() {}

    /**
     * Creates views of all the spatial subspaces present in the given image
     * @implNote Assumes that there's at most one channel and time dimension
     * @implNote Does not affect the number of spatial dimensions
     * TODO generalize
     */
    public static List<SpatialView> createSpatialViews(ImgPlus<?> imgPlus) {
        final List<SpatialView> views = new ArrayList<>();
        final int timeIndex = AxisUtils.getTimeIndex(imgPlus);
        int channelIndex = AxisUtils.getChannelIndex(imgPlus);

        final long channels = channelIndex >= 0 ? imgPlus.dimension(channelIndex) : 0;
        final long frames = timeIndex >= 0 ? imgPlus.dimension(timeIndex) : 0;
        long frame = 0;

        if (channelIndex > timeIndex && timeIndex != -1) {
            // Channel index is one smaller once time dimension has been cut
            channelIndex--;
        }

        do {
            long channel = 0;
            // No need to add clarifying suffix is there's only one frame
            final String frameSuffix = frames > 1 ? "_F" + (frame + 1) : "";
            RandomAccessibleInterval timeView = safeHyperSlice(imgPlus, timeIndex, frame);
            do {
                // No need to add clarifying suffix is there's only one channel
                final String channelSuffix = channels > 1 ? "_C" + (channel + 1) : "";
                RandomAccessibleInterval channelView = safeHyperSlice(timeView, channelIndex, channel);
                final SpatialView view = new SpatialView(channelView, frameSuffix + channelSuffix);
                views.add(view);
                channel++;
            } while (channel < channels);
            frame++;
        } while (frame < frames);

        return views;
    }

    //region -- Helper methods --
    private static RandomAccessibleInterval safeHyperSlice(RandomAccessibleInterval view, int dimension, long position) {
        if (dimension < 0 || dimension >= view.numDimensions()) {
            return view;
        }

        return Views.hyperSlice(view, dimension, position);
    }
    //endregion

    //region -- Helper classes --

    /**
     * A class which stores a view of a spatial subspace,
     * and a verbal description of its position in an n-dimensional hyper space
     */
    public static final class SpatialView {
        public final RandomAccessibleInterval view;
        public final String hyperPosition;

        /**
         * Creates a SpatialView
         *
         * @param view          A view of a spatial subspace in an n-dimensional space
         * @param hyperPosition A string describing the position of the subspace, e.g. "Channel 1"
         */
        public SpatialView(final RandomAccessibleInterval view, String hyperPosition) {
            this.view = view;
            this.hyperPosition = hyperPosition;
        }
    }
    //endregion
}
