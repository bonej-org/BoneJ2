package org.bonej.wrapperPlugins.wrapperUtils;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.ByteType;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils.SpatialView;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the {@link ViewUtils ViewUtils} class
 *
 * @author Richard Domander
 */
public class ViewUtilsTest {
    /** Test createSpatialViews with a 3D image (no extra dimensions) */
    @Test
    public void testCreateSpatialViews3D() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<ByteType> img = ArrayImgs.bytes(3, 3, 3, 1, 1);
        final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, cAxis, tAxis);

        final List<SpatialView<ByteType>> views = ViewUtils.createSpatialViews(imgPlus);

        assertEquals("Wrong number of spatial views generated", 1, views.size());
        assertTrue("There should be no description of hyper position", views.get(0).hyperPosition.isEmpty());
    }

    /** Test createSpatialViews with an image that has channels */
    @Test
    public void testCreateSpatialViewsChannels() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final int channels = 3;
        final Img<ByteType> img = ArrayImgs.bytes(3, 3, 3, channels);
        final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, cAxis);

        final List<SpatialView<ByteType>> views = ViewUtils.createSpatialViews(imgPlus);

        assertEquals("Wrong number of spatial views generated", channels, views.size());

        for (int c = 0; c < channels; c++) {
            final SpatialView view = views.get(c);
            final String hyperPosition = view.hyperPosition;
            final String expected = "_C" + (c + 1);
            assertEquals("The description of hyper position is incorrect", expected, hyperPosition);
        }
    }

    /** Test createSpatialViews with an image that has a time dimension */
    @Test
    public void testCreateSpatialViewsFrames() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final int frames = 5;
        final Img<ByteType> img = ArrayImgs.bytes(3, 3, 3, frames);
        final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, tAxis);

        final List<SpatialView<ByteType>> views = ViewUtils.createSpatialViews(imgPlus);

        assertEquals("Wrong number of spatial views generated", frames, views.size());

        for (int f = 0; f < frames; f++) {
            final SpatialView view = views.get(f);
            final String hyperPosition = view.hyperPosition;
            final String expected = "_F" + (f + 1);
            assertEquals("The description of hyper position is incorrect", expected, hyperPosition);
        }
    }

    /** Test createSpatialViews with an image that has both channel and time dimensions */
    @Test
    public void testCreateSpatialViews() throws Exception {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final int channels = 3;
        final int frames = 5;
        // Note the inverted order of time & channel dimensions from the usual
        final Img<ByteType> img = ArrayImgs.bytes(3, 3, 3, frames, channels);
        final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, tAxis, cAxis);

        final List<SpatialView<ByteType>> views = ViewUtils.createSpatialViews(imgPlus);

        assertEquals("Wrong number of spatial views generated", frames * channels, views.size());

        int v = 0;
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) {
                final SpatialView view = views.get(v);
                final String hyperPosition = view.hyperPosition;
                final String expected = "_F" + (f + 1) + "_C" + (c + 1);
                assertEquals("The description of hyper position is incorrect", expected, hyperPosition);
                v++;
            }
        }
    }
}