package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.units.UnitService;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import org.bonej.ops.thresholdFraction.Thresholds;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils.SpatialView;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.util.List;

import static org.bonej.wrapperPlugins.CommonMessages.BAD_CALIBRATION;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * A wrapper command to calculate mesh surface area
 *
 * @author Richard Domander 
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Isosurface", headless = true)
public class IsosurfaceWrapper<T extends RealType<T> & NativeType<T>> extends ContextCommand {
    @Parameter(initializer = "initializeImage")
    private ImgPlus<T> inputImage;

    @Parameter
    private OpService opService;

    @Parameter
    private UIService uiService;

    @Parameter
    private UnitService unitService;

    private boolean calibrationWarned = false;

    @Override
    public void run() {
        final String name = inputImage.getName();
        final Img<BitType> bitImg = opService.convert().bit(inputImage);
        final ImgPlus<BitType> bitImgPlus = new ImgPlus<>(bitImg);
        Common.copyMetadata(inputImage, bitImgPlus);
        final List<SpatialView> views = ViewUtils.createSpatialViews(bitImgPlus);

        for (SpatialView view : views) {
            final String label = name + view.hyperPosition;
            final double area = 0.0;
            showArea(label, area);
        }
    }

    private void showArea(final String label, final double area) {
        final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, '2');

        if (unitHeader.isEmpty() && !calibrationWarned) {
            uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
            calibrationWarned = true;
        }
    }

    @SuppressWarnings("unused")
    private void initializeImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }

        if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
            cancel(NOT_3D_IMAGE);
        }

        if (!ElementUtil.isColorsBinary(inputImage)) {
            cancel(NOT_BINARY);
        }
    }
}
