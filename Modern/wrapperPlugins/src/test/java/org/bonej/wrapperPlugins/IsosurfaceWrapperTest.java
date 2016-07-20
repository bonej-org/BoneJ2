package org.bonej.wrapperPlugins;

import net.imagej.ImageJ;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Tests for {@link IsosurfaceWrapper}
 *
 * @author Richard Domander 
 */
public class IsosurfaceWrapperTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testNullImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void test2DImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void testNonBinaryImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void testNoCalibrationShowsWarning() throws Exception {
        CommonWrapperTests.testNoCalibrationShowsWarning(IMAGE_J, IsosurfaceWrapper.class);
    }
}