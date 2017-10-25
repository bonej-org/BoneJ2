
package org.bonej.ops;

import net.imagej.ops.AbstractOp;
import net.imagej.ops.Op;
import net.imglib2.type.numeric.real.DoubleType;

import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Quat4d;
import org.scijava.vecmath.Vector3d;

/**
 * Rotates a vector around an axis by the given radians.
 * <p>
 * The rotations are done by quaternions so there's no risk of gimbal lock as
 * with rotation matrices.
 * </p>
 * 
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class RotateAboutAxis extends AbstractOp {

	@Parameter(type = ItemIO.BOTH)
	private Vector3d vector;

	/**
	 * An axis around which the vector is rotated. The axis should be a unit
	 * vector, if not it'll be normalized. If left null, it's generated randomly.
	 */
	@Parameter(type = ItemIO.BOTH, required = false)
	private Vector3d axis;

	/** The rotation angle in radians. If left null, it's generated randomly. */
	// A ItemIO.BOTH needs to be a mutable object, i.e. a Double won't do.
	@Parameter(type = ItemIO.BOTH, required = false)
	private DoubleType angle;

	/**
	 * Generates four normally distributed values that describe a unit quaternion.
	 * These can be used to create uniformally distributed rotations.
	 */
	private static final UnitSphereRandomVectorGenerator qGenerator =
		new UnitSphereRandomVectorGenerator(4);

	@Override
	public void run() {
		final Quat4d q = populateParameters();
		rotate(vector, q);
	}

	private static void rotate(final Vector3d v, final Quat4d q) {
		final Quat4d p = new Quat4d();
		p.set(v.getX(), v.getY(), v.getZ(), 0);
		final Quat4d r = new Quat4d(q);
		r.mul(p);
		r.mulInverse(q);
		v.set(r.x, r.y, r.z);
	}

	private Quat4d populateParameters() {
		final Quat4d q = new Quat4d();
		q.set(qGenerator.nextVector());
		final AxisAngle4d axisAngle = new AxisAngle4d();
		axisAngle.set(q);
		if (axis == null) {
			axis = new Vector3d(axisAngle.x, axisAngle.y, axisAngle.z);
		}
		else {
			axis.normalize();
		}
		if (angle == null) {
			angle = new DoubleType(axisAngle.angle);
		}
		axisAngle.set(axis.getX(), axis.getY(), axis.getZ(), angle.get());
		q.set(axisAngle);
		q.normalize();
		return q;
	}
}
