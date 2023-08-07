package org.bonej.ops.ellipsoid;

import static org.junit.Assert.*;

import org.bonej.geometry.Vectors;
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
	public void testWiggle() {
//	//initialise an image-axis-aligned ellipsoid centred on 0,0,0 and with radii 1,1,1.
	final double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
	Ellipsoid e = new Ellipsoid(new double[] {1, 1, 1}, new double[] {0, 0, 0}, axes);
	EllipsoidOptimisationStrategy.wiggle(e);
//		fail("Not yet implemented");
	}

	@Test
	public void testTurn() {
//		fail("Not yet implemented");
	}

	@Test
	public void testIsInvalid() {
//		fail("Not yet implemented");
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
}


