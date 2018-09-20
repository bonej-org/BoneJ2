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

package org.bonej.wrapperPlugins;

import static java.util.stream.Collectors.toList;
import static net.imglib2.roi.Regions.countTrue;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import net.imagej.ImgPlus;
import net.imagej.display.ColorTables;
import net.imagej.ops.OpService;
import net.imagej.units.UnitService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.apache.commons.math3.util.CombinatoricsUtils;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.FindEllipsoidFromBoundaryPoints;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;

/**
 * Ellipsoid Factor
 * <p>
 * Ellipsoid
 * </p>
 *
 * @author Alessandro Felder
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor 2")
public class EllipsoidFactorWrapper<R extends RealType<R> & NativeType<R>> extends ContextCommand {

    // Several ellipsoids may fall in same bin if this is too small a number!
    // This will be ignored!
    private static final long FLINN_PLOT_DIMENSION = 501;
    private final FindEllipsoidFromBoundaryPoints findLocalEllipsoidOp = new FindEllipsoidFromBoundaryPoints();

    @SuppressWarnings("unused")
    @Parameter(validater = "validateImage")
    private ImgPlus<R> inputImage;

    @Parameter(persist = false, required = false)
    private DoubleType sigma = new DoubleType(0);

    @Parameter(persist = false, required = false)
    private DoubleType thresholdForBeingARidgePoint = new DoubleType(0.95);

    @Parameter(persist = false, required = false)
    private IntType approximateNumberOfInternalSeeds = new IntType(250);

    @Parameter(label = "Ridge image", type = ItemIO.OUTPUT)
    private ImgPlus<UnsignedByteType> ridgePointsImage;

    @Parameter(label = "EF image", type = ItemIO.OUTPUT)
    private ImgPlus<FloatType> efImage;

    @Parameter(label = "ID image", type = ItemIO.OUTPUT)
    private ImgPlus<IntType> eIdImage;

    @Parameter(label = "Volume Image", type = ItemIO.OUTPUT)
    private ImgPlus<FloatType> vImage;

    @Parameter(label = "a/b Image", type = ItemIO.OUTPUT)
    private ImgPlus<FloatType> aToBAxisRatioImage;

    @Parameter(label = "b/c Image", type = ItemIO.OUTPUT)
    private ImgPlus<FloatType> bToCAxisRatioImage;

    @Parameter(label = "Unweighted Flinn Plot", type = ItemIO.OUTPUT)
    private ImgPlus<BitType> flinnPlotImage;

    @Parameter(label = "Flinn Peak Plot", type = ItemIO.OUTPUT)
    private ImgPlus<FloatType> flinnPeakPlotImage;

    @SuppressWarnings("unused")
    @Parameter
    private OpService opService;

    @SuppressWarnings("unused")
    @Parameter
    private StatusService statusService;

    @SuppressWarnings("unused")
    @Parameter
    private UIService uiService;

    @SuppressWarnings("unused")
    @Parameter
    private UnitService unitService;

    @SuppressWarnings("unused")
    @Parameter
    private LogService logService;

    private boolean calibrationWarned;

    private Random rng;

    @Override
    public void run() {
        statusService.showStatus("Ellipsoid Factor: initialising...");
        rng = new Random();
        final ImgPlus<BitType> bitImage = Common.toBitTypeImgPlus(opService, inputImage);
        final List<Vector3dc> internalSeedPoints = getRidgeSeedPoints(bitImage);

        statusService.showStatus("Ellipsoid Factor: finding ellipsoids...");
        final List<Ellipsoid> ellipsoids = findEllipsoids(internalSeedPoints);
        //TODO fail loudly if ellipsoids.size()==0
        ellipsoids.sort(Comparator.comparingDouble(e -> -e.getVolume()));

        statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
        final Img<IntType> ellipsoidIdentityImage = assignEllipsoidID(bitImage, ellipsoids);
        createOutputImages(ellipsoids, ellipsoidIdentityImage);

        final double numberOfForegroundVoxels = countTrue(bitImage);
        final double numberOfAssignedVoxels = countAssignedVoxels(ellipsoidIdentityImage);

        // TODO Should this be logService.debug?
        logService.info("found "+ellipsoids.size()+" ellipsoids");
        logService.info("assigned voxels = "+numberOfAssignedVoxels);
        logService.info("foreground voxels = "+numberOfForegroundVoxels);
        final double fillingPercentage = 100.0 * (numberOfAssignedVoxels / numberOfForegroundVoxels);
        logService.info("filling percentage = "+fillingPercentage+"%");
        logService.info("number of seed points = " + internalSeedPoints.size());
    }

	private void createAToBImage(final double[] aBRatios,
		final IterableInterval<IntType> ellipsoidIDs)
	{
		final Img<FloatType> aToBImage = createNaNCopy();
		mapValuesToImage(aBRatios, ellipsoidIDs, aToBImage);
		aToBAxisRatioImage = new ImgPlus<>(aToBImage, "a/b");
		aToBAxisRatioImage.setChannelMaximum(0, 1.0f);
		aToBAxisRatioImage.setChannelMinimum(0, 0.0f);
	}

	private void createBToCImage(final double[] bCRatios,
		final IterableInterval<IntType> ellipsoidIDs)
	{
		final Img<FloatType> bToCImage = createNaNCopy();
		mapValuesToImage(bCRatios, ellipsoidIDs, bToCImage);
		bToCAxisRatioImage = new ImgPlus<>(bToCImage, "b/c");
		bToCAxisRatioImage.setChannelMaximum(0, 1.0f);
		bToCAxisRatioImage.setChannelMinimum(0, 0.0f);
	}

	private void createEFImage(final Collection<Ellipsoid> ellipsoids,
		final IterableInterval<IntType> ellipsoidIDs)
	{
		final Img<FloatType> ellipsoidFactorImage = createNaNCopy();
		final double[] ellipsoidFactors = ellipsoids.parallelStream().mapToDouble(
			EllipsoidFactorWrapper::computeEllipsoidFactor).toArray();
		mapValuesToImage(ellipsoidFactors, ellipsoidIDs, ellipsoidFactorImage);
		efImage = new ImgPlus<>(ellipsoidFactorImage, "EF");
		efImage.setChannelMaximum(0, 1);
		efImage.setChannelMinimum(0, -1);
		efImage.initializeColorTables(1);
		efImage.setColorTable(ColorTables.FIRE, 0);
	}

	private void createFlinnPeakPlot(final double[] aBRatios,
		final double[] bCRatios, final Img<IntType> ellipsoidIDs)
	{
		Img<FloatType> flinnPeakPlot = ArrayImgs.floats(FLINN_PLOT_DIMENSION,
			FLINN_PLOT_DIMENSION);
		final RandomAccess<FloatType> flinnPeakPlotRA = flinnPeakPlot
			.randomAccess();
		final RandomAccess<IntType> idAccess = ellipsoidIDs.randomAccess();
		final Cursor<IntType> idCursor = ellipsoidIDs.localizingCursor();
		final long[] position = new long[3];
		while (idCursor.hasNext()) {
			idCursor.fwd();
			if (idCursor.get().getInteger() < 0) {
				continue;
			}
			idCursor.localize(position);
			idAccess.setPosition(position);
			final int localMaxEllipsoidID = idAccess.get().getInteger();
			final long x = Math.round(aBRatios[localMaxEllipsoidID] *
				(FLINN_PLOT_DIMENSION - 1));
			final long y = Math.round(bCRatios[localMaxEllipsoidID] *
				(FLINN_PLOT_DIMENSION - 1));
			flinnPeakPlotRA.setPosition(new long[] { x, FLINN_PLOT_DIMENSION - y -
				1 });
			final float currentValue = flinnPeakPlotRA.get().getRealFloat();
			flinnPeakPlotRA.get().set(currentValue + 1.0f);
		}
		if (sigma.getRealDouble() > 0.0) {
			flinnPeakPlot = (Img<FloatType>) opService.filter().gauss(flinnPeakPlot,
				sigma.get());
		}
		flinnPeakPlotImage = new ImgPlus<>(flinnPeakPlot, "Flinn Peak Plot");
		flinnPeakPlotImage.setChannelMaximum(0, 255.0f);
		flinnPeakPlotImage.setChannelMinimum(0, 0.0f);
	}

	private void createFlinnPlotImage(final double[] aBRatios,
		final double[] bCRatios)
	{
		final Img<BitType> flinnPlot = ArrayImgs.bits(FLINN_PLOT_DIMENSION,
			FLINN_PLOT_DIMENSION);
		final RandomAccess<BitType> flinnRA = flinnPlot.randomAccess();
		for (int i = 0; i < aBRatios.length; i++) {
			final long x = Math.round(aBRatios[i] * (FLINN_PLOT_DIMENSION - 1));
			final long y = FLINN_PLOT_DIMENSION - Math.round(bCRatios[i] *
				(FLINN_PLOT_DIMENSION - 1)) - 1;
			flinnRA.setPosition(x, 0);
			flinnRA.setPosition(y, 1);
			flinnRA.get().setOne();
		}
		flinnPlotImage = new ImgPlus<>(flinnPlot, "Unweighted Flinn Plot");
		flinnPlotImage.setChannelMaximum(0, 255.0f);
		flinnPlotImage.setChannelMinimum(0, 0.0f);
	}

	private Img<FloatType> createNaNCopy() {
		final ArrayImg<FloatType, FloatArray> copy = ArrayImgs.floats(inputImage
			.dimension(0), inputImage.dimension(1), inputImage.dimension(2));
		copy.forEach(e -> e.setReal(Float.NaN));
		return copy;
	}

	private void createOutputImages(final List<Ellipsoid> ellipsoids,
		final Img<IntType> ellipsoidIDs)
	{
		createEFImage(ellipsoids, ellipsoidIDs);
		createVolumeImage(ellipsoids, ellipsoidIDs);
		final double[] aBRatios = ellipsoids.parallelStream().mapToDouble(e -> e
			.getA() / e.getB()).toArray();
		createAToBImage(aBRatios, ellipsoidIDs);
		final double[] bCRatios = ellipsoids.parallelStream().mapToDouble(e -> e
			.getB() / e.getC()).toArray();
		createBToCImage(bCRatios, ellipsoidIDs);
		createFlinnPlotImage(aBRatios, bCRatios);
		createFlinnPeakPlot(aBRatios, bCRatios, ellipsoidIDs);
		eIdImage = new ImgPlus<>(ellipsoidIDs, "ID");
		eIdImage.setChannelMaximum(0, ellipsoids.size() / 10.0f);
		eIdImage.setChannelMinimum(0, -1.0f);
	}

	private void createVolumeImage(final List<Ellipsoid> ellipsoids,
		final IterableInterval<IntType> ellipsoidIDs)
	{
		final Img<FloatType> volumeImage = createNaNCopy();
		final double[] volumes = ellipsoids.parallelStream().mapToDouble(
			Ellipsoid::getVolume).toArray();
		mapValuesToImage(volumes, ellipsoidIDs, volumeImage);
		vImage = new ImgPlus<>(volumeImage, "Volume");
		vImage.setChannelMaximum(0, ellipsoids.get(0).getVolume());
		vImage.setChannelMinimum(0, -1.0f);
	}

    private long countAssignedVoxels(final Iterable<IntType> ellipsoidIdentityImage) {
        final LongType assignedVoxels = new LongType();
        ellipsoidIdentityImage.forEach(e -> {
            if (e.get() >= 0) {
                assignedVoxels.inc();
            }
        });
        return assignedVoxels.get();
    }

    private static Img<IntType> assignEllipsoidID(final Img<BitType> inputAsBit, final Collection<Ellipsoid> ellipsoids) {
        final Img<IntType> ellipsoidIdentityImage = ArrayImgs.ints(inputAsBit.dimension(0), inputAsBit.dimension(1), inputAsBit.dimension(2));
        ellipsoidIdentityImage.forEach(c -> c.setInteger(-1));

        final LongStream zRange = LongStream.range(0, inputAsBit.dimension(2));

        zRange.parallel().forEach(sliceIndex -> {
            //multiply by image unit? make more intelligent bounding box?
            final List<Ellipsoid> localEllipsoids = ellipsoids.stream().filter(e -> Math.abs(e.getCentroid().z() - sliceIndex) < e.getC()).collect(toList());
            final long[] mins = {0,0,sliceIndex};
            final long[] maxs = {inputAsBit.dimension(0) - 1, inputAsBit.dimension(1) - 1, sliceIndex};
            final IntervalView<BitType> inputslice = Views.interval(inputAsBit, mins, maxs);
            final Cursor<BitType> inputCursor = inputslice.localizingCursor();
            while (inputCursor.hasNext()) {
                inputCursor.fwd();
				if (!inputCursor.get().get()) {
					continue;
				}
				final long [] coordinates = new long[3];
				inputCursor.localize(coordinates);
				final Vector3d point = new Vector3d(coordinates[0], coordinates[1], coordinates[2]);
				point.add(0.5 , 0.5, 0.5);
				findID(localEllipsoids, Views.interval(ellipsoidIdentityImage, mins, maxs), point);
			}
        });
        return ellipsoidIdentityImage;
    }


    private List<Ellipsoid> findEllipsoids(final Collection<Vector3dc> internalSeedPoints) {
        final List<Vector3dc> filterSamplingDirections = getGeneralizedSpiralSetOnSphere(250);
        return internalSeedPoints.parallelStream().map(this::getPointCombinationsForOneSeedPoint).flatMap(Collection::stream)
                .map(c -> findLocalEllipsoidOp.calculate(new ArrayList<>(c.getA()), c.getB()))
                .filter(Optional::isPresent).map(Optional::get)
                .filter(e -> isEllipsoidWhollyInForeground(e, filterSamplingDirections)).collect(toList());
    }

    private List<ValuePair<Set<ValuePair<Vector3dc, Vector3dc>>, Vector3dc>> getPointCombinationsForOneSeedPoint(final Vector3dc internalSeedPoint)
    {
        final int nSphere = 40;
        final List<Vector3dc> sphereSamplingDirections = getGeneralizedSpiralSetOnSphere(nSphere);

        final List<Vector3dc> contactPoints = sphereSamplingDirections.parallelStream().map(d -> {
            final Vector3dc direction = new Vector3d(d);
            return findFirstPointInBGAlongRay(direction, internalSeedPoint);
        }).collect(toList());

        final List<ValuePair<Vector3dc, Vector3dc>> seedPoints = new ArrayList<>();
        contactPoints.forEach(c -> {
            final Vector3d inwardDirection = new Vector3d(internalSeedPoint);
            inwardDirection.sub(c);
            inwardDirection.normalize();
            seedPoints.add(new ValuePair<>(c, inwardDirection));
        });

        return new ArrayList<>(getAllUniqueCombinationsOfFourPoints(seedPoints, internalSeedPoint));
    }

	private IterableInterval<R> createRidge(
		final RandomAccessibleInterval<BitType> image)
	{
		final RandomAccessibleInterval<R> distanceTransform = opService.image()
			.distancetransform(image);
		final List<Shape> shapes = new ArrayList<>();
		shapes.add(new HyperSphereShape(2));
		final IterableInterval<R> open = opService.morphology().open(
			distanceTransform, shapes);
		final IterableInterval<R> close = opService.morphology().close(
			distanceTransform, shapes);
		final IterableInterval<R> ridge = opService.math().subtract(close, open);
		final Cursor<R> ridgeCursor = ridge.localizingCursor();
		// remove ridgepoints in BG - how does this make a difference?
		final RandomAccess<BitType> inputBitRA = image.randomAccess();
		final long[] position = new long[3];
		while (ridgeCursor.hasNext()) {
			ridgeCursor.fwd();
			ridgeCursor.localize(position);
			inputBitRA.setPosition(position);
			if (!inputBitRA.get().get()) {
				ridgeCursor.get().setReal(0.0f);
			}
		}
		return ridge;
	}

	private void createRidgePointsImage(final IterableInterval<R> ridge,
		final double ridgePointCutOff)
	{
		final R threshold = ridge.cursor().get().createVariable();
		threshold.setReal(ridgePointCutOff);
		final IterableInterval<BitType> thresholdedRidge = opService.threshold()
			.apply(ridge, threshold);
		ridgePointsImage = new ImgPlus<>(opService.convert().uint8(
			thresholdedRidge), "Seeding Points");
	}

	private List<Vector3dc> getRidgeSeedPoints(
		final RandomAccessibleInterval<BitType> bitImage)
	{
		final IterableInterval<R> ridge = createRidge(bitImage);
		final double threshold = thresholdForBeingARidgePoint.getRealFloat() *
			opService.stats().max(ridge).getRealFloat();
		createRidgePointsImage(ridge, threshold);
		final List<Vector3dc> seeds = new ArrayList<>();
		final Cursor<R> ridgeCursor = ridge.cursor();
		final long[] position = new long[3];
		while (ridgeCursor.hasNext()) {
			ridgeCursor.fwd();
			final double localValue = ridgeCursor.get().getRealFloat();
			if (localValue <= threshold) {
				continue;
			}
			ridgeCursor.localize(position);
			final Vector3d seed = new Vector3d(position[0], position[1], position[2]);
			seed.add(0.5, 0.5, 0.5);
			seeds.add(seed);
		}
		if (seeds.size() > approximateNumberOfInternalSeeds.getInt()) {
			reduceSeedPoints(seeds);
		}
		return seeds;
	}

	private void reduceSeedPoints(final Collection<Vector3dc> seeds) {
		final double probabilityOfAcceptingSeed =
			((double) approximateNumberOfInternalSeeds.getInt()) / seeds.size();
		seeds.removeIf(i -> rng.nextDouble() > probabilityOfAcceptingSeed);
	}

	private void mapValuesToImage(final double[] values, final IterableInterval<IntType> ellipsoidIdentityImage, final RandomAccessible<FloatType> ellipsoidFactorImage) {
        final RandomAccess<FloatType> ef = ellipsoidFactorImage.randomAccess();
        final Cursor<IntType> id = ellipsoidIdentityImage.localizingCursor();
		final long[] position = new long[3];
        while(id.hasNext()){
            id.fwd();
			if (id.get().getInteger() < 0) {
				continue;
			}
			id.localize(position);
			final double value = values[id.get().getInteger()];
			ef.setPosition(position);
			ef.get().setReal(value);
		}
    }

    // TODO Write Javadoc
	// find largest ellipsoid containing current position
	private static void findID(final List<Ellipsoid> ellipsoids,
		final RandomAccessible<IntType> ellipsoidIdentityImage,
		final Vector3d point)
	{
		final int id = IntStream.range(0, ellipsoids.size()).filter(i -> isInside(
			point, ellipsoids.get(i))).findFirst().orElse(-1);
		if (id < 0) {
			return;
		}
		final RandomAccess<IntType> eIDRandomAccess = ellipsoidIdentityImage
			.randomAccess();
		eIDRandomAccess.setPosition(vectorToPixelGrid(point));
		eIDRandomAccess.get().set(id);
	}

	private boolean isEllipsoidWhollyInForeground(final Ellipsoid e,
		final Collection<Vector3dc> sphereSamplingDirections)
	{
		if (!isInBounds(vectorToPixelGrid(e.getCentroid()))) {
			return false;
		}
		final Builder<Vector3dc> builder = Stream.builder();
		final Matrix3d orientation = e.getOrientation().get3x3(new Matrix3d());
		for (int i = 0; i < 3; i++) {
			final Vector3dc v = orientation.getColumn(i, new Vector3d());
			builder.add(v);
			builder.add(v.negate(new Vector3d()));
		}
		final Stream<Vector3dc> directions = Stream.concat(sphereSamplingDirections
			.stream(), builder.build());
		final Matrix3d reconstruction = reconstructMatrix(e);
		return directions.noneMatch(dir -> isEllipsoidIntersectionBackground(
			reconstruction, e.getCentroid(), dir));
	}

	private Matrix3d reconstructMatrix(final Ellipsoid e) {
		final double axisReduction = Math.sqrt(3);
		final double[] scales = DoubleStream.of(e.getA(), e.getB(), e.getC()).map(
			s -> s - axisReduction).map(s -> s * s).map(s -> 1.0 / s).toArray();
		final Matrix3dc Q = e.getOrientation().get3x3(new Matrix3d());
		final Matrix3dc lambda = new Matrix3d(scales[0], 0, 0, 0, scales[1], 0, 0,
			0, scales[2]);
		final Matrix3dc QT = Q.transpose(new Matrix3d());
		final Matrix3dc LambdaQT = lambda.mul(QT, new Matrix3d());
		return Q.mul(LambdaQT, new Matrix3d());
	}

	private boolean isEllipsoidIntersectionBackground(final Matrix3dc a,
		final Vector3dc centroid, final Vector3dc dir)
	{
		final Vector3dc aTimesDir = a.transform(dir, new Vector3d());
		final double surfaceIntersectionParameter = Math.sqrt(1.0 / dir.dot(
			aTimesDir));
		final Vector3d intersectionPoint = new Vector3d(dir);
		intersectionPoint.mul(surfaceIntersectionParameter);
		intersectionPoint.add(centroid);
		final long[] pixel = vectorToPixelGrid(intersectionPoint);
		if (!isInBounds(pixel)) {
			return true;
		}
		final RandomAccess<R> inputRA = inputImage.getImg().randomAccess();
		inputRA.setPosition(pixel);
		return inputRA.get().getRealDouble() == 0;
	}

    private static float computeEllipsoidFactor(final Ellipsoid ellipsoid) {
        return (float) (ellipsoid.getA() / ellipsoid.getB() - ellipsoid.getB() / ellipsoid.getC());
    }

    // TODO Make a method of the Ellipsoid class
    static boolean isInside(final Vector3d coordinates, final Ellipsoid ellipsoid) {
        final Vector3d centroid = ellipsoid.getCentroid();
        final double c = ellipsoid.getC();

        //TODO restate in one statement with && and ||
        if(Math.abs(coordinates.x()-centroid.x())>c) return false;
        if(Math.abs(coordinates.y()-centroid.y())>c) return false;
        if(Math.abs(coordinates.z()-centroid.z())>c) return false;

        final Vector3d x = new Vector3d(coordinates);
        x.sub(centroid);

        if(x.length()>ellipsoid.getC()) return false;

        final Matrix3d orientation = new Matrix3d();
        ellipsoid.getOrientation().get3x3(orientation);

        final Matrix3d eigenMatrix = new Matrix3d();
        eigenMatrix.scaling(1.0 / (ellipsoid.getA() * ellipsoid.getA()),1.0 / (ellipsoid.getB() * ellipsoid.getB()),1.0 / (ellipsoid.getC() * ellipsoid.getC()));

        eigenMatrix.mul(orientation, eigenMatrix);
        final Matrix3d orientationTransposed = orientation.transpose();
        orientationTransposed.mul(eigenMatrix, eigenMatrix);

        final Vector3d Ax = new Vector3d();
        eigenMatrix.transform(x, Ax);

        return x.dot(Ax) < 1;
    }

    /**
     * Method to numerically approximate equidistantly spaced points on the
     * surface of a sphere
     * <p>
     * The implementation follows the description of the theoretical work by
     * Rakhmanov et al., 1994 in Saff and Kuijlaars, 1997
     * (<a href="doi:10.1007/BF03024331">dx.doi.org/10.1007/BF03024331</a>), but k
     * is shifted by one to the left for more convenient indexing.
     *
     * @param n : number of points required (has to be > 2)
     * </p>
     */
    private static List<Vector3dc> getGeneralizedSpiralSetOnSphere(final int n) {
        final List<Vector3dc> spiralSet = new ArrayList<>();

        final List<Double> phi = new ArrayList<>();
        phi.add(0.0);
        for (int k = 1; k < n - 1; k++) {
            final double h = -1.0 + 2.0 * k / (n - 1);
            phi.add(getPhiByRecursion(n, phi.get(k - 1), h));
        }
        phi.add(0.0);

        for (int k = 0; k < n; k++) {
            final double h = -1.0 + 2.0 * k / (n - 1);
            final double theta = Math.acos(h);
            spiralSet.add(new Vector3d(Math.sin(theta) * Math.cos(phi.get(k)), Math
                    .sin(theta) * Math.sin(phi.get(k)), Math.cos(theta)));

        }

        return spiralSet;
    }

    private static double getPhiByRecursion(final double n, final double phiKMinus1,
                                            final double hk) {
        final double phiK = phiKMinus1 + 3.6 / Math.sqrt(n) * 1.0 / Math.sqrt(1 - hk *
                hk);
        // modulo 2pi calculation works for positive numbers only, which is not a
        // problem in this case.
        return phiK - Math.floor(phiK / (2 * Math.PI)) * 2 * Math.PI;
    }

    private Vector3d findFirstPointInBGAlongRay(final Vector3dc rayIncrement,
                                                final Vector3dc start) {
        final RandomAccess<R> randomAccess = inputImage.randomAccess();

        final Vector3d currentRealPosition = new Vector3d(start);
        long[] currentPixelPosition = vectorToPixelGrid(start);
        randomAccess.setPosition(currentPixelPosition);

        while (randomAccess.get().getRealDouble() > 0) {
            currentRealPosition.add(rayIncrement);
            currentPixelPosition = vectorToPixelGrid(currentRealPosition);
            if (!isInBounds(currentPixelPosition)) break;
            randomAccess.setPosition(currentPixelPosition);
        }
        return currentRealPosition;
    }


    private boolean isInBounds(final long[] currentPixelPosition) {
        final long width = inputImage.dimension(0);
        final long height = inputImage.dimension(1);
        final long depth = inputImage.dimension(2);
        return !(currentPixelPosition[0] < 0 || currentPixelPosition[0] >= width ||
                currentPixelPosition[1] < 0 || currentPixelPosition[1] >= height ||
                currentPixelPosition[2] < 0 || currentPixelPosition[2] >= depth);
    }

    // TODO make a utility method, similar used in MILPlane op
    private static long[] vectorToPixelGrid(final Vector3dc currentPosition) {
        return Stream.of(currentPosition.x(), currentPosition.y(),
                currentPosition.z()).mapToLong(x -> (long) x.doubleValue()).toArray();
    }

	private static
		List<ValuePair<Set<ValuePair<Vector3dc, Vector3dc>>, Vector3dc>>
		getAllUniqueCombinationsOfFourPoints(
			final List<ValuePair<Vector3dc, Vector3dc>> points,
			final Vector3dc centre)
	{
		final Iterator<int[]> iterator = CombinatoricsUtils.combinationsIterator(
			points.size(), 4);
		final List<ValuePair<Set<ValuePair<Vector3dc, Vector3dc>>, Vector3dc>> pointCombinations =
			new ArrayList<>();
		iterator.forEachRemaining(el -> {
			final Set<ValuePair<Vector3dc, Vector3dc>> pointCombination = IntStream
				.range(0, 4).mapToObj(i -> points.get(el[i])).collect(Collectors
					.toSet());
			if (pointCombination.size() == 4) {
				pointCombinations.add(new ValuePair<>(pointCombination, centre));
			}
		});
		return pointCombinations;
	}

    @SuppressWarnings("unused")
    private void validateImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }
        if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
            cancel(NOT_3D_IMAGE);
            return;
        }
        if (!ElementUtil.isColorsBinary(inputImage)) {
            cancel(NOT_BINARY);
            return;
        }
		if (!AxisUtils.isSpatialCalibrationsIsotropic(inputImage, 0.003,
			unitService) && !calibrationWarned)
		{
			final Result result = uiService.showDialog(
				"The voxels in the image are anisotropic, which may affect results. Continue anyway?",
				WARNING_MESSAGE, OK_CANCEL_OPTION);
			// Avoid showing warning more than once (validator gets called before and
			// after dialog pops up..?)
			calibrationWarned = true;
			if (result != OK_OPTION) {
				cancel(null);
			}
		}
    }

    // endregion
}