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

import java.util.concurrent.atomic.AtomicInteger;

import org.doube.util.ImageCheck;
import org.doube.util.Multithreader;
import org.doube.util.ResultInserter;
import org.doube.util.UsageReporter;

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
 *      <a href="http://dx.doi.org/10.1016/8756-3282(93)90245-6">doi:10.1016
 *      /8756-3282(93)90245-6</a>
 *      </p>
 *      <p>
 *      Lee TC, Kashyap RL, Chu CN (1994) Building Skeleton Models via 3-D
 *      Medial Surface Axis Thinning Algorithms. CVGIP: Graphical Models and
 *      Image Processing 56: 462-478.
 *      <a href="http://dx.doi.org/10.1006/cgip.1994.1042" >doi:10.1006/cgip.
 *      1994.1042</a>
 *      </p>
 *      <p>
 *      Several of the methods are based on Ignacio Arganda-Carreras's
 *      Skeletonize3D_ plugin: <a href=
 *      "http://imagejdocu.tudor.lu/doku.php?id=plugin:morphology:skeletonize3d:start"
 *      >Skeletonize3D homepage</a>
 *      </p>
 *
 */
public class Connectivity implements PlugIn {

	/** working image width */
	private int width = 0;

	/** working image height */
	private int height = 0;

	/** working image depth */
	private int depth = 0;

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment())
			return;
		final ImagePlus imp = IJ.getImage();
		final ImageCheck ic = new ImageCheck();
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
	 * @param imp
	 *            Binary ImagePlus
	 * @param connectivity
	 *            Result of getConnectivity()
	 * @return
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
	 * @param deltaChi
	 *            result of getDeltaChi()
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

		final int eulerLUT[] = new int[256];
		fillEulerLUT(eulerLUT);

		final int[] sumEulerInt = new int[depth + 1];

		final AtomicInteger ai = new AtomicInteger(0);
		final Thread[] threads = Multithreader.newThreads();
		for (int thread = 0; thread < threads.length; thread++) {
			threads[thread] = new Thread(new Runnable() {
				@Override
				public void run() {
					long deltaEuler = 0;
					for (int z = ai.getAndIncrement(); z <= depth; z = ai.getAndIncrement()) {
						for (int y = 0; y <= height; y++) {
							for (int x = 0; x <= width; x++) {
								final byte[] octant = getOctant(stack, x, y, z);
								if (octant[0] > 0) { // this octant is not empty
									deltaEuler = getDeltaEuler(octant, eulerLUT);
									sumEulerInt[z] += deltaEuler;
								}
							}
						}
					}
				}
			});
		}
		Multithreader.startAndJoin(threads);
		double sumEuler = 0;
		for (int i = 0; i < sumEulerInt.length; i++) {
			sumEuler += sumEulerInt[i];
		}

		sumEuler /= 8;
		return sumEuler;
	}

	private void setDimensions(final ImagePlus imp) {
		this.width = imp.getWidth();
		this.height = imp.getHeight();
		this.depth = imp.getStackSize();
		return;
	}

	/*
	 * -----------------------------------------------------------------------
	 */
	/**
	 * Get octant of a vertex at (0,0,0) of a voxel (upper top left) in a 3D
	 * image (0 border conditions)
	 *
	 * @param stack
	 *            3D image (ImageStack)
	 * @param x
	 *            x- coordinate
	 * @param y
	 *            y- coordinate
	 * @param z
	 *            z- coordinate (in image stacks the indexes start at 1)
	 * @return corresponding 8-pixel octant (0 if out of image)
	 */
	private byte[] getOctant(final ImageStack stack, final int x, final int y, final int z) {
		final byte[] octant = new byte[9]; // index 0 is counter to determine
											// octant
		// emptiness, index 8 is at (x,y,z)

		octant[1] = getPixel(stack, x - 1, y - 1, z - 1);
		octant[2] = getPixel(stack, x - 1, y, z - 1);
		octant[3] = getPixel(stack, x, y - 1, z - 1);
		octant[4] = getPixel(stack, x, y, z - 1);
		octant[5] = getPixel(stack, x - 1, y - 1, z);
		octant[6] = getPixel(stack, x - 1, y, z);
		octant[7] = getPixel(stack, x, y - 1, z);
		octant[8] = getPixel(stack, x, y, z);

		for (int n = 1; n < 9; n++)
			octant[0] -= octant[n]; // foreground is -1, so octant[0] contains
		// nVoxels in octant
		return octant;
	} /* end getNeighborhood */

	/*
	 * -----------------------------------------------------------------------
	 */
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
	 * Only use this method when there is at least one foreground voxel in
	 * octant.
	 *
	 * In binary images, foreground is -1, background = 0
	 *
	 * @param octant
	 *            9 element array containing nVoxels in zeroth element and 8
	 *            voxel values
	 * @param LUT
	 *            Euler LUT
	 * @return or false if the point is Euler invariant or not
	 */
	private int getDeltaEuler(final byte[] octant, final int[] LUT) {
		int deltaEuler = 0;
		if (octant[0] == 0) { // check to make sure there is a foreground voxel
			// in this octant
			return deltaEuler;
		}
		char n = 1;
		// have to rotate octant voxels around vertex so that
		// octant[8] is foreground as eulerLUT assumes that voxel in position
		// 8 is always foreground. Only have to check each voxel once.
		if (octant[8] == -1) {
			n = 1;
			if (octant[1] == -1)
				n |= 128;
			if (octant[2] == -1)
				n |= 64;
			if (octant[3] == -1)
				n |= 32;
			if (octant[4] == -1)
				n |= 16;
			if (octant[5] == -1)
				n |= 8;
			if (octant[6] == -1)
				n |= 4;
			if (octant[7] == -1)
				n |= 2;
		} else if (octant[7] == -1) {
			n = 1;
			if (octant[2] == -1)
				n |= 128;
			if (octant[4] == -1)
				n |= 64;
			if (octant[1] == -1)
				n |= 32;
			if (octant[3] == -1)
				n |= 16;
			if (octant[6] == -1)
				n |= 8;
			if (octant[5] == -1)
				n |= 2;
		} else if (octant[6] == -1) {
			n = 1;
			if (octant[3] == -1)
				n |= 128;
			if (octant[1] == -1)
				n |= 64;
			if (octant[4] == -1)
				n |= 32;
			if (octant[2] == -1)
				n |= 16;
			if (octant[5] == -1)
				n |= 4;
		} else if (octant[5] == -1) {
			n = 1;
			if (octant[4] == -1)
				n |= 128;
			if (octant[3] == -1)
				n |= 64;
			if (octant[2] == -1)
				n |= 32;
			if (octant[1] == -1)
				n |= 16;
		} else if (octant[4] == -1) {
			n = 1;
			if (octant[1] == -1)
				n |= 8;
			if (octant[3] == -1)
				n |= 4;
			if (octant[2] == -1)
				n |= 2;
		} else if (octant[3] == -1) {
			n = 1;
			if (octant[2] == -1)
				n |= 8;
			if (octant[1] == -1)
				n |= 4;
		} else if (octant[2] == -1) {
			n = 1;
			if (octant[1] == -1)
				n |= 2;
		} else {
			// if we have got here, all the other voxels are background
			n = 1;
		}
		deltaEuler += LUT[n];
		return deltaEuler;
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
		final int xInc = Math.max(1, width - 1);
		final int yInc = Math.max(1, height - 1);
		final int zInc = Math.max(1, depth - 1);

		// vertex voxels contribute 3 edges
		// this could be taken out into a variable to avoid recalculating it
		// nStackEdges += getStackVertices(stack) * 3; = f * 3;

		// left to right stack edges
		for (int z = 0; z < depth; z += zInc) {
			for (int y = 0; y < height; y += yInc) {
				for (int x = 1; x < width - 1; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackEdges++;
				}
			}
		}

		// back to front stack edges
		for (int z = 0; z < depth; z += zInc) {
			for (int x = 0; x < width; x += xInc) {
				for (int y = 1; y < height - 1; y++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackEdges++;
				}
			}
		}

		// top to bottom stack edges
		for (int y = 0; y < height; y += yInc) {
			for (int x = 0; x < width; x += xInc) {
				for (int z = 1; z < depth - 1; z++) {
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
		final int xInc = Math.max(1, width - 1);
		final int yInc = Math.max(1, height - 1);
		final int zInc = Math.max(1, depth - 1);
		long nStackFaces = 0;

		// vertex voxels contribute 3 faces
		// this could be taken out into a variable to avoid recalculating it
		// nStackFaces += getStackVertices(stack) * 3;

		// edge voxels contribute 2 faces
		// this could be taken out into a variable to avoid recalculating it
		// nStackFaces += getStackEdges(stack) * 2;

		// top and bottom faces
		for (int z = 0; z < depth; z += zInc) {
			for (int y = 1; y < height - 1; y++) {
				for (int x = 1; x < width - 1; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackFaces++;
				}
			}
		}

		// back and front faces
		for (int y = 0; y < height; y += yInc) {
			for (int z = 1; z < depth - 1; z++) {
				for (int x = 1; x < width - 1; x++) {
					if (getPixel(stack, x, y, z) == -1)
						nStackFaces++;
				}
			}
		}

		// left and right faces
		for (int x = 0; x < width; x += xInc) {
			for (int y = 1; y < height - 1; y++) {
				for (int z = 1; z < depth - 1; z++) {
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

		// vertex voxels contribute 1 edge vertex each
		// this could be taken out into a variable to avoid recalculating it
		// nEdgeVertices += getStackVertices(stack);

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
		final long c = getStackFaces(stack) + 2 * e - 3 * f; // there are
																// already 6 *
		// f in 2 * e, so remove
		// 3 * f
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
	 *
	 * @param LUT
	 *            Euler LUT
	 */
	private final void fillEulerLUT(final int[] LUT) {
		LUT[1] = 1;
		LUT[3] = 0;
		LUT[5] = 0;
		LUT[7] = -1;
		LUT[9] = -2;
		LUT[11] = -1;
		LUT[13] = -1;
		LUT[15] = 0;
		LUT[17] = 0;
		LUT[19] = -1;
		LUT[21] = -1;
		LUT[23] = -2;
		LUT[25] = -3;
		LUT[27] = -2;
		LUT[29] = -2;
		LUT[31] = -1;
		LUT[33] = -2;
		LUT[35] = -1;
		LUT[37] = -3;
		LUT[39] = -2;
		LUT[41] = -1;
		LUT[43] = -2;
		LUT[45] = 0;
		LUT[47] = -1;
		LUT[49] = -1;

		LUT[51] = 0;
		LUT[53] = -2;
		LUT[55] = -1;
		LUT[57] = 0;
		LUT[59] = -1;
		LUT[61] = 1;
		LUT[63] = 0;
		LUT[65] = -2;
		LUT[67] = -3;
		LUT[69] = -1;
		LUT[71] = -2;
		LUT[73] = -1;
		LUT[75] = 0;
		LUT[77] = -2;
		LUT[79] = -1;
		LUT[81] = -1;
		LUT[83] = -2;
		LUT[85] = 0;
		LUT[87] = -1;
		LUT[89] = 0;
		LUT[91] = 1;
		LUT[93] = -1;
		LUT[95] = 0;
		LUT[97] = -1;
		LUT[99] = 0;

		LUT[101] = 0;
		LUT[103] = 1;
		LUT[105] = 4;
		LUT[107] = 3;
		LUT[109] = 3;
		LUT[111] = 2;
		LUT[113] = -2;
		LUT[115] = -1;
		LUT[117] = -1;
		LUT[119] = 0;
		LUT[121] = 3;
		LUT[123] = 2;
		LUT[125] = 2;
		LUT[127] = 1;
		LUT[129] = -6;
		LUT[131] = -3;
		LUT[133] = -3;
		LUT[135] = 0;
		LUT[137] = -3;
		LUT[139] = -2;
		LUT[141] = -2;
		LUT[143] = -1;
		LUT[145] = -3;
		LUT[147] = 0;
		LUT[149] = 0;

		LUT[151] = 3;
		LUT[153] = 0;
		LUT[155] = 1;
		LUT[157] = 1;
		LUT[159] = 2;
		LUT[161] = -3;
		LUT[163] = -2;
		LUT[165] = 0;
		LUT[167] = 1;
		LUT[169] = 0;
		LUT[171] = -1;
		LUT[173] = 1;
		LUT[175] = 0;
		LUT[177] = -2;
		LUT[179] = -1;
		LUT[181] = 1;
		LUT[183] = 2;
		LUT[185] = 1;
		LUT[187] = 0;
		LUT[189] = 2;
		LUT[191] = 1;
		LUT[193] = -3;
		LUT[195] = 0;
		LUT[197] = -2;
		LUT[199] = 1;

		LUT[201] = 0;
		LUT[203] = 1;
		LUT[205] = -1;
		LUT[207] = 0;
		LUT[209] = -2;
		LUT[211] = 1;
		LUT[213] = -1;
		LUT[215] = 2;
		LUT[217] = 1;
		LUT[219] = 2;
		LUT[221] = 0;
		LUT[223] = 1;
		LUT[225] = 0;
		LUT[227] = 1;
		LUT[229] = 1;
		LUT[231] = 2;
		LUT[233] = 3;
		LUT[235] = 2;
		LUT[237] = 2;
		LUT[239] = 1;
		LUT[241] = -1;
		LUT[243] = 0;
		LUT[245] = 0;
		LUT[247] = 1;
		LUT[249] = 2;
		LUT[251] = 1;
		LUT[253] = 1;
		LUT[255] = 0;
	}/* end fillEulerLUT */
}
