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

import java.awt.Checkbox;

import org.bonej.plugins.UsageReporter;
import org.bonej.util.ImageCheck;
import org.bonej.util.ResultInserter;
import org.bonej.util.RoiMan;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.macro.Interpreter;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.StackStatistics;
import sc.fiji.localThickness.LocalThicknessWrapper;

/**
 * A wrapper plugin to add the LocalThickness plugin under BoneJ menu path.
 * <p>
 * Also shows a custom setup dialog that introduces new options, and overrides
 * the options in the original LocalThickness plugin. Displays additional
 * incompatibility warnings to the user.
 * </p>
 *
 * @author Michael Doube
 * @author Richard Domander
 * @deprecated Replaced by ThicknessWrapper in BoneJ2
 */
@Deprecated
public class LocalThickness implements PlugIn {
	private static final boolean THICKNESS_DEFAULT = true;
	private static final boolean SPACING_DEFAULT = false;
	private static final boolean GRAPHIC_DEFAULT = true;
	private static final boolean ROI_DEFAULT = false;
	private static final boolean MASK_DEFAULT = true;
	private static final String HELP_URL = "http://bonej.org/thickness";
	private static final String TRABECULAR_THICKNESS = "Tb.Th";
	private static final String TRABECULAR_SPACING = "Tb.Sp";

	private final LocalThicknessWrapper thickness = new LocalThicknessWrapper();
	private GenericDialog setupDialog = null;
	private RoiManager roiManager = null;
	private boolean doThickness = THICKNESS_DEFAULT;
	private boolean doSpacing = SPACING_DEFAULT;
	private boolean doGraphic = GRAPHIC_DEFAULT;
	private boolean doRoi = ROI_DEFAULT;
	private boolean doMask = MASK_DEFAULT;

	@Override
	public void run(String arg) {
		if (!ImageCheck.checkEnvironment()) {
			return;
		}

		final ImagePlus inputImage;

		try {
			inputImage = IJ.getImage();
		} catch (RuntimeException e) {
			// If no image is open, getImage() throws an exception
			return;
		}

		if (!ImageCheck.isBinary(inputImage)) {
			IJ.error("Local thickness requires an 8-bit greyscale binary image");
			return;
		}

		final double ANISOTROPY_TOLERANCE = 1E-3;
		if (!ImageCheck.isVoxelIsotropic(inputImage, ANISOTROPY_TOLERANCE)) {
			final boolean cancel = !IJ.showMessageWithCancel("Anisotropic voxels",
					"This image contains anisotropic voxels, which will\n"
							+ "result in incorrect thickness calculation.\n\n"
							+ "Consider rescaling your data so that voxels are isotropic\n" + "(Image > Scale...).\n\n"
							+ "Continue anyway?");
			if (cancel) {
				return;
			}
		}

		createSetupDialog();
		setupDialog.showDialog();
		if (setupDialog.wasCanceled()) {
			return;
		}
		getSettingsFromDialog();

		if (!doThickness && !doSpacing) {
			IJ.showMessage("Nothing to process, shutting down plugin.");
			return;
		}

		if (doThickness) {
			final ImagePlus resultImage = calculateLocalThickness(inputImage, true);
			if (resultImage == null) {
				return;
			}

			final StackStatistics resultStats = new StackStatistics(resultImage);
			showResultImage(resultImage, resultStats);
			showThicknessStats(resultImage, resultStats, true);
		}

		if (doSpacing) {
			final ImagePlus resultImage = calculateLocalThickness(inputImage, false);
			if (resultImage == null) {
				return;
			}

			final StackStatistics resultStats = new StackStatistics(resultImage);
			showResultImage(resultImage, resultStats);
			showThicknessStats(resultImage, resultStats, false);
		}

		UsageReporter.reportEvent(this).send();
	}

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
	public ImagePlus getLocalThickness(final ImagePlus imp, final boolean invert, final boolean doMask) {
		if (!ImageCheck.isVoxelIsotropic(imp, 1E-3)) {
			IJ.log("Warning: voxels are anisotropic. Local thickness results will be inaccurate");
		}

		this.doMask = doMask;

		return processThicknessSteps(imp, !invert, "");
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
	public ImagePlus getLocalThickness(final ImagePlus imp, final boolean invert) {
		return getLocalThickness(imp, invert, false);
	}

	private void createSetupDialog() {
		setupDialog = new GenericDialog("Plugin options");
		setupDialog.addCheckbox("Thickness", doThickness);
		setupDialog.addCheckbox("Spacing", doSpacing);
		setupDialog.addCheckbox("Graphic Result", doGraphic);

		setupDialog.addCheckbox("Crop using ROI manager", doRoi);
		if (roiManager == null) {
			// Disable option if there's no ROI manager
			Checkbox cropCheckbox = (Checkbox) setupDialog.getCheckboxes().elementAt(3);
			cropCheckbox.setState(false);
			cropCheckbox.setEnabled(false);
		}

		setupDialog.addCheckbox("Mask thickness map", doMask);
		setupDialog.addHelp(HELP_URL);
	}

	private void getSettingsFromDialog() {
		doThickness = setupDialog.getNextBoolean();
		doSpacing = setupDialog.getNextBoolean();
		doGraphic = setupDialog.getNextBoolean();
		doRoi = setupDialog.getNextBoolean();
		doMask = setupDialog.getNextBoolean();
	}

	private void showResultImage(final ImagePlus resultImage, final StackStatistics resultStats) {
		if (!doGraphic || Interpreter.isBatchMode()) {
			return;
		}

		resultImage.show();
		resultImage.getProcessor().setMinAndMax(0.0, resultStats.max);
		IJ.run("Fire");
	}

	/**
	 * Calculate the local thickness measure with various user options from the
	 * setup dialog (foreground/background thickness, crop image, show
	 * image...).
	 *
	 * @param doForeground
	 *            If true, then process the thickness of the foreground
	 *            (trabecular thickness). If false, then process the thickness
	 *            of the background (trabecular spacing).
	 * @return Returns true if localThickness succeeded, and resultImage != null
	 */
	private ImagePlus calculateLocalThickness(final ImagePlus inputImage, final boolean doForeground) {
		String suffix = doForeground ? "_" + TRABECULAR_THICKNESS : "_" + TRABECULAR_SPACING;

		if (doRoi) {
			ImageStack croppedStack = RoiMan.cropStack(roiManager, inputImage.getStack(), true, 0, 0);

			if (croppedStack == null) {
				IJ.error("There are no valid ROIs in the ROI Manager for cropping");
				return null;
			}

			ImagePlus croppedImage = new ImagePlus("", croppedStack);
			croppedImage.copyScale(inputImage);
			return processThicknessSteps(croppedImage, doForeground, suffix);
		}

		return processThicknessSteps(inputImage, doForeground, suffix);
	}

	/**
	 * Process the given image through all the steps of LocalThickness_ plugin.
	 *
	 * @param image
	 *            Binary (black & white) ImagePlus
	 * @param doForeground
	 *            If true, then process the thickness of the foreground. If
	 *            false, then process the thickness of the background
	 * @return A new ImagePlus which contains the thickness
	 */
	private ImagePlus processThicknessSteps(final ImagePlus image, final boolean doForeground,
			final String tittleSuffix) {
		thickness.setSilence(true);
		thickness.inverse = !doForeground;
		thickness.setShowOptions(false);
		thickness.maskThicknessMap = doMask;
		thickness.setTitleSuffix(tittleSuffix);
		thickness.calibratePixels = true;
		return thickness.processImage(image);
	}

	private void showThicknessStats(final ImagePlus resultImage, final StackStatistics resultStats,
			final boolean doForeground) {
		String units = resultImage.getCalibration().getUnits();
		String legend = doForeground ? TRABECULAR_THICKNESS : TRABECULAR_SPACING;

		ResultInserter resultInserter = ResultInserter.getInstance();
		resultInserter.setResultInRow(resultImage, legend + " Mean (" + units + ")", resultStats.mean);
		resultInserter.setResultInRow(resultImage, legend + " Std Dev (" + units + ")", resultStats.stdDev);
		resultInserter.setResultInRow(resultImage, legend + " Max (" + units + ")", resultStats.max);
		resultInserter.updateTable();
	}
}
