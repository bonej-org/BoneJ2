/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.bonej.ops.ellipsoid;

import java.util.Arrays;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;

/**
 * <p>
 * Represents an ellipsoid defined by its centroid, eigenvalues and 3x3
 * eigenvector matrix. Semiaxis lengths (radii) are calculated as the inverse
 * square root of the eigenvalues.
 * </p>
 *
 * @author Michael Doube
 * @author Alessandro Felder
 */
public class QuickEllipsoid {

	private RandomGenerator rng = new MersenneTwister();
	private UnitSphereRandomVectorGenerator sphereRng = new UnitSphereRandomVectorGenerator(3,rng);
	private final static int randomNumberRefreshmentPeriodicity = 100;
	private final static int numberOfPreallocatedRandomNumbers = 150;
	private int lastRefreshed = 0;
	private double[][] sphereRandomVectors;
	private double[] uniformRandomNumbers;

	/**
	 * Eigenvalue matrix. Size-based ordering is not performed. They are in the same
	 * order as the eigenvectors.
	 */
	private final double[][] ed;
	/**
	 * Centroid of ellipsoid (cx, cy, cz)
	 */
	private double cx;
	private double cy;
	private double cz;
	/**
	 * Radii (semiaxis lengths) of ellipsoid. Size-based ordering (e.g. a > b > c)
	 * is not performed. They are in the same order as the eigenvalues and
	 * eigenvectors.
	 */
	private double ra;
	private double rb;
	private double rc;

	/**
	 * Eigenvector matrix Size-based ordering is not performed. They are in the same
	 * order as the eigenvalues.
	 */
	private double[][] ev;
	/**
	 * 3x3 matrix describing shape of ellipsoid
	 */
	private double[][] eh;

	/**
	 * Construct an QuickEllipsoid from the radii (a,b,c), centroid (cx, cy, cz) and
	 * Eigenvectors.
	 *
	 * @param radii radii (a,b,c) as a double array
	 * @param centroid ellipsoid centre x,y,z coordinates as a double array
	 * @param eigenVectors the orientation of the ellipsoid.
	 *
	 */
	public QuickEllipsoid(final double[] radii, final double[] centroid, final double[][] eigenVectors) {

		ra = radii[0];
		rb = radii[1];
		rc = radii[2];
		this.cx = centroid[0];
		this.cy = centroid[1];
		this.cz = centroid[2];
		ev = new double[3][3];
		ed = new double[3][3];
		eh = new double[3][3];
		setRotation(eigenVectors);
		setEigenvalues();
	}

	/**
	 * Transpose a 3x3 matrix in double[][] format. Does no error checking.
	 *
	 * @param a
	 *            a matrix.
	 * @return new transposed matrix.
	 */
	public static double[][] transpose(final double[][] a) {
		final double[][] t = new double[3][3];
		t[0][0] = a[0][0];
		t[0][1] = a[1][0];
		t[0][2] = a[2][0];
		t[1][0] = a[0][1];
		t[1][1] = a[1][1];
		t[1][2] = a[2][1];
		t[2][0] = a[0][2];
		t[2][1] = a[1][2];
		t[2][2] = a[2][2];
		return t;
	}

	/**
	 * High performance 3x3 matrix multiplier with no bounds or error checking
	 *
	 * @param a
	 *            3x3 matrix
	 * @param b
	 *            3x3 matrix
	 * @return result of matrix multiplication, c = ab
	 */
	private static double[][] times(final double[][] a, final double[][] b) {
		final double a00 = a[0][0];
		final double a01 = a[0][1];
		final double a02 = a[0][2];
		final double a10 = a[1][0];
		final double a11 = a[1][1];
		final double a12 = a[1][2];
		final double a20 = a[2][0];
		final double a21 = a[2][1];
		final double a22 = a[2][2];
		final double b00 = b[0][0];
		final double b01 = b[0][1];
		final double b02 = b[0][2];
		final double b10 = b[1][0];
		final double b11 = b[1][1];
		final double b12 = b[1][2];
		final double b20 = b[2][0];
		final double b21 = b[2][1];
		final double b22 = b[2][2];
		return new double[][]{
				{a00 * b00 + a01 * b10 + a02 * b20, a00 * b01 + a01 * b11 + a02 * b21,
						a00 * b02 + a01 * b12 + a02 * b22},
				{a10 * b00 + a11 * b10 + a12 * b20, a10 * b01 + a11 * b11 + a12 * b21,
						a10 * b02 + a11 * b12 + a12 * b22},
				{a20 * b00 + a21 * b10 + a22 * b20, a20 * b01 + a21 * b11 + a22 * b21,
						a20 * b02 + a21 * b12 + a22 * b22},};
	}

	/**
	 * Method based on the inequality (X-X0)^T H (X-X0) &le; 1 Where X is the test
	 * point, X0 is the centroid, H is the ellipsoid's 3x3 matrix
	 *
	 * @param x
	 *            x-coordinate of the point.
	 * @param y
	 *            y-coordinate of the point.
	 * @param z
	 *            z-coordinate of the point.
	 * @return true if the point (x,y,z) lies inside or on the ellipsoid, false
	 *         otherwise
	 */
	public boolean contains(final double x, final double y, final double z) {
		// calculate vector between point and centroid
		final double vx = x - cx;
		final double vy = y - cy;
		final double vz = z - cz;

		final double[] radii = getSortedRadii();
		final double maxRadius = radii[2];

		// if further than maximal sphere's bounding box, must be outside
		if (Math.abs(vx) > maxRadius || Math.abs(vy) > maxRadius || Math.abs(vz) > maxRadius)
			return false;

		// calculate distance from centroid
		final double length = Math.sqrt(vx * vx + vy * vy + vz * vz);

		// if further from centroid than major semiaxis length
		// must be outside
		if (length > maxRadius)
			return false;

		// if length closer than minor semiaxis length
		// must be inside
		if (length <= radii[0])
			return true;

		final double[][] h = getEllipsoidTensor();

		final double dot0 = vx * h[0][0] + vy * h[1][0] + vz * h[2][0];
		final double dot1 = vx * h[0][1] + vy * h[1][1] + vz * h[2][1];
		final double dot2 = vx * h[0][2] + vy * h[1][2] + vz * h[2][2];

		final double dot = dot0 * vx + dot1 * vy + dot2 * vz;

		return dot <= 1;
	}

	/**
	 * Gets an up to date ellipsoid tensor (H)
	 *
	 * @return 3×3 matrix containing H, the ellipsoid tensor
	 */
	private double[][] getEllipsoidTensor() {
		if (this.eh == null) {
			this.eh = times(times(ev, ed), transpose(ev));
		}
		return this.eh;
	}

	/**
	 * Constrict all three axes by a fractional increment
	 *
	 * @param increment
	 *            scaling factor.
	 */
	public void contract(final double increment) {
		dilate(-increment);
	}

	/**
	 * Perform a deep copy of this QuickEllipsoid
	 *
	 * @return a copy of the instance.
	 */
	public QuickEllipsoid copy() {
		final double[][] clone = new double[ev.length][];
		for (int i = 0; i < ev.length; i++) {
			clone[i] = ev[i].clone();
		}
		return new QuickEllipsoid(new double[]{ra, rb, rc}, new double[]{cx, cy, cz}, clone);
	}

	/**
	 * Dilate the ellipsoid semiaxes by independent absolute amounts
	 *
	 * @param da
	 *            value added to the 1st radius.
	 * @param db
	 *            value added to the 2nd radius.
	 * @param dc
	 *            value added to the 3rd radius.
	 * @throws IllegalArgumentException
	 *             if new semi-axes are non-positive.
	 */
	public void dilate(final double da, final double db, final double dc) throws IllegalArgumentException {

		final double a = ra + da;
		final double b = rb + db;
		final double c = rc + dc;
		if (a <= 0 || b <= 0 || c <= 0) {
			throw new IllegalArgumentException("QuickEllipsoid cannot have semiaxis <= 0");
		}
		setRadii(a, b, c);
	}

	public double[] getCentre() {
		return new double[]{cx, cy, cz};
	}

	/**
	 * Gets a copy of the radii.
	 *
	 * @return the semiaxis lengths a, b and c. Note these are not ordered by size,
	 *         but the order does relate to the 0th, 1st and 2nd columns of the
	 *         rotation matrix respectively.
	 */
	public double[] getRadii() {
		return new double[]{ra, rb, rc};
	}

	/**
	 * Return a copy of the ellipsoid's eigenvector matrix
	 *
	 * @return a 3x3 rotation matrix
	 */
	public double[][] getRotation() {
		return ev.clone();
	}

	/**
	 * Set rotation to the supplied rotation matrix. Does no error checking.
	 *
	 * @param rotation
	 *            a 3x3 rotation matrix
	 */
	public void setRotation(final double[][] rotation) {
		ev = rotation.clone();
		update3x3Matrix();
	}

	/**
	 * Get the radii sorted in ascending order. Note that there is no guarantee that
	 * this ordering relates at all to the eigenvectors or eigenvalues.
	 *
	 * @return radii in ascending order
	 */
	public double[] getSortedRadii() {
		final double[] sortedRadii = {ra, rb, rc};
		Arrays.sort(sortedRadii);
		return sortedRadii;
	}


	public double[][] getSurfacePoints(final double[][] vectors) {
		final int nPoints = vectors.length;
		for (int p = 0; p < nPoints; p++) {
			final double[] v = vectors[p];

			// stretch the unit sphere into an ellipsoid
			final double x = ra * v[0];
			final double y = rb * v[1];
			final double z = rc * v[2];
			// rotate and translate the ellipsoid into position
			final double vx = x * ev[0][0] + y * ev[0][1] + z * ev[0][2] + cx;
			final double vy = x * ev[1][0] + y * ev[1][1] + z * ev[1][2] + cy;
			final double vz = x * ev[2][0] + y * ev[2][1] + z * ev[2][2] + cz;

			vectors[p] = new double[]{vx, vy, vz};
		}
		return vectors;
	}

	public double[][] getAxisAlignRandomlyDistributedSurfacePoints(int n) {
		refreshRandomNumbersIfNeeded();

		final double[] sortedRadii = getSortedRadii();
		final double muMax = sortedRadii[1]* sortedRadii[2];
		final double[][] surfacePoints = new double[n][3];
		int surfacePointsFound = 0;
		int attemptCounter = 0;
		while (surfacePointsFound<n) {
			final double[] v =  (attemptCounter<numberOfPreallocatedRandomNumbers)? sphereRandomVectors[attemptCounter] : sphereRng.nextVector();
			final double mu = getMu(v);
			double rn = (attemptCounter<numberOfPreallocatedRandomNumbers) ? uniformRandomNumbers[attemptCounter] : rng.nextDouble();
			if(rn<=mu/muMax) {
				surfacePoints[surfacePointsFound] = new double[]{v[0], v[1], v[2]};
				surfacePointsFound++;
			}
			attemptCounter++;
		}
		return surfacePoints;
	}

	private void refreshRandomNumbersIfNeeded() {
		if(sphereRandomVectors==null)
		{
			sphereRandomVectors = new double[numberOfPreallocatedRandomNumbers][3];
			uniformRandomNumbers = new double[numberOfPreallocatedRandomNumbers];
		}

		if((lastRefreshed % randomNumberRefreshmentPeriodicity)==0)
		{
			for(int i=0;i<numberOfPreallocatedRandomNumbers;i++)
			{
				sphereRandomVectors[i] = sphereRng.nextVector();
				uniformRandomNumbers[i] = rng.nextDouble();
			}
		}
		lastRefreshed++;
	}

	private double getMu(final double[] v) {
		final double ra2 = ra*ra;
		final double rb2 = rb*rb;
		final double rc2 = rc*rc;
		final double sqSum = ra2*rc2*v[0]*v[0]+ra2*rb2*v[2]*v[2]+rb2*rc2*v[0]*v[0];
		return Math.sqrt(sqSum);
	}

	/**
	 * Gets the volume of this ellipsoid, calculated as PI * a * b * c * 4 / 3
	 *
	 * @return copy of the stored volume value.
	 */
	public double getVolume() {
		return 4.0 * Math.PI * ra * rb * rc / 3.0;
	}

	/**
	 * Rotate the ellipsoid by the given 3x3 Matrix
	 *
	 * @param rotation
	 *            a 3x3 rotation matrix
	 */
	public void rotate(final double[][] rotation) {
		setRotation(times(ev, rotation));
	}

	/**
	 * Translate the ellipsoid to a given new centroid
	 *
	 * @param x
	 *            new centroid x-coordinate
	 * @param y
	 *            new centroid y-coordinate
	 * @param z
	 *            new centroid z-coordinate
	 */
	public void setCentroid(final double x, final double y, final double z) {
		cx = x;
		cy = y;
		cz = z;
	}

	/**
	 * Calculates eigenvalues from current radii
	 */
	private void setEigenvalues() {
		ed[0][0] = 1 / (ra * ra);
		ed[1][1] = 1 / (rb * rb);
		ed[2][2] = 1 / (rc * rc);
		update3x3Matrix();
	}

	/**
	 * Set the radii (semiaxes). No ordering is assumed, except with regard to the
	 * columns of the eigenvector rotation matrix (i.e. a relates to the 0th
	 * eigenvector column, b to the 1st and c to the 2nd)
	 *
	 * @param a
	 *            1st radius of the ellipsoid.
	 * @param b
	 *            2nd radius of the ellipsoid.
	 * @param c
	 *            3rd radius of the ellipsoid.
	 * @throws IllegalArgumentException
	 *             if radii are non-positive.
	 */
	private void setRadii(final double a, final double b, final double c) throws IllegalArgumentException {
		ra = a;
		rb = b;
		rc = c;
		setEigenvalues();
	}

	/**
	 * Needs to be run any time the eigenvalues or eigenvectors change
	 */
	private void update3x3Matrix() {
		eh = null;
	}

	/**
	 * Dilate all three axes by a fractional increment
	 *
	 * @param increment
	 *            scaling factor.
	 */
	private void dilate(final double increment) {
		dilate(ra * increment, rb * increment, rc * increment);
	}
}
