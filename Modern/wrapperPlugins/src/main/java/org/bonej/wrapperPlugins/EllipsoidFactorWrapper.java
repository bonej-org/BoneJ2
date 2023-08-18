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

import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.display.ColorTables;
import net.imagej.units.UnitService;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.bonej.ops.ellipsoid.EllipsoidFactorErrorTracking;
import org.bonej.ops.ellipsoid.EllipsoidOptimisationStrategy;
import org.bonej.ops.ellipsoid.OptimisationParameters;
import org.bonej.ops.ellipsoid.RayCaster;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.skeletonize.FindRidgePoints;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
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

	private static final byte FORE = (byte) 0xFF;

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
	private List<ImgPlus<FloatType>> ellipsoidFactorOutputImages;
	@Parameter(label = "Seed Points", type = ItemIO.OUTPUT)
	private ImgPlus<ByteType> seedPointImage;// 0=not a seed, 1=medial seed

	@Override
	public void run() {

		byte[][] pixels = imgPlusToByteArray(inputImage);

		int totalEllipsoids = 0;
		List<ImgPlus> outputList = null;
		final EllipsoidFactorErrorTracking errorTracking =
				new EllipsoidFactorErrorTracking(opService);

		int counter = 0;
		double[] medianErrors = new double[runs];
		double[] maxErrors = new double[runs];

		//optimise ellipsoids
		//handle multiple runs inside runEllipsoidOptimisation
		final Ellipsoid[][] ellipsoids = runEllipsoidOptimisation(pixels);

		//if no ellipsoids were found then cancel the plugin
		int nEllipsoids = 0;
		for (int i = 0; i < runs; i++)
			nEllipsoids += ellipsoids[i].length;
		if (nEllipsoids == 0) {
			cancelMacroSafe(this, NO_ELLIPSOIDS_FOUND);
			return;
		}

		//assign one ellipsoid to each FG voxel
		statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
		long start = System.currentTimeMillis();
		
		//new model - 
		//go through each foreground pixel, find the maximal ellipsoids with 
		//volume-weighted averaging over multiple runs and
		//over n-biggest ellipsoids.
		ellipsoidFactorOutputImages = getOutputImagesFromEllipsoids(pixels, ellipsoids);
		
		long stop = System.currentTimeMillis();
		
		logService.info("Found maximal ellipsoids and generated output images in " + (stop - start) + " ms");

		start = System.currentTimeMillis();
		logService.info("Calibrating output images...");
		//calibrate output images
		final double voxelVolume = ElementUtil.calibratedSpatialElementSize(inputImage, unitService);
		for(final ImgPlus<FloatType> imp : ellipsoidFactorOutputImages) {
			
			//only do the 3D images: EF etc., not the 2D Flinn plot(s)
			if(imp.numDimensions() >= 3) {
				// set spatial axis for first 3 dimensions (ID is 4d)
				for (int dim = 0; dim < 3; dim++) {
					CalibratedAxis axis = inputImage.axis(dim);
					axis.setUnit(inputImage.axis(dim).unit());
					imp.setAxis(axis, dim);
				}
			}
		}
		stop = System.currentTimeMillis();
		logService.info("Output images calibrated in "+(stop - start)+" ms");
		
		start = System.currentTimeMillis();
		logService.info("Calculating filling percentage...");
		final ImgPlus<FloatType> EF = ellipsoidFactorOutputImages.get(0);
		final double numberOfForegroundPixels = countForegroundPixels(pixels);
		final double numberOfAssignedPixels = countAssignedPixels(EF);
		final double fillingPercentage = 100.0 * (numberOfAssignedPixels / numberOfForegroundPixels);
		stop = System.currentTimeMillis();
		logService.info("Filling percentage calculated in "+(stop - start)+" ms");

		start = System.currentTimeMillis();
		logService.info("Calculating descriptive statistics...");
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
		//TODO move convergence analysis to the mean calculation
		if(showConvergence)
		{
//			for(int i=1; i<runs; i++)
//			{
//				SharedTable.add(inputImage.getName(),"median change "+i, medianErrors[i]);
//				SharedTable.add(inputImage.getName(),"maximum change "+i, maxErrors[i]);
//			}
			addResults(totalEllipsoids, fillingPercentage);
		}
		stop = System.currentTimeMillis();
		logService.info("Descriptive statistics calculated in "+(stop - start)+" ms");
		
		resultsTable = SharedTable.getTable();
		statusService.showStatus("Ellipsoid Factor completed");
		reportUsage();
	}

	/**
	 * Calculate the output images
	 * @param pixels
	 * @param ellipsoids
	 * @return List of output images: EF, Flinn Plot...
	 */
	private List<ImgPlus<FloatType>> getOutputImagesFromEllipsoids(byte[][] pixels, Ellipsoid[][] ellipsoids) {
		
		final int w = (int) inputImage.dimension(0);
		final int h = (int) inputImage.dimension(1);
		final int d = (int) inputImage.dimension(2);
		final int flinnSize = 512;
		
		CellImgFactory<FloatType> imgFactory = new CellImgFactory<FloatType>(new FloatType(), 4);
		
		final Img<FloatType> efImg = imgFactory.create(w, h, d);
		final Img<FloatType> flinn = imgFactory.create(flinnSize, flinnSize);
		//working array to hold  slice-wise sums
		final double[][] flinnWorking = new double[pixels.length][flinnSize * flinnSize];
		
		IntStream slices = IntStream.range(0, pixels.length);
		
		slices.parallel().forEach(z -> {
			final byte[] slice = pixels[z];
			final double[] flinnSlice = flinnWorking[z];
			//TODO find out if there is a better way to synchronise the localisation in the array and the Img.
			//options are to get an Interval which is a single z-slice
			//or to make a float[] array that is then copied into the Img z-slice
			//or a View?
			RandomAccess<FloatType> efAccess = efImg.randomAccess();
			for (int y = 0; y < h; y++) {
				final int yw = y * w;
				for (int x = 0; x < w; x++) {
					if (slice[yw + x] == FORE) {
						//pack the result in to a double array for further use
						double[] pixelResult = getMaximalEllipsoidAverages(x, y, z, ellipsoids);
						
						//add ellipsoid volume to coordinate in flinn plot
						//TODO figure out what is a better way to do the average: before or after calculating ratios
						double a = pixelResult[0];
						double b = pixelResult[1];
						double c = pixelResult[2];
						
						//calculate the coordinate
						//y coordinate is flinnWidth * (1 - a/b) (lower left origin), x coordinate is flinnWidth * (b/c)
						final int fx = (int) Math.floor(flinnSize * (b / c));
						final int fy = (int) Math.floor(flinnSize * (1 - a / b));
						//add 1 to the (b/c,a/b) coordinate for this pixel
						//volume weighting occurs because bigger ellipsoids will appear more often and at a single coordinate
						flinnSlice[fy * flinnSize + fx] += 1;
						
						//set values in the output image
						final double ef = a / b - b / c;
						//I am going to take a punt that this is slow
						efAccess.setPositionAndGet(x, y, z).set((float)ef);
					} else {
						efAccess.setPositionAndGet(x, y, z).set(Float.NaN);
					}
				}
			}
		});
		
		final int flinnSize2 = flinnSize * flinnSize;
		final float[] flinnArray = new float[flinnSize2];
		
		for (int z = 0; z < d; z++) {
			final double[] zSlice = flinnWorking[z];
			for (int i = 0; i < flinnSize2; i++) {
				flinnArray[i] += (float) zSlice[i];
			}
		}
		RandomAccess<FloatType> flinnAccess = flinn.randomAccess(flinn);
		for (int y = 0; y < flinnSize; y++) {
			final int yf = y * flinnSize;
			for (int x = 0; x < flinnSize; x++) {
				final float p = flinnArray[yf + x];
				flinnAccess.setPositionAndGet(x, y).set(p);
			}
		}
		
		//get the Img pixel data ready for display by adding some metadata as ImgPlus
		List<ImgPlus<FloatType>> outputImages = new ArrayList<>();
		
		ImgPlus<FloatType> efImp = new ImgPlus<>(efImg, inputImage.getName()+"_EF");
        efImp.setChannelMaximum(0, 1);
        efImp.setChannelMinimum(0, -1);
        efImp.initializeColorTables(1);
        efImp.setColorTable(ColorTables.FIRE,0);
		
        outputImages.add(efImp);

        ImgPlus<FloatType> flinnImp = new ImgPlus<>(flinn, inputImage.getName()+"_Flinn");
        double max = flinnImp.getChannelMaximum(0);
        flinnImp.setChannelMinimum(0, 0.0);
        flinnImp.setChannelMaximum(0, max);
        DefaultLinearAxis xFlinnAxis = new DefaultLinearAxis(Axes.get("b/c",true),1.0/flinnSize);
        xFlinnAxis.setUnit("b/c");
        DefaultLinearAxis yFlinnAxis = new DefaultLinearAxis(Axes.get("a/b",true),-1.0/flinnSize);
        yFlinnAxis.setOrigin(flinnSize);
        yFlinnAxis.setUnit("a/b");
        flinnImp.setAxis(xFlinnAxis,0);
        flinnImp.setAxis(yFlinnAxis,1);
        
		outputImages.add(flinnImp);

		return outputImages;
	}

	/**
	 * find the largest ellipsoid(s) that contain the point and return average measurements
	 * @param x
	 * @param y
	 * @param z
	 * @param ellipsoids
	 * @return a double[] array listing: radii a, b, c; a/b, b/c; EF; volume (sum);  //TODO rotation matrix 
	 */
	private double[] getMaximalEllipsoidAverages(int x, int y, int z, Ellipsoid[][] ellipsoids) {
		final int nRuns = ellipsoids.length;
		
		double aSum = 0;
		double bSum = 0;
		double cSum = 0;
		double abSum = 0;
		double bcSum = 0;
		double efSum = 0;
		double vSum = 0;
		
		for (int i = 0; i < nRuns; i++) {
			//get all the ellipsoids from a run, sorted large -> small
			Ellipsoid[] ellipsoidRun = ellipsoids[i];
			//get the user-specified number of maximal ellipsoids for this point (x, y, z) and this run
			Ellipsoid[] maximalEllipsoids = getNMaximalEllipsoids(x, y, z, ellipsoidRun, weightedAverageN);
			
			//calculate weighted sums 
			for (int j = 0; j < weightedAverageN; j++) {
				Ellipsoid e = maximalEllipsoids[j];
				if (e == null)
					continue;
				final double v = e.getVolume();
				double[] radii = e.getSortedRadii();
				final double a = radii[0];
				final double b = radii[1];
				final double c = radii[2];
				aSum += a * v;
				bSum += b * v;
				cSum += c * v;
				abSum += (a / b) * v;
				bcSum += (b / c) * v;
				efSum += (a / b - b / c) * v;
				vSum += v;
			}			
		}
		
		final double a = aSum / vSum;
		final double b = bSum / vSum;
		final double c = cSum / vSum;
		final double ab = abSum / vSum;
		final double bc = bcSum / vSum;
		final double ef = efSum / vSum;
		
		return new double[] { a, b, c, ab, bc, ef, vSum };
	}

	/**
	 * Find the n maximal ellipsoids that contain the given point and return them in an array
	 * 
	 * @param x x-coordinate of the point
	 * @param y y-coordinate of the point
	 * @param z z-coordinate of the point
	 * @param ellipsoidRun all the ellipsoids from a run of optimisation, sorted large -> small
	 * @param averageOverN how many ellipsoids to find
	 * @return array of ellipsoids that contain the point. elements could be null if not enough ellipsoids contain the point.
	 */
	private Ellipsoid[] getNMaximalEllipsoids(final int x, final int y, final int z, Ellipsoid[] ellipsoidRun, final int averageOverN) {
		int foundCount = 0;
		final int nEllipsoids = ellipsoidRun.length;
		Ellipsoid[] maximalEllipsoids = new Ellipsoid[averageOverN];
		for (int i = 0; i < nEllipsoids; i++) {
			Ellipsoid e = ellipsoidRun[i];
			
			if (e.contains(x, y, z)) {
				maximalEllipsoids[foundCount] = e;
				foundCount++;
			}
			
			if (foundCount == averageOverN)
				break;
		}
		return maximalEllipsoids;
	}

	/**
	 * Using input image surface points as seeds, check whether each seed point is visible,
	 * and thus available to use as a boundary point for the ellipsoid centred on the seed point.
	 * Use the resulting clouds of boundary points as input into an ellipsoid fit.
	 * 
	 * Multiple runs re-use the same seed and boundary points and keep independent
	 * lists of maximal ellipsoids.
	 *
	 * @param imp
	 *            input image
	 * @return 2D array of fitted ellipsoids
	 */
	private Ellipsoid[][] runEllipsoidOptimisation(final byte[][] pixels) {
		long start = System.currentTimeMillis();

		final int w = (int) inputImage.dimension(0);
		final int h = (int) inputImage.dimension(1);
		final int d = (int) inputImage.dimension(2);
		
		//set up a 2D array to hold the results of multiple runs on the same seed points & boundary points
		Ellipsoid[][] ellipsoidsArray = new Ellipsoid[runs][];
		
		//set the optimisation parameters for the whole EF operation
		final OptimisationParameters parameters = new OptimisationParameters(contactSensitivity, maxIterations, maxDrift, vectorIncrement);
		
		//Make a list to hold all the seed points
		final List<int[]> allSeedPoints = new ArrayList<>();
		
		//add the distance ridge seed points if that was chosen
		if (seedOnDistanceRidge) {
			final ImgPlus<BitType> inputAsBitType = Common.toBitTypeImgPlus(opService, inputImage);
			allSeedPoints.addAll(getDistanceRidgePoints(inputAsBitType));
		}

		if (seedOnTopologyPreserving) {
			allSeedPoints.addAll(getSkeletonPoints(pixels));
		}
		
		//thin out the seed points by the skip ratio setting
		final List<int[]> seedPointsList = applySkipRatio(allSeedPoints);
		
		//optionally make an image containing the seed points, usually for debugging purposes
		if(showSecondaryImages)	{
			//set up an image for the seed points
			final ArrayImg<ByteType, ByteArray> seedImage = ArrayImgs.bytes(w, h, d);
			addPointsToDisplay(seedPointsList, seedImage, (byte) 1);
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
		
		//need to randomise seed points' order in the List so that bounding box isn't biased
		//by the ordered way they were added to the list
		Collections.shuffle(seedPointsList);
		final int nSeedPoints = seedPointsList.size();
		
		final int[][] seedPoints = new int[nSeedPoints][3];
		
		for (int i = 0; i < nSeedPoints; i++)
			seedPoints[i] = seedPointsList.get(i);
		
		final long startRayTrace = System.currentTimeMillis();
		logService.info("Gathering boundary point clouds for "+nSeedPoints+" seed points...");
		//get the boundary points by raytracing from surface pixels to seed points
		HashMap<int[], ArrayList<int[]>> boundaryPointLists = RayCaster.getVisibleClouds(seedPoints, pixels, w, h, d);
		final long stopRayTrace = System.currentTimeMillis();
		logService.info("Boundary point clouds collected for "+nSeedPoints+" seed points in "+(stopRayTrace - startRayTrace)+" ms");
		
		//do the ellipsoid optimisation "runs" times, reusing the seed points and boundary points
		for (int i = 0; i < runs; i++) {

			statusService.showStatus("Optimising ellipsoids from "+nSeedPoints+" seed points..." +
					" (run "+ (i + 1) +"/"+ runs +")");

			//the list of ellipsoids for a single run
			Ellipsoid[] ellipsoidArray = new Ellipsoid[nSeedPoints];
			
			final AtomicInteger progress = new AtomicInteger();
			
			//TODO keep an eye on whether reusing this instance across all the threads
			//is thread safe
			EllipsoidOptimisationStrategy optimiser = new EllipsoidOptimisationStrategy(
				new long[] {w, h, d}, logService, statusService, parameters, true);
			
			//iterate over all the seed points and get an optimised ellipsoid for each.
			IntStream.range(0, nSeedPoints)//.parallel()
				//get a status update for user feedback
				.peek(pk -> statusService.showProgress(progress.getAndIncrement(), nSeedPoints))
				
				//iterate over all the seed numbers
				.forEach(j -> {
				
				final int[] seedPoint = seedPoints[j];
				final ArrayList<int[]> boundaryPointList = boundaryPointLists.get(seedPoint);
				logService.info("Optimising ellipsoid seeded at ("+seedPoint[0]+", "+seedPoint[1]+", "+seedPoint[2]+
						") within "+boundaryPointList.size()+" boundary points...");
				boundaryPointList.forEach(s -> {
					if (s == null) {
						logService.info("Found a null boundary point!");
						throw new IllegalArgumentException("Boundary point cannot be null");
					}
				});
				
				//convert the list of boundary points to an array of 3D int coordinates
				final int nBoundaryPoints = boundaryPointList.size();
				final int[][] boundaryPoints = new int[nBoundaryPoints][3];
				
				for (int p = 0; p < nBoundaryPoints; p++) {
					boundaryPoints[p] = boundaryPointList.get(p);
				}
								
				Ellipsoid ellipsoid = optimiser.calculate(boundaryPoints, seedPoint);
				ellipsoidArray[j] = ellipsoid;				
			});
			
			//cleanup the OpenCL environment - allows VRAM to clear
			optimiser.shutdownCL();
			
			List<Ellipsoid> ellipsoids = new ArrayList<>(nSeedPoints);
						
			//remove nulls that resulted from failed optimisation attempts
			//the streamy filtery way
			//Collections.addAll(ellipsoids, ellipsoidArray);
			//ellipsoids = ellipsoids.stream().filter(Objects::nonNull).collect(Collectors.toList());
			//or the non-streamy way
			//ellipsoids.removeIf(Objects::isNull);
			
			//or the C-style way
			for (int p = 0; p < nSeedPoints; p++) {
				if (ellipsoidArray[p] != null)
					ellipsoids.add(ellipsoidArray[p]);
			}

			ellipsoids.sort((a, b) -> Double.compare(b.getVolume(), a.getVolume()));

			final int nEllipsoids = ellipsoids.size();
			final Ellipsoid[] ellipsoidRunArray = new Ellipsoid[nEllipsoids];
			for (int e = 0; e < nEllipsoids; e++)
				ellipsoidRunArray[e] = ellipsoids.get(e);
				
			ellipsoidsArray[i] = ellipsoidRunArray;

			final long stop = System.currentTimeMillis();
			logService.info("Found " + nEllipsoids + " ellipsoids in " + (stop - start) + " ms" +
					" (run "+ (i + 1) +"/"+ runs +")");
		}
		
		return ellipsoidsArray;
	}

	// region --seed point finding--

	private List<int[]> getSkeletonPoints(byte[][] pixels) {
		final int w = (int) inputImage.dimension(0);
		final int h = (int) inputImage.dimension(1);
		final int d = pixels.length;
		ImageStack stack = new ImageStack();
		for (int z = 0; z < d; z++)
			stack.addSlice(new ByteProcessor(w, h, pixels[z]));
		
		final ImagePlus imp = new ImagePlus();
		imp.setStack(stack);
		final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
		skeletoniser.setup("", imp);
		skeletoniser.run(null);
		final List<int[]> skeletonPoints = new ArrayList<>();
		final ImageStack skeletonStack = imp.getImageStack();
		for (int z = 0; z < d; z++) {
			final byte[] slicePixels = (byte[]) skeletonStack.getPixels(z + 1);
			for (int y = 0; y < h; y++) {
				final int yw = y * w;
				for (int x = 0; x < w; x++) {
					if (slicePixels[yw + x] == FORE) {
						skeletonPoints.add(new int[] {x, y, z});
					}
				}
			}
		}
		return skeletonPoints;
	}

	private List<int[]> getDistanceRidgePoints(final ImgPlus<BitType> imp) {
		final List<int[]> ridgePoints = (List<int[]>) opService.run(FindRidgePoints.class, imp, distanceThreshold);
		logService.info("Found " + ridgePoints.size() + " distance-ridge-based points");
		return ridgePoints;
	}

	private List<int[]> applySkipRatio(final List<int[]> seedPoints) {
		if (skipRatio > 1) {
			final int nSeedPoints = seedPoints.size();
			List<int[]> trimmedList = new ArrayList<>(nSeedPoints / skipRatio);
			final int randomStart = (new Random()).nextInt(skipRatio);
			for (int i = randomStart; i < nSeedPoints; i += skipRatio )
				trimmedList.add(seedPoints.get(i));
			
			logService.info("Trimmed seed point list to " + trimmedList.size() + " points");
			return trimmedList;
		}
		return seedPoints;
	}

	/**
	 * Debug method to visualise the seed points, not needed in normal operation
	 * 
	 * @param seedPoints
	 * @param seedImage
	 * @param i
	 */
	private void addPointsToDisplay(final List<int[]> seedPoints, final Img<ByteType> seedImage,
									final byte i) {
		final RandomAccess<ByteType> access = seedImage.randomAccess();
		for (final int[] p : seedPoints) {
			access.setPosition(p);
			access.get().set(i);
		}
	}
	//endregion


	private long countAssignedPixels(final Iterable<FloatType> ellipsoidFactorImage) {
		final LongType assignedVoxels = new LongType();
		ellipsoidFactorImage.forEach(e -> {
			if (Float.isFinite(e.get())) {
				assignedVoxels.inc();
			}
		});
		return assignedVoxels.get();
	}

	
	private long countForegroundPixels(final byte[][] pixels) {
		
		final int d = pixels.length;
		final int wh = pixels[0].length;
		
		final long[] counts = new long[d];
		
		IntStream slices = IntStream.range(0, d);
		
		slices.parallel().forEach(z -> {
			long sliceCount = 0;
			final byte[] slicePixels = pixels[z];
			for (int i = 0; i < wh; i++) {
				if (slicePixels[i] == FORE) {
					sliceCount++;
				}
			}
			
			counts[z] = sliceCount;
		});
		
		return Arrays.stream(counts).sum();
	}
	
	/**
	 *TODO redo this with performance in mind 
	 * 
	 * 
	 * @param mask
	 * @param ellipsoids
	 * @return
	 */
	private Img<IntType> assignEllipsoidIDs(final byte[][] pixels, final Ellipsoid[][] ellipsoids) {

		final Img<IntType> idImage = ArrayImgs.ints(inputImage.dimension(0), inputImage.dimension(1), weightedAverageN, inputImage.dimension(2));
		
		return idImage;
	}

	//TODO this seems a bit inefficient
	private void colourSlice(final RandomAccessible<IntType> idImage, final Cursor<BitType> mask,
							 final Collection<Ellipsoid> localEllipsoids, final Map<Ellipsoid, Integer> iDs,
							 final int nLargest) {
		final int[] point = new int[mask.numDimensions()];
		while (mask.hasNext()) {
			mask.fwd();
			if (!mask.get().get()) {
				continue;
			}
			mask.localize(point);
			colourID(localEllipsoids, idImage, point, iDs, nLargest);
		}
	}

	private void colourID(final Collection<Ellipsoid> localEllipsoids,
						  final RandomAccessible<IntType> ellipsoidIdentityImage, final int[] point,
						  final Map<Ellipsoid, Integer> iDs, int nLargest) {
		final Optional<Ellipsoid> candidate = localEllipsoids.stream()
				.filter(e -> e.contains(point[0], point[1], point[2])).skip(nLargest).findFirst();
		if (!candidate.isPresent()) {
			return;
		}
		final RandomAccess<IntType> eIDRandomAccess = ellipsoidIdentityImage.randomAccess();
		eIDRandomAccess.setPosition(new long[]{point[0], point[1], nLargest, point[2]});
		final Ellipsoid ellipsoid = candidate.get();
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
	/**
	 * @param <T>
	 * @param imgPlus
	 * @return
	 */
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
				pixels[position[2]][position[1] * w + position[0]] = FORE;
			}
		}

		return pixels;
	}
}
