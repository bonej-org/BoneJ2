package org.bonej.menuWrappers;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.macro.Interpreter;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.StackStatistics;
import org.doube.util.ImageCheck;
import org.doube.util.ResultInserter;
import org.doube.util.RoiMan;
import org.doube.util.UsageReporter;
import sc.fiji.localThickness.LocalThicknessWrapper;

import java.awt.*;

/**
 * A wrapper plugin to add the LocalThickness plugin under Plugins>BoneJ menu path.
 *
 * Also shows a custom setup dialog that introduces new options,
 * and overrides the options in the original LocalThickness plugin.
 * Displays additional incompatibility warnings to the user.
 *
 * @author Michael Doube
 * @author Richard Domander
 * @todo Overwrite input or not?
 */
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
            final ImagePlus resultImage = getLocalThickness(inputImage, true);
            if(resultImage == null) {
                return;
            }

            final StackStatistics resultStats = new StackStatistics(resultImage);
            showResultImage(resultImage, resultStats);
            showThicknessStats(resultImage, resultStats, true);
        }

        if (doSpacing) {
            final ImagePlus resultImage = getLocalThickness(inputImage, false);
            if(resultImage == null) {
                return;
            }

            final StackStatistics resultStats = new StackStatistics(resultImage);
            showResultImage(resultImage, resultStats);
            showThicknessStats(resultImage, resultStats, false);
        }

        UsageReporter.reportEvent(this).send();
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

    private void showResultImage(final ImagePlus resultImage, final StackStatistics resultStats)
    {
        if (!doGraphic || Interpreter.isBatchMode()) {
            return;
        }

        resultImage.show();
        resultImage.getProcessor().setMinAndMax(0.0, resultStats.max);
        IJ.run("Fire");
    }

    /**
     * Calculate the local thickness measure with various user options from the setup dialog
     * (foreground/background thickness, crop image, show image...).
     *
     * @param doForeground  If true, then process the thickness of the foreground (trabecular thickness).
     *                      If false, then process the thickness of the background (trabecular spacing).
     * @return Returns true if localThickness succeeded, and resultImage != null
     */
    private ImagePlus getLocalThickness(final ImagePlus inputImage, final boolean doForeground) {
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
     * @param image         Binary (black & white) ImagePlus
     * @param doForeground  If true, then process the thickness of the foreground.
     *                      If false, then process the thickness of the background
     * @return  A new ImagePlus which contains the thickness
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

    public void showThicknessStats(final ImagePlus resultImage, final StackStatistics resultStats,
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
