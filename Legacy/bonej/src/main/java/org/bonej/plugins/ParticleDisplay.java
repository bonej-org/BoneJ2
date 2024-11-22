/*-
 * #%L
 * Mavenized version of the BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bonej.geometry.Ellipsoid;
import org.bonej.geometry.FitEllipsoid;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Point3f;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import customnode.CustomPointMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij3d.Image3DUniverse;

/**
 * Display stacks and 3D visualisation of particles and particle analysis
 * 
 * @author Michael Doube
 *
 */
public class ParticleDisplay {

	/** Surface colour style: gradient */
	static final int GRADIENT = 0;
	/** Surface colour style: split */
	static final int SPLIT = 1;
	/** Surface colour style: orientation */
	static final int ORIENTATION = 2;

	// ----------------- STACK DISPLAY ---------------//

	/**
	 * Create an image showing some particle measurement
	 *
	 * @param imp            an image.
	 * @param particleLabels the particles in the image.
	 * @param values         list of values whose array indices correspond to
	 *                       particlelabels
	 * @return ImagePlus with particle labels substituted with some value
	 */
	static ImagePlus displayParticleValues(final ImagePlus imp, final int[][] particleLabels, final double[] values) {
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
		final ImagePlus impOut = new ImagePlus(imp.getShortTitle() + "_" + "volume", stack);
		impOut.setCalibration(imp.getCalibration());
		impOut.getProcessor().setMinAndMax(0, max);
		return impOut;
	}

	/**
	 * Display the particle labels as an ImagePlus
	 *
	 * @param particleLabels particles labelled in the original image.
	 * @param imp            original image, used for image dimensions, calibration
	 *                       and titles
	 * @return an image of the particles.
	 */
	static ImagePlus displayParticleLabels(final int[][] particleLabels, final ImagePlus imp) {
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
		final ImagePlus impParticles = new ImagePlus(imp.getShortTitle() + "_parts", stack);
		impParticles.setCalibration(imp.getCalibration());
		impParticles.getProcessor().setMinAndMax(0, max);
		if (max > ConnectedComponents.MAX_LABEL)
			IJ.error("Warning", "More than 2^23 particles. "
					+ "Particle label display is imprecise above this number due to int to float conversion.");
		return impParticles;
	}

	/**
	 * Draw ellipsoids in a stack
	 * 
	 * @param imp ImagePlus, needed for calibration
	 * @param ellipsoids list of ellipsoids
	 * @return ImagePlus containing particles drawn as best-fit solid ellipsoids
	 */
	static ImagePlus displayParticleEllipsoids(final ImagePlus imp, final Object[] ellipsoids) {
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		final int d = imp.getImageStackSize();

		Calibration cal = imp.getCalibration();
		final double pW = cal.pixelWidth;
		final double pH = cal.pixelHeight;
		final double pD = cal.pixelDepth;

		// set up a work array
		final ByteProcessor[] bps = new ByteProcessor[d];
		for (int z = 0; z < d; z++) {
			bps[z] = new ByteProcessor(w, h);
		}

		final int n = ellipsoids.length;
		for (int i = 0; i < n; i++) {
			IJ.showStatus("Drawing ellipsoid stack...");
			IJ.showProgress(i, n);
			Ellipsoid ellipsoid;
			try {
				ellipsoid = new Ellipsoid((Object[]) ellipsoids[i]);
			} catch (Exception e) {
				continue;
			}

			// ellipsoid is in calibrated real-world units
			final double[] box = ellipsoid.getAxisAlignedBoundingBox();

			// decalibrate to pixels
			final int xMin = clamp((int) Math.floor(box[0] / pW), 0, w - 1);
			final int xMax = clamp((int) Math.floor(box[1] / pW), 0, w - 1);
			final int yMin = clamp((int) Math.floor(box[2] / pH), 0, h - 1);
			final int yMax = clamp((int) Math.floor(box[3] / pH), 0, h - 1);
			final int zMin = clamp((int) Math.floor(box[4] / pD), 0, d - 1);
			final int zMax = clamp((int) Math.floor(box[5] / pD), 0, d - 1);

			// set the ellipsoid-contained pixels to foreground
			for (int z = zMin; z <= zMax; z++) {
				for (int y = yMin; y <= yMax; y++) {
					for (int x = xMin; x <= xMax; x++) {
						if (ellipsoid.contains(x * pW, y * pH, z * pD)) {
							bps[z].set(x, y, 255);
						}
					}
				}
			}
		}

		ImageStack stack = new ImageStack(w, h);
		for (ByteProcessor bp : bps)
			stack.addSlice(bp);

		final ImagePlus impOut = new ImagePlus(imp.getShortTitle() + "_Ellipsoids", stack);
		impOut.setCalibration(cal);
		return impOut;
	}

	// ----------------- 3D VIEWER DISPLAY ----------//

	/**
	 * Draw the particle centroids in a 3D viewer
	 *
	 * @param centroids [n][3] centroids of particles.
	 * @param univ      universe where the centroids are displayed.
	 */
	static void displayCentroids(final double[][] centroids, final Image3DUniverse univ) {
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
			} catch (final NullPointerException npe) {
				IJ.log("3D Viewer was closed before rendering completed.");
				return;
			}
		}
	}

	/**
	 * Draws 3 orthogonal axes defined by the centroid, unitvector and axis length.
	 *
	 * @param univ       the universe where axes are drawn.
	 * @param centroid   centroid of a particle.
	 * @param unitVector orientation of the particle.
	 * @param lengths    lengths of the axes.
	 * @param green      green component of the axes' color.
	 * @param title      text shown by the axes.
	 */
	private static void displayAxes(final Image3DUniverse univ, final double[] centroid, final double[][] unitVector,
			final double[] lengths, final float green, final String title) {
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
		} catch (final NullPointerException npe) {
			IJ.log("3D Viewer was closed before rendering completed.");
		}
	}

	/**
	 * Display principal axes in the 3D Viewer
	 * 
	 * @param univ 3D Viewer universe
	 * @param eigens list of eigenvalue decompositions
	 * @param centroids list of centroids
	 * @param particleSizes list of particle sizes
	 */
	static void displayPrincipalAxes(final Image3DUniverse univ, final EigenvalueDecomposition[] eigens,
			final double[][] centroids, long[] particleSizes) {
		final int nEigens = eigens.length;

		for (int p = 1; p < nEigens; p++) {
			IJ.showStatus("Rendering principal axes...");
			IJ.showProgress(p, nEigens);

			final long size = particleSizes[p];
			final Matrix eVec = eigens[p].getV();
			final Matrix eVal = eigens[p].getD();
			double[] lengths = new double[3];
			for (int i = 0; i < 3; i++) {
				lengths[i] = 2 * Math.sqrt(eVal.get(2 - i, 2 - i) / size);
			}
			displayAxes(univ, centroids[p], eVec.getArray(), lengths, 0.0f, "Principal Axes " + p);
		}
	}
	
	static void displayAlignedBoundingBoxes(double[][] alignedBoxes,
		final EigenvalueDecomposition[] eigens, Image3DUniverse univ)
	{
		final int nBoxes = alignedBoxes.length;
		
		for (int p = 1; p < nBoxes; p++) {
			final double[] box = alignedBoxes[p];
			final double cx = box[0];
			final double cy = box[1];
			final double cz = box[2];
			
			//use half-lengths because vertex positions are calculated as an offset from the centre
			final double l0 = box[3] / 2;
			final double l1 = box[4] / 2;
			final double l2 = box[5] / 2;
			
			//get the unit vectors
			final double[][] eVec = eigens[p].getV().getArray();
			final double eV0x = eVec[0][0];
			final double eV0y = eVec[1][0];
			final double eV0z = eVec[2][0];
			final double eV1x = eVec[0][1];
			final double eV1y = eVec[1][1];
			final double eV1z = eVec[2][1];
			final double eV2x = eVec[0][2];
			final double eV2y = eVec[1][2];
			final double eV2z = eVec[2][2];
			
			//calculate the 3 semi-axis vectors
			final double v0x = l0 * eV0x;
			final double v0y = l0 * eV0y;
			final double v0z = l0 * eV0z;
			
			final double v1x = l1 * eV1x;
			final double v1y = l1 * eV1y;
			final double v1z = l1 * eV1z;
			
			final double v2x = l2 * eV2x;
			final double v2y = l2 * eV2y;
			final double v2z = l2 * eV2z;
			
			//calculate the positions of 8 vertices by summing the vector components
			Point3f v0 = new Point3f();
			v0.x = (float)(cx - v0x - v1x - v2x);
			v0.y = (float)(cy - v0y - v1y - v2y);
			v0.z = (float)(cz - v0z - v1z - v2z);
			
			Point3f v1 = new Point3f();
			v1.x = (float)(cx - v0x + v1x - v2x);
			v1.y = (float)(cy - v0y + v1y - v2y);
			v1.z = (float)(cz - v0z + v1z - v2z);
			
			Point3f v2 = new Point3f();
			v2.x = (float)(cx - v0x + v1x + v2x);
			v2.y = (float)(cy - v0y + v1y + v2y);
			v2.z = (float)(cz - v0z + v1z + v2z);
			
			Point3f v3 = new Point3f();
			v3.x = (float)(cx - v0x - v1x + v2x);
			v3.y = (float)(cy - v0y - v1y + v2y);
			v3.z = (float)(cz - v0z - v1z + v2z);
			
			Point3f v4 = new Point3f();
			v4.x = (float)(cx + v0x - v1x - v2x);
			v4.y = (float)(cy + v0y - v1y - v2y);
			v4.z = (float)(cz + v0z - v1z - v2z);
			
			Point3f v5 = new Point3f();
			v5.x = (float)(cx + v0x + v1x - v2x);
			v5.y = (float)(cy + v0y + v1y - v2y);
			v5.z = (float)(cz + v0z + v1z - v2z);
			
			Point3f v6 = new Point3f();
			v6.x = (float)(cx + v0x + v1x + v2x);
			v6.y = (float)(cy + v0y + v1y + v2y);
			v6.z = (float)(cz + v0z + v1z + v2z);
			
			Point3f v7 = new Point3f();
			v7.x = (float)(cx + v0x - v1x + v2x);
			v7.y = (float)(cy + v0y - v1y + v2y);
			v7.z = (float)(cz + v0z - v1z + v2z);
			
			//construct a mesh with consecutive pairs of vertices for each edge
			final List<Point3f> mesh = new ArrayList<>();
			mesh.add(v0); mesh.add(v1);
			mesh.add(v1); mesh.add(v2);
			mesh.add(v2); mesh.add(v3);
			mesh.add(v3); mesh.add(v0);
			mesh.add(v0); mesh.add(v4);
			mesh.add(v1); mesh.add(v5);
			mesh.add(v2); mesh.add(v6);
			mesh.add(v3); mesh.add(v7);
			mesh.add(v4); mesh.add(v5);
			mesh.add(v5); mesh.add(v6);
			mesh.add(v6); mesh.add(v7);
			mesh.add(v7); mesh.add(v4);
			
			final Color3f aColour = new Color3f(1.0f, 1.0f, 0.0f);
			try {
				univ.addLineMesh(mesh, aColour, "aligned box "+p, false).setLocked(true);
			} catch (final NullPointerException npe) {
				IJ.log("3D Viewer was closed before rendering completed.");
			}
		}
		
	}
	
	/**
	 * Display Feret points and axis in the 3D Viewer
	 * 
	 * @param univ 3D Viewer universe
	 * @param ferets array of results from {@link ParticleAnalysis#getFerets(List)}
	 */
	static void displayMaxFeret(final Image3DUniverse univ, double[][] ferets) {
		final int nParticles = ferets.length;

		for (int p = 1; p < nParticles; p++) {
			IJ.showStatus("Rendering Ferets...");
			IJ.showProgress(p, nParticles);
			
			final double[] f = ferets[p];
			
			if (Double.isNaN(f[0]))
				continue;
			
			final Point3f a = new Point3f((float) f[1], (float) f[2], (float) f[3]);
			final Point3f b = new Point3f((float) f[4], (float) f[5], (float) f[6]);
			
			final List<Point3f> points = Arrays.asList(a, b);
			
			final CustomPointMesh feretPointMesh = new CustomPointMesh(points);
			
			feretPointMesh.setPointSize(5.0f);
			final float red = 0.0f;
			final float green = 1.0f;
			final float blue = 0.5f;
			final Color3f cColour = new Color3f(red, green, blue);
			feretPointMesh.setColor(cColour);
			
			try {
				univ.addCustomMesh(feretPointMesh, "Feret " + p);
				univ.addLineMesh(points, cColour, "Feret axis " + p, false);
			} catch (final NullPointerException npe) {
				IJ.log("3D Viewer was closed before rendering completed.");
				return;
			}
		}
	}

	/**
	 * Display ellipsoids in the 3D Viewer as point clouds
	 * 
	 * @param ellipsoids list of ellipsoids
	 * @param univ 3D Viewer universe
	 */
	static void displayEllipsoids(final Object[] ellipsoids, final Image3DUniverse univ) {
		final int nEllipsoids = ellipsoids.length;
		for (int el = 1; el < nEllipsoids; el++) {
			IJ.showStatus("Rendering ellipsoids...");
			IJ.showProgress(el, nEllipsoids);
			Object[] ellipsoidObject = (Object[]) ellipsoids[el];
			if (ellipsoids[el] == null) {
				continue;
			}
			final double[] radii = (double[]) ellipsoidObject[1];
			if (!isRadiiValid(radii)) {
				continue;
			}
			final double[] centre = (double[]) ellipsoidObject[0];
			final double[][] eV = (double[][]) ellipsoidObject[2];
			final double a = radii[0]; // longest
			final double b = radii[1]; // middle
			final double c = radii[2]; // shortest
			if (a < b || b < c || a < c) {
				IJ.log("Error: Bad ellipsoid radius ordering! Surface: " + el);
			}
			final double[][] ellipsoid = FitEllipsoid.testEllipsoid(a, b, c, 0, 0, 0, 0, 0, 1000, false);
			final int nPoints = ellipsoid.length;
			// rotate points by eigenvector matrix
			// and add transformation for centre
			for (int p = 0; p < nPoints; p++) {
				final double x = ellipsoid[p][0];
				final double y = ellipsoid[p][1];
				final double z = ellipsoid[p][2];
				ellipsoid[p][0] = x * eV[0][0] + y * eV[0][1] + z * eV[0][2] + centre[0];
				ellipsoid[p][1] = x * eV[1][0] + y * eV[1][1] + z * eV[1][2] + centre[1];
				ellipsoid[p][2] = x * eV[2][0] + y * eV[2][1] + z * eV[2][2] + centre[2];
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
			} catch (final NullPointerException npe) {
				IJ.log("3D Viewer was closed before rendering completed.");
				return;
			}
			// Add some axes
			displayAxes(univ, centre, eV, radii, 1.0f, "Ellipsoid Axes " + el);
		}
	}

	/**
	 * Draw the particle surfaces in a 3D viewer
	 *
	 * @param univ          universe where the centroids are displayed.
	 * @param surfacePoints points of each particle.
	 * @param colourMode  colour particles by SPLIT, GRADIENT, or ORIENTATION
	 * @param volumes   list of particle volumes
	 * @param splitValue volume at which to split the colours for SPLIT colour option
	 * @param eigens list of eigendecompositions, needed for ORIENTATION colouring
	 */
	static void displayParticleSurfaces(final Image3DUniverse univ, final List<List<Point3f>> surfacePoints,
			final int colourMode, final double[] volumes, final double splitValue,
			final EigenvalueDecomposition[] eigens) {
		final int nSurfaces = surfacePoints.size();
		for (int p = 1; p < nSurfaces; p++) {
			IJ.showStatus("Rendering surfaces...");
			IJ.showProgress(p, nSurfaces);
			final List<Point3f> surfacePoint = surfacePoints.get(p);
			if (surfacePoint == null)
				continue;
			if (!surfacePoint.isEmpty()) {
				Color3f colour = getColour(p, nSurfaces, colourMode, volumes, eigens, splitValue);
				// Add the mesh
				try {
					univ.addTriangleMesh(surfacePoint, colour, "Surface " + p).setLocked(true);
				} catch (final NullPointerException npe) {
					IJ.log("3D Viewer was closed before rendering completed.");
					return;
				}
			}
		}
	}

	/**
	 * Display the input image in the 3D Viewer
	 * 
	 * @param imp Input image
	 * @param resampling pixel resampling for the viewer; minimum 1
	 * @param univ 3D Viewer universe
	 */
	static void display3DOriginal(final ImagePlus imp, final int resampling, final Image3DUniverse univ) {
		final Color3f colour = new Color3f(1.0f, 1.0f, 1.0f);
		final boolean[] channels = { true, true, true };
		try {
			univ.addVoltex(imp, colour, imp.getTitle(), 0, channels, resampling).setLocked(true);
		} catch (final NullPointerException npe) {
			IJ.log("3D Viewer was closed before rendering completed.");
		}
	}

	// ----------------- HELPER METHODS --------------//

	/**
	 * Calculate a colour based on particle parameters
	 * 
	 * @param p particle number, for GRADIENT
	 * @param nSurfaces total number of particles being displayed for GRADIENT
	 * @param colourMode SPLIT, GRADIENT or ORIENTATION
	 * @param volumes particle volumes, for SPLIT
	 * @param eigens particle eigendecompositions, for ORIENTATION
	 * @param splitValue volumt at which colour changes
	 * 
	 * @return RGB (Color3f) colour of the particle
	 */
	private static Color3f getColour(int p, int nSurfaces, int colourMode, double[] volumes, 
			EigenvalueDecomposition[] eigens, double splitValue) {
		Color3f colour = new Color3f(0, 0, 0);
		if (colourMode == GRADIENT) {
			final float red = 1.0f - p / (float) nSurfaces;
			final float green = 1.0f - red;
			final float blue = p / (2.0f * nSurfaces);
			colour = new Color3f(red, green, blue);
		} else if (colourMode == SPLIT) {
			if (volumes[p] > splitValue) {
				// red if over
				colour = new Color3f(1.0f, 0.0f, 0.0f);
			} else {
				// yellow if under
				colour = new Color3f(1.0f, 1.0f, 0.0f);
			}
		} else if (colourMode == ORIENTATION) {
			colour = ParticleDisplay.colourFromEigenVector(eigens[p]);
		}
		return colour;
	}
	
	/**
	 * Generate a colour based on the inertia tensor's eigenvector
	 * 
	 * Colour is from the HSB colour wheel scaled by 0.5 to fit into pi radians
	 * (rather than the 2 pi it normally occupies), so that red is at 0, pi and 2pi
	 * radians.
	 * 
	 * Colour is mapped to the axis-angle representation of the tensor so hue varies
	 * as a function of second axis rotation around the first.
	 * 
	 * @param eigen Eigenvalue decomposition of the particle
	 * @return Colour scaling in red for axis and green for angle
	 */
	static Color3f colourFromEigenVector(EigenvalueDecomposition eigen) {
		final Matrix rotation = eigen.getV();

		// deflection of long axis from image z axis, 0 - pi radians
		final double angle = Math.acos(-Math.abs(rotation.get(2, 0)));

		final float hue = (float) (angle / Math.PI);
		final float saturation = 1.0f;
		final float brightness = 1.0f;

		final int rgb = Color.HSBtoRGB(hue, saturation, brightness);
		final Color color = new Color(rgb);
		float red = color.getRed() / 255f;
		float green = color.getGreen() / 255f;
		float blue = color.getBlue() / 255f;

		return new Color3f(red, green, blue);
	}

	/**
	 * Check whether ellipsoid radii are NaN
	 * 
	 * @param radii list of radii
	 * @return true if all radii are not NaN
	 */
	private static boolean isRadiiValid(final double[] radii) {
		for (int r = 0; r < 3; r++) {
			if (Double.isNaN(radii[r])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Limit value to within minimum and maxium values
	 * 
	 * @param value the input value
	 * @param min the allowed minimum
	 * @param max the allowed maximum
	 * @return value, or min if value < min; max if value > max
	 */
	private static int clamp(int value, int min, int max) {
		if (min > max) {
			throw new IllegalArgumentException("min must be less than or equal to max");
		}
		if (value < min)
			return min;
		if (value > max)
			return max;
		return value;
	}

}
