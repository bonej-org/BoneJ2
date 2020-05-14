package org.bonej.ops.ellipsoid;

import java.util.*;

import org.bonej.ops.ellipsoid.constrain.EllipsoidConstrainStrategy;
import org.joml.Vector3d;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;

/**
 * An Op that handles the stochastic optimisation of local ellipsoids for the Ellipsoid Factor algorithm
 * <p>
 *     Main inputs are a byte array representing the fore- and background, and a seeding point.
 *     The input {@link OptimisationParameters} regulate the optimisation.
 *     The input {@link EllipsoidConstrainStrategy} determine how the seed point is constrained.
 *     The optimisation consists of a custom order of stochastically "bumping","wiggling" and "turning"
 *     the ellipsoid until it achieves a locally maximum volume.
 *     Returns a locally maximal ellipsoid.
 * </p>
 *
 * @author Alessandro Felder
 */

@Plugin(type = Op.class)
public class EllipsoidOptimisationStrategy extends AbstractBinaryFunctionOp<byte[][], Vector3d, QuickEllipsoid> {
	@Parameter
	private long[] imageDimensions;//TODO shift into OptimisationParameters
	@Parameter
	private LogService logService;
	@Parameter(required = false)
	private StatusService statusService;
	@Parameter
	private static EllipsoidConstrainStrategy constrainStrategy;
	@Parameter(required = false)
	private OptimisationParameters algorithmParameters = new OptimisationParameters(0.435,100,1,100,1.73, 1.0);
	double stackVolume;


	private static double[] threeWayShuffle() {
		final double[] a = {0, 0, 0};
		final double rand = Math.random();
		if (rand < 1.0 / 3.0)
			a[0] = 1;
		else if (rand >= 2.0 / 3.0)
			a[2] = 1;
		else
			a[1] = 1;
		return a;
	}

	private static double[][] findContactUnitVectors(final QuickEllipsoid ellipsoid,
			final ArrayList<double[]> contactPoints) {
		final double[][] unitVectors = new double[contactPoints.size()][3];
		final double[] c = ellipsoid.getCentre();
		final double cx = c[0];
		final double cy = c[1];
		final double cz = c[2];

		for (int i = 0; i < contactPoints.size(); i++) {
			final double[] p = contactPoints.get(i);
			final double px = p[0];
			final double py = p[1];
			final double pz = p[2];

			Vector3d distance = new Vector3d(px, py, pz);
			distance.sub(new Vector3d(cx, cy, cz));
			final double l = distance.length();
			final double x = (px - cx) / l;
			final double y = (py - cy) / l;
			final double z = (pz - cz) / l;
			final double[] u = {x, y, z};
			unitVectors[i] = u;
		}
		return unitVectors;
	}

	/**
	 * return true if pixel coordinate is out of image bounds
	 *
	 * @param x
	 *            pixel x-coordinate
	 * @param y
	 *            pixel y-coordinate
	 * @param z
	 *            pixel z-coordinate
	 * @param w
	 *            image dimension in x
	 * @param h
	 *            image dimension in y
	 * @param d
	 *            image dimension in z
	 * @return true if pixel is outside image bounds, false otherwise
	 */
	private static boolean isOutOfBounds(final int x, final int y, final int z, final int w, final int h, final int d) {
		return x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= d;
	}

	/**
	 * Calculate the torque of unit normals acting at the contact points
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param contactPoints
	 *            the contact points of the ellipsoid
	 * @return the torque vector
	 */
	static double[] calculateTorque(final QuickEllipsoid ellipsoid, final Iterable<double[]> contactPoints) {

		final double[] pc = ellipsoid.getCentre();
		final double cx = pc[0];
		final double cy = pc[1];
		final double cz = pc[2];

		final double[] r = ellipsoid.getRadii();
		final double a = r[0];
		final double b = r[1];
		final double c = r[2];

		final double s = 2 / (a * a);
		final double t = 2 / (b * b);
		final double u = 2 / (c * c);

		final double[][] rot = ellipsoid.getRotation();
		final double[][] inv = QuickEllipsoid.transpose(rot);

		double t0 = 0;
		double t1 = 0;
		double t2 = 0;

		for (final double[] p : contactPoints) {
			// translate point to centre on origin
			final double px = p[0] - cx;
			final double py = p[1] - cy;
			final double pz = p[2] - cz;

			// derotate the point
			final double x = inv[0][0] * px + inv[0][1] * py + inv[0][2] * pz;
			final double y = inv[1][0] * px + inv[1][1] * py + inv[1][2] * pz;
			final double z = inv[2][0] * px + inv[2][1] * py + inv[2][2] * pz;

			// calculate the unit normal on the centred and derotated ellipsoid
			final double nx = s * x;
			final double ny = t * y;
			final double nz = u * z;
			final double length = new Vector3d(nx, ny, nz).length();
			final double unx = nx / length;
			final double uny = ny / length;
			final double unz = nz / length;

			// rotate the normal back to the original ellipsoid
			final double ex = rot[0][0] * unx + rot[0][1] * uny + rot[0][2] * unz;
			final double ey = rot[1][0] * unx + rot[1][1] * uny + rot[1][2] * unz;
			final double ez = rot[2][0] * unx + rot[2][1] * uny + rot[2][2] * unz;

			final double[] torqueVector = crossProduct(px, py, pz, ex, ey, ez);

			t0 += torqueVector[0];
			t1 += torqueVector[1];
			t2 += torqueVector[2];

		}
		return new double[]{-t0, -t1, -t2};
	}

	/**
	 * Calculate the mean unit vector between the ellipsoid's centroid and contact
	 * points
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param contactPoints
	 *            the contact points
	 * @return a double array that is the mean unit vector
	 */
	private static double[] contactPointUnitVector(final QuickEllipsoid ellipsoid,
			final Collection<double[]> contactPoints) {

		final int nPoints = contactPoints.size();

		if (nPoints < 1)
			throw new IllegalArgumentException("Need at least one contact point");

		final double[] c = ellipsoid.getCentre();
		final double cx = c[0];
		final double cy = c[1];
		final double cz = c[2];
		double xSum = 0;
		double ySum = 0;
		double zSum = 0;
		for (final double[] p : contactPoints) {
			final double x = p[0] - cx;
			final double y = p[1] - cy;
			final double z = p[2] - cz;
			final double l = new Vector3d(x, y, z).length();

			xSum += x / l;
			ySum += y / l;
			zSum += z / l;
		}

		final double x = xSum / nPoints;
		final double y = ySum / nPoints;
		final double z = zSum / nPoints;
		final double l = new Vector3d(x, y, z).length();

		return new double[]{x / l, y / l, z / l};
	}

	/**
	 * Calculate the cross product of 2 vectors, both in double[3] format
	 *
	 * @param a
	 *            first vector
	 * @param b
	 *            second vector
	 * @return resulting vector in double[3] format
	 */
	private static double[] crossProduct(final double[] a, final double[] b) {
		return crossProduct(a[0], a[1], a[2], b[0], b[1], b[2]);
	}

	/**
	 * Calculate the cross product of two vectors (x1, y1, z1) and (x2, y2, z2)
	 *
	 * @param x1
	 *            x-coordinate of the 1st vector.
	 * @param y1
	 *            y-coordinate of the 1st vector.
	 * @param z1
	 *            z-coordinate of the 1st vector.
	 * @param x2
	 *            x-coordinate of the 2nd vector.
	 * @param y2
	 *            y-coordinate of the 2nd vector.
	 * @param z2
	 *            z-coordinate of the 2nd vector.
	 * @return cross product in {x, y, z} format
	 */
	private static double[] crossProduct(final double x1, final double y1, final double z1, final double x2,
			final double y2, final double z2) {
		final double x = y1 * z2 - z1 * y2;
		final double y = z1 * x2 - x1 * z2;
		final double z = x1 * y2 - y1 * x2;
		return new double[]{x, y, z};
	}

	static void findContactPointsForGivenDirections(final QuickEllipsoid ellipsoid,
			final ArrayList<double[]> contactPoints, final double[][] unitVectors, final byte[][] pixels, final int w,
			final int h, final int d) {
		contactPoints.clear();
		final double[][] points = ellipsoid.getSurfacePoints(unitVectors);
		for (final double[] p : points) {
			final int x = (int) Math.floor(p[0]);
			final int y = (int) Math.floor(p[1]);
			final int z = (int) Math.floor(p[2]);
			if (isOutOfBounds(x, y, z, w, h, d)) {
				continue;
			}
			if (pixels[z][y * w + x] != -1) {
				contactPoints.add(p);
			}
		}
	}

	/**
	 * Rotate the ellipsoid 0.1 radians around an arbitrary unit vector
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param axis
	 *            the rotation axis
	 * @see <a href=
	 *      "http://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_and_angle">Rotation
	 *      matrix from axis and angle</a>
	 */
	private static void rotateAboutAxis(final QuickEllipsoid ellipsoid, final double[] axis) {
		final double theta = 0.1;
		final double sin = Math.sin(theta);
		final double cos = Math.cos(theta);
		final double cos1 = 1 - cos;
		final double x = axis[0];
		final double y = axis[1];
		final double z = axis[2];
		final double xy = x * y;
		final double xz = x * z;
		final double yz = y * z;
		final double xsin = x * sin;
		final double ysin = y * sin;
		final double zsin = z * sin;
		final double xycos1 = xy * cos1;
		final double xzcos1 = xz * cos1;
		final double yzcos1 = yz * cos1;
		final double[][] rotation = {{cos + x * x * cos1, xycos1 - zsin, xzcos1 + ysin},
				{xycos1 + zsin, cos + y * y * cos1, yzcos1 - xsin},
				{xzcos1 - ysin, yzcos1 + xsin, cos + z * z * cos1},};

		ellipsoid.rotate(rotation);
	}

	/**
	 * Normalise a vector to have a length of 1 and the same orientation as the
	 * input vector a
	 *
	 * @param a
	 *            a 3D vector.
	 * @return Unit vector in direction of a
	 */
	private static double[] norm(final double[] a) {
		final double a0 = a[0];
		final double a1 = a[1];
		final double a2 = a[2];
		final double length = Math.sqrt(a0 * a0 + a1 * a1 + a2 * a2);

		final double[] normed = new double[3];
		normed[0] = a0 / length;
		normed[1] = a1 / length;
		normed[2] = a2 / length;
		return normed;
	}

	static void wiggle(QuickEllipsoid ellipsoid) {
		final double b = Math.random() * 0.2 - 0.1;
		final double c = Math.random() * 0.2 - 0.1;
		final double a = Math.sqrt(1 - b * b - c * c);

		final double k = Math.sqrt(a*a+b*b+c*c);
		// zeroth column, should be very close to [1, 0, 0]^T (mostly x)
		final double[] zerothColumn = {a, b, c};

		// first column, should be very close to [0, 1, 0]^T
		final double[] firstColumn = {-b/k, a/k, 0};

		// second column, should be very close to [0, 0, 1]^T
		final double[] secondColumn = norm(new double[]{-a*c, -b*c, a*a+b*b});

		// array has subarrays as rows, need them as columns
		double[][] rotation = {	{zerothColumn[0], firstColumn[0], secondColumn[0]},
								{zerothColumn[1], firstColumn[1], secondColumn[1]},
								{zerothColumn[2], firstColumn[2], secondColumn[2]}
		};

		ellipsoid.rotate(rotation);
	}

	private void inflateToFit(final QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints, final double a,
			final double b, final double c, final byte[][] pixels, final int w, final int h, final int d) {

		findContactPoints(ellipsoid, contactPoints, pixels, w, h, d);

		final double av = a * algorithmParameters.vectorIncrement;
		final double bv = b * algorithmParameters.vectorIncrement;
		final double cv = c * algorithmParameters.vectorIncrement;

		int safety = 0;
		while (contactPoints.size() < algorithmParameters.contactSensitivity && safety < algorithmParameters.maxIterations) {
			ellipsoid.dilate(av, bv, cv);
			findContactPoints(ellipsoid, contactPoints, pixels, w, h, d);
			safety++;
		}
	}

	@Override
	public QuickEllipsoid calculate(byte[][] pixels, Vector3d seedPoint) {

		final long start = System.currentTimeMillis();

		final int w = (int) imageDimensions[0];
		final int h = (int) imageDimensions[1];
		final int d = (int) imageDimensions[2];
		stackVolume = w * h * d;

		// Instantiate a small spherical ellipsoid
		final double[] radii = {algorithmParameters.vectorIncrement, algorithmParameters.vectorIncrement, algorithmParameters.vectorIncrement};
		final double[] centre = {seedPoint.get(0), seedPoint.get(1), seedPoint.get(2)};
		final double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

		QuickEllipsoid ellipsoid = new QuickEllipsoid(radii, centre, axes);

		final List<Double> volumeHistory = new ArrayList<>();
		volumeHistory.add(ellipsoid.getVolume());

		// instantiate the ArrayList
		ArrayList<double[]> contactPoints = new ArrayList<>();

		// dilate the sphere until it hits the background
		while (isContained(ellipsoid, contactPoints, pixels, w, h, d)) {
			constrainStrategy.preConstrain(ellipsoid, seedPoint);
			ellipsoid.dilate(algorithmParameters.vectorIncrement, algorithmParameters.vectorIncrement, algorithmParameters.vectorIncrement);
			constrainStrategy.postConstrain(ellipsoid);
		}

		volumeHistory.add(ellipsoid.getVolume());

		orientAxes(ellipsoid, contactPoints);

		// shrink the ellipsoid slightly
		shrinkToFit(ellipsoid, contactPoints, pixels, w, h, d);
		ellipsoid.contract(0.1);

		// dilate other two axes until number of contact points increases
		// by contactSensitivity number of contacts

		while (contactPoints.size() < algorithmParameters.contactSensitivity) {
			ellipsoid.dilate(0, algorithmParameters.vectorIncrement, algorithmParameters.vectorIncrement);
			findContactPoints(ellipsoid, contactPoints, pixels, w, h, d);
			if (isInvalid(ellipsoid, w, h, d)) {
				logService.debug("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
						+ ") is invalid, nullifying at initial oblation");
				return null;
			}
		}

		volumeHistory.add(ellipsoid.getVolume());

		// until ellipsoid is totally jammed within the structure, go through
		// cycles of contraction, wiggling, dilation
		// goal is maximal inscribed ellipsoid, maximal being defined by volume

		// store a copy of the 'best ellipsoid so far'
		QuickEllipsoid maximal = ellipsoid.copy();

		// alternately try each axis
		int totalIterations = 0;
		int noImprovementCount = 0;
		final int absoluteMaxIterations = algorithmParameters.maxIterations * 10;
		while (totalIterations < absoluteMaxIterations && noImprovementCount < algorithmParameters.maxIterations) {

			// rotate a little bit
			constrainStrategy.preConstrain(ellipsoid, seedPoint);
			wiggle(ellipsoid);
			constrainStrategy.postConstrain(ellipsoid);

			// contract until no contact
			shrinkToFit(ellipsoid, contactPoints, pixels, w, h, d);

			// dilate an axis
			double[] abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, w, h, d);

			if (isInvalid(ellipsoid, w, h, d)) {
				logService.debug("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
						+ ") is invalid, nullifying after " + totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// bump a little away from the sides
			findContactPoints(ellipsoid, contactPoints, pixels, w, h, d);
			constrainStrategy.preConstrain(ellipsoid, seedPoint);
			// if can't bump then do a wiggle
			if (contactPoints.isEmpty()) {
				wiggle(ellipsoid);
			} else {
				bump(ellipsoid, contactPoints, centre);
			}
			constrainStrategy.postConstrain(ellipsoid);
			// contract
			shrinkToFit(ellipsoid, contactPoints, pixels, w, h, d);

			// dilate an axis
			abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, w, h, d);

			if (isInvalid(ellipsoid, w, h, d)) {
				logService.debug("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
						+ ") is invalid, nullifying after " + totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// rotate a little bit
			constrainStrategy.preConstrain(ellipsoid, seedPoint);
			turn(ellipsoid, contactPoints, pixels, w, h, d);
			constrainStrategy.postConstrain(ellipsoid);

			// contract until no contact
			shrinkToFit(ellipsoid, contactPoints, pixels, w, h, d);

			// dilate an axis
			abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, w, h, d);

			if (isInvalid(ellipsoid, w, h, d)) {
				logService.debug("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
						+ ") is invalid, nullifying after " + totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// keep the maximal ellipsoid found
			ellipsoid = maximal.copy();

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// keep the maximal ellipsoid found
			ellipsoid = maximal.copy();
			// log its volume
			volumeHistory.add(ellipsoid.getVolume());

			// if the last value is bigger than the second-to-last value
			// reset the noImprovementCount
			// otherwise, increment it by 1.
			// if noImprovementCount exceeds a preset value the while() is
			// broken
			final int i = volumeHistory.size() - 1;
			if (volumeHistory.get(i) > volumeHistory.get(i - 1))
				noImprovementCount = 0;
			else
				noImprovementCount++;

			totalIterations++;
		}

		// this usually indicates that the ellipsoid
		// grew out of control for some reason
		if (totalIterations == absoluteMaxIterations) {
			logService.debug("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
					+ ") seems to be out of control, nullifying after " + totalIterations + " iterations");
			return null;
		}

		if (ellipsoid.getSortedRadii()[2]<algorithmParameters.minimumSemiAxis) {
			logService.debug("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
					+ ") is too small, nullifying after " + totalIterations + " iterations");
			return null;
		}

		final long stop = System.currentTimeMillis();

		logService.debug("Optimised ellipsoid in " + (stop - start) + " ms after " + totalIterations + " iterations ("
				+ (double) (stop - start) / totalIterations + " ms/iteration)");

		String centreString = "("+(int) centre[0]+",  "+ (int) centre[1]+",  "+(int) centre[2]+")";
		if(statusService!=null) {
			statusService.showStatus("Ellipsoid optimised at " + centreString);//non-null check needed for tests
		}

		return ellipsoid;
	}

	private void orientAxes(QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints) {
		// find the mean unit vector pointing to the points of contact from the
		// centre
		final double[] shortAxis = contactPointUnitVector(ellipsoid, contactPoints);

		// find an orthogonal axis
		final double[] xAxis = {1, 0, 0};
		double[] middleAxis = crossProduct(shortAxis, xAxis);
		middleAxis = norm(middleAxis);

		// find a mutually orthogonal axis by forming the cross product
		double[] longAxis = crossProduct(shortAxis, middleAxis);
		longAxis = norm(longAxis);

		// construct a rotation matrix
		double[][] rotation = {shortAxis, middleAxis, longAxis};
		rotation = QuickEllipsoid.transpose(rotation);

		// rotate ellipsoid to point this way...
		ellipsoid.setRotation(rotation);
	}

	private void shrinkToFit(final QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints, final byte[][] pixels,
			final int w, final int h, final int d) {

		// get the contact points
		findContactPoints(ellipsoid, contactPoints, pixels, w, h, d);

		// get the unit vectors to the contact points
		final double[][] unitVectors = findContactUnitVectors(ellipsoid, contactPoints);

		// contract until no contact
		int safety = 0;
		while (!contactPoints.isEmpty() && safety < algorithmParameters.maxIterations) {
			ellipsoid.contract(0.01);
			findContactPointsForGivenDirections(ellipsoid, contactPoints, unitVectors, pixels, w, h, d);
			safety++;
		}

		ellipsoid.contract(0.05);
	}

	/**
	 * Rotate the ellipsoid theta radians around the unit vector formed by the sum
	 * of torques effected by unit normals acting on the surface of the ellipsoid
	 * (if the ellipsoid surface is in contact with the foreground boundary)
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param w
	 *            the image dimension in x
	 * @param h
	 *            the image dimension in y
	 * @param d
	 *            the image dimension in z
	 */
	void turn(QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints, final byte[][] pixels, final int w,
			final int h, final int d) {
		findContactPoints(ellipsoid, contactPoints, pixels, w, h, d);
		if (!contactPoints.isEmpty()) {
			final double[] torque = calculateTorque(ellipsoid, contactPoints);
			rotateAboutAxis(ellipsoid, norm(torque));
		}
	}

	private boolean isContained(final QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints,
			final byte[][] pixels, final int w, final int h, final int d) {
		final double[][] points = ellipsoid
				.getSurfacePoints(ellipsoid.getAxisAlignRandomlyDistributedSurfacePoints(algorithmParameters.nVectors));
		for (final double[] p : points) {
			final int x = (int) Math.floor(p[0]);
			final int y = (int) Math.floor(p[1]);
			final int z = (int) Math.floor(p[2]);
			if (isOutOfBounds(x, y, z, w, h, d))
				continue;
			if (pixels[z][y * w + x] != -1) {
				contactPoints.clear();
				contactPoints.addAll(Arrays.asList(points));
				return false;
			}
		}
		return true;
	}

	/**
	 * Check whether this ellipsoid is sensible
	 *
	 * @param ellipsoid
	 *            ellipsoids
	 * @param w
	 *            image dimension in x
	 * @param h
	 *            image dimension in y
	 * @param d
	 *            image dimension in z
	 * @return true if half or more of the surface points are outside the image
	 *         stack, if the smallest radius is less than half a pixel length, or if
	 *         the volume of the ellipsoid exceeds that of the image stack
	 */
	boolean isInvalid(final QuickEllipsoid ellipsoid, final int w, final int h, final int d) {
		double[][] surfacePoints = ellipsoid.getAxisAlignRandomlyDistributedSurfacePoints(algorithmParameters.nVectors);

		final double minRadius = ellipsoid.getSortedRadii()[0];
		if (minRadius < 0.5) {
			return true;
		}

		int outOfBoundsCount = 0;
		final int half = algorithmParameters.nVectors / 2;

		for (final double[] p : surfacePoints) {
			if (isOutOfBounds((int) (p[0]), (int) (p[1]), (int) (p[2]), w, h, d))
				outOfBoundsCount++;
			if (outOfBoundsCount > half)
				return true;
		}

		final double volume = ellipsoid.getVolume();
		return volume > stackVolume;

	}

	void findContactPoints(final QuickEllipsoid ellipsoid, final ArrayList<double[]> contactPoints,
			final byte[][] pixels, final int w, final int h, final int d) {
		findContactPointsForGivenDirections(ellipsoid, contactPoints,
				ellipsoid.getAxisAlignRandomlyDistributedSurfacePoints(algorithmParameters.nVectors), pixels, w, h, d);
	}

	void bump(final QuickEllipsoid ellipsoid, final Collection<double[]> contactPoints, final double[] seedPoint) {
		final double displacement = algorithmParameters.vectorIncrement / 2;

		final double[] c = ellipsoid.getCentre();
		final double[] vector = contactPointUnitVector(ellipsoid, contactPoints);
		final double x = c[0] + vector[0] * displacement;
		final double y = c[1] + vector[1] * displacement;
		final double z = c[2] + vector[2] * displacement;

		Vector3d distance = new Vector3d(seedPoint[0], seedPoint[1], seedPoint[2]);
		distance.sub(new Vector3d(x, y, z));
		if (distance.length() < algorithmParameters.maxDrift)
			ellipsoid.setCentroid(x, y, z);
	}


}
