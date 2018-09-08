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

import static org.bonej.plugins.ParticleCounter.JOINING.LINEAR;
import static org.bonej.plugins.ParticleCounter.JOINING.MAPPED;
import static org.bonej.plugins.ParticleCounter.JOINING.MULTI;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.TextField;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.plugins.ParticleCounter.JOINING;
import org.bonej.util.DialogModifier;
import org.bonej.util.ImageCheck;
import org.bonej.util.Multithreader;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
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
 * <a href="http://dx.doi.org/10.1016/8756-3282(93)90245-6">doi:10.1016
 * /8756-3282(93)90245-6</a>
 * </p>
 *
 * @author Michael Doube
 * @version 1.0
 */
public class Purify implements PlugIn, DialogListener {

	@Override
	public boolean dialogItemChanged(final GenericDialog gd, final AWTEvent e) {
		if (DialogModifier.hasInvalidNumber(gd.getNumericFields())) return false;
		final List<?> choices = gd.getChoices();
		final List<?> numbers = gd.getNumericFields();
		final Choice choice = (Choice) choices.get(0);
		final TextField num = (TextField) numbers.get(0);
		if (choice.getSelectedItem().contentEquals("Multithreaded")) {
			num.setEnabled(true);
		}
		else {
			num.setEnabled(false);
		}
		DialogModifier.registerMacroValues(gd, gd.getComponents());
		return true;
	}

	@Override
	public void run(final String arg) {
		final ImagePlus imp = IJ.getImage();
		if (!ImageCheck.isBinary(imp)) {
			IJ.error("Purify requires a binary image");
			return;
		}
		final GenericDialog gd = new GenericDialog("Setup");
		final String[] items = { "Multithreaded", "Linear", "Mapped" };
		gd.addChoice("Labelling algorithm", items, items[2]);
		gd.addNumericField("Chunk Size", 4, 0, 4, "slices");
		gd.addCheckbox("Performance Log", false);
		gd.addCheckbox("Make_copy", true);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		final String choice = gd.getNextChoice();
		final JOINING labelMethod;
		if (choice.equals(items[0])) labelMethod = MULTI;
		else if (choice.equals(items[1])) labelMethod = LINEAR;
		else labelMethod = MAPPED;
		final int slicesPerChunk = (int) Math.floor(gd.getNextNumber());
		final boolean showPerformance = gd.getNextBoolean();
		final boolean doCopy = gd.getNextBoolean();
		final long startTime = System.currentTimeMillis();
		final ImagePlus purified = purify(imp, slicesPerChunk, labelMethod);
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
			if (labelMethod == LINEAR) {
				showResults(duration, imp, imp.getImageStackSize(), LINEAR);
			}
			else {
				showResults(duration, imp, slicesPerChunk, labelMethod);
			}
		}
		UsageReporter.reportEvent(this).send();
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
		final int fg = ParticleCounter.FORE;
		final int bg = ParticleCounter.BACK;
		long maxVC = 0;
		final int nPartSizes = particleSizes.length;
		for (int i = 1; i < nPartSizes; i++) {
			if (particleSizes[i] > maxVC) {
				maxVC = particleSizes[i];
			}
		}
		final long maxVoxCount = maxVC;
		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				if (phase == fg) {
					// go through work array and turn all
					// smaller foreground particles into background (0)
					for (int z = ai.getAndIncrement(); z < d; z = ai.getAndIncrement()) {
						for (int i = 0; i < wh; i++) {
							if (workArray[z][i] == fg) {
								if (particleSizes[particleLabels[z][i]] < maxVoxCount) {
									workArray[z][i] = bg;
								}
							}
						}
						IJ.showStatus("Removing foreground particles");
						IJ.showProgress(z, d);
					}
				}
				else if (phase == bg) {
					// go through work array and turn all
					// smaller background particles into foreground
					for (int z = ai.getAndIncrement(); z < d; z = ai.getAndIncrement()) {
						for (int i = 0; i < wh; i++) {
							if (workArray[z][i] == bg) {
								if (particleSizes[particleLabels[z][i]] < maxVoxCount) {
									workArray[z][i] = fg;
								}
							}
						}
						IJ.showStatus("Removing background particles");
						IJ.showProgress(z, d);
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);
	}

	/**
	 * Check whole array replacing m with n
	 *
	 * @param particleLabels particle labels in the image.
	 * @param m value to be replaced
	 * @param n new value
	 * @param endZ last+1 z coordinate to check
	 */
	private static void replaceLabel(final int[][] particleLabels, final int m,
		final int n, final int endZ)
	{
		final int s = particleLabels[0].length;
		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z < endZ; z = ai.getAndIncrement()) {
					for (int i = 0; i < s; i++)
						if (particleLabels[z][i] == m) {
							particleLabels[z][i] = n;
						}
				}
			});
		}
		Multithreader.startAndJoin(threads);
	}

	/**
	 * Show a Results table containing some performance information
	 *
	 * @param duration time elapsed in purifying.
	 * @param imp the purified image.
	 * @param slicesPerChunk slices processed by each chunk.
	 * @param labelMethod labelling method used.
	 */
	private static void showResults(final double duration, final ImagePlus imp,
		final int slicesPerChunk, final JOINING labelMethod)
	{
		final int nChunks = ParticleCounter.getNChunks(imp, slicesPerChunk);
		final int[][] chunkRanges = ParticleCounter.getChunkRanges(imp, nChunks,
			slicesPerChunk);
		final ResultsTable rt = ResultsTable.getResultsTable();
		rt.incrementCounter();
		rt.addLabel(imp.getTitle());
		rt.addValue("Algorithm", labelMethod.ordinal());
		rt.addValue("Threads", Runtime.getRuntime().availableProcessors());
		rt.addValue("Slices", imp.getImageStackSize());
		rt.addValue("Chunks", nChunks);
		rt.addValue("Chunk size", slicesPerChunk);
		rt.addValue("Last chunk size", chunkRanges[1][nChunks - 1] -
			chunkRanges[0][nChunks - 1]);
		rt.addValue("Duration (s)", duration);
		rt.show("Results");
	}

	/**
	 * <p>
	 * Find particles of phase that touch the stack sides and assign them the ID
	 * of the biggest particle of phase. Euler number calculation assumes that the
	 * background phase is connected outside the image stack, so apparently
	 * isolated background particles touching the sides should be assigned to the
	 * single background particle.
	 * </p>
	 *
	 * @param workArray a work array
	 * @param particleLabels particle labels.
	 * @param particleSizes sizes of the particles.
	 */
	private static void touchEdges(final ImagePlus imp, final byte[][] workArray,
		final int[][] particleLabels, final long[] particleSizes)
	{
		final String status = "Background particles touching ";
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		// find the label associated with the biggest
		// particle in phase
		long maxVoxCount = 0;
		int bigP = 0;
		final int nPartSizes = particleSizes.length;
		for (int i = 0; i < nPartSizes; i++) {
			if (particleSizes[i] > maxVoxCount) {
				maxVoxCount = particleSizes[i];
				bigP = i;
			}
		}
		final int biggestParticle = bigP;
		// check each face of the stack for pixels that are touching edges and
		// replace that particle's label in particleLabels with
		// the label of the biggest particle
		int x;
		int y;
		int z;

		// up
		z = 0;
		for (y = 0; y < h; y++) {
			IJ.showStatus(status + "top");
			IJ.showProgress(y, h);
			final int rowOffset = y * w;
			for (x = 0; x < w; x++) {
				final int offset = rowOffset + x;
				if (workArray[z][offset] == 0 &&
					particleLabels[z][offset] != biggestParticle)
				{
					replaceLabel(particleLabels, particleLabels[z][offset],
						biggestParticle, d);
				}
			}
		}

		// down
		z = d - 1;
		for (y = 0; y < h; y++) {
			IJ.showStatus(status + "bottom");
			IJ.showProgress(y, h);
			final int rowOffset = y * w;
			for (x = 0; x < w; x++) {
				final int offset = rowOffset + x;
				if (workArray[z][offset] == 0 &&
					particleLabels[z][offset] != biggestParticle)
				{
					replaceLabel(particleLabels, particleLabels[z][offset],
						biggestParticle, d);
				}
			}
		}

		// left
		for (z = 0; z < d; z++) {
			IJ.showStatus(status + "left");
			IJ.showProgress(z, d);
			for (y = 0; y < h; y++) {
				final int offset = y * w;
				if (workArray[z][offset] == 0 &&
					particleLabels[z][offset] != biggestParticle)
				{
					replaceLabel(particleLabels, particleLabels[z][offset],
						biggestParticle, d);
				}
			}
		}

		// right
		for (z = 0; z < d; z++) {
			IJ.showStatus(status + "right");
			IJ.showProgress(z, d);
			for (y = 0; y < h; y++) {
				final int offset = y * w + w - 1;
				if (workArray[z][offset] == 0 &&
					particleLabels[z][offset] != biggestParticle)
				{
					replaceLabel(particleLabels, particleLabels[z][offset],
						biggestParticle, d);
				}
			}
		}

		// front
		final int rowOffset = (h - 1) * w;
		for (z = 0; z < d; z++) {
			IJ.showStatus(status + "front");
			IJ.showProgress(z, d);
			for (x = 0; x < w; x++) {
				final int offset = rowOffset + x;
				if (workArray[z][offset] == 0 &&
					particleLabels[z][offset] != biggestParticle)
				{
					replaceLabel(particleLabels, particleLabels[z][offset],
						biggestParticle, d);
				}
			}
		}

		// back
		for (z = 0; z < d; z++) {
			IJ.showStatus(status + "back");
			IJ.showProgress(z, d);
			for (x = 0; x < w; x++) {
				if (workArray[z][x] == 0 && particleLabels[z][x] != biggestParticle) {
					replaceLabel(particleLabels, particleLabels[z][x], biggestParticle,
						d);
				}
			}
		}
	}

	/**
	 * Find all foreground and particles in an image and remove all but the
	 * largest. Foreground is 26-connected and background is 8-connected.
	 *
	 * @param imp input image
	 * @param slicesPerChunk number of slices to send to each CPU core as a chunk
	 * @param labelMethod number of labelling method
	 * @return purified image
	 */
	static ImagePlus purify(final ImagePlus imp, final int slicesPerChunk,
		final JOINING labelMethod)
	{

		final ParticleCounter pc = new ParticleCounter();
		pc.setLabelMethod(labelMethod);

		final int fg = ParticleCounter.FORE;
		final Object[] foregroundParticles = pc.getParticles(imp, slicesPerChunk,
			fg);
		final byte[][] workArray = (byte[][]) foregroundParticles[0];
		int[][] particleLabels = (int[][]) foregroundParticles[1];
		// index 0 is background particle's size...
		long[] particleSizes = pc.getParticleSizes(particleLabels);
		removeSmallParticles(workArray, particleLabels, particleSizes, fg);

		final int bg = ParticleCounter.BACK;
		final Object[] backgroundParticles = pc.getParticles(imp, workArray,
			slicesPerChunk, bg);
		particleLabels = (int[][]) backgroundParticles[1];
		particleSizes = pc.getParticleSizes(particleLabels);
		touchEdges(imp, workArray, particleLabels, particleSizes);
		particleSizes = pc.getParticleSizes(particleLabels);
		removeSmallParticles(workArray, particleLabels, particleSizes, bg);

		final ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
		final int nSlices = workArray.length;
		for (int z = 0; z < nSlices; z++) {
			stack.addSlice(imp.getStack().getSliceLabel(z + 1), workArray[z]);
		}
		final ImagePlus purified = new ImagePlus("Purified", stack);
		purified.setCalibration(imp.getCalibration());
		IJ.showStatus("Image Purified");
		IJ.showProgress(1.0);
		return purified;
	}
}
