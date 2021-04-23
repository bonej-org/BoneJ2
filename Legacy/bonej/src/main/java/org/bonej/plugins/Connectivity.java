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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.util.ImageCheck;
import org.bonej.util.Multithreader;
import org.bonej.util.ResultInserter;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.plugin.PlugIn;

/**
 * <p>
 * Connectivity_
 * </p>
 *
 * <p>
 * Calculate the Euler characteristic (&#967;) and connectivity index (&#946;
 * <sub>1</sub>) of a trabecular bone network. The connectivity index can be
 * thought of as "trabecular number".
 * </p>
 * <ol>
 * <li>Purify stack to contain only 1 connected bone phase and 1 connected
 * marrow phase (Purify_ plugin)</li>
 * <li>Iterate through stack, calculating Euler characteristic for each bone
 * voxel (&#948;&#967;) and summing to give an Euler characteristic for bone
 * (&#967;)</li>
 * <li>Calculate the Euler characteristic of the bone sample as though it is
 * floating in space (&#967; = &#8721;&#948;&#967;)</li>
 * <li>Calculate the bone sample's contribution to the Euler characteristic of
 * the bone it was connected to (&#916;&#967;) by checking the intersections of
 * voxels and stack edges</li>
 * <li>Calculate connectivity as &#946;<sub>1</sub> = 1 - &#916;&#967;</li>
 * <li>Calculate connectivity density as &#946;<sub>1</sub> / V</li>
 * </ol>
 *
 * @author Michael Doube
 *
 * @see
 * 		<p>
 *      Toriwaki J, Yonekura T (2002) Euler Number and Connectivity Indexes of a
 *      Three Dimensional Digital Picture. Forma 17: 183-209. <a href=
 *      "http://www.scipress.org/journals/forma/abstract/1703/17030183.html" >
 *      http://www.scipress.org/journals/forma/abstract/1703/17030183.html</a>
 *      </p>
 *      <p>
 *      Odgaard A, Gundersen HJG (1993) Quantification of connectivity in
 *      cancellous bone, with special emphasis on 3-D reconstructions. Bone 14:
 *      173-182.
 *      <a href="https://doi.org/10.1016/8756-3282(93)90245-6">doi:10.1016
 *      /8756-3282(93)90245-6</a>
 *      </p>
 *      <p>
 *      Lee TC, Kashyap RL, Chu CN (1994) Building Skeleton Models via 3-D
 *      Medial Surface Axis Thinning Algorithms. CVGIP: Graphical Models and
 *      Image Processing 56: 462-478.
 *      <a href="https://doi.org/10.1006/cgip.1994.1042" >doi:10.1006/cgip.
 *      1994.1042</a>
 *      </p>
 *      <p>
 *      Several of the methods are based on Ignacio Arganda-Carreras's
 *      Skeletonize3D_ plugin: <a href="https://imagej.github.io/plugins/skeletonize3d">
 *      Skeletonize3D homepage</a>
 *      </p>
 *
 */
public class Connectivity implements PlugIn {

	private final static int[] EULER_LUT = fillEulerLUT();
	
	/** working image width */
	private int width = 0;

	/** working image height */
	private int height = 0;

	/** working image depth */
	private int depth = 0; 

	@Override
	public void run(final String arg) {
		final ImagePlus imp = IJ.getImage();
		if (!ImageCheck.isBinary(imp)) {
			IJ.error("Connectivity requires a binary image.");
			return;
		}

		final double sumEuler = getSumEuler(imp);

		final double deltaChi = getDeltaChi(imp, sumEuler);

		final double connectivity = getConnectivity(deltaChi);

		final double connDensity = getConnDensity(imp, connectivity);

		if (connectivity < 0 && !Interpreter.isBatchMode()) {
			IJ.showMessage("Caution", "Connectivity is negative.\n\n" + "This usually happens if there are multiple\n"
					+ "particles or enclosed cavities.\n\n" + "Try running Purify prior to Connectivity.");
		}

		final ResultInserter ri = ResultInserter.getInstance();
		ri.setResultInRow(imp, "Euler ch.", sumEuler);
		ri.setResultInRow(imp, "Δ(χ)", deltaChi);
		ri.setResultInRow(imp, "Connectivity", connectivity);
		ri.setResultInRow(imp, "Conn.D (" + imp.getCalibration().getUnit() + "^-3)", connDensity);
		ri.updateTable();
		UsageReporter.reportEvent(this).send();
		return;
	}

	/**
	 * Calculate connectivity density
	 *
	 * @param imp Binary ImagePlus
	 * @param connectivity Result of getConnectivity()
	 * @return connectivity density.
	 */
	public double getConnDensity(final ImagePlus imp, final double connectivity) {
		setDimensions(imp);
		final Calibration cal = imp.getCalibration();
		final double vW = cal.pixelWidth;
		final double vH = cal.pixelHeight;
		final double vD = cal.pixelDepth;
		final double stackVolume = width * height * depth * vW * vH * vD;
		final double connDensity = connectivity / stackVolume;
		return connDensity;
	}

	/**
	 * Return the connectivity of the image, which is 1 - deltaChi.
	 *
	 * @param deltaChi result of getDeltaChi()
	 * @return double connectivity
	 */
	public double getConnectivity(final double deltaChi) {
		final double connectivity = 1 - deltaChi;
		return connectivity;
	}

	/**
	 * Get the contribution of the stack's foreground particles to the Euler
	 * characteristic of the universe the stack was cut from.
	 *
	 * @param imp
	 *            Binary ImagePlus
	 * @param sumEuler
	 *            (result of getSumEuler() )
	 * @return delta Chi
	 */
	public double getDeltaChi(final ImagePlus imp, final double sumEuler) {
		setDimensions(imp);
		final double deltaChi = sumEuler - correctForEdges(imp.getStack());
		return deltaChi;
	}

	/**
	 * Calculate the Euler characteristic of the foreground in a binary stack
	 *
	 * @param imp
	 *            Binary ImagePlus
	 * @return Euler characteristic of the foreground particles
	 */
	public double getSumEuler(final ImagePlus imp) {
		setDimensions(imp);
		final ImageStack stack = imp.getImageStack();

		final int[] sumEulerInt = new int[depth + 1];

		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(new Runnable() {
				@Override
				public void run() {
					byte o1 = 0, o2 = 0, o3 = 0, o4 = 0, o5 = 0, o6 = 0, o7 = 0, o8 = 0;
					for (int z = ai.getAndIncrement(); z <= depth; z = ai.getAndIncrement()) {
						for (int y = 0; y <= height; y++) {
							for (int x = 0; x <= width; x++) {
								
								final int y1 = y - 1;
								final int z1 = z - 1;
								
								o1 = o3;
								o2 = o4;
								o3 = getPixel(stack, x, y1, z1);
								o4 = getPixel(stack, x, y, z1);
								o5 = o7;
								o6 = o8;
								o7 = getPixel(stack, x, y1, z);
								o8 = getPixel(stack, x, y, z);

							  if ( o1 != 0 || o2 != 0 || o3 != 0 || o4 != 0 || o5 != 0 || o6 != 0 || o7 != 0 || o8 != 0 )
							  	sumEulerInt[z] += getDeltaEuler(o1, o2, o3, o4, o5, o6, o7, o8);
							}
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);
		double sumEuler = Arrays.stream(sumEulerInt).sum();

		sumEuler /= 8;
		return sumEuler;
	}

	private void setDimensions(final ImagePlus imp) {
		this.width = imp.getWidth();
		this.height = imp.getHeight();
		this.depth = imp.getStackSize();
		return;
	}

	/**
	 * Get pixel in 3D image stack (0 border conditions)
	 *
	 * @param stack
	 *            3D image
	 * @param x
	 *            x- coordinate
	 * @param y
	 *            y- coordinate
	 * @param z
	 *            z- coordinate (in image stacks the indexes start at 1)
	 * @return corresponding pixel (0 if out of image)
	 */
	private byte getPixel(final ImageStack stack, final int x, final int y, final int z) {
		if (x >= 0 && x < this.width && y >= 0 && y < this.height && z >= 0 && z < this.depth)
			return ((byte[]) stack.getPixels(z + 1))[y * this.width + x];

		return 0;
	} /* end getPixel */

	/**
	 * Get delta euler value for an octant (~= vertex) from look up table
	 *
	 * Use this method only when there is at least one foreground voxel in
	 * octant.
	 *
	 * In binary images, foreground is -1, background = 0. o1 = 08 are the octant values.
	 * @return delta Euler for the octant or false if the point is Euler invariant or not
	 */
	private int getDeltaEuler(final byte o1, final byte o2, final byte o3, final byte o4,
		final byte o5, final byte o6, final byte o7, final byte o8) {
		
		char n = 1;
		if (o8 == -1) {
			if (o1 == -1) n |= 128;
			if (o2 == -1) n |= 64;
			if (o3 == -1) n |= 32;
			if (o4 == -1) n |= 16;
			if (o5 == -1) n |= 8;
			if (o6 == -1) n |= 4;
			if (o7 == -1) n |= 2;
		}
		else if (o7 == -1) {
			if (o2 == -1) n |= 128;
			if (o4 == -1) n |= 64;
			if (o1 == -1) n |= 32;
			if (o3 == -1) n |= 16;
			if (o6 == -1) n |= 8;
			if (o5 == -1) n |= 2;
		}
		else if (o6 == -1) {
			if (o3 == -1) n |= 128;
			if (o1 == -1) n |= 64;
			if (o4 == -1) n |= 32;
			if (o2 == -1) n |= 16;
			if (o5 == -1) n |= 4;
		}
		else if (o5 == -1) {
			if (o4 == -1) n |= 128;
			if (o3 == -1) n |= 64;
			if (o2 == -1) n |= 32;
			if (o1 == -1) n |= 16;
		}
		else if (o4 == -1) {
			if (o1 == -1) n |= 8;
			if (o3 == -1) n |= 4;
			if (o2 == -1) n |= 2;
		}
		else if (o3 == -1) {
			if (o2 == -1) n |= 8;
			if (o1 == -1) n |= 4;
		}
		else if (o2 == -1) {
			if (o1 == -1) n |= 2;
		}
		else return 1;

		return EULER_LUT[n];
	}/* end getDeltaEuler */

	/*------------------------------------------------------------------------*/
	/**
	 * Check all vertices of stack and count if foreground (-1) this is &#967;
	 * <sub>0</sub> from Odgaard and Gundersen (1993) and <i>f</i> in my working
	 *
	 * @param stack
	 * @return number of voxel vertices intersecting with stack vertices
	 */
	private long getStackVertices(final ImageStack stack) {
		long nStackVertices = 0;
		final int xInc = Math.max(1, width - 1);
		final int yInc = Math.max(1, height - 1);
		final int zInc = Math.max(1, depth - 1);

		for (int z = 0; z < depth; z += zInc) {
			for (int y = 0; y < height; y += yInc) {
				for (int x = 0; x < width; x += xInc) {
					if (getPixel(stack, x, y, z) == -1)
						nStackVertices++;
				}
			}
		}

		return nStackVertices;
	}/* end getStackVertices */

	/**
	 * Count the number of foreground voxels on edges of stack, this is part of
	 * &#967;<sub>1</sub> (<i>e</i> in my working)
	 *
	 * @param stack
	 * @return number of voxel edges intersecting with stack edges
	 */
	private long getStackEdges(final ImageStack stack) {
		long nStackEdges = 0;
		
		final int w1 = width - 1;
		final int h1 = height - 1;
		final int d1 = depth -1;
		
		final int xInc = Math.max(1, w1);
		final int yInc = Math.max(1, h1);
		final int zInc = Math.max(1, d1);
		
		// left to right stack edges
		for (int z = 0; z < depth; z += zInc) {
			for (int y = 0; y < height; y += yInc) {
				for (int x = 1; x < w1; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackEdges++;
				}
			}
		}

		// back to front stack edges
		for (int z = 0; z < depth; z += zInc) {
			for (int x = 0; x < width; x += xInc) {
				for (int y = 1; y < h1; y++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackEdges++;
				}
			}
		}

		// top to bottom stack edges
		for (int y = 0; y < height; y += yInc) {
			for (int x = 0; x < width; x += xInc) {
				for (int z = 1; z < d1; z++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackEdges++;
				}
			}
		}
		return nStackEdges;
	}/* end getStackEdges */

	/*---------------------------------------------------------------------*/
	/**
	 * Count the number of foreground voxel faces intersecting with stack faces
	 * This is part of &#967;<sub>2</sub> and is <i>c</i> in my working
	 *
	 * @param stack
	 * @return number of voxel faces intersecting with stack faces
	 */
	private long getStackFaces(final ImageStack stack) {
		
		final int w1 = width - 1;
		final int h1 = height - 1;
		final int d1 = depth -1;
		
		final int xInc = Math.max(1, w1);
		final int yInc = Math.max(1, h1);
		final int zInc = Math.max(1, d1);
		long nStackFaces = 0;

		// top and bottom faces
		for (int z = 0; z < depth; z += zInc) {
			for (int y = 1; y < h1; y++) {
				for (int x = 1; x < w1; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackFaces++;
				}
			}
		}

		// back and front faces
		for (int y = 0; y < height; y += yInc) {
			for (int z = 1; z < d1; z++) {
				for (int x = 1; x < w1; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackFaces++;
				}
			}
		}

		// left and right faces
		for (int x = 0; x < width; x += xInc) {
			for (int y = 1; y < h1; y++) {
				for (int z = 1; z < d1; z++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackFaces++;
				}
			}
		}
		return nStackFaces;
	}/* end getStackFaces */

	/**
	 * Count the number of voxel vertices intersecting stack faces. This
	 * contributes to &#967;<sub>2</sub> (<i>a</i> in my working)
	 *
	 * @param stack
	 * @return Number of voxel vertices intersecting stack faces
	 */
	private long getFaceVertices(final ImageStack stack) {
		final int xInc = Math.max(1, width - 1);
		final int yInc = Math.max(1, height - 1);
		final int zInc = Math.max(1, depth - 1);
		long nFaceVertices = 0;

		// top and bottom faces (all 4 edges)
		for (int z = 0; z < depth; z += zInc) {
			for (int y = 0; y <= height; y++) {
				for (int x = 0; x <= width; x++) {
					// if the voxel or any of its neighbours are foreground, the
					// vertex is counted
					if (getPixel(stack, x, y, z) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x, y - 1, z) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x - 1, y - 1, z) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x - 1, y, z) == -1)
						nFaceVertices++;
				}
			}
		}

		// left and right faces (2 vertical edges)
		for (int x = 0; x < width; x += xInc) {
			for (int y = 0; y <= height; y++) {
				for (int z = 1; z < depth; z++) {
					// if the voxel or any of its neighbours are foreground, the
					// vertex is counted
					if (getPixel(stack, x, y, z) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x, y - 1, z) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x, y - 1, z - 1) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x, y, z - 1) == -1)
						nFaceVertices++;
				}
			}
		}

		// back and front faces (0 vertical edges)
		for (int y = 0; y < height; y += yInc) {
			for (int x = 1; x < width; x++) {
				for (int z = 1; z < depth; z++) {
					// if the voxel or any of its neighbours are foreground, the
					// vertex is counted
					if (getPixel(stack, x, y, z) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x, y, z - 1) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x - 1, y, z - 1) == -1)
						nFaceVertices++;
					else if (getPixel(stack, x - 1, y, z) == -1)
						nFaceVertices++;
				}
			}
		}
		return nFaceVertices;
	}/* end getFaceVertices */

	/**
	 * Count the number of intersections between voxel edges and stack faces.
	 * This is part of &#967;<sub>2</sub>, in my working it's called <i>b</i>
	 *
	 * @param stack
	 * @return number of intersections between voxel edges and stack faces
	 */
	private long getFaceEdges(final ImageStack stack) {
		final int xInc = Math.max(1, width - 1);
		final int yInc = Math.max(1, height - 1);
		final int zInc = Math.max(1, depth - 1);
		long nFaceEdges = 0;

		// top and bottom faces (all 4 edges)
		// check 2 edges per voxel
		for (int z = 0; z < depth; z += zInc) {
			for (int y = 0; y <= height; y++) {
				for (int x = 0; x <= width; x++) {
					// if the voxel or any of its neighbours are foreground, the
					// vertex is counted
					if (getPixel(stack, x, y, z) == -1) {
						nFaceEdges += 2;
					} else {
						if (getPixel(stack, x, y - 1, z) == -1) {
							nFaceEdges++;
						}
						if (getPixel(stack, x - 1, y, z) == -1) {
							nFaceEdges++;
						}
					}
				}
			}
		}

		// back and front faces, horizontal edges
		for (int y = 0; y < height; y += yInc) {
			for (int z = 1; z < depth; z++) {
				for (int x = 0; x < width; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nFaceEdges++;
					else if (getPixel(stack, x, y, z - 1) == -1)
						nFaceEdges++;
				}
			}
		}

		// back and front faces, vertical edges
		for (int y = 0; y < height; y += yInc) {
			for (int z = 0; z < depth; z++) {
				for (int x = 0; x <= width; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nFaceEdges++;
					else if (getPixel(stack, x - 1, y, z) == -1)
						nFaceEdges++;
				}
			}
		}

		// left and right stack faces, horizontal edges
		for (int x = 0; x < width; x += xInc) {
			for (int z = 1; z < depth; z++) {
				for (int y = 0; y < height; y++) {
					if (getPixel(stack, x, y, z) == -1)
						nFaceEdges++;
					else if (getPixel(stack, x, y, z - 1) == -1)
						nFaceEdges++;
				}
			}
		}

		// left and right stack faces, vertical voxel edges
		for (int x = 0; x < width; x += xInc) {
			for (int z = 0; z < depth; z++) {
				for (int y = 1; y < height; y++) {
					if (getPixel(stack, x, y, z) == -1)
						nFaceEdges++;
					else if (getPixel(stack, x, y - 1, z) == -1)
						nFaceEdges++;
				}
			}
		}
		return nFaceEdges;
	}/* end getFaceEdges */

	/*-------------------------------------------------------------------------*/
	/**
	 * Count number of voxel vertices intersecting stack edges. It contributes
	 * to &#967;<sub>1</sub>, and I call it <i>d</i> in my working
	 *
	 * @param stack
	 * @return number of voxel vertices intersecting stack edges
	 */
	private long getEdgeVertices(final ImageStack stack) {
		final int xInc = Math.max(1, width - 1);
		final int yInc = Math.max(1, height - 1);
		final int zInc = Math.max(1, depth - 1);
		long nEdgeVertices = 0;

		// left->right edges
		for (int z = 0; z < depth; z += zInc) {
			for (int y = 0; y < height; y += yInc) {
				for (int x = 1; x < width; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nEdgeVertices++;
					else if (getPixel(stack, x - 1, y, z) == -1)
						nEdgeVertices++;
				}
			}
		}

		// back->front edges
		for (int z = 0; z < depth; z += zInc) {
			for (int x = 0; x < width; x += xInc) {
				for (int y = 1; y < height; y++) {
					if (getPixel(stack, x, y, z) == -1)
						nEdgeVertices++;
					else if (getPixel(stack, x, y - 1, z) == -1)
						nEdgeVertices++;
				}
			}
		}

		// top->bottom edges
		for (int x = 0; x < width; x += xInc) {
			for (int y = 0; y < height; y += yInc) {
				for (int z = 1; z < depth; z++) {
					if (getPixel(stack, x, y, z) == -1)
						nEdgeVertices++;
					else if (getPixel(stack, x, y, z - 1) == -1)
						nEdgeVertices++;
				}
			}
		}
		return nEdgeVertices;
	}/* end getEdgeVertices */

	/*----------------------------------------------------------------------*/
	/**
	 * <p>
	 * Calculate a correction value to convert the Euler number of a stack to
	 * the stack's contribution to the Euler number of whatever it is cut from.
	 * <ol type="a">
	 * <li>Number of voxel vertices on stack faces</li>
	 * <li>Number of voxel edges on stack faces</li>
	 * <li>Number of voxel faces on stack faces</li>
	 * <li>Number of voxel vertices on stack edges</li>
	 * <li>Number of voxel edges on stack edges</li>
	 * <li>Number of voxel vertices on stack vertices</li>
	 * </ol>
	 * </p>
	 * <p>
	 * Subtract the returned value from the Euler number prior to calculation of
	 * connectivity
	 * </p>
	 *
	 * @param stack
	 * @return edgeCorrection for subtraction from the stack's Euler number
	 */
	private double correctForEdges(final ImageStack stack) {

		final long f = getStackVertices(stack);
		final long e = getStackEdges(stack) + 3 * f;
		final long c = getStackFaces(stack) + 2 * e - 3 * f;
		final long d = getEdgeVertices(stack) + f;
		final long a = getFaceVertices(stack);
		final long b = getFaceEdges(stack);

		final double chiZero = f;
		final double chiOne = (double) d - (double) e;
		final double chiTwo = (double) a - (double) b + c;

		final double edgeCorrection = chiTwo / 2 + chiOne / 4 + chiZero / 8;

		return edgeCorrection;
	}/* end correctForEdges */

	/*
	 * -----------------------------------------------------------------------
	 */
	/**
	 * Fill Euler LUT Only odd indices are needed because we only check object
	 * voxels' neighbours, so there is always a 1 in each index.
	 *
	 * This is derived from Toriwaki & Yonekura (2002) Table 2 for 26-connected
	 * images.
	 */
	private final static int[] fillEulerLUT() {
		final int[] lut = new int[256];
		lut[1] = 1;
		lut[3] = 0;
		lut[5] = 0;
		lut[7] = -1;
		lut[9] = -2;
		lut[11] = -1;
		lut[13] = -1;
		lut[15] = 0;
		lut[17] = 0;
		lut[19] = -1;
		lut[21] = -1;
		lut[23] = -2;
		lut[25] = -3;
		lut[27] = -2;
		lut[29] = -2;
		lut[31] = -1;
		lut[33] = -2;
		lut[35] = -1;
		lut[37] = -3;
		lut[39] = -2;
		lut[41] = -1;
		lut[43] = -2;
		lut[45] = 0;
		lut[47] = -1;
		lut[49] = -1;

		lut[51] = 0;
		lut[53] = -2;
		lut[55] = -1;
		lut[57] = 0;
		lut[59] = -1;
		lut[61] = 1;
		lut[63] = 0;
		lut[65] = -2;
		lut[67] = -3;
		lut[69] = -1;
		lut[71] = -2;
		lut[73] = -1;
		lut[75] = 0;
		lut[77] = -2;
		lut[79] = -1;
		lut[81] = -1;
		lut[83] = -2;
		lut[85] = 0;
		lut[87] = -1;
		lut[89] = 0;
		lut[91] = 1;
		lut[93] = -1;
		lut[95] = 0;
		lut[97] = -1;
		lut[99] = 0;

		lut[101] = 0;
		lut[103] = 1;
		lut[105] = 4;
		lut[107] = 3;
		lut[109] = 3;
		lut[111] = 2;
		lut[113] = -2;
		lut[115] = -1;
		lut[117] = -1;
		lut[119] = 0;
		lut[121] = 3;
		lut[123] = 2;
		lut[125] = 2;
		lut[127] = 1;
		lut[129] = -6;
		lut[131] = -3;
		lut[133] = -3;
		lut[135] = 0;
		lut[137] = -3;
		lut[139] = -2;
		lut[141] = -2;
		lut[143] = -1;
		lut[145] = -3;
		lut[147] = 0;
		lut[149] = 0;

		lut[151] = 3;
		lut[153] = 0;
		lut[155] = 1;
		lut[157] = 1;
		lut[159] = 2;
		lut[161] = -3;
		lut[163] = -2;
		lut[165] = 0;
		lut[167] = 1;
		lut[169] = 0;
		lut[171] = -1;
		lut[173] = 1;
		lut[175] = 0;
		lut[177] = -2;
		lut[179] = -1;
		lut[181] = 1;
		lut[183] = 2;
		lut[185] = 1;
		lut[187] = 0;
		lut[189] = 2;
		lut[191] = 1;
		lut[193] = -3;
		lut[195] = 0;
		lut[197] = -2;
		lut[199] = 1;

		lut[201] = 0;
		lut[203] = 1;
		lut[205] = -1;
		lut[207] = 0;
		lut[209] = -2;
		lut[211] = 1;
		lut[213] = -1;
		lut[215] = 2;
		lut[217] = 1;
		lut[219] = 2;
		lut[221] = 0;
		lut[223] = 1;
		lut[225] = 0;
		lut[227] = 1;
		lut[229] = 1;
		lut[231] = 2;
		lut[233] = 3;
		lut[235] = 2;
		lut[237] = 2;
		lut[239] = 1;
		lut[241] = -1;
		lut[243] = 0;
		lut[245] = 0;
		lut[247] = 1;
		lut[249] = 2;
		lut[251] = 1;
		lut[253] = 1;
		lut[255] = 0;
		
		return lut;
	}/* end fillEulerLUT */
}
