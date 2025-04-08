/*-
 * #%L
 * Mavenized version of the BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.List;

import org.bonej.menuWrappers.ThicknessHelper;
import org.bonej.util.DialogModifier;
import org.bonej.util.ImageCheck;
import org.jogamp.vecmath.Point3f;

import Jama.EigenvalueDecomposition;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij3d.Image3DUniverse;
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
public class ParticleCounter implements PlugIn, DialogListener {

	
	/* (non-Javadoc)
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog, java.awt.AWTEvent)
	 */
	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
		if (DialogModifier.hasInvalidNumber(gd.getNumericFields())) return false;
		final List<?> choices = gd.getChoices();
		final List<?> checkboxes = gd.getCheckboxes();
		final List<?> numbers = gd.getNumericFields();

		// link moments and ellipsoid choice to unit vector choice
		final Checkbox momBox = (Checkbox) checkboxes.get(4);
		final Checkbox elBox = (Checkbox) checkboxes.get(8);
		final Checkbox vvvBox = (Checkbox) checkboxes.get(9);
		vvvBox.setEnabled(elBox.getState() || momBox.getState());
		// link show stack 3d to volume resampling
		final Checkbox box = (Checkbox) checkboxes.get(18);
		final TextField numb = (TextField) numbers.get(4);
		numb.setEnabled(box.getState());
		// link show surfaces, gradient choice and split value
		final Checkbox surfbox = (Checkbox) checkboxes.get(14);
		final Choice col = (Choice) choices.get(0);
		final TextField split = (TextField) numbers.get(3);
		col.setEnabled(surfbox.getState());
		split.setEnabled(surfbox.getState() && col.getSelectedIndex() == 1);
		DialogModifier.registerMacroValues(gd, gd.getComponents());
		return true;
	}

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(final String arg) {
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
			return;
		}
		if (!ImageCheck.isBinary(imp)) {
			IJ.error("Binary image required");
			return;
		}
		final Calibration cal = imp.getCalibration();
		final String units = cal.getUnits();
		final GenericDialog gd = new GenericDialog("Setup");
		final String[] headers = { "Measurement Options", " " };
		final String[] labels = new String[12];
		final boolean[] defaultValues = new boolean[12];
		labels[0] = "Exclude on sides";
		defaultValues[0] = false;
		labels[1] = "Surface_area";
		defaultValues[1] = true;
		labels[2] = "Feret diameter";
		defaultValues[2] = false;
		labels[3] = "Enclosed_volume";
		defaultValues[3] = true;
		labels[4] = "Moments of inertia";
		defaultValues[4] = true;
		labels[5] = "Euler characteristic";
		defaultValues[5] = true;
		labels[6] = "Thickness";
		defaultValues[6] = true;
		labels[7] = "Mask thickness map";
		defaultValues[7] = false;
		labels[8] = "Ellipsoids";
		defaultValues[8] = true;
		labels[9] = "Record unit vectors";
		defaultValues[9] = false;
		labels[10] = "Skeletons";
		defaultValues[10] = false;
		labels[11] = "Aligned boxes";
		defaultValues[11] = false;
		gd.addCheckboxGroup(6, 2, labels, defaultValues, headers);
		gd.addNumericField("Min Volume", 0, 3, 7, units + "³");
		gd.addNumericField("Max Volume", Double.POSITIVE_INFINITY, 3, 7, units +
			"³");
		gd.addNumericField("Surface_resampling", 2, 0);
		final String[] headers2 = { "Graphical Results", " " };
		final String[] labels2 = new String[10];
		final boolean[] defaultValues2 = new boolean[10];
		labels2[0] = "Show_particle stack";
		defaultValues2[0] = true;
		labels2[1] = "Show_size stack";
		defaultValues2[1] = false;
		labels2[2] = "Show_thickness stack";
		defaultValues2[2] = false;
		labels2[3] = "Show_surfaces (3D)";
		defaultValues2[3] = true;
		labels2[4] = "Show_centroids (3D)";
		defaultValues2[4] = true;
		labels2[5] = "Show_axes (3D)";
		defaultValues2[5] = true;
		labels2[6] = "Show_ellipsoids (3D)";
		defaultValues2[6] = true;
		labels2[7] = "Show_stack (3D)";
		defaultValues2[7] = true;
		labels2[8] = "Draw_ellipsoids";
		defaultValues2[8] = false;
		labels2[9] = "Show_aligned_boxes (3D)";
		defaultValues2[9] = false;
		gd.addCheckboxGroup(5, 2, labels2, defaultValues2, headers2);
		final String[] items = { "Gradient", "Split", "Orientation"};
		gd.addChoice("Surface colours", items, items[0]);
		gd.addNumericField("Split value", 0, 3, 7, units + "³");
		gd.addNumericField("Volume_resampling", 2, 0);
		gd.addHelp("https://imagej.github.io/plugins/bonej#particle-analyser");
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}
		final double minVol = gd.getNextNumber();
		final double maxVol = gd.getNextNumber();
		final boolean doExclude = gd.getNextBoolean();
		final boolean doSurfaceArea = gd.getNextBoolean();
		final boolean doFeret = gd.getNextBoolean();
		final boolean doSurfaceVolume = gd.getNextBoolean();
		final int resampling = (int) Math.floor(gd.getNextNumber());
		final boolean doMoments = gd.getNextBoolean();
		final boolean doEulerCharacters = gd.getNextBoolean();
		final boolean doThickness = gd.getNextBoolean();
		final boolean doMask = gd.getNextBoolean();
		final boolean doEllipsoids = gd.getNextBoolean();
		final boolean doVerboseUnitVectors = gd.getNextBoolean();
		final boolean doSkeletons = gd.getNextBoolean();
		final boolean doAlignedBoxes = gd.getNextBoolean();
		final boolean doParticleImage = gd.getNextBoolean();
		final boolean doParticleSizeImage = gd.getNextBoolean();
		final boolean doThickImage = gd.getNextBoolean();
		final boolean doSurfaceImage = gd.getNextBoolean();
		final int colourMode = gd.getNextChoiceIndex();
		final double splitValue = gd.getNextNumber();
		final boolean doCentroidImage = gd.getNextBoolean();
		final boolean doAxesImage = gd.getNextBoolean();
		final boolean doEllipsoidImage = gd.getNextBoolean();
		final boolean do3DOriginal = gd.getNextBoolean();
		final boolean doEllipsoidStack = gd.getNextBoolean();
		final boolean doAlignedBoxesImage = gd.getNextBoolean();
		final int origResampling = (int) Math.floor(gd.getNextNumber());

		// get the particles and do the analysis
		final long start = System.nanoTime();
		ConnectedComponents connector = new ConnectedComponents();
		final Object[] result = getParticles(connector, imp, minVol, maxVol,
				ConnectedComponents.FORE, doExclude);
		// calculate particle labelling time in ms
		final long time = (System.nanoTime() - start) / 1000000;
		IJ.log("Particle labelling finished in " + time + " ms");
		
		//start of analysis
		final int[][] particleLabels = (int[][]) result[1];
		final long[] particleSizes = (long[]) result[2];
		final int nParticles = particleSizes.length;
		
		if (nParticles > ConnectedComponents.MAX_FINAL_LABEL)
			IJ.log("Number of particles ("+nParticles+") exceeds the accurate display range (2^23) of the 32-bit float particle image");

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
			surfacePoints = ParticleAnalysis.getSurfacePoints(imp, particleLabels, limits, resampling, nParticles);
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
				thickImp.getProcessor().setMinAndMax(0, max);
				thickImp.setTitle(imp.getShortTitle() + "_thickness");
				thickImp.show();
				thickImp.setSlice(1);
				IJ.run("Fire");
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
		final ResultsTable rt = new ResultsTable();
		for (int i = 1; i < volumes.length; i++) {
			if (volumes[i] > 0) {
				rt.incrementCounter();
				rt.addLabel(imp.getTitle());
				rt.addValue("ID", i);
				rt.addValue("Vol. (" + units + "³)", volumes[i]);
				rt.addValue("x Cent (" + units + ")", centroids[i][0]);
				rt.addValue("y Cent (" + units + ")", centroids[i][1]);
				rt.addValue("z Cent (" + units + ")", centroids[i][2]);
				if (doAlignedBoxes) {
					rt.addValue("Box x (" + units + ")", alignedBoxes[i][0]);
					rt.addValue("Box y (" + units + ")", alignedBoxes[i][1]);
					rt.addValue("Box z (" + units + ")", alignedBoxes[i][2]);
					rt.addValue("Box l0 (" + units + ")", alignedBoxes[i][3]);
					rt.addValue("Box l1 (" + units + ")", alignedBoxes[i][4]);
					rt.addValue("Box l2 (" + units + ")", alignedBoxes[i][5]);
				}
				if (doSurfaceArea) {
					rt.addValue("SA (" + units + "²)", surfaceAreas[i]);
				}
				if (doFeret) {
					rt.addValue("Feret (" + units + ")", ferets[i][0]);
					rt.addValue("FeretAx (" + units + ")", ferets[i][1]);
					rt.addValue("FeretAy (" + units + ")", ferets[i][2]);
					rt.addValue("FeretAz (" + units + ")", ferets[i][3]);
					rt.addValue("FeretBx (" + units + ")", ferets[i][4]);
					rt.addValue("FeretBy (" + units + ")", ferets[i][5]);
					rt.addValue("FeretBz (" + units + ")", ferets[i][6]);
				}
				if (doSurfaceVolume) {
					rt.addValue("Encl. Vol. (" + units + "³)", surfaceVolumes[i]);
				}
				if (doMoments) {
					final EigenvalueDecomposition E = eigens[i];
					rt.addValue("I1", E.getD().get(2, 2));
					rt.addValue("I2", E.getD().get(1, 1));
					rt.addValue("I3", E.getD().get(0, 0));
					rt.addValue("vX", E.getV().get(0, 0));
					rt.addValue("vY", E.getV().get(1, 0));
					rt.addValue("vZ", E.getV().get(2, 0));
					if (doVerboseUnitVectors) {
						rt.addValue("vX1", E.getV().get(0, 1));
						rt.addValue("vY1", E.getV().get(1, 1));
						rt.addValue("vZ1", E.getV().get(2, 1));
						rt.addValue("vX2", E.getV().get(0, 2));
						rt.addValue("vY2", E.getV().get(1, 2));
						rt.addValue("vZ2", E.getV().get(2, 2));
					}
				}
				if (doSkeletons) {
					int nBranches = 0;
					double branchesLength = Double.NaN;
					final SkeletonResult skeletonResult = skeletonResults[i];
					if (skeletonResult.getNumOfTrees() == 0) {
						IJ.log("No skeleton found for particle "+i);
					} else {
					    nBranches = skeletonResults[i].getBranches()[0];
					    branchesLength = skeletonResults[i].getAverageBranchLength()[0]
					    		* nBranches;
					}
					rt.addValue("n Branches", nBranches);
					rt.addValue("Branches length ("+units+")", branchesLength);
				}
				if (doEulerCharacters) {
					rt.addValue("Euler (χ)", eulerCharacters[i][0]);
					rt.addValue("Holes (β1)", eulerCharacters[i][1]);
					rt.addValue("Cavities (β2)", eulerCharacters[i][2]);
				}
				if (doThickness) {
					rt.addValue("Thickness (" + units + ")", thick[i][0]);
					rt.addValue("SD Thickness (" + units + ")", thick[i][1]);
					rt.addValue("Max Thickness (" + units + ")", thick[i][2]);
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
					rt.addValue("Major radius (" + units + ")", rad[0]);
					rt.addValue("Int. radius (" + units + ")", rad[1]);
					rt.addValue("Minor radius (" + units + ")", rad[2]);
					if (doVerboseUnitVectors) {
						rt.addValue("V00", unitV[0][0]);
						rt.addValue("V01", unitV[0][1]);
						rt.addValue("V02", unitV[0][2]);
						rt.addValue("V10", unitV[1][0]);
						rt.addValue("V11", unitV[1][1]);
						rt.addValue("V12", unitV[1][2]);
						rt.addValue("V20", unitV[2][0]);
						rt.addValue("V21", unitV[2][1]);
						rt.addValue("V22", unitV[2][2]);
					}
				}
				rt.updateResults();
			}
		}
		rt.show("Results");

		// Show resulting image stacks
		if (doParticleImage) {
			ParticleDisplay.displayParticleLabels(particleLabels, imp).show();
			IJ.run("3-3-2 RGB");
		}
		if (doParticleSizeImage) {
			ParticleDisplay.displayParticleValues(imp, particleLabels, volumes).show();
			IJ.run("Fire");
		}
		if (doEllipsoidStack) {
			ParticleDisplay.displayParticleEllipsoids(imp, ellipsoids).show();
		}

		// show 3D renderings
		if (doSurfaceImage || doCentroidImage || doAxesImage || do3DOriginal ||
			doEllipsoidImage || doAlignedBoxesImage)
		{

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
		IJ.showProgress(1.0);
		IJ.showStatus("Particle Analysis Complete");
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


