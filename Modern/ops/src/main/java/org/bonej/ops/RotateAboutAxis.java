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

package org.bonej.ops;

import net.imagej.ops.Op;
import net.imagej.ops.special.hybrid.AbstractBinaryHybridCFI1;

import org.apache.commons.math3.random.RandomVectorGenerator;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Quat4d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Rotates a tuple i.e. a vector or a point around an axis.
 * <p>
 * The rotations are done by quaternions so there's no risk of gimbal lock as
 * with rotation matrices.
 * </p>
 *
 * @see AxisAngle4d
 * @author Richard Domander
 */
@Plugin(type = Op.class)
public class RotateAboutAxis extends
	AbstractBinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d>
{

	/**
	 * Generates four normally distributed values between [0, 1] that describe a
	 * unit quaternion. These can be used to create isotropically distributed
	 * rotations.
	 */
	private static final RandomVectorGenerator qGenerator =
		new UnitSphereRandomVectorGenerator(4);

	/**
	 * Rotates the input tuple around the axis-angle and stores the result in the
	 * output.
	 *
	 * @param input input tuple.
	 * @param axisAngle the rotation axis and angle.
	 * @param output the input tuple rotated.
	 */
	@Override
	public void compute(final Tuple3d input, final AxisAngle4d axisAngle,
		final Tuple3d output)
	{
		mutate1(output, axisAngle);
	}

	/**
	 * Initialises the output vector by calling the copy constructor with the
	 * input.
	 */
	@Override
	public Vector3d createOutput(final Tuple3d input,
		final AxisAngle4d axisAngle)
	{
		return new Vector3d(input);
	}

	/**
	 * Rotates the tuple and stores the result in the given object.
	 *
	 * @param v the input and output tuple.
	 * @param axisAngle the rotation axis and angle.
	 */
	@Override
	public void mutate1(final Tuple3d v, final AxisAngle4d axisAngle) {
		final Quat4d q = new Quat4d();
		// the setter normalizes the quaternion
		q.set(axisAngle);
		rotate(v, q);
	}

	/**
	 * Creates {@link AxisAngle4d} from random isotropically distributed
	 * quaternions.
	 *
	 * @return an axis-angle which can be used as a parameter for the op.
	 */
	public static AxisAngle4d randomAxisAngle() {
		final Quat4d q = new Quat4d(qGenerator.nextVector());
		final AxisAngle4d axisAngle4d = new AxisAngle4d();
		axisAngle4d.set(q);
		return axisAngle4d;
	}

	private static void rotate(final Tuple3d v, final Quat4d q) {
		final Quat4d p = new Quat4d();
		p.set(v.getX(), v.getY(), v.getZ(), 0);
		final Quat4d r = new Quat4d(q);
		r.mul(p);
		r.mulInverse(q);
		v.set(r.x, r.y, r.z);
	}
}
