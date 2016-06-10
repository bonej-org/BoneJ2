package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import org.bonej.ops.connectivity.DeltaEuler;
import org.bonej.ops.connectivity.EulerCharacteristic;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.ResultsInserter;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * A wrapper UI class for the Connectivity Ops
 *
 * @author Richard Domander 
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Connectivity")
public class ConnectivityWrapper extends ContextCommand {
    @Parameter(initializer = "initializeImage")
    private ImgPlus inputImage;

    @Parameter
    private OpService opService;

    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        final Integer eulerCharacteristic = (Integer) opService.run(EulerCharacteristic.class, inputImage);
        final Integer deltaEuler = (Integer) opService.run(DeltaEuler.class, inputImage);
        final int connectivity = 1 - deltaEuler;
        final double connectivityDensity = calculateConnectivityDensity(connectivity);

        showResults(eulerCharacteristic, deltaEuler, connectivity, connectivityDensity);
    }

    //region -- Helper methods --
    private void showResults(final Integer eulerCharacteristic, final Integer deltaEuler, final int connectivity,
            final double connectivityDensity) {
        final String label = inputImage.getName();
        final String unitHeader = WrapperUtils.getUnitHeader(inputImage, "³");

        if (unitHeader.isEmpty()) {
            uiService.showDialog("Calibration has no unit: showing plain values", WARNING_MESSAGE);
        }

        final ResultsInserter inserter = new ResultsInserter();
        inserter.setMeasurementInFirstFreeRow(label, "Euler char. (χ)", eulerCharacteristic);
        inserter.setMeasurementInFirstFreeRow(label, "Contribution (Δχ)", deltaEuler);
        inserter.setMeasurementInFirstFreeRow(label, "Connectivity", connectivity);
        inserter.setMeasurementInFirstFreeRow(label, "Conn. density " + unitHeader, connectivityDensity);
        inserter.updateResults();
    }

    private double calculateConnectivityDensity(final int connectivity) {
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

        //TODO check for non-linear axis
    }
    //endregion
}
