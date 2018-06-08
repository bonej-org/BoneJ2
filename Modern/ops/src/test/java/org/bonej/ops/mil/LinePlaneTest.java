
package org.bonej.ops.mil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;

import org.bonej.ops.RotateAboutAxis;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link LinePlane}.
 *
 * @author Richard Domander
 */
public class LinePlaneTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static final AxisAngle4d IDENTITY = new AxisAngle4d(0, 0, 1, 0);
	private static final int SIZE = 5;
	private static final Img IMG = ArrayImgs.bits(SIZE, SIZE, SIZE);
	private static BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp;

	@Test
	public void testGetDirection() {
		final Vector3d expectedDirection = new Vector3d(0, 0, 1);
		final LinePlane plane = new LinePlane(IMG, IDENTITY, rotateOp);

		final Vector3d direction = plane.getDirection();

		assertEquals("Incorrect direction", expectedDirection, direction);
	}

	@Test
	public void testGetDirectionRotation() {
		// SETUP
		final Vector3d expectedDirection = new Vector3d(1, 0, 0);
		final AxisAngle4d rotation = new AxisAngle4d(0, 1, 0, Math.PI / 2.0);
		final LinePlane plane = new LinePlane(IMG, rotation, rotateOp);

		// EXECUTE
		final Vector3d direction = plane.getDirection();

		// VERIFY
		assertTrue("Direction rotated incorrectly", expectedDirection.epsilonEquals(
			direction, 1e-12));
	}

	@Test
	public void testGetOriginsBinsCount() {
		final LinePlane plane = new LinePlane(IMG, IDENTITY, rotateOp);

		final long count = plane.getOrigins(1L).count();
		assertEquals("Wrong number of origin points", 1, count);
		final long count1 = plane.getOrigins(4L).count();
		assertEquals("Wrong number of origin points", 16, count1);
	}

	@Test
	public void testGetOriginsBinsRegions() {
		final LinePlane plane = new LinePlane(IMG, IDENTITY, rotateOp);
		final double cX = SIZE / 2.0;
		final double cY = SIZE / 2.0;

		// EXECUTE
		final List<Point3d> origins = plane.getOrigins(2L).collect(Collectors
			.toList());

		final Point3d a = origins.get(0);
		assertTrue("Point is in the wrong quadrant of the plane", a.x <= cX &&
			a.y <= cY);
		final Point3d b = origins.get(1);
		assertTrue("Point is in the wrong quadrant of the plane", b.x > cX &&
			b.y <= cY);
		final Point3d c = origins.get(2);
		assertTrue("Point is in the wrong quadrant of the plane", c.x <= cX &&
			c.y > cY);
		final Point3d d = origins.get(3);
		assertTrue("Point is in the wrong quadrant of the plane", d.x > cX &&
			d.y > cY);
	}

	@Test
	public void testGetOriginsCoPlanar() {
		// SETUP
		final double translation = -(Math.sqrt(SIZE * SIZE * 3.0) / 2.0) + SIZE /
			2.0;
		final Point3d pointOnPlane = new Point3d(translation, translation, SIZE /
			2.0);
		final Vector3d normal = new Vector3d(0, 0, 1);
		final LinePlane plane = new LinePlane(IMG, IDENTITY, rotateOp);

		// EXECUTE
		final Stream<Point3d> origins = plane.getOrigins(4L);

		// VERIFY
		origins.forEach(o -> {
			final Vector3d a = new Vector3d(o);
			a.sub(pointOnPlane);
			assertEquals("Point " + a + " is not on the expected plane", 0.0, normal
				.dot(a), 0.0);
		});
	}

	@Test
	public void testGetOriginsRotation() {
		// SETUP
		final double translation = -(Math.sqrt(SIZE * SIZE * 3) / 2.0) + SIZE / 2.0;
		final Point3d pointOnPlane = new Point3d(SIZE / 2.0, translation,
			translation);
		final Vector3d normal = new Vector3d(1, 0, 0);
		final AxisAngle4d rotation = new AxisAngle4d(0, 1, 0, Math.PI / 2.0);
		final LinePlane plane = new LinePlane(IMG, rotation, rotateOp);

		// EXECUTE
		final Stream<Point3d> origins = plane.getOrigins(2L);

		// VERIFY
		origins.forEach(o -> {
			final Vector3d a = new Vector3d(o);
			a.sub(pointOnPlane);
			assertEquals("Point " + a + " rotated incorrectly", 0.0, normal.dot(a),
				1e-12);
		});
	}

	@Test
	public void testSetSeed() throws NoSuchElementException {
		// SETUP
		final LinePlane plane = new LinePlane(IMG, IDENTITY, rotateOp);

		// EXECUTE
		plane.setSeed(0xc0ff33);
		final Point3d p = plane.getOrigins(1L).findFirst().get();
		plane.setSeed(0xc0ff33);
		final Point3d p2 = plane.getOrigins(1L).findFirst().get();
		plane.setSeed(0x70ff33);
		final Point3d q = plane.getOrigins(1L).findFirst().get();

		// VERIFY
		assertEquals("Same seed should create the same point", p, p2);
		assertNotEquals("Different seeds should create different points", p, q);
	}

	@BeforeClass
	public static void oneTimeSetup() {
		rotateOp = Hybrids.binaryCFI1(IMAGE_J.op(), RotateAboutAxis.class,
			Tuple3d.class, new Vector3d(), new AxisAngle4d());
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
