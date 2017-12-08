/*
 * #%L
 * BoneJ: open source tools for trabecular geometry and whole bone shape analysis.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers. See also individual class @authors.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.bonej.geometry;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

/**
 * <p>
 * Represents an ellipsoid defined by its centroid, eigenvalues and 3x3
 * eigenvector matrix. Semiaxis lengths (radii) are calculated as the inverse
 * square root of the eigenvalues.
 * </p>
 *
 * @author Michael Doube
 */
public class Ellipsoid {

	/** Centroid of ellipsoid (cx, cy, cz) */
	private double cx, cy, cz;

	/**
	 * Radii (semiaxis lengths) of ellipsoid. Size-based ordering (e.g. a > b >
	 * c) is not performed. They are in the same order as the eigenvalues and
	 * eigenvectors.
	 */
	private double ra, rb, rc;

	/** Volume of ellipsoid, calculated as 4 * PI * ra * rb * rc / 3 */
	private double volume;

	/**
	 * Eigenvector matrix Size-based ordering is not performed. They are in the
	 * same order as the eigenvalues.
	 */
	private double[][] ev;

	/**
	 * Eigenvalue matrix. Size-based ordering is not performed. They are in the
	 * same order as the eigenvectors.
	 */
	private final double[][] ed;

	/** 3x3 matrix describing shape of ellipsoid */
	private double[][] eh;

	/** ID field for tracking this particular ellipsoid */
	public int id;

	/**
	 * Instantiate an ellipsoid from the result of FitEllipsoid
	 *
	 * @param ellipsoid the properties of an ellipsoid.
	 */
	public Ellipsoid(final Object[] ellipsoid) {
		final double[] centre = (double[]) ellipsoid[0];
		this.cx = centre[0];
		this.cy = centre[1];
		this.cz = centre[2];

		final double[] radii = (double[]) ellipsoid[1];
		this.ra = radii[0];
		this.rb = radii[1];
		this.rc = radii[2];

		if (Double.isNaN(ra) || Double.isNaN(rb) || Double.isNaN(rc))
			throw new IllegalArgumentException("Radius is NaN");

		if (ra <= 0 || rb <= 0 || rc <= 0)
			throw new IllegalArgumentException("Radius cannot be <= 0");

		this.ev = new double[3][3];
		this.ed = new double[3][3];
		this.eh = new double[3][3];
		setRotation((double[][]) ellipsoid[2]);
		setEigenvalues();
		setVolume();
	}

	/**
	 * Construct an Ellipsoid from the radii (a,b,c), centroid (cx, cy, cz) and
	 * Eigenvectors.
	 *
	 * @param a 1st radius.
	 * @param b 2nd radius.
	 * @param c 3rd radius.
	 * @param cx centroid x-coordinate.
	 * @param cy centroid y-coordinate.
	 * @param cz centroid z-coordinate.
	 * @param eigenVectors the orientation of the ellipsoid.
	 */
	public Ellipsoid(final double a, final double b, final double c, final double cx, final double cy, final double cz,
			final double[][] eigenVectors) {

		this.ra = a;
		this.rb = b;
		this.rc = c;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.ev = new double[3][3];
		this.ed = new double[3][3];
		this.eh = new double[3][3];
		setRotation(eigenVectors);
		setEigenvalues();
		setVolume();
	}

	/**
	 * Gets the volume of this ellipsoid, calculated as PI * a * b * c * 4 / 3
	 *
	 * @return copy of the stored volume value.
	 */
	public double getVolume() {
        return this.volume;
	}

	private void setVolume() {
		volume = Math.PI * ra * rb * rc * 4 / 3;
	}

	/**
     * Gets a copy of the radii.
     *
	 * @return the semiaxis lengths a, b and c. Note these are not ordered by
	 *         size, but the order does relate to the 0th, 1st and 2nd columns
	 *         of the rotation matrix respectively.
	 */
	public double[] getRadii() {
		final double[] radii = { ra, rb, rc };
		return radii.clone();
	}

	/**
	 * Method based on the inequality
	 *
	 * (X-X0)^T H (X-X0) <= 1
	 *
	 * Where X is the test point, X0 is the centroid, H is the ellipsoid's 3x3
	 * matrix
	 *
	 * @param x x-coordinate of the point.
	 * @param y y-coordinate of the point.
	 * @param z z-coordinate of the point.
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

		final double[][] h = eh;

		final double dot0 = vx * h[0][0] + vy * h[1][0] + vz * h[2][0];
		final double dot1 = vx * h[0][1] + vy * h[1][1] + vz * h[2][1];
		final double dot2 = vx * h[0][2] + vy * h[1][2] + vz * h[2][2];

		final double dot = dot0 * vx + dot1 * vy + dot2 * vz;

		if (dot <= 1)
			return true;

		return false;
	}

	/**
	 * Get the radii sorted in ascending order. Note that there is no guarantee
	 * that this ordering relates at all to the eigenvectors or eigenvalues.
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

	public double[] getCentre() {
		final double[] centre = { cx, cy, cz };
		return centre.clone();
	}

	public double[][] getSurfacePoints(final int nPoints) {

		// get regularly-spaced points on the unit sphere
		final double[][] vectors = Vectors.regularVectors(nPoints);
		return getSurfacePoints(vectors);

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

			vectors[p] = new double[] { vx, vy, vz };
		}
		return vectors;
	}

	/**
	 * Dilate all three axes by a fractional increment
	 *
	 * @param increment scaling factor.
	 */
	public void dilate(final double increment) {
		dilate(this.ra * increment, this.rb * increment, this.rc * increment);
	}

	/**
	 * Constrict all three axes by a fractional increment
	 *
	 * @param increment scaling factor.
	 */
	// TODO Shouldn't this divide the radii, or does it call the wrong dilate..?!
	public void contract(final double increment) {
		dilate(-increment);
	}

	/**
	 * Dilate the ellipsoid semiaxes by independent absolute amounts
	 *
	 * @param da value added to the 1st radius.
	 * @param db value added to the 2nd radius.
	 * @param dc value added to the 3rd radius.
	 */
	public void dilate(final double da, final double db, final double dc) {
		setRadii(this.ra + da, this.rb + db, this.rc + dc);
	}

	/**
	 * Translate the ellipsoid to a given new centroid
	 *
	 * @param x new centroid x-coordinate
	 * @param y new centroid y-coordinate
	 * @param z new centroid z-coordinate
	 */
	public void setCentroid(final double x, final double y, final double z) {
		this.cx = x;
		this.cy = y;
		this.cz = z;
	}

	/**
	 * Rotate the ellipsoid by the given 3x3 Matrix
	 *
	 * @param rotation a 3x3 rotation matrix
	 */
	public void rotate(final double[][] rotation) {
		setRotation(times(this.ev, rotation));
	}

	/**
	 * Set rotation to the supplied rotation matrix. Does no error checking.
     *
     * @param rotation a 3x3 rotation matrix
	 */
	public void setRotation(final double[][] rotation) {
		this.ev = rotation.clone();
		update3x3Matrix();
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
	 * Set the radii (semiaxes). No ordering is assumed, except with regard to the
	 * columns of the eigenvector rotation matrix (i.e. a relates to the 0th
	 * eigenvector column, b to the 1st and c to the 2nd)
	 *
	 * @param a 1st radius of the ellipsoid.
	 * @param b 2nd radius of the ellipsoid.
	 * @param c 3rd radius of the ellipsoid.
	 * @throws IllegalArgumentException if radii are non-positive.
	 */
	public void setRadii(final double a, final double b, final double c) throws IllegalArgumentException {
		if (a <= 0 || b <= 0 || c <= 0) {
			throw new IllegalArgumentException("Ellipsoid cannot have semiaxis <= 0");
		}
		this.ra = a;
		this.rb = b;
		this.rc = c;
		setEigenvalues();
		setVolume();
	}

	/**
	 * Calculates eigenvalues from current radii
	 */
	private void setEigenvalues() {
		this.ed[0][0] = 1 / (this.ra * this.ra);
		this.ed[1][1] = 1 / (this.rb * this.rb);
		this.ed[2][2] = 1 / (this.rc * this.rc);
		update3x3Matrix();
	}

	/**
	 * Needs to be run any time the eigenvalues or eigenvectors change
	 */
	private void update3x3Matrix() {
		this.eh = times(times(ev, ed), transpose(ev));
	}

	/**
	 * Calculate the minimal and maximal x values bounding this ellipsoid
	 *
	 * @return array containing minimal and maximal x values
	 */
	public double[] getXMinAndMax() {
		final double m11 = ev[0][0] * ra;
		final double m12 = ev[0][1] * rb;
		final double m13 = ev[0][2] * rc;
		final double d = Math.sqrt(m11 * m11 + m12 * m12 + m13 * m13);
		final double[] minMax = { cx - d, cx + d };
		return minMax;
	}

	/**
	 * Calculate the minimal and maximal y values bounding this ellipsoid
	 *
	 * @return array containing minimal and maximal y values
	 */
	public double[] getYMinAndMax() {
		final double m21 = ev[1][0] * ra;
		final double m22 = ev[1][1] * rb;
		final double m23 = ev[1][2] * rc;
		final double d = Math.sqrt(m21 * m21 + m22 * m22 + m23 * m23);
		final double[] minMax = { cy - d, cy + d };
		return minMax;
	}

	/**
	 * Calculate the minimal and maximal z values bounding this ellipsoid
	 *
	 * @return array containing minimal and maximal z values
	 */
	public double[] getZMinAndMax() {
		final double m31 = ev[2][0] * ra;
		final double m32 = ev[2][1] * rb;
		final double m33 = ev[2][2] * rc;
		final double d = Math.sqrt(m31 * m31 + m32 * m32 + m33 * m33);
		final double[] minMax = { cz - d, cz + d };
		return minMax;
	}

	/**
	 * Calculate the minimal axis-aligned bounding box of this ellipsoid
	 *
	 * Thanks to Tavian Barnes for the simplification of the maths
	 * http://tavianator.com/2014/06/exact-bounding-boxes-for-spheres-ellipsoids
	 *
	 *
	 * @return 6-element array containing x min, x max, y min, y max, z min, z
	 *         max
	 */
	public double[] getAxisAlignedBoundingBox() {
		final double[] x = getXMinAndMax();
		final double[] y = getYMinAndMax();
		final double[] z = getZMinAndMax();
		final double[] boundingBox = { x[0], x[1], y[0], y[1], z[0], z[1] };
		return boundingBox;
	}

	/**
	 * High performance 3x3 matrix multiplier with no bounds or error checking
	 *
	 * @param a 3x3 matrix
	 * @param b 3x3 matrix
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
		final double[][] c = {
				{ a00 * b00 + a01 * b10 + a02 * b20, a00 * b01 + a01 * b11 + a02 * b21,
						a00 * b02 + a01 * b12 + a02 * b22 },
				{ a10 * b00 + a11 * b10 + a12 * b20, a10 * b01 + a11 * b11 + a12 * b21,
						a10 * b02 + a11 * b12 + a12 * b22 },
				{ a20 * b00 + a21 * b10 + a22 * b20, a20 * b01 + a21 * b11 + a22 * b21,
						a20 * b02 + a21 * b12 + a22 * b22 }, };
		return c;
	}

	/**
	 * Transpose a 3x3 matrix in double[][] format. Does no error checking.
     *
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
	 * Calculate the matrix representation of the ellipsoid (centre, eigenvalues,
	 * eigenvectors) from the equation <i>ax</i> <sup>2</sup> +
	 * <i>by</i><sup>2</sup> + <i>cz</i><sup>2</sup> + 2 <i>dxy</i> + 2<i>exz</i>
	 * + 2<i>fyz</i> + 2<i>gx</i> + 2<i>hy</i> + 2 <i>iz</i> = 1 <br />
	 *
	 * @param a coefficient of <em>x<sup>2</sup></em>
	 * @param b coefficient of <em>y<sup>2</sup></em>
	 * @param c coefficient of <em>z<sup>2</sup></em>.
	 * @param d coefficient of <em>x</em><em>y</em>.
	 * @param e coefficient of <em>x</em><em>z</em>.
	 * @param f coefficient of <em>y</em><em>z</em>.
	 * @param g coefficient of 2<em>x</em>.
	 * @param h coefficient of 2<em>y</em>.
	 * @param i coefficient of 2<em>z</em>.
	 * @return Object[] array containing centre (double[3]), eigenvalues
	 *         (double[3][3]), eigenvectors (double[3][3]), and the
	 *         EigenvalueDecomposition
	 */
	public static Object[] matrixFromEquation(final double a, final double b, final double c, final double d,
			final double e, final double f, final double g, final double h, final double i) {

		// the fitted equation
		final double[][] v = { { a }, { b }, { c }, { d }, { e }, { f }, { g }, { h }, { i } };
		final Matrix V = new Matrix(v);

		// 4x4 based on equation variables
		final double[][] aa = { { a, d, e, g }, { d, b, f, h }, { e, f, c, i }, { g, h, i, -1 }, };
		final Matrix A = new Matrix(aa);

		// find the centre
		final Matrix C = (A.getMatrix(0, 2, 0, 2).times(-1).inverse()).times(V.getMatrix(6, 8, 0, 0));

		// using the centre and 4x4 calculate the
		// eigendecomposition
		final Matrix T = Matrix.identity(4, 4);
		T.setMatrix(3, 3, 0, 2, C.transpose());
		final Matrix R = T.times(A.times(T.transpose()));
		final double r33 = R.get(3, 3);
		final Matrix R02 = R.getMatrix(0, 2, 0, 2);
		final EigenvalueDecomposition E = new EigenvalueDecomposition(R02.times(-1 / r33));

		final double[] centre = C.getColumnPackedCopy();
		final double[][] eigenVectors = E.getV().getArrayCopy();
		final double[][] eigenValues = E.getD().getArrayCopy();
		final Object[] result = { centre, eigenValues, eigenVectors, E };
		return result;
	}

	/**
	 * Perform a deep copy of this Ellipsoid
	 *
	 * @return a copy of the instance.
	 */
	public Ellipsoid copy() {
		final Ellipsoid copy = new Ellipsoid(this.ra, this.rb, this.rc, this.cx, this.cy, this.cz, this.ev.clone());
		return copy;
	}
}
