package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import org.joml.Vector3d;
import org.junit.BeforeClass;
import org.junit.Test;

import net.imagej.ImgPlus;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;

import java.util.concurrent.ExecutionException;

public class EllipsoidFactorWrapperTest extends AbstractWrapperTest {

    @Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
    @Test
    public void test2DImageCancelsConnectivity() {
        CommonWrapperTests.test2DImageCancelsPlugin(imageJ(),
                EllipsoidFactorWrapper.class);
    }

    @Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
    @Test
    public void testNonBinaryImageCancelsConnectivity() {
        CommonWrapperTests.testNonBinaryImageCancelsPlugin(imageJ(),
                EllipsoidFactorWrapper.class);
    }

    @Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
    @Test
    public void testNullImageCancelsConnectivity() {
        CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
                EllipsoidFactorWrapper.class);
    }



    @Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
    @Test
    public void testCancelledRunDoesNotReport() throws ExecutionException,
            InterruptedException
    {
        // SETUP
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "", 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "", 1.0);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "", 1.0);
        final Img<UnsignedByteType> img = ArrayImgs.unsignedBytes(5, 5, 5);
        final ImgPlus<UnsignedByteType> empty = new ImgPlus<>(img, "", xAxis, yAxis, zAxis);

        // EXECUTE
        final CommandModule module = command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", empty, "nVectors", 100,
                "vectorIncrement", 0.435, "skipRatio", 1, "contactSensitivity", 10, "maxIterations",
                100, "maxDrift", 1.73, "minimumSemiAxis", 1.0, "runs", 1, "weightedAverageN", 1,
                "seedOnDistanceRidge", true, "distanceThreshold", 0.6, "seedOnTopologyPreserving",
                false).get();

        // VERIFY
        final String reason = module.getCancelReason();
        assertEquals("EF should have cancelled because there are no ellipsoids in an empty image",
                EllipsoidFactorWrapper.NO_ELLIPSOIDS_FOUND, reason);
        verify(MOCK_REPORTER, timeout(1000).times(0)).reportEvent(anyString());
    }

    // run(nvectors=100 vectorincrement=0.435 skipratio=1 contactsensitivity=1 maxiterations=100 maxdrift=1.73 minimumsemiaxis=1.0 runs=1 weightedaveragen=1 seedondistanceridge=true distancethreshold=0.6 seedontopologypreserving=false);

    @Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
    @Test
    public void testSuccessfulRunReports() throws ExecutionException, InterruptedException {
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "", 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "", 1.0);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "", 1.0);
        final Img<UnsignedByteType> img = createSphereImg();
        final ImgPlus<UnsignedByteType> imgPlus = new ImgPlus<>(img, "Sphere", xAxis, yAxis, zAxis);

        final CommandModule module = command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", imgPlus, "nVectors", 100,
                "vectorIncrement", 0.435, "skipRatio", 1, "contactSensitivity", 10, "maxIterations",
                100, "maxDrift", 1.73, "minimumSemiAxis", 1.0, "runs", 1, "weightedAverageN", 1,
                "seedOnDistanceRidge", true, "distanceThreshold", 0.6, "seedOnTopologyPreserving",
                false).get();

        assertFalse("Sanity check failed: method cancelled", module.isCanceled());
        verify(MOCK_REPORTER, timeout(1000)).reportEvent(anyString());
    }

    @Test
    public void testImgToByteArray(){
        final int fg = 0xFF;
        final ArrayImg<UnsignedByteType, ByteArray> unsignedIntTypes = ArrayImgs.unsignedBytes(3, 3, 3);
        final ArrayRandomAccess<UnsignedByteType> access = unsignedIntTypes.randomAccess();
        access.setPosition(new int[]{1,1,1});
        access.get().setInteger(fg);
        final byte[][] bytes = EllipsoidFactorWrapper.imgPlusToByteArray(new ImgPlus<>(unsignedIntTypes));

        assertEquals("Central pixel should be FG", (byte) fg, bytes[1][4]);
        assertEquals("Pixel at (0,0,0) should be BG", 0, bytes[0][0]);
    }

    @BeforeClass
    public static void oneTimeSetup() {
        EllipsoidFactorWrapper.setReporter(MOCK_REPORTER);
    }

    private static Img<UnsignedByteType> createSphereImg() {
        final int r = 5;
        final int c = 10 + r;
        final Vector3d sphereCentre = new Vector3d(c, c, c);
        final int imgDim = 2 * r + 20;
        final Img<UnsignedByteType> img = ArrayImgs.unsignedBytes(imgDim, imgDim, imgDim);
        final Vector3d position = new Vector3d();
        final RandomAccess<UnsignedByteType> access = img.randomAccess();

        for (int z = c - r; z <= c + r; z++) {
            for (int y = c - r; y <= c + r; y++){
                for (int x = c - r; x <= c + r; x++) {
                    position.set(x, y, z);
                    position.sub(sphereCentre);
                    if (position.length() <= r) {
                        access.setPosition(x, 0);
                        access.setPosition(y, 1);
                        access.setPosition(z, 2);
                        access.get().setByte((byte) 0xFF);
                    }
                }
            }
        }

        return img;
    }
}
