
package org.bonej.ops.mil;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;

import org.bonej.ops.BoxIntersect;
import org.bonej.ops.RotateAboutAxis;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * A small proof-of-concept that shows that a grid of directional vectors
 * samples an image uniformly when rotated multiple times. A similar type of
 * sampling is the first step in the {@link MILGrid} op.
 *
 * @author Richard Domander
 */
public class MILPOCSampling {

	public static void main(String... args) {
		// SETUP
		final ImageJ imageJ = new ImageJ();
		final Random random = new Random();
		final int width = 100;
		final int height = 100;
		final int depth = 100;
		final int rotations = 100;
		final int bins = 100;
		final Img<FloatType> img = ArrayImgs.floats(width, height, depth);
		@SuppressWarnings("unchecked")
		final BinaryFunctionOp<ValuePair<Point3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>> intersectOp =
			(BinaryFunctionOp) Functions.binary(imageJ.op(), BoxIntersect.class,
				Optional.class, ValuePair.class, img);
		final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp = Hybrids
			.binaryCFI1(imageJ.op(), RotateAboutAxis.class, Tuple3d.class,
				new Vector3d(), new AxisAngle4d());

		// EXECUTE
		for (int i = 0; i < rotations; i++) {
			final AxisAngle4d rotation = RotateAboutAxis.randomAxisAngle();
			final LineGrid grid = createGrid(img, rotateOp, rotation, random);
			final Stream<Section> sections = createSections(img, grid, bins,
				intersectOp);
			sections.forEach(s -> sample(img, s, 1.0, random));
		}

		// DISPLAY RESULT
		final ImgPlus<FloatType> image = new ImgPlus<>(img, "Sampling count",
			new DefaultLinearAxis(Axes.X), new DefaultLinearAxis(Axes.Y),
			new DefaultLinearAxis(Axes.Z));
		imageJ.launch(args);
		imageJ.ui().show(image);
	}

	static LineGrid createGrid(final Img<?> img,
		final BinaryHybridCFI1<Tuple3d, AxisAngle4d, Tuple3d> rotateOp,
		final AxisAngle4d rotation, final Random random)
	{
		final LineGrid grid = new LineGrid(img);
		grid.setRandomGenerator(random);
		grid.setRotation(rotateOp, rotation);
		if (Math.random() <= 0.5) {
			grid.randomReflection();
		}
		return grid;
	}

	static Stream<Section> createSections(final Interval interval,
		final LineGrid grid, final long bins,
		final BinaryFunctionOp<ValuePair<Point3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>> intersectOp)
	{
		return grid.lines(bins).map(l -> section(l, interval, intersectOp)).filter(
			Objects::nonNull);
	}

	/**
	 * Adds one the value of each image element accessed during sampling.
	 * <p>
	 * Proceeds along the given section in the image from Section#tMin to
	 * Section#tMax. After each increment the section coordinates are floored to
	 * the voxel grid.
	 * </p>
	 * 
	 * @param img a 3D image.
	 * @param section a section of a line inside the image.
	 * @param increment the scalar step between the sample positions. For example,
	 *          an increment of 1.0 adds a vector of length 1.0 to the position.
	 *          More formally, this is the value added to <em>t</em> in the line.
	 * @param random a random generator.
	 */
	private static void sample(final Img<FloatType> img, final Section section,
		final double increment, final Random random)
	{
		final Vector3d startOffset = new Vector3d(section.direction);
		// Add a random offset so that sampling doesn't always start from the same
		// plane
		final double offsetScale = random.nextDouble() * increment;
		startOffset.scale(offsetScale);
		final Vector3d samplePoint = new Vector3d(section.direction);
		samplePoint.scale(section.tMin);
		samplePoint.add(section.origin);
		samplePoint.add(startOffset);
		final Vector3d sampleGap = new Vector3d(section.direction);
		sampleGap.scale(increment);
		final double[] coordinates = new double[3];
		final RandomAccess<FloatType> access = img.randomAccess();
		for (double t = section.tMin + offsetScale; t <= section.tMax; t +=
			increment)
		{
			samplePoint.get(coordinates);
			// Assuming that coordinates are always non-negative
			final long[] voxelCoordinates = Arrays.stream(coordinates).mapToLong(
				c -> (long) c).toArray();
			access.setPosition(voxelCoordinates);
			access.get().setReal(access.get().getRealDouble() + 1.0);
			samplePoint.add(sampleGap);
		}
	}

	/**
	 * Maps the line to section in the interval.
	 *
	 * @param line an (origin, direction pair)
	 * @param interval a 3D interval.
	 * @param intersectOp an op that finds intersection points between the line
	 *          and the interval.
	 * @return a {@link Section} in the interval, null if line doesn't intersect
	 *         it.
	 */
	static Section section(final ValuePair<Point3d, Vector3d> line,
		final Interval interval,
		final BinaryFunctionOp<ValuePair<Point3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>> intersectOp)
	{
		final Optional<ValuePair<DoubleType, DoubleType>> result = intersectOp
			.calculate(line, interval);
		if (!result.isPresent()) {
			return null;
		}
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		final double tMin = scalars.getA().get();
		final double tMax = scalars.getB().get();
		return new Section(line.a, line.b, tMin, tMax);
	}

	/**
	 * Stores the origin <b>a</b> and direction <b>v</b> of a line, and the
	 * scalars for <b>a</b> + <em>t<sub>1</sub></em><b>v</b> and <b>a</b> +
	 * <em>t<sub>2</sub></em><b>v</b> where the line intersects an interval.
	 */
	static class Section {

		public final double tMin;
		public final double tMax;
		public final Point3d origin;
		public final Vector3d direction;

		public Section(final Point3d origin, final Vector3d direction,
			final double tMin, final double tMax)
		{
			this.tMin = tMin;
			this.tMax = tMax;
			this.origin = origin;
			this.direction = direction;
		}
	}
}
