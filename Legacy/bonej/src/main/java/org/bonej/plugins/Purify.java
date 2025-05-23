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

import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.util.ImageCheck;
import org.bonej.util.Multithreader;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;

/**
 * <p>
 * Purify_ plugin for ImageJ
 * </p>
 * <p>
 * Prepare binary stack for connectivity analysis by reducing number of
 * reference phase (foreground) particles to 1, filling cavities within the
 * single reference phase particle and ensuring there is only 1 particle in the
 * background phase.
 * </p>
 * <p>
 * Foreground is 26-connected and background is 6-connected.
 * </p>
 * <p>
 * Odgaard A, Gundersen HJG (1993) Quantification of connectivity in cancellous
 * bone, with special emphasis on 3-D reconstructions. Bone 14: 173-182.
 * <a href="https://doi.org/10.1016/8756-3282(93)90245-6">doi:10.1016
 * /8756-3282(93)90245-6</a>
 * </p>
 *
 * @author Michael Doube
 * @version 1.0
 */
public class Purify implements PlugIn {

	@Override
	public void run(final String arg) {
		final ImagePlus imp = IJ.getImage();
		if (!ImageCheck.isBinary(imp)) {
			IJ.error("Purify requires a binary image");
			return;
		}
		final GenericDialog gd = new GenericDialog("Setup");
		gd.addCheckbox("Performance Log", false);
		gd.addCheckbox("Make_copy", true);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		final boolean showPerformance = gd.getNextBoolean();
		final boolean doCopy = gd.getNextBoolean();
		final long startTime = System.currentTimeMillis();
		final ImagePlus purified = purify(imp);
		if (null != purified) {
			if (doCopy) {
				purified.show();
				if (imp.isInvertedLut() && !purified.isInvertedLut()) IJ.run(
					"Invert LUT");
			}
			else {
				imp.setStack(null, purified.getStack());
				if (!imp.isInvertedLut()) IJ.run("Invert LUT");
			}
		}
		final double duration = (System.currentTimeMillis() - startTime) / 1000.0;
		if (showPerformance) {
			showResults(duration, imp);
		}
	}


	/**
	 * Show a Results table containing some performance information
	 *
	 * @param duration time elapsed in purifying.
	 * @param imp the purified image.
	 */
	private static void showResults(final double duration, final ImagePlus imp)
	{
		final ResultsTable rt = ResultsTable.getResultsTable();
		rt.incrementCounter();
		rt.addLabel(imp.getTitle());
		rt.addValue("Threads", Runtime.getRuntime().availableProcessors());
		rt.addValue("Slices", imp.getImageStackSize());
		rt.addValue("Duration (s)", duration);
		rt.show("Results");
	}

	/**
	 * Find all foreground and particles in an image and remove all but the
	 * largest. Foreground is 26-connected and background is 8-connected.
	 *
	 * @param imp input image
	 * @return purified image
	 */
	static ImagePlus purify(final ImagePlus imp)
	{

		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int nSlices = imp.getNSlices();
		
		final ConnectedComponents connector = new ConnectedComponents();
		final ParticleAnalysis pa = new ParticleAnalysis();

		int[][] particleLabels = connector.run(imp, ConnectedComponents.FORE);
		byte[][] workArray = connector.getWorkArray();
		final int nFgParticles = connector.getNParticles();
		//if there are no foreground particles, stop processing and return
		//there is always one particle, pixel value & label = 0, representing background
		if (nFgParticles == 1)
			return imp;
		
		// index 0 is background particle's size...
		long[] particleSizes = pa.getParticleSizes(particleLabels, nFgParticles);
		//no need to remove particles when there is only one foreground particle
		//>2 because label 0 is for background, label 1 is for the first foreground particle
		if (nFgParticles > 2)
			removeSmallParticles(workArray, particleLabels, particleSizes, ConnectedComponents.FORE);

		ImageStack stack = new ImageStack(w, h);
		for (int z = 0; z < nSlices; z++) {
			stack.addSlice(imp.getStack().getSliceLabel(z + 1), workArray[z]);
		}
		//halfPurifiedImp has only one big foreground particle
		ImagePlus halfPurifiedImp = new ImagePlus("Half Purified", stack);
			
		//particleLabels is now background particles of the half-purified image
		particleLabels = connector.run(halfPurifiedImp, ConnectedComponents.BACK);
		halfPurifiedImp = null;
		final int nBgParticles = connector.getNParticles();
		particleSizes = pa.getParticleSizes(particleLabels, nBgParticles);
		final int biggestParticle = getBiggestParticleLabel(particleSizes);
		final IntHashSet labelList = getParticlesTouchingEdges(particleLabels, w, h, nSlices);
		relabelEdgeTouchingParticles(particleLabels, labelList, biggestParticle);
		
		workArray = connector.getWorkArray();
		removeSmallParticles(workArray, particleLabels, particleSizes, ConnectedComponents.BACK);
		
		stack = new ImageStack(w, h);
		for (int z = 0; z < nSlices; z++) {
			stack.addSlice(imp.getStack().getSliceLabel(z + 1), workArray[z]);
		}
		final ImagePlus purified = new ImagePlus("Purified", stack);
		purified.setCalibration(imp.getCalibration());
		IJ.showStatus("Image Purified");
		IJ.showProgress(1.0);
		return purified;
	}
	
	/**
	 * Find the label of the largest particle in the image, excluding 0 (which is the opposite phase label).
	 * @param particleSizes
	 * @return label of the largest particle
	 */
	private static int getBiggestParticleLabel(final long[] particleSizes) {
		long max = 0;
		int biggestParticleLabel = 0;
		final int nPartSizes = particleSizes.length;
		for (int i = 1; i < nPartSizes; i++) {
			if (particleSizes[i] > max) {
				max = particleSizes[i];
				biggestParticleLabel = i;
			}
		}
		return biggestParticleLabel;
	}
	
	/**
	 * Get a list of labels for all the particles that touch the sides.
	 * 
	 * @param particleLabels
	 * @param w image width
	 * @param h image height
	 * @param d image depth
	 * @return list of particle IDs that touch the sides
	 */
	private static IntHashSet getParticlesTouchingEdges(final int[][] particleLabels,
		final int w, final int h, final int d) {
		
		final IntHashSet labelList = new IntHashSet();
		
		// scan faces
		// top and bottom faces
		final int[] top = particleLabels[0];
		final int[] bottom = particleLabels[d - 1];
		final int wh = top.length;
		for (int i = 0; i < wh; i++) {
			final int pt = top[i];
			if (pt > 0) {
				labelList.add(pt);
			}
			final int pb = bottom[i];
			if (pb > 0) {
				labelList.add(pb);
			}
		}

		// west and east faces
		// north and south faces
		final int lastRow = w * (h - 1);
		for (int z = 0; z < d; z++) {
			final int[] slice = particleLabels[z];
			for (int x = 0; x < w; x++) {
				final int pn = slice[x];
				final int ps = slice[lastRow + x];
				if (pn > 0) {
					labelList.add(pn);
				}
				if (ps > 0) {
					labelList.add(ps);
				}
			}
			for (int y = 0; y < h; y++) {
				final int yw = y * w;
				final int pw = slice[yw];
				final int pe = slice[yw + w - 1];
				if (pw > 0) {
					labelList.add(pw);
				}
				if (pe > 0) {
					labelList.add(pe);
				}
			}
		}
		return labelList;
	}
	
	/**
	 * Replace all particles in the label list with a new value
	 * 
	 * @param particleLabels
	 * @param labelList
	 * @param newLabel
	 */
	private static void relabelEdgeTouchingParticles(final int[][] particleLabels, final IntHashSet labelList,
		final int newLabel) {
		if (newLabel == 0 || newLabel < 0) {
			throw new IllegalArgumentException("replacement label cannot be 0 (that would be background) or < 0");
		}
		
		final int nSlices = particleLabels.length;
		final int wh = particleLabels[0].length;
		final Thread[] threads = Multithreader.newThreads();
		final AtomicInteger ai = new AtomicInteger(0);
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z < nSlices; z = ai.getAndIncrement()) {
					final int[] sliceParticleLabels = particleLabels[z];
					for (int i = 0; i < wh; i++) {
						final int particleID = sliceParticleLabels[i];
						if (particleID > 0) {
							if (labelList.contains(particleID)) {
								sliceParticleLabels[i] = newLabel;
							}
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);
	}
	
	/**
	 * Remove all but the largest phase particle from workArray
	 *
	 * @param workArray a work array
	 * @param particleLabels particle labels.
	 * @param particleSizes sizes of the particles.
	 * @param phase foreground or background.
	 */
	private static void removeSmallParticles(final byte[][] workArray,
		final int[][] particleLabels, final long[] particleSizes, final int phase)
	{
		final int d = workArray.length;
		final int wh = workArray[0].length;

		final int biggestParticleLabel = getBiggestParticleLabel(particleSizes);
		
		final int testPhase = phase;
		final byte replacementPhase = (byte) (phase == ConnectedComponents.FORE ? ConnectedComponents.BACK : ConnectedComponents.FORE);
		final String sPhase = phase == ConnectedComponents.FORE ? "foreground" : "background";
		
		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
					// go through work array and turn all
					// smaller foreground (255) particles into background (0) (or vice versa)
					for (int z = ai.getAndIncrement(); z < d; z = ai.getAndIncrement()) {
						for (int i = 0; i < wh; i++) {
							if (workArray[z][i] == testPhase) {
								if (particleLabels[z][i] != biggestParticleLabel) {
									workArray[z][i] = replacementPhase;
								}
							}
						}
						IJ.showStatus("Removing "+sPhase+" particles");
						IJ.showProgress(z, d);
					}
			});
		}
		Multithreader.startAndJoin(threads);
	}	
}
