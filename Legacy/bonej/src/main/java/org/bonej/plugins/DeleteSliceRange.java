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

package org.bonej.plugins;

import org.bonej.util.ImageCheck;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class DeleteSliceRange implements PlugIn {

	@Override
	public void run(final String arg) {
		if (!ImageCheck.checkEnvironment()) return;
		final ImagePlus imp = IJ.getImage();
		if (null == imp) {
			IJ.noImage();
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
		}
		if (last > imp.getStackSize()) {
			IJ.error("Last slice cannot be greater than the number of slices.");
			return;
		}
		if (first > last) {
			IJ.error("First slice cannot be after last slice");
			return;
		}

		final ImageStack stack = imp.getStack();
		deleteSliceRange(stack, first, last);
		imp.setStack(null, stack);

		imp.show();
		UsageReporter.reportEvent(this).send();
	}

	/**
	 * Delete a range of slices from a stack
	 *
	 * @param stack an image stack.
	 * @param first the first slice to remove
	 * @param last the last slice to remove
	 */
	private void deleteSliceRange(final ImageStack stack, final int first,
		final int last)
	{
		for (int s = first; s <= last; s++) {
			stack.deleteSlice(first);
		}
	}
}
