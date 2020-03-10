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

package org.bonej.menuWrappers;

import org.bonej.util.ImageCheck;

import ij.IJ;
import ij.ImagePlus;
import sc.fiji.localThickness.LocalThicknessWrapper;

/**
 * A helper class that allows Legacy plugins to call sc.fiji_LocalThickness_
 * like they called Thickness in BoneJ1
 *
 * @author Michael Doube
 * @author Richard Domander
 * @deprecated Replaced by ThicknessWrapper in BoneJ2
 */
@Deprecated
public class LocalThickness {

	 /**
	 * Get a local thickness map from an ImagePlus with optional masking
	 * correction
	 *
	 * A convenience method for legacy code
	 *
	 * @param imp
	 *            Binary ImagePlus
	 * @param invert
	 *            false if you want the thickness of the foreground and true if
	 *            you want the thickness of the background
	 * @param doMask
	 *            true to apply a masking operation to enforce the map to
	 *            contain thickness values only at coordinates where there is a
	 *            corresponding input pixel
	 * @return 32-bit ImagePlus containing a local thickness map
	 */
	public static ImagePlus getLocalThickness(final ImagePlus imp, final boolean invert,
											  final boolean doMask) {
		if (!ImageCheck.isVoxelIsotropic(imp, 1E-3)) {
			IJ.log("Warning: voxels are anisotropic. Local thickness results will be inaccurate");
		}

		return processThicknessSteps(imp, !invert, doMask);
	}

	/**
	 * Get a local thickness map from an ImagePlus, without masking correction
	 *
	 * A convenience method for legacy code
	 *
	 * @see #getLocalThickness(ImagePlus, boolean, boolean)
	 * @param imp
	 *            Binary ImagePlus
	 * @param invert
	 *            false if you want the thickness of the foreground and true if
	 *            you want the thickness of the background
	 * @return 32-bit ImagePlus containing a local thickness map
	 */
	public static ImagePlus getLocalThickness(final ImagePlus imp, final boolean invert) {
		return getLocalThickness(imp, invert, false);
	}

	/**
	 * Process the given image through all the steps of LocalThickness_ plugin.
	 *
	 * @param image
	 *            Binary (black & white) ImagePlus
	 * @param doForeground
	 *            If true, then process the thickness of the foreground. If
	 *            false, then process the thickness of the background
	 * @param doMask true to apply a masking operation to enforce the map to
	 * 	             contain thickness values only at coordinates where there is a
	 * 	             corresponding input pixel
	 * @return A new ImagePlus which contains the thickness
	 */
	private static ImagePlus processThicknessSteps(final ImagePlus image,
												   final boolean doForeground, boolean doMask) {

		final LocalThicknessWrapper plugin = new LocalThicknessWrapper();
		plugin.setSilence(true);
		plugin.inverse = !doForeground;
		plugin.setShowOptions(false);
		plugin.maskThicknessMap = doMask;
		plugin.setTitleSuffix("");
		plugin.calibratePixels = true;
		return plugin.processImage(image);
	}
}
