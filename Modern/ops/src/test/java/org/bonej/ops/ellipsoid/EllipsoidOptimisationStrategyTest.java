package org.bonej.ops.ellipsoid;

import static org.junit.Assert.*;

import org.bonej.geometry.Vectors;
import org.joml.Matrix3d;
import org.junit.Test;

import java.util.ArrayList;

public class EllipsoidOptimisationStrategyTest {
	
	/** run parameters to be used for all the tests */
	private static final OptimisationParameters params = new OptimisationParameters(1, 100, Math.sqrt(3), 1.0/2.3);
	
	@Test
	public void testCalculateTorque() {
//		fail("Not yet implemented");
	}

	@Test
	public void testRotateAboutAxis() {
		final int nTests = 100;
		final double[][] unitVectors = Vectors.randomVectors(nTests);
		final double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		Ellipsoid e = new Ellipsoid(new double[] {1, 1, 1}, new double[] {0, 0, 0}, axes);
		
		for (int i = 0; i < nTests; i++) {

			EllipsoidOptimisationStrategy.rotateAboutAxis(e, unitVectors[i]);

			double[][] r = e.getRotation();

			isRotation(r);
		}
	}
	
	@Test
	public void testWiggle() {
		//initialise an image-axis-aligned ellipsoid centred on 0,0,0 and with radii 1,1,1.
		final double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		Ellipsoid e = new Ellipsoid(new double[] {1, 1, 1}, new double[] {0, 0, 0}, axes);

		for (int i = 0; i < 100; i++) {
			EllipsoidOptimisationStrategy.wiggle(e);

			double[][] r = e.getRotation();

			isRotation(r);
		}
	}
	
	@Test
	public void testOrientAxis() {
		final int nTests = 100;
		final double[][] unitVectors = Vectors.randomVectors(nTests);
		EllipsoidOptimisationStrategy eos = new EllipsoidOptimisationStrategy(new long[] {10, 10, 10}, null, null, params);
		//initialise an image-axis-aligned ellipsoid centred on 0,0,0 and with radii 10,10,10.
		final double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		Ellipsoid e = new Ellipsoid(new double[] {10, 10, 10}, new double[] {0, 0, 0}, axes);

		ArrayList<int[]> contactPoints = new ArrayList<>();
		for (int i = 0; i < nTests; i++) {
			final double[] u = unitVectors[i];
			contactPoints.add(new int[] {(int) (u[0] * 10), (int) (u[1] * 10), (int) (u[2] * 10)});
			
			eos.orientAxes(e, contactPoints);

			double[][] r = e.getRotation();

			isRotation(r);
			contactPoints.clear();
		}
	}

	@Test
	public void testTurn() {
//		fail("Not yet implemented");
	}

	@Test
	public void testIsInvalid() {
		int imageSize = 20;
		final double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		
		//ellipsoid with volume greater than the image
		Ellipsoid e = new Ellipsoid(new double[] {200, 200, 200}, new double[] {imageSize / 2, imageSize / 2, imageSize / 2}, axes);
		EllipsoidOptimisationStrategy eos = new EllipsoidOptimisationStrategy(new long[] {imageSize, imageSize, imageSize}, null, null, params);
		assertTrue("Ellipsoid is bigger than image, should be invalid", eos.isInvalid(e, imageSize, imageSize, imageSize));
		
		//ellipsoid with a very small radius
		e = new Ellipsoid(new double[] {7, 0.1, 8}, new double[] {imageSize / 2, imageSize / 2, imageSize / 2}, axes);
		
		assertTrue("Ellipsoid minor axis is < 0.5, should be invalid", eos.isInvalid(e, imageSize, imageSize, imageSize));
		
		//ellipsoid near the corner of the image
		e = new Ellipsoid(new double[] {5, 5, 5}, new double[] {2, 2, 2}, axes);
		
		assertTrue("Ellipsoid is more than half outside the volume, should be invalid", eos.isInvalid(e, imageSize, imageSize, imageSize));
	}

	@Test
	public void testFindContactPoints() {
//		fail("Not yet implemented");
	}

	@Test
	public void testBump() {
		//initialise an image-axis-aligned ellipsoid centred on 0,0,0 and with radii 10,10,10.
		final double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		Ellipsoid e = new Ellipsoid(new double[] {10, 10, 10}, new double[] {0, 0, 0}, axes);
		final int[] seedPoint = new int[] {0, 0, 0};
		final ArrayList<int[]> contactPoint = new ArrayList<>();
		EllipsoidOptimisationStrategy eos = new EllipsoidOptimisationStrategy(new long[] {100, 100, 100}, null, null, params);
		
		final int nTests = 100;
		final double[][] u = Vectors.randomVectors(nTests);
		
		for (int i = 0; i < nTests; i++){
			//set a contact point at (1, 0, 0)
			contactPoint.add(new int[] {(int) (u[i][0] * 10), (int) (u[i][1] * 10), (int) (u[i][2] * 10)});

			double[] startCentre = e.getCentre();
			double startDistance = distance(startCentre, contactPoint.get(0));
			eos.bump(e, contactPoint, seedPoint);
			double[] newCentre = e.getCentre();
			double endDistance = distance(newCentre, contactPoint.get(0));
			assertTrue("Bump must move the centre away from the contact point", endDistance > startDistance);
			contactPoint.clear(); 
			e.setCentroid(0, 0, 0);
		}
	}

	@Test
	public void testContactPointUnitVector() {
//		fail("not yet implemented");
	}
	
	/**
	 * calculates the distance between two points (vectors) in nD space
	 * @param a first vector
	 * @param b second vector
	 * @return square root of sum of squared differences between a and b
	 */
	private static double distance(double[] a, int[] b) {
		final int l = a.length;
		if (l != b.length)
			throw new IllegalArgumentException("Vector lengths must be equal");
		double sqSum = 0;
		for (int i = 0; i < l; i++) {
			final double d = a[i] - b[i];
			sqSum += d * d;
		}
		return Math.sqrt(sqSum);
	}
	
	/**
	 * Check whether the given 3x3 matrix is a rotation matrix by checking that
	 * the dot product = 0 and M^TM = I
	 * 
	 * @param matrix
	 * @return
	 */
	private static void isRotation(double[][] r) {
		Matrix3d q = new Matrix3d();
		final Matrix3d qt = new Matrix3d();
		for (int n = 0; n < 3; n++) {
			for (int m = 0; m < 3; m++) {
				q.set(m, n, r[m][n]);
				qt.set(n, m, r[m][n]);
			}
		}
		
		//check that det(r) = +- 1
		assertEquals(1, Math.abs(q.determinant()), 1E-12);
		
		//check that R^TR = I is true
		Matrix3d i = qt.mul(q);
		assertEquals(1, i.get(0, 0), 1E-12);
		assertEquals(1, i.get(1, 1), 1E-12);
		assertEquals(1, i.get(2, 2), 1E-12);
	}
}


