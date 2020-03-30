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
import static java.util.stream.Collectors.toMap;
import static net.imglib2.roi.Regions.countTrue;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import net.imagej.axis.DefaultLinearAxis;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.bonej.ops.ellipsoid.*;
import org.bonej.ops.ellipsoid.constrain.NoEllipsoidConstrain;
import org.bonej.ops.skeletonize.FindRidgePoints;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.ImageStack;
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
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.*;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

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
 * @see <a href="http://dx.doi.org/10.3389/fendo.2015.00015">
 *      "The ellipsoid factor for quantification of rods, plates, and intermediate forms in 3D geometries"
 *      Frontiers in Endocrinology (2015)</a>
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor")
public class EllipsoidFactorWrapper extends ContextCommand {

	private static final String NO_ELLIPSOIDS_FOUND = "No ellipsoids were found - try modifying input parameters.";

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
	private CommandService commandService;


	//main input image
	@SuppressWarnings("unused")
	@Parameter
	private ImgPlus<UnsignedIntType> inputImgPlus;

	//algorithm parameters
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String setup = "Setup";
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
	@Parameter(label = "Minimum semi axis", description = "Minimum length for the longest semi-axis needed for an ellipsoid to be valid. Defaults to unit voxel", min="0")
	private double minimumSemiAxis = 1.0;

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

	@Parameter(label = "Show secondary images")
	private boolean showSecondaryImages = false;

	@Parameter (label = "EF Output Images", type = ItemIO.OUTPUT)
	private List<ImgPlus> ellipsoidFactorOutputImages;
	@Parameter(label = "Seed Points", type = ItemIO.OUTPUT)
	private ImgPlus<ByteType> seedPointImage;// 0=not a seed, 1=medial seed

	private EllipsoidFactorErrorTracking errorTracking;
	/**
	 * The EF results in a {@link Table}, null if there are no results
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<Double>, Double> resultsTable;

	@Override
	public void run() {
		final ImgPlus<BitType> inputAsBitType = Common.toBitTypeImgPlus(opService, inputImgPlus);

		int totalEllipsoids = 0;
		List<ImgPlus> outputList = null;
		errorTracking = new EllipsoidFactorErrorTracking(opService);

		int counter = 0;
		for(int i = 0; i<runs; i++) {
			//optimise ellipsoids
			final List<QuickEllipsoid> ellipsoids = runEllipsoidOptimisation(inputImgPlus);
			if (ellipsoids.isEmpty()) {
				cancel(NO_ELLIPSOIDS_FOUND);
				return;
			}

			//assign one ellipsoid to each FG voxel
			statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
			long start = System.currentTimeMillis();
			final Img<IntType> ellipsoidIdentityImage = assignEllipsoidIDs(inputAsBitType, ellipsoids);
			long stop = System.currentTimeMillis();
			logService.info("Found maximal ellipsoids in " + (stop - start) + " ms");

			//add result of this run to overall result
			//TODO do not match Op every time
			final List<ImgPlus> currentOutputList = (List<ImgPlus>) opService.run(EllipsoidFactorOutputGenerator.class, ellipsoidIdentityImage,
					ellipsoids, showSecondaryImages);

			if(outputList!=null)
			{
				outputList = sumOutput(outputList, currentOutputList, (double) counter);
				final Map<String, Double> errors = errorTracking.calculate(outputList.get(0));
				errors.forEach((stat,value) -> logService.info(stat+": "+value.toString()));
				SharedTable.add(inputImgPlus.getName(),"median change "+i,errors.get("Median"));
				SharedTable.add(inputImgPlus.getName(),"maximum change "+i,errors.get("Max"));
				counter++;
			}
			else{
				outputList = currentOutputList;
				SharedTable.add(inputImgPlus.getName(),"median change "+i,2);
				SharedTable.add(inputImgPlus.getName(),"maximum change "+i,2);
				counter++;
			}
			totalEllipsoids += ellipsoids.size();
		}

		if(runs>1)
		{
			outputList = divideOutput(outputList, runs);
		}

		ellipsoidFactorOutputImages = outputList;

		//calibrate output images
		final double voxelVolume = Math.pow(inputImgPlus.axis(0).calibratedValue(1),3);
		for(ImgPlus imgPlus : ellipsoidFactorOutputImages)
		{
			if(imgPlus.numDimensions()>=3)
			{
				// set spatial axis for first 3 dimensions (ID is 4d)
				for(int dim = 0; dim<3; dim++)
				{
					imgPlus.setAxis(inputImgPlus.axis(dim), dim);
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
			double value = cursor.get().getRealDouble();
			if(!Double.isNaN(value)){
				stats.addValue(value);
			}
		}
		final double median = stats.getPercentile(50);
		SharedTable.add(inputImgPlus.getName(), "Median EF", median);
		final double max = stats.getMax();
		SharedTable.add(inputImgPlus.getName(), "Max EF", max);
		final double min = stats.getMin();
		SharedTable.add(inputImgPlus.getName(), "Min EF", min);
		addResults(totalEllipsoids, fillingPercentage);
		statusService.showStatus("Ellipsoid Factor completed");

	}

	private List<ImgPlus> divideOutput(List<ImgPlus> outputList, int repetitions) {
		List<ImgPlus> divided = new ArrayList<>();
		for(int i=0; i<outputList.size(); i++)
		{
			final Img division = (Img) opService.math().divide(opService.convert().float32(outputList.get(i).getImg()), new FloatType(repetitions));
			final ImgPlus divisionImgPlus = new ImgPlus<>(division, outputList.get(i));
			divisionImgPlus.setChannelMaximum(0, outputList.get(i).getChannelMaximum(0));
			divisionImgPlus.setChannelMinimum(0, outputList.get(i).getChannelMinimum(0));
			divided.add(divisionImgPlus);
		}
		return divided;
	}

	private List<ImgPlus> sumOutput(List<ImgPlus> outputList, List<ImgPlus> currentOutputList, double nSum) {
		List<ImgPlus> summed = new ArrayList<>();
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

				double p = previousVal.get().getRealDouble();
				double c = currentVal.get().getRealDouble();
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
		final OptimisationParameters parameters = new OptimisationParameters(vectorIncrement, nVectors, contactSensitivity, maxIterations, maxDrift, minimumSemiAxis);
		if (seedOnDistanceRidge) {
			// noinspection unchecked
			final ImgPlus<BitType> inputAsBitType = Common.toBitTypeImgPlus(opService, inputImgPlus);
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

		final DefaultLinearAxis xAxis = (DefaultLinearAxis) inputImgPlus.axis(0);
		final DefaultLinearAxis yAxis = (DefaultLinearAxis) inputImgPlus.axis(1);
		final DefaultLinearAxis zAxis = (DefaultLinearAxis) inputImgPlus.axis(2);
		seedPointImage = new ImgPlus<>(seedImage, "Seed points", xAxis, yAxis, zAxis);
		seedPointImage.setChannelMaximum(0, 1);
		seedPointImage.setChannelMinimum(0, 0);
		quickEllipsoids.sort((a, b) -> Double.compare(b.getVolume(), a.getVolume()));
		long stop = System.currentTimeMillis();
		logService.info("Found " + quickEllipsoids.size() + " ellipsoids in " + (stop - start) + " ms");
		return quickEllipsoids;
	}

	// region --seed point finding--

	private List<Vector3d> getSkeletonPoints() {
		ImagePlus skeleton;
		final List<Vector3d> skeletonPoints = new ArrayList<>();

		try {
			final CommandModule skeletonizationModule =
					commandService.run("org.bonej.wrapperPlugins.SkeletoniseWrapper", true).get();
			skeleton = (ImagePlus) skeletonizationModule.getOutput("skeleton");
		} catch (InterruptedException | ExecutionException e) {
			logService.error(e);
			return skeletonPoints;
		}

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
		skeleton.close();
		return skeletonPoints;
	}

	private List<Vector3d> getDistanceRidgePoints(ImgPlus<BitType> imp) {
		List<Vector3d> ridgePoints = (List<Vector3d>) opService.run(FindRidgePoints.class, imp, distanceThreshold);
		logService.info("Found " + ridgePoints.size() + " distance-ridge-based points");
		return ridgePoints;
	}

	private List<Vector3d> applySkipRatio(List<Vector3d> seedPoints) {
		if (skipRatio > 1) {
			int limit = seedPoints.size() / skipRatio;
			final Random random = new Random();
			final int skipper = random.nextInt(skipRatio);
			seedPoints = Stream.iterate(skipper, i -> i + skipRatio).limit(limit).map(seedPoints::get).collect(toList());
		}
		return seedPoints;
	}

	private void addPointsToDisplay(List<Vector3d> seedPoints, ArrayImg<ByteType, ByteArray> seedImage, byte i) {
		final ArrayRandomAccess<ByteType> access = seedImage.randomAccess();
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
							 final Collection<QuickEllipsoid> localEllipsoids, final Map<QuickEllipsoid, Integer> iDs, int nlargest) {
		while (mask.hasNext()) {
			mask.fwd();
			if (!mask.get().get()) {
				continue;
			}
			final long[] coordinates = new long[3];
			mask.localize(coordinates);
			final Vector3d point = new Vector3d(coordinates[0] + 0.5, coordinates[1] + 0.5, coordinates[2] + 0.5);
			colourID(localEllipsoids, idImage, point, iDs, nlargest);
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

	private void addResults(final int totalEllipsoids, double fillingPercentage) {
		final String label = inputImgPlus.getName();
		SharedTable.add(label, "filling percentage", fillingPercentage);
		SharedTable.add(label, "number of ellipsoids found in total", totalEllipsoids);
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		} else {
			cancel(NO_ELLIPSOIDS_FOUND);
		}
	}
	// endregion
	static byte[][] imgPlusToByteArray(ImgPlus<UnsignedByteType> imp) {
		final int w = (int) imp.dimension(0);
		final int h = (int) imp.dimension(1);
		final int d = (int) imp.dimension(2);

		final byte[][] pixels = new byte[d][w * h];
		final IntStream zRange = IntStream.range(0, d);
		zRange.forEach(z -> {
			final long[] minValues = {0, 0, z};
			final long[] maxValues = {w - 1, h - 1, z};
			final Cursor<UnsignedByteType> sliceCursor = Views.interval(imp, minValues, maxValues).localizingCursor();
			while (sliceCursor.hasNext()) {
				sliceCursor.fwd();
				int[] position = new int[3];
				sliceCursor.localize(position);
				if (sliceCursor.get().get()!=0) {
					pixels[position[2]][position[1] * w + position[0]] = (byte) 255;
				}
			}
		});
		return pixels;
	}
}
