/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

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

	@Test(expected = NullPointerException.class)
	public void testConstructorThrowsNPEIfIntervalNull() {
		new LinePlane(null, IDENTITY, rotateOp);
	}

	@Test(expected = NullPointerException.class)
	public void testConstructorThrowsNPEIfRotationNull() {
		new LinePlane(IMG, null, rotateOp);
	}

	@Test(expected = NullPointerException.class)
	public void testConstructorThrowsNPEIfOpNull() {
		new LinePlane(IMG, IDENTITY, null);
	}

	@Test
	public void testGetDirection() {
		final Vector3d expectedDirection = new Vector3d(0, 0, 1);
		final LinePlane plane = new LinePlane(IMG, IDENTITY, rotateOp);

		final Vector3dc direction = plane.getDirection();

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
		final Vector3dc direction = plane.getDirection();

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
		final List<Vector3dc> origins = plane.getOrigins(2L).collect(Collectors
			.toList());

		final Vector3dc a = origins.get(0);
		assertTrue("Point is in the wrong quadrant of the plane", a.x() <= cX &&
			a.y() <= cY);
		final Vector3dc b = origins.get(1);
		assertTrue("Point is in the wrong quadrant of the plane", b.x() > cX &&
			b.y() <= cY);
		final Vector3dc c = origins.get(2);
		assertTrue("Point is in the wrong quadrant of the plane", c.x() <= cX &&
			c.y() > cY);
		final Vector3dc d = origins.get(3);
		assertTrue("Point is in the wrong quadrant of the plane", d.x() > cX &&
			d.y() > cY);
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
		final Stream<Vector3dc> origins = plane.getOrigins(4L);

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
		final Vector3dc pointOnPlane = new Vector3d(SIZE / 2.0, translation,
			translation);
		final Vector3dc normal = new Vector3d(1, 0, 0);
		final Quaterniondc rotation = new Quaterniond(new AxisAngle4d(Math.PI / 2.0,
			0, 1, 0));
		final LinePlane plane = new LinePlane(IMG, rotation, rotateOp);

		// EXECUTE
		final Stream<Vector3dc> origins = plane.getOrigins(2L);

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
