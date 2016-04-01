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

import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

/**
 * Implements the stack cropping method from RoiMan as a plugin
 *
 * @author Michael Doube
 *
 */
public class StackCropper implements PlugIn {

	@Override
	public void run(final String arg) {
		final ImagePlus imp = WindowManager.getCurrentImage();
		final RoiManager roiMan = RoiManager.getInstance();
		if (imp == null || roiMan == null)
			return;

		final GenericDialog gd = new GenericDialog("Crop Stack by ROI");
		gd.addCheckbox("Replace Original", false);
		gd.addCheckbox("Fill outside", false);
		gd.addNumericField("Fill_value", 0, 0, 6, "");
		gd.addNumericField("Padding", 0, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		final boolean doReplace = gd.getNextBoolean();
		final boolean doFill = gd.getNextBoolean();
		final int fillValue = (int) gd.getNextNumber();
		final int padding = (int) gd.getNextNumber();
		final ImageStack stack = RoiMan.cropStack(roiMan, imp.getImageStack(), doFill, fillValue, padding);
		if (doReplace) {
			imp.setStack(stack);
			imp.show();
		} else {
			final ImagePlus out = new ImagePlus(imp.getTitle() + "-crop");
			out.setStack(stack);
			out.show();
		}
		UsageReporter.reportEvent(this).send();
	}
}
