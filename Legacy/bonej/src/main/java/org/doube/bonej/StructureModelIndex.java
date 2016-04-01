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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.doube.geometry.Vectors;
import org.doube.util.ImageCheck;
import org.doube.util.ResultInserter;
import org.doube.util.UsageReporter;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import customnode.CustomTriangleMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij3d.Image3DUniverse;
import isosurface.MeshEditor;
import marchingcubes.MCTriangulator;
import process3d.Dilate_;

/**
 * <p>
 * Calculates the structure model index (SMI), a measure of how plate-like or
 * rod-like a structure is.
 * </p>
 *
 * <p>
 * Hildebrand T, Rüegsegger P. Quantification of Bone Microarchitecture with the
 * Structure Model Index. Comput Methods Biomech Biomed Engin 1997;1(1):15-23.
 * </p>
 * 
 * @author Michael Doube
 * @see <a href="http://doi.org/10.1080/01495739708936692">doi:
 *      10.1080/01495739708936692</a>
 *
 */
public class StructureModelIndex implements PlugIn {

	private static boolean do3D = false;
	private static List<Point3f> mesh;
	private static List<Color3f> colours;

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment()) {
			return;
		}
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
			return;
		}
		if (!ImageCheck.isBinary(imp)) {
			IJ.error("SMI needs a binary image.");
			return;
		}

		final GenericDialog gd = new GenericDialog("Mesh Parameters");
		final String[] smiMethods = { "Hildebrand & Rüegsegger", "SkyScan" };
		gd.addChoice("SMI Method", smiMethods, smiMethods[0]);
		gd.addNumericField("Voxel resampling", 6, 0, 5, "voxels");
		gd.addNumericField("Mesh smoothing (0-1)", 0.5, 3, 5, "");
		gd.addCheckbox("Show in 3D", do3D);
		gd.addHelp("http://bonej.org/smi");
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}

		if (!IJ.macroRunning())
		IJ.showMessage("SMI does not reliably measure rods and plates",
				"<html><p>SMI is strongly confounded by the amount of surface that is concave.</p>"
						+ "<p>Please use <b>caution</b> if measuring SMI for scientific research.</p>"
						+ "<p>For more details see:</p>"
						+ "<p>Salmon PL et al. (2015) Structure model index does not measure rods and plates in trabecular bone <a href=\"http://dx.doi.org/10.3389/fendo.2015.00162\">http://dx.doi.org/10.3389/fendo.2015.00162</a></p></html>");

		final String smiMethod = gd.getNextChoice();
		final int voxelResampling = (int) Math.floor(gd.getNextNumber());
		final float meshSmoothing = (float) gd.getNextNumber();
		do3D = gd.getNextBoolean();

		if (do3D) {
			mesh = new ArrayList<Point3f>();
			colours = new ArrayList<Color3f>();
		}

		double smi = 0;
		if (smiMethod.equals(smiMethods[1])) {
			smi = skyScan(imp, voxelResampling, meshSmoothing);
		} else {
			smi = hildRueg(imp, voxelResampling, meshSmoothing);
		}
		final ResultInserter ri = ResultInserter.getInstance();
		ri.setResultInRow(imp, "SMI", smi);
		ri.updateTable();
		UsageReporter.reportEvent(this).send();

		if (do3D) {
			final Image3DUniverse universe = new Image3DUniverse();

			final CustomTriangleMesh triangles = new CustomTriangleMesh(mesh);

			triangles.setColor(colours);

			universe.addTriangleMesh(mesh, colours, "Surface curvature");

			universe.show();
		}
		IJ.showProgress(1);
		return;
	}

	/**
	 * <p>
	 * Calculate the SMI according to the SkyScan technical manual: make a
	 * surface mesh, get the surface area (s<sub>1</sub>) and volume (v), dilate
	 * the voxel model, get the new surface area (s<sub>2</sub>), calculate SMI
	 * as <br/>
	 * smi = 6 * (s<sub>2</sub>-s<sub>1</sub>)*v / s<sub>1</sub><sup>2</sup>
	 * </p>
	 *
	 * @param imp
	 *            binary ImagePlus
	 * @param voxelResampling
	 *            resampling of voxels for smoother mesh creation. If set too
	 *            large will obliterate small features, if set too small, will
	 *            get too many surface triangles with jagging artefact.
	 * @param meshSmoothing
	 *            factor between 0 and 1 for smoothing of the surface mesh
	 * @return SMI. This is 0 for a plate, 3 for a rod and 4 for a sphere. SMI
	 *         can be negative if the structure contains many concave surfaces.
	 * @see <a href="http://www.skyscan.be/products/downloads.htm">The
	 *      description of measured parameters</a>
	 */
	@SuppressWarnings("unchecked")
	public static double skyScan(final ImagePlus imp, final int voxelResampling, final float meshSmoothing) {
		final int threshold = 128;
		final boolean[] channels = { true, false, false };
		final MCTriangulator mct = new MCTriangulator();
		IJ.showStatus("Finding surface points...");
		List<Point3f> points = mct.getTriangles(imp, threshold, channels, voxelResampling);
		final Color3f colour = new Color3f(0.0f, 0.0f, 0.0f);
		IJ.showStatus("Creating surface mesh...");
		CustomTriangleMesh surface = new CustomTriangleMesh(points, colour, 0.0f);
		IJ.showStatus("Smoothing surface mesh...");
		MeshEditor.smooth(surface, meshSmoothing);
		IJ.showStatus("Calculating volume...");
		final double v = Math.abs(surface.getVolume());

		final double s1 = MeasureSurface.getSurfaceArea(surface.getMesh());

		IJ.showStatus("Dilating voxel model...");
		final Dilate_ d = new Dilate_();
		final ImagePlus imp2 = d.dilate(imp, 255, false);

		IJ.showStatus("Finding surface points...");
		points = mct.getTriangles(imp2, threshold, channels, voxelResampling);
		imp2.changes = false;
		imp2.close();
		IJ.showStatus("Creating surface mesh...");
		surface = new CustomTriangleMesh(points, colour, 0.0f);
		IJ.showStatus("Smoothing surface mesh...");
		MeshEditor.smooth(surface, meshSmoothing);
		final double s2 = MeasureSurface.getSurfaceArea(surface.getMesh());
		final double smi = 6 * ((s2 - s1) * v / (s1 * s1));
		IJ.showStatus("SMI calculated.");
		return smi;
	}

	/**
	 * <p>
	 * Calculate the structure model index according to the description by
	 * Hildebrand and Rüegsegger. Creates a surface model, dilates it by a small
	 * increment and compares the areas before and after dilation.
	 * </p>
	 *
	 * <p>
	 * This method is preferred to voxel-dilating methods, like skyScan() in
	 * this class
	 * </p>
	 *
	 * <p>
	 * Hildebrand T, Rüegsegger P. Quantification of Bone Microarchitecture with
	 * the Structure Model Index. Comput Methods Biomech Biomed Engin
	 * 1997;1(1):15-23.
	 * </p>
	 *
	 * @see <a href="http://dx.doi.org/10.1080/01495739708936692">doi:
	 *      10.1080/01495739708936692</a>
	 *
	 * @param imp
	 *            binary 3D image
	 * @param voxelResampling
	 *            how much to resample voxels while creating surface mesh
	 * @param meshSmoothing
	 *            how much smoothing to apply to the mesh
	 * @return SMI
	 */
	@SuppressWarnings("unchecked")
	public static double hildRueg(final ImagePlus imp, final int voxelResampling, final float meshSmoothing) {
		final int threshold = 128;
		final boolean[] channels = { true, false, false };
		final double r = imp.getCalibration().pixelWidth / 100;
		final Point3f origin = new Point3f(0.0f, 0.0f, 0.0f);
		final MCTriangulator mct = new MCTriangulator();
		IJ.showStatus("Finding surface points...");
		final List<Point3f> triangles = mct.getTriangles(imp, threshold, channels, voxelResampling);
		final Color3f colour = new Color3f(0.0f, 0.0f, 0.0f);
		IJ.showStatus("Creating surface mesh...");
		final CustomTriangleMesh surface = new CustomTriangleMesh(triangles, colour, 0.0f);
		IJ.showStatus("Smoothing surface mesh...");
		MeshEditor.smooth(surface, meshSmoothing);
		IJ.showStatus("Calculating volume...");
		final double v = Math.abs(surface.getVolume());

		final double s1 = MeasureSurface.getSurfaceArea(surface.getMesh());

		// get all the unique vertices
		// associate each unique vertex with the triangles around it
		final Hashtable<Point3f, ArrayList<Integer>> vertexHash = new Hashtable<Point3f, ArrayList<Integer>>();
		ArrayList<Integer> locations = new ArrayList<Integer>();
		final int nPoints = triangles.size();
		for (int p = 0; p < nPoints; p++) {
			IJ.showStatus("Finding vertices...");
			IJ.showProgress(p + 1, nPoints);
			final Point3f testPoint = triangles.get(p);
			if (vertexHash.get(testPoint) == null) {
				final ArrayList<Integer> points = new ArrayList<Integer>();
				points.add(p);
				vertexHash.put(testPoint, points);
			} else {
				locations = vertexHash.get(testPoint);
				locations.add(p);
				vertexHash.put(testPoint, locations);
			}
		}

		// get the normals of the triangles around each vertex
		// and calculate the normal of the vertex as the mean triangle normal
		final Hashtable<Point3f, Point3f> normalsHash = new Hashtable<Point3f, Point3f>();
		Point3f vert = new Point3f();
		final Enumeration<Point3f> e = vertexHash.keys();
		while (e.hasMoreElements()) {
			IJ.showStatus("Calculating vertex normals...");
			vert = e.nextElement();
			locations = vertexHash.get(vert);
			final Point3f sumNormals = new Point3f();
			final int vT = locations.size();
			for (int i = 0; i < vT; i++) {
				final int pointIndex = locations.get(i);
				final int corner = pointIndex % 3;
				Point3f point0 = new Point3f();
				Point3f point1 = new Point3f();
				Point3f point2 = new Point3f();
				switch (corner) {
				case 0:
					point0 = triangles.get(pointIndex);
					point1 = triangles.get(pointIndex + 2);
					point2 = triangles.get(pointIndex + 1);
					break;
				case 1:
					point0 = triangles.get(pointIndex + 1);
					point1 = triangles.get(pointIndex);
					point2 = triangles.get(pointIndex - 1);
					break;
				case 2:
					point0 = triangles.get(pointIndex - 1);
					point1 = triangles.get(pointIndex - 2);
					point2 = triangles.get(pointIndex);
					break;
				}
				final Point3f surfaceNormal = Vectors.crossProduct(point0, point1, point2);
				sumNormals.x += surfaceNormal.x;
				sumNormals.y += surfaceNormal.y;
				sumNormals.z += surfaceNormal.z;
			}

			final Point3f normal = new Point3f();
			normal.x = sumNormals.x / vT;
			normal.y = sumNormals.y / vT;
			normal.z = sumNormals.z / vT;

			// Turn normal into a unit vector
			final double length = normal.distance(origin);
			normal.x /= length;
			normal.y /= length;
			normal.z /= length;

			normalsHash.put(vert, normal);
		}

		// move all the points by the unit normal * small increment r
		final ArrayList<Point3f> movedTriangles = new ArrayList<Point3f>();
		for (int t = 0; t < nPoints; t++) {
			IJ.showStatus("Dilating surface mesh...");
			IJ.showProgress(t + 1, nPoints);
			final Point3f point = triangles.get(t);
			final Point3f newPoint = (Point3f) point.clone();
			final Point3f normal = normalsHash.get(point);
			newPoint.x += normal.x * r;
			newPoint.y += normal.y * r;
			newPoint.z += normal.z * r;
			movedTriangles.add(newPoint);
		}

		double convexDelta = 0;
		double concaveDelta = 0;
		double convexArea = 0;
		double concaveArea = 0;

		// find the sums of the +ve and -ve changes in area
		for (int i = 0; i < nPoints; i += 3) {
			Point3f point0 = triangles.get(i);
			Point3f point1 = triangles.get(i + 1);
			Point3f point2 = triangles.get(i + 2);
			final double area1 = 0.5 * Vectors.crossProduct(point0, point1, point2).distance(origin);
			point0 = movedTriangles.get(i);
			point1 = movedTriangles.get(i + 1);
			point2 = movedTriangles.get(i + 2);
			final double area2 = 0.5 * Vectors.crossProduct(point0, point1, point2).distance(origin);

			final double deltaArea = area2 - area1;

			if (do3D)
				addTo3DUniverse(point0, point1, point2, area1, deltaArea);

			if (deltaArea >= 0) {
				convexDelta += deltaArea;
				convexArea += area1;
			} else if (deltaArea < 0) {
				concaveDelta += deltaArea;
				concaveArea += area1;
			}
		}

		final double concaveFraction = concaveArea / (concaveArea + convexArea);
		IJ.log("Fraction of surface area that is concave: " + concaveFraction);
		final double sRconvex = convexDelta / r;
		final double sRconcave = concaveDelta / r;
		final double convexSMI = 6 * sRconvex * v / (s1 * s1);
		final double concaveSMI = 6 * sRconcave * v / (s1 * s1);
		IJ.log("Convex SMI = " + convexSMI);
		IJ.log("Concave SMI = " + concaveSMI);

		final ResultInserter ri = ResultInserter.getInstance();
		ri.setResultInRow(imp, "Concave", concaveFraction);
		ri.setResultInRow(imp, "SMI+", convexSMI);
		ri.setResultInRow(imp, "SMI-", concaveSMI);
		ri.updateTable();

		final double s2 = MeasureSurface.getSurfaceArea(movedTriangles);
		final double sR = (s2 - s1) / r;
		final double smi = 6 * sR * v / (s1 * s1);
		IJ.showStatus("SMI calculated.");
		IJ.showProgress(1.0);
		return smi;
	}

	private static void addTo3DUniverse(final Point3f point0, final Point3f point1, final Point3f point2,
			final double area1, final double deltaArea) {

		mesh.add(point0);
		mesh.add(point1);
		mesh.add(point2);

		final double af = deltaArea / area1;

		float red = 1.0f;
		float green = 1.0f;
		float blue = 1.0f;

		if (af >= 0) {
			blue -= (float) Math.pow(af, 0.333) * 9;
		} else {
			red -= (float) Math.pow(-af, 0.333) * 9;
			green -= (float) Math.pow(-af, 0.333) * 9;
		}

		final Color3f colour = new Color3f(red, green, blue);
		colours.add(colour);
		colours.add(colour);
		colours.add(colour);

	}
}
