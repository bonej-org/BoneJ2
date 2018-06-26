
package org.bonej.ops.mil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;

import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link LinePlane}.
 *
 * @author Richard Domander
 */
public class LinePlaneTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static final Quaterniondc IDENTITY = new Quaterniond(new AxisAngle4d(
		0, 0, 1, 0));
	private static final int SIZE = 5;
	private static final Img IMG = ArrayImgs.bits(SIZE, SIZE, SIZE);
	private static BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;

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
		final Quaterniondc rotation = new Quaterniond(new AxisAngle4d(Math.PI / 2.0,
			0, 1, 0));
		final Vector3d expectedDirection = IMAGE_J.op().linalg().rotate(
			new Vector3d(0, 0, 1), rotation);
		final LinePlane plane = new LinePlane(IMG, rotation, rotateOp);

		// EXECUTE
		final Vector3d direction = plane.getDirection();

		// VERIFY
		assertEquals("Direction rotated incorrectly", expectedDirection, direction);
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
		final List<Vector3d> origins = plane.getOrigins(2L).collect(Collectors
			.toList());

		final Vector3d a = origins.get(0);
		assertTrue("Point is in the wrong quadrant of the plane", a.x <= cX &&
			a.y <= cY);
		final Vector3d b = origins.get(1);
		assertTrue("Point is in the wrong quadrant of the plane", b.x > cX &&
			b.y <= cY);
		final Vector3d c = origins.get(2);
		assertTrue("Point is in the wrong quadrant of the plane", c.x <= cX &&
			c.y > cY);
		final Vector3d d = origins.get(3);
		assertTrue("Point is in the wrong quadrant of the plane", d.x > cX &&
			d.y > cY);
	}

	@Test
	public void testGetOriginsCoPlanar() {
		// SETUP
		final double translation = -(Math.sqrt(SIZE * SIZE * 3.0) / 2.0) + SIZE /
			2.0;
		final Vector3dc pointOnPlane = new Vector3d(translation, translation, SIZE /
			2.0);
		final Vector3dc normal = new Vector3d(0, 0, 1);
		final LinePlane plane = new LinePlane(IMG, IDENTITY, rotateOp);

		// EXECUTE
		final Stream<Vector3d> origins = plane.getOrigins(4L);

		// VERIFY
		origins.forEach(o -> {
			final Vector3d a = new Vector3d(o);
			a.sub(pointOnPlane);
			assertTrue("Point " + a.toString() + " is not on the expected plane",
				normal.dot(a) == 0.0);
		});
	}

	@Test
	public void testGetOriginsRotation() {
		// SETUP
		final double translation = -(Math.sqrt(SIZE * SIZE * 3) / 2.0) + SIZE / 2.0;
		final Vector3dc pointOnPlane = new Vector3d(SIZE / 2.0, translation,
			translation);
		final Vector3dc normal = new Vector3d(1, 0, 0);
		final Quaterniondc rotation = new Quaterniond(new AxisAngle4d(Math.PI / 2.0,
			0, 1, 0));
		final LinePlane plane = new LinePlane(IMG, rotation, rotateOp);

		// EXECUTE
		final Stream<Vector3d> origins = plane.getOrigins(2L);

		// VERIFY
		origins.forEach(o -> {
			final Vector3d a = new Vector3d(o);
			a.sub(pointOnPlane);
			assertEquals("Point " + a.toString() + " rotated incorrectly", 0.0, normal
				.dot(a), 1e-12);
		});
	}

	@Test
	public void testSetSeed() throws NoSuchElementException {
		// SETUP
		final LinePlane plane = new LinePlane(IMG, IDENTITY, rotateOp);

		// EXECUTE
		plane.setSeed(0xc0ff33);
		final Vector3dc p = plane.getOrigins(1L).findFirst().get();
		plane.setSeed(0xc0ff33);
		final Vector3dc p2 = plane.getOrigins(1L).findFirst().get();
		plane.setSeed(0x70ff33);
		final Vector3dc q = plane.getOrigins(1L).findFirst().get();

		// VERIFY
		assertEquals("Same seed should create the same point", p, p2);
		assertNotEquals("Different seeds should create different points", p, q);
	}

	@BeforeClass
	public static void oneTimeSetup() {
		rotateOp = Hybrids.binaryCFI1(IMAGE_J.op(), Rotate3d.class, Vector3d.class,
			new Vector3d(), new Quaterniond());
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
