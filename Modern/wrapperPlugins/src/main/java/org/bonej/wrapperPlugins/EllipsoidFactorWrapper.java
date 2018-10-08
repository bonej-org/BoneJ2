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

import net.imagej.ImgPlus;
import net.imagej.display.ColorTables;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imagej.units.UnitService;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
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
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.EllipsoidPoints;
import org.bonej.ops.ellipsoid.FindEllipsoidFromBoundaryPoints;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
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
import org.scijava.widget.NumberWidget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.imglib2.roi.Regions.countTrue;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

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
    private final BinaryFunctionOp<List<ValuePair<Vector3dc, Vector3dc>>, Vector3dc, Optional<Ellipsoid>> findLocalEllipsoidOp = new FindEllipsoidFromBoundaryPoints();
	private static final String NO_ELLIPSOIDS_FOUND = "No ellipsoids were found - try allowing more sampling directions and/or more seedpoints.";

	@SuppressWarnings("unused")
    @Parameter(validater = "validateImage")
    private ImgPlus<R> inputImage;

    @Parameter(persist = false, required = false)
    private DoubleType sigma = new DoubleType(0);

    @Parameter(label = "Maximum internal seeds", min = "0", stepSize = "1",
            description = "Approximate maximum of internal seed points allowed. If more seeds are found, they are filtered with probability 1-Maximum internal seeds/total internal seeds found.",
            style = NumberWidget.SPINNER_STYLE)
    private long approximateMaximumNumberOfSeeds = 10000;

    @Parameter(label = "Sampling directions", min = "0", stepSize = "1",
            description = "Number of directions (evenly spaced on the surface of a sphere) that internal seed points will search for contact points.",
            style = NumberWidget.SPINNER_STYLE)
    private int nSphere = 20;

	@Parameter(persist = false, required = false)
	private ComplexType<DoubleType> thresholdForBeingARidgePoint = new DoubleType(0.8);

	@Parameter(persist = false, required = false)
	private boolean showSecondaryImages = false;

	@Parameter(label = "Seed point image", type = ItemIO.OUTPUT)
	private ImgPlus<UnsignedByteType> seedPointsImage;

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

	/** The EF results in a {@link Table}, null if there are no results */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<Double>, Double> resultsTable;

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

    private final int nFilterSampling = 200;

    @Override
    public void run() {
        statusService.showStatus("Ellipsoid Factor: initialising...");
        rng = new Random(23);
        final ImgPlus<BitType> bitImage = Common.toBitTypeImgPlus(opService, inputImage);
        final List<Vector3dc> internalSeedPoints = getRidgeSeedPoints(bitImage);

        statusService.showStatus("Ellipsoid Factor: finding ellipsoids...");
        final List<Ellipsoid> ellipsoids = findEllipsoids(internalSeedPoints);
        if(ellipsoids.isEmpty())
        {
        	cancel(NO_ELLIPSOIDS_FOUND);
        	return;
		}
        ellipsoids.sort(Comparator.comparingDouble(e -> -e.getVolume()));

        statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
        final Img<IntType> ellipsoidIdentityImage = assignEllipsoidIDs(bitImage, ellipsoids);
        createPrimaryOutputImages(ellipsoids, ellipsoidIdentityImage);
        if(showSecondaryImages) createSecondaryOutputImages(ellipsoids,ellipsoidIdentityImage);

        final double numberOfForegroundVoxels = countTrue(bitImage);
        final double numberOfAssignedVoxels = countAssignedVoxels(ellipsoidIdentityImage);
		final double fillingPercentage = 100.0 * (numberOfAssignedVoxels / numberOfForegroundVoxels);
		addResults(ellipsoids,fillingPercentage);

        if(logService.isDebug()) {
			logService.debug("initial sampling directions = " + nSphere);
			logService.debug("threshold for ridge point inclusions = " + thresholdForBeingARidgePoint);
			logService.debug("filtering sampling directions = " + nFilterSampling);
			logService.debug("assigned voxels = " + numberOfAssignedVoxels);
			logService.debug("foreground voxels = " + numberOfForegroundVoxels);
			for (int i = 0; i < Math.min(100, ellipsoids.size()); i++) {
				logService.debug("ellipsoid(" + i + "):\n" + ellipsoids.get(i).toString());
			}
		}



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

	private void createPrimaryOutputImages(final List<Ellipsoid> ellipsoids,
										   final Img<IntType> ellipsoidIDs) {
		createEFImage(ellipsoids, ellipsoidIDs);
		createVolumeImage(ellipsoids, ellipsoidIDs);
	}

	private void createSecondaryOutputImages(final List<Ellipsoid> ellipsoids, final Img<IntType> ellipsoidIDs)
	{
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

	private static Img<IntType> assignEllipsoidIDs(final Img<BitType> mask,
		final List<Ellipsoid> ellipsoids)
	{
		final Img<IntType> idImage = ArrayImgs.ints(mask.dimension(0), mask
			.dimension(1), mask.dimension(2));
		idImage.forEach(c -> c.setInteger(-1));
		final Map<Ellipsoid, Integer> iDs =
				IntStream.range(0, ellipsoids.size()).boxed().collect(toMap(ellipsoids::get, Function.identity()));
		final LongStream zRange = LongStream.range(0, mask.dimension(2));
		zRange.parallel().forEach(z -> {
			// multiply by image unit? make more intelligent bounding box?
            final List<Ellipsoid> localEllipsoids = ellipsoids.stream().filter(
				e -> Math.abs(e.getCentroid().z() - z) < e.getC()).collect(
					toList());
			final long[] mins = { 0, 0, z };
			final long[] maxs = { mask.dimension(0) - 1, mask.dimension(1) - 1,
				z };
			final Cursor<BitType> maskSlice = Views.interval(mask, mins, maxs)
				.localizingCursor();
			colourSlice(idImage, maskSlice, localEllipsoids, iDs);
		});
		return idImage;
	}

	private void addResults(final List<Ellipsoid> ellipsoids, double fillingPercentage) {
		final String label = inputImage.getName();
		SharedTable.add(label,"filling percentage", fillingPercentage);
		SharedTable.add(label, "number of ellipsoids found", ellipsoids.size());
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
		else {
			cancel(NO_ELLIPSOIDS_FOUND);
		}
	}

	private static void colourSlice(final RandomAccessible<IntType> idImage,
									final Cursor<BitType> mask, final Collection<Ellipsoid> localEllipsoids, final Map<Ellipsoid, Integer> iDs)
	{
		while (mask.hasNext()) {
			mask.fwd();
			if (!mask.get().get()) {
				continue;
			}
			final long[] coordinates = new long[3];
			mask.localize(coordinates);
			final Vector3d point = new Vector3d(coordinates[0], coordinates[1],
				coordinates[2]);
			point.add(0.5, 0.5, 0.5);
			colourID(localEllipsoids, idImage, point, iDs);
		}
	}

	// TODO Refactor this and sub-functions into an Op
	private List<Ellipsoid> findEllipsoids(final Collection<Vector3dc> seeds) {
		final List<Vector3dc> filterSamplingDirections =
			getGeneralizedSpiralSetOnSphere(nFilterSampling).collect(toList());
		final Stream<Optional<Ellipsoid>> ellipsoidCandidates = seeds
			.parallelStream().flatMap(seed -> getPointCombinationsForOneSeedPoint(
				seed).map(c -> findLocalEllipsoidOp.calculate(new ArrayList<>(c),
					seed)));
		return ellipsoidCandidates.filter(Optional::isPresent).map(Optional::get)
			.filter(e -> isEllipsoidWhollyInForeground(e,filterSamplingDirections))
			.collect(toList());
	}

	private Stream<Set<ValuePair<Vector3dc, Vector3dc>>>
		getPointCombinationsForOneSeedPoint(final Vector3dc centre)
	{
		final Stream<Vector3dc> sphereSamplingDirections = getGeneralizedSpiralSetOnSphere(nSphere);
        final List<Vector3dc> contactPoints = sphereSamplingDirections.map(d -> {
			final Vector3dc direction = new Vector3d(d);
			return findFirstPointInBGAlongRay(direction, centre);
		}).collect(toList());
		return getAllUniqueCombinationsOfFourPoints(contactPoints, centre);
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
		final Img openImg = (Img) open;
		final RandomAccess<R> openedRA = openImg.randomAccess();
		final long[] position = new long[3];
		while (ridgeCursor.hasNext()) {
			ridgeCursor.fwd();
			ridgeCursor.localize(position);
			openedRA.setPosition(position);
			if (openedRA.get().getRealDouble()<1.0+1e-12)//avoids false ridge points on edge of FG
			{
				ridgeCursor.get().setReal(0.0f);
			}
		}
		return ridge;
	}

	private void createSeedPointImage(final Img<R> seedPointImage,
									  final List<Vector3dc> seedPoints)
	{
		RandomAccess<R> randomAccess = seedPointImage.randomAccess();
		seedPoints.forEach(seed ->
				{
					long[] seedPixel = vectorToPixelGrid(seed);
					randomAccess.setPosition(seedPixel);
					randomAccess.get().setOne();
				}
		);
		seedPointsImage = new ImgPlus<>(opService.convert().uint8(
			seedPointImage), "Seeding Points");
	}

	// TODO Could this be an op?
	private List<Vector3dc> getRidgeSeedPoints(
		final RandomAccessibleInterval<BitType> bitImage)
	{
		final Img<R> ridge = (Img<R>) createRidge(bitImage);
		final double threshold = thresholdForBeingARidgePoint.getRealFloat() *
			opService.stats().max(ridge).getRealFloat();
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
		if (seeds.size() > approximateMaximumNumberOfSeeds) {
			reduceSeedPoints(seeds);
		}
		createSeedPointImage(ridge, seeds);
		return seeds;
	}

	private void reduceSeedPoints(final Collection<Vector3dc> seeds) {
		final double probabilityOfAcceptingSeed =
			((double) approximateMaximumNumberOfSeeds / seeds.size());
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

    private static void colourID(final Collection<Ellipsoid> localEllipsoids,
								 final RandomAccessible<IntType> ellipsoidIdentityImage,
								 final Vector3dc point, final Map<Ellipsoid, Integer> iDs)
	{
		final Optional<Ellipsoid> candidate = localEllipsoids.stream().filter(e -> e.inside(point)).findFirst();
		if (!candidate.isPresent()) {
			return;
		}
		final RandomAccess<IntType> eIDRandomAccess = ellipsoidIdentityImage
			.randomAccess();
		eIDRandomAccess.setPosition(vectorToPixelGrid(point));
		final Ellipsoid ellipsoid = candidate.get();
		eIDRandomAccess.get().set(iDs.get(ellipsoid));
	}

	private boolean isEllipsoidWhollyInForeground(final Ellipsoid e,
		final Collection<Vector3dc> sphereSamplingDirections)
	{
		if (outOfBounds(inputImage, vectorToPixelGrid(e.getCentroid()))) {
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
		final Matrix3d reconstruction = reconstructMatrixOfSlightlySmallerEllipsoid(e,Math.sqrt(3.0));
		return directions.noneMatch(dir -> isEllipsoidIntersectionBackground(
			reconstruction, e.getCentroid(), dir));
	}

	public Matrix3d reconstructMatrixOfSlightlySmallerEllipsoid(Ellipsoid e, final double reduction) {
		final double[] scales = DoubleStream.of(e.getA(), e.getB(), e.getC()).map(
				s -> s - reduction).map(s -> s * s).map(s -> 1.0 / s).toArray();
		final Matrix3dc Q = e.getOrientation().get3x3(new Matrix3d());
		final Matrix3dc lambda = new Matrix3d(scales[0], 0, 0, 0, scales[1], 0, 0,
				0, scales[2]);
		final Matrix3dc QT = Q.transpose(new Matrix3d());
		final Matrix3dc LambdaQ = lambda.mul(Q, new Matrix3d());
		return QT.mul(LambdaQ, new Matrix3d());
	}
	//using points uniformly distributed on ellipsoid surface
	private boolean isEllipsoidWhollyInForeground(final Ellipsoid e)
    {
        EllipsoidPoints points = new EllipsoidPoints();
        final List<Vector3d> directionsUniformOnEllipsoid = points.calculate(DoubleStream.of(e.getA(), e.getB(), e.getC()).toArray(), 100L);
        final Matrix3d Q = new Matrix3d();
        e.getOrientation().get3x3(Q);
        directionsUniformOnEllipsoid.forEach(sp -> Q.transpose().transform(sp));
        directionsUniformOnEllipsoid.forEach(Vector3d::normalize);
        final Matrix3d eMatrix = reconstructMatrixOfSlightlySmallerEllipsoid(e, Math.sqrt(3.0));
        return directionsUniformOnEllipsoid.stream().noneMatch(dir -> isEllipsoidIntersectionBackground(
                eMatrix, e.getCentroid(), dir));
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
		if (outOfBounds(inputImage, pixel)) {
			return true;
		}
		final RandomAccess<R> inputRA = inputImage.getImg().randomAccess();
		inputRA.setPosition(pixel);
		return inputRA.get().getRealDouble() == 0;
	}

    private static float computeEllipsoidFactor(final Ellipsoid ellipsoid) {
        return (float) (ellipsoid.getA() / ellipsoid.getB() - ellipsoid.getB() / ellipsoid.getC());
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
    // TODO Is there an implementation for this in Apache Commons?
    private static Stream<Vector3dc> getGeneralizedSpiralSetOnSphere(final int n) {
        final Builder<Vector3dc> spiralSet = Stream.builder();
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
        return spiralSet.build();
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
            if (outOfBounds(inputImage, currentPixelPosition)) break;
            randomAccess.setPosition(currentPixelPosition);
        }
        return currentRealPosition;
    }

	// TODO make into a utility method, and see where else needed in BoneJ
    private static boolean outOfBounds(final Dimensions dimensions, final long[] currentPixelPosition) {
		for (int i = 0; i < currentPixelPosition.length; i++) {
			final long position = currentPixelPosition[i];
			if (position < 0 || position >= dimensions.dimension(i)) {
				return true;
			}
		}
        return false;
    }

    // TODO make a utility method, similar used in MILPlane op
    private static long[] vectorToPixelGrid(final Vector3dc currentPosition) {
        return Stream.of(currentPosition.x(), currentPosition.y(),
                currentPosition.z()).mapToLong(x -> (long) x.doubleValue()).toArray();
    }

	private static Stream<Set<ValuePair<Vector3dc, Vector3dc>>>
		getAllUniqueCombinationsOfFourPoints(final List<Vector3dc> points,
			final Vector3dc centre)
	{
		final Iterator<int[]> iterator = CombinatoricsUtils.combinationsIterator(
			points.size(), 4);
		final Builder<Set<ValuePair<Vector3dc, Vector3dc>>> pointCombinations = Stream.builder();
		iterator.forEachRemaining(el -> {
			final Stream<Vector3dc> pointCombo = IntStream.range(0, 4).mapToObj(
				i -> points.get(el[i]));
			final Set<ValuePair<Vector3dc, Vector3dc>> dirPoints = pointCombo.map(
				p -> {
					final Vector3dc inwardDir = centre.sub(p, new Vector3d());
					final Vector3dc unitDir = inwardDir.normalize(new Vector3d());
					return new ValuePair<>(p, unitDir);
				}).collect(Collectors.toSet());
			if (dirPoints.size() == 4) {
				pointCombinations.add(dirPoints);
			}
		});
		return pointCombinations.build();
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

    @SuppressWarnings("unused")
    private void enforceValidRange() {
        if (approximateMaximumNumberOfSeeds > inputImage.dimension(0)*inputImage.dimension(1)*inputImage.dimension(2)) {
            approximateMaximumNumberOfSeeds = inputImage.dimension(0)*inputImage.dimension(1)*inputImage.dimension(3);
        }
    }

    // endregion
}