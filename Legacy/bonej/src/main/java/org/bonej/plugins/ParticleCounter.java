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

package org.bonej.plugins;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bonej.geometry.FitEllipsoid;
import org.bonej.menuWrappers.LocalThickness;
import org.bonej.util.DialogModifier;
import org.bonej.util.ImageCheck;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import customnode.CustomPointMesh;
import customnode.CustomTriangleMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij3d.Image3DUniverse;
import marchingcubes.MCTriangulator;

/**
 * <p>
 * This class implements multithreaded and linear O(n) 3D particle
 * identification and shape analysis. Surface meshing and 3D visualisation are
 * provided by Bene Schmid's ImageJ 3D Viewer.
 * </p>
 * <p>
 * This plugin is based on Object_Counter3D by Fabrice P Cordelires and Jonathan
 * Jackson, but with significant speed increases through reduction of recursion
 * and multi-threading. Thanks to Robert Barbour for the suggestion to 'chunk'
 * the stack. Chunking works as follows:
 * </p>
 * <ol>
 * <li>Perform initial labelling on the whole stack in a single thread</li>
 * <li>for <i>n</i> discrete, contiguous chunks within the labelling array,
 * connectStructures()
 * <ol type="a">
 * <li>connectStructures() can run in a separate thread for each chunk</li>
 * <li>chunks are approximately equal-sized sets of slices</li>
 * </ol>
 * <li>stitchChunks() for the pixels on the first slice of each chunk, except
 * for the first chunk, restricting replaceLabels() to the current and all
 * previous chunks.
 * <ol type="a">
 * <li>stitchChunks() iterates through the slice being stitched in a single
 * thread</li>
 * </ol>
 * </li>
 * </ol>
 * <p>
 * The performance improvement should be in the region of a factor of <i>n</i>
 * if run linearly, and if multithreaded over <i>c</i> processors, speed
 * increase should be in the region of <i>n</i> * <i>c</i>, minus overhead.
 * </p>
 *
 * @author Michael Doube
 * @author Jonathan Jackson
 * @author Fabrice Cordelires
 * @author Michał Kłosowski
 * @see <a href="http://rsbweb.nih.gov/ij/plugins/track/objects.html">3D Object
 *      Counter</a>
 */
public class ParticleCounter implements PlugIn, DialogListener {

	/** Foreground value */
	static final int FORE = -1;
	/** Background value */
	static final int BACK = 0;
	/** Surface colour style */
	private static final int GRADIENT = 0;
	private static final int SPLIT = 1;
	private static final int ORIENTATION = 2;
	private String sPhase = "";
	private String chunkString = "";
	private JOINING labelMethod = JOINING.MAPPED;

	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
		if (DialogModifier.hasInvalidNumber(gd.getNumericFields())) return false;
		final List<?> choices = gd.getChoices();
		final List<?> checkboxes = gd.getCheckboxes();
		final List<?> numbers = gd.getNumericFields();
		// link algorithm choice to chunk size field
		final Choice choice = (Choice) choices.get(1);
		final TextField num = (TextField) numbers.get(5);
		num.setEnabled(choice.getSelectedItem().contentEquals("Multithreaded"));
		// link moments and ellipsoid choice to unit vector choice
		final Checkbox momBox = (Checkbox) checkboxes.get(4);
		final Checkbox elBox = (Checkbox) checkboxes.get(8);
		final Checkbox vvvBox = (Checkbox) checkboxes.get(9);
		vvvBox.setEnabled(elBox.getState() || momBox.getState());
		// link show stack 3d to volume resampling
		final Checkbox box = (Checkbox) checkboxes.get(17);
		final TextField numb = (TextField) numbers.get(4);
		numb.setEnabled(box.getState());
		// link show surfaces, gradient choice and split value
		final Checkbox surfbox = (Checkbox) checkboxes.get(13);
		final Choice col = (Choice) choices.get(0);
		final TextField split = (TextField) numbers.get(3);
		col.setEnabled(surfbox.getState());
		split.setEnabled(surfbox.getState() && col.getSelectedIndex() == 1);
		DialogModifier.registerMacroValues(gd, gd.getComponents());
		return true;
	}

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
		final String[] labels = new String[10];
		final boolean[] defaultValues = new boolean[10];
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
		gd.addCheckboxGroup(5, 2, labels, defaultValues, headers);
		gd.addNumericField("Min Volume", 0, 3, 7, units + "³");
		gd.addNumericField("Max Volume", Double.POSITIVE_INFINITY, 3, 7, units +
			"³");
		gd.addNumericField("Surface_resampling", 2, 0);
		final String[] headers2 = { "Graphical Results", " " };
		final String[] labels2 = new String[8];
		final boolean[] defaultValues2 = new boolean[8];
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
		gd.addCheckboxGroup(4, 2, labels2, defaultValues2, headers2);
		final String[] items = { "Gradient", "Split" };
		gd.addChoice("Surface colours", items, items[0]);
		gd.addNumericField("Split value", 0, 3, 7, units + "³");
		gd.addNumericField("Volume_resampling", 2, 0);
		final String[] items2 = { "Multithreaded", "Linear", "Mapped" };
		gd.addChoice("Labelling algorithm", items2, items2[2]);
		gd.addNumericField("Slices per chunk", 2, 0);
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
		final boolean doParticleImage = gd.getNextBoolean();
		final boolean doParticleSizeImage = gd.getNextBoolean();
		final boolean doThickImage = gd.getNextBoolean();
		final boolean doSurfaceImage = gd.getNextBoolean();
		int colourMode = GRADIENT;
		if (gd.getNextChoice().equals(items[1])) {
			colourMode = SPLIT;
		}
		final double splitValue = gd.getNextNumber();
		final boolean doCentroidImage = gd.getNextBoolean();
		final boolean doAxesImage = gd.getNextBoolean();
		final boolean doEllipsoidImage = gd.getNextBoolean();
		final boolean do3DOriginal = gd.getNextBoolean();
		final int origResampling = (int) Math.floor(gd.getNextNumber());
		final String choice = gd.getNextChoice();
		if (choice.equals(items2[0])) labelMethod = JOINING.MULTI;
		else if (choice.equals(items2[1])) labelMethod = JOINING.LINEAR;
		else labelMethod = JOINING.MAPPED;
		final int slicesPerChunk = (int) Math.floor(gd.getNextNumber());

		// get the particles and do the analysis
		final long start = System.nanoTime();
		final Object[] result = getParticles(imp, slicesPerChunk, minVol, maxVol,
			FORE, doExclude);
		// calculate particle labelling time in ms
		final long time = (System.nanoTime() - start) / 1000000;
		IJ.log("Particle labelling finished in " + time + " ms");
		final int[][] particleLabels = (int[][]) result[1];
		final long[] particleSizes = getParticleSizes(particleLabels);
		final int nParticles = particleSizes.length;
		final double[] volumes = getVolumes(imp, particleSizes);
		final double[][] centroids = getCentroids(imp, particleLabels,
			particleSizes);
		final int[][] limits = getParticleLimits(imp, particleLabels, nParticles);

		// set up resources for analysis
		ArrayList<List<Point3f>> surfacePoints = new ArrayList<>();
		if (doSurfaceArea || doSurfaceVolume || doSurfaceImage || doEllipsoids ||
			doFeret)
		{
			surfacePoints = getSurfacePoints(imp, particleLabels, limits, resampling,
				nParticles);
		}
		EigenvalueDecomposition[] eigens = new EigenvalueDecomposition[nParticles];
		if (doMoments || doAxesImage) {
			eigens = getEigens(imp, particleLabels, centroids);
		}
		// calculate dimensions
		double[] surfaceAreas = new double[nParticles];
		if (doSurfaceArea) {
			surfaceAreas = getSurfaceAreas(surfacePoints);
		}
		double[] ferets = new double[nParticles];
		if (doFeret) {
			ferets = getFerets(surfacePoints);
		}
		double[] surfaceVolumes = new double[nParticles];
		if (doSurfaceVolume) {
			surfaceVolumes = getSurfaceVolume(surfacePoints);
		}
		double[][] eulerCharacters = new double[nParticles][3];
		if (doEulerCharacters) {
			eulerCharacters = getEulerCharacter(imp, particleLabels, limits,
				nParticles);
		}
		double[][] thick = new double[nParticles][2];
		if (doThickness) {
			final LocalThickness th = new LocalThickness();
			final ImagePlus thickImp = th.getLocalThickness(imp, false, doMask);
			thick = getMeanStdDev(thickImp, particleLabels, particleSizes);
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
		Object[][] ellipsoids = new Object[nParticles][10];
		if (doEllipsoids || doEllipsoidImage) {
			ellipsoids = getEllipsoids(surfacePoints);
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
				if (doSurfaceArea) {
					rt.addValue("SA (" + units + "²)", surfaceAreas[i]);
				}
				if (doFeret) {
					rt.addValue("Feret (" + units + ")", ferets[i]);
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
						final Object[] el = ellipsoids[i];
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
			displayParticleLabels(particleLabels, imp).show();
			IJ.run("Fire");
		}
		if (doParticleSizeImage) {
			displayParticleValues(imp, particleLabels, volumes).show();
			IJ.run("Fire");
		}

		// show 3D renderings
		if (doSurfaceImage || doCentroidImage || doAxesImage || do3DOriginal ||
			doEllipsoidImage)
		{

			final Image3DUniverse univ = new Image3DUniverse();
			if (doSurfaceImage) {
				displayParticleSurfaces(univ, surfacePoints, colourMode, volumes,
					splitValue, eigens);
			}
			if (doCentroidImage) {
				displayCentroids(centroids, univ);
			}
			if (doAxesImage) {
				final double[][] lengths = (double[][]) getMaxDistances(imp,
					particleLabels, centroids, eigens)[1];
				displayPrincipalAxes(univ, eigens, centroids, lengths);
			}
			if (doEllipsoidImage) {
				displayEllipsoids(ellipsoids, univ);
			}
			if (do3DOriginal) {
				display3DOriginal(imp, origResampling, univ);
			}
			univ.show();
		}
		IJ.showProgress(1.0);
		IJ.showStatus("Particle Analysis Complete");
		UsageReporter.reportEvent(this).send();
	}

	/**
	 * Add all the neighbouring labels of a pixel to the map, except 0
	 * (background) and the pixel's own label, which is already in the map. The
	 * LUT gets updated with the minimum neighbour found, but this is only within
	 * the first neighbours and not the minimum label in the pixel's neighbour
	 * network
	 *
	 * @param map a map of LUT values.
	 * @param nbh a neighbourhood in the image.
	 * @param centre current pixel's label
	 */
	private static void addNeighboursToMap(final List<HashSet<Integer>> map,
		final int[] nbh, final int centre)
	{
		final HashSet<Integer> set = map.get(centre);
		Arrays.stream(nbh).filter(n -> n > 0).forEach(set::add);
	}

	private static void applyLUT(final int[][] particleLabels, final int[] lut,
		final int w, final int h, final int d)
	{
		for (int z = 0; z < d; z++) {
			IJ.showStatus("Applying LUT...");
			IJ.showProgress(z, d - 1);
			final int[] slice = particleLabels[z];
			for (int y = 0; y < h; y++) {
				final int yw = y * w;
				for (int x = 0; x < w; x++) {
					final int i = yw + x;
					final int label = slice[i];
					if (label == 0) continue;
					slice[i] = lut[label];
				}
			}
		}
	}

	private static boolean checkConsistence(final int[] lut,
		final List<HashSet<Integer>> map)
	{
		final int l = lut.length;
		for (int i = 1; i < l; i++) {
			if (!map.get(lut[i]).contains(i)) return false;
		}
		return true;
	}

	/**
	 * Connect structures = minimisation of IDs
	 *
	 * @param imp an image.
	 * @param workArray work array.
	 * @param particleLabels particles in the image.
	 * @param phase foreground or background
	 * @param scanRanges int[][] listgetPixel(particleLabels, x, y, z, w, h,
	 *          d);ing ranges to run connectStructures on
	 */
	private void connectStructures(final ImagePlus imp, final byte[][] workArray,
		final int[][] particleLabels, final int phase, final int[][] scanRanges)
	{
		IJ.showStatus("Connecting " + sPhase + " structures" + chunkString);
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		for (int c = 0; c < scanRanges[0].length; c++) {
			final int sR0 = scanRanges[0][c];
			final int sR1 = scanRanges[1][c];
			final int sR2 = scanRanges[2][c];
			final int sR3 = scanRanges[3][c];
			for (int z = sR0; z < sR1; z++) {
				for (int y = 0; y < h; y++) {
					final int rowIndex = y * w;
					for (int x = 0; x < w; x++) {
						final int arrayIndex = rowIndex + x;
						final List<int[]> coordinates;
						if (workArray[z][arrayIndex] == BACK) {
							coordinates = get26NeighbourhoodCoordinates(x, y, z, w, h, d);
						}
						else if (workArray[z][arrayIndex] == FORE &&
							particleLabels[z][arrayIndex] > 1)
						{
							coordinates = get6NeighbourhoodCoordinates(x, y, z, w, h, d);
						}
						else {
							continue;
						}
						replaceLabelsWithMinimum(coordinates, workArray, particleLabels, w,
							phase, sR2, sR3);
					}
				}
				if (phase == FORE) {
					IJ.showStatus("Connecting foreground structures" + chunkString);
					IJ.showProgress(z, d);
				}
				else {
					IJ.showStatus("Connecting background structures" + chunkString);
					IJ.showProgress(z, d + 1);
				}
			}
		}
	}

	/**
	 * Find duplicated values and update the LUT
	 *
	 * @param counter counts of LUT values.
	 * @param map map of LUT values.
	 * @param lut a look-up table.
	 * @return total number of duplicate values found.
	 */
	private static int countDuplicates(int[] counter,
		final ArrayList<HashSet<Integer>> map, final int[] lut)
	{
		//reset to 0 the counter array
		final int l = counter.length;
		counter = new int[l];
		HashSet<Integer> set = null;
		for (int i = 1; i < map.size(); i++) {
			set = map.get(i);
			for (final Integer val : set) {
				final int v = val.intValue();
				// every time a value is seen, log it
				counter[v]++;
				// update its LUT value if value was
				// found in a set with lower than current
				// lut value
				if (lut[v] > i) lut[v] = i;
			}
		}
		minimiseLutArray(lut);
		// all values should be seen only once,
		// count how many >1s there are.
		int count = 0;
		for (int i = 1; i < l; i++) {
			if (counter[i] > 1) count++;
		}
		return count;
	}

	/**
	 * Calculate the cross product of 3 Point3f's, which describe two vectors
	 * joined at the tails. Can be used to find the plane / surface normal of a
	 * triangle. Half of its magnitude is the area of the triangle.
	 *
	 * @param point0 both vectors' tails
	 * @param point1 vector 1's head
	 * @param point2 vector 2's head
	 * @return cross product vector
	 */
	private static Point3f crossProduct(final Point3f point0,
		final Point3f point1, final Point3f point2)
	{
		final double x1 = point1.x - point0.x;
		final double y1 = point1.y - point0.y;
		final double z1 = point1.z - point0.z;
		final double x2 = point2.x - point0.x;
		final double y2 = point2.y - point0.y;
		final double z2 = point2.z - point0.z;
		final Point3f crossVector = new Point3f();
		crossVector.x = (float) (y1 * z2 - z1 * y2);
		crossVector.y = (float) (z1 * x2 - x1 * z2);
		crossVector.z = (float) (x1 * y2 - y1 * x2);
		return crossVector;
	}

	private static void display3DOriginal(final ImagePlus imp,
		final int resampling, final Image3DUniverse univ)
	{
		final Color3f colour = new Color3f(1.0f, 1.0f, 1.0f);
		final boolean[] channels = { true, true, true };
		try {
			univ.addVoltex(imp, colour, imp.getTitle(), 0, channels, resampling)
				.setLocked(true);
		}
		catch (final NullPointerException npe) {
			IJ.log("3D Viewer was closed before rendering completed.");
		}
	}

	/**
	 * Draws 3 orthogonal axes defined by the centroid, unitvector and axis
	 * length.
	 *
	 * @param univ the universe where axes are drawn.
	 * @param centroid centroid of a particle.
	 * @param unitVector orientation of the particle.
	 * @param lengths lengths of the axes.
	 * @param green green component of the axes' color.
	 * @param title text shown by the axes.
	 */
	private static void displayAxes(final Image3DUniverse univ,
		final double[] centroid, final double[][] unitVector,
		final double[] lengths, final float green, final String title)
	{
		final double cX = centroid[0];
		final double cY = centroid[1];
		final double cZ = centroid[2];
		final double eVec1x = unitVector[0][0];
		final double eVec1y = unitVector[1][0];
		final double eVec1z = unitVector[2][0];
		final double eVec2x = unitVector[0][1];
		final double eVec2y = unitVector[1][1];
		final double eVec2z = unitVector[2][1];
		final double eVec3x = unitVector[0][2];
		final double eVec3y = unitVector[1][2];
		final double eVec3z = unitVector[2][2];
		final double l1 = lengths[0];
		final double l2 = lengths[1];
		final double l3 = lengths[2];

		final List<Point3f> mesh = new ArrayList<>();
		final Point3f start1 = new Point3f();
		start1.x = (float) (cX - eVec1x * l1);
		start1.y = (float) (cY - eVec1y * l1);
		start1.z = (float) (cZ - eVec1z * l1);
		mesh.add(start1);

		final Point3f end1 = new Point3f();
		end1.x = (float) (cX + eVec1x * l1);
		end1.y = (float) (cY + eVec1y * l1);
		end1.z = (float) (cZ + eVec1z * l1);
		mesh.add(end1);

		final Point3f start2 = new Point3f();
		start2.x = (float) (cX - eVec2x * l2);
		start2.y = (float) (cY - eVec2y * l2);
		start2.z = (float) (cZ - eVec2z * l2);
		mesh.add(start2);

		final Point3f end2 = new Point3f();
		end2.x = (float) (cX + eVec2x * l2);
		end2.y = (float) (cY + eVec2y * l2);
		end2.z = (float) (cZ + eVec2z * l2);
		mesh.add(end2);

		final Point3f start3 = new Point3f();
		start3.x = (float) (cX - eVec3x * l3);
		start3.y = (float) (cY - eVec3y * l3);
		start3.z = (float) (cZ - eVec3z * l3);
		mesh.add(start3);

		final Point3f end3 = new Point3f();
		end3.x = (float) (cX + eVec3x * l3);
		end3.y = (float) (cY + eVec3y * l3);
		end3.z = (float) (cZ + eVec3z * l3);
		mesh.add(end3);

		final Color3f aColour = new Color3f(1.0f, green, 0.0f);
		try {
			univ.addLineMesh(mesh, aColour, title, false).setLocked(true);
		}
		catch (final NullPointerException npe) {
			IJ.log("3D Viewer was closed before rendering completed.");
		}
	}

	/**
	 * Draw the particle centroids in a 3D viewer
	 *
	 * @param centroids [n][3] centroids of particles.
	 * @param univ universe where the centroids are displayed.
	 */
	private static void displayCentroids(final double[][] centroids,
		final Image3DUniverse univ)
	{
		final int nCentroids = centroids.length;
		for (int p = 1; p < nCentroids; p++) {
			IJ.showStatus("Rendering centroids...");
			IJ.showProgress(p, nCentroids);
			final Point3f centroid = new Point3f();
			centroid.x = (float) centroids[p][0];
			centroid.y = (float) centroids[p][1];
			centroid.z = (float) centroids[p][2];
			final List<Point3f> point = new ArrayList<>();
			point.add(centroid);
			final CustomPointMesh mesh = new CustomPointMesh(point);
			mesh.setPointSize(5.0f);
			final float red = 0.0f;
			final float green = 0.5f * p / nCentroids;
			final float blue = 1.0f;
			final Color3f cColour = new Color3f(red, green, blue);
			mesh.setColor(cColour);
			try {
				univ.addCustomMesh(mesh, "Centroid " + p).setLocked(true);
			}
			catch (final NullPointerException npe) {
				IJ.log("3D Viewer was closed before rendering completed.");
				return;
			}
		}
	}

	private static void displayEllipsoids(final Object[][] ellipsoids,
		final Image3DUniverse univ)
	{
		final int nEllipsoids = ellipsoids.length;
		for (int el = 1; el < nEllipsoids; el++) {
			IJ.showStatus("Rendering ellipsoids...");
			IJ.showProgress(el, nEllipsoids);
			if (ellipsoids[el] == null) {
				continue;
			}
			final double[] radii = (double[]) ellipsoids[el][1];
			if (!isRadiiValid(radii)) {
				continue;
			}
			final double[] centre = (double[]) ellipsoids[el][0];
			final double[][] eV = (double[][]) ellipsoids[el][2];
			final double a = radii[0]; // longest
			final double b = radii[1]; // middle
			final double c = radii[2]; // shortest
			if (a < b || b < c || a < c) {
				IJ.log("Error: Bad ellipsoid radius ordering! Surface: " + el);
			}
			final double[][] ellipsoid = FitEllipsoid.testEllipsoid(a, b, c, 0, 0, 0,
				0, 0, 1000, false);
			final int nPoints = ellipsoid.length;
			// rotate points by eigenvector matrix
			// and add transformation for centre
			for (int p = 0; p < nPoints; p++) {
				final double x = ellipsoid[p][0];
				final double y = ellipsoid[p][1];
				final double z = ellipsoid[p][2];
				ellipsoid[p][0] = x * eV[0][0] + y * eV[0][1] + z * eV[0][2] +
					centre[0];
				ellipsoid[p][1] = x * eV[1][0] + y * eV[1][1] + z * eV[1][2] +
					centre[1];
				ellipsoid[p][2] = x * eV[2][0] + y * eV[2][1] + z * eV[2][2] +
					centre[2];
			}

			final List<Point3f> points = new ArrayList<>();
			for (final double[] anEllipsoid : ellipsoid) {
				final Point3f e = new Point3f();
				e.x = (float) anEllipsoid[0];
				e.y = (float) anEllipsoid[1];
				e.z = (float) anEllipsoid[2];
				points.add(e);
			}
			final CustomPointMesh mesh = new CustomPointMesh(points);
			mesh.setPointSize(1.0f);
			final float red = 0.0f;
			final float green = 0.5f;
			final float blue = 1.0f;
			final Color3f cColour = new Color3f(red, green, blue);
			mesh.setColor(cColour);
			try {
				univ.addCustomMesh(mesh, "Ellipsoid " + el).setLocked(true);
			}
			catch (final NullPointerException npe) {
				IJ.log("3D Viewer was closed before rendering completed.");
				return;
			}
			// Add some axes
			displayAxes(univ, centre, eV, radii, 1.0f, "Ellipsoid Axes " + el);
		}
	}

	/**
	 * Display the particle labels as an ImagePlus
	 *
	 * @param particleLabels particles labelled in the original image.
	 * @param imp original image, used for image dimensions, calibration and
	 *          titles
	 * @return an image of the particles.
	 */
	private static ImagePlus displayParticleLabels(final int[][] particleLabels,
		final ImagePlus imp)
	{
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int wh = w * h;
		final ImageStack stack = new ImageStack(w, h);
		double max = 0;
		for (int z = 0; z < d; z++) {
			final float[] slicePixels = new float[wh];
			for (int i = 0; i < wh; i++) {
				slicePixels[i] = particleLabels[z][i];
				max = Math.max(max, slicePixels[i]);
			}
			stack.addSlice(imp.getImageStack().getSliceLabel(z + 1), slicePixels);
		}
		final ImagePlus impParticles = new ImagePlus(imp.getShortTitle() + "_parts",
			stack);
		impParticles.setCalibration(imp.getCalibration());
		impParticles.getProcessor().setMinAndMax(0, max);
		if (max > Math.pow(2, 24)) IJ.error("Warning",
			"More than 16777216 (2^24) particles./n/n" +
				"Particle image labels are inaccurate above this number.");
		return impParticles;
	}

	/**
	 * Draw the particle surfaces in a 3D viewer
	 *
	 * @param univ universe where the centroids are displayed.
	 * @param surfacePoints points of each particle.
	 */
	private static void displayParticleSurfaces(final Image3DUniverse univ,
		final Collection<List<Point3f>> surfacePoints, final int colourMode,
		final double[] volumes, final double splitValue,
		final EigenvalueDecomposition[] eigens)
	{
		int p = 0;
		final int nParticles = surfacePoints.size();
		for (final List<Point3f> surfacePoint : surfacePoints) {
			IJ.showStatus("Rendering surfaces...");
			IJ.showProgress(p, nParticles);
			if (p > 0 && !surfacePoint.isEmpty()) {
				Color3f pColour = new Color3f(0, 0, 0);
				if (colourMode == GRADIENT) {
					final float red = 1.0f - p / (float) nParticles;
					final float green = 1.0f - red;
					final float blue = p / (2.0f * nParticles);
					pColour = new Color3f(red, green, blue);
				}
				else if (colourMode == SPLIT) {
					if (volumes[p] > splitValue) {
						// red if over
						pColour = new Color3f(1.0f, 0.0f, 0.0f);
					}
					else {
						// yellow if under
						pColour = new Color3f(1.0f, 1.0f, 0.0f);
					}
				}
				else if (colourMode == ORIENTATION) {
					pColour = colourFromEigenVector(eigens[p]);
				}
				// Add the mesh
				try {
					univ.addTriangleMesh(surfacePoint, pColour, "Surface " + p).setLocked(
						true);
				}
				catch (final NullPointerException npe) {
					IJ.log("3D Viewer was closed before rendering completed.");
					return;
				}
			}
			p++;
		}
	}

	private static Color3f colourFromEigenVector(EigenvalueDecomposition eigen)
	{
		final Matrix rotation = eigen.getV();
		/*
		 * http://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToAngle/
		 * https://en.wikipedia.org/wiki/Rotation_matrix#Conversion_from_and_to_axis%E2%80%93angle
		 */
		float red = 0, green = 0, blue = 0;
		return new Color3f(red, green, blue);
	}

	/**
	 * Create an image showing some particle measurement
	 *
	 * @param imp an image.
	 * @param particleLabels the particles in the image.
	 * @param values list of values whose array indices correspond to
	 *          particlelabels
	 * @return ImagePlus with particle labels substituted with some value
	 */
	private static ImagePlus displayParticleValues(final ImagePlus imp,
		final int[][] particleLabels, final double[] values)
	{
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int wh = w * h;
		final float[][] pL = new float[d][wh];
		values[0] = 0; // don't colour the background
		final ImageStack stack = new ImageStack(w, h);
		for (int z = 0; z < d; z++) {
			for (int i = 0; i < wh; i++) {
				final int p = particleLabels[z][i];
				pL[z][i] = (float) values[p];
			}
			stack.addSlice(imp.getImageStack().getSliceLabel(z + 1), pL[z]);
		}
		final double max = Arrays.stream(values).max().orElse(0.0);
		final ImagePlus impOut = new ImagePlus(imp.getShortTitle() + "_" + "volume",
			stack);
		impOut.setCalibration(imp.getCalibration());
		impOut.getProcessor().setMinAndMax(0, max);
		return impOut;
	}

	private static void displayPrincipalAxes(final Image3DUniverse univ,
		final EigenvalueDecomposition[] eigens, final double[][] centroids,
		final double[][] lengths)
	{
		final int nEigens = eigens.length;
		for (int p = 1; p < nEigens; p++) {
			IJ.showStatus("Rendering principal axes...");
			IJ.showProgress(p, nEigens);
			final Matrix eVec = eigens[p].getV();
			displayAxes(univ, centroids[p], eVec.getArray(), lengths[p], 0.0f,
				"Principal Axes " + p);
		}
	}

	/**
	 * Scans edge voxels and set all touching particles to background
	 *
	 * @param imp an image.
	 * @param particleLabels particles in the image.
	 */
	private void excludeOnEdges(final ImagePlus imp, final int[][] particleLabels,
		final byte[][] workArray)
	{
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final long[] particleSizes = getParticleSizes(particleLabels);
		final int nLabels = particleSizes.length;
		final int[] newLabel = new int[nLabels];
		for (int i = 0; i < nLabels; i++)
			newLabel[i] = i;

		// scan faces
		// top and bottom faces
		for (int y = 0; y < h; y++) {
			final int index = y * w;
			for (int x = 0; x < w; x++) {
				final int pt = particleLabels[0][index + x];
				if (pt > 0) newLabel[pt] = 0;
				final int pb = particleLabels[d - 1][index + x];
				if (pb > 0) newLabel[pb] = 0;
			}
		}

		// west and east faces
		for (int z = 0; z < d; z++) {
			for (int y = 0; y < h; y++) {
				final int pw = particleLabels[z][y * w];
				final int pe = particleLabels[z][y * w + w - 1];
				if (pw > 0) newLabel[pw] = 0;
				if (pe > 0) newLabel[pe] = 0;
			}
		}

		// north and south faces
		final int lastRow = w * (h - 1);
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				final int pn = particleLabels[z][x];
				final int ps = particleLabels[z][lastRow + x];
				if (pn > 0) newLabel[pn] = 0;
				if (ps > 0) newLabel[ps] = 0;
			}
		}

		// replace labels
		final int wh = w * h;
		for (int z = 0; z < d; z++) {
			for (int i = 0; i < wh; i++) {
				final int p = particleLabels[z][i];
				final int nL = newLabel[p];
				if (nL == 0) {
					particleLabels[z][i] = 0;
					workArray[z][i] = 0;
				}
			}
		}

	}

	/**
	 * Remove particles outside user-specified volume thresholds
	 *
	 * @param imp ImagePlus, used for calibration
	 * @param workArray binary foreground and background information
	 * @param particleLabels Packed 3D array of particle labels
	 * @param minVol minimum (inclusive) particle volume
	 * @param maxVol maximum (inclusive) particle volume
	 * @param phase phase we are interested in
	 */
	private void filterParticles(final ImagePlus imp, final byte[][] workArray,
		final int[][] particleLabels, final double minVol, final double maxVol,
		final int phase)
	{
		if (minVol == 0 && maxVol == Double.POSITIVE_INFINITY) return;
		final int d = imp.getImageStackSize();
		final int wh = workArray[0].length;
		final long[] particleSizes = getParticleSizes(particleLabels);
		final double[] particleVolumes = getVolumes(imp, particleSizes);
		final byte flip;
		if (phase == FORE) {
			flip = 0;
		}
		else {
			flip = (byte) 255;
		}
		for (int z = 0; z < d; z++) {
			for (int i = 0; i < wh; i++) {
				final int p = particleLabels[z][i];
				final double v = particleVolumes[p];
				if (v < minVol || v > maxVol) {
					workArray[z][i] = flip;
					particleLabels[z][i] = 0;
				}
			}
		}
	}

	private static boolean findFirstAppearance(final int[] lut,
		final List<HashSet<Integer>> map)
	{
		final int l = map.size();
		boolean changed = false;
		for (int i = 0; i < l; i++) {
			final HashSet<Integer> set = map.get(i);
			for (final Integer val : set) {
				// if the current lut value is greater
				// than the current position
				// update lut with current position
				if (lut[val] > i) {
					lut[val] = i;
					changed = true;
				}
			}
		}
		return changed;
	}

	private static int findMinimumLabel(final Iterable<int[]> coordinates,
		final byte[][] workArray, final int[][] particleLabels, final int w,
		final int phase, final int minStart)
	{
		int minLabel = minStart;
		for (final int[] coordinate : coordinates) {
			final int z = coordinate[2];
			final int index = getOffset(coordinate[0], coordinate[1], w);
			if (workArray[z][index] == phase) {
				final int label = particleLabels[z][index];
				if (label != 0 && label < minLabel) {
					minLabel = label;
				}
			}
		}
		return minLabel;
	}

	/**
	 * Go through all pixels and assign initial particle label
	 *
	 * @param imp an image.
	 * @param workArray byte[] array containing pixel values
	 * @param phase FORE or BACK for foreground of background respectively
	 * @return particleLabels int[] array containing label associating every pixel
	 *         with a particle
	 */
	private int[][] firstIDAttribution(final ImagePlus imp,
		final byte[][] workArray, final int phase)
	{
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int[] bounds = { w, h, d };
		final int wh = w * h;
		IJ.showStatus("Finding " + sPhase + " structures");
		final int[][] particleLabels = new int[d][wh];
		int ID = 1;
		final BiFunction<int[], int[], List<int[]>> neighbourhoodFinder;
		if (phase == BACK) {
			neighbourhoodFinder = ParticleCounter::get6NeighbourhoodCoordinates;
		}
		else {
			neighbourhoodFinder = ParticleCounter::get26NeighbourhoodCoordinates;
		}
		for (int z = 0; z < d; z++) {
			for (int y = 0; y < h; y++) {
				final int rowIndex = y * w;
				for (int x = 0; x < w; x++) {
					final int arrayIndex = rowIndex + x;
					if (workArray[z][arrayIndex] != phase) {
						continue;
					}
					particleLabels[z][arrayIndex] = ID;
					final List<int[]> coordinates = neighbourhoodFinder.apply(new int[] {
						x, y, z }, bounds);
					coordinates.add(new int[] { x, y, z });
					final int minTag = findMinimumLabel(coordinates, workArray,
						particleLabels, w, phase, ID);
					particleLabels[z][arrayIndex] = minTag;
					if (minTag == ID) {
						ID++;
					}
				}
			}
			IJ.showProgress(z, d);
		}
		return particleLabels;
	}

	/**
	 * Get neighborhood of a pixel in a 3D image (0 border conditions)
	 *
	 * @param neighborhood a neighbourhood in the image.
	 * @param image 3D image (int[][])
	 * @param x0 x- coordinate
	 * @param y0 y- coordinate
	 * @param z0 z- coordinate (in image stacks the indexes start at 1)
	 * @param w width of the image.
	 * @param h height of the image.
	 * @param d depth of the image.
	 */
	private static void get26Neighborhood(final int[] neighborhood,
		final int[][] image, final int x0, final int y0, final int z0, final int w,
		final int h, final int d)
	{
		int i = 0;
		for (int z = z0 - 1; z < z0 + 1; z++) {
			for (int y = y0 - 1; y < y0 + 1; y++) {
				for (int x = x0 - 1; x < x0 + 1; x++) {
					if (x == x0 && y == y0 && z == z0) {
						continue;
					}
					neighborhood[i] = getPixel(image, x, y, z, w, h, d);
					i++;
				}
			}
		}
	}

	private static List<int[]> get26NeighbourhoodCoordinates(final int[] voxel,
		final int[] bounds)
	{
		return get26NeighbourhoodCoordinates(voxel[0], voxel[1], voxel[2],
			bounds[0], bounds[1], bounds[2]);
	}

	private static List<int[]> get26NeighbourhoodCoordinates(final int x0,
		final int y0, final int z0, final int w, final int h, final int d)
	{
		final List<int[]> coordinates = new ArrayList<>();
		for (int z = z0 - 1; z <= z0 + 1; z++) {
			for (int y = y0 - 1; y <= y0 + 1; y++) {
				for (int x = x0 - 1; x <= x0 + 1; x++) {
					if (z == z0 && y == y0 && x == x0) {
						continue;
					}
					if (withinBounds(x, y, z, w, h, d)) {
						coordinates.add(new int[] { x, y, z });
					}
				}
			}
		}
		return coordinates;
	}

	private static void get6Neighborhood(final int[] neighborhood,
		final int[][] image, final int x, final int y, final int z, final int w,
		final int h, final int d)
	{
		neighborhood[0] = getPixel(image, x - 1, y, z, w, h, d);
		neighborhood[1] = getPixel(image, x, y - 1, z, w, h, d);
		neighborhood[2] = getPixel(image, x, y, z - 1, w, h, d);
		neighborhood[3] = getPixel(image, x + 1, y, z, w, h, d);
		neighborhood[4] = getPixel(image, x, y + 1, z, w, h, d);
		neighborhood[5] = getPixel(image, x, y, z + 1, w, h, d);
	}

	private static List<int[]> get6NeighbourhoodCoordinates(final int[] voxel,
		final int[] bounds)
	{
		return get26NeighbourhoodCoordinates(voxel[0], voxel[1], voxel[2],
				bounds[0], bounds[1], bounds[2]);
	}

	private static List<int[]> get6NeighbourhoodCoordinates(final int x0,
		final int y0, final int z0, final int w, final int h, final int d)
	{
		return Stream.of(new int[] { x0 - 1, y0, z0 }, new int[] { x0 + 1, y0, z0 },
			new int[] { x0, y0 - 1, z0 }, new int[] { x0, y0 + 1, z0 }, new int[] {
				x0, y0, z0 - 1 }, new int[] { x0, y0, z0 + 1 }).filter(
					a -> withinBounds(a[0], a[1], a[2], w, h, d)).collect(Collectors
						.toList());
	}

	/**
	 * create a binary ImagePlus containing a single particle and which 'just
	 * fits' the particle
	 *
	 * @param p The particle ID to get
	 * @param imp original image, used for calibration
	 * @param particleLabels work array of particle labels
	 * @param limits x,y and z limits of each particle
	 * @param padding amount of empty space to pad around each particle
	 * @return a cropped single particle image.
	 */
	private static ImagePlus getBinaryParticle(final int p, final ImagePlus imp,
		final int[][] particleLabels, final int[][] limits, final int padding)
	{

		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int xMin = Math.max(0, limits[p][0] - padding);
		final int xMax = Math.min(w - 1, limits[p][1] + padding);
		final int yMin = Math.max(0, limits[p][2] - padding);
		final int yMax = Math.min(h - 1, limits[p][3] + padding);
		final int zMin = Math.max(0, limits[p][4] - padding);
		final int zMax = Math.min(d - 1, limits[p][5] + padding);
		final int stackWidth = xMax - xMin + 1;
		final int stackHeight = yMax - yMin + 1;
		final int stackSize = stackWidth * stackHeight;
		final ImageStack stack = new ImageStack(stackWidth, stackHeight);
		for (int z = zMin; z <= zMax; z++) {
			final byte[] slice = new byte[stackSize];
			int i = 0;
			for (int y = yMin; y <= yMax; y++) {
				final int sourceIndex = y * w;
				for (int x = xMin; x <= xMax; x++) {
					if (particleLabels[z][sourceIndex + x] == p) {
						slice[i] = (byte) 0xFF;
					}
					i++;
				}
			}
			stack.addSlice(imp.getStack().getSliceLabel(z + 1), slice);
		}
		final ImagePlus binaryImp = new ImagePlus("Particle_" + p, stack);
		final Calibration cal = imp.getCalibration();
		binaryImp.setCalibration(cal);
		return binaryImp;
	}

	/**
	 * Get the centroids of all the particles in real units
	 *
	 * @param imp an image.
	 * @param particleLabels particles in the image.
	 * @param particleSizes sizes of the particles
	 * @return double[][] containing all the particles' centroids
	 */
	private static double[][] getCentroids(final ImagePlus imp,
		final int[][] particleLabels, final long[] particleSizes)
	{
		final int nParticles = particleSizes.length;
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final double[][] sums = new double[nParticles][3];
		for (int z = 0; z < d; z++) {
			for (int y = 0; y < h; y++) {
				final int index = y * w;
				for (int x = 0; x < w; x++) {
					final int particle = particleLabels[z][index + x];
					sums[particle][0] += x;
					sums[particle][1] += y;
					sums[particle][2] += z;
				}
			}
		}
		final Calibration cal = imp.getCalibration();
		final double[][] centroids = new double[nParticles][3];
		for (int p = 0; p < nParticles; p++) {
			centroids[p][0] = cal.pixelWidth * sums[p][0] / particleSizes[p];
			centroids[p][1] = cal.pixelHeight * sums[p][1] / particleSizes[p];
			centroids[p][2] = cal.pixelDepth * sums[p][2] / particleSizes[p];
		}
		return centroids;
	}

	private static EigenvalueDecomposition[] getEigens(final ImagePlus imp,
		final int[][] particleLabels, final double[][] centroids)
	{
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final double voxVhVd = (vH * vH + vD * vD) / 12;
		final double voxVwVd = (vW * vW + vD * vD) / 12;
		final double voxVhVw = (vH * vH + vW * vW) / 12;
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int nParticles = centroids.length;
		final EigenvalueDecomposition[] eigens =
			new EigenvalueDecomposition[nParticles];
		final double[][] momentTensors = new double[nParticles][6];
		for (int z = 0; z < d; z++) {
			IJ.showStatus("Calculating particle moments...");
			IJ.showProgress(z, d);
			final double zVd = z * vD;
			for (int y = 0; y < h; y++) {
				final double yVh = y * vH;
				final int index = y * w;
				for (int x = 0; x < w; x++) {
					final int p = particleLabels[z][index + x];
					if (p > 0) {
						final double xVw = x * vW;
						final double dx = xVw - centroids[p][0];
						final double dy = yVh - centroids[p][1];
						final double dz = zVd - centroids[p][2];
						momentTensors[p][0] += dy * dy + dz * dz + voxVhVd; // Ixx
						momentTensors[p][1] += dx * dx + dz * dz + voxVwVd; // Iyy
						momentTensors[p][2] += dy * dy + dx * dx + voxVhVw; // Izz
						momentTensors[p][3] += dx * dy; // Ixy
						momentTensors[p][4] += dx * dz; // Ixz
						momentTensors[p][5] += dy * dz; // Iyz
					}
				}
			}
			for (int p = 1; p < nParticles; p++) {
				final double[][] inertiaTensor = new double[3][3];
				inertiaTensor[0][0] = momentTensors[p][0];
				inertiaTensor[1][1] = momentTensors[p][1];
				inertiaTensor[2][2] = momentTensors[p][2];
				inertiaTensor[0][1] = -momentTensors[p][3];
				inertiaTensor[0][2] = -momentTensors[p][4];
				inertiaTensor[1][0] = -momentTensors[p][3];
				inertiaTensor[1][2] = -momentTensors[p][5];
				inertiaTensor[2][0] = -momentTensors[p][4];
				inertiaTensor[2][1] = -momentTensors[p][5];
				final Matrix inertiaTensorMatrix = new Matrix(inertiaTensor);
				final EigenvalueDecomposition E = new EigenvalueDecomposition(
					inertiaTensorMatrix);
				eigens[p] = E;
			}
		}
		return eigens;
	}

	private static Object[][] getEllipsoids(
		final Collection<List<Point3f>> surfacePoints)
	{
		final Object[][] ellipsoids = new Object[surfacePoints.size()][];
		int p = 0;
		for (final List<Point3f> points : surfacePoints) {
			if (points == null) {
				p++;
				continue;
			}
			final Iterator<Point3f> pointIter = points.iterator();
			final double[][] coOrdinates = new double[points.size()][3];
			int i = 0;
			while (pointIter.hasNext()) {
				final Point3f point = pointIter.next();
				coOrdinates[i][0] = point.x;
				coOrdinates[i][1] = point.y;
				coOrdinates[i][2] = point.z;
				i++;
			}
			try {
				ellipsoids[p] = FitEllipsoid.yuryPetrov(coOrdinates);
			}
			catch (final RuntimeException re) {
				IJ.log("Could not fit ellipsoid to surface " + p);
			}
			p++;
		}
		return ellipsoids;
	}

	/**
	 * Get the Euler characteristic of each particle
	 *
	 * @param imp an image.
	 * @param particleLabels particles of the image.
	 * @param limits limits of the particles.
	 * @param nParticles number particles.
	 * @return euler characteristic of each image.
	 */
	private double[][] getEulerCharacter(final ImagePlus imp,
		final int[][] particleLabels, final int[][] limits, final int nParticles)
	{
		final Connectivity con = new Connectivity();
		final double[][] eulerCharacters = new double[nParticles][3];
		for (int p = 1; p < nParticles; p++) {
			final ImagePlus particleImp = getBinaryParticle(p, imp, particleLabels,
				limits, 1);
			final double euler = con.getSumEuler(particleImp);
			final double cavities = getNCavities(particleImp);
			// Calculate number of holes and cavities using
			// Euler = particles - holes + cavities
			// where particles = 1
			final double holes = cavities - euler + 1;
			final double[] bettis = { euler, holes, cavities };
			eulerCharacters[p] = bettis;
		}
		return eulerCharacters;
	}

	/**
	 * Get the Feret diameter of a surface. Uses an inefficient brute-force
	 * algorithm.
	 *
	 * @param particleSurfaces points of all the particles.
	 * @return Feret diameters of the surfaces.
	 */
	private static double[] getFerets(
		final List<List<Point3f>> particleSurfaces)
	{
		final int nParticles = particleSurfaces.size();
		final double[] ferets = new double[nParticles];
		final ListIterator<List<Point3f>> it = particleSurfaces.listIterator();
		int i = 0;
		Point3f a;
		Point3f b;
		List<Point3f> surface;
		ListIterator<Point3f> ita;
		ListIterator<Point3f> itb;
		while (it.hasNext()) {
			IJ.showStatus("Finding Feret diameter...");
			IJ.showProgress(it.nextIndex(), nParticles);
			surface = it.next();
			if (surface == null) {
				ferets[i] = Double.NaN;
				i++;
				continue;
			}
			ita = surface.listIterator();
			while (ita.hasNext()) {
				a = ita.next();
				itb = surface.listIterator(ita.nextIndex());
				while (itb.hasNext()) {
					b = itb.next();
					ferets[i] = Math.max(ferets[i], a.distance(b));
				}
			}
			i++;
		}
		return ferets;
	}

	/**
	 * Get the maximum distances from the centroid in x, y, and z axes, and
	 * transformed x, y and z axes
	 *
	 * @param imp an image.
	 * @param particleLabels labelled particles in the image.
	 * @param centroids centroids of the particles.
	 * @param E transformation eigenvectors and values of the particles.
	 * @return array containing two nPoints * 3 arrays with max and max
	 *         transformed distances respectively
	 */
	private static Object[] getMaxDistances(final ImagePlus imp,
		final int[][] particleLabels, final double[][] centroids,
		final EigenvalueDecomposition[] E)
	{
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int nParticles = centroids.length;
		final double[][] maxD = new double[nParticles][3];
		final double[][] maxDt = new double[nParticles][3];
		for (int z = 0; z < d; z++) {
			for (int y = 0; y < h; y++) {
				final int index = y * w;
				for (int x = 0; x < w; x++) {
					final int p = particleLabels[z][index + x];
					if (p > 0) {
						final double dX = x * vW - centroids[p][0];
						final double dY = y * vH - centroids[p][1];
						final double dZ = z * vD - centroids[p][2];
						maxD[p][0] = Math.max(maxD[p][0], Math.abs(dX));
						maxD[p][1] = Math.max(maxD[p][1], Math.abs(dY));
						maxD[p][2] = Math.max(maxD[p][2], Math.abs(dZ));
						final double[][] eV = E[p].getV().getArray();
						final double dXt = dX * eV[0][0] + dY * eV[0][1] + dZ * eV[0][2];
						final double dYt = dX * eV[1][0] + dY * eV[1][1] + dZ * eV[1][2];
						final double dZt = dX * eV[2][0] + dY * eV[2][1] + dZ * eV[2][2];
						maxDt[p][0] = Math.max(maxDt[p][0], Math.abs(dXt));
						maxDt[p][1] = Math.max(maxDt[p][1], Math.abs(dYt));
						maxDt[p][2] = Math.max(maxDt[p][2], Math.abs(dZt));
					}
				}
			}
		}
		for (int p = 0; p < nParticles; p++) {
			Arrays.sort(maxDt[p]);
			final double[] temp = new double[3];
			for (int i = 0; i < 3; i++) {
				temp[i] = maxDt[p][2 - i];
			}
			maxDt[p] = temp.clone();
		}
		return new Object[] { maxD, maxDt };
	}

	/**
	 * Get the mean and standard deviation of pixel values &gt;0 for each particle
	 * in a particle label work array
	 *
	 * @param imp Input image containing pixel values
	 * @param particleLabels workArray containing particle labels
	 * @param particleSizes array of particle sizes as pixel counts
	 * @return array containing mean, std dev and max pixel values for each
	 *         particle
	 */
	private static double[][] getMeanStdDev(final ImagePlus imp,
		final int[][] particleLabels, final long[] particleSizes)
	{
		final int nParticles = particleSizes.length;
		final int d = imp.getImageStackSize();
		final int wh = imp.getWidth() * imp.getHeight();
		final ImageStack stack = imp.getImageStack();
		final double[] sums = new double[nParticles];
		for (int z = 0; z < d; z++) {
			final float[] pixels = (float[]) stack.getPixels(z + 1);
			final int[] labelPixels = particleLabels[z];
			for (int i = 0; i < wh; i++) {
				final double value = pixels[i];
				if (value > 0) {
					sums[labelPixels[i]] += value;
				}
			}
		}
		final double[][] meanStdDev = new double[nParticles][3];
		for (int p = 1; p < nParticles; p++) {
			meanStdDev[p][0] = sums[p] / particleSizes[p];
		}

		final double[] sumSquares = new double[nParticles];
		for (int z = 0; z < d; z++) {
			final float[] pixels = (float[]) stack.getPixels(z + 1);
			final int[] labelPixels = particleLabels[z];
			for (int i = 0; i < wh; i++) {
				final double value = pixels[i];
				if (value > 0) {
					final int p = labelPixels[i];
					final double residual = value - meanStdDev[p][0];
					sumSquares[p] += residual * residual;
					meanStdDev[p][2] = Math.max(meanStdDev[p][2], value);
				}
			}
		}
		for (int p = 1; p < nParticles; p++) {
			meanStdDev[p][1] = Math.sqrt(sumSquares[p] / particleSizes[p]);
		}
		return meanStdDev;
	}

	private int getNCavities(final ImagePlus imp) {
		final Object[] result = getParticles(imp, BACK);
		final long[] particleSizes = (long[]) result[2];
		final int nParticles = particleSizes.length;
		return nParticles - 2;
	}

	/**
	 * Find the offset within a 1D array given 2 (x, y) offset values
	 *
	 * @param m x difference
	 * @param n y difference
	 * @param w width of the stack.
	 * @return Integer offset for looking up pixel in work array
	 */
	private static int getOffset(final int m, final int n, final int w) {
		return m + n * w;
	}

	/**
	 * Get the minimum and maximum x, y and z coordinates of each particle
	 *
	 * @param imp ImagePlus (used for stack size)
	 * @param particleLabels work array containing labelled particles
	 * @param nParticles number of particles in the stack
	 * @return int[][] containing x, y and z minima and maxima.
	 */
	private static int[][] getParticleLimits(final ImagePlus imp,
		final int[][] particleLabels, final int nParticles)
	{
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int[][] limits = new int[nParticles][6];
		for (int i = 0; i < nParticles; i++) {
			limits[i][0] = Integer.MAX_VALUE; // x min
			limits[i][1] = 0; // x max
			limits[i][2] = Integer.MAX_VALUE; // y min
			limits[i][3] = 0; // y max
			limits[i][4] = Integer.MAX_VALUE; // z min
			limits[i][5] = 0; // z max
		}
		for (int z = 0; z < d; z++) {
			for (int y = 0; y < h; y++) {
				final int index = y * w;
				for (int x = 0; x < w; x++) {
					final int i = particleLabels[z][index + x];
					limits[i][0] = Math.min(limits[i][0], x);
					limits[i][1] = Math.max(limits[i][1], x);
					limits[i][2] = Math.min(limits[i][2], y);
					limits[i][3] = Math.max(limits[i][3], y);
					limits[i][4] = Math.min(limits[i][4], z);
					limits[i][5] = Math.max(limits[i][5], z);
				}
			}
		}
		return limits;
	}

	private ArrayList<ArrayList<short[]>> getParticleLists(
		final int[][] particleLabels, final int nBlobs, final int w, final int h,
		final int d)
	{
		final ArrayList<ArrayList<short[]>> pL = new ArrayList<>(nBlobs);
		pL.add(new ArrayList<>());
		final long[] particleSizes = getParticleSizes(particleLabels);
		for (int b = 1; b < nBlobs; b++) {
			pL.add(new ArrayList<>((int) particleSizes[b]));
		}
		// add all the particle coordinates to the appropriate list
		for (short z = 0; z < d; z++) {
			IJ.showStatus("Listing substructures...");
			IJ.showProgress(z, d);
			final int[] sliceLabels = particleLabels[z];
			for (short y = 0; y < h; y++) {
				final int i = y * w;
				for (short x = 0; x < w; x++) {
					final int p = sliceLabels[i + x];
					if (p > 0) { // ignore background
						final short[] voxel = { x, y, z };
						pL.get(p).add(voxel);
					}
				}
			}
		}
		return pL;
	}

	/**
	 * Get particles, particle labels and particle sizes from a 3D ImagePlus
	 *
	 * @param imp Binary input image
	 * @param slicesPerChunk number of slices per chunk. 2 is generally good.
	 * @param minVol minimum volume particle to include
	 * @param maxVol maximum volume particle to include
	 * @param phase foreground or background (FORE or BACK)
	 * @param doExclude if true, remove particles touching sides of the stack
	 * @return Object[] {byte[][], int[][]} containing a binary workArray and
	 *         particle labels.
	 */
	private Object[] getParticles(final ImagePlus imp, final int slicesPerChunk,
		final double minVol, final double maxVol, final int phase,
		final boolean doExclude)
	{
		final byte[][] workArray = makeWorkArray(imp);
		return getParticles(imp, workArray, slicesPerChunk, minVol, maxVol, phase,
			doExclude);
	}

	private Object[] getParticles(final ImagePlus imp, final int phase) {
		final byte[][] workArray = makeWorkArray(imp);
		final double minVol = 0;
		final double maxVol = Double.POSITIVE_INFINITY;
		return getParticles(imp, workArray, 4, minVol, maxVol, phase, false);
	}

	/**
	 * Get particles, particle labels and sizes from a workArray using an
	 * ImagePlus for scale information
	 *
	 * @param imp input binary image
	 * @param workArray work array
	 * @param slicesPerChunk number of slices to use for each chunk
	 * @param minVol minimum volume particle to include
	 * @param maxVol maximum volume particle to include
	 * @param phase FORE or BACK for foreground or background respectively
	 * @param doExclude exclude particles touching the edges.
	 * @return Object[] array containing a binary workArray, particle labels and
	 *         particle sizes
	 */
	private Object[] getParticles(final ImagePlus imp, final byte[][] workArray,
		final int slicesPerChunk, final double minVol, final double maxVol,
		final int phase, final boolean doExclude)
	{
		if (phase == FORE) {
			sPhase = "foreground";
		}
		else if (phase == BACK) {
			sPhase = "background";
		}
		else {
			throw new IllegalArgumentException();
		}
		if (slicesPerChunk < 1) {
			throw new IllegalArgumentException();
		}
		// Set up the chunks
		final int[][] particleLabels = firstIDAttribution(imp, workArray, phase);
		if (labelMethod == JOINING.MULTI) {
			// connect particles within chunks
			final int nChunks = getNChunks(imp, slicesPerChunk);
			final int nThreads = Runtime.getRuntime().availableProcessors();
			final ConnectStructuresThread[] cptf =
				new ConnectStructuresThread[nThreads];
			final int[][] chunkRanges = getChunkRanges(imp, nChunks, slicesPerChunk);
			for (int thread = 0; thread < nThreads; thread++) {
				cptf[thread] = new ConnectStructuresThread(thread, nThreads, imp,
					workArray, particleLabels, phase, nChunks, chunkRanges);
				cptf[thread].start();
			}
			try {
				for (int thread = 0; thread < nThreads; thread++) {
					cptf[thread].join();
				}
			}
			catch (final InterruptedException ie) {
				IJ.error("A thread was interrupted.");
			}
			// connect particles between chunks
			if (nChunks > 1) {
				chunkString = ": stitching...";
				final int[][] stitchRanges = getStitchRanges(imp, nChunks,
					slicesPerChunk);
				connectStructures(imp, workArray, particleLabels, phase, stitchRanges);
			}
		}
		else if (labelMethod == JOINING.LINEAR) {
			joinStructures(imp, particleLabels, phase);
		}
		else if (labelMethod == JOINING.MAPPED) {
			final int nParticles = getParticleSizes(particleLabels).length;
			joinMappedStructures(imp, particleLabels, nParticles, phase);
		}
		filterParticles(imp, workArray, particleLabels, minVol, maxVol, phase);
		if (doExclude) excludeOnEdges(imp, particleLabels, workArray);
		minimiseLabels(particleLabels);
		final long[] particleSizes = getParticleSizes(particleLabels);
		return new Object[] { workArray, particleLabels, particleSizes };

	}

	/**
	 * Get pixel in 3D image (0 border conditions)
	 *
	 * @param image 3D image
	 * @param x x- coordinate
	 * @param y y- coordinate
	 * @param z z- coordinate (in image stacks the indexes start at 1)
	 * @param w width of the image.
	 * @param h height of the image.
	 * @param d depth of the image.
	 * @return corresponding pixel (0 if out of image)
	 */
	private static int getPixel(final int[][] image, final int x, final int y,
		final int z, final int w, final int h, final int d)
	{
		if (withinBounds(x, y, z, w, h, d)) return image[z][x + y * w];

		return 0;
	}

	/**
	 * Return scan ranges for stitching. The first 2 values for each chunk are the
	 * first slice of the next chunk and the last 2 values are the range through
	 * which to replaceLabels() Running replace labels over incrementally
	 * increasing volumes as chunks are added is OK (for 1st interface connect
	 * chunks 0 & 1, for 2nd connect chunks 0, 1, 2, etc.)
	 *
	 * @param imp an image.
	 * @param nC number of chunks
	 * @param slicesPerChunk number of slices chunks process.
	 * @return scanRanges list of scan limits for connectStructures() to stitch
	 *         chunks back together
	 */
	private static int[][] getStitchRanges(final ImagePlus imp, final int nC,
		final int slicesPerChunk)
	{
		final int nSlices = imp.getImageStackSize();
		if (nC < 2) {
			return null;
		}
		final int[][] scanRanges = new int[4][3 * (nC - 1)]; // there are nC - 1
		// interfaces

		for (int c = 0; c < nC - 1; c++) {
			scanRanges[0][c] = (c + 1) * slicesPerChunk;
			scanRanges[1][c] = (c + 1) * slicesPerChunk + 1;
			scanRanges[2][c] = c * slicesPerChunk; // forward and reverse
			// algorithm
			// scanRanges[2][c] = 0; //cumulative algorithm - reliable but O²
			// hard
			scanRanges[3][c] = (c + 2) * slicesPerChunk;
		}
		// stitch back
		for (int c = nC - 1; c < 2 * (nC - 1); c++) {
			scanRanges[0][c] = (2 * nC - c - 2) * slicesPerChunk - 1;
			scanRanges[1][c] = (2 * nC - c - 2) * slicesPerChunk;
			scanRanges[2][c] = (2 * nC - c - 3) * slicesPerChunk;
			scanRanges[3][c] = (2 * nC - c - 1) * slicesPerChunk;
		}
		// stitch forwards (paranoid third pass)
		for (int c = 2 * (nC - 1); c < 3 * (nC - 1); c++) {
			scanRanges[0][c] = (-2 * nC + c + 3) * slicesPerChunk;
			scanRanges[1][c] = (-2 * nC + c + 3) * slicesPerChunk + 1;
			scanRanges[2][c] = (-2 * nC + c + 2) * slicesPerChunk;
			scanRanges[3][c] = (-2 * nC + c + 4) * slicesPerChunk;
		}
		for (int i = 0; i < scanRanges.length; i++) {
			for (int c = 0; c < scanRanges[i].length; c++) {
				if (scanRanges[i][c] > nSlices) {
					scanRanges[i][c] = nSlices;
				}
			}
		}
		scanRanges[3][nC - 2] = nSlices;
		return scanRanges;
	}

	/**
	 * Calculate surface area of the isosurface
	 *
	 * @param points in 3D triangle mesh
	 * @return surface area
	 */
	private static double getSurfaceArea(final List<Point3f> points) {
		if (points == null) {
			return 0;
		}
		double sumArea = 0;
		final int nPoints = points.size();
		final Point3f origin = new Point3f(0.0f, 0.0f, 0.0f);
		for (int n = 0; n < nPoints; n += 3) {
			IJ.showStatus("Calculating surface area...");
			final Point3f cp = crossProduct(points.get(n), points.get(n + 1), points
				.get(n + 2));
			final double deltaArea = 0.5 * cp.distance(origin);
			sumArea += deltaArea;
		}
		return sumArea;
	}

	private static double[] getSurfaceAreas(
		final Collection<List<Point3f>> surfacePoints)
	{
		return surfacePoints.stream().mapToDouble(ParticleCounter::getSurfaceArea)
			.toArray();
	}

	private static ArrayList<List<Point3f>> getSurfacePoints(final ImagePlus imp,
		final int[][] particleLabels, final int[][] limits, final int resampling,
		final int nParticles)
	{
		final Calibration cal = imp.getCalibration();
		final ArrayList<List<Point3f>> surfacePoints = new ArrayList<>();
		final boolean[] channels = { true, false, false };
		for (int p = 0; p < nParticles; p++) {
			IJ.showStatus("Getting surface meshes...");
			IJ.showProgress(p, nParticles);
			if (p > 0) {
				final ImagePlus binaryImp = getBinaryParticle(p, imp, particleLabels,
					limits, resampling);
				// noinspection TypeMayBeWeakened
				final MCTriangulator mct = new MCTriangulator();
				@SuppressWarnings("unchecked")
				final List<Point3f> points = mct.getTriangles(binaryImp, 128, channels,
					resampling);

				final double xOffset = (limits[p][0] - 1) * cal.pixelWidth;
				final double yOffset = (limits[p][2] - 1) * cal.pixelHeight;
				final double zOffset = (limits[p][4] - 1) * cal.pixelDepth;
				for (final Point3f point : points) {
					point.x += xOffset;
					point.y += yOffset;
					point.z += zOffset;
				}
				surfacePoints.add(points);
				if (points.isEmpty()) {
					IJ.log("Particle " + p + " resulted in 0 surface points");
				}
			}
			else {
				surfacePoints.add(null);
			}
		}
		return surfacePoints;
	}

	private static double[] getSurfaceVolume(
		final Collection<List<Point3f>> surfacePoints)
	{
		return surfacePoints.stream().mapToDouble(p -> {
			if (p == null) {
				return 0;
			}
			final CustomTriangleMesh mesh = new CustomTriangleMesh(p);
			return Math.abs(mesh.getVolume());
		}).toArray();
	}

	private static double[] getVolumes(final ImagePlus imp,
		final long[] particleSizes)
	{
		final Calibration cal = imp.getCalibration();
		final double voxelVolume = cal.pixelWidth * cal.pixelHeight *
			cal.pixelDepth;
		final int nLabels = particleSizes.length;
		final double[] particleVolumes = new double[nLabels];
		for (int i = 0; i < nLabels; i++) {
			particleVolumes[i] = voxelVolume * particleSizes[i];
		}
		return particleVolumes;
	}

	private static boolean isRadiiValid(final double[] radii) {
		for (int r = 0; r < 3; r++) {
			if (Double.isNaN(radii[r])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Join particle p to particle b, relabelling p with b.
	 *
	 * @param b a particle label.
	 * @param p another particle label.
	 * @param particleLabels array of particle labels
	 * @param particleLists list of particle voxel coordinates
	 * @param w stack width
	 */
	private static void joinBlobs(final int b, final int p,
		final int[][] particleLabels, final List<ArrayList<short[]>> particleLists,
		final int w)
	{
		for (final short[] voxelB : particleLists.get(p)) {
			particleLists.get(b).add(voxelB);
			final int iB = voxelB[1] * w + voxelB[0];
			particleLabels[voxelB[2]][iB] = b;
		}
		particleLists.get(p).clear();
	}

	private static void joinMappedStructures(final ImagePlus imp,
		final int[][] particleLabels, final int nParticles, final int phase)
	{
		IJ.showStatus("Mapping structures and joining...");
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();

		final ArrayList<HashSet<Integer>> map = new ArrayList<>(nParticles + 1);

		final int[] lut = new int[nParticles + 1];
		// set each label to be its own root
		final int initialCapacity = 1;
		for (int i = 0; i < nParticles + 1; i++) {
			lut[i] = i;
			final HashSet<Integer> set = new HashSet<>(initialCapacity);
			set.add(i);
			map.add(set);
		}

		// populate the first list with neighbourhoods
		int[] nbh = null;
		if (phase == FORE) nbh = new int[26];
		else if (phase == BACK) nbh = new int[6];
		for (int z = 0; z < d; z++) {
			IJ.showStatus("Building neighbourhood list");
			IJ.showProgress(z, d - 1);
			final int[] slice = particleLabels[z];
			for (int y = 0; y < h; y++) {
				final int yw = y * w;
				for (int x = 0; x < w; x++) {
					final int centre = slice[yw + x];
					// ignore background
					if (centre == 0) continue;
					if (phase == FORE) get26Neighborhood(nbh, particleLabels, x, y, z, w,
						h, d);
					else if (phase == BACK) get6Neighborhood(nbh, particleLabels, x, y, z,
						w, h, d);
					addNeighboursToMap(map, nbh, centre);
				}
			}
		}
		// map now contains for every value the set of first degree neighbours

		IJ.showStatus("Minimising list and generating LUT...");
		// place to store counts of each label
		final int[] counter = new int[lut.length];
		
		// place to map lut values and targets
		// lutList lists the indexes which point to each transformed lutvalue
		// for quick updating
		final ArrayList<HashSet<Integer>> lutList = new ArrayList<>(nParticles);

		// initialise the lutList
		for (int i = 0; i <= nParticles; i++) {
			lutList.add(new HashSet<>(2));
		}

		// set it up. ArrayList index is now the transformed value
		// list contains the lut indices that have the transformed value
		for (int i = 1; i < nParticles; i++) {
			final HashSet<Integer> list = lutList.get(lut[i]);
			list.add(i);
		}

		// initialise LUT with minimal label in set
		updateLUTwithMinPosition(lut, map, lutList);

		// find the minimal position of each value
		findFirstAppearance(lut, map);

		// de-chain the lut array
		minimiseLutArray(lut);

		int duplicates = Integer.MAX_VALUE;
		boolean snowball = true;
		boolean merge = true;
		boolean update = true;
		boolean find = true;
		boolean minimise = true;
		boolean inconsistent = true;
		while ((duplicates > 0) && snowball && merge && update && find &&
			minimise && inconsistent)
		{
			snowball = snowballLUT(lut, map, lutList);
			duplicates = countDuplicates(counter, map, lut);

			// merge duplicates
			merge = mergeDuplicates(map, counter, duplicates, lut, lutList);

			// update the LUT
			update = updateLUTwithMinPosition(lut, map, lutList);

			find = findFirstAppearance(lut, map);

			// minimise the LUT
			minimise = minimiseLutArray(lut);

			inconsistent = !checkConsistence(lut, map);
		}

		// replace all labels with LUT values
		applyLUT(particleLabels, lut, w, h, d);
		IJ.showStatus("LUT applied");
	}

	/**
	 * Joins semi-labelled particles using a non-recursive algorithm
	 *
	 * @param imp an image.
	 * @param particleLabels particles in the image.
	 * @param phase foreground or background
	 */
	private void joinStructures(final ImagePlus imp, final int[][] particleLabels,
		final int phase)
	{
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int[] bounds = { w, h, d };
		final long[] particleSizes = getParticleSizes(particleLabels);
		final int nBlobs = particleSizes.length;
		final ArrayList<ArrayList<short[]>> particleLists = getParticleLists(
			particleLabels, nBlobs, w, h, d);
		final BiFunction<int[], int[], List<int[]>> neighbourhoodFinder;
		if (phase == BACK) {
			neighbourhoodFinder = ParticleCounter::get6NeighbourhoodCoordinates;
		}
		else {
			neighbourhoodFinder = ParticleCounter::get26NeighbourhoodCoordinates;
		}
		for (int b = 1; b < nBlobs; b++) {
			IJ.showStatus("Joining substructures...");
			IJ.showProgress(b, nBlobs);
			final ArrayList<short[]> blob = particleLists.get(b);
			if (blob.isEmpty()) {
				continue;
			}
			for (final short[] voxel : blob) {
				final int[] tmp = { voxel[0], voxel[1], voxel[2] };
				final List<int[]> coordinates = neighbourhoodFinder.apply(tmp, bounds);
				for (final int[] coordinate : coordinates) {
					final int index = coordinate[1] * w + coordinate[0];
					final int label = particleLabels[coordinate[2]][index];
					if (label > b) {
						joinBlobs(b, label, particleLabels, particleLists, w);
					}
				}
			}
		}
	}

	/**
	 * Create a work array
	 *
	 * @param imp an image.
	 * @return byte[][] work array
	 */
	private static byte[][] makeWorkArray(final ImagePlus imp) {
		final int s = imp.getStackSize();
		final int p = imp.getWidth() * imp.getHeight();
		final byte[][] workArray = new byte[s][p];
		final ImageStack stack = imp.getStack();
		for (int z = 0; z < s; z++) {
			final ImageProcessor ip = stack.getProcessor(z + 1);
			for (int i = 0; i < p; i++) {
				workArray[z][i] = (byte) ip.get(i);
			}
		}
		return workArray;
	}

	private static boolean mergeDuplicates(final List<HashSet<Integer>> map,
		final int[] counter, final int duplicates, final int[] lut,
		final List<HashSet<Integer>> lutList)
	{
		boolean changed = false;
		// create a list of duplicate values to check for
		final int[] dupList = new int[duplicates];
		final int l = counter.length;
		int dup = 0;
		for (int i = 1; i < l; i++) {
			if (counter[i] > 1) dupList[dup] = i;
			dup++;
		}

		// find duplicates hiding in sets which are greater than the lut
		for (int i = 1; i < l; i++) {
			final HashSet<Integer> set = map.get(i);
			if (set.isEmpty()) continue;
			for (final int d : dupList) {
				// if we are in the lut key of this value, continue
				final int lutValue = lut[d];
				if (lutValue == i) continue;
				// otherwise check to see if the non-lut set contains our dup
				if (set.contains(d)) {
					// we found a dup, merge whole set back to lut
					changed = true;
					final Iterator<Integer> iter = set.iterator();
					final HashSet<Integer> target = map.get(lutValue);
					while (iter.hasNext()) {
						final Integer val = iter.next();
						target.add(val);
						lut[val] = lutValue;
					}
					// empty the set
					set.clear();
					updateLUT(i, lutValue, lut, lutList);
					// move to the next set
					break;
				}
			}
		}
		return changed;
	}

	/**
	 * Gets rid of redundant particle labels
	 *
	 * @param particleLabels particles in the image.
	 */
	private void minimiseLabels(final int[][] particleLabels) {
		IJ.showStatus("Minimising labels...");
		final int d = particleLabels.length;
		final long[] particleSizes = getParticleSizes(particleLabels);
		final int nLabels = particleSizes.length;
		final int[] newLabel = new int[nLabels];
		int minLabel = 0;
		// find the minimised labels
		for (int i = 0; i < nLabels; i++) {
			if (particleSizes[i] > 0) {
				if (i == minLabel) {
					newLabel[i] = i;
					minLabel++;
					continue;
				}
				newLabel[i] = minLabel;
				particleSizes[minLabel] = particleSizes[i];
				particleSizes[i] = 0;
				minLabel++;
			}
		}
		// now replace labels
		final int wh = particleLabels[0].length;
		for (int z = 0; z < d; z++) {
			IJ.showStatus("Replacing with minimised labels...");
			IJ.showProgress(z, d);
			final int[] slice = particleLabels[z];
			for (int i = 0; i < wh; i++) {
				final int p = slice[i];
				if (p > 0) {
					slice[i] = newLabel[p];
				}
			}
		}
	}

	private static boolean minimiseLutArray(final int[] lutArray) {
		final int l = lutArray.length;
		boolean changed = false;
		for (int key = 1; key < l; key++) {
			final int value = lutArray[key];
			// this is a root
			if (value == key) continue;
			// otherwise update the current key with the
			// value from the referred key
			final int minValue = lutArray[value];
			lutArray[key] = minValue;
			changed = true;
		}
		return changed;
	}

	/**
	 * Check whole array replacing m with n
	 *
	 * @param particleLabels particles labelled in the image.
	 * @param m value to be replaced
	 * @param n new value
	 * @param startZ first z coordinate to check
	 * @param endZ last+1 z coordinate to check
	 */
	private static void replaceLabel(final int[][] particleLabels, final int m,
		final int n, final int startZ, final int endZ)
	{
		final int s = particleLabels[0].length;
		for (int z = startZ; z < endZ; z++) {
			for (int i = 0; i < s; i++)
				if (particleLabels[z][i] == m) {
					particleLabels[z][i] = n;
				}
		}
	}

	private static void replaceLabelsWithMinimum(
		final Iterable<int[]> coordinates, final byte[][] workArray,
		final int[][] particleLabels, final int w, final int phase, final int minZ,
		final int maxZ)
	{
		final int minimumLabel = findMinimumLabel(coordinates, workArray,
			particleLabels, w, phase, 0);
		for (final int[] coordinate : coordinates) {
			final int vX = coordinate[0];
			final int vY = coordinate[1];
			final int vZ = coordinate[2];
			final int offset = getOffset(vX, vY, w);
			if (workArray[vZ][offset] == phase) {
				final int label = particleLabels[vZ][offset];
				if (label != 0 && label != minimumLabel) {
					replaceLabel(particleLabels, label, minimumLabel, minZ, maxZ);
				}
			}
		}
	}

	/**
	 * Iterate backwards over map entries, moving set values to their new lut
	 * positions in the map. Updates LUT value of shifted values
	 *
	 * @param lut a color look-up table.
	 * @param map a map of LUT values.
	 * @return false if nothing changed, true if something changed
	 */
	private static boolean snowballLUT(final int[] lut,
		final List<HashSet<Integer>> map, final List<HashSet<Integer>> lutList)
	{
		boolean changed = false;
		for (int i = lut.length - 1; i > 0; i--) {
			IJ.showStatus("Snowballing labels...");
			IJ.showProgress(lut.length - i + 1, lut.length);
			final int lutValue = lut[i];
			if (lutValue < i) {
				changed = true;
				final HashSet<Integer> set = map.get(i);
				final HashSet<Integer> target = map.get(lutValue);
				for (final Integer n : set) {
					target.add(n);
					lut[n] = lutValue;
				}
				// set is made empty
				// if later tests come across empty sets, then
				// must look up the lut to find the new location of the
				// neighbour network
				set.clear();
				// update lut so that anything pointing
				// to cleared set points to the new set
				updateLUT(i, lutValue, lut, lutList);
			}
		}
		return changed;
	}

	/**
	 * Replace old value with new value in LUT using map
	 *
	 * @param oldValue a LUT value to be replaced.
	 * @param newValue new value of the LUT value.
	 * @param lut a look-up table.
	 * @param lutlist hash of LUT values.
	 */
	private static void updateLUT(final int oldValue, final int newValue,
		final int[] lut, final List<HashSet<Integer>> lutlist)
	{
		final HashSet<Integer> list = lutlist.get(oldValue);
		final HashSet<Integer> newList = lutlist.get(newValue);

		for (final Integer in : list) {
			lut[in] = newValue;
			newList.add(in);
		}
		list.clear();
	}

	private static boolean updateLUTwithMinPosition(final int[] lut,
		final List<HashSet<Integer>> map, final List<HashSet<Integer>> lutList)
	{
		final int l = lut.length;
		final boolean changed = false;
		for (int i = 1; i < l; i++) {
			final HashSet<Integer> set = map.get(i);
			if (set.isEmpty()) continue;
			// find minimal value or lut value in the set
			int min = Integer.MAX_VALUE;
			int minLut = Integer.MAX_VALUE;
			for (final Integer val : set) {
				min = Math.min(min, val);
				minLut = Math.min(minLut, lut[val]);
			}
			// min now contains the smaller of the neighbours or their LUT
			// values
			min = Math.min(min, minLut);
			// add minimal value to lut
			final HashSet<Integer> target = map.get(min);
			for (final Integer val : set) {
				target.add(val);
				if (lut[val] > min) lut[val] = min;
			}
			set.clear();
			updateLUT(i, min, lut, lutList);
		}
		return changed;
	}

	private static boolean withinBounds(final int m, final int n, final int o,
		final int w, final int h, final int d)
	{
		return (m >= 0 && m < w && n >= 0 && n < h && o >= 0 && o < d);
	}

	/** Particle joining method */
	public enum JOINING {
			MULTI, LINEAR, MAPPED
	}

	private final class ConnectStructuresThread extends Thread {

		private final ImagePlus imp;
		private final int thread;
		private final int nThreads;
		private final int nChunks;
		private final int phase;
		private final byte[][] workArray;
		private final int[][] particleLabels;
		private final int[][] chunkRanges;

		private ConnectStructuresThread(final int thread, final int nThreads,
			final ImagePlus imp, final byte[][] workArray,
			final int[][] particleLabels, final int phase, final int nChunks,
			final int[][] chunkRanges)
		{
			this.imp = imp;
			this.thread = thread;
			this.nThreads = nThreads;
			this.workArray = workArray;
			this.particleLabels = particleLabels;
			this.phase = phase;
			this.nChunks = nChunks;
			this.chunkRanges = chunkRanges;
		}

		@Override
		public void run() {
			for (int k = thread; k < nChunks; k += nThreads) {
				// assign singleChunkRange for chunk k from chunkRanges
				final int[][] singleChunkRange = new int[4][1];
				for (int i = 0; i < 4; i++) {
					singleChunkRange[i][0] = chunkRanges[i][k];
				}
				chunkString = ": chunk " + (k + 1) + "/" + nChunks;
				connectStructures(imp, workArray, particleLabels, phase,
					singleChunkRange);
			}
		}
	}

	Object[] getParticles(final ImagePlus imp, final int slicesPerChunk,
		final int phase)
	{
		final byte[][] workArray = makeWorkArray(imp);
		return getParticles(imp, workArray, slicesPerChunk, 0.0,
			Double.POSITIVE_INFINITY, phase, false);
	}

	Object[] getParticles(final ImagePlus imp, final byte[][] workArray,
		final int slicesPerChunk, final int phase)
	{
		return getParticles(imp, workArray, slicesPerChunk, 0.0,
			Double.POSITIVE_INFINITY, phase, false);
	}

	/**
	 * Gets number of chunks needed to divide a stack into evenly-sized sets of
	 * slices.
	 *
	 * @param imp input image
	 * @param slicesPerChunk number of slices per chunk
	 * @return number of chunks
	 */
	static int getNChunks(final ImagePlus imp, final int slicesPerChunk) {
		final double d = imp.getImageStackSize();
		return (int) Math.ceil(d / slicesPerChunk);
	}

	/**
	 * Get a 2 d array that defines the z-slices to scan within while connecting
	 * particles within chunkified stacks.
	 *
	 * @param imp an image.
	 * @param nC number of chunks
	 * @param slicesPerChunk number of slices chunks process.
	 * @return scanRanges int[][] containing 4 limits: int[0][] - start of outer
	 *         for; int[1][] end of outer for; int[3][] start of inner for; int[4]
	 *         end of inner 4. Second dimension is chunk number.
	 */
	static int[][] getChunkRanges(final ImagePlus imp, final int nC,
		final int slicesPerChunk)
	{
		final int nSlices = imp.getImageStackSize();
		final int[][] scanRanges = new int[4][nC];
		scanRanges[0][0] = 0; // the first chunk starts at the first (zeroth)
		// slice
		scanRanges[2][0] = 0; // and that is what replaceLabel() will work on
		// first

		if (nC == 1) {
			scanRanges[1][0] = nSlices;
			scanRanges[3][0] = nSlices;
		}
		else if (nC > 1) {
			scanRanges[1][0] = slicesPerChunk;
			scanRanges[3][0] = slicesPerChunk;

			for (int c = 1; c < nC; c++) {
				for (int i = 0; i < 4; i++) {
					scanRanges[i][c] = scanRanges[i][c - 1] + slicesPerChunk;
				}
			}
			// reduce the last chunk to nSlices
			scanRanges[1][nC - 1] = nSlices;
			scanRanges[3][nC - 1] = nSlices;
		}
		return scanRanges;
	}

	/**
	 * Get the sizes of all the particles as a voxel count
	 *
	 * @param particleLabels particles in the image.
	 * @return particleSizes sizes of the particles.
	 */
	long[] getParticleSizes(final int[][] particleLabels) {
		IJ.showStatus("Getting " + sPhase + " particle sizes");
		final int d = particleLabels.length;
		final int wh = particleLabels[0].length;
		// find the highest value particleLabel
		int maxParticle = 0;
		for (final int[] slice : particleLabels) {
			for (int i = 0; i < wh; i++) {
				maxParticle = Math.max(maxParticle, slice[i]);
			}
		}

		final long[] particleSizes = new long[maxParticle + 1];
		for (int z = 0; z < d; z++) {
			final int[] slice = particleLabels[z];
			for (int i = 0; i < wh; i++) {
				particleSizes[slice[i]]++;
			}
			IJ.showProgress(z, d);
		}
		return particleSizes;
	}

	/**
	 * Set the value of this instance's labelMethod field
	 *
	 * @param label one of ParticleCounter.MULTI or .LINEAR
	 */
	void setLabelMethod(final JOINING label) {
		labelMethod = label;
	}
}
