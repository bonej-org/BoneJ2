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
import ij.ImageStack;
import ij.VirtualStack;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class DeleteSliceRange implements PlugIn {
	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment())
			return;
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			return;
		}
		final GenericDialog gd = new GenericDialog("Delete slices");
		gd.addMessage("Inclusive range of slices to delete");
		gd.addNumericField("First", imp.getCurrentSlice(), 0);
		gd.addNumericField("Last", imp.getCurrentSlice(), 0);
		gd.showDialog();
		if (gd.wasCanceled()) {
			return;
		}
		final int first = (int) Math.floor(gd.getNextNumber());
		final int last = (int) Math.floor(gd.getNextNumber());

		// check sanity of first and last values
		if (first < 1) {
			IJ.error("First slice cannot be less than 1.");
			return;
		} else if (last > imp.getStackSize()) {
			IJ.error("Last slice cannot be greater than the number of slices.");
			return;
		} else if (first > last) {
			IJ.error("First slice cannot be after last slice");
			return;
		}

		if (imp.getStack().isVirtual()) {
			final VirtualStack stack = (VirtualStack) imp.getStack();
			deleteSliceRange(stack, first, last);
			imp.setStack(null, stack);
		} else {
			final ImageStack stack = imp.getStack();
			deleteSliceRange(stack, first, last);
			imp.setStack(null, stack);
		}
		imp.show();
		UsageReporter.reportEvent(this).send();
	}

	/**
	 * Delete a range of slices from a stack
	 *
	 * @param stack
	 * @param first
	 *            the first slice to remove
	 * @param last
	 *            the last slice to remove
	 */
	public void deleteSliceRange(final ImageStack stack, final int first, final int last) {
		for (int s = first; s <= last; s++) {
			stack.deleteSlice(first);
		}
		return;
	}

	/**
	 * Delete a range of slices from a virtual stack
	 *
	 * @param stack
	 * @param first
	 *            the first slice to remove
	 * @param last
	 *            the last slice to remove
	 */
	public void deleteSliceRange(final VirtualStack stack, final int first, final int last) {
		for (int s = first; s <= last; s++) {
			stack.deleteSlice(first);
		}
		return;
	}
}
