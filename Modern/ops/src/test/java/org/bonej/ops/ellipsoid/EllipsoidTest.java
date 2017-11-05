
package org.bonej.ops.ellipsoid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.imagej.ImageJ;

import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.vecmath.Vector3d;

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
	public void testConstructor() throws Exception {
		final double a = 1.0;
		final double b = 2.0;
		final double c = 3.0;

		final Ellipsoid ellipsoid = new Ellipsoid(b, c, a);

		assertEquals(a, ellipsoid.getA(), 1e-12);
		assertEquals(b, ellipsoid.getB(), 1e-12);
		assertEquals(c, ellipsoid.getC(), 1e-12);
		final Vector3d centroid = ellipsoid.getCentroid();
		assertNotNull("Default centroid should not be null", centroid);
		assertEquals("Default centroid should be at origin", new Vector3d(0, 0, 0),
			centroid);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionNegativeRadius() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setA(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionZeroRadius() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setA(0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionNonFiniteRadius() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setA(Double.NaN);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionGTB() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setA(3);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetAThrowsExceptionGTC() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 2, 2);

		ellipsoid.setA(3);
	}

	@Test
	public void testSetA() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(6, 7, 8);

		ellipsoid.setA(5);

		assertEquals(5, ellipsoid.getA(), 1e-12);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetBThrowsExceptionLTA() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4);

		ellipsoid.setB(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetBThrowsExceptionGTC() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setB(4);
	}

	@Test
	public void testSetB() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 7, 8);

		ellipsoid.setB(4);

		assertEquals(4, ellipsoid.getB(), 1e-12);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetCThrowsExceptionLTA() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4);

		ellipsoid.setC(1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSetCThrowsExceptionLTB() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(2, 3, 4);

		ellipsoid.setC(2);
	}

	@Test
	public void testSetC() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(6, 7, 8);

		ellipsoid.setC(11);

		assertEquals(11, ellipsoid.getC(), 1e-12);
	}

	@Test
	public void testSamplePoints() throws Exception {
		final Vector3d centroid = new Vector3d(4, 5, 6);
		final double a = 2.0;
		final double b = 3.0;
		final double c = 4.0;
		final Ellipsoid ellipsoid = new Ellipsoid(a, b, c);
		ellipsoid.setCentroid(centroid);
		final long n = 10;
		final Function<Vector3d, Double> ellipsoidEq = (Vector3d p) -> {
			final BiFunction<Double, Double, Double> term = (x, r) -> (x * x) / (r *
				r);
			return term.apply(p.x, a) + term.apply(p.y, b) + term.apply(p.z, c);
		};
		ellipsoid.initSampling(imageJ.op());

		final List<Vector3d> points = ellipsoid.samplePoints(n);

		assertNotNull(points);
		assertEquals(n, points.size());
		points.forEach(p -> p.sub(centroid));
		points.forEach(p -> assertEquals(
			"Point doesn't solve the ellipsoid equation", 1.0, ellipsoidEq.apply(p),
			1e-12));
	}

	@Test(expected = NullPointerException.class)
    public void testInitSamplingThrowsNPEIfOpsNull() {
        final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

        ellipsoid.initSampling(null);
    }

	@Test(expected = RuntimeException.class)
	public void testSamplePointsThrowsRuntimeExceptionIfNotInitialized()
		throws Exception
	{
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.samplePoints(10);
	}

	@Test
	public void testGetCentroid() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);
		final Vector3d centroid = new Vector3d(6, 7, 8);
		ellipsoid.setCentroid(centroid);

		final Vector3d c = ellipsoid.getCentroid();
		c.add(new Vector3d(1, 1, 1));

		assertEquals("Getter should have returned a copy, not a reference",
			centroid, ellipsoid.getCentroid());
	}

	@Test(expected = NullPointerException.class)
	public void testSetCentroidThrowsNPEIfCentroidNull() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);

		ellipsoid.setCentroid(null);
	}

	@Test
	public void testSetCentroid() throws Exception {
		final Ellipsoid ellipsoid = new Ellipsoid(1, 2, 3);
		final Vector3d centroid = new Vector3d(6, 7, 8);

		ellipsoid.setCentroid(centroid);

		assertFalse("Setter should not copy reference", centroid == ellipsoid
			.getCentroid());
		assertEquals("Setter copied values wrong", centroid, ellipsoid
			.getCentroid());
	}
}
