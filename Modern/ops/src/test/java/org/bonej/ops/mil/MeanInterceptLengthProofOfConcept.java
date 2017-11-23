
package org.bonej.ops.mil;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.generate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.bonej.ops.BoxIntersect;
import org.bonej.ops.RotateAboutAxis;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Vector3d;

/**
 * A small proof-of-concept that shows that a grid of vectors samples an image
 * uniformly.
 *
 * @author Richard Domander
 */
public class MeanInterceptLengthProofOfConcept {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static BinaryFunctionOp<ValuePair<Point3d, Vector3d>, Interval, Optional<ValuePair<DoubleType, DoubleType>>> boxIntersect;
	private static Random random = new Random();

	public static void main(String... args) throws ExecutionException,
		InterruptedException
	{
		final int width = 100;
		final int height = 100;
		final int depth = 100;
		final Img<FloatType> img = ArrayImgs.floats(width, height, depth);
		boxIntersect = (BinaryFunctionOp) Functions.binary(IMAGE_J.op(),
			BoxIntersect.class, Optional.class, ValuePair.class, img);

        for (int i = 0; i < 100; i++) {
            final AxisAngle4d rotation = RotateAboutAxis.randomAxisAngle();
            createSections(img, rotation, 100).forEach(s -> sample(img, s,
                    1.0));
        }


        // sampleParallel(img, 300, 1.0, 100);

		printStatistics(img);
		
		final ImgPlus<FloatType> image = new ImgPlus<>(img, "Sampling count",
			new DefaultLinearAxis(Axes.X), new DefaultLinearAxis(Axes.Y),
			new DefaultLinearAxis(Axes.Z));
		IMAGE_J.launch(args);
		IMAGE_J.ui().show(image);
	}

	private static <I extends Interval> Stream<Section> createSections(
		final I interval, final AxisAngle4d rotation, final long bins)
	{
		final LineGrid grid = new LineGrid(interval);
		grid.setRandomGenerator(random);
		grid.setRotation(rotation, IMAGE_J.op());
		if (Math.random() > 0.5) {
		    grid.flipPlanes();
        }
		return grid.lines(bins).map(l -> section(l, interval)).filter(
			Objects::nonNull);
	}

	private static void printStatistics(final Img<FloatType> stack) {
		final IterableInterval<FloatType> iterable = Views.flatIterable(stack);
		final SummaryStatistics statistics = new SummaryStatistics();
		iterable.cursor().forEachRemaining(i -> statistics.addValue(i.get()));
		System.out.println(statistics.toString());
	}

	private static void sample(final Img<FloatType> img, final Section section,
		final double increment)
	{
		final Vector3d startOffset = new Vector3d(section.direction);
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
			final long[] voxelCoordinates = Arrays.stream(coordinates).mapToLong(
				c -> (long) c).toArray();
			access.setPosition(voxelCoordinates);
			access.get().setReal(access.get().getRealDouble() + 1.0);
			samplePoint.add(sampleGap);
		}
	}

	private static void sampleParallel(final Img<FloatType> img, final long lines,
		final double increment, final int rotations) throws ExecutionException,
		InterruptedException
	{
		final Runnable task = () -> {
			final AxisAngle4d rotation = RotateAboutAxis.randomAxisAngle();
			createSections(img, rotation, lines).forEach(s -> sample(img, s,
				increment));
		};
		final ExecutorService executors = Executors.newFixedThreadPool(5);
		final List<Future> futures = generate(() -> task).map(executors::submit)
			.limit(rotations).collect(toList());
		for (final Future future : futures) {
			future.get();
		}
		executors.shutdown();
	}

	private static <I extends Interval> Section section(
		final ValuePair<Point3d, Vector3d> line, final I interval)
	{
		final Optional<ValuePair<DoubleType, DoubleType>> result = boxIntersect
			.calculate(line, interval);
		if (!result.isPresent()) {
			return null;
		}
		final ValuePair<DoubleType, DoubleType> scalars = result.get();
		final double tMin = scalars.getA().get();
		final double tMax = scalars.getB().get();
		return new Section(line.a, line.b, tMin, tMax);
	}

	private static class Section {

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
