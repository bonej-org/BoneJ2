package org.bonej.wrapperPlugins.anisotropy;

import java.util.Random;

import net.imagej.ImageJ;
import net.imagej.ops.stats.regression.leastSquares.Quadric;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;

import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DegreeOfAnisotropyTest {
    private static final long SEED = 12345L;
    private static final int DIRECTIONS = 100;
    private static ImageJ IMAGE_J = new ImageJ();
    private static AnisotropyWrapper<?> mockWrapper;
    private static Img<BitType> xySheets = createXYSheets();
    private static DegreeOfAnisotropy emptyStackCalculator;
    private static DegreeOfAnisotropy xySheetsCalculator;

    @Test
    public void testEmptyStackDA() {
        assertEquals(0.0, emptyStackCalculator.getDegreeOfAnisotropy(), 1e-12);
    }

    @Test
    public void testEmptyStackRadii() {
        final double[] radii = emptyStackCalculator.getRadii();
        assertEquals(radii[0], radii[1], 1e-10);
        assertEquals(radii[1], radii[2], 1e-10);
    }

    @Test
    public void testBinaryNoiseDA() throws Exception {
        final Img<BitType> binaryNoise = createBinaryNoise();
        final DegreeOfAnisotropy degreeOfAnisotropy = new DegreeOfAnisotropy(mockWrapper);
        degreeOfAnisotropy.setLinesPerDirection(25);
        degreeOfAnisotropy.setSamplingDirections(100);
        degreeOfAnisotropy.setSeed(SEED);

        degreeOfAnisotropy.calculate(binaryNoise);
        final double dA = degreeOfAnisotropy.getDegreeOfAnisotropy();

        assertEquals(0.043318339051122035, dA, 1e-12);
    }

    @Test
    public void testXYSheetsDA() {
        final double dA = xySheetsCalculator.getDegreeOfAnisotropy();
        assertEquals(0.9900879398296404, dA, 1e-12);
    }

    @Test
    public void testXYSheetsRadii() {
        final double[] radii = xySheetsCalculator.getRadii();
        assertEquals(4.677484949735923, radii[0], 1e-12);
        assertEquals(15.004677262978667, radii[1], 1e-12);
        assertEquals(46.98188461474766, radii[2], 1e-12);
    }

    @Test
    public void testXYSheetsEigenVectors() {
        final Vector3dc xAxis = new Vector3d(1, 0, 0);
        final Vector3dc yAxis = new Vector3d(0, 1, 0);
        final Vector3dc zAxis = new Vector3d(0, 0, 1);
        final Matrix3dc eigenMatrix = xySheetsCalculator.getEigenMatrix();
        final Vector3d ev1 = new Vector3d();
        eigenMatrix.getColumn(0, ev1);
        final Vector3d ev2 = new Vector3d();
        eigenMatrix.getColumn(1, ev2);
        final Vector3d ev3 = new Vector3d();
        eigenMatrix.getColumn(2, ev3);

        // It isn't guaranteed which way of the axes of the ellipsoid point,
        // they can point to the opposite direction of the expected
        assertEquals("The short axis of the ellipsoid should be (roughly) parallel to z-axis",
                0.9764361735420987, Math.abs(zAxis.dot(ev1)), 1e-12);
        assertEquals("Ellipsoid axis should be (roughly) parallel to y-axis",
                0.8820734061182398, Math.abs(yAxis.dot(ev2)), 1e-12);
        assertEquals("Ellipsoid axis should be (roughly) parallel to x-axis",
                0.8979117353710572, Math.abs(xAxis.dot(ev3)), 1e-12);
    }


    @Test
    public void testXYSheetsEigenValues() {
        final double[] radii = xySheetsCalculator.getRadii();
        final double[] eigenValues = xySheetsCalculator.getEigenValues();
        assertEquals(1.0 / (radii[2] * radii[2]), eigenValues[0], 1e-12);
        assertEquals(1.0 / (radii[1] * radii[1]), eigenValues[1], 1e-12);
        assertEquals(1.0 / (radii[0] * radii[0]), eigenValues[2], 1e-12);
    }

    @Test
    public void testXYSheetsMILVectors() {
        assertEquals(DIRECTIONS, xySheetsCalculator.getMILVectors().size());
    }

    @Test(expected = EllipsoidFittingFailedException.class)
    public void testEllipsoidFittingFailingThrowsException() throws Exception {
        final DegreeOfAnisotropy degreeOfAnisotropy = new DegreeOfAnisotropy(mockWrapper);
        degreeOfAnisotropy.setLinesPerDirection(1);
        degreeOfAnisotropy.setSamplingDirections(Quadric.MIN_DATA);
        degreeOfAnisotropy.setSeed(SEED);

        degreeOfAnisotropy.calculate(xySheets);
    }

    @Test
    public void testObserverIsNotified() throws Exception {
        final AnisotropyWrapper<?> observer = Mockito.mock(AnisotropyWrapper.class);
        when(observer.context()).thenReturn(IMAGE_J.context());
        final DegreeOfAnisotropy degreeOfAnisotropy = new DegreeOfAnisotropy(observer);
        degreeOfAnisotropy.setLinesPerDirection(25);
        degreeOfAnisotropy.setSamplingDirections(DIRECTIONS);
        degreeOfAnisotropy.setSeed(SEED);

        degreeOfAnisotropy.calculate(xySheets);

        verify(observer, times(DIRECTIONS)).directionFinished();
    }

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        mockWrapper = Mockito.mock(AnisotropyWrapper.class);
        when(mockWrapper.context()).thenReturn(IMAGE_J.context());
        setupEmptyStackDA();
        setupXYSheetsDA();
    }

    private static void setupXYSheetsDA() throws Exception {
        xySheetsCalculator = new DegreeOfAnisotropy(mockWrapper);
        xySheetsCalculator.setLinesPerDirection(25);
        xySheetsCalculator.setSamplingDirections(DIRECTIONS);
        xySheetsCalculator.setSeed(SEED);
        xySheetsCalculator.calculate(xySheets);
    }

    private static void setupEmptyStackDA() throws Exception {
        emptyStackCalculator = new DegreeOfAnisotropy(mockWrapper);
        emptyStackCalculator.setLinesPerDirection(25);
        emptyStackCalculator.setSamplingDirections(DIRECTIONS);
        emptyStackCalculator.setSeed(SEED);
        final Img<BitType> emptyStack = ArrayImgs.bits(50, 50, 50);
        emptyStackCalculator.calculate(emptyStack);
    }

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
        IMAGE_J = null;
        mockWrapper = null;
        xySheets = null;
        xySheetsCalculator = null;
        emptyStackCalculator = null;
    }

    private Img<BitType> createBinaryNoise() {
        final Img<BitType> img = ArrayImgs.bits(50, 50, 50);
        final Random random = new Random(SEED);
        img.forEach(e -> {
            if (random.nextDouble() >= 0.5) {
                e.setOne();
            }
        });
        return img;
    }

    private static Img<BitType> createXYSheets() {
        final Img<BitType> xySheets = ArrayImgs.bits(50, 50, 50);
        for (int z = 0; z < xySheets.dimension(2); z += 2) {
            fillSlice(xySheets, z);
        }
        return xySheets;
    }

    private static void fillSlice(final Img<BitType> xySheets, final int z) {
        final long width = xySheets.dimension(0);
        final long height = xySheets.dimension(1);
        final IntervalView<BitType> view = Views.interval(xySheets, new long[] { 0,
                0, z }, new long[] { width - 1, height - 1, z });
        view.forEach(BitType::setOne);
    }
}