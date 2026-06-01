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

import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.util.ImageCheck;
import org.bonej.util.Multithreader;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.BoneJCommand;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import net.imagej.Dataset;
import net.imagej.DatasetService;

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
 * * <p>
 * Python Usage Example (PyImageJ with SharedTable):
 * </p>
 * <pre>
 * from imagej import ImageJ
 *
 * # Initialize ImageJ2
 * ij = ImageJ()
 *
 * # Open a binary image
 * dataset = ij.io().open("path/to/binary_image.tif")
 *
 * # Run the Purify plugin with performance logging enabled
 * purify_command = ij.command().run(
 *     "org.bonej.plugins.Purify",
 *     {
 *         "inputDataset": dataset,
 *         "makeCopy": True,
 *         "showPerformance": True  # Enable performance logging
 *     }
 * )
 *
 * # Access the Results table (SharedTable) from BoneJ
 * # Note: SharedTable is a static utility in BoneJ, so we access it via the plugin's context.
 * # In PyImageJ, you can retrieve the Results table as a Python dictionary.
 * results_table = purify_command.getContext().getService("org.bonej.utilities.SharedTable").getTable()
 *
 * # Convert the Results table to a Python-friendly format (e.g., a dictionary)
 * # The SharedTable in BoneJ is typically a SciJava Table object, which can be converted to a list of rows.
 * # Here's how to extract the performance metrics for the purified image:
 * results_dict = {}
 * for row in results_table:
 *     image_title = row[0]  # First column: image title (e.g., "image_purified")
 *     metric_name = row[1]  # Second column: metric name (e.g., "Purify Threads")
 *     metric_value = row[2]  # Third column: metric value (e.g., 8)
 *     if image_title not in results_dict:
 *         results_dict[image_title] = {}
 *     results_dict[image_title][metric_name] = metric_value
 *
 * # Print the performance metrics for the purified image
 * purified_title = dataset.getName() + "_purified"  # Match the title used in the plugin
 * if purified_title in results_dict:
 *     print(f"Performance metrics for {purified_title}:")
 *     for metric, value in results_dict[purified_title].items():
 *         print(f"  {metric}: {value}")
 * else:
 *     print(f"No metrics found for {purified_title}")
 * </pre>
 *
 * @author Michael Doube
 * @version 2.0
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Purify")
public class Purify extends BoneJCommand implements Command {

	/* IJ2 parameters */
	@Parameter(type = ItemIO.BOTH)
    private Dataset inputDataset;
	
	@Parameter(type = ItemIO.OUTPUT, required = false)
	private Dataset outputDataset;
	
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
	
	@Parameter(label = "Performance Log",
			   description = "Show performance metrics in the Results table")
	private boolean showPerformance = false;
	
	@Parameter(label = "Make copy",
			   description = "Return the purified image as a new Dataset")
	private boolean makeCopy = true;
	
	/**
	 * Modern scijava Plugin entry point.
	 */
	@Override
	public void run() {
        ImagePlus imp = convertService.convert(inputDataset, ImagePlus.class);
        
        if (imp == null) {
            logService.error("Purify: Failed to convert Dataset to ImagePlus.");
            return;
        }
        
        if (!ImageCheck.isBinary(imp)) {
        	String errorMsg = "Purify requires a binary image. " +
        			"The provided image (" + imp.getTitle() + ") is not binary.";

        	// Log to console/file (safe in headless mode)
        	logService.error(errorMsg);

        	return;
		}
        
        final long startTime = System.currentTimeMillis();
        ImagePlus purified = purify(imp);
        
        if (purified != null) {
            
        	if (makeCopy) {
        		//create a new Dataset containing the purified image data
        		outputDataset = convertService.convert(purified, Dataset.class);
        		purified.close();
            	if (outputDataset == null) {
            		throw new RuntimeException("Failed to convert purified ImagePlus to Dataset");
            	}
            	
        		//if there is a UI, display the dataset
        		if (uiService != null && uiService.isVisible())
        			uiService.show(outputDataset);
            	
        	} else {
        		//replace the input image with the purified image and update the window title
        		convertService.convert(purified, Dataset.class).copyInto(inputDataset);
        		inputDataset.setName(purified.getTitle());
                if (displayService != null) {
                    displayService.getDisplays(inputDataset).forEach(display -> {
                        display.setName(purified.getTitle());
                    });
                }
        	}
        	
            if (showPerformance) {
                final double duration = (System.currentTimeMillis() - startTime) / 1000.0;
                showResults(duration, purified);
            }
        } else {
        	logService.error("purified ImagePlus was null");
        }
	}
	


	/**
	 * Show a Results table containing some performance information
	 *
	 * @param duration time elapsed in purifying.
	 * @param imp the purified image.
	 */
	private void showResults(final double duration, final ImagePlus imp)
	{
		SharedTable.add(imp.getTitle(), "Purify Threads", Runtime.getRuntime().availableProcessors());
		SharedTable.add(imp.getTitle(), "Slices", imp.getImageStackSize());
		SharedTable.add(imp.getTitle(), "Duration (s)", duration);
		resultsTable = SharedTable.getTable();
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
		final ImagePlus purified = new ImagePlus(imp.getTitle()+"_purified", stack);
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
