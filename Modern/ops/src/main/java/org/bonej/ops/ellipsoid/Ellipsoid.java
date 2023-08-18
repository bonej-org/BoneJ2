/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
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
public class Ellipsoid {

	/**
	 * Eigenvalue matrix. Size-based ordering is not performed. They are in the same
	 * order as the eigenvectors. In Flat 9 format, so diagonal is on 0, 4, 8, rest is 0.
	 */
	private final double[] ed;
	
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
	 * order as the eigenvalues. Flattened so that ev[3m + n] = ev[m][n].
	 */
	private double[] ev;
	
	/**
	 * 3x3 matrix describing shape of ellipsoid. Flattened so that ev[3m + n] = ev[m][n].
	 */
	private double[] eh;

	/**
	 * Construct an Ellipsoid from the radii (a,b,c), centroid (cx, cy, cz) and
	 * Eigenvectors.
	 *
	 * @param radii radii (a,b,c) as a double array
	 * @param centroid ellipsoid centre x,y,z coordinates as a double array
	 * @param eigenVectors the orientation of the ellipsoid.
	 *
	 */
	public Ellipsoid(final double[] radii, final double[] centroid, final double[][] eigenVectors) {

		ra = radii[0];
		rb = radii[1];
		rc = radii[2];
		this.cx = centroid[0];
		this.cy = centroid[1];
		this.cz = centroid[2];
		ev = new double[9];
		ed = new double[9];
		eh = new double[9];
		setRotation(eigenVectors);
		setEigenvalues();
	}

	/**
	 * Transpose a 3x3 matrix in double[9] format. Does no error checking.
	 * a[m][n] = a[3m + n]
	 *
	 * @param a
	 *            a matrix.
	 * @return new transposed matrix.
	 */
	public static double[] transpose(final double[] a) {
		final double[] t = new double[9];
		t[0] = a[0];
		t[1] = a[3];
		t[2] = a[6];
		t[3] = a[1];
		t[4] = a[4];
		t[5] = a[7];
		t[6] = a[2];
		t[7] = a[5];
		t[8] = a[8];
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
	private static double[] times(final double[] a, final double[] b) {
		final double a00 = a[0];
		final double a01 = a[1];
		final double a02 = a[2];
		final double a10 = a[3];
		final double a11 = a[4];
		final double a12 = a[5];
		final double a20 = a[6];
		final double a21 = a[7];
		final double a22 = a[8];
		final double b00 = b[0];
		final double b01 = b[1];
		final double b02 = b[2];
		final double b10 = b[3];
		final double b11 = b[4];
		final double b12 = b[5];
		final double b20 = b[6];
		final double b21 = b[7];
		final double b22 = b[8];
		return new double[]{
				a00 * b00 + a01 * b10 + a02 * b20, a00 * b01 + a01 * b11 + a02 * b21,
						a00 * b02 + a01 * b12 + a02 * b22,
				a10 * b00 + a11 * b10 + a12 * b20, a10 * b01 + a11 * b11 + a12 * b21,
						a10 * b02 + a11 * b12 + a12 * b22,
				a20 * b00 + a21 * b10 + a22 * b20, a20 * b01 + a21 * b11 + a22 * b21,
						a20 * b02 + a21 * b12 + a22 * b22};
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
		final double minRadius = radii[0];
		if (length <= minRadius)
			return true;

		final double[] h = getEllipsoidTensor();

		final double dot0 = vx * h[0] + vy * h[3] + vz * h[6];
		final double dot1 = vx * h[1] + vy * h[4] + vz * h[7];
		final double dot2 = vx * h[2] + vy * h[5] + vz * h[8];

		final double dot = dot0 * vx + dot1 * vy + dot2 * vz;

		return dot <= 1;
	}

	/**
	 * Gets an up to date ellipsoid tensor (H)
	 *
	 * @return 3×3 matrix containing H, the ellipsoid tensor, as a flat double[9] where H[3m + n] = H[m][n]
	 */
	protected double[] getEllipsoidTensor() {
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
	 * Perform a deep copy of this Ellipsoid
	 *
	 * @return a copy of the instance.
	 */
	public Ellipsoid copy() {
		return new Ellipsoid(new double[]{ra, rb, rc}, new double[]{cx, cy, cz}, flat9ToSquare3x3(ev));
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
			throw new IllegalArgumentException("Ellipsoid cannot have semiaxis <= 0");
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
	 * Return a copy of the ellipsoid's eigenvector matrix in 3x3 format
	 *
	 * @return a 3x3 rotation matrix
	 */
	public double[][] getRotation() {
		return flat9ToSquare3x3(ev);
	}
	
	/**
	 * Return a copy of the ellipsoid's eigenvector matrix in 9x1 format
	 *
	 * @return a 3x3 rotation matrix in flat9 format: a[3m + n] = a[m][n]
	 */
	public double[] getRotationFlat9() {
		return ev.clone();
	}

	private double[][] flat9ToSquare3x3(double[] a){
		final double[][] b = new double[3][3];
		b[0][0] = a[0];
		b[0][1] = a[1];
		b[0][2] = a[2];
		b[1][0] = a[3];
		b[1][1] = a[4];
		b[1][2] = a[5];
		b[2][0] = a[6];
		b[2][1] = a[7];
		b[2][2] = a[8];
		return b;
	}
	
	private double[] square3x3Toflat9(double[][] a){
		final double[] b = new double[9];
		b[0] = a[0][0];
		b[1] = a[0][1];
		b[2] = a[0][2];
		b[3] = a[1][0];
		b[4] = a[1][1];
		b[5] = a[1][2];
		b[6] = a[2][0];
		b[7] = a[2][1];
		b[8] = a[2][2];
		return b;
	}
	
	/**
	 * Set rotation to the supplied rotation matrix. Does no error checking.
	 *
	 * @param rotation
	 *            a 3x3 rotation matrix
	 */
	public void setRotation(final double[][] rotation) {
		ev = square3x3Toflat9(rotation);
		update3x3Matrix();
	}
	
	/**
	 * Set rotation to the supplied rotation matrix. Does no error checking.
	 *
	 * @param rotation
	 *            a 3x3 rotation matrix as a flat double[9] with rot[3m + n] = rot[m][n]
	 */
	public void setRotation(final double[] rotation) {
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
		double a = this.ra;
		double b = this.rb;
		double c = this.rc;
		double temp = 0;

		if (a > b) {
			temp = a;
			a = b;
			b = temp;
		}
		if (b > c) {
			temp = b;
			b = c;
			c = temp;
		}
		if (a > b) {
			temp = a;
			a = b;
			b = temp;
		}

		final double[] sortedRadii = { a, b, c };

		return sortedRadii;
	}


	public double[][] getSurfacePoints(final double[][] vectors) {
		final int nPoints = vectors.length;
		final double[][] surfacePoints = new double[nPoints][3];
		for (int p = 0; p < nPoints; p++) {
			surfacePoints[p] = getSurfacePoint(vectors[p]);
		}
		return surfacePoints;
	}

	/**
	 * Find the coordinates of the point on the ellipsoid found
	 * by extending the given unit vector v from the centroid of this ellipsoid.
	 *  
	 * @param v 3-element unit vector
	 * @return coordinates on the ellipsoid that the unit vector points to from the ellipsoid's centre
	 */
	public double[] getSurfacePoint(final double[] v) {
		// stretch the unit sphere into an ellipsoid
		final double x = ra * v[0];
		final double y = rb * v[1];
		final double z = rc * v[2];
		// rotate and translate the ellipsoid into position
		final double vx = x * ev[0] + y * ev[1] + z * ev[2] + cx;
		final double vy = x * ev[3] + y * ev[4] + z * ev[3] + cy;
		final double vz = x * ev[6] + y * ev[7] + z * ev[8] + cz;

		return new double[]{vx, vy, vz};
	}

	/**
	 * Gets the volume of this ellipsoid, calculated as PI * a * b * c * 4 / 3
	 *
	 * @return ellipsoid's volume
	 */
	public double getVolume() {
		return 4.0 * Math.PI * ra * rb * rc / 3.0;
	}

	/**
	 * Rotate the ellipsoid by the given 3x3 Matrix
	 *
	 * @param rotation
	 *            a 3x3 rotation matrix as 
	 */
	public void rotate(final double[] rotation) {
		setRotation(times(ev, rotation));
	}
	
	public void rotate(final double[][] rotation) {
		rotate(square3x3Toflat9(rotation));
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
		ed[0] = 1 / (ra * ra);
		ed[4] = 1 / (rb * rb);
		ed[8] = 1 / (rc * rc);
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
