package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.bonej.ops.connectivity.EulerCharacteristicFloating;
import org.bonej.ops.connectivity.EulerCorrection;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.ResultsInserter;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
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

    private boolean negativityWarned = false;
    private boolean calibrationWarned = false;


    @Override
    public void run() {
        final String name = inputImage.getName();
        final Img<BitType> bitImg = opService.convert().bit(inputImage);
        final int dimensions = inputImage.numDimensions();
        final ImgPlus<BitType> bitImgPlus = new ImgPlus<>(bitImg);

        // Copy ImgPlus metadata from original
        //TODO create testable util method
        bitImgPlus.setName(name);
        for (int d = 0; d < dimensions; d++) {
            final CalibratedAxis axis = inputImage.axis(d);
            bitImgPlus.setAxis(axis, d);
        }

        final int timeIndex = AxisUtils.getTimeIndex(bitImgPlus);
        int channelIndex = AxisUtils.getChannelIndex(bitImgPlus);

        if (timeIndex == -1 && channelIndex == -1) {
            // Not a hyperstack, just process the 3D image and exit
            processSubStack(name, bitImgPlus, "");
            return;
        }

        final long channels = channelIndex >= 0 ? bitImgPlus.dimension(channelIndex) : 0;
        final long frames = timeIndex >= 0 ? bitImgPlus.dimension(timeIndex) : 0;
        long frame = 0;

        if (channelIndex > timeIndex && timeIndex != -1) {
            // Channel index is one smaller once time dimension has been cut
            channelIndex--;
        }

        // Call connectivity for each 3D subspace in the colour/time hyperstack
        do {
            long channel = 0;
            // No need to add clarifying suffix is there's only one frame
            final String frameSuffix = frames > 1 ? "_F" + (frame + 1) : "";
            RandomAccessibleInterval timeView = safeHyperSlice(bitImgPlus, timeIndex, frame);
            do {
                // No need to add clarifying suffix is there's only one channel
                final String channelSuffix = channels > 1 ? "_C" + (channel + 1) : "";
                RandomAccessibleInterval channelView = safeHyperSlice(timeView, channelIndex, channel);
                processSubStack(name, channelView, frameSuffix + channelSuffix);
                channel++;
            } while (channel < channels);
            frame++;
        } while (frame < frames);
    }

    private RandomAccessibleInterval safeHyperSlice(RandomAccessibleInterval view, int dimension, long position) {
        if (dimension < 0) {
            return view;
        }

        return Views.hyperSlice(view, dimension, position);
    }

    //region -- Helper methods --
    private void processSubStack(final String name, final RandomAccessibleInterval view, final String suffix) {
        final double eulerCharacteristic = (Double) opService.run(EulerCharacteristicFloating.class, view);
        final double edgeCorrection = (Double) opService.run(EulerCorrection.class, view);
        final double correctedEuler = eulerCharacteristic - edgeCorrection;
        final double connectivity = 1 - correctedEuler;
        final double connectivityDensity = calculateConnectivityDensity(connectivity);
        final String label = name + suffix;

        showResults(label, eulerCharacteristic, correctedEuler, connectivity, connectivityDensity);
    }

    private void showResults(String label, final double eulerCharacteristic, final double deltaEuler,
                             final double connectivity, final double connectivityDensity) {
        final String unitHeader = ResultUtils.getUnitHeader(inputImage, '³');

        if (connectivity < 0 && !negativityWarned) {
            uiService.showDialog(NEGATIVE_CONNECTIVITY, INFORMATION_MESSAGE);
            negativityWarned = true;
        }

        if (unitHeader.isEmpty() && !calibrationWarned) {
            uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
            calibrationWarned = true;
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
