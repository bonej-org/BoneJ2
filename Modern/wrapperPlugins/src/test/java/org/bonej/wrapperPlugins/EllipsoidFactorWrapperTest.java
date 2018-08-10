package org.bonej.wrapperPlugins;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.FloatType;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;
import org.scijava.vecmath.Vector3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link EllipsoidFactorWrapper}.
 *
 * @author Alessandro Felder
 */

/**
 * Tests for {@link EllipsoidFactorWrapper}.
 *
 * @author Alessandro Felder
 */

public class EllipsoidFactorWrapperTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @After
    public void tearDown() {
        SharedTable.reset();
    }

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testNullImageCancelsPlugin() throws Exception {
        CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
                EllipsoidFactorWrapper.class);
    }

    @Test
    public void testNonBinaryImgCancelsPlugin() throws Exception {
        CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
                EllipsoidFactorWrapper.class);
    }

    @Test
    public void test2DImgCancelsPlugin() throws Exception {
        CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J, EllipsoidFactorWrapper.class);
    }

    public void testSphereVoxelsHaveEFZero() throws Exception {
        // SETUP
        final UserInterface mockUI = mock(UserInterface.class);
        doNothing().when(mockUI).show(any(ImgPlus.class));
        IMAGE_J.ui().setDefaultUI(mockUI);
        final ImgPlus<BitType> sphereImgPlus = getSphereImage();

        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", sphereImgPlus).get();

        // VERIFY
        final ImgPlus<FloatType> efImage = (ImgPlus) module.getOutput("efImage");
        double expectedValue = 0.0;
        assertFiniteImageEntriesMatchValue(efImage, expectedValue, 1e-5);
    }

    @Test
    public void testSphereVoxelsHaveCorrectVolume() throws Exception {
        // SETUP
        final UserInterface mockUI = mock(UserInterface.class);
        doNothing().when(mockUI).show(any(ImgPlus.class));
        IMAGE_J.ui().setDefaultUI(mockUI);
        final ImgPlus<BitType> sphereImgPlus = getSphereImage();

        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", sphereImgPlus).get();

        // VERIFY
        final ImgPlus<FloatType> volumeImage = (ImgPlus) module.getOutput("vImage");
        double expectedValue = 5575.27976;
        assertFiniteImageEntriesMatchValue(volumeImage,expectedValue,1e-4);
    }


    @Test
    public void testSphereVoxelsHaveCorrectAxisRatios() throws Exception {
        // SETUP
        final UserInterface mockUI = mock(UserInterface.class);
        doNothing().when(mockUI).show(any(ImgPlus.class));
        IMAGE_J.ui().setDefaultUI(mockUI);
        final ImgPlus<BitType> sphereImgPlus = getSphereImage();

        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", sphereImgPlus).get();

        // VERIFY
        double expectedValue = 1.0;

        final ImgPlus<FloatType> aToB = (ImgPlus) module.getOutput("aToBAxisRatioImage");
        assertFiniteImageEntriesMatchValue(aToB,expectedValue,1e-4);

        final ImgPlus<FloatType> bToC = (ImgPlus) module.getOutput("bToCAxisRatioImage");
        assertFiniteImageEntriesMatchValue(bToC,expectedValue,1e-4);
    }

    // TODO check image has any finite entries!
    private void assertFiniteImageEntriesMatchValue(ImgPlus<FloatType> efImage, double expectedValue, double tolerance) {
        Cursor<FloatType> efCursor = efImage.getImg().localizingCursor();
        while (efCursor.hasNext())
        {
            efCursor.fwd();
            if(Double.isFinite(efCursor.get().getRealDouble()))
            {
                long [] coordinates = new long[3];
                efCursor.localize(coordinates);
                assertEquals(expectedValue, efCursor.get().getRealDouble(),tolerance);
            }
        }
    }

    private static ImgPlus<BitType> getSphereImage() {
        long[] imageDimensions = {101,101,101};
        Vector3d centre = new Vector3d(Math.floor(imageDimensions[0]/2.0),Math.floor(imageDimensions[1]/2.0),Math.floor(imageDimensions[2]/2.0));
        int radius = 10;

        final Img<BitType> sphereImg = ArrayImgs.bits(imageDimensions[0], imageDimensions[1], imageDimensions[2]);
        final ImgPlus<BitType> sphereImgPlus = new ImgPlus<>(sphereImg, "Sphere test image",
                new AxisType[]{Axes.X,Axes.Y, Axes.Z},
                new double[]{1.0,1.0,1.0},
                new String[]{"","",""});
        Cursor<BitType> cursor = sphereImgPlus.localizingCursor();

        while(cursor.hasNext())
        {
            cursor.fwd();
            long [] coordinates = new long[3];
            cursor.localize(coordinates);
            double x = centre.getX()-coordinates[0];
            double y = centre.getY()-coordinates[1];
            double z = centre.getZ()-coordinates[2];
            double distanceFromCentre = x*x+y*y+z*z;
            if(distanceFromCentre <= radius*radius)
                cursor.get().setOne();
        }
        return sphereImgPlus;
    }

    @Test
    public void testInsideEllipsoidEasy() throws Exception {
        //SETUP
        Ellipsoid axisAligned = new Ellipsoid(1,2,3);
        Vector3d origin = new Vector3d(0,0,0);
        Vector3d definitelyOutside = new Vector3d(4,4,4);
        Vector3d justInside = new Vector3d(0,0,2);
        Vector3d justOutside = new Vector3d(0,2,0);


        //EXECUTE AND VERIFY
        assertTrue(EllipsoidFactorWrapper.insideEllipsoid(origin,axisAligned));
        assertTrue(!EllipsoidFactorWrapper.insideEllipsoid(definitelyOutside,axisAligned));
        assertTrue(EllipsoidFactorWrapper.insideEllipsoid(justInside,axisAligned));
        assertTrue(!EllipsoidFactorWrapper.insideEllipsoid(justOutside,axisAligned));

    }

    // main method for manual visual testing
    public static void main(String[] args) {

    }

}