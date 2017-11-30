
package org.bonej.ops;

import static java.util.stream.DoubleStream.generate;

import java.util.Collection;
import java.util.Iterator;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.GMatrix;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Tuple3d;

/**
 * An op that fits a quadratic surface (quadric) into a set of points.
 * <p>
 * The op first solves the quadric that best fits the point cloud by minimising
 * the distance by least squares fitting. It's found by solving a polynomial -
 * the general equation of a quadric. The algorithm is sensitive to outlier
 * points.
 * </p>
 * <p>
 * The op is based on the the implementations of Yury Petrov &amp; "KalebKE".
 * </p>
 * <p>
 * The input collection must have at least {@link #QUADRIC_TERMS} points.
 * </p>
 *
 * @author Richard Domander
 * @param <T> type of the three-element tuple.
 */
@Plugin(type = Op.class)
public class SolveQuadricEq<T extends Tuple3d> extends
	AbstractUnaryFunctionOp<Collection<T>, Matrix4d> implements Contingent
{

	/**
	 * Number of terms in the quadric equation that needs to be solved.
	 * <p>
	 * Due the number of terms, we also need at least 9 points in the input to
	 * solve the equation.
	 * </p>
	 */
	public static final int QUADRIC_TERMS = 9;

	@Override
	public Matrix4d calculate(final Collection<T> points) {
		final double[] vector = solveVector(points);
		return toQuadricMatrix(vector);
	}

	@Override
	public boolean conforms() {
		return in().size() >= QUADRIC_TERMS;
	}

	/**
	 * Creates a design matrix used for least squares fitting from a collection of
	 * points.
	 *
	 * @see #solveVector(Collection)
	 * @param points points in a 3D space.
	 * @return a [points.size()][9] matrix of real values.
	 */
	private GMatrix createDesignMatrix(final Collection<T> points) {
		final GMatrix designMatrix = new GMatrix(points.size(), 9);
		final Iterator<T> iterator = points.iterator();
		for (int i = 0; i < points.size(); i++) {
			final T p = iterator.next();
			final double[] rowData = { p.x * p.x, p.y * p.y, p.z * p.z, 2 * p.x * p.y,
				2 * p.x * p.z, 2 * p.y * p.z, 2 * p.x, 2 * p.y, 2 * p.z };
			designMatrix.setRow(i, rowData);
		}
		return designMatrix;
	}

	/**
	 * Calculates the pseudoinverse matrix A+ of the given matrix A.
	 * <p>
	 * A common use of the pseudoinverse is to compute a 'best fit' (least
	 * squares) solution to a system of linear equations that lacks a unique
	 * solution. Which is exactly what happens in
	 * {@link #solveVector(Collection)}. Because the design matrix in the method
	 * might lack an exact solution, it can cause a
	 * {@link org.scijava.vecmath.SingularMatrixException} if were trying to get
	 * its inverse.
	 * </p>
	 *
	 * @param a the design matrix which when solved (best fit) provides an
	 *          equation for a quadric surface.
	 */
	// Using apache.commons.math since scijava.vecmath doesn't yet offer
	// pseudoinverse
	private void pseudoInverse(final GMatrix a) {
		final int rows = a.getNumRow();
		final double[][] data = new double[rows][];
		for (int i = 0; i < rows; i++) {
			final double[] rowData = new double[a.getNumCol()];
			a.getRow(i, rowData);
			data[i] = rowData;
		}
		final RealMatrix pseudoInverse = new SingularValueDecomposition(
			new Array2DRowRealMatrix(data)).getSolver().getInverse();
		for (int i = 0; i < rows; i++) {
			a.setRow(i, pseudoInverse.getRow(i));
		}
	}

	/**
	 * Solves the equation for the quadratic surface that best fits the given
	 * points.
	 * <p>
	 * The vector solved is the polynomial Ax<sup>2</sup> + By<sup>2</sup> +
	 * Cz<sup>2</sup> + 2Dxy + 2Exz + 2Fyz + 2Gx + 2Hy + 2Iz, i.e. the general
	 * equation of a quadric. The fitting is done with least squares.
	 * </p>
	 *
	 * @param points A collection of points in a 3D space.
	 * @return the solution vector of the surface.
	 */
	private double[] solveVector(final Collection<T> points) {
		final int n = points.size();
		// Find (dT * d)^-1
		final GMatrix d = createDesignMatrix(points);
		final GMatrix dT = new GMatrix(d);
		dT.transpose();
		final GMatrix dTDInv = new GMatrix(dT.getNumRow(), d.getNumCol());
		dTDInv.mul(dT, d);
		pseudoInverse(dTDInv);
		// Multiply dT * O, where O = [1, 1, ... 1] (n x 1) matrix
		final GMatrix o = new GMatrix(n, 1, generate(() -> 1.0).limit(n).toArray());
		final GMatrix dTO = new GMatrix(dT.getNumRow(), o.getNumCol());
		dTO.mul(dT, o);
		// Find solution A = (dT * d)^-1 * (dT * O)
		final GMatrix a = new GMatrix(dTDInv.getNumRow(), dTO.getNumCol());
		a.mul(dTDInv, dTO);
		// Return solution vector
		final double[] vector = new double[a.getNumRow()];
		a.getColumn(0, vector);
		return vector;
	}

	/**
	 * Creates a matrix out of a quadric surface solution vector in homogeneous
	 * coordinates (w = 1).
	 *
	 * @see #solveVector(Collection)
	 * @return a matrix representing the polynomial solution vector in an
	 *         algebraic form.
	 */
	private Matrix4d toQuadricMatrix(final double[] solution) {
		// I'm not a clever man, so I'm using named variables and row setters to
		// better follow the matrix assignment.
		final double a = solution[0];
		final double b = solution[1];
		final double c = solution[2];
		final double d = solution[3];
		final double e = solution[4];
		final double f = solution[5];
		final double g = solution[6];
		final double h = solution[7];
		final double i = solution[8];
		final Matrix4d matrix = new Matrix4d();
		matrix.setRow(0, new double[] { a, d, e, g });
		matrix.setRow(1, new double[] { d, b, f, h });
		matrix.setRow(2, new double[] { e, f, c, i });
		matrix.setRow(3, new double[] { g, h, i, -1 });
		return matrix;
	}
}
