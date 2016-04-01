/*
 * #%L
 * BoneJ: open source tools for trabecular geometry and whole bone shape analysis.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers. See also individual class @authors.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.doube.bonej;

import java.util.ArrayList;

import org.doube.util.ImageCheck;
import org.doube.util.ResultInserter;
import org.doube.util.UsageReporter;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.macro.Interpreter;
import ij.measure.CurveFitter;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.util.Tools;

/**
 * <p>
 * FractalCount_.java,v 1.42 2005/05/30 07:52:59
 * </p>
 * <p>
 * Estimates the fractal dimension of 2D and 3D binary images. <br />
 * Free Software in the Public domain.
 * </p>
 *
 * @author Per Christian Henden
 * @author Jens Bache-Wiig
 * @author Michael Doube (refactoring)
 * @see
 * 		<p>
 *      <a href="http://www.pvv.org/~perchrh/imagej/fractal.html">http://www.
 *      pvv. org/~perchrh/imagej/fractal.html</a>
 *      </p>
 *
 */

public class FractalBoxCounter implements PlugIn {

	boolean noGo = false;
	final int autoDiv = 4;

	// User-changeable defaults :
	boolean plotGraph = true;
	boolean verboseOutput = true;
	int threshold = 128;
	int maxBox = 24;
	int minBox = 6;
	double divBox = 1.2;
	int numOffsets = 1;
	boolean autoParam = true;

	// TODO split run method into more sensible methods
	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment())
			return;
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
			return;
		}
		final ImageCheck ic = new ImageCheck();
		if (!ImageCheck.isBinary(imp)) {
			IJ.showMessage("Fractal Count requires a binary image.");
			return;
		}
		if (noGo)
			return;
		final ImagePlus surfaceImp = findSurfaceVoxels(imp);
		try {
			// Fetch data
			final int width = imp.getWidth();
			final int height = imp.getHeight();
			final int depth = imp.getStackSize();

			if (autoParam) {
				maxBox = Math.max(width, Math.max(height, depth)) / autoDiv;
				if (verboseOutput) {
					IJ.log("Automatic max box size " + maxBox + " selected");
				}
			}

			// Create variables we need and set them
			long bestCount; // keep track of best count so far
			long count = 0; // current count
			final ArrayList<Double> xList = new ArrayList<Double>();
			final ArrayList<Double> yList = new ArrayList<Double>();
			int xPos, yPos, zPos, yPart;
			int xGrid, yGrid, zGrid;
			int xStart, yStart, zStart;
			int xEnd, yEnd, zEnd;

			// Start timer
			final long startTime = System.currentTimeMillis();

			for (int boxSize = maxBox; boxSize >= minBox; boxSize /= divBox) {
				if (verboseOutput) {
					IJ.showStatus("Estimating dimension, box size: " + boxSize);
				}

				bestCount = Long.MAX_VALUE; // init count for this boxSize

				final int increment = Math.max(1, boxSize / numOffsets);

				for (int gridOffsetX = 0; (gridOffsetX < boxSize) && (gridOffsetX < width); gridOffsetX += increment) {

					for (int gridOffsetY = 0; (gridOffsetY < boxSize)
							&& (gridOffsetY < height); gridOffsetY += increment) {

						for (int gridOffsetZ = 0; (gridOffsetZ < boxSize)
								&& (gridOffsetZ < depth); gridOffsetZ += increment) {

							count = 0;

							final int iMax = width + gridOffsetX;
							final int jMax = height + gridOffsetY;
							final int kMax = depth + gridOffsetZ;

							// Iterate over box-grid
							for (int i = 0; i <= iMax; i += boxSize) {
								xGrid = -gridOffsetX + i;
								for (int j = 0; j <= jMax; j += boxSize) {
									yGrid = -gridOffsetY + j;
									for (int k = 0; k <= kMax; k += boxSize) {
										zGrid = -gridOffsetZ + k;

										xStart = 0;
										if (xGrid < 0) {
											xStart = -xGrid;
										}
										if ((boxSize + xGrid) >= width) {
											xEnd = Math.min(width, (width - xGrid));
										} else {
											xEnd = boxSize;
										}

										yStart = 0;
										if (yGrid < 0) {
											yStart = -yGrid;
										}
										if ((boxSize + yGrid) >= height) {
											yEnd = Math.min(height, (height - yGrid));
										} else {
											yEnd = boxSize;
										}

										zStart = 0;
										if (zGrid < 0) {
											zStart = -zGrid;
										}
										if ((boxSize + zGrid) >= depth) {
											zEnd = Math.min(depth, (depth - zGrid));
										} else {
											zEnd = boxSize;
										}

										for (int x = xStart; x < xEnd; x++) {
											xPos = x + xGrid;

											for (int y = yStart; y < yEnd; y++) {
												yPos = (y + yGrid);
												yPart = yPos * width;

												for (int z = zStart; z < zEnd; z++) {
													zPos = z + zGrid;

													// If pixel inside region,
													// count it

													if ((0xff
															& ((byte[]) surfaceImp.getStack().getPixels(zPos + 1))[xPos
																	+ yPart]) >= threshold) {
														count++;
														z = x = y = boxSize; // stops
														// things
													}
												}
											}
										}
									}
								}
							}
							if (count < bestCount) {
								bestCount = count;
							}
						}
					}
				}
				xList.add(new Double(boxSize));
				yList.add(new Double(bestCount));
			}

			if (verboseOutput) {
				IJ.log("\nDuration: " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds\n");
			}

			final double[] boxSizes = new double[xList.size()];
			final double[] boxCountSums = new double[yList.size()];
			for (int i = 0; i < boxSizes.length; i++) {
				boxSizes[i] = -Math.log((xList.get(i)).doubleValue());
				boxCountSums[i] = Math.log((yList.get(i)).doubleValue());
			}

			if (verboseOutput) {
				IJ.log("Used " + boxSizes.length + " different box sizes, from " + maxBox + " to " + minBox);
				IJ.log("with a reduction rate of " + divBox + " and " + numOffsets + " translations of each box.");
			}

			if (boxSizes.length == 0) {
				IJ.error("\nError: No boxes!\n" + "Make sure that starting and ending box size and"
						+ "reduction rate allow for at least one" + " box size to exist!");
				return;
			}

			final CurveFitter cf = new CurveFitter(boxSizes, boxCountSums);
			cf.doFit(CurveFitter.STRAIGHT_LINE);
			final double[] p = cf.getParams();
			final double RSq = cf.getRSquared();

			if (verboseOutput) {
				IJ.log(imp.getTitle() + ": Dimension estimate: " + IJ.d2s(p[1], 4) + ": R²: " + RSq + ": Settings: "
						+ maxBox + ":" + minBox + ":" + divBox + ":" + numOffsets);
			}

			final ResultInserter ri = ResultInserter.getInstance();
			ri.setResultInRow(imp, "Fractal Dimension", p[1]);
			ri.setResultInRow(imp, "R²", RSq);
			ri.updateTable();

			if (plotGraph && !Interpreter.isBatchMode()) {
				drawGraph(p, boxSizes, boxCountSums);
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		UsageReporter.reportEvent(this).send();
	}

	private ImagePlus findSurfaceVoxels(final ImagePlus imp) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();
		final ImageStack stack = imp.getImageStack();
		final ImageStack surfaceStack = new ImageStack(w, h, d);

		for (int z = 0; z < d; z++) {
			IJ.showStatus("Finding surface voxels");
			final byte[] pixels = (byte[]) stack.getPixels(z + 1);
			surfaceStack.setPixels(pixels.clone(), z + 1);
			final ImageProcessor surfaceIP = surfaceStack.getProcessor(z + 1);
			for (int y = 0; y < h; y++) {
				checkNeighbours: for (int x = 0; x < w; x++) {
					if (getPixel(stack, x, y, z, w, h, d) == (byte) 0)
						continue;
					for (int nz = -1; nz < 2; nz++) {
						final int znz = z + nz;
						for (int ny = -1; ny < 2; ny++) {
							final int yny = y + ny;
							for (int nx = -1; nx < 2; nx++) {
								final int xnx = x + nx;
								final byte pixel = getPixel(stack, xnx, yny, znz, w, h, d);
								if (pixel == (byte) 0)
									continue checkNeighbours;
							}
						}
					}
					// we checked all the neighbours for a 0
					// but didn't find one, so this is not a surface voxel
					surfaceIP.set(x, y, (byte) 1);
				}
			}
		}
		// turn all the 1's into 0's
		final int wh = w * h;
		for (int z = 0; z < d; z++) {
			IJ.showStatus("Finding surface voxels");
			final ImageProcessor ip = surfaceStack.getProcessor(z + 1);
			for (int i = 0; i < wh; i++) {
				if (ip.get(i) == (byte) 1)
					ip.set(i, (byte) 0);
			}
		}

		final ImagePlus surfaceImp = new ImagePlus("Surface");
		surfaceImp.setStack(surfaceStack);
		surfaceImp.setCalibration(imp.getCalibration());
		return surfaceImp;
	}

	private byte getPixel(final ImageStack image, final int x, final int y, final int z, final int w, final int h,
			final int d) {
		if (x >= 0 && x < w && y >= 0 && y < h && z >= 0 && z < d)
			return ((byte[]) image.getPixels(z + 1))[x + y * w];

		return (byte) 255;
	} /* end getPixel */

	private void drawGraph(final double[] params, final double[] boxSizes, final double[] boxCountSums) {

		final int samples = 100;
		final float[] px = new float[samples];
		final float[] py = new float[samples];
		double[] a = Tools.getMinMax(boxSizes);
		final double xmin = a[0], xmax = a[1];

		a = Tools.getMinMax(boxCountSums);
		double ymin = a[0], ymax = a[1];
		final double inc = (xmax - xmin) / ((double) samples - 1);
		double tmp = xmin;

		for (int i = 0; i < samples; i++) {
			px[i] = (float) tmp;
			tmp += inc;
		}
		for (int i = 0; i < samples; i++) {
			py[i] = (float) CurveFitter.f(CurveFitter.STRAIGHT_LINE, params, px[i]);
		}
		a = Tools.getMinMax(py);
		ymin = Math.min(ymin, a[0]);
		ymax = Math.max(ymax, a[1]);
		final Plot plot = new Plot("Plot", "-log(box size)", "log(box count)", px, py);
		plot.setLimits(xmin, xmax * 0.9, ymin, ymax * 1.1);
		plot.draw();
		plot.addPoints(boxSizes, boxCountSums, Plot.CIRCLE);
		plot.addPoints(px, py, Plot.LINE);
		final CurveFitter cf = new CurveFitter(boxSizes, boxCountSums);
		cf.doFit(CurveFitter.STRAIGHT_LINE);
		plot.addLabel(0.1, 0.1, "Slope: " + IJ.d2s(params[1], 4) + "\n" + "R²: " + IJ.d2s(cf.getRSquared(), 4));
		final ImagePlus plotImage = new ImagePlus("Plot", plot.getProcessor());
		plotImage.show();
		return;
	}
}