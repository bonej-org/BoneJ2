package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.geom.geom3d.mesh.Mesh;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import org.bonej.ops.thresholdFraction.SurfaceMask;
import org.bonej.ops.thresholdFraction.Thresholds;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.ResultsInserter;
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

import static org.bonej.wrapperPlugins.CommonMessages.*;
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
    private OpService ops;

    @Parameter
    private UIService uiService;

    @Parameter
    private UnitService unitService;

    private boolean calibrationWarned = false;
    private BinaryFunctionOp<RandomAccessibleInterval, Thresholds, RandomAccessibleInterval> maskOp;
    private UnaryFunctionOp<RandomAccessibleInterval, Mesh> marchingCubesOp;

    @Override
    public void run() {
        //TODO create Common.toBitTypeWithMetaData(ImgPlus<T>)
        final Img<BitType> bitImg = ops.convert().bit(inputImage);
        final ImgPlus<BitType> bitImgPlus = new ImgPlus<>(bitImg);
        Common.copyMetadata(inputImage, bitImgPlus);

        final List<SpatialView<BitType>> views = ViewUtils.createSpatialViews(bitImgPlus);
        matchOps(views.get(0).view);
        processViews(views);
    }

    // -- Helper methods --
    private void matchOps(final RandomAccessibleInterval<BitType> matchingView) {
        //TODO match with suitable mocked "NilTypes" (conforms == true)
        final Thresholds<BitType> thresholds = new Thresholds<>(matchingView, 1, 1);
        maskOp = Functions.binary(ops, SurfaceMask.class, RandomAccessibleInterval.class, matchingView, thresholds);
        marchingCubesOp = Functions.unary(ops, Ops.Geometric.MarchingCubes.class, Mesh.class, matchingView);
    }

    private void processViews(List<SpatialView<BitType>> views) {
        final String name = inputImage.getName();

        for (SpatialView<?> view : views) {
            final String label = name + view.hyperPosition;
            final RandomAccessibleInterval mask = maskOp.compute1(view.view);
            final Mesh mesh = marchingCubesOp.compute1(mask);
            final double area = mesh.getSurfaceArea();
            showArea(label, area);
        }
    }

    private void showArea(final String label, final double area) {
        final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, '²');

        if (unitHeader.isEmpty() && !calibrationWarned) {
            uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
            calibrationWarned = true;
        }

        final ResultsInserter resultsInserter = ResultsInserter.getInstance();
        resultsInserter.setMeasurementInFirstFreeRow(label, "Surface area " + unitHeader, area);
        resultsInserter.updateResults();
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
