package org.bonej.wrapperPlugins;

import ij.IJ;
import ij.ImagePlus;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;
import org.scijava.vecmath.Vector3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Ignore
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
    public void testNonBinaryImageCancelsPlugin() throws Exception {
        CommonWrapperTests.testNonBinaryImagePlusCancelsPlugin(IMAGE_J,
                EllipsoidFactorWrapper.class);
    }

    @Test
    public void testCompositeImageCancelsPlugin() throws Exception {
        // SETUP
        final String expectedMessage = CommonMessages.HAS_CHANNEL_DIMENSIONS +
                ". Please split the channels.";
        final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
        final ImagePlus imagePlus = IJ.createHyperStack("test", 3, 3, 3, 3, 1, 8);

        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", imagePlus).get();

        // VERIFY
        assertTrue("A composite image should have cancelled the plugin", module
                .isCanceled());
        assertEquals("Cancel reason is incorrect", expectedMessage, module
                .getCancelReason());
        verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
                any());
    }

    @Test
    public void testSphereHasEFZero() throws Exception {
        // SETUP
        final UserInterface mockUI = mock(UserInterface.class);
        doNothing().when(mockUI).show(any(ImgPlus.class));
        IMAGE_J.ui().setDefaultUI(mockUI);


        long[] imageDimensions = {101,101,101};
        Vector3d centre = new Vector3d(Math.floor(imageDimensions[0]/2.0),Math.floor(imageDimensions[1]/2.0),Math.floor(imageDimensions[2]/2.0));
        int radius = 10;

        final Img<IntType> sphereImg = ArrayImgs.ints(imageDimensions[0], imageDimensions[1], imageDimensions[2]);
        final ImgPlus<IntType> sphereImgPlus = new ImgPlus<>(sphereImg, "Sphere test image");
        Cursor<IntType> cursor = sphereImgPlus.localizingCursor();

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
                cursor.get().set(255);
        }
        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", sphereImgPlus).get();

        // VERIFY
        final ImgPlus<DoubleType> efImage = (ImgPlus) module.getOutput("efImage");
        Cursor<DoubleType> efCursor = efImage.getImg().localizingCursor();
        while (cursor.hasNext())
        {
            cursor.fwd();
            long [] coordinates = new long[3];
            cursor.localize(coordinates);

            if(efCursor.get().get()!=Double.NaN)
            {
                assertEquals(0.0, efCursor.get().get(),1e-12);
            }
        }
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