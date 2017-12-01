
package org.bonej.ops.mil;

import static org.bonej.ops.mil.MILPOCSampling.createGrid;
import static org.bonej.ops.mil.MILPOCSampling.createSections;

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
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.bonej.ops.BoxIntersect;
import org.bonej.ops.RotateAboutAxis;
import org.bonej.ops.mil.MILPOCSampling.Section;
import org.scijava.vecmath.AxisAngle4d;
import org.scijava.vecmath.Point3d;
import org.scijava.vecmath.Tuple3d;
import org.scijava.vecmath.Vector3d;

/**
 * Another proof-of-concept for the {@link MILGrid} op that continues where
 * {@link MILPOCSampling} left. This one shows that an image is sampled randomly
 * from each orthogonal direction (x,y,z). Result is displayed as an image with
 * three channels: red for X, green for Y and blue for Z.
 *
 * @author Richard Domander
 */
public class MILPOCDirectionComponents {

	public static void main(final String... args) {
		// SETUP
		final ImageJ imageJ = new ImageJ();
		final Random random = new Random();
		final int width = 100;
		final int height = 100;
		final int depth = 100;
		final int rotations = 100;
		final int bins = 100;
		final Img<FloatType> img = ArrayImgs.floats(width, height, depth, 3);
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

		// DISPLAY RESULTS
		// Swap channel- and z-axes swapped so that image displays OK.
		final IntervalView<FloatType> displayView = Views.permute(img, 3, 2);
		final Img<FloatType> displayImg = ImgView.wrap(displayView, img.factory());
		final ImgPlus<FloatType> image = new ImgPlus<>(displayImg,
			"Sampling directions", new DefaultLinearAxis(Axes.X),
			new DefaultLinearAxis(Axes.Y), new DefaultLinearAxis(Axes.CHANNEL),
			new DefaultLinearAxis(Axes.Z));
		imageJ.launch(args);
		imageJ.ui().show(image);
	}

	private static void addOrientation(final RandomAccess<FloatType> access,
		final Vector3d position, final Vector3d direction)
	{
		access.setPosition((long) position.x, 0);
		access.setPosition((long) position.y, 1);
		access.setPosition((long) position.z, 2);
		access.setPosition(0, 3);
		access.get().setReal(access.get().getRealDouble() + direction.x);
		access.setPosition(1, 3);
		access.get().setReal(access.get().getRealDouble() + direction.y);
		access.setPosition(2, 3);
		access.get().setReal(access.get().getRealDouble() + direction.z);
	}

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
		final double[] coordinates = new double[4];
		final RandomAccess<FloatType> access = img.randomAccess();
		for (double t = section.tMin + offsetScale; t <= section.tMax; t +=
			increment)
		{
			samplePoint.get(coordinates);
			addOrientation(access, samplePoint, section.direction);
			samplePoint.add(sampleGap);
		}
	}
}
