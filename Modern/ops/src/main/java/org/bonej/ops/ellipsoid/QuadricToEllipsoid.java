
package org.bonej.ops.ellipsoid;

import java.util.Arrays;
import java.util.Optional;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.GMatrix;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.SingularMatrixException;
import org.scijava.vecmath.Vector3d;

/**
 * Tries to create an {@link Ellipsoid} from a general equation of a quadratic
 * surface i.e. quadric.
 * <p>
 * The input equation must be in a matrix. If the quadric's polynomial in
 * homogeneous coordinates (w = 1) is Ax<sup>2</sup> + By<sup>2</sup> +
 * Cz<sup>2</sup> + 2Dxy + 2Exz + 2Fyz + 2Gx + 2Hy + 2Iz, then the matrix must
 * be:<br>
 * </p>
 * 
 * <pre>
 * [a, d, e, g]
 * [d, b, f, h]
 * [e, f, c, i]
 * [g, h, i, -1]
 * </pre>
 * 
 * @author Richard Domander
 * @see org.bonej.ops.SolveQuadricEq
 */
@Plugin(type = Op.class)
public class QuadricToEllipsoid extends
	AbstractUnaryFunctionOp<Matrix4d, Optional<Ellipsoid>>
{

	/**
	 * Minimum value for an eigenvalue to be considered non-zero.
	 *
	 * @see #isEllipsoid(double[])
	 */
	private static final double EIGENVALUE_TOLERANCE = 1e-10;

	@Override
	public Optional<Ellipsoid> calculate(final Matrix4d quadricSolution) {
		Vector3d center;
		try {
			center = findCenter(quadricSolution);
		}
		catch (SingularMatrixException sme) {
			return Optional.empty();
		}
		final Matrix4d translated = translateToCenter(quadricSolution, center);
		final EigenDecomposition decomposition = solveEigenDecomposition(
			translated);
		if (!isEllipsoid(decomposition.getRealEigenvalues())) {
			return Optional.empty();
		}
		final double[] radii = Arrays.stream(decomposition.getRealEigenvalues())
			.map(ev -> Math.sqrt(1.0 / ev)).toArray();
		final Ellipsoid ellipsoid = new Ellipsoid(radii[0], radii[1], radii[2]);
		ellipsoid.setCentroid(center);
		final Matrix3d orientation = toOrientationMatrix(decomposition);
		ellipsoid.setOrientation(orientation);
		return Optional.of(ellipsoid);
	}

	/**
	 * Finds the center point of a quadric surface.
	 *
	 * @param quadric the general equation of a quadric in algebraic matrix form.
	 * @return the 3D center point in a vector.
	 */
	private Vector3d findCenter(final Matrix4d quadric) {
		// @formatter:off
		final GMatrix sub = new GMatrix(3, 3, new double[] {
				quadric.m00, quadric.m01, quadric.m02,
				quadric.m10, quadric.m11, quadric.m12,
				quadric.m20, quadric.m21, quadric.m22
		});
		// @formatter:on
		sub.negate();
		sub.invert();
		final GMatrix translation = new GMatrix(3, 1, new double[] { quadric.m03,
			quadric.m13, quadric.m23 });
		final GMatrix center = new GMatrix(3, 1);
		center.mul(sub, translation);
		final double[] centerCoords = new double[3];
		center.getColumn(0, centerCoords);
		return new Vector3d(centerCoords);
	}

	/**
	 * Determines if the quadric is an ellipsoid from its eigenvalues.
	 * <p>
	 * The signs of the eigenvalues determine the type of a quadric. If they are
	 * all positive, it is an ellipsoid; two positive and one negative gives a
	 * hyperboloid of one sheet; one positive and two negative gives a hyperboloid
	 * of two sheets. If one or more eigenvalues vanish, we have a degenerate case
	 * such as a paraboloid,or a cylinder or even a pair of planes.
	 * </p>
	 *
	 * @param eigenvalues eigenvalues of a quadric surface
	 * @return true if the surface is an ellipsoid, false otherwise.
	 */
	private boolean isEllipsoid(final double[] eigenvalues) {
		return Arrays.stream(eigenvalues).allMatch(x -> x > EIGENVALUE_TOLERANCE);
	}

	private boolean isLeftHandedBasis(final Vector3d x, final Vector3d y,
		final Vector3d z)
	{
		final Vector3d v = new Vector3d();
		v.cross(x, y);
		return v.dot(z) < 0;
	}

	// Using apache.commons.math3 since scijava.vecmath doesn't yet have eigen
	// decomposition, and I can't figure out how to use the tensor eigen stuff
	// from net.imglib2.algorithm.linalg.eigen
	private static EigenDecomposition solveEigenDecomposition(
		final Matrix4d quadric)
	{
		// @formatter:off
		final RealMatrix input = new Array2DRowRealMatrix(new double[][]{
				{quadric.m00, quadric.m01, quadric.m02},
				{quadric.m10, quadric.m11, quadric.m12},
				{quadric.m20, quadric.m21, quadric.m22},
		}).scalarMultiply(-1.0 / quadric.m33);
		// @formatter:on
		return new EigenDecomposition(input);
	}

	private Matrix3d toOrientationMatrix(final EigenDecomposition decomposition) {
		final RealVector e1 = decomposition.getEigenvector(0);
		final RealVector e2 = decomposition.getEigenvector(1);
		final RealVector e3 = decomposition.getEigenvector(2);
		final Vector3d x = new Vector3d(e1.getEntry(0), e1.getEntry(1), e1.getEntry(
			2));
		final Vector3d y = new Vector3d(e2.getEntry(0), e2.getEntry(1), e2.getEntry(
			2));
		final Vector3d z = new Vector3d(e3.getEntry(0), e3.getEntry(1), e3.getEntry(
			2));
		if (isLeftHandedBasis(x, y, z)) {
			// Make the basis right handed
			final Vector3d tmp = new Vector3d(y);
			y.set(z);
			z.set(tmp);
		}
		final Matrix3d orientation = new Matrix3d();
		orientation.setColumn(0, x);
		orientation.setColumn(1, y);
		orientation.setColumn(2, z);
		return orientation;
	}

	/**
	 * Translates the quadratic surface to the center point.
	 *
	 * @param quadric the general equation of a quadric in algebraic matrix form.
	 * @param center the center point of the surface.
	 */
	private static Matrix4d translateToCenter(final Matrix4d quadric,
		final Vector3d center)
	{
		//@formatter:off
		final Matrix4d t = new Matrix4d(
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, 0,
				center.x, center.y, center.z, 1
		);
		//@formatter:on
		final Matrix4d tT = new Matrix4d(t);
		tT.transpose();
		final Matrix4d translated = new Matrix4d();
		translated.mul(t, quadric);
		translated.mul(tT);
		return translated;
	}
}
