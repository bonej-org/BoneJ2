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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.bonej.geometry.Ellipsoid;
import org.bonej.geometry.FitEllipsoid;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import customnode.CustomPointMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij3d.Image3DUniverse;

public class ParticleDisplay {

	/** Surface colour style */
	static final int GRADIENT = 0;
	static final int SPLIT = 1;
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
	 * 
	 * @param imp
	 * @param ellipsoids
	 * @param title
	 * @return ImagePlus containing particles drawn as best-fit solid ellipsoids
	 */
	static ImagePlus displayParticleEllipsoids(final ImagePlus imp, final Object[] ellipsoids, final String title) {
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

		final ImagePlus impOut = new ImagePlus(imp.getShortTitle() + "_" + title, stack);
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
	 */
	static void displayParticleSurfaces(final Image3DUniverse univ, final Collection<List<Point3f>> surfacePoints,
			final int colourMode, final double[] volumes, final double splitValue,
			final EigenvalueDecomposition[] eigens) {
		int p = 0;
		final int nSurfaces = surfacePoints.size();
		for (final List<Point3f> surfacePoint : surfacePoints) {
			IJ.showStatus("Rendering surfaces...");
			IJ.showProgress(p, nSurfaces);
			if (p > 0 && !surfacePoint.isEmpty()) {
				Color3f pColour = new Color3f(0, 0, 0);
				if (colourMode == GRADIENT) {
					final float red = 1.0f - p / (float) nSurfaces;
					final float green = 1.0f - red;
					final float blue = p / (2.0f * nSurfaces);
					pColour = new Color3f(red, green, blue);
				} else if (colourMode == SPLIT) {
					if (volumes[p] > splitValue) {
						// red if over
						pColour = new Color3f(1.0f, 0.0f, 0.0f);
					} else {
						// yellow if under
						pColour = new Color3f(1.0f, 1.0f, 0.0f);
					}
				} else if (colourMode == ORIENTATION) {
					pColour = ParticleDisplay.colourFromEigenVector(eigens[p]);
				}
				// Add the mesh
				try {
					univ.addTriangleMesh(surfacePoint, pColour, "Surface " + p).setLocked(true);
				} catch (final NullPointerException npe) {
					IJ.log("3D Viewer was closed before rendering completed.");
					return;
				}
			}
			p++;
		}
	}

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
		float red = (float) (color.getRed() / 255d);
		float green = (float) (color.getGreen() / 255d);
		float blue = (float) (color.getBlue() / 255d);

		return new Color3f(red, green, blue);
	}

	private static boolean isRadiiValid(final double[] radii) {
		for (int r = 0; r < 3; r++) {
			if (Double.isNaN(radii[r])) {
				return false;
			}
		}
		return true;
	}

	private static int clamp(int value, int min, int max) {
		if (value < min)
			return min;
		if (value > max)
			return max;
		return value;
	}

}
