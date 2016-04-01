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

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.doube.geometry.Vectors;
import org.doube.util.ImageCheck;
import org.doube.util.ResultInserter;
import org.doube.util.StackStats;
import org.doube.util.UsageReporter;
import org.scijava.vecmath.Color3f;
import org.scijava.vecmath.Point3f;

import customnode.CustomTriangleMesh;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij3d.Content;
import ij3d.Executer;
import ij3d.Image3DUniverse;
import marchingcubes.MCTriangulator;

/**
 * Make a mesh from a binary or 8-bit image and get surface area measurements
 * from it.
 *
 * @author Michael Doube
 *
 */
public class MeasureSurface implements PlugIn {

	@Override
	@SuppressWarnings("unchecked")
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment())
			return;
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
			return;
		}
		int threshold = 128;
		if (ImageCheck.isBinary(imp)) {
			threshold = 128;
		} else if (imp.getBitDepth() == 8) {
			threshold = imp.getProcessor().getAutoThreshold(StackStats.getStackHistogram(imp));
		} else {
			IJ.error("Isosurface", "Image type not supported");
			return;
		}

		final GenericDialog gd = new GenericDialog("Options");
		gd.addNumericField("Resampling", 6, 0);
		gd.addNumericField("Threshold", threshold, 0);
		gd.addCheckbox("Show surface", true);
		gd.addCheckbox("Save as binary STL", false);
		gd.addHelp("http://bonej.org/isosurface");
		gd.showDialog();
		final int resamplingF = (int) Math.floor(gd.getNextNumber());
		threshold = (int) Math.floor(gd.getNextNumber());
		final boolean doSurfaceRendering = gd.getNextBoolean();
		final boolean doBinarySTL = gd.getNextBoolean();
		if (gd.wasCanceled())
			return;

		final boolean[] channels = { true, false, false };

		final MCTriangulator mct = new MCTriangulator();
		final List<Point3f> points = mct.getTriangles(imp, threshold, channels, resamplingF);

		IJ.log("Isosurface contains " + (points.size() / 3) + " triangles");

		final ResultInserter ri = ResultInserter.getInstance();
		final double area = getSurfaceArea(points);
		ri.setResultInRow(imp, "BS (" + imp.getCalibration().getUnits() + "²)", area);
		ri.updateTable();

		if (points.size() == 0) {
			IJ.error("Isosurface contains no points");
			return;
		}

		if (doSurfaceRendering) {
			renderSurface(points, "Surface of " + imp.getTitle());
		}

		if (doBinarySTL)
			writeBinarySTL(points);

		IJ.showStatus("Isosurface completed");

		UsageReporter.reportEvent(this).send();
		return;
	}

	/**
	 * Show the surface in the ImageJ 3D Viewer
	 *
	 * @param points
	 * @param title
	 */
	private void renderSurface(final List<Point3f> points, final String title) {
		IJ.showStatus("Generating mesh...");
		final CustomTriangleMesh mesh = new CustomTriangleMesh(points);

		// Create a universe
		final Image3DUniverse univ = new Image3DUniverse();

		// Add the mesh
		IJ.showStatus("Adding mesh to 3D viewer...");
		final Content c = univ.addCustomMesh(mesh, title);
		final Color3f green = new Color3f(0.0f, 0.5f, 0.0f);
		c.getColor();
		c.setColor(green);
		c.setTransparency((float) 0.33);
		c.setSelected(true);

		// show the universe
		IJ.showStatus("Displaying mesh in 3D viewer...");
		univ.show();
		IJ.showStatus("");
	}

	/**
	 * Calculate surface area of the isosurface
	 *
	 * @param points
	 *            in 3D triangle mesh
	 * @return surface area
	 */
	public static double getSurfaceArea(final List<Point3f> points) {
		double sumArea = 0;
		final int nPoints = points.size();
		final Point3f origin = new Point3f(0.0f, 0.0f, 0.0f);
		for (int n = 0; n < nPoints; n += 3) {
			IJ.showStatus("Calculating surface area...");
			// TODO reject triangle and continue if it is flush
			// with a cut face / image side

			// area of triangle is half magnitude
			// of cross product of 2 edge vectors
			final Point3f cp = Vectors.crossProduct(points.get(n), points.get(n + 1), points.get(n + 2));

			final double deltaArea = 0.5 * cp.distance(origin);

			sumArea += deltaArea;
		}
		return sumArea;
	}

	public static void writeBinarySTL(final List<Point3f> vertices) {
		try {
			final File stl_file = Executer.promptForFile("Save as binary STL", "untitled", ".stl");
			// OutputStreamWriter dos = null;
			DataOutputStream out = null;
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(stl_file)));

			String header = "Binary STL created by BoneJ.";
			for (int i = header.length(); i < 80; i++) {
				header = header + ".";
			}
			final int triangles = vertices.size() / 3;

			out.writeBytes(header);
			out.writeByte(triangles & 0xFF);
			out.writeByte((triangles >> 8) & 0xFF);
			out.writeByte((triangles >> 16) & 0xFF);
			out.writeByte((triangles >> 24) & 0xFF);
			for (int i = 0; i < vertices.size(); i += 3) {
				final Point3f p0 = vertices.get(i);
				final Point3f p1 = vertices.get(i + 2);
				final Point3f p2 = vertices.get(i + 1);
				final Point3f n = Vectors.normalise(Vectors.crossProduct(p0, p1, p2));
				final ByteBuffer bb = ByteBuffer.allocate(50);
				bb.order(ByteOrder.LITTLE_ENDIAN);
				bb.putFloat(n.x);
				bb.putFloat(n.y);
				bb.putFloat(n.z);
				bb.putFloat(p0.x);
				bb.putFloat(p0.y);
				bb.putFloat(p0.z);
				bb.putFloat(p1.x);
				bb.putFloat(p1.y);
				bb.putFloat(p1.z);
				bb.putFloat(p2.x);
				bb.putFloat(p2.y);
				bb.putFloat(p2.z);
				bb.putShort((short) 0);
				out.write(bb.array());
			}
			out.flush();
			out.close();
		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final NullPointerException e) {
			return;
		} catch (final Exception e) {
			IJ.showMessage("STL error",
					"Something went wrong writing your STL file." + "\nTry updating your 3D Viewer.");
		}
	}
}
