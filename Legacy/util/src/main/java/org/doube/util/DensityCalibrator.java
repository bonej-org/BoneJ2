/*
 * #%L
 * BoneJ utility classes.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers.
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
package org.doube.util;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.util.DicomTools;

public class DensityCalibrator implements PlugIn {

	@Override
	public void run(final String arg) {
		final ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.noImage();
			return;
		}
		if (arg.equals("scanco"))
			try {
				scanco(imp);
			} catch (final NumberFormatException e) {
				IJ.error("Calibration data missing from DICOM header");
				return;
			} catch (final NullPointerException e) {
				IJ.error("Calibration data missing from DICOM header");
				return;
			} catch (final IllegalArgumentException e) {
				IJ.error(e.getMessage());
				return;
			} catch (final Exception e) {
				IJ.error("Can't calibrate image\n" + e.getMessage());
				return;
			}
		UsageReporter.reportEvent(this).send();
	}

	private void scanco(final ImagePlus imp) throws IllegalArgumentException {
		final String manufacturer = DicomTools.getTag(imp, "0008,0070");
		if (manufacturer == null || !manufacturer.contains("SCANCO")) {
			throw new IllegalArgumentException("File is not a SCANCO Medical DICOM");
		}
		final double slope = Double.parseDouble(DicomTools.getTag(imp, "0029,1004"));
		final double intercept = Double.parseDouble(DicomTools.getTag(imp, "0029,1005"));
		final double scaling = Double.parseDouble(DicomTools.getTag(imp, "0029,1000"));
		final double c = intercept - 32768 * slope / scaling;
		final double m = slope / scaling;
		final double[] coef = { c, m };
		final Calibration cal = imp.getCalibration();
		cal.setFunction(Calibration.STRAIGHT_LINE, coef, "mg HA/ccm");
		imp.setCalibration(cal);
		imp.updateAndDraw();
	}

}
