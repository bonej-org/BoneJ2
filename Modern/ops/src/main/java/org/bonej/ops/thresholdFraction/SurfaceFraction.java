
package org.bonej.ops.thresholdFraction;

import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.Ops;
import net.imagej.ops.geom.geom3d.mesh.DefaultMesh;
import net.imagej.ops.geom.geom3d.mesh.Mesh;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

import org.scijava.plugin.Plugin;

/**
 * An Op which calculates the volume of the thresholded elements, the total
 * volume of the image, and their ratio. It calculates the volumes by creating
 * surfaces from the elements with a marching cubes Op.
 *
 * @author Richard Domander TODO: Add surface smoothing / resampling in marching
 *         cubes when it becomes available
 */
@Plugin(type = Op.class, name = "surfaceFraction")
public class SurfaceFraction<T extends NativeType<T> & RealType<T>> extends
	AbstractBinaryFunctionOp<RandomAccessibleInterval<T>, Thresholds<T>, SurfaceFraction.Results>
	implements Contingent
{

	private BinaryFunctionOp<RandomAccessibleInterval, Thresholds, RandomAccessibleInterval> maskOp;
	private UnaryFunctionOp<RandomAccessibleInterval, Mesh> marchingCubesOp;
	private UnaryFunctionOp<Mesh, DoubleType> volumeOp;

	/** Match the ops that this op uses */
	@Override
	public void initialize() {
		maskOp = Functions.binary(ops(), SurfaceMask.class,
			RandomAccessibleInterval.class, in1(), in2());
		marchingCubesOp = Functions.unary(ops(), Ops.Geometric.MarchingCubes.class,
			Mesh.class, in1());
		volumeOp = Functions.unary(ops(), Ops.Geometric.Size.class,
			DoubleType.class, new DefaultMesh());
	}

	/** (Default) marching cubes only supports RAIs with three dimensions */
	@Override
	public boolean conforms() {
		return in1().numDimensions() == 3;
	}

	@Override
	public Results compute2(final RandomAccessibleInterval<T> interval,
		final Thresholds<T> thresholds)
	{
		final RandomAccessibleInterval thresholdMask = maskOp.compute2(interval,
			thresholds);
		final Mesh thresholdSurface = marchingCubesOp.compute1(thresholdMask);
		final double thresholdSurfaceVolume = volumeOp.compute1(thresholdSurface)
			.get();

		final Thresholds<T> totalThresholds = new Thresholds<>(interval,
			Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
		final RandomAccessibleInterval totalMask = maskOp.compute2(interval,
			totalThresholds);
		final Mesh totalSurface = marchingCubesOp.compute1(totalMask);
		final double totalSurfaceVolume = volumeOp.compute1(totalSurface).get();

		return new Results(thresholdSurface, totalSurface, thresholdSurfaceVolume,
			totalSurfaceVolume);
	}

	// region -- Helper classes --

	/** A helper class for passing outputs */
	public static final class Results {

		/** A surface mesh created from the elements within the thresholds */
		public final Mesh thresholdSurface;
		/** A surface mesh created from all the elements */
		public final Mesh totalSurface;
		public final double thresholdSurfaceVolume;
		public final double totalSurfaceVolume;
		/** Ratio of threshold / total surface volumes */
		public final double ratio;

		public Results(final Mesh thresholdSurface, final Mesh totalSurface,
			final double thresholdSurfaceVolume, final double totalSurfaceVolume)
		{
			this.thresholdSurface = thresholdSurface;
			this.totalSurface = totalSurface;
			this.thresholdSurfaceVolume = thresholdSurfaceVolume;
			this.totalSurfaceVolume = totalSurfaceVolume;
			this.ratio = thresholdSurfaceVolume / totalSurfaceVolume;
		}
	}
	// endregion
}
