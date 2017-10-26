
package org.bonej.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import net.imagej.ImageJ;
import net.imagej.ops.OpCandidate;
import net.imagej.ops.OpMatchingService;
import net.imagej.ops.OpRef;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.module.Module;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for the {@link RotateAboutAxis} op.
 *
 * @author Richard Domander
 */
public class RotateAboutAxisTest {

	private static final ImageJ imageJ = new ImageJ();
	private static Module op;

	@BeforeClass
	public static void oneTimeSetUp() {
		final OpRef opRef = OpRef.create(RotateAboutAxis.class, Vector3d.class,
			Vector3d.class, DoubleType.class);
		final OpMatchingService matcher = imageJ.op().matcher();
		final OpCandidate candidate = matcher.findMatch(imageJ.op(), opRef);
		op = matcher.match(candidate);
	}

	@After
	public void tearDown() {
		op.setInput("vector", null);
		op.setInput("axis", null);
		op.setInput("angle", null);
	}

	@Test
	public void testRandomAxisAndAngle() {
		op.setInput("vector", new Vector3d(1, 0, 0));

		op.run();

		assertNotNull(op.getOutput("axis"));
		assertNotNull(op.getOutput("angle"));
	}

	@Test
	public void testRandomAxis() {
		op.setInput("vector", new Vector3d(1, 0, 0));
		final DoubleType expectedAngle = new DoubleType(Math.PI / 2.0);
		op.setInput("angle", expectedAngle);

		op.run();

		final DoubleType angle = (DoubleType) op.getOutput("angle");
		assertTrue(expectedAngle.valueEquals(angle));
		assertNotNull(op.getOutput("axis"));
	}

	@Test
	public void testRandomAngle() {
		op.setInput("vector", new Vector3d(1, 0, 0));
		final Vector3d expectedAxis = new Vector3d(0, 0, 1);
		op.setInput("axis", expectedAxis);

		op.run();

		final Vector3d axis = (Vector3d) op.getOutput("axis");
		assertEquals(expectedAxis, axis);
		assertNotNull(op.getOutput("angle"));
	}

	@Test
	public void testAxisNormalised() {
		op.setInput("vector", new Vector3d(1, 0, 0));
		op.setInput("axis", new Vector3d(0, 0, 312));

		op.run();

		final Vector3d axis = (Vector3d) op.getOutput("axis");
		assertEquals(1.0, axis.length(), 1e-12);
	}

	@Test
	public void testOp() {
		final Vector3d expected = new Vector3d(Math.cos(Math.PI / 4.0), Math.sin(
			Math.PI / 4.0), 0);
		expected.scale(3.0);
		final Vector3d axis = new Vector3d(0, 0, 1);
		final DoubleType angle = new DoubleType(Math.PI / 4.0);
		op.setInput("vector", new Vector3d(3, 0, 0));
		op.setInput("axis", axis);
		op.setInput("angle", angle);

		op.run();

		final Vector3d rotated = (Vector3d) op.getOutput("vector");
		assertEquals(expected.x, rotated.x, 1e-12);
		assertEquals(expected.y, rotated.y, 1e-12);
		assertEquals(expected.z, rotated.z, 1e-12);
	}
}
