package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.bonej.ops.connectivity.EulerCharacteristic;
import org.bonej.ops.connectivity.EulerCorrection;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.ResultsInserter;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * A wrapper UI class for the Connectivity Ops
 *
 * @author Richard Domander 
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Connectivity", headless = true)
public class ConnectivityWrapper extends ContextCommand {
    public static final String NEGATIVE_CONNECTIVITY =
            "Connectivity is negative.\nThis usually happens if there are multiple particles or enclosed cavities.\n" +
                    "Try running Purify prior to Connectivity.\n";

    @Parameter(initializer = "initializeImage")
    private ImgPlus<UnsignedByteType> inputImage;

    @Parameter
    private OpService opService;

    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        final Img<BitType> bitImg = opService.convert().bit(inputImage);
        final int dimensions = inputImage.numDimensions();
        final ImgPlus<BitType> bitImgPlus = new ImgPlus<>(bitImg);
        for (int d = 0; d < dimensions; d++) {
            // Copy metadata
            final CalibratedAxis axis = inputImage.axis(d);
            bitImgPlus.setAxis(axis, d);
        }

        final double eulerCharacteristic = (Double) opService.run(EulerCharacteristic.class, bitImgPlus);
        final double edgeCorrection = (Double) opService.run(EulerCorrection.class, bitImgPlus);
        final double correctedEuler = eulerCharacteristic - edgeCorrection;
        final double connectivity = 1 - correctedEuler;
        final double connectivityDensity = calculateConnectivityDensity(connectivity);

        showResults(eulerCharacteristic, correctedEuler, connectivity, connectivityDensity);
    }

    //region -- Helper methods --
    private void showResults(final double eulerCharacteristic, final double deltaEuler, final double connectivity,
            final double connectivityDensity) {
        final String label = inputImage.getName();
        final String unitHeader = WrapperUtils.getUnitHeader(inputImage, "³");

        if (connectivity < 0) {
            uiService.showDialog(NEGATIVE_CONNECTIVITY, INFORMATION_MESSAGE);
        }

        if (unitHeader.isEmpty()) {
            uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
        }

        final ResultsInserter inserter = ResultsInserter.getInstance();
        inserter.setMeasurementInFirstFreeRow(label, "Euler char. (χ)", eulerCharacteristic);
        inserter.setMeasurementInFirstFreeRow(label, "Corrected Euler (Δχ)", deltaEuler);
        inserter.setMeasurementInFirstFreeRow(label, "Connectivity", connectivity);
        inserter.setMeasurementInFirstFreeRow(label, "Conn. density " + unitHeader, connectivityDensity);
        inserter.updateResults();
    }

    private double calculateConnectivityDensity(final double connectivity) {
        final double elements = AxisUtils.spatialSpaceSize(inputImage);
        final double elementSize = AxisUtils.calibratedSpatialElementSize(inputImage);
        return connectivity / (elements * elementSize);
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        if (AxisUtils.countSpatialDimensions(inputImage) < 3) {
            cancel(NOT_3D_IMAGE);
            return;
        }

        if (!ElementUtil.isColorsBinary(inputImage)) {
            cancel(NOT_BINARY);
        }
    }
    //endregion
}
