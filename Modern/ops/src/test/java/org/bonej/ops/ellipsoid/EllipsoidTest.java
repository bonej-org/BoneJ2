
package org.bonej.ops.ellipsoid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import net.imagej.ImageJ;

import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.vecmath.Vector3d;

import java.util.List;

/**
 * Tests for {@link Ellipsoid}.
 *
 * @author Richard Domander
 */
public class EllipsoidTest {

	private static final ImageJ imageJ = new ImageJ();

	@AfterClass
	public static void oneTimeTearDown() {
		imageJ.context().dispose();
	}

	@Test
	public void testConstructorSorting() throws Exception {
		final double a = 1.0;
		final double b = 2.0;
		final double c = 3.0;

		final Ellipsoid ellipsoid = new Ellipsoid(b, c, a, imageJ.op());

		assertEquals(a, ellipsoid.getA(), 1e-12);
        assertEquals(b, ellipsoid.getB(), 1e-12);
        assertEquals(c, ellipsoid.getC(), 1e-12);
	}

	@Test(expected = NullPointerException.class)
	public void testSetOpEnvironmentThrowsNPE() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3, imageJ.op());

		ellipsoid.setOpEnvironment(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionNegativeRadius() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3, imageJ.op());

		ellipsoid.setA(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionZeroRadius() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3, imageJ.op());

		ellipsoid.setA(0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionNonFiniteRadius() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3, imageJ.op());

		ellipsoid.setA(Double.NaN);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionGTB() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3, imageJ.op());

		ellipsoid.setA(3);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionGTC() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 2, 2, imageJ.op());

		ellipsoid.setA(3);
	}

	@Test
	public void testSetA() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(6, 7, 8, imageJ.op());

		ellipsoid.setA(5);

		assertEquals(5, ellipsoid.getA(), 1e-12);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetBThrowsExceptionLTA() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4, imageJ.op());

		ellipsoid.setB(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetBThrowsExceptionGTC() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3, imageJ.op());

		ellipsoid.setB(4);
	}

	@Test
	public void testSetB() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 7, 8, imageJ.op());

		ellipsoid.setB(4);

		assertEquals(4, ellipsoid.getB(), 1e-12);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetCThrowsExceptionLTA() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4, imageJ.op());

		ellipsoid.setC(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetCThrowsExceptionLTB() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4, imageJ.op());

		ellipsoid.setC(2);
	}

	@Test
	public void testSetC() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(6, 7, 8, imageJ.op());

		ellipsoid.setC(11);

		assertEquals(11, ellipsoid.getC(), 1e-12);
	}

	@Test
    public void testSamplePoints() throws Exception {
        final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3, imageJ.op());
        final long n = 10;

        final List<Vector3d> points = ellipsoid.samplePoints(n);

        assertNotNull(points);
        assertEquals(n, points.size());
    }
}
