package org.bonej.ops.ellipsoid;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.type.numeric.real.DoubleType;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Vector3d;


import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;


/**
 * Tests for {@link DistanceFromEllipsoidSurfaceOp}.
 *
 * @author Alessandro Felder
 */
public class DistanceFromEllipsoidSurfaceOpTest {

    private static final ImageJ IMAGE_J = new ImageJ();
    private static BinaryFunctionOp<Ellipsoid, Point3d, DoubleType> distanceFromEllipsoidSurfaceOp;

    private static UnitSphereRandomVectorGenerator sphereRng = new UnitSphereRandomVectorGenerator(3);
    final private Supplier<Vector3d> spherePointSupplier = () -> new Vector3d(sphereRng.nextVector());

    private static Ellipsoid axisAlignedEllipsoid;
    private static Ellipsoid transformedEllipsoid;
    private static Ellipsoid sphere;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        distanceFromEllipsoidSurfaceOp = Functions.binary(IMAGE_J.op(), DistanceFromEllipsoidSurfaceOp.class, DoubleType.class, Ellipsoid.class, Point3d.class);
    }

    @BeforeClass
    public static void oneTimeSetUp() {
        axisAlignedEllipsoid = new Ellipsoid(1.0, 2.0, 3.0);

        //axisAlignedEllipsoid translated by (1,1,1) and rotated 30 degrees around x-axis
        transformedEllipsoid = new Ellipsoid(1.0, 2.0, 3.0);
        transformedEllipsoid.setOrientation(new Matrix3d(1,0,0,0,Math.sqrt(3.0)/2.0,0.5,0,-0.5,Math.sqrt(3.0)/2.0));
        transformedEllipsoid.setCentroid(new Vector3d(1,1,1));

        sphere = new Ellipsoid(2.0, 2.0, 2.0);
    }

    /**
     * test known distances from surface of an axis-aligned ellipsoid
     */
    @Test
    public void testInsidePointForAxisAlignedEllipsoid() {
        Point3d insidePoint = new Point3d(0.0, 1.0, 0.0);
        double distanceToInsidePoint = distanceFromEllipsoidSurfaceOp.calculate(axisAlignedEllipsoid, insidePoint).get();
        assertEquals("Distance to inside point failed", 1.0, distanceToInsidePoint, 1.0e-12);
    }

    @Test
    public void testOutsidePointForAxisAlignedEllipsoid() {
        Point3d outsidePoint = new Point3d(2.0, 0.0, 0.0);
        double distanceToOutsidePoint = distanceFromEllipsoidSurfaceOp.calculate(axisAlignedEllipsoid, outsidePoint).get();
        assertEquals("Distance to outside point failed", 1.0, distanceToOutsidePoint, 1.0e-12);
    }

    @Test
    public void testSurfacePointForAxisAlignedEllipsoid() {
        Point3d surfacePoint  = new Point3d(0.0,0.0,3.0);
        double distanceToSurfacePoint = distanceFromEllipsoidSurfaceOp.calculate(axisAlignedEllipsoid,surfacePoint).get();
        assertEquals("Distance to surface point failed",0.0,distanceToSurfacePoint,1.0e-12);
    }

    /**
     * Same three tests for a more complicated ellipsoid
     */
    @Test
    public void testInsidePointForArbitraryEllipsoid() {
        Point3d insidePoint  = new Point3d(1.0,1.0+Math.sqrt(3.0)/2.0,1-0.5);
        double distanceToInsidePoint = distanceFromEllipsoidSurfaceOp.calculate(transformedEllipsoid, insidePoint).get();
        assertEquals("Distance to inside point failed", 1.0, distanceToInsidePoint, 1.0e-12);
    }

    @Test
    public void testOutsidePointForArbitraryEllipsoid() {
        Point3d outsidePoint  = new Point3d(1.0+2.0,1.0,1.0);
        double distanceToOutsidePoint = distanceFromEllipsoidSurfaceOp.calculate(transformedEllipsoid, outsidePoint).get();
        assertEquals("Distance to outside point failed", 1.0, distanceToOutsidePoint, 1.0e-12);
    }

    @Test
    public void testSurfacePointForArbitraryEllipsoid() {
        Point3d surfacePoint  = new Point3d(1.0,1.0+1.5,1.0+3.0*Math.sqrt(3.0)/2.0);
        double distanceToSurfacePoint = distanceFromEllipsoidSurfaceOp.calculate(transformedEllipsoid,surfacePoint).get();
        assertEquals("Distance to surface point failed",0.0,distanceToSurfacePoint,1.0e-12);
    }

    /**
     * Same three tests for a sphere and arbitrarily chosen points
     */
    @Test
    public void testInsidePointForSphere() {
        Vector3d sphereVector = new Vector3d(spherePointSupplier.get());
        Point3d insidePoint  = new Point3d(sphereVector);
        double distanceToInsidePoint = distanceFromEllipsoidSurfaceOp.calculate(sphere, insidePoint).get();
        assertEquals("Distance to inside point failed", 1.0, distanceToInsidePoint, 1.0e-12);
    }

    @Test
    public void testOutsidePointForSphere() {
        Vector3d sphereVector = new Vector3d(spherePointSupplier.get());
        sphereVector.scale(3.0);
        Point3d outsidePoint  = new Point3d(sphereVector);
        double distanceToOutsidePoint = distanceFromEllipsoidSurfaceOp.calculate(sphere, outsidePoint).get();
        assertEquals("Distance to outside point failed", 1.0, distanceToOutsidePoint, 1.0e-12);
    }

    @Test
    public void testSurfacePointForSphere() {
        Vector3d sphereVector = new Vector3d(spherePointSupplier.get());
        sphereVector.scale(2.0);
        Point3d surfacePoint  = new Point3d(sphereVector);
        double distanceToSurfacePoint = distanceFromEllipsoidSurfaceOp.calculate(sphere,surfacePoint).get();
        assertEquals("Distance to surface point failed",0.0,distanceToSurfacePoint,1.0e-12);
    }

    @Test(expected = ArithmeticException.class)
    public void testArithmeticExceptionForZeroDeterminant() throws Exception
    {
        Ellipsoid ellipsoid = new Ellipsoid(2.0, 2.0, 2.0);
        Point3d origin = new Point3d(0.0,0.0,0.0);
        distanceFromEllipsoidSurfaceOp.calculate(ellipsoid,origin).get();
    }

    @Test
    public void testTranslationTransformation()
    {
        Ellipsoid ellipsoid = new Ellipsoid(2.0, 2.0, 2.0);
        ellipsoid.setCentroid(new Vector3d(5,7,8));
        Point3d point  = new Point3d(5,7,9);

        Point3d translated = DistanceFromEllipsoidSurfaceOp.toEllipsoidCoordinates(point,ellipsoid);
        assertEquals(0.0,translated.x,1.0e-12);
        assertEquals(0.0,translated.y,1.0e-12);
        assertEquals(1.0,translated.z,1.0e-12);

    }

    @Test
    public void testRotationTransformation()
    {
        Ellipsoid ellipsoid = new Ellipsoid(1.0, 2.0, 3.0);
        ellipsoid.setOrientation(new Matrix3d(0,0,1,0,-1,0,1,0,0));
        Point3d point  = new Point3d(0,0,8);

        Point3d rotated = DistanceFromEllipsoidSurfaceOp.toEllipsoidCoordinates(point,ellipsoid);
        assertEquals(8.0,rotated.x,1.0e-12);
        assertEquals(0.0,rotated.y,1.0e-12);
        assertEquals(0.0,rotated.z,1.0e-12);

    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        IMAGE_J.context().dispose();
    }
}