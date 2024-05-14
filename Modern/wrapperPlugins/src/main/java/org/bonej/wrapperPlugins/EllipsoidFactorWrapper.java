/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.wrapperPlugins;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.imglib2.roi.Regions.countTrue;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.units.UnitService;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.bonej.ops.ellipsoid.EllipsoidFactorErrorTracking;
import org.bonej.ops.ellipsoid.EllipsoidFactorOutputGenerator;
import org.bonej.ops.ellipsoid.EllipsoidOptimisationStrategy;
import org.bonej.ops.ellipsoid.OptimisationParameters;
import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.bonej.ops.ellipsoid.constrain.NoEllipsoidConstrain;
import org.bonej.ops.skeletonize.FindRidgePoints;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * ImageJ plugin to describe the local geometry of a binary image in an
 * oblate/prolate spheroid space. Uses Skeletonize3D to generate a 3D skeleton
 * and/or a distance ridge-based image to generate seed points, which are used
 * as centres for maximally inscribed ellipsoids.
 *
 * The ellipsoid factor (EF) is a method for the local determination of the rod-
 * or plate-like nature of porous or spongy continua. EF at a point within a 3D
 * structure is defined as the difference in axis ratios of the greatest
 * ellipsoid that fits inside the structure and that contains the point of
 * interest, and ranges from −1 for strongly oblate (discus-shaped) ellipsoids,
 * to +1 for strongly prolate (javelin-shaped) ellipsoids. For an ellipsoid with
 * axes a ≤ b ≤ c, EF = a/b − b/c.
 *
 * @author Michael Doube
 * @author Alessandro Felder
 * @see <a href="https://doi.org/10.3389/fendo.2015.00015">
 *      "The ellipsoid factor for quantification of rods, plates, and intermediate forms in 3D geometries"
 *      Frontiers in Endocrinology (2015)</a>
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor")
public class EllipsoidFactorWrapper <T extends RealType<T> & NativeType<T>> extends BoneJCommand {

	static final String NO_ELLIPSOIDS_FOUND = "No ellipsoids were found - try modifying input parameters.";

	//ImageJ services
	@SuppressWarnings("unused")
	@Parameter
	private OpService opService;
	@SuppressWarnings("unused")
	@Parameter
	private LogService logService;
	@SuppressWarnings("unused")
	@Parameter
	private StatusService statusService;
	@SuppressWarnings("unused")
	@Parameter
	private UIService uiService;
	@SuppressWarnings("unused")
    @Parameter
	private UnitService unitService;
	//main input image
	@SuppressWarnings("unused")
	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	//algorithm parameters
	@Parameter(label = "Vectors")
	int nVectors = 100;
	@Parameter(label = "Sampling increment", description = "Increment for vector searching in real units. Default is ~Nyquist sampling of a unit pixel.", min="0.01", max = "0.99")
	private double vectorIncrement = 1 / 2.3;
	@Parameter(label = "Skeleton points per ellipsoid", description = "Number of skeleton points per ellipsoid. Sets the granularity of the ellipsoid fields.", min="1")
	private int skipRatio = 50;
	@Parameter(label = "Contact sensitivity", description = "Number of contacts with surface required to determine collision.", min = "1")
	private int contactSensitivity = 1;
	@Parameter(label = "Maximum iterations", description = "Maximum currentIteration to try improving ellipsoid fit before stopping.", min="10")
	private int maxIterations = 100;
	@Parameter(label = "Maximum drift", description = "Maximum distance ellipsoid may drift from seed point. Defaults to unit voxel diagonal length", min="0")
	private double maxDrift = Math.sqrt(3);

	//averaging / smoothing
	@Parameter(label = "Repetitions", description = "Number of currentIteration over which to average EF value", min="1")
	private int runs = 1;
	@Parameter(label = "Average over largest n ellipsoids", min="1")
	private int weightedAverageN = 1;
	
	
	//what seed points should I use?
	@Parameter(label = "Seed points based on distance ridge", description = "Tick this if you would like ellipsoids to be seeded based on the foreground distance ridge")
	private boolean seedOnDistanceRidge = true;
	@Parameter(label = "Threshold for distance ridge", description = "How far must a pixel be from the boundary to count as ridge")
	private double distanceThreshold = 0.6;
	@Parameter(label = "Seed points on topology-preserving skeletonization ", description = "Tick this if you would like ellipsoids to be seeded on the topology-preserving skeletonization (\"Skeletonize3D\").")
	private boolean seedOnTopologyPreserving = false;

	@Parameter(label = "Show Flinn plots")
	private boolean showFlinnPlots = false;

	@Parameter(label = "Show algorithm convergence")
	private boolean showConvergence = false;

	@Parameter(label = "Show verbose output images")
	private boolean showSecondaryImages = false;

	@Parameter (label = "EF Output Images", type = ItemIO.OUTPUT)
	private List<ImgPlus> ellipsoidFactorOutputImages;
	@Parameter(label = "Seed Points", type = ItemIO.OUTPUT)
	private ImgPlus<ByteType> seedPointImage;// 0=not a seed, 1=medial seed

	private ImgPlus<BitType> inputAsBitType;

	@Override
	public void run() {
		inputAsBitType = Common.toBitTypeImgPlus(opService, inputImage);

		int totalEllipsoids = 0;
		List<ImgPlus> outputList = null;
		final EllipsoidFactorErrorTracking errorTracking =
				new EllipsoidFactorErrorTracking(opService);

		int counter = 0;
		double[] medianErrors = new double[runs];
		double[] maxErrors = new double[runs];

		for(int i = 0; i<runs; i++) {
			//optimise ellipsoids
			final List<QuickEllipsoid> ellipsoids = runEllipsoidOptimisation(inputImage);
			if (ellipsoids.isEmpty()) {
				cancelMacroSafe(this, NO_ELLIPSOIDS_FOUND);
				return;
			}

			//assign one ellipsoid to each FG voxel
			statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
			final long start = System.currentTimeMillis();
			final Img<IntType> ellipsoidIdentityImage = assignEllipsoidIDs(inputAsBitType, ellipsoids);
			final long stop = System.currentTimeMillis();
			logService.info("Found maximal ellipsoids in " + (stop - start) + " ms");

			//add result of this run to overall result
			//TODO do not match Op every time
			final List<ImgPlus> currentOutputList = (List<ImgPlus>) opService.run(EllipsoidFactorOutputGenerator.class, ellipsoidIdentityImage,
					ellipsoids, showFlinnPlots, showSecondaryImages, inputImage.getName().split("\\.")[0]);

			if(outputList!=null)
			{
				outputList = sumOutput(outputList, currentOutputList, counter);
				if(showConvergence)
				{
					final Map<String, Double> errors = errorTracking.calculate(outputList.get(0));
					errors.forEach((stat,value) -> logService.info(stat+": "+value.toString()));
					medianErrors[i] = errors.get("Median");
					maxErrors[i] = errors.get("Max");
				}
			}
			else{
				outputList = currentOutputList;
				if(showConvergence)
				{
					medianErrors[i] = 2.0; // start with maximum possible error in first run (as no previous runs exist)
					maxErrors[i] = 2.0;
				}
			}
			counter++;
			totalEllipsoids += ellipsoids.size();
		}
		if (totalEllipsoids == 0) {
			cancelMacroSafe(this, NO_ELLIPSOIDS_FOUND);
			return;
		}

		if(runs>1)
		{
			outputList = divideOutput(outputList, runs);
		}

		ellipsoidFactorOutputImages = outputList;

		//calibrate output images
		final double voxelVolume = ElementUtil.calibratedSpatialElementSize(inputImage, unitService);
		for(final ImgPlus imgPlus : ellipsoidFactorOutputImages)
		{
			if(imgPlus.numDimensions()>=3)
			{
				// set spatial axis for first 3 dimensions (ID is 4d)
				for(int dim = 0; dim<3; dim++)
				{
					CalibratedAxis axis = inputImage.axis(dim);
					axis.setUnit(inputImage.axis(dim).unit());
					imgPlus.setAxis(axis, dim);
				}
				if("Volume".equals(imgPlus.getName())) {
					final Cursor<RealType> cursor = imgPlus.cursor();
					cursor.forEachRemaining(c ->
					{
						c.mul(voxelVolume);
					});
					imgPlus.setChannelMaximum(0,imgPlus.getChannelMaximum(0)*voxelVolume);
				}
			}
		}

		final ImgPlus EF = ellipsoidFactorOutputImages.get(0);
		final double numberOfForegroundVoxels = countTrue(inputAsBitType);
		final double numberOfAssignedVoxels = countAssignedVoxels(EF);
		final double fillingPercentage = 100.0 * (numberOfAssignedVoxels / numberOfForegroundVoxels);


		DescriptiveStatistics stats = new DescriptiveStatistics();
		final Cursor<FloatType> cursor = EF.cursor();
		while(cursor.hasNext()){
			cursor.fwd();
			final double value = cursor.get().getRealDouble();
			if(!Double.isNaN(value)){
				stats.addValue(value);
			}
		}
		final double median = stats.getPercentile(50);
		SharedTable.add(inputImage.getName(), "Median EF", median);
		final double max = stats.getMax();
		SharedTable.add(inputImage.getName(), "Max EF", max);
		final double min = stats.getMin();
		SharedTable.add(inputImage.getName(), "Min EF", min);
		if(showConvergence)
		{
			for(int i=1; i<runs; i++)
			{
				SharedTable.add(inputImage.getName(),"median change "+i, medianErrors[i]);
				SharedTable.add(inputImage.getName(),"maximum change "+i, maxErrors[i]);
			}
			addResults(totalEllipsoids, fillingPercentage);
		}
		resultsTable = SharedTable.getTable();
		statusService.showStatus("Ellipsoid Factor completed");
	}

	private List<ImgPlus> divideOutput(final List<ImgPlus> outputList, final int repetitions) {
		final List<ImgPlus> divided = new ArrayList<>();
		for (final ImgPlus floatTypes : outputList) {
			final Img division = (Img) opService.math()
					.divide(opService.convert().float32(floatTypes.getImg()),
							new FloatType(repetitions));
			final ImgPlus divisionImgPlus = new ImgPlus<>(division, floatTypes);
			divisionImgPlus.setChannelMaximum(0, floatTypes.getChannelMaximum(0));
			divisionImgPlus.setChannelMinimum(0, floatTypes.getChannelMinimum(0));
			divided.add(divisionImgPlus);
		}
		return divided;
	}

	private List<ImgPlus> sumOutput(final List<ImgPlus> outputList,
											   final List<ImgPlus> currentOutputList,
											   final double nSum) {
		final List<ImgPlus> summed = new ArrayList<>();
		for(int i=0; i<outputList.size(); i++)
		{
			//this does not deal with NaNs the way we would like
			//final Img addition = (Img) opService.math().add(outputList.get(i).getImg(), (IterableInterval<FloatType>) currentOutputList.get(i).getImg());

			//workaround to avoid NaN addition, still dodgy, first nonNaN will get loads of weight.
			final Img addition = outputList.get(i).getImg().copy();
			final Cursor<? extends RealType> previousVal = outputList.get(i).cursor();
			final Cursor<? extends RealType> currentVal = currentOutputList.get(i).getImg().cursor();
			final Cursor<? extends RealType> nextVal = addition.cursor();

			while(previousVal.hasNext())
			{
				previousVal.fwd();
				currentVal.fwd();
				nextVal.fwd();

				final double p = previousVal.get().getRealDouble();
				final double c = currentVal.get().getRealDouble();
				// only need to care about XOR case
				if(!Double.isFinite(c) ^ !Double.isFinite(p))
				{
					if(!Double.isFinite(c)){
						nextVal.get().setReal(p*(1.0+1.0/nSum));
					}
					else
					{
						nextVal.get().setReal(c*(nSum+1));
					}
				}
				else {
					nextVal.get().setReal(p+c);
				}

			}
			final ImgPlus additionImgPlus = new ImgPlus<>(addition, outputList.get(i));
			additionImgPlus.setChannelMaximum(0, outputList.get(i).getChannelMaximum(0));
			additionImgPlus.setChannelMinimum(0, outputList.get(i).getChannelMinimum(0));
			summed.add(additionImgPlus);

		}
		return summed;
	}

	/**
	 * Using skeleton points as seeds, propagate along each vector until a boundary
	 * is hit. Use the resulting cloud of boundary points as input into an ellipsoid
	 * fit.
	 *
	 * @param imp
	 *            input image
	 * @return array of fitted ellipsoids
	 */
	private List<QuickEllipsoid> runEllipsoidOptimisation(final ImgPlus imp) {
		long start = System.currentTimeMillis();

		final int w = (int) imp.dimension(0);
		final int h = (int) imp.dimension(1);
		final int d = (int) imp.dimension(2);

		final byte[][] pixels = imgPlusToByteArray(imp);
		final ArrayImg<ByteType, ByteArray> seedImage = ArrayImgs.bytes(w, h, d);
		final List<QuickEllipsoid> quickEllipsoids = new ArrayList<>();
		final OptimisationParameters parameters = new OptimisationParameters(vectorIncrement, nVectors, contactSensitivity, maxIterations, maxDrift);
		if (seedOnDistanceRidge) {
			final ImgPlus<BitType> inputAsBitType = Common.toBitTypeImgPlus(opService, inputImage);
			List<Vector3d> ridgePoints = getDistanceRidgePoints(inputAsBitType);
			ridgePoints = applySkipRatio(ridgePoints);
			addPointsToDisplay(ridgePoints, seedImage, (byte) 1);

			statusService.showStatus("Optimising distance-ridge-seeded ellipsoids from "+ridgePoints.size()+" seed points...");
			final BinaryFunctionOp<byte[][], Vector3d, QuickEllipsoid> medialOptimisation = Functions.binary(opService,
					EllipsoidOptimisationStrategy.class, QuickEllipsoid.class, pixels, new Vector3d(),
					new long[]{w, h, d}, new NoEllipsoidConstrain(),parameters);
			final AtomicInteger progress = new AtomicInteger();
			final int points = ridgePoints.size();
			final List<QuickEllipsoid> ridgePointEllipsoids = ridgePoints.parallelStream()
					.peek(p -> statusService.showProgress(progress.getAndIncrement(), points))
					.map(sp -> medialOptimisation.calculate(pixels, sp)).filter(Objects::nonNull)
					.collect(toList());
			logService.info("Found " + ridgePointEllipsoids.size() + " distance-ridge-seeded ellipsoids.");
			quickEllipsoids.addAll(ridgePointEllipsoids);
		}

		if (seedOnTopologyPreserving) {
			List<Vector3d> skeletonPoints = getSkeletonPoints();
			skeletonPoints = applySkipRatio(skeletonPoints);
			addPointsToDisplay(skeletonPoints, seedImage, (byte) 1);

			statusService.showStatus("Optimising skeleton-seeded ellipsoids from "+skeletonPoints.size()+" seed points...");
			final BinaryFunctionOp<byte[][], Vector3d, QuickEllipsoid> medialOptimisation = Functions.binary(opService,
					EllipsoidOptimisationStrategy.class, QuickEllipsoid.class, pixels, new Vector3d(),
					new long[]{w, h, d}, new NoEllipsoidConstrain(),parameters);
			final AtomicInteger progress = new AtomicInteger();
			final int points = skeletonPoints.size();
			final List <QuickEllipsoid> skeletonSeededEllipsoids = skeletonPoints.parallelStream()
					.peek(p -> statusService.showProgress(progress.getAndIncrement(), points))
					.map(sp -> medialOptimisation.calculate(pixels, sp)).filter(Objects::nonNull)
					.collect(toList());
			logService.info("Found " + skeletonSeededEllipsoids.size() + " skeleton-seeded ellipsoids.");
			quickEllipsoids.addAll(skeletonSeededEllipsoids);
		}

		if(showSecondaryImages)
		{
			final DefaultLinearAxis xAxis = (DefaultLinearAxis) inputImage.axis(0);
			final DefaultLinearAxis yAxis = (DefaultLinearAxis) inputImage.axis(1);
			final DefaultLinearAxis zAxis = (DefaultLinearAxis) inputImage.axis(2);
			xAxis.setUnit(inputImage.axis(0).unit());
			yAxis.setUnit(inputImage.axis(1).unit());
			zAxis.setUnit(inputImage.axis(2).unit());
			seedPointImage = new ImgPlus<>(seedImage, inputImage.getName().split("\\.")[0]+"_seed_points", xAxis, yAxis, zAxis);
			seedPointImage.setChannelMaximum(0, 1);
			seedPointImage.setChannelMinimum(0, 0);
		}
		quickEllipsoids.sort((a, b) -> Double.compare(b.getVolume(), a.getVolume()));
		final long stop = System.currentTimeMillis();
		logService.info("Found " + quickEllipsoids.size() + " ellipsoids in " + (stop - start) + " ms");
		return quickEllipsoids;
	}

	// region --seed point finding--

	private List<Vector3d> getSkeletonPoints() {
		final ImagePlus skeleton = copyAsBinaryImagePlus(inputAsBitType);
		final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);
		final List<Vector3d> skeletonPoints = new ArrayList<>();
		final ImageStack skeletonStack = skeleton.getImageStack();
		for (int z = 0; z < skeleton.getStackSize(); z++) {
			final byte[] slicePixels = (byte[]) skeletonStack.getPixels(z + 1);
			for (int x = 0; x < skeleton.getWidth(); x++) {
				for (int y = 0; y < skeleton.getHeight(); y++) {
					if (slicePixels[y * skeleton.getWidth() + x] != 0) {
						skeletonPoints.add(new Vector3d(x, y, z));
					}
				}
			}
		}
		return skeletonPoints;
	}

	private static ImagePlus copyAsBinaryImagePlus(final ImgPlus<BitType> inputAsBitType) {
		// TODO Don't assume that 0, 1, 2 are X, Y, Z
		final int w = (int) inputAsBitType.dimension(0);
		final int h = (int) inputAsBitType.dimension(1);
		final int d = (int) inputAsBitType.dimension(2);
		final ImagePlus imagePlus = IJ.createImage(inputAsBitType.getName(), w, h, d, 8);
		final ImageStack stack = imagePlus.getStack();
		final Cursor<BitType> cursor = inputAsBitType.cursor();
		final int[] position = new int[3];
		while (cursor.hasNext()) {
			cursor.next();
			if (cursor.get().get()) {
				cursor.localize(position);
				stack.setVoxel(position[0], position[1], position[2], 0xFF);
			}
		}
		return imagePlus;
	}

	private List<Vector3d> getDistanceRidgePoints(final ImgPlus<BitType> imp) {
		final List<Vector3d> ridgePoints = (List<Vector3d>) opService.run(FindRidgePoints.class, imp, distanceThreshold);
		logService.info("Found " + ridgePoints.size() + " distance-ridge-based points");
		return ridgePoints;
	}

	private List<Vector3d> applySkipRatio(final List<Vector3d> seedPoints) {
		if (skipRatio > 1) {
			final int limit = seedPoints.size() / skipRatio;
			final Random random = new Random();
			final int skipper = random.nextInt(skipRatio);
			return Stream.iterate(skipper, i -> i + skipRatio).limit(limit).map(seedPoints::get).collect(toList());
		}
		return seedPoints;
	}

	private void addPointsToDisplay(final List<Vector3d> seedPoints, final Img<ByteType> seedImage,
									final byte i) {
		final RandomAccess<ByteType> access = seedImage.randomAccess();
		for (final Vector3d p : seedPoints) {
			access.setPosition(new int[]{(int) p.x, (int) p.y, (int) p.z});
			access.get().set(i);
		}
	}
	//endregion


	private long countAssignedVoxels(final Iterable<FloatType> ellipsoidFactorImage) {
		final LongType assignedVoxels = new LongType();
		ellipsoidFactorImage.forEach(e -> {
			if (Float.isFinite(e.get())) {
				assignedVoxels.inc();
			}
		});
		return assignedVoxels.get();
	}

	private Img<IntType> assignEllipsoidIDs(final Img<BitType> mask, final List<QuickEllipsoid> ellipsoids) {

		final Img<IntType> idImage = ArrayImgs.ints(mask.dimension(0), mask.dimension(1),weightedAverageN, mask.dimension(2));
		idImage.forEach(c -> c.setInteger(-1));

		for(int nn=0;nn<weightedAverageN;nn++) {
			final int n = nn;
			final Map<QuickEllipsoid, Integer> iDs = IntStream.range(0, ellipsoids.size()).boxed()
					.collect(toMap(ellipsoids::get, Function.identity()));
			final LongStream zRange = LongStream.range(0, mask.dimension(2));
			zRange.parallel().forEach(z -> {
				// multiply by image unit? make more intelligent bounding box?
				final List<QuickEllipsoid> localEllipsoids = ellipsoids.stream()
						.filter(e -> Math.abs(e.getCentre()[2] - z) < e.getSortedRadii()[2]).collect(toList());
				final long[] mins = {0, 0, z};
				final long[] maxs = {mask.dimension(0) - 1, mask.dimension(1) - 1, z};
				final Cursor<BitType> maskSlice = Views.interval(mask, mins, maxs).localizingCursor();
				colourSlice(idImage, maskSlice, localEllipsoids, iDs, n);
			});
		}
		return idImage;
	}

	private void colourSlice(final RandomAccessible<IntType> idImage, final Cursor<BitType> mask,
							 final Collection<QuickEllipsoid> localEllipsoids, final Map<QuickEllipsoid, Integer> iDs,
							 final int nLargest) {
		final long[] coordinates = new long[mask.numDimensions()];
		while (mask.hasNext()) {
			mask.fwd();
			if (!mask.get().get()) {
				continue;
			}
			mask.localize(coordinates);
			final Vector3d point = new Vector3d(coordinates[0] + 0.5, coordinates[1] + 0.5, coordinates[2] + 0.5);
			colourID(localEllipsoids, idImage, point, iDs, nLargest);
		}
	}

	private void colourID(final Collection<QuickEllipsoid> localEllipsoids,
						  final RandomAccessible<IntType> ellipsoidIdentityImage, final Vector3dc point,
						  final Map<QuickEllipsoid, Integer> iDs, int nLargest) {
		final Optional<QuickEllipsoid> candidate = localEllipsoids.stream()
				.filter(e -> e.contains(point.x(), point.y(), point.z())).skip(nLargest).findFirst();
		if (!candidate.isPresent()) {
			return;
		}
		final RandomAccess<IntType> eIDRandomAccess = ellipsoidIdentityImage.randomAccess();
		eIDRandomAccess.setPosition(new long[]{(long) point.x(), (long) point.y(), (long) nLargest, (long) point.z()});
		final QuickEllipsoid ellipsoid = candidate.get();
		eIDRandomAccess.get().set(iDs.get(ellipsoid));
	}

	private void addResults(final int totalEllipsoids, final double fillingPercentage) {
		final String label = inputImage.getName();
		SharedTable.add(label, "filling percentage", fillingPercentage);
		SharedTable.add(label, "number of ellipsoids found in total", totalEllipsoids);
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}

		if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
			cancelMacroSafe(this, NOT_3D_IMAGE);
			return;
		}

		if (!ElementUtil.isBinary(inputImage)) {
			cancelMacroSafe(this, NOT_BINARY);
		}
	}

	// endregion
	static <T extends RealType<T>> byte[][] imgPlusToByteArray(final ImgPlus<T> imgPlus) {
		final int w = (int) imgPlus.dimension(0);
		final int h = (int) imgPlus.dimension(1);
		final int d = (int) imgPlus.dimension(2);

		final byte[][] pixels = new byte[d][w * h];
		final Cursor<T> cursor = imgPlus.localizingCursor();
		final int[] position = new int[imgPlus.numDimensions()];
		final T ZERO = imgPlus.firstElement().createVariable();
		ZERO.setZero();
		while (cursor.hasNext()) {
			cursor.fwd();
			if (!cursor.get().valueEquals(ZERO)) {
				cursor.localize(position);
				pixels[position[2]][position[1] * w + position[0]] = (byte) 0xFF;
			}
		}

		return pixels;
	}
}
