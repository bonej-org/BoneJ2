
package org.bonej.ops.ellipsoid;

import java.util.Arrays;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.plugin.Plugin;

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
	AbstractUnaryFunctionOp<Matrix4dc, Ellipsoid> implements Contingent
{

	@Override
	public Ellipsoid calculate(final Matrix4dc quadricSolution) {
		final Vector3dc center = findCenter(quadricSolution);
		final Matrix4dc translated = translateToCenter(quadricSolution, center);
		final EigenDecomposition decomposition = solveEigenDecomposition(
			translated);
		final double[] radii = Arrays.stream(decomposition.getRealEigenvalues())
			.map(ev -> Math.sqrt(1.0 / ev)).toArray();
		final Ellipsoid ellipsoid = new Ellipsoid(radii[0], radii[1], radii[2]);
		ellipsoid.setCentroid(new org.scijava.vecmath.Vector3d(center.x(), center.y(), center.z()));
		final org.scijava.vecmath.Matrix3d orientation = toOrientationMatrix(decomposition);
		ellipsoid.setOrientation(orientation);
		return ellipsoid;
	}

	/**
	 * Checks if the matrix has the equation of an ellipsoid.
	 *
	 * @return true if an ellipsoid can be created from the input quadric.
	 */
	@Override
	public boolean conforms() {
		return isEllipsoid(in());
	}

	/**
	 * Checks if the quadric matrix describes a real ellipsoid.
	 *
	 * @param quadric a quadric in the algebraic form.
	 * @return true if an ellipsoid can be created, false if not.
	 */
	public static boolean isEllipsoid(final Matrix4dc quadric) {
		final double det2d = quadric.m00() * quadric.m11() - quadric.m10() * quadric.m01();
		return quadric.m00() > 0 && det2d > 0 && quadric.determinant3x3() > 0;
	}

	/**
	 * Finds the center point of a quadric surface.
	 *
	 * @param quadric the general equation of a quadric in algebraic matrix form.
	 * @return the 3D center point in a vector.
	 */
	private static Vector3dc findCenter(final Matrix4dc quadric) {
		// @formatter:off
		final Matrix3dc sub = new Matrix3d(
				quadric.m00(), quadric.m01(), quadric.m02(),
				quadric.m10(), quadric.m11(), quadric.m12(),
				quadric.m20(), quadric.m21(), quadric.m22()
		).scale(-1.0).invert();
		// @formatter:on
		final Vector3d translation = new Vector3d(quadric.m03(), quadric.m13(),
			quadric.m23());
		return sub.transform(translation);
	}

	private static EigenDecomposition solveEigenDecomposition(
		final Matrix4dc quadric)
	{
		// TODO Figure out how to solve eigen decomposition with ojAlgo!
		// @formatter:off
		final RealMatrix input = new Array2DRowRealMatrix(new double[][]{
				{ quadric.m00(), quadric.m01(), quadric.m02() },
				{ quadric.m10(), quadric.m11(), quadric.m12() },
				{ quadric.m20(), quadric.m21(), quadric.m22() }
		}).scalarMultiply(-1.0 / quadric.m33());
		// @formatter:on
		return new EigenDecomposition(input);
	}

	private static org.scijava.vecmath.Matrix3d toOrientationMatrix(
		final EigenDecomposition decomposition)
	{
		final RealVector e1 = decomposition.getEigenvector(0);
		final RealVector e2 = decomposition.getEigenvector(1);
		final RealVector e3 = decomposition.getEigenvector(2);
		final Vector3d x = new Vector3d(e1.getEntry(0), e1.getEntry(1), e1.getEntry(
			2));
		final Vector3d y = new Vector3d(e2.getEntry(0), e2.getEntry(1), e2.getEntry(
			2));
		final Vector3d z = new Vector3d(e3.getEntry(0), e3.getEntry(1), e3.getEntry(
			2));
		final org.scijava.vecmath.Matrix3d orientation = new org.scijava.vecmath.Matrix3d();
		orientation.setColumn(0, new org.scijava.vecmath.Vector3d(x.x, x.y, x.z));
		orientation.setColumn(1, new org.scijava.vecmath.Vector3d(y.x, y.y, y.z));
		orientation.setColumn(2, new org.scijava.vecmath.Vector3d(z.x, z.y, z.z));
		return orientation;
	}

	/**
	 * Translates the quadratic surface to the center point.
	 *
	 * @param quadric the general equation of a quadric in algebraic matrix form.
	 * @param center the center point of the surface.
	 */
	private static Matrix4d translateToCenter(final Matrix4dc quadric,
		final Vector3dc center)
	{
		//@formatter:off
		final Matrix4d t = new Matrix4d(
				1, 0, 0, center.x(),
				0, 1, 0, center.y(),
				0, 0, 1, center.z(),
				0, 0, 0, 1
		);
		//@formatter:on
		final Matrix4d tT = t.transpose(new Matrix4d());
		t.mul(quadric);
		t.mul(tT);
		return t;
	}
}
