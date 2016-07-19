package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.units.UnitService;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.bonej.ops.thresholdFraction.ElementFraction;
import org.bonej.ops.thresholdFraction.ElementFraction.Results;
import org.bonej.ops.thresholdFraction.ElementFraction.Settings;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.ResultsInserter;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * A wrapper UI class for the ElementFraction Op
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Fraction>Area/Volume fraction", headless = true)
public class ElementFractionWrapper<T extends RealType<T> & NativeType<T>> extends ContextCommand {
    @Parameter(initializer = "initializeImage")
    private ImgPlus<T> inputImage;

    @Parameter
    private OpService opService;

    @Parameter
    private UIService uiService;

    @Parameter
    private UnitService unitService;

    @Override
    public void run() {
        final T element = inputImage.cursor().get();
        final T minThreshold = element.createVariable();
        final T maxThreshold = element.createVariable();
        minThreshold.setReal(127);
        maxThreshold.setReal(255);
        final Settings<T> settings = new Settings<>(minThreshold, maxThreshold);

        final Results results = (Results) opService.run(ElementFraction.class, inputImage, settings);

        showResults(results);
    }

    private void showResults(final Results results) {
        final String label = inputImage.getName();
        final String sizeDescription = ResultUtils.getSizeDescription(inputImage);
        final char exponent = ResultUtils.getExponent(inputImage);
        final double elementSize = ElementUtil.calibratedSpatialElementSize(inputImage, unitService);
        final double thresholdSize = results.thresholdElements * elementSize;
        final double totalSize = results.elements * elementSize;
        final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, exponent);

        if (unitHeader.isEmpty()) {
            uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
        }

        final ResultsInserter resultsInserter = ResultsInserter.getInstance();
        resultsInserter
                .setMeasurementInFirstFreeRow(label, "Bone " + sizeDescription + " " + unitHeader, thresholdSize);
        resultsInserter.setMeasurementInFirstFreeRow(label, "Total " + sizeDescription + " " + unitHeader, totalSize);
        resultsInserter.setMeasurementInFirstFreeRow(label, sizeDescription + " Ratio", results.ratio);
        resultsInserter.updateResults();
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        if (!ElementUtil.isColorsBinary(inputImage)) {
            cancel(NOT_BINARY);
        }
    }
}
