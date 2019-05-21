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
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.bonej.ops.ellipsoid.EllipsoidOptimisationStrategy;
import org.bonej.ops.ellipsoid.OptimisationParameters;
import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.bonej.ops.ellipsoid.constrain.AnchorEllipsoidConstrain;
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
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;
import org.scijava.ui.UIService;

import net.imagej.ImgPlus;
import net.imagej.display.ColorTables;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.*;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.*;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
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

	// Several ellipsoids may fall in same bin if this is too small a number!
	// This will be ignored!
	private static final long FLINN_PLOT_DIMENSION = 501;
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

	//main input image
	@SuppressWarnings("unused")
	@Parameter
	private ImgPlus<UnsignedIntType> inputImgPlus;

	//algorithm parameters
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String setup = "Setup";
	@Parameter(label = "Vectors")
	int nVectors = 100;
	@Parameter(label = "Sampling_increment", description = "Increment for vector searching in real units. Default is ~Nyquist sampling of a unit pixel.")
	private double vectorIncrement = 1 / 2.3;
	@Parameter(label = "Skeleton_points per ellipsoid", description = "Number of skeleton points per ellipsoid. Sets the granularity of the ellipsoid fields.")
	private int skipRatio = 50;
	@Parameter(label = "Contact sensitivity")
	private int contactSensitivity = 1;
	@Parameter(label = "Maximum_iterations", description = "Maximum iterations to try improving ellipsoid fit before stopping.")
	private int maxIterations = 100;
	@Parameter(label = "Maximum_drift", description = "maximum distance ellipsoid may drift from seed point. Defaults to unit voxel diagonal length")
	private double maxDrift = Math.sqrt(3);

	@Parameter(label = "Repetitions", description = "Number of repetitions over which to average EF value")
	private int repetitions = 1;

	//what seed points should I use?
	@Parameter(label = "Seed points on distance ridge", description = "tick this if you would like ellipsoids to be seeded on the foreground distance ridge")
	private boolean seedOnDistanceRidge = true;
	@Parameter(label = "Seed points on unoccupied surfaces", description = "tick this if you would like ellipsoids to be seeded on surface voxels that are not inside an ellipsoid")
	private boolean seedOnSurface = false;

	//output parameters
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String outputs = "Outputs";
	@Parameter(label = "Show secondary images")
	private boolean showSecondaryImages = false;
	@Parameter(label = "Gaussian_sigma")
	private double sigma = 2;

	//output variables
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
	@Parameter(label = "Seed Points", type = ItemIO.OUTPUT)
	private ImgPlus<ByteType> seedPointImage;// 0=not a seed, 1=medial seed && not anchor seed, 2=surface anchor seed

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
		final EFOutput output = new EFOutput(repetitions);

		for(int i=0; i<repetitions;i++) {
			final QuickEllipsoid[] ellipsoids = runEllipsoidOptimisation(inputImgPlus);
			if (ellipsoids.length == 0) {
				cancel(NO_ELLIPSOIDS_FOUND);
				return;
			}
			statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
			long start = System.currentTimeMillis();
			final Img<IntType> ellipsoidIdentityImage = assignEllipsoidIDs(inputAsBitType, Arrays.asList(ellipsoids));
			long stop = System.currentTimeMillis();
			logService.info("Found maximal ellipsoids in " + (stop - start) + " ms");

			createEFOutputs(ellipsoidIdentityImage,ellipsoids);
			output.addImage(efImage.getImg(),i);
			totalEllipsoids += ellipsoids.length;
		}

		efImage = new ImgPlus<>(output.getAveragedEFImage());
		efImage.setChannelMaximum(0, 1);
		efImage.setChannelMinimum(0, -1);
		efImage.initializeColorTables(1);
		efImage.setColorTable(ColorTables.FIRE, 0);

		final double numberOfForegroundVoxels = countTrue(inputAsBitType);
		final double numberOfAssignedVoxels = countAssignedVoxels(efImage.getImg());
		final double fillingPercentage = 100.0 * (numberOfAssignedVoxels / numberOfForegroundVoxels);
		addResults(totalEllipsoids, fillingPercentage);
		statusService.showStatus("Ellipsoid Factor completed");
	}

	private static float computeEllipsoidFactor(final QuickEllipsoid ellipsoid) {
		final double[] sortedRadii = ellipsoid.getSortedRadii();
		return (float) (sortedRadii[0] / sortedRadii[1] - sortedRadii[1] / sortedRadii[2]);
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

			statusService.showStatus("Optimising centrally-seeded ellipsoids from "+ridgePoints.size()+" ridge points...");
			final BinaryFunctionOp<byte[][], Vector3d, QuickEllipsoid> medialOptimisation = Functions.binary(opService,
					EllipsoidOptimisationStrategy.class, QuickEllipsoid.class, pixels, new Vector3d(),
					new long[]{w, h, d}, new NoEllipsoidConstrain(),parameters);
			quickEllipsoids = ridgePoints.parallelStream().map(sp -> medialOptimisation.calculate(pixels, sp))
					.filter(Objects::nonNull).toArray(QuickEllipsoid[]::new);
			Arrays.sort(quickEllipsoids, (a, b) -> Double.compare(b.getVolume(), a.getVolume()));
			logService.info("Found " + quickEllipsoids.length + " centrally-seeded ellipsoids.");
		}
		if (seedOnSurface) {
			List<Vector3d> anchors = getAnchors(quickEllipsoids, imp);
			anchors = applySkipRatio(anchors);
			addPointsToDisplay(anchors, seedImage, (byte) 2);
			logService.info("Found " + anchors.size() + " anchors.");

			statusService.showStatus("Optimising surface-seeded ellipsoids...");
			final BinaryFunctionOp<byte[][], Vector3d, QuickEllipsoid> surfaceOptimisation = Functions.binary(opService,
					EllipsoidOptimisationStrategy.class, QuickEllipsoid.class, pixels, new Vector3d(),
					new long[]{w, h, d}, new AnchorEllipsoidConstrain(),parameters);
			final QuickEllipsoid[] anchoredEllipsoids = anchors.parallelStream()
					.map(a -> surfaceOptimisation.calculate(pixels, a)).filter(Objects::nonNull)
					.toArray(QuickEllipsoid[]::new);
			logService.info("Found " + anchoredEllipsoids.length + " surface-seeded ellipsoids.");

			quickEllipsoids = Stream.concat(Arrays.stream(quickEllipsoids), Arrays.stream(anchoredEllipsoids))
					.toArray(QuickEllipsoid[]::new);
		}

		seedPointImage = new ImgPlus<>(seedImage, "Seed points");
		seedPointImage.setChannelMaximum(0, 2);
		Arrays.sort(quickEllipsoids, (a, b) -> Double.compare(b.getVolume(), a.getVolume()));
		long stop = System.currentTimeMillis();
		logService.info("Found " + quickEllipsoids.length + " ellipsoids in " + (stop - start) + " ms");
		return quickEllipsoids;
	}

	private class EFOutput {
		private int repetitions;
		private Img<FloatType> ellipsoidFactorImage;
		private Img<FloatType> currentAverage;
		private Img<FloatType> previousAverage;

		EFOutput(int reps) {
			repetitions = reps;
			ellipsoidFactorImage = null;
		}

		public void addImage(Img<FloatType> ef, int currentRep){
			if(repetitions>0 && ellipsoidFactorImage!=null) {
					if(currentRep>1) {
						previousAverage = (Img) opService.math().divide(ellipsoidFactorImage, new FloatType(currentRep - 1));
					}
					else
					{
						previousAverage = ellipsoidFactorImage;
					}

				ellipsoidFactorImage = (Img) opService.math().add(ef, (IterableInterval<FloatType>) ellipsoidFactorImage);
					currentAverage = (Img) opService.math().divide(ellipsoidFactorImage, new FloatType(currentRep));
					currentAverage = (Img) opService.math().subtract(previousAverage, (IterableInterval<FloatType>) currentAverage);
					final Cursor<FloatType> cursor = currentAverage.cursor();
					while(cursor.hasNext())
					{
						final float next = cursor.next().get();
						if(next < 0){
							cursor.get().setReal(-next);
						}
					}
					cursor.reset();
					double max = 0;
					double min = 2;
					double mean = 0;
					double count = 0;
					while(cursor.hasNext())
					{
						final float next = cursor.next().get();
						if(Double.isNaN(next))
							continue;
						if(next>max){
							max = next;
						}
						if(next<min)
						{
							min = next;
						}
						mean += next;
						count++;
					}
					mean /= count;

					logService.info("mean change: "+mean);
					logService.info("max change: "+max);
					logService.info("min change: "+min);
			}
			else
			{
				ellipsoidFactorImage = ef;
			}
		}

		public Img<FloatType> getAveragedEFImage(){
			if(repetitions>1) {
				return (Img<FloatType>) opService.math().divide(ellipsoidFactorImage, new FloatType(repetitions));
			}
			else return ellipsoidFactorImage;
		}

	}

	// region --seed point finding--
	/**
	 * This method finds the anchor points in the input image. Anchor points are
	 * defined as centres of foreground pixels which have at least one background
	 * neighbour and are not contained in any of the ellipsoids.
	 *
	 * @param ellipsoids
	 *            Array of QuickEllipsoid that excludes candidate anchor points
	 * @param inputImgPlus
	 *            ImgPlus in which to find anchor points
	 * @return the list of anchor points
	 */
	static List<Vector3d> getAnchors(final QuickEllipsoid[] ellipsoids, final ImgPlus<UnsignedByteType> inputImgPlus) {
		final List<Vector3d> anchors = new ArrayList<>();

		final Interval inputShrunkByOne = Intervals.expand(inputImgPlus, -1);
		final IntervalView<UnsignedByteType> source = Views.interval(inputImgPlus, inputShrunkByOne);
		final Cursor<UnsignedByteType> centre = Views.iterable(source).localizingCursor();
		final RectangleShape shape = new RectangleShape(1, true);

		for (final Neighborhood<UnsignedByteType> localNeighborhood : shape.neighborhoods(source)) {
			// (the center cursor runs over the image in the same iteration order as
			// neighborhood)
			final UnsignedByteType centerValue = centre.next();
			if (centerValue.get() == 0) {
				continue;
			}
			long[] position = new long[3];
			centre.localize(position);

			for (final UnsignedByteType value : localNeighborhood) {
				if (value.get() == 0) {
					final double[] voxelCentre = new double[]{position[0] + 0.5, position[1] + 0.5, position[2] + 0.5};
					if (Arrays.stream(ellipsoids)
							.anyMatch(e -> e.contains(voxelCentre[0], voxelCentre[1], voxelCentre[2]))) {
						continue;
					}
					anchors.add(new Vector3d(voxelCentre[0], voxelCentre[1], voxelCentre[2]));
					break;
				}
			}

		}
		return anchors;
	}

	private List<Vector3d> getDistanceRidgePoints(ImgPlus<BitType> imp) {
		List<Vector3d> ridgePoints = (List<Vector3d>) opService.run(FindRidgePoints.class, imp);
		logService.info("Found " + ridgePoints.size() + " skeleton points");
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

	// region -- output methods --
	private void createAToBImage(final double[] aBRatios, final IterableInterval<IntType> ellipsoidIDs) {
		final Img<FloatType> aToBImage = createNaNCopy();
		mapValuesToImage(aBRatios, ellipsoidIDs, aToBImage);
		aToBAxisRatioImage = new ImgPlus<>(aToBImage, "a/b");
		aToBAxisRatioImage.setChannelMaximum(0, 1.0f);
		aToBAxisRatioImage.setChannelMinimum(0, 0.0f);
	}

	private void createBToCImage(final double[] bCRatios, final IterableInterval<IntType> ellipsoidIDs) {
		final Img<FloatType> bToCImage = createNaNCopy();
		mapValuesToImage(bCRatios, ellipsoidIDs, bToCImage);
		bToCAxisRatioImage = new ImgPlus<>(bToCImage, "b/c");
		bToCAxisRatioImage.setChannelMaximum(0, 1.0f);
		bToCAxisRatioImage.setChannelMinimum(0, 0.0f);
	}

	private void mapValuesToImage(final double[] values, final IterableInterval<IntType> ellipsoidIdentityImage,
			final RandomAccessible<FloatType> ellipsoidFactorImage) {
		final RandomAccess<FloatType> ef = ellipsoidFactorImage.randomAccess();
		final Cursor<IntType> id = ellipsoidIdentityImage.localizingCursor();
		final long[] position = new long[3];
		while (id.hasNext()) {
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

	private void createEFImage(final Collection<QuickEllipsoid> ellipsoids,
			final IterableInterval<IntType> ellipsoidIDs) {
		final Img<FloatType> ellipsoidFactorImage = createNaNCopy();
		final double[] ellipsoidFactors = ellipsoids.parallelStream()
				.mapToDouble(EllipsoidFactorWrapper::computeEllipsoidFactor).toArray();
		mapValuesToImage(ellipsoidFactors, ellipsoidIDs, ellipsoidFactorImage);
		efImage = new ImgPlus<>(ellipsoidFactorImage, "EF");
		efImage.setChannelMaximum(0, 1);
		efImage.setChannelMinimum(0, -1);
		efImage.initializeColorTables(1);
		efImage.setColorTable(ColorTables.FIRE, 0);
	}

	private void createFlinnPeakPlot(final double[] aBRatios, final double[] bCRatios,
			final Img<IntType> ellipsoidIDs) {
		Img<FloatType> flinnPeakPlot = ArrayImgs.floats(FLINN_PLOT_DIMENSION, FLINN_PLOT_DIMENSION);
		final RandomAccess<FloatType> flinnPeakPlotRA = flinnPeakPlot.randomAccess();
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
			final long x = Math.round(aBRatios[localMaxEllipsoidID] * (FLINN_PLOT_DIMENSION - 1));
			final long y = Math.round(bCRatios[localMaxEllipsoidID] * (FLINN_PLOT_DIMENSION - 1));
			flinnPeakPlotRA.setPosition(new long[]{x, FLINN_PLOT_DIMENSION - y - 1});
			final float currentValue = flinnPeakPlotRA.get().getRealFloat();
			flinnPeakPlotRA.get().set(currentValue + 1.0f);
		}
		if (sigma > 0.0) {
			flinnPeakPlot = (Img<FloatType>) opService.filter().gauss(flinnPeakPlot, sigma);
		}
		flinnPeakPlotImage = new ImgPlus<>(flinnPeakPlot, "Flinn Peak Plot");
		flinnPeakPlotImage.setChannelMaximum(0, 255.0f);
		flinnPeakPlotImage.setChannelMinimum(0, 0.0f);
	}

	private void createFlinnPlotImage(final double[] aBRatios, final double[] bCRatios) {
		final Img<BitType> flinnPlot = ArrayImgs.bits(FLINN_PLOT_DIMENSION, FLINN_PLOT_DIMENSION);
		final RandomAccess<BitType> flinnRA = flinnPlot.randomAccess();
		for (int i = 0; i < aBRatios.length; i++) {
			final long x = Math.round(aBRatios[i] * (FLINN_PLOT_DIMENSION - 1));
			final long y = FLINN_PLOT_DIMENSION - Math.round(bCRatios[i] * (FLINN_PLOT_DIMENSION - 1)) - 1;
			flinnRA.setPosition(x, 0);
			flinnRA.setPosition(y, 1);
			flinnRA.get().setOne();
		}
		flinnPlotImage = new ImgPlus<>(flinnPlot, "Unweighted Flinn Plot");
		flinnPlotImage.setChannelMaximum(0, 255.0f);
		flinnPlotImage.setChannelMinimum(0, 0.0f);
	}

	private Img<FloatType> createNaNCopy() {
		final ArrayImg<FloatType, FloatArray> copy = ArrayImgs.floats(inputImgPlus.dimension(0),
				inputImgPlus.dimension(1), inputImgPlus.dimension(2));
		copy.forEach(e -> e.setReal(Float.NaN));
		return copy;
	}

	private void createPrimaryOutputImages(final List<QuickEllipsoid> ellipsoids, final Img<IntType> ellipsoidIDs) {
		createEFImage(ellipsoids, ellipsoidIDs);
		createVolumeImage(ellipsoids, ellipsoidIDs);
	}

	private void createSecondaryOutputImages(final List<QuickEllipsoid> ellipsoids, final Img<IntType> ellipsoidIDs) {
		final double[] aBRatios = ellipsoids.parallelStream()
				.mapToDouble(e -> e.getSortedRadii()[0] / e.getSortedRadii()[1]).toArray();
		createAToBImage(aBRatios, ellipsoidIDs);
		final double[] bCRatios = ellipsoids.parallelStream()
				.mapToDouble(e -> e.getSortedRadii()[1] / e.getSortedRadii()[2]).toArray();
		createBToCImage(bCRatios, ellipsoidIDs);
		createFlinnPlotImage(aBRatios, bCRatios);
		createFlinnPeakPlot(aBRatios, bCRatios, ellipsoidIDs);
		eIdImage = new ImgPlus<>(ellipsoidIDs, "ID");
		eIdImage.setChannelMaximum(0, ellipsoids.size() / 10.0f);
		eIdImage.setChannelMinimum(0, -1.0f);
	}

	private void createVolumeImage(final List<QuickEllipsoid> ellipsoids,
			final IterableInterval<IntType> ellipsoidIDs) {
		final Img<FloatType> volumeImage = createNaNCopy();
		final double[] volumes = ellipsoids.parallelStream().mapToDouble(QuickEllipsoid::getVolume).toArray();
		mapValuesToImage(volumes, ellipsoidIDs, volumeImage);
		vImage = new ImgPlus<>(volumeImage, "Volume");
		vImage.setChannelMaximum(0, ellipsoids.get(0).getVolume());
		vImage.setChannelMinimum(0, -1.0f);
	}

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
		final Img<IntType> idImage = ArrayImgs.ints(mask.dimension(0), mask.dimension(1), mask.dimension(2));
		idImage.forEach(c -> c.setInteger(-1));
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
			colourSlice(idImage, maskSlice, localEllipsoids, iDs);
		});
		return idImage;
	}

	private void colourSlice(final RandomAccessible<IntType> idImage, final Cursor<BitType> mask,
			final Collection<QuickEllipsoid> localEllipsoids, final Map<QuickEllipsoid, Integer> iDs) {
		while (mask.hasNext()) {
			mask.fwd();
			if (!mask.get().get()) {
				continue;
			}
			final long[] coordinates = new long[3];
			mask.localize(coordinates);
			final Vector3d point = new Vector3d(coordinates[0] + 0.5, coordinates[1] + 0.5, coordinates[2] + 0.5);
			colourID(localEllipsoids, idImage, point, iDs);
		}
	}

	private void colourID(final Collection<QuickEllipsoid> localEllipsoids,
			final RandomAccessible<IntType> ellipsoidIdentityImage, final Vector3dc point,
			final Map<QuickEllipsoid, Integer> iDs) {
		final Optional<QuickEllipsoid> candidate = localEllipsoids.stream()
				.filter(e -> e.contains(point.x(), point.y(), point.z())).findFirst();
		if (!candidate.isPresent()) {
			return;
		}
		final RandomAccess<IntType> eIDRandomAccess = ellipsoidIdentityImage.randomAccess();
		eIDRandomAccess.setPosition(new long[]{(long) point.x(), (long) point.y(), (long) point.z()});
		final QuickEllipsoid ellipsoid = candidate.get();
		eIDRandomAccess.get().set(iDs.get(ellipsoid));
	}

	private void createEFOutputs(Img<IntType> ellipsoidIdentityImage, QuickEllipsoid[] ellipsoids) {
		createPrimaryOutputImages(Arrays.asList(ellipsoids), ellipsoidIdentityImage);
		if (showSecondaryImages)
			createSecondaryOutputImages(Arrays.asList(ellipsoids), ellipsoidIdentityImage);
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
