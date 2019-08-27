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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.bonej.ops.ellipsoid.EllipsoidOptimisationStrategy;
import org.bonej.ops.ellipsoid.OptimisationParameters;
import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.bonej.ops.ellipsoid.constrain.NoEllipsoidConstrain;
import org.bonej.ops.skeletonize.FindRidgePoints;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.joml.Vector3d;
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
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * ImageJ plugin to describe the local geometry of a binary image in an
 * oblate/prolate spheroid space. Uses Skeletonize3D to generate a 3D skeleton,
 * the points of which are used as centres for maximally inscribed ellipsoids.
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

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor 2")
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
	@Parameter(label = "Sampling_increment", description = "Increment for vector searching in real units. Default is ~Nyquist sampling of a unit pixel.", min="0.01", max = "0.99")
	private double vectorIncrement = 1 / 2.3;
	@Parameter(label = "Skeleton_points per ellipsoid", description = "Number of skeleton points per ellipsoid. Sets the granularity of the ellipsoid fields.", min="1")
	private int skipRatio = 50;
	@Parameter(label = "Contact sensitivity", description = "Number of contacts with surface required to determine collision.", min = "1")
	private int contactSensitivity = 1;
	@Parameter(label = "Maximum_iterations", description = "Maximum currentIteration to try improving ellipsoid fit before stopping.", min="10")
	private int maxIterations = 100;
	@Parameter(label = "Maximum_drift", description = "maximum distance ellipsoid may drift from seed point. Defaults to unit voxel diagonal length", min="0")
	private double maxDrift = Math.sqrt(3);

	//averaging / smoothing
	@Parameter(label = "Repetitions", description = "Number of currentIteration over which to average EF value", min="1")
	private int runs = 1;
	@Parameter(label = "Average over largest n ellipsoids", min="1")
	private int weightedAverageN = 1;
	
	
	//what seed points should I use?
	@Parameter(label = "Seed points based on distance ridge", description = "tick this if you would like ellipsoids to be seeded based on the foreground distance ridge")
	private boolean seedOnDistanceRidge = true;
	@Parameter(label = "Seed points on topology-preserving skeletonization ", description = "tick this if you would like ellipsoids to be seeded on the topology-preserving skeletonization (\"Skeletonize3D\").")
	private boolean seedOnTopologyPreserving = false;

	@Parameter(label = "Show secondary images")
	private boolean showSecondaryImages = false;

	//output parameters
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String outputs = "Outputs";
	@Parameter (label = "EF Output Images", type = ItemIO.OUTPUT)
	private List<ImgPlus> ellipsoidFactorOutputImages;
	@Parameter(label = "Seed Points", type = ItemIO.OUTPUT)
	private ImgPlus<ByteType> seedPointImage;// 0=not a seed, 1=medial seed

	/**
	 * The EF results in a {@link Table}, null if there are no results
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<Double>, Double> resultsTable;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String note = "Ellipsoid Factor is beta software.\n" + "Please report your experiences to the user group:\n"
			+ "http://forum.image.sc/tags/bonej";

	@Override
	public void run() {
		final ImgPlus<BitType> inputAsBitType = Common.toBitTypeImgPlus(opService, inputImgPlus);

		int totalEllipsoids = 0;
		List<ImgPlus> outputList = new ArrayList<>();

		final QuickEllipsoid[][] ellipsoidMatrix = new QuickEllipsoid[runs][];
		for(int i = 0; i<runs; i++) {
			//optimise ellipsoids
			ellipsoidMatrix[i] = runEllipsoidOptimisation(inputImgPlus);
			if (ellipsoidMatrix[i].length == 0) {
				cancel(NO_ELLIPSOIDS_FOUND);
				return;
			}
		}

		//assign one ellipsoid per run to each FG voxel
		statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
		long start = System.currentTimeMillis();
		outputList.add(new ImgPlus(ArrayImgs.floats(inputAsBitType.dimension(0),inputAsBitType.dimension(1),inputAsBitType.dimension(2)),"Ellipsoid Factor"));
		assignEllipsoidIDs(outputList, inputAsBitType, ellipsoidMatrix);
		long stop = System.currentTimeMillis();
		logService.info("Found maximal ellipsoids in " + (stop - start) + " ms");

		ellipsoidFactorOutputImages = outputList;

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

	/**
	 * Using skeleton points as seeds, propagate along each vector until a boundary
	 * is hit. Use the resulting cloud of boundary points as input into an ellipsoid
	 * fit.
	 *
	 * @param imp
	 *            input image
	 * @return array of fitted ellipsoids
	 */
	private QuickEllipsoid[] runEllipsoidOptimisation(final ImgPlus imp) {
		long start = System.currentTimeMillis();

		final int w = (int) imp.dimension(0);
		final int h = (int) imp.dimension(1);
		final int d = (int) imp.dimension(2);

		final byte[][] pixels = imgPlusToByteArray(imp);
		final ArrayImg<ByteType, ByteArray> seedImage = ArrayImgs.bytes(w, h, d);
		QuickEllipsoid[] quickEllipsoids = new QuickEllipsoid[]{};
		final OptimisationParameters parameters = new OptimisationParameters(vectorIncrement, nVectors, contactSensitivity, maxIterations, maxDrift);
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
			quickEllipsoids = ridgePoints.parallelStream().map(sp -> medialOptimisation.calculate(pixels, sp))
					.filter(Objects::nonNull).toArray(QuickEllipsoid[]::new);
			Arrays.sort(quickEllipsoids, (a, b) -> Double.compare(b.getVolume(), a.getVolume()));
			logService.info("Found " + quickEllipsoids.length + " distance-ridge-seeded ellipsoids.");
		}

		if (seedOnTopologyPreserving) {
			List<Vector3d> skeletonPoints = getSkeletonPoints();
			skeletonPoints = applySkipRatio(skeletonPoints);
			addPointsToDisplay(skeletonPoints, seedImage, (byte) 1);

			statusService.showStatus("Optimising skeleton-seeded ellipsoids from "+skeletonPoints.size()+" seed points...");
			final BinaryFunctionOp<byte[][], Vector3d, QuickEllipsoid> medialOptimisation = Functions.binary(opService,
					EllipsoidOptimisationStrategy.class, QuickEllipsoid.class, pixels, new Vector3d(),
					new long[]{w, h, d}, new NoEllipsoidConstrain(),parameters);
			QuickEllipsoid[] skeletonSeededEllipsoids = skeletonPoints.parallelStream().map(sp -> medialOptimisation.calculate(pixels, sp))
					.filter(Objects::nonNull).toArray(QuickEllipsoid[]::new);
			Arrays.sort(quickEllipsoids, (a, b) -> Double.compare(b.getVolume(), a.getVolume()));
			logService.info("Found " + quickEllipsoids.length + " skeleton-seeded ellipsoids.");
			quickEllipsoids = Stream.concat(Arrays.stream(quickEllipsoids), Arrays.stream(skeletonSeededEllipsoids))
					.toArray(QuickEllipsoid[]::new);
		}

		seedPointImage = new ImgPlus<>(seedImage, "Seed points");
		seedPointImage.setChannelMaximum(0, 1);
		seedPointImage.setChannelMinimum(0, 0);
		Arrays.sort(quickEllipsoids, (a, b) -> Double.compare(b.getVolume(), a.getVolume()));
		long stop = System.currentTimeMillis();
		logService.info("Found " + quickEllipsoids.length + " ellipsoids in " + (stop - start) + " ms");
		return quickEllipsoids;
	}

	// region --seed point finding--

	private List<Vector3d> getSkeletonPoints() {
		ImagePlus skeleton = null;
		try {
			final CommandModule skeletonizationModule = commandService.run("org.bonej.wrapperPlugins.SkeletoniseWrapper", true).get();
			skeleton = (ImagePlus) skeletonizationModule.getOutput("skeleton");
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		final ImageStack skeletonStack = skeleton.getImageStack();
		final List<Vector3d> skeletonPoints = new ArrayList<>();
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

	private List<Vector3d> getDistanceRidgePoints(ImgPlus<BitType> imp) {
		List<Vector3d> ridgePoints = (List<Vector3d>) opService.run(FindRidgePoints.class, imp);
		logService.info("Found " + ridgePoints.size() + " distance-ridge-based points");
		return ridgePoints;
	}

	private List<Vector3d> applySkipRatio(List<Vector3d> seedPoints) {
		if (skipRatio > 1) {
			int limit = seedPoints.size() / skipRatio + Math.min(seedPoints.size() % skipRatio, 1);
			seedPoints = Stream.iterate(0, i -> i + skipRatio).limit(limit).map(seedPoints::get).collect(toList());
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

	private static double computeWeightedEllipsoidFactor(final QuickEllipsoid[][] ellipsoid) {
		double EF = 0;
		for(int run=0; run<ellipsoid.length; run++) {
			double volSum = 0;
			for (int weight = 0; weight < ellipsoid[run].length; weight++) {
				QuickEllipsoid e = ellipsoid[run][weight];
				final double[] sortedRadii = e.getSortedRadii();
				final double volume = e.getVolume();
				double weightedEF = (sortedRadii[0] / sortedRadii[1] - sortedRadii[1] / sortedRadii[2]) * volume;
				volSum+=volume;
				EF+=weightedEF;
			}
			EF/=volSum;
		}
		EF/=ellipsoid.length;
		return EF;
	}

	private void assignEllipsoidIDs(List<ImgPlus> outputList, final Img<BitType> inputBinaryImage, final QuickEllipsoid[][] ellipsoids) {

		final LongStream zRange = LongStream.range(0, inputBinaryImage.dimension(2));
		zRange.parallel().forEach(z -> {

			//get coordinates
			final long[] mins = {0, 0, z};
			final long[] maxs = {inputBinaryImage.dimension(0) - 1, inputBinaryImage.dimension(1) - 1, z};
			final Cursor<BitType> currentSlice = Views.interval(inputBinaryImage, mins, maxs).localizingCursor();

			//get runs by weighted array
			while(currentSlice.hasNext())
			{
				currentSlice.fwd();
				long[] coordinates = new long[3];
				currentSlice.localize(coordinates);
				final Vector3d point = new Vector3d(coordinates[0] + 0.5, coordinates[1] + 0.5, coordinates[2] + 0.5);
				final QuickEllipsoid[][] firstNContainingEllipsoidsForEachRun = new QuickEllipsoid[runs][];

				for(int r=0; r<runs; r++)
				{
					//filter out impossible ellipsoids for this z
					final List<QuickEllipsoid> localEllipsoids = Arrays.stream(ellipsoids[r]).filter(e -> Math.abs(e.getCentre()[2] - z) < e.getSortedRadii()[2]).collect(toList());
					firstNContainingEllipsoidsForEachRun[r] = localEllipsoids.stream()
						.filter(e -> e.contains(point.x(), point.y(), point.z())).limit(weightedAverageN).toArray(QuickEllipsoid[]::new);
				}
				setOutputForVoxel(outputList, coordinates, firstNContainingEllipsoidsForEachRun);
			}
		});
	}

	private void setOutputForVoxel(List<ImgPlus> imgPluses, long[] coordinates, QuickEllipsoid[][] firstNContainingEllipsoidsForEachRun) {
		for(ImgPlus i : imgPluses) {
			final RandomAccess<? extends RealType> randomAccess = i.randomAccess();
			randomAccess.setPosition(coordinates);
			randomAccess.get().setReal(computeWeightedEllipsoidFactor(firstNContainingEllipsoidsForEachRun));
		}
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
