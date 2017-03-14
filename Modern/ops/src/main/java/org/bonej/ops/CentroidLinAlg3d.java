
package org.bonej.ops;

import java.util.Collection;

import net.imagej.ops.Ops;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;

import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Calculates the centroid (geometrical centre) of the given tuples
 *
 * @author Richard Domander
 */
@Plugin(type = Ops.Geometric.Centroid.class, name = "centroidLinAlg3d")
public class CentroidLinAlg3d<T extends Tuple3d> extends
	AbstractUnaryFunctionOp<Collection<T>, Vector3d>
{

	/**
	 * Calculates the centroid of the given collection in 3D space
	 *
	 * @return The centroid point, (Double.NaN, Double.NaN, Double.NaN) if
	 *         collection is empty
	 */
	@Override
	public Vector3d calculate(final Collection<T> vectors) {
		final Vector3d sum = new Vector3d(0.0, 0.0, 0.0);
		vectors.forEach(sum::add);
		sum.scale(1.0 / vectors.size());
		return sum;
	}
}
