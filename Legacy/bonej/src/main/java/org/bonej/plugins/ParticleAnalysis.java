/*-
 * #%L
 * Mavenized version of the BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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
/*
BSD 2-Clause License
Copyright (c) 2020, Michael Doube
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.geometry.FitEllipsoid;
import org.bonej.util.Multithreader;
import org.scijava.vecmath.Point3f;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import customnode.CustomTriangleMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import marchingcubes.MCTriangulator;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * Perform analysis of particles
 * @author Michael Doube
 *
 */
public class ParticleAnalysis {

	/** list of particle sizes */
	private long[] particleSizes;

	/** Constructor */
	public ParticleAnalysis() {

	}

	// ------------- PIXELWISE OPERATIONS ------------------------------//

	/**
	 * Remove edge-touching, too small and too big particles.
	 * 
	 * 
	 * Relabels the particles to be continuous from 1 to maxParticle and updates
	 * particleSizes accordingly. workArray is updated too.
	 * 
	 * @param imp            Input image. Needed for calibration
	 * @param particleLabels Particle label image array
	 * @param workArray      Binary work array
	 * @param nParticles     Number of particles in the image
	 * @param phase          Foreground or background
	 * @param doExclude      true to remove all particles touching a side
	 * @param min            minimum volume in calibrated units to include
	 * @param max            minimum volume in calibrated units to include
	 */
	public void filterParticles(final ImagePlus imp, final int[][] particleLabels, final byte[][] workArray,
			int nParticles, final int phase, final boolean doExclude, final double min, final double max) {

		getParticleSizes(particleLabels, nParticles);

		// flag to check whether sizes & labels arrays need to be updated
		boolean runLutNeeded = false;

		// set up the replacement lut
		final int[] lut = new int[nParticles];
		for (int i = 0; i < nParticles; i++)
			lut[i] = i;

		if (min > 0 || max < Double.POSITIVE_INFINITY) {
			// do the size filtering check
			Calibration cal = imp.getCalibration();
			final double pxVol = cal.pixelDepth * cal.pixelHeight * cal.pixelWidth;

			for (int i = 0; i < nParticles; i++) {
				final double particleVolume = particleSizes[i] * pxVol;
				// if this particle is outside min & max limits
				if (particleVolume > max || particleVolume < min) {
					// set lut value to 0 schedules for deletion
					lut[i] = 0;
					runLutNeeded = true;
				}
			}
		}

		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();

		if (doExclude) {
			// do the edge filtering check. Set all edge particles to 0 in the lut

			// scan faces
			// top and bottom faces
			final int[] top = particleLabels[0];
			final int[] bottom = particleLabels[d - 1];
			final int wh = top.length;
			for (int i = 0; i < wh; i++) {
				final int pt = top[i];
				if (pt > 0) {
					lut[pt] = 0;
					runLutNeeded = true;
				}
				final int pb = bottom[i];
				if (pb > 0) {
					lut[pb] = 0;
					runLutNeeded = true;
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
						lut[pn] = 0;
						runLutNeeded = true;
					}
					if (ps > 0) {
						lut[ps] = 0;
						runLutNeeded = true;
					}
				}
				for (int y = 0; y < h; y++) {
					final int pw = slice[y * w];
					final int pe = slice[y * w + w - 1];
					if (pw > 0) {
						lut[pw] = 0;
						runLutNeeded = true;
					}
					if (pe > 0) {
						lut[pe] = 0;
						runLutNeeded = true;
					}
				}
			}
		}

		// check the arrays only if needed
		if (runLutNeeded) {

			// minimise the lut and count non-zeros
			int nonZeroCount = 0;
			for (int i = 0; i < nParticles; i++) {
				final int lutValue = lut[i];
				if (lutValue != 0) {
					nonZeroCount++;
					lut[i] = nonZeroCount;
				}
			}
			// lut is now 0 for particles to be deleted or a lower value to get rid of gaps

			// reset nParticles, +1 is for particle 0 (background particle)
			nParticles = nonZeroCount + 1;

			// replace particle sizes based on lut
			long[] filteredParticleSizes = new long[nParticles];

			final int l = particleSizes.length;
			for (int i = 0; i < l; i++) {
				final long size = particleSizes[i];
				final int lutValue = lut[i];
				if (lutValue != 0) {
					filteredParticleSizes[lutValue] = size;
				}
			}

			// particleSizes now has the shorter length to match the new particleLabels
			this.particleSizes = filteredParticleSizes;

			// replace labels based on lut

			// handle both phases in the workArray
			final byte flip;
			if (phase == ConnectedComponents.FORE) {
				flip = 0;
			} else {
				flip = (byte) 255;
			}

			final int wh = w * h;

			AtomicInteger ai = new AtomicInteger(0);

			final Thread[] threads = Multithreader.newThreads();
			for (int thread = 0; thread < threads.length; thread++) {
				threads[thread] = new Thread(() -> {
					for (int z = ai.getAndIncrement(); z < d; z = ai.getAndIncrement()) {
						final int[] particleLabelSlice = particleLabels[z];
						final byte[] workArraySlice = workArray[z];
						for (int i = 0; i < wh; i++) {
							final int oldLabel = particleLabelSlice[i];
							if (oldLabel == 0)
								continue;
							final int newLabel = lut[oldLabel];
							particleLabelSlice[i] = newLabel;
							if (newLabel == 0)
								workArraySlice[i] = flip;
						}
					}
				});
			}
			Multithreader.startAndJoin(threads);
		}
	}

	/**
	 * @return particleSizes field from this instance
	 */
	public long[] getParticleSizes() {
		return this.particleSizes.clone();
	}

	/**
	 * Run the particle size analysis. Store the particleSizes array as a long[] as
	 * a field in this instance.
	 *
	 * @param particleLabels particle label image array
	 * @param nParticles     number of particles
	 * @return array of particle sizes
	 */
	public long[] getParticleSizes(final int[][] particleLabels, final int nParticles) {
		final int d = particleLabels.length;
		final int wh = particleLabels[0].length;

		// make a list of all the particle sizes with
		// index = particle value
		// need to handle the ID offset for the chunks
		AtomicInteger an = new AtomicInteger(0);
		final long[][] partSizes = new long[d][];

		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int z = an.getAndIncrement(); z < d; z = an.getAndIncrement()) {
					final long[] particleSizes = new long[nParticles];
					final int[] slice = particleLabels[z];
					for (int i = 0; i < wh; i++) {
						final int label = slice[i];
						particleSizes[label]++;
					}
					partSizes[z] = particleSizes;
				}
			});
		}
		Multithreader.startAndJoin(threads);

		this.particleSizes = new long[nParticles];
		for (int i = 0; i < nParticles; i++) {
			long partSum = 0;
			for (int z = 0; z < d; z++) {
				if (partSizes[z] != null)
					partSum += partSizes[z][i];
			}
			this.particleSizes[i] = partSum;
		}
		return this.particleSizes.clone();
	}

	/**
	 * Calculate calibrated volumes of the particles
	 * 
	 * @param imp ImagePlus, used only for its calibration information
	 * @param particleSizes list of particle sizes in pixel counts
	 * @return array of particle sizes in calibrated units
	 */
	static double[] getVolumes(final ImagePlus imp, final long[] particleSizes) {
		final Calibration cal = imp.getCalibration();
		final double voxelVolume = cal.pixelWidth * cal.pixelHeight * cal.pixelDepth;
		final int nLabels = particleSizes.length;
		final double[] particleVolumes = new double[nLabels];
		for (int i = 0; i < nLabels; i++) {
			particleVolumes[i] = voxelVolume * particleSizes[i];
		}
		return particleVolumes;
	}

	/**
	 * Get the mean and standard deviation of pixel values &gt;0 for each particle
	 * in a particle label work array
	 *
	 * @param imp            Input image containing pixel values
	 * @param particleLabels workArray containing particle labels
	 * @param particleSizes  array of particle sizes as pixel counts
	 * @return array containing mean, std dev and max pixel values for each particle
	 */
	static double[][] getMeanStdDev(final ImagePlus imp, final int[][] particleLabels, final long[] particleSizes) {
		final int d = imp.getImageStackSize();
		final int wh = imp.getWidth() * imp.getHeight();
		final ImageStack stack = imp.getImageStack();
		final int nParticles = particleSizes.length;
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

	/**
	 * Calculate Eigenvalue decompositions of all the particles
	 * 
	 * @param imp ImagePlus, used for calibration
	 * @param particleLabels label image array
	 * @param centroids list of particle centroids
	 * @return list of EigenvalueDecompositions
	 */
	static EigenvalueDecomposition[] getEigens(final ImagePlus imp, final int[][] particleLabels,
			final double[][] centroids) {
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
		final EigenvalueDecomposition[] eigens = new EigenvalueDecomposition[nParticles];
		final double[][] momentTensors = new double[nParticles][6];
		for (int z = 0; z < d; z++) {
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
				final EigenvalueDecomposition E = new EigenvalueDecomposition(inertiaTensorMatrix);
				eigens[p] = E;
			}
		}
		return eigens;
	}

	/**
	 * Get the centroid and minimum and maximum x, y and z coordinates of each particle
	 *
	 * @param imp            ImagePlus (used for stack size)
	 * @param particleLabels work array containing labelled particles
	 * @param particleSizes sizes of the particles in pixel counts
	 * @return 2-element Object array containing for each particle the centroid (in calibrated units as double[nParticles][3]) and the 
	 * min and max x, y and z limits (in uncalibrated pixel units as int[nParticles][6]).
	 */
	static Object[] getBoundingBoxes(final ImagePlus imp, final int[][] particleLabels, final long[] particleSizes) {
		
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final int nParticles = particleSizes.length;
		
		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		final List<int[][]> listOfLimits = Collections.synchronizedList(new ArrayList<>());
		final List<double[][]> listOfSums = Collections.synchronizedList(new ArrayList<>());
		
		for (int thread = 0; thread < threads.length; thread++) {
			
			final double[][] threadSums = new double[nParticles][3];
			
			//set up a limit range for each thread and particle
			final int[][] threadLimits = new int[nParticles][6];
			for (int p = 0; p < nParticles; p++) {
				threadLimits[p][0] = Integer.MAX_VALUE; // x min
				threadLimits[p][1] = 0; // x max
				threadLimits[p][2] = Integer.MAX_VALUE; // y min
				threadLimits[p][3] = 0; // y max
				threadLimits[p][4] = Integer.MAX_VALUE; // z min
				threadLimits[p][5] = 0; // z max
			}
			
			threads[thread] = new Thread(() -> {
				for (int z = ai.getAndIncrement(); z < d; z = ai.getAndIncrement()) {
					final int[] slice = particleLabels[z];
					for (int y = 0; y < h; y++) {
						final int index = y * w;
						for (int x = 0; x < w; x++) {
							final int p = slice[index + x];
							if (p == 0) continue;
							
							threadSums[p][0] += x;
							threadSums[p][1] += y;
							threadSums[p][2] += z;
							
							final int[] threadLimitsP = threadLimits[p];
							threadLimitsP[0] = Math.min(x, threadLimitsP[0]);
							threadLimitsP[1] = Math.max(x, threadLimitsP[1]);
							threadLimitsP[2] = Math.min(y, threadLimitsP[2]);
							threadLimitsP[3] = Math.max(y, threadLimitsP[3]);
							threadLimitsP[4] = Math.min(z, threadLimitsP[4]);
							threadLimitsP[5] = Math.max(z, threadLimitsP[5]);
							threadLimits[p] = threadLimitsP;
						}
					}
				}
			});
			listOfLimits.add(threadLimits);
			listOfSums.add(threadSums);
		}
		Multithreader.startAndJoin(threads);
		
		final int[][] limits = new int[nParticles][6];
		final double[][] sums = new double[nParticles][3];
		
		for (int p = 0; p < nParticles; p++) {
			limits[p][0] = Integer.MAX_VALUE; // x min
			limits[p][1] = 0; // x max
			limits[p][2] = Integer.MAX_VALUE; // y min
			limits[p][3] = 0; // y max
			limits[p][4] = Integer.MAX_VALUE; // z min
			limits[p][5] = 0; // z max
		}
		
		Iterator<int[][]> iter = listOfLimits.iterator();
		while (iter.hasNext()) {
			final int[][] threadLimits = iter.next();
			for (int p = 1; p < nParticles; p++) {
				final int[] threadP = threadLimits[p];
				final int[] limitsP = limits[p];
				limitsP[0] = Math.min(threadP[0], limitsP[0]);
				limitsP[1] = Math.max(threadP[1], limitsP[1]);
				limitsP[2] = Math.min(threadP[2], limitsP[2]);
				limitsP[3] = Math.max(threadP[3], limitsP[3]);
				limitsP[4] = Math.min(threadP[4], limitsP[4]);
				limitsP[5] = Math.max(threadP[5], limitsP[5]);
				limits[p] = limitsP; 
			}
		}

		Iterator<double[][]> iterSums = listOfSums.iterator();
		while (iterSums.hasNext()) {
			final double[][] threadSums = iterSums.next();
			for (int p = 1; p < nParticles; p++) {
				sums[p][0] += threadSums[p][0];
				sums[p][1] += threadSums[p][1];
				sums[p][2] += threadSums[p][2];
			}
		}
		
		final Calibration cal = imp.getCalibration();
		final double[][] centroids = new double[nParticles][3];
		for (int p = 0; p < nParticles; p++) {
			final long particleSize = particleSizes[p];
			centroids[p][0] = cal.pixelWidth * sums[p][0] / particleSize;
			centroids[p][1] = cal.pixelHeight * sums[p][1] / particleSize;
			centroids[p][2] = cal.pixelDepth * sums[p][2] / particleSize;
		}
		
		return new Object[] {centroids, limits};
	}
	
	/**
	 * Get the limits of each particle in the directions defined by an 
	 * eigenvector tensor (usually the principal axes).
	 * 
	 * @param imp input image, needed for calibration
	 * @param particleLabels label image
	 * @param tensors array of rotation matrices, one per particle
	 * @param nParticles number of particles
	 * @return array of box dimensions, each containing the centre x, y, z
	 * coordinates and box width, height and depth.
	 */
	static double[][] getAxisAlignedBoundingBoxes(final ImagePlus imp, final int[][] particleLabels,
		final Matrix[] tensors, final int nParticles){
		
		double[][] limits = new double[nParticles][6];
		for (int i = 1; i < nParticles; i++) {
			limits[i][0] = Double.MAX_VALUE; // 0 min
			limits[i][1] = -Double.MAX_VALUE; // 0 max
			limits[i][2] = Double.MAX_VALUE; // 1 min
			limits[i][3] = -Double.MAX_VALUE; // 1 max
			limits[i][4] = Double.MAX_VALUE; // 2 min
			limits[i][5] = -Double.MAX_VALUE; // 2 max
		}
		
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		
		double[][] v = new double[3][3];
		for (int z = 0; z < d; z++) {
			final double zd = z * vD;
			for (int y = 0; y < h; y++) {
				final double yh = y * vH;
				final int index = y * w;
				for (int x = 0; x < w; x++) {
					final double xw = x * vW;
					
					final int i = particleLabels[z][index + x];
					if (i == 0) continue;
					v = tensors[i].getArray();
					
					final double v00 = v[0][0];
					final double v10 = v[1][0];
					final double v20 = v[2][0];
					final double v01 = v[0][1];
					final double v11 = v[1][1];
					final double v21 = v[2][1];
					final double v02 = v[0][2];
					final double v12 = v[1][2];
					final double v22 = v[2][2];
					
					final double xwv00 = xw * v00;
					final double xwv01 = xw * v01;
					final double xwv02 = xw * v02;
					final double yhv10 = yh * v10;
					final double yhv11 = yh * v11;
					final double yhv12 = yh * v12;
					final double zdv20 = zd * v20;
					final double zdv21 = zd * v21;
					final double zdv22 = zd * v22;
					
					final double l0 = xwv00 + yhv10 + zdv20;
					final double l1 = xwv01 + yhv11 + zdv21;
					final double l2 = xwv02 + yhv12 + zdv22;
			
					limits[i][0] = Math.min(limits[i][0], l0);
					limits[i][1] = Math.max(limits[i][1], l0);
					limits[i][2] = Math.min(limits[i][2], l1);
					limits[i][3] = Math.max(limits[i][3], l1);
					limits[i][4] = Math.min(limits[i][4], l2);
					limits[i][5] = Math.max(limits[i][5], l2);
				}
			}
		}
		
		final double[][] alignedBoxes = new double[nParticles][6];
		
		for (int i = 1; i < nParticles; i++) {
			//centroid in rotated coordinate frame is average of the two limits
			final double c0 = (limits[i][0] + limits[i][1]) / 2; //long axis 0th column
			final double c1 = (limits[i][2] + limits[i][3]) / 2; //medium axis 1st column
			final double c2 = (limits[i][4] + limits[i][5]) / 2; //short axis 2nd column
			
			//rotate back to original coordinate frame by multiplying by the inverse
			final double[][] vi = tensors[i].inverse().getArray();
			
			final double c2vi20 = c2 * vi[2][0];
			final double c2vi21 = c2 * vi[2][1];
			final double c2vi22 = c2 * vi[2][2];
			
			final double c1vi10 = c1 * vi[1][0];
			final double c1vi11 = c1 * vi[1][1];
			final double c1vi12 = c1 * vi[1][2];
			
			final double c0vi00 = c0 * vi[0][0];
			final double c0vi01 = c0 * vi[0][1];
			final double c0vi02 = c0 * vi[0][2];
			
			final double cx = c2vi20 + c1vi10 + c0vi00;
			final double cy = c2vi21 + c1vi11 + c0vi01;
			final double cz = c2vi22 + c1vi12 + c0vi02;
			
			//particle's length along each tensor axis is difference between limits
			final double d0 = limits[i][1] - limits[i][0];
			final double d1 = limits[i][3] - limits[i][2];
			final double d2 = limits[i][5] - limits[i][4];
			
			final double[] box = {cx, cy, cz, d0, d1, d2};
			
			alignedBoxes[i] = box;
		}
		
		return alignedBoxes;
	}
	
	/**
	 * Overloaded method that accepts the EVD of each particle and unwraps its Matrixes
	 * for convenience.
	 * 
	 * @param imp ImagePlus
	 * @param particleLabels 
	 * @param eigens array of 3 × 3 rotation matrices (inertia tensors)
	 * @param nParticles 
	 * @return dimensions of the axis aligned bounding box, aligned to the eigenvectors
	 * of the supplied eigenvalue decomposition.
	 */
	static double[][] getAxisAlignedBoundingBoxes(final ImagePlus imp, final int[][] particleLabels,
		final EigenvalueDecomposition[] eigens, final int nParticles){
		
		final Matrix[] tensors = new Matrix[nParticles];
		
		for (int i = 1; i < nParticles; i++) {
			tensors[i] = eigens[i].getV();
		}
		
		return getAxisAlignedBoundingBoxes(imp, particleLabels, tensors, nParticles);
	}
	
	/**
	 * Get the Euler characteristic of each particle
	 *
	 * @param imp an image.
	 * @param particleLabels particles of the image.
	 * @param limits limits of the particles.
	 * @param nParticles number of particles in the image
	 * @return euler characteristic of each image.
	 */
	static double[][] getEulerCharacter(final ImagePlus imp,
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
	 * Calculate number of branches and total branch length by running Skeletonize3D
	 * and Analyze Skeleton on each particle.
	 * 
	 * <p>
	 * Calls {@link AnalyzeSkeleton_#run()} with no arguments.
	 * </p>
	 *  
	 * @param imp            Input image
	 * @param particleLabels particle label array
	 * @param limits         xyz limits of each particle
	 * @param nParticles     number of particles
	 * @return array of {@link SkeletonResult} result objects. Use methods therein
	 *         to get specific results out. Each result is a list of trees, but
	 *         there should be only one tree per particle so its result is at [0].
	 */
	static SkeletonResult[] getBranchLength(final ImagePlus imp,
			final int[][] particleLabels, final int[][] limits, final int nParticles)
	{
		final SkeletonResult[] skeletonResults = new SkeletonResult[nParticles];
		final Thread[] threads = Multithreader.newThreads();
		final AtomicInteger ai = new AtomicInteger(1);
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
				final AnalyzeSkeleton_ analyzeSkeleton_ = new AnalyzeSkeleton_();
				for (int i = ai.getAndIncrement(); i < nParticles; i = ai.getAndIncrement()) {
					final ImagePlus particleImp = getBinaryParticle(i, imp, particleLabels,
							limits, 1);
					skeletoniser.setup("", particleImp);
					skeletoniser.run(null);
					analyzeSkeleton_.setup("", particleImp);
					final SkeletonResult skeletonResult = analyzeSkeleton_.run();
					skeletonResults[i] = skeletonResult;
				}
			});
		}
		Multithreader.startAndJoin(threads);
		return skeletonResults;
	}

	/**
	 * Calculate the number of cavities in the image, which may be interpreted
	 * as a count of disconnected particles of background.
	 * 
	 * @param imp input image
	 * @return number of cavities
	 */
	private static int getNCavities(final ImagePlus imp) {
		final ConnectedComponents connector = new ConnectedComponents();
		connector.run(imp, ConnectedComponents.BACK);
		return connector.getNParticles() - 2;
	}	

	/**
	 * create a binary ImagePlus containing a single particle and which 'just fits'
	 * the particle
	 *
	 * @param p              The particle ID to get
	 * @param imp            original image, used for calibration
	 * @param particleLabels work array of particle labels
	 * @param limits         x,y and z limits of each particle
	 * @param padding        amount of empty space to pad around each particle
	 * @return a cropped single particle image.
	 */
	static ImagePlus getBinaryParticle(final int p, final ImagePlus imp, final int[][] particleLabels,
			final int[][] limits, final int padding) {

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

	// ----------- SURFACE MESH OPERATIONS ------------------------//

	/**
	 * Create a list of surface meshes, each wrapping a particle
	 * 
	 * @param imp Input image, needed for calibration
	 * @param particleLabels label array
	 * @param limits bounding box limits for each particle
	 * @param resampling user-set resampling level
	 * @param nParticles number of particles
	 * @return list of surface meshes, one per particle
	 */
	static ArrayList<List<Point3f>> getSurfacePoints(final ImagePlus imp, final int[][] particleLabels,
			final int[][] limits, final int resampling, final int nParticles) {
		final Calibration cal = imp.getCalibration();
		final ArrayList<List<Point3f>> surfacePoints = new ArrayList<>();
		final boolean[] channels = { true, false, false };
		for (int p = 0; p < nParticles; p++) {
			if (p > 0) {
				final ImagePlus binaryImp = getBinaryParticle(p, imp, particleLabels, limits, resampling);
				// noinspection TypeMayBeWeakened
				final MCTriangulator mct = new MCTriangulator();
				@SuppressWarnings("unchecked")
				final List<Point3f> points = mct.getTriangles(binaryImp, 128, channels, resampling);

				final double xOffset = (limits[p][0] - 1) * cal.pixelWidth;
				final double yOffset = (limits[p][2] - 1) * cal.pixelHeight;
				final double zOffset = (limits[p][4] - 1) * cal.pixelDepth;
				for (final Point3f point : points) {
					point.x += xOffset;
					point.y += yOffset;
					point.z += zOffset;
				}
				if (points.isEmpty()) {
					IJ.log("Particle " + p + " resulted in 0 surface points");
					surfacePoints.add(null);
				} else {
					surfacePoints.add(points);
				}
			} else {
				surfacePoints.add(null);
			}
		}
		return surfacePoints;
	}

	/**
	 * Calculate surface areas of the particles
	 * 
	 * @param surfacePoints list of surface points
	 * @return list of surface areas
	 */
	static double[] getSurfaceAreas(final Collection<List<Point3f>> surfacePoints) {
		return surfacePoints.parallelStream().mapToDouble(ParticleAnalysis::getSurfaceArea).toArray();
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
			final Point3f cp = crossProduct(points.get(n), points.get(n + 1), points.get(n + 2));
			final double deltaArea = 0.5 * cp.distance(origin);
			sumArea += deltaArea;
		}
		return sumArea;
	}

	/**
	 * Calculate volume contained within surface points
	 * 
	 * @param surfacePoints list of surface points
	 * @return  list of particle volumes
	 */
	static double[] getSurfaceVolume(final Collection<List<Point3f>> surfacePoints) {
		return surfacePoints.parallelStream().mapToDouble(p -> {
			if (p == null) {
				return 0;
			}
			final CustomTriangleMesh mesh = new CustomTriangleMesh(p);
			return Math.abs(mesh.getVolume());
		}).toArray();
	}

	/**
	 * Get the Feret diameter of a surface. Uses an inefficient brute-force
	 * algorithm.
	 *
	 * @param surfacePoints points from a surface mesher
	 * @return Feret diameters and x, y, z coordinates of the two feret points of each surface,
	 * packed in a double so that the feret diameter of particle i is found at [i * 7] and the
	 * points' coordinates are in the following 6 positions in ax, ay, az, bx, by, bz order.
	 */
	static double[][] getFerets(final List<List<Point3f>> surfacePoints) {
		Thread[] threads = Multithreader.newThreads();
		final int nSurfaces = surfacePoints.size();
		//distance, xa, ya, za, xb, yb, zb
		final double[][] ferets = new double[nSurfaces][7];
		AtomicInteger ai = new AtomicInteger(0);
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(() -> {
				for (int i = ai.getAndIncrement(); i < nSurfaces; i = ai.getAndIncrement()) {
					final List<Point3f> surface = surfacePoints.get(i);
					
					
					if (surface == null) {
						Arrays.fill(ferets[i], Double.NaN);
						continue;
					}
					
					final int nPoints = surface.size();
					
					//4 triangles * 3 points for the minimal tetrahedron
					if (nPoints < 12) {
						Arrays.fill(ferets[i], Double.NaN);
					}
					
					// check all the point pairs in this surface
					Point3f feretA = new Point3f();
					Point3f feretB = new Point3f();
					double feret = 0;
					for (int p = 0; p < nPoints; p++) {
						Point3f a = surface.get(p);
						for (int q = p + 1; q < nPoints; q++) {
							Point3f b = surface.get(q);
							final double distance = a.distance(b);
							if (distance > feret) {
								feret = distance;
								feretA = a;
								feretB = b;
							}
						}
					}
					ferets[i][0] = feret;
					ferets[i][1] = feretA.x;
					ferets[i][2] = feretA.y;
					ferets[i][3] = feretA.z;
					ferets[i][4] = feretB.x;
					ferets[i][5] = feretB.y;
					ferets[i][6] = feretB.z;
				}
			});
		}
		Multithreader.startAndJoin(threads);
		return ferets;
	}

	/**
	 * Get the list of best-fit ellipsoids for the particle surfaces
	 * 
	 * @param surfacePoints list of surface points
	 * @return Object[] array containing the list of ellipsoids, each of which is
	 *         also stored as an Object[] array (see FitEllipsoid.yuryPetrov() for
	 *         details). Note that an Object[] is also an Object so there is no need
	 *         to make a 2D array (i.e. Object[][]). However, client code must
	 *         unwrap the ellipsoid elements into arrays by casting to Object[].
	 */
	static Object[] getEllipsoids(final Collection<List<Point3f>> surfacePoints) {
		return surfacePoints.parallelStream().map(surface -> {

			if (surface == null)
				return null;

			final Iterator<Point3f> pointIter = surface.iterator();
			final double[][] coOrdinates = new double[surface.size()][3];
			int i = 0;
			while (pointIter.hasNext()) {
				final Point3f point = pointIter.next();
				coOrdinates[i][0] = point.x;
				coOrdinates[i][1] = point.y;
				coOrdinates[i][2] = point.z;
				i++;
			}

			Object[] ellipsoid = null;
			try {
				ellipsoid = FitEllipsoid.yuryPetrov(coOrdinates);
			} catch (final IllegalArgumentException re) {
				IJ.log("Could not fit ellipsoid to particle: "+re.getMessage());
			} catch (final Exception e) {
				IJ.log("Could not fit ellipsoid to particle: "+e.getMessage());
			}
			return ellipsoid;
		}).toArray();
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
}
