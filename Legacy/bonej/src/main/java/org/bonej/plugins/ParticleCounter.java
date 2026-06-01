/*-
 * #%L
 * Mavenized version of the BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2026 Michael Doube, BoneJ developers
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


package org.bonej.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bonej.menuWrappers.ThicknessHelper;
import org.bonej.util.ImageCheck;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.BoneJCommand;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.jogamp.vecmath.Point3f;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.display.DisplayService;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import Jama.EigenvalueDecomposition;

import ij.ImagePlus;
import ij3d.Image3DUniverse;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.display.ColorTables;
import sc.fiji.analyzeSkeleton.SkeletonResult;

/**
 * <p>
 * This class implements mutithreaded linear O(n) 3D particle
 * identification and shape analysis. It is a two-pass connected components labelling
 * algorithm, which uses reduction of a neighbour network to generate a lut.
 * Processing time increases linearly with number of pixels.
 * </p>
 *
 * @author Michael Doube
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Particle Analyser")
public class ParticleCounter extends BoneJCommand implements Command {

	/* IJ2 parameters */
	@Parameter(type = ItemIO.INPUT)
    private Dataset inputDataset;
	
	@Parameter(type = ItemIO.OUTPUT, required = false)
	private Dataset thickDataset;
	
	@Parameter(type = ItemIO.OUTPUT, required = false)
	private Dataset particleDataset;
	
	@Parameter(type = ItemIO.OUTPUT, required = false)
	private Dataset sizeDataset;
	
	@Parameter(type = ItemIO.OUTPUT, required = false)
	private Dataset ellipsoidDataset;
	
	@Parameter
	private ConvertService convertService;
	
	@Parameter
	private DatasetService datasetService;
	
	@Parameter
	private UIService uiService;
	
	@Parameter
	private DisplayService displayService;
	
	@Parameter
	private LogService logService;
	
	@Parameter(label = "Exclude on sides")
	private boolean doExclude = false;

	@Parameter(label = "Surface area")
	private boolean doSurfaceArea = true;

	@Parameter(label = "Feret diameter")
	private boolean doFeret = false;

	@Parameter(label = "Enclosed volume")
	private boolean doSurfaceVolume = true;

	@Parameter(label = "Moments of inertia")
	private boolean doMoments = true;

	@Parameter(label = "Euler characteristic")
	private boolean doEulerCharacters = true;

	@Parameter(label = "Thickness")
	private boolean doThickness = true;

	@Parameter(label = "Mask thickness map")
	private boolean doMask = false;

	@Parameter(label = "Ellipsoids")
	private boolean doEllipsoids = true;

	@Parameter(label = "Record unit vectors")
	private boolean doVerboseUnitVectors = false;

	@Parameter(label = "Skeletons")
	private boolean doSkeletons = false;

	@Parameter(label = "Aligned boxes")
	private boolean doAlignedBoxes = false;

	@Parameter(label = "Minimum volume", style = "format:0.000", min = "0.0")
	private double minVol = 0.0;

	@Parameter(label = "Maximum volume", style = "format:0.000")
	private double maxVol = Double.POSITIVE_INFINITY;

	@Parameter(label = "Surface resampling", style = "spinner", min = "0")
	private int surfaceResampling = 2;

	// Graphical Results
	@Parameter(label = "Show particle stack")
	private boolean doParticleImage = true;

	@Parameter(label = "Show size stack")
	private boolean doParticleSizeImage = false;

	@Parameter(label = "Show thickness stack")
	private boolean doThickImage = false;
	
	@Parameter(label = "Draw ellipsoids")
	private boolean doEllipsoidStack = false;

	@Parameter(label = "Show surfaces (3D)")
	private boolean doSurfaceImage = true;

	@Parameter(label = "Show centroids (3D)")
	private boolean doCentroidImage = true;

	@Parameter(label = "Show axes (3D)")
	private boolean doAxesImage = true;

	@Parameter(label = "Show ellipsoids (3D)")
	private boolean doEllipsoidImage = true;

	@Parameter(label = "Show original stack (3D)")
	private boolean do3DOriginal = true;

	@Parameter(label = "Show aligned boxes (3D)")
	private boolean doAlignedBoxesImage = false;

	@Parameter(label = "Surface colours", choices = {"Gradient", "Split", "Orientation"})
	private String surfaceColours = "Gradient";
	 //for hybrid use
	static final String[] COLOUR_CHOICES = {"Gradient", "Split", "Orientation"};
	private int colourMode;

	@Parameter(label = "Split value", style = "format:0.000", min = "0")
	private double splitValue = 0.0;

	@Parameter(label = "Volume resampling", style = "spinner", min = "0")
	private int origResampling = 2;
	
	
	/**
	 * Modern scijava Plugin entry point.
	 */
	@Override
	public void run() {
        ImagePlus imp = convertService.convert(inputDataset, ImagePlus.class);
        
        if (imp == null) {
            logService.error("Particle Counter: Failed to convert Dataset to ImagePlus.");
            return;
        }
        
        if (!ImageCheck.isBinary(imp)) {
        	String errorMsg = "Particle Counter requires a binary image. " +
        			"The provided image (" + imp.getTitle() + ") is not binary.";

        	// Log to console/file (safe in headless mode)
        	logService.error(errorMsg);

        	return;
		}
        
        final long start = System.nanoTime();
		ConnectedComponents connector = new ConnectedComponents();
		final Object[] result = getParticles(connector, imp, minVol, maxVol,
				ConnectedComponents.FORE, doExclude);
		// calculate particle labelling time in ms
		final long time = (System.nanoTime() - start) / 1000000;
		logService.log(LogLevel.INFO, "Particle labelling finished in " + time + " ms");
		
		//do the analysis on the result
		colourMode = Arrays.asList(COLOUR_CHOICES).indexOf(surfaceColours);
        doAnalysis(imp, result);        
	}

	private void doAnalysis(ImagePlus imp, Object[] result) {
		
		//if inputDataset is present, we are in modern mode
		final boolean isModernMode = (inputDataset != null);		
		
		//start of analysis
		final int[][] particleLabels = (int[][]) result[1];
		final long[] particleSizes = (long[]) result[2];
		final int nParticles = particleSizes.length;
		
		if (nParticles > ConnectedComponents.MAX_FINAL_LABEL)
			logService.warn("Number of particles ("+nParticles+") exceeds the accurate display range (2^23) of the 32-bit float particle image");

		final double[] volumes = ParticleAnalysis.getVolumes(imp, particleSizes);
		
		final Object[] boxes = ParticleAnalysis.getBoundingBoxes(imp, particleLabels, particleSizes);
		
		final double[][] centroids = (double[][]) boxes[0];
		final int[][] limits = (int[][]) boxes[1];

		EigenvalueDecomposition[] eigens = new EigenvalueDecomposition[nParticles];
		if (doMoments || doAxesImage || colourMode == ParticleDisplay.ORIENTATION || doAlignedBoxes || doAlignedBoxesImage) {
			eigens = ParticleAnalysis.getEigens(imp, particleLabels, centroids);
		}
		
		double[][] alignedBoxes = new double[nParticles][6];
		if (doAlignedBoxes || doAlignedBoxesImage) {
			alignedBoxes = ParticleAnalysis.getAxisAlignedBoundingBoxes(imp, particleLabels, eigens, nParticles);
		}
		
		// set up resources for analysis
		List<List<Point3f>> surfacePoints = new ArrayList<>();
		if (doSurfaceArea || doSurfaceVolume || doSurfaceImage || doEllipsoids ||
			doFeret || doEllipsoidStack)
		{
			surfacePoints = ParticleAnalysis.getSurfacePoints(imp, particleLabels, limits, surfaceResampling, nParticles);
		}
		// calculate dimensions
		double[] surfaceAreas = new double[nParticles];
		if (doSurfaceArea) {
			surfaceAreas = ParticleAnalysis.getSurfaceAreas(surfacePoints);
		}
		double[][] ferets = new double[nParticles][7];
		if (doFeret) {
			ferets = ParticleAnalysis.getFerets(surfacePoints);
		}
		double[] surfaceVolumes = new double[nParticles];
		if (doSurfaceVolume) {
			surfaceVolumes = ParticleAnalysis.getSurfaceVolume(surfacePoints);
		}
		double[][] eulerCharacters = new double[nParticles][3];
		if (doEulerCharacters) {
			eulerCharacters = ParticleAnalysis.getEulerCharacter(imp, particleLabels, limits, nParticles);
		}
		double[][] thick = new double[nParticles][2];
		if (doThickness) {
			final ImagePlus thickImp = ThicknessHelper.getLocalThickness(imp, false, doMask);
			thick = ParticleAnalysis.getMeanStdDev(thickImp, particleLabels, particleSizes);
			if (doThickImage) {
				double max = 0;
				for (int i = 1; i < nParticles; i++) {
					max = Math.max(max, thick[i][2]);
				}
				thickImp.setLut(Common.makeFire());
				thickImp.getProcessor().setMinAndMax(0, max);
				thickImp.setTitle(imp.getShortTitle() + "_thickness");
				if (isModernMode) {
					thickDataset = convertService.convert(thickImp, Dataset.class);
					thickImp.close();
					thickDataset.getImgPlus().setColorTable(ColorTables.FIRE, 0);
					thickDataset.setChannelMinimum(0, 0);
					thickDataset.setChannelMaximum(0, max);
					if (uiService != null && uiService.isVisible())
	        			uiService.show(thickDataset);
				} else {
					thickImp.show();
					thickImp.setSlice(1);
				}
			}
		}
		Object[] ellipsoids = new Object[nParticles][10];
		if (doEllipsoids || doEllipsoidImage || doEllipsoidStack) {
			ellipsoids = ParticleAnalysis.getEllipsoids(surfacePoints);
		}
		SkeletonResult[] skeletonResults = null;
		if (doSkeletons) {
			skeletonResults = ParticleAnalysis.getBranchLength(imp, particleLabels, limits, nParticles);
		}

		// Show numerical results
		final String units = imp.getCalibration().getUnits();
		SharedTable.reset();
		for (int i = 1; i < volumes.length; i++) {
			if (volumes[i] > 0) {
				SharedTable.add(imp.getTitle(),"ID", i);
				SharedTable.add(imp.getTitle(),"Vol. (" + units + "³)", volumes[i]);
				SharedTable.add(imp.getTitle(),"x Cent (" + units + ")", centroids[i][0]);
				SharedTable.add(imp.getTitle(),"y Cent (" + units + ")", centroids[i][1]);
				SharedTable.add(imp.getTitle(),"z Cent (" + units + ")", centroids[i][2]);
				if (doAlignedBoxes) {
					SharedTable.add(imp.getTitle(),"Box x (" + units + ")", alignedBoxes[i][0]);
					SharedTable.add(imp.getTitle(),"Box y (" + units + ")", alignedBoxes[i][1]);
					SharedTable.add(imp.getTitle(),"Box z (" + units + ")", alignedBoxes[i][2]);
					SharedTable.add(imp.getTitle(),"Box l0 (" + units + ")", alignedBoxes[i][3]);
					SharedTable.add(imp.getTitle(),"Box l1 (" + units + ")", alignedBoxes[i][4]);
					SharedTable.add(imp.getTitle(),"Box l2 (" + units + ")", alignedBoxes[i][5]);
				}
				if (doSurfaceArea) {
					SharedTable.add(imp.getTitle(),"SA (" + units + "²)", surfaceAreas[i]);
				}
				if (doFeret) {
					SharedTable.add(imp.getTitle(),"Feret (" + units + ")", ferets[i][0]);
					SharedTable.add(imp.getTitle(),"FeretAx (" + units + ")", ferets[i][1]);
					SharedTable.add(imp.getTitle(),"FeretAy (" + units + ")", ferets[i][2]);
					SharedTable.add(imp.getTitle(),"FeretAz (" + units + ")", ferets[i][3]);
					SharedTable.add(imp.getTitle(),"FeretBx (" + units + ")", ferets[i][4]);
					SharedTable.add(imp.getTitle(),"FeretBy (" + units + ")", ferets[i][5]);
					SharedTable.add(imp.getTitle(),"FeretBz (" + units + ")", ferets[i][6]);
				}
				if (doSurfaceVolume) {
					SharedTable.add(imp.getTitle(),"Encl. Vol. (" + units + "³)", surfaceVolumes[i]);
				}
				if (doMoments) {
					final EigenvalueDecomposition E = eigens[i];
					SharedTable.add(imp.getTitle(),"I1", E.getD().get(2, 2));
					SharedTable.add(imp.getTitle(),"I2", E.getD().get(1, 1));
					SharedTable.add(imp.getTitle(),"I3", E.getD().get(0, 0));
					SharedTable.add(imp.getTitle(),"vX", E.getV().get(0, 0));
					SharedTable.add(imp.getTitle(),"vY", E.getV().get(1, 0));
					SharedTable.add(imp.getTitle(),"vZ", E.getV().get(2, 0));
					if (doVerboseUnitVectors) {
						SharedTable.add(imp.getTitle(),"vX1", E.getV().get(0, 1));
						SharedTable.add(imp.getTitle(),"vY1", E.getV().get(1, 1));
						SharedTable.add(imp.getTitle(),"vZ1", E.getV().get(2, 1));
						SharedTable.add(imp.getTitle(),"vX2", E.getV().get(0, 2));
						SharedTable.add(imp.getTitle(),"vY2", E.getV().get(1, 2));
						SharedTable.add(imp.getTitle(),"vZ2", E.getV().get(2, 2));
					}
				}
				if (doSkeletons) {
					int nBranches = 0;
					double branchesLength = Double.NaN;
					final SkeletonResult skeletonResult = skeletonResults[i];
					if (skeletonResult.getNumOfTrees() == 0) {
						logService.warn("No skeleton found for particle "+i);
					} else {
					    nBranches = skeletonResults[i].getBranches()[0];
					    branchesLength = skeletonResults[i].getAverageBranchLength()[0]
					    		* nBranches;
					}
					SharedTable.add(imp.getTitle(),"n Branches", nBranches);
					SharedTable.add(imp.getTitle(),"Branches length ("+units+")", branchesLength);
				}
				if (doEulerCharacters) {
					SharedTable.add(imp.getTitle(),"Euler (χ)", eulerCharacters[i][0]);
					SharedTable.add(imp.getTitle(),"Holes (β1)", eulerCharacters[i][1]);
					SharedTable.add(imp.getTitle(),"Cavities (β2)", eulerCharacters[i][2]);
				}
				if (doThickness) {
					SharedTable.add(imp.getTitle(),"Thickness (" + units + ")", thick[i][0]);
					SharedTable.add(imp.getTitle(),"SD Thickness (" + units + ")", thick[i][1]);
					SharedTable.add(imp.getTitle(),"Max Thickness (" + units + ")", thick[i][2]);
				}
				if (doEllipsoids) {
					final double[] rad;
					final double[][] unitV;
					if (ellipsoids[i] == null) {
						rad = new double[] { Double.NaN, Double.NaN, Double.NaN };
						unitV = new double[][] { { Double.NaN, Double.NaN, Double.NaN }, {
							Double.NaN, Double.NaN, Double.NaN }, { Double.NaN, Double.NaN,
								Double.NaN } };
					}
					else {
						final Object[] el = (Object[]) ellipsoids[i];
						rad = (double[]) el[1];
						unitV = (double[][]) el[2];
					}
					SharedTable.add(imp.getTitle(),"Major radius (" + units + ")", rad[0]);
					SharedTable.add(imp.getTitle(),"Int. radius (" + units + ")", rad[1]);
					SharedTable.add(imp.getTitle(),"Minor radius (" + units + ")", rad[2]);
					if (doVerboseUnitVectors) {
						SharedTable.add(imp.getTitle(),"V00", unitV[0][0]);
						SharedTable.add(imp.getTitle(),"V01", unitV[0][1]);
						SharedTable.add(imp.getTitle(),"V02", unitV[0][2]);
						SharedTable.add(imp.getTitle(),"V10", unitV[1][0]);
						SharedTable.add(imp.getTitle(),"V11", unitV[1][1]);
						SharedTable.add(imp.getTitle(),"V12", unitV[1][2]);
						SharedTable.add(imp.getTitle(),"V20", unitV[2][0]);
						SharedTable.add(imp.getTitle(),"V21", unitV[2][1]);
						SharedTable.add(imp.getTitle(),"V22", unitV[2][2]);
					}
				}
			}
		}
		resultsTable = SharedTable.getTable();
	
		// Show resulting image stacks
		if (doParticleImage) {
			ImagePlus particleImp = ParticleDisplay.displayParticleLabels(particleLabels, imp);
			particleImp.setLut(Common.make332RGB());
			particleImp.getProcessor().setMinAndMax(0, nParticles - 1);
			if (isModernMode) {
				particleDataset = convertService.convert(particleImp, Dataset.class);
				particleImp.close();
				particleDataset.getImgPlus().setColorTable(ColorTables.RGB332, 0);
				particleDataset.getImgPlus().setChannelMinimum(0, 0);
				particleDataset.getImgPlus().setChannelMaximum(0, nParticles - 1);
				if (uiService != null && uiService.isVisible())
        			uiService.show(particleDataset);
			} else {
				particleImp.show();
			}
		}
		if (doParticleSizeImage) {
			ImagePlus sizeImp = ParticleDisplay.displayParticleValues(imp, particleLabels, volumes);
			sizeImp.setLut(Common.makeFire());
			double maxSize = 0;
			for (int i = 0; i < nParticles; i++)
				maxSize = Math.max(maxSize, volumes[i]);
			sizeImp.getProcessor().setMinAndMax(0, maxSize);
			if (isModernMode) {
				sizeDataset = convertService.convert(sizeImp, Dataset.class);
				sizeImp.close();
				sizeDataset.getImgPlus().setColorTable(ColorTables.FIRE, 0);
				sizeDataset.setChannelMinimum(0, 0);
				sizeDataset.setChannelMaximum(0, maxSize);
				if (uiService != null && uiService.isVisible())
        			uiService.show(sizeDataset);
			} else {
				sizeImp.show();
			}
		}
		if (doEllipsoidStack) {
			ImagePlus ellipsoidImp = ParticleDisplay.displayParticleEllipsoids(imp, ellipsoids);
			if (isModernMode) {
				ellipsoidDataset = convertService.convert(ellipsoidImp, Dataset.class);
				ellipsoidImp.close();
				if (uiService != null && uiService.isVisible())
        			uiService.show(ellipsoidDataset);
			} else {
				ellipsoidImp.show();
			}
		}

		show3DRenderings(imp, surfacePoints, volumes, eigens, centroids, particleSizes, ferets, ellipsoids, alignedBoxes);

		logService.info("Particle analysis complete");
	}
	
	private void show3DRenderings(ImagePlus imp, List<List<Point3f>> surfacePoints, double[] volumes, EigenvalueDecomposition[] eigens,
			double[][] centroids, long[] particleSizes, double[][] ferets, Object[] ellipsoids,
			double[][] alignedBoxes) {
		// show 3D renderings
		if (doSurfaceImage || doCentroidImage || doAxesImage || do3DOriginal ||
			doEllipsoidImage || doAlignedBoxesImage)
		{
			//don't try to do 3D rendering in an unsupported environment
			if (uiService.isHeadless()) {
				logService.warn("3D rendering skipped: no suitable display environment available");
				return;
			}

			final Image3DUniverse univ = new Image3DUniverse();
			if (doSurfaceImage) {
				ParticleDisplay.displayParticleSurfaces(univ, surfacePoints, colourMode, volumes,
						splitValue, eigens);
			}
			if (doCentroidImage) {
				ParticleDisplay.displayCentroids(centroids, univ);
			}
			if (doAxesImage) {
				ParticleDisplay.displayPrincipalAxes(univ, eigens, centroids, particleSizes);
				if (doFeret) {
					ParticleDisplay.displayMaxFeret(univ, ferets);
				}
			}
			if (doEllipsoidImage) {
				//ellipsoids are an Object[] array (1D)
				//with elements that are also Object[] arrays
				//but an Object[] is also an Object so only need a
				//1D array
				ParticleDisplay.displayEllipsoids(ellipsoids, univ);
			}
			if (do3DOriginal) {
				ParticleDisplay.display3DOriginal(imp, origResampling, univ);
			}
			if (doAlignedBoxesImage) {
				ParticleDisplay.displayAlignedBoundingBoxes(alignedBoxes, eigens, univ);
			}
			univ.show();
		}
	}

	/**
	 * Get particles, particle labels and particle sizes from a 3D ImagePlus
	 * 
	 * @param connector Instance of ConnectedComponents 
	 * @param imp Input image
	 * @param phase foreground or background (ConnectedComponents.FORE or .BACK)
	 * @return array containing a binary workArray, particle labels and
	 *         particle sizes
	 */
	Object[] getParticles(ConnectedComponents connector, final ImagePlus imp, final int phase)
	{
		return getParticles(connector, imp, 0.0,
			Double.POSITIVE_INFINITY, phase, false);
	}
	
	/**
	 * Get particles, particle labels and sizes from a workArray using an
	 * ImagePlus for scale information
	 * 
	 * @param connector Instance of ConnectedComponents
	 * @param imp input binary image
	 * @param minVol minimum volume particle to include
	 * @param maxVol maximum volume particle to include
	 * @param phase FORE or BACK for foreground or background respectively
	 * @param doExclude exclude particles touching the edges.
	 * @return Object[] array containing a binary workArray, particle labels and
	 *         particle sizes
	 */
	private Object[] getParticles(ConnectedComponents connector, 
			final ImagePlus imp, final double minVol, final double maxVol,
		final int phase, final boolean doExclude)
	{		
		//do the connected components
		final int[][] particleLabels = connector.run(imp, phase);
		
		byte[][] workArray = connector.getWorkArray();

		final int nParticles = connector.getNParticles();

		ParticleAnalysis pa = new ParticleAnalysis();
		
		//optionally remove too big, too small, and edge-touching particles
		pa.filterParticles(imp, particleLabels, workArray, nParticles,
				phase, doExclude, minVol, maxVol);
		
		final long[] particleSizes = pa.getParticleSizes();
		
		return new Object[] { workArray, particleLabels, particleSizes };
	}
}


