
package org.bonej.ops.ellipsoid;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import net.imagej.ops.Contingent;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.BooleanType;

import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.bonej.ops.RotateAboutAxis;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Vector3d;

public class FindEllipsoidOp<B extends BooleanType<B>> extends
	AbstractBinaryFunctionOp<RandomAccessibleInterval<B>, Vector3d, Ellipsoid>
	implements Contingent
{

	private final static UnitSphereRandomVectorGenerator rvg =
		new UnitSphereRandomVectorGenerator(4);
	private BinaryHybridCFI1<Vector3d, AxisAngle4d, Vector3d> rotateVectorOp;

	@Override
	public Ellipsoid calculate(final RandomAccessibleInterval<B> binaryImage,
		final Vector3d seedPoint)
	{
		rotateVectorOp = Hybrids.binaryCFI1(ops(), RotateAboutAxis.class,
			Vector3d.class, Vector3d.class, AxisAngle4d.class);

		double maxSamplingRadius = 10;
		double samplingWidth = 1.0;

		int nSphere = estimateNSpiralPointsRequired(maxSamplingRadius,
			samplingWidth);
		int nPlane = (int) Math.ceil(2 * Math.PI * maxSamplingRadius);

		List<Vector3d> sphereSamplingDirections = getGeneralizedSpiralSetOnSphere(
			nSphere);
		Vector3d closestContactPointOverall = findClosestContactPoint(seedPoint,
			sphereSamplingDirections);
		List<Ellipsoid> ellipsoids = sphereSamplingDirections.stream().map(
			dir -> getEllipsoidFromInitialAxis(seedPoint, closestContactPointOverall,
				nPlane, dir)).collect(toList());

		ellipsoids.sort(Comparator.comparingDouble(Ellipsoid::getVolume));

		return ellipsoids.get(ellipsoids.size() - 1);
	}

	public Ellipsoid getEllipsoidFromInitialAxis(Vector3d seedPoint,
		final Vector3d contactPoint, int nPlane, Vector3d initialAxis)
	{
		List<Vector3d> initialDirectionToTry = new ArrayList<>();
		initialDirectionToTry.add(initialAxis);

		Vector3d minusSeedPoint = new Vector3d(seedPoint);
		minusSeedPoint.negate();

		Vector3d firstAxis = findClosestContactPoint(seedPoint,
			initialDirectionToTry);
		firstAxis.add(minusSeedPoint);
		double a = Math.max(firstAxis.length() - 1, 1.0);
		firstAxis.normalize();

		List<Vector3d> orthogonalSearchDirections = getSearchDirectionsInPlane(
			initialAxis, nPlane);

		Vector3d secondAxis = findClosestContactPoint(seedPoint,
			orthogonalSearchDirections);
		secondAxis.add(minusSeedPoint);
		double b = Math.max(secondAxis.length() - 1, 1.0);
		secondAxis.normalize();

		List<Vector3d> thirdAxisSearchDirections = new ArrayList<>();

		Vector3d thirdAxisSearchDirection = new Vector3d();
		thirdAxisSearchDirection.cross(secondAxis, firstAxis);
		thirdAxisSearchDirection.normalize();
		Vector3d negativeThirdAxisSearchDirection = new Vector3d(
			thirdAxisSearchDirection);
		negativeThirdAxisSearchDirection.scale(-1.0);

		thirdAxisSearchDirections.add(thirdAxisSearchDirection);
		thirdAxisSearchDirections.add(negativeThirdAxisSearchDirection);

		Vector3d thirdAxis = findClosestContactPoint(seedPoint,
			thirdAxisSearchDirections);
		thirdAxis.add(minusSeedPoint);
		double c = Math.max(thirdAxis.length() - 1, 1.0);
		thirdAxis.normalize();

		ensureRightHandedBasis(firstAxis, secondAxis, thirdAxis);

		Ellipsoid ellipsoid = new Ellipsoid(a, b, c);
		ellipsoid.setCentroid(seedPoint);
		ellipsoid.setOrientation(new Matrix3d(firstAxis.getX(), secondAxis.getX(),
			thirdAxis.getX(), firstAxis.getY(), secondAxis.getY(), thirdAxis.getY(),
			firstAxis.getZ(), secondAxis.getZ(), thirdAxis.getZ()));

		return ellipsoid;
	}

	static Vector3d getFlooredVector3d(Vector3d vector) {
		return new Vector3d(Math.floor(vector.getX()), Math.floor(vector.getY()),
			Math.floor(vector.getZ()));
	}

	double getMinimumRadiusFromContactPointAndAxis(Vector3d contactPoint,
		Vector3d axis)
	{
		double kappa = 0.0;

		// split contact point into ellipsoid axis aligned coordinates
		Vector3d contactPointInEllipsoidAlignedCoordinates = null;
		return kappa;
	}

	private List<Vector3d> getSearchDirectionsInPlane(Vector3d planeNormalAxis,
		int n)
	{
		AxisAngle4d axisAngle = new AxisAngle4d(planeNormalAxis, 2 * Math.PI / n);

		double[] searchDirection = rvg.nextVector();
		Vector3d inPlaneVector = new Vector3d(searchDirection[0],
			searchDirection[1], searchDirection[2]);
		inPlaneVector.cross(planeNormalAxis, inPlaneVector);
		inPlaneVector.normalize();

		List<Vector3d> inPlaneSearchDirections = new ArrayList<>();

		for (int k = 0; k < n; k++) {
			inPlaneSearchDirections.add(inPlaneVector);
			inPlaneVector = rotateVectorOp.calculate(inPlaneVector, axisAngle);
		}
		return inPlaneSearchDirections;
	}

	private Vector3d findClosestContactPoint(Vector3d seedPoint,
		List<Vector3d> samplingDirections)
	{
		List<Vector3d> contactPoints = samplingDirections.stream().map(d -> {
			final Vector3d n = new Vector3d(d);
			n.normalize();
			return findFirstPointInBGAlongRay(n, new Vector3d(seedPoint));
		}).collect(toList());

		contactPoints.sort((o1, o2) -> {
			Vector3d dist1 = new Vector3d();
			Vector3d dist2 = new Vector3d();
			dist1.sub(o1, seedPoint);
			dist2.sub(o2, seedPoint);
			return Double.compare(dist1.length(), dist2.length());
		});

		Vector3d closestContact = contactPoints.get(0);
		return closestContact;
	}

	/**
	 * Method to numerically approximate equidistantly spaced points on the
	 * surface of a sphere
	 * <p>
	 * The implementation follows the description of the theoretical work by
	 * Rakhmanov et al., 1994 in Saff and Kuijlaars, 1997
	 * (<a href="doi:10.1007/BF03024331">dx.doi.org/10.1007/BF03024331</a>), but k
	 * is shifted by one to the left for more convenient indexing.
	 * 
	 * @param n : number of points required (has to be > 2)
	 *          </p>
	 */
	static List<Vector3d> getGeneralizedSpiralSetOnSphere(int n) {
		List<Vector3d> spiralSet = new ArrayList<>();

		List<Double> phi = new ArrayList<>();
		phi.add(0.0);
		for (int k = 1; k < n - 1; k++) {
			double h = -1.0 + 2.0 * ((double) k) / (n - 1);
			phi.add(getPhiByRecursion(n, phi.get(k - 1), h));
		}
		phi.add(0.0);

		for (int k = 0; k < n; k++) {
			double h = -1.0 + 2.0 * ((double) k) / (n - 1);
			double theta = Math.acos(h);
			spiralSet.add(new Vector3d(Math.sin(theta) * Math.cos(phi.get(k)), Math
				.sin(theta) * Math.sin(phi.get(k)), Math.cos(theta)));

		}

		return spiralSet;
	}

	private static double getPhiByRecursion(double n, double phiKMinus1,
		double hk)
	{
		double phiK = phiKMinus1 + 3.6 / Math.sqrt(n) * 1.0 / Math.sqrt(1 - hk *
			hk);
		// modulo 2pi calculation works for positive numbers only, which is not a
		// problem in this case.
		return phiK - Math.floor(phiK / (2 * Math.PI)) * 2 * Math.PI;
	}

	private static int estimateNSpiralPointsRequired(double searchRadius,
		double pixelWidth)
	{
		return (int) Math.ceil(Math.pow(searchRadius * 3.809 / pixelWidth, 2));
	}

	Vector3d findFirstPointInBGAlongRay(final Vector3d rayIncrement,
		final Vector3d start)
	{
		RandomAccess<B> randomAccess = in1().randomAccess();

		Vector3d currentRealPosition = start;
		long[] currentPixelPosition = vectorToPixelGrid(start);
		randomAccess.setPosition(currentPixelPosition);

		while (randomAccess.get().get()) {
			currentRealPosition.add(rayIncrement);
			currentPixelPosition = vectorToPixelGrid(currentRealPosition);
			if (!isInBounds(currentPixelPosition)) break;
			randomAccess.setPosition(currentPixelPosition);
		}
		return currentRealPosition;
	}

	private boolean isInBounds(long[] currentPixelPosition) {
		long width = in1().max(0);
		long height = in1().max(1);
		long depth = in1().max(2);
		return !(currentPixelPosition[0] < 0 || currentPixelPosition[0] >= width ||
			currentPixelPosition[1] < 0 || currentPixelPosition[1] >= height ||
			currentPixelPosition[2] < 0 || currentPixelPosition[2] >= depth);
	}

	private long[] vectorToPixelGrid(Vector3d currentPosition) {
		return Stream.of(currentPosition.getX(), currentPosition.getY(),
			currentPosition.getZ()).mapToLong(x -> (long) x.doubleValue()).toArray();
	}

	// TODO make this a utility function
	// function
	private boolean isLeftHandedBasis(final Vector3d x, final Vector3d y,
		final Vector3d z)
	{
		final Vector3d v = new Vector3d();
		v.cross(x, y);
		return v.dot(z) < 0;
	}

	private void ensureRightHandedBasis(Vector3d x, Vector3d y, Vector3d z) {
		if (isLeftHandedBasis(x, y, z)) {
			// Make the basis right handed
			final Vector3d tmp = new Vector3d(y);
			y.set(z);
			z.set(tmp);
		}
	}

	@Override
	public boolean conforms() {
		return in1().numDimensions() == 3;
	}
}
