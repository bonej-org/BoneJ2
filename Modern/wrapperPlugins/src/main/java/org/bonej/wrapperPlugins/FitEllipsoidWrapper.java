/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
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

package org.bonej.wrapperPlugins;

import static net.imagej.ops.stats.regression.leastSquares.Quadric.MIN_DATA;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.imagej.ops.OpService;
import net.imagej.ops.stats.regression.leastSquares.Quadric;
import net.imagej.patcher.LegacyInjector;

import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.QuadricToEllipsoid;
import org.bonej.utilities.ImagePlusUtil;
import org.bonej.utilities.RoiManagerUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

/**
 * A command that takes point ROIs from the ROI manager, and tries to fit an
 * ellipsoid on them. If the fitting succeeds, it reports the properties of the
 * ellipsoid.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Fit ellipsoid")
public class FitEllipsoidWrapper extends BoneJCommand {

	static {
		// NB: Needed if you mix-and-match IJ1 and IJ2 in a class.
		// And even then: do not use IJ1 classes in the API!
		LegacyInjector.preinit();
	}

	// Take in ImagePlus because there's already legacy dependencies (ROIs), and
	// there's no stable ROI system for ImageJ2 classes yet.
	@Parameter(validater = "validateImage")
	private ImagePlus inputImage;
	@Parameter
	private OpService opService;
	@Parameter
	private StatusService statusService;
	@Parameter
	private UIService uiService;
	@Parameter
	private LogService logService;

	private List<Vector3d> points;

	@Override
	public void run() {
		if (!initPointROIs()) {
			cancelMacroSafe(this, "Please populate ROI Manager with at least "
					+ MIN_DATA + " point ROIs");
			return;
		}
		statusService.showStatus("Fit ellipsoid: solving ellipsoid equation");
		statusService.showProgress(0, 2);
		final Matrix4dc quadric = (Matrix4dc) opService.run(Quadric.class, points);
		statusService.showStatus("Fit ellipsoid: determining ellipsoid parameters");
		statusService.showProgress(1, 2);
		@SuppressWarnings("unchecked")
		final Optional<Ellipsoid> result = (Optional<Ellipsoid>) opService.run(
			QuadricToEllipsoid.class, quadric);
		if (!result.isPresent()) {
			cancelMacroSafe(this, "Can't fit ellipsoid to points.\n" +
				"Try adding more point ROIs to the ROI Manager and try again.");
			return;
		}
		addResults(result.get());
		resultsTable = SharedTable.getTable();
		reportUsage();
	}

	private void addResults(final Ellipsoid ellipsoid) {
		final String unitHeader = ResultUtils.getUnitHeader(inputImage);
		final String label = inputImage.getTitle();
		final Vector3dc centroid = ellipsoid.getCentroid();
		SharedTable.add(label, "Centroid x " + unitHeader, centroid.x());
		SharedTable.add(label, "Centroid y " + unitHeader, centroid.y());
		SharedTable.add(label, "Centroid z " + unitHeader, centroid.z());
		SharedTable.add(label, "Radius a " + unitHeader, ellipsoid.getA());
		SharedTable.add(label, "Radius b " + unitHeader, ellipsoid.getB());
		SharedTable.add(label, "Radius c " + unitHeader, ellipsoid.getC());
	}

	private boolean initPointROIs() {
		// You can't have a RoiManager as a @Parameter with its own validator method
		final RoiManager manager = RoiManager.getInstance();
		if (manager == null) {
			return false;
		}
		final Calibration calibration = inputImage.getCalibration();
		final Function<Vector3d, Vector3d> calibrate = v -> {
			v.x *= calibration.pixelWidth;
			v.y *= calibration.pixelHeight;
			v.z *= calibration.pixelDepth;
			return v;
		};
		points = RoiManagerUtil.pointROICoordinates(manager).stream().filter(
			p -> !RoiManagerUtil.isActiveOnAllSlices((int) p.z)).map(calibrate)
			.collect(Collectors.toList());
		return points.size() >= MIN_DATA;
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}
		if (!ImagePlusUtil.is3D(inputImage)) {
			cancelMacroSafe(this, NOT_3D_IMAGE);
			return;
		}
		if (!Common.warnAnisotropy(inputImage, uiService, logService)) {
			cancel(null);
		}
	}
}
