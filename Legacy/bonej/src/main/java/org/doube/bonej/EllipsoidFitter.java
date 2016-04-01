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

import org.doube.geometry.FitEllipsoid;
import org.doube.util.ImageCheck;
import org.doube.util.ResultInserter;
import org.doube.util.RoiMan;
import org.doube.util.UsageReporter;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

/**
 * <p>
 * Takes point selections from ROI manager and returns the centroid and radii of
 * a best fit ellipsoid
 * </p>
 *
 *
 * @author Michael Doube
 */
public class EllipsoidFitter implements PlugIn {

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
		if (!ImageCheck.isMultiSlice(imp)) {
			IJ.error("Stack required");
			return;
		}
		final RoiManager roiMan = RoiManager.getInstance();
		if (roiMan == null && imp != null) {
			IJ.error("Please populate ROI Manager with point ROIs");
			IJ.run("ROI Manager...");
			return;
		}
		if (!ImageCheck.isVoxelIsotropic(imp, 0.05)) {
			if (!IJ.showMessageWithCancel("Voxel depth problem",
					"Voxels are anisotropic." + "\nWidth = " + imp.getCalibration().pixelWidth + "\nHeight = "
							+ imp.getCalibration().pixelHeight + "\nDepth = " + imp.getCalibration().pixelDepth
							+ "\nClick OK if voxel dimensions are correct.")) {
				imp.unlock();
				IJ.run("Properties...");
			}
		}
		if (!imp.lock())
			imp.lock(); // if we have unlocked the image to reset properties,
		// relock it.

		final double[][] points = RoiMan.getRoiManPoints(imp, roiMan);
		// Object[] ellipsoid = { centre, radii, eigenVectors, equation, E };
		Object[] ellipsoid = new Object[5];
		try {
			ellipsoid = FitEllipsoid.yuryPetrov(points);
		} catch (final IllegalArgumentException ia) {
			IJ.showMessage(ia.getMessage());
			return;
		} catch (final RuntimeException re) {
			IJ.showMessage(
					"Can't fit ellipsoid to points.\n" + "Add more point ROI's to the ROI Manager and try again.");
			return;
		}

		final double[] centroid = (double[]) ellipsoid[0];
		final double[] radii = (double[]) ellipsoid[1];
		final String units = imp.getCalibration().getUnits();
		final ResultInserter ri = ResultInserter.getInstance();
		ri.setResultInRow(imp, "Centroid X(" + units + ")", centroid[0]);
		ri.setResultInRow(imp, "Centroid Y(" + units + ")", centroid[1]);
		ri.setResultInRow(imp, "Centroid Z(" + units + ")", centroid[2]);
		ri.setResultInRow(imp, "Radius 1 (" + units + ")", radii[0]);
		ri.setResultInRow(imp, "Radius 2 (" + units + ")", radii[1]);
		ri.setResultInRow(imp, "Radius 3 (" + units + ")", radii[2]);
		ri.updateTable();
		UsageReporter.reportEvent(this).send();
		return;
	}
}
