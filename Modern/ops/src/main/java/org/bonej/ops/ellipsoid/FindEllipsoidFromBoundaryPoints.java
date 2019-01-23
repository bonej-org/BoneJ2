/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Dimensions;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.util.ValuePair;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.bonej.utilities.VectorUtil;
import org.joml.*;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.lang.Math;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;


/**
 * Tries to do one step of an {@link Ellipsoid} decomposition, starting from a
 * point and a list of possible other points to collide with
 * <p>
 * The ellipsoid decomposition is drawn from the paper of Bischoff and Kobbelt,
 * 2002 (https://www.graphics.rwth-aachen.de/media/papers/ellipsoids1.pdf) The
 * variable naming widely follows Bischoff and Kobbelt's nomenclature, it is
 * recommended to read this code in conjunction with the paper. The
 * Ellipsoid-Plane intersection is done slightly differently, using the
 * {@link EllipsoidPlaneIntersection}. This op starts growing an ellipsoid from
 * the starting point <b>p</b>, and keeps growing in various directions until it
 * hits three more points (<b>q</b>, <b>r</b>, <b>s</b>). If less than four
 * distinct points are found, the op returns an empty ellipsoid. More details on
 * the growing process are in the original publication.
 * </p>
 *
 * @author Alessandro Felder
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class FindEllipsoidFromBoundaryPoints extends AbstractUnaryFunctionOp<Vector3dc, Stream<Ellipsoid>> {

    private static final QuadricToEllipsoid quadricToEllipsoid = new QuadricToEllipsoid();
    private static final EllipsoidPlaneIntersection intersectionOp = new EllipsoidPlaneIntersection();

    @Parameter
	private Img<BitType> inputImage;

    @Parameter(persist = false, required = false)
	private int nSphere = 20;

	private final int nFilterSampling = 200;

    @Override
    public Stream<Ellipsoid>calculate(final Vector3dc internalSeedPoint) {

		final List<Vector3dc> filterSamplingDirections = getGeneralizedSpiralSetOnSphere(nFilterSampling).collect(toList());

		final List<ArrayList<ValuePair<Vector3dc, Vector3dc>>> verticesWithNormals = getSurfacePoints(internalSeedPoint).map(set -> new ArrayList<>(set)).collect(toList());

		return verticesWithNormals.stream().map(c -> getEllipsoidStream(internalSeedPoint, c))
				.filter(Optional::isPresent).map(Optional::get).filter(e -> !tooLarge(e))
				.filter(e -> isEllipsoidNonBackground(e,filterSamplingDirections));
	}

	private boolean tooLarge(Ellipsoid ellipsoid) {
    	return ellipsoid.getC()>Math.sqrt(inputImage.dimension(0)*inputImage.dimension(0)+inputImage.dimension(1)*inputImage.dimension(1)+inputImage.dimension(2)*inputImage.dimension(2));
	}

	private Optional<Ellipsoid> getEllipsoidStream(Vector3dc internalSeedPoint, ArrayList<ValuePair<Vector3dc, Vector3dc>> verticesWithNormals) {
		verticesWithNormals.sort(comparingDouble(p -> p.getA().distance(internalSeedPoint)));
		ValuePair<Vector3dc, Vector3dc> p = verticesWithNormals.get(0);
		final List<Vector3dc> vertices = verticesWithNormals.stream().map(ValuePair::getA).skip(1).collect(toList());
		// TODO Move try-catch to where it's actually needed
		try {
			final ValuePair<ValuePair<Vector3dc,Vector3dc>, Double> qAndRadius = calculateQ(vertices, p);
			if (!isValidSphere(qAndRadius)) return Optional.empty();
			vertices.remove(qAndRadius.getA().a);

			final Vector3d np = new Vector3d(p.b);
			np.mul(qAndRadius.getB());
			p  = new ValuePair<>(p.a, np);

			final Matrix4d q1 = getQ1(new ValuePair<>(p.a, np), qAndRadius.getB());
			final Matrix4d q2 = getQ2(p, qAndRadius.getA());

			final ValuePair<Vector3d, Double> rAndAlpha = calculateSurfacePointAndGreekCoefficient(q1, q2, vertices);
			if (rAndAlpha == null) return Optional.empty();
			vertices.remove(rAndAlpha.getA());

			final Matrix4d q1PlusAlphaQ2 = new Matrix4d(q2);
			final Matrix4d fullScalingMatrix = new Matrix4d();
			fullScalingMatrix.scaling(rAndAlpha.getB());
			fullScalingMatrix.m33(rAndAlpha.getB());
			q1PlusAlphaQ2.mul(fullScalingMatrix);
			q1PlusAlphaQ2.add(q1);

			final Optional<Ellipsoid> ellipsoid = quadricToEllipsoid.calculate(q1PlusAlphaQ2);
			if (!ellipsoid.isPresent()) return ellipsoid;

			final Matrix4d q3 = getQ3(p.a, qAndRadius.getA().a, rAndAlpha.getA(), ellipsoid.get());

			final ValuePair<Vector3d, Double> sAndBeta = calculateSurfacePointAndGreekCoefficient(q1PlusAlphaQ2, q3, vertices);
			if (sAndBeta == null) return ellipsoid;

			final Matrix4d q1PlusAlphaQ2plusBetaQ3 = new Matrix4d(q3);
			fullScalingMatrix.scaling(sAndBeta.getB());
			fullScalingMatrix.m33(sAndBeta.getB());
			q1PlusAlphaQ2plusBetaQ3.mul(fullScalingMatrix);
			q1PlusAlphaQ2plusBetaQ3.add(q1PlusAlphaQ2);

			return quadricToEllipsoid.calculate(q1PlusAlphaQ2plusBetaQ3);
		}
		catch (final MaxCountExceededException e)
		{
			return Optional.empty();
		}
	}

	private Stream<Set<ValuePair<Vector3dc, Vector3dc>>>
	getSurfacePoints(final Vector3dc centre)
	{
		final Stream<Vector3dc> sphereSamplingDirections = getGeneralizedSpiralSetOnSphere(nSphere);
		final List<Vector3dc> contactPoints = sphereSamplingDirections.map(d -> {
			final Vector3dc direction = new Vector3d(d);
			return findFirstNonForegroundPointAlongRay(direction, centre);
		}).filter(Objects::nonNull).collect(toList());
		return getAllUniqueCombinationsOfFourPoints(contactPoints, centre);
	}

	//non-background includes foreground + space outside image boundary
	private boolean isEllipsoidNonBackground(final Ellipsoid e,
											 final Collection<Vector3dc> sphereSamplingDirections)
	{
		final Stream.Builder<Vector3dc> builder = Stream.builder();
		final Matrix3d orientation = e.getOrientation().get3x3(new Matrix3d());
		for (int i = 0; i < 3; i++) {
			final Vector3dc v = orientation.getColumn(i, new Vector3d());
			builder.add(v);
			builder.add(v.negate(new Vector3d()));
		}
		final Stream<Vector3dc> directions = Stream.concat(sphereSamplingDirections
				.stream(), builder.build());
		final Matrix3d reconstruction = reconstructMatrixOfSlightlySmallerEllipsoid(e,Math.sqrt(3.0));
		return directions.noneMatch(dir -> isEllipsoidIntersectionBackground(
				reconstruction, e.getCentroid(), dir)) && !moreThanHalfEllipsoidSurfaceOutside(e,sphereSamplingDirections);
	}

	public Matrix3d reconstructMatrixOfSlightlySmallerEllipsoid(Ellipsoid e, final double reduction) {
		final double[] scales = DoubleStream.of(e.getA(), e.getB(), e.getC()).map(
				s -> s - reduction).map(s -> s * s).map(s -> 1.0 / s).toArray();
		final Matrix3dc Q = e.getOrientation().get3x3(new Matrix3d());
		final Matrix3dc lambda = new Matrix3d(scales[0], 0, 0, 0, scales[1], 0, 0,
				0, scales[2]);
		final Matrix3dc QT = Q.transpose(new Matrix3d());
		final Matrix3dc LambdaQ = lambda.mul(Q, new Matrix3d());
		return QT.mul(LambdaQ, new Matrix3d());
	}

    /**
	 * Calculates the quadric denoted by Q<sub>1</sub> in the original paper
	 *
	 * @param sphere centre and normal of a sphere touching p and q
	 * @param radius radius of the sphere touching p and q
	 * @return Q<sub>1</sub>
	 */
	static Matrix4d getQ1(final ValuePair<Vector3dc, Vector3dc> sphere,
		final double radius)
	{
		// Do all with Vector4d, when Vector4d.add methods work
		final Vector3d centre = new Vector3d(sphere.b);
		centre.add(sphere.a.x(), sphere.a.y(), sphere.a.z());
		centre.negate();
		final Vector4dc v = new Vector4d(centre, 0);
		final double w = centre.lengthSquared() - radius * radius;
		//@formatter:off
		return new Matrix4d(
				1, 0, 0, v.x(),
				0, 1, 0, v.y(),
				0, 0, 1, v.z(),
				v.x(), v.y(), v.z(), w
		);
		//@formatter:on
	}

    /**
     * Calculates the quadric denoted by Q<sub>2</sub> in the original paper
     *
     * @param pnp p and normal at p
     * @param qnq q and normal at q
     * @return the 4x4 matrix Q<sub>2</sub>
     */
	static Matrix4d getQ2(final ValuePair<Vector3dc, Vector3dc> pnp,
		final ValuePair<Vector3dc, Vector3dc> qnq)
	{
		final Matrix3dc q2Rotation = findRotation(pnp, qnq);
		final double nqDotqHalf = qnq.b.dot(qnq.a) / 2.0;
		final double npDotpHalf = pnp.b.dot(pnp.a) / 2.0;

		final Vector3d q2Translation = new Vector3d(pnp.b.x() * nqDotqHalf + qnq.b
			.x() * npDotpHalf, pnp.b.y() * nqDotqHalf + qnq.b.y() * npDotpHalf, pnp.b
				.z() * nqDotqHalf + qnq.b.z() * npDotpHalf);

		final Matrix4d quadric2 = new Matrix4d(q2Rotation);
		quadric2.setColumn(3, new Vector4d(q2Translation, 1));
		quadric2.setRow(3, new Vector4d(q2Translation, 1));
		quadric2.m33(-pnp.b.dot(pnp.a) * qnq.b.dot(qnq.a));
		return quadric2;
	}

	private boolean isEllipsoidIntersectionBackground(final Matrix3dc a,
													  final Vector3dc centroid, final Vector3dc dir)
	{
		final Vector3dc aTimesDir = a.transform(dir, new Vector3d());
		final double surfaceIntersectionParameter = Math.sqrt(1.0 / dir.dot(
				aTimesDir));
		final Vector3d intersectionPoint = new Vector3d(dir);
		intersectionPoint.mul(surfaceIntersectionParameter);
		intersectionPoint.add(centroid);
		final long[] pixel = VectorUtil.toPixelGrid(intersectionPoint);
		if (outOfBounds(inputImage, pixel)) {
			return false;
		}
		final net.imglib2.RandomAccess<BitType> inputRA = inputImage.randomAccess();
		inputRA.setPosition(pixel);
		return !inputRA.get().get();
	}

	/**
	 * @param e Ellipsoid that may have more than half its volume outside the image boundary
	 * @param samplingDirections directions in which to perform the inside/outside test
	 * @return true if more than half the ellipsoid sampling points are outside the input image boundary
	 */
	public boolean moreThanHalfEllipsoidSurfaceOutside(final Ellipsoid e, final Collection<Vector3dc> samplingDirections)
	{
		final Matrix3d a = reconstructMatrixOfSlightlySmallerEllipsoid(e,0.0);
		final Vector3dc centroid = e.getCentroid();
		final long surfacePointsOutside = samplingDirections.stream().filter(dir -> {
			final Vector3dc aTimesDir = a.transform(dir, new Vector3d());
			final double surfaceIntersectionParameter = Math.sqrt(1.0 / dir.dot(
					aTimesDir));
			final Vector3d intersectionPoint = new Vector3d(dir);
			intersectionPoint.mul(surfaceIntersectionParameter);
			intersectionPoint.add(centroid);
			final long[] pixel = VectorUtil.toPixelGrid(intersectionPoint);
			if (outOfBounds(inputImage, pixel)) {
				return true;
			} else {
				return false;
			}
		}).count();

		return 0.5<=((double) surfacePointsOutside/((double) samplingDirections.size()));
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
	 * </p>
	 */
	// TODO Is there an implementation for this in Apache Commons?
	// I don't think so: could be own op!
	private static Stream<Vector3dc> getGeneralizedSpiralSetOnSphere(final int n) {
		final Stream.Builder<Vector3dc> spiralSet = Stream.builder();
		final List<Double> phi = new ArrayList<>();
		phi.add(0.0);
		for (int k = 1; k < n - 1; k++) {
			final double h = -1.0 + 2.0 * k / (n - 1);
			phi.add(getPhiByRecursion(n, phi.get(k - 1), h));
		}
		phi.add(0.0);

		for (int k = 0; k < n; k++) {
			final double h = -1.0 + 2.0 * k / (n - 1);
			final double theta = Math.acos(h);
			spiralSet.add(new Vector3d(Math.sin(theta) * Math.cos(phi.get(k)), Math
					.sin(theta) * Math.sin(phi.get(k)), Math.cos(theta)));

		}
		return spiralSet.build();
	}

	private static double getPhiByRecursion(final double n, final double phiKMinus1,
											final double hk) {
		final double phiK = phiKMinus1 + 3.6 / Math.sqrt(n) * 1.0 / Math.sqrt(1 - hk *
				hk);
		// modulo 2pi calculation works for positive numbers only, which is not a
		// problem in this case.
		return phiK - Math.floor(phiK / (2 * Math.PI)) * 2 * Math.PI;
	}

	// non-foreground = background **or** outside stack
	private Vector3d findFirstNonForegroundPointAlongRay(final Vector3dc rayIncrement,
														 final Vector3dc start) {
		final net.imglib2.RandomAccess<BitType> randomAccess = inputImage.randomAccess();

		final Vector3d currentRealPosition = new Vector3d(start);
		long[] currentPixelPosition = VectorUtil.toPixelGrid(start);
		randomAccess.setPosition(currentPixelPosition);

		while (randomAccess.get().getRealDouble() > 0) {
			currentRealPosition.add(rayIncrement);
			currentPixelPosition = VectorUtil.toPixelGrid(currentRealPosition);
			if (outOfBounds(inputImage, currentPixelPosition)) break;
			randomAccess.setPosition(currentPixelPosition);
		}
		return currentRealPosition;
	}

	// TODO make into a utility method, and see where else needed in BoneJ
	private static boolean outOfBounds(final Dimensions dimensions, final long[] currentPixelPosition) {
		for (int i = 0; i < currentPixelPosition.length; i++) {
			final long position = currentPixelPosition[i];
			if (position < 0 || position >= dimensions.dimension(i)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Finds the rotation of the quadric denoted by Q<sub>2</sub> in the original paper
	 *
	 * @param pnp p and normal at p
	 * @param qnq q and normal at q
	 * @return the 3x3 rotation matrix of Q<sub>2</sub>
	 */
	private static Matrix3dc findRotation(
		final ValuePair<Vector3dc, Vector3dc> pnp,
		final ValuePair<Vector3dc, Vector3dc> qnq)
	{
		final Matrix3d NRotation = new Matrix3d();
		NRotation.m00 = -pnp.b.x() * qnq.b.x();
		NRotation.m01 = -pnp.b.x() * qnq.b.y();
		NRotation.m02 = -pnp.b.x() * qnq.b.z();
		NRotation.m10 = -pnp.b.y() * qnq.b.x();
		NRotation.m11 = -pnp.b.y() * qnq.b.y();
		NRotation.m12 = -pnp.b.y() * qnq.b.z();
		NRotation.m20 = -pnp.b.z() * qnq.b.x();
		NRotation.m21 = -pnp.b.z() * qnq.b.y();
		NRotation.m22 = -pnp.b.z() * qnq.b.z();
		final Matrix3d NRotationTransposed = new Matrix3d(NRotation);
		NRotationTransposed.transpose();
		final Matrix3d rotation = new Matrix3d(NRotation);
		rotation.add(NRotationTransposed);
		rotation.scale(0.5);
		return rotation;
	}


	/**
	 * Calculates the elliptic cylinder denoted by Q_3 in the original paper
	 *
	 * @param p a non-collinear point defining a plane
	 * @param q a non-collinear point defining a plane
	 * @param r a non-collinear point defining a plane
	 * @param ellipsoid ellipsoid through p,q, and r
	 * @return a 4x4 matrix representation of the elliptic cylinder defined by the
	 *         intersection ellipse of pqr and qBar
	 */
	private static Matrix4d getQ3(final Vector3dc p, final Vector3dc q,
		final Vector3dc r, final Ellipsoid ellipsoid)
	{
		final List<Vector3d> ellipse = intersectPlane(p, q, r, ellipsoid);
		final Vector3dc a1 = ellipse.get(1);
		final Vector3dc a2 = ellipse.get(2);
		// axis and translation contributions calculated with sympy - see
		// documentation
		final Matrix3d A1 = getAxisContribution(a1);
		final Matrix3d A2 = getAxisContribution(a2);
		A2.add(A1);
		final Vector3dc a0 = ellipse.get(0);
		final Vector3d translation = getTranslationContribution(a0, a1);
		final Vector3d translation2 = getTranslationContribution(a0, a2);
		translation2.add(translation);
		final Matrix4d quadric = new Matrix4d();
		quadric.set3x3(A2);
		quadric.setRow(3, new Vector4d(translation2, 0));
		quadric.setColumn(3, new Vector4d(translation2, 0));
		quadric.m33(getConstantContribution(a0, a1) + getConstantContribution(a0,
			a2) - 1.0);
		return quadric;
	}

	private static List<Vector3d> intersectPlane(final Vector3dc p,
		final Vector3dc q, final Vector3dc r, final Ellipsoid ellipsoid)
	{
		final Vector3d interiorPoint = new Vector3d(p);
		interiorPoint.negate();
		interiorPoint.add(q);
		interiorPoint.mul(0.5);
		interiorPoint.add(p);
		final Vector3d planeNormal = new Vector3d(q);
		planeNormal.negate();
		planeNormal.add(p);
		final Vector3d v = new Vector3d(r);
		v.negate();
		v.add(p);
		planeNormal.cross(v);
		return intersectionOp.calculate(ellipsoid, new ValuePair<>(interiorPoint,
			planeNormal));
	}

	private static Vector3d getTranslationContribution(final Vector3dc centre, final Vector3dc axis) {
        final Vector3d unitAxis = new Vector3d(axis);
        unitAxis.normalize();
        final Vector3d translation = new Vector3d(unitAxis);
        translation.negate();
        translation.mul(centre.dot(unitAxis));
        return translation.div(axis.lengthSquared());
    }

    private static double getConstantContribution(final Vector3dc centre, final Vector3dc axis) {
        final Vector3d unitAxis = new Vector3d(axis);
        unitAxis.normalize();

		final double constantComponent = centre.x() * centre.x() * unitAxis.x *
			unitAxis.x + centre.y() * centre.y() * unitAxis.y * unitAxis.y + centre
				.z() * centre.z() * unitAxis.z * unitAxis.z + 2.0 * centre.x() * centre
					.y() * unitAxis.x * unitAxis.y + 2.0 * centre.x() * centre.z() *
						unitAxis.x * unitAxis.z + 2.0 * centre.y() * centre.z() *
							unitAxis.y * unitAxis.z;
        return constantComponent/(axis.lengthSquared());
    }

    private static Matrix3d getAxisContribution(final Vector3dc axis) {
        final Vector3d normalizedAxis = new Vector3d(axis);
        normalizedAxis.normalize();
        final Matrix3d axisContribution = new Matrix3d();
        axisContribution.m00(normalizedAxis.x()*normalizedAxis.x());
        axisContribution.m11(normalizedAxis.y()*normalizedAxis.y());
        axisContribution.m22(normalizedAxis.z()*normalizedAxis.z());
        axisContribution.m01(normalizedAxis.x()*normalizedAxis.y());
        axisContribution.m10(normalizedAxis.x()*normalizedAxis.y());
        axisContribution.m02(normalizedAxis.x()*normalizedAxis.z());
        axisContribution.m20(normalizedAxis.x()*normalizedAxis.z());
        axisContribution.m12(normalizedAxis.y()*normalizedAxis.z());
        axisContribution.m21(normalizedAxis.y()*normalizedAxis.z());
        return axisContribution.scale(1.0 / axis.lengthSquared());
    }


	private static ValuePair<ValuePair<Vector3dc, Vector3dc>, Double> calculateQ(
		final Collection<Vector3dc> candidateQs,
		final ValuePair<Vector3dc, Vector3dc> p)
	{
		return candidateQs.stream().map(q -> calculatePossibleQAndR(q, p)).filter(
			qnr -> qnr.getB() > 0).min(comparingDouble(ValuePair::getB)).orElse(null);
	}

	private static ValuePair<ValuePair<Vector3dc,Vector3dc>,Double> calculatePossibleQAndR(final Vector3dc x, final ValuePair<Vector3dc,Vector3dc> p)
    {
        final Vector3d xMinusP = new Vector3d(x);
        xMinusP.sub(p.a);

        final double scalarProduct = xMinusP.dot(p.b);

        if(scalarProduct<=0.0) return new ValuePair<>(null,-1.0);

		final double radius = xMinusP.lengthSquared()/(2*scalarProduct);
        final Vector3d centre = new Vector3d(p.b);
        centre.mul(radius);
        centre.add(p.a);
        centre.sub(x);
        return new ValuePair<>(new ValuePair<>(x,centre),radius);
    }

	private static ValuePair<Vector3d, Double>
		calculateSurfacePointAndGreekCoefficient(final Matrix4dc Q1,
			final Matrix4dc Q2, final Collection<Vector3dc> vertices)
	{
		return vertices.stream().map(
			v -> calculateCandidateSurfacePointAndGreekCoefficient(Q1, Q2, v)).filter(
				a -> !a.getB().isNaN()).min(comparingDouble(ValuePair::getB)).orElse(
					null);
	}

    private static double calculateXtQX(final Matrix4dc Q2, final Vector3dc x)
    {
        final Vector4d x4d = new Vector4d(x,1);
        final Vector4d Q2X = new Vector4d(x4d);
        Q2.transform(Q2X);
        return x4d.dot(Q2X);
    }

    private static ValuePair<Vector3d, Double> calculateCandidateSurfacePointAndGreekCoefficient(final Matrix4dc Q1, final Matrix4dc Q2, final Vector3dc x)
    {
        final double xtQ2X = calculateXtQX(Q2, x);
        if(xtQ2X>=0) return new ValuePair<>(new Vector3d(), Double.NaN);

        final double xtQ1X = calculateXtQX(Q1, x);
        return new ValuePair<>(new Vector3d(x),-xtQ1X/xtQ2X);
    }

	//TODO FIX THIS!!
	private boolean isValidSphere(
			final ValuePair<ValuePair<Vector3dc, Vector3dc>, Double> qAndRadius)
	{
		return qAndRadius != null;
	}

	private static Stream<Set<ValuePair<Vector3dc, Vector3dc>>>
	getAllUniqueCombinationsOfFourPoints(final List<Vector3dc> points,
										 final Vector3dc centre)
	{
		final Iterator<int[]> iterator = CombinatoricsUtils.combinationsIterator(
				points.size(), 4);
		final Stream.Builder<Set<ValuePair<Vector3dc, Vector3dc>>> pointCombinations = Stream.builder();
		iterator.forEachRemaining(el -> {
			final Stream<Vector3dc> pointCombo = IntStream.range(0, 4).mapToObj(
					i -> points.get(el[i]));
			final Set<ValuePair<Vector3dc, Vector3dc>> dirPoints = pointCombo.map(
					p -> {
						final Vector3dc inwardDir = centre.sub(p, new Vector3d());
						final Vector3dc unitDir = inwardDir.normalize(new Vector3d());
						return new ValuePair<>(p, unitDir);
					}).collect(Collectors.toSet());
			if (dirPoints.size() == 4) {
				pointCombinations.add(dirPoints);
			}
		});
		return pointCombinations.build();
	}
}