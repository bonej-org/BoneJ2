package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import org.bonej.ops.connectivity.DeltaEuler;
import org.bonej.ops.connectivity.EulerCharacteristic;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static org.bonej.wrapperPlugins.CommonMessages.*;

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
        }

        if (!ElementUtil.isColorsBinary(inputImage)) {
            cancel(NOT_BINARY);
        }

        //TODO check for non-linear axis
    }
    //endregion
}
