package org.bonej.wrapperPlugins;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.bonej.utilities.Streamers.spatialAxisStream;
import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import com.google.common.base.Strings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.geom.geom3d.mesh.Facet;
import net.imagej.ops.geom.geom3d.mesh.Mesh;
import net.imagej.ops.geom.geom3d.mesh.TriangularFacet;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.ResultsInserter;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils;
import org.bonej.wrapperPlugins.wrapperUtils.ViewUtils.SpatialView;
import org.jetbrains.annotations.Nullable;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

/**
 * A wrapper command to calculate mesh surface area
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Isosurface", headless = true)
public class IsosurfaceWrapper<T extends RealType<T> & NativeType<T>> extends ContextCommand {
    public static final String STL_WRITE_ERROR = "Failed to write the following STL files:\n\n";
    public static final String STL_HEADER = Strings.padEnd("Binary STL created by BoneJ", 80, '.');
    public static final String BAD_SCALING = "Cannot scale result because axis calibrations don't match";

    @Parameter(validater = "validateImage")
    private ImgPlus<T> inputImage;

    @Parameter(label = "Export STL file(s)", description = "Create a binary STL file from the surface mesh",
            required = false)
    private boolean exportSTL;

    @Parameter(label = "Help", description = "Open help web page", callback = "openHelpPage")
    private Button helpButton;

    @Parameter
    private LogService logService;

    @Parameter
    private OpService ops;

    @Parameter
    private PlatformService platformService;

    @Parameter
    private UIService uiService;

    @Parameter
    private UnitService unitService;

    private boolean calibrationWarned = false;
    private boolean scalingWarned = false;
    private UnaryFunctionOp<RandomAccessibleInterval, Mesh> marchingCubesOp;

    @Override
    public void run() {
        final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(ops, inputImage);
        final List<SpatialView<BitType>> views = ViewUtils.createSpatialViews(bitImgPlus);
        matchOps(views.get(0).view);
        processViews(views);
    }

    // -- Helper methods --
    private static void writeSTLFacet(ByteBuffer buffer, TriangularFacet facet) {
        writeSTLVector(buffer, facet.getNormal());
        writeSTLVector(buffer, facet.getP0());
        writeSTLVector(buffer, facet.getP1());
        writeSTLVector(buffer, facet.getP2());
        buffer.putShort((short) 0); // Attribute byte count
    }

    private static void writeSTLVector(ByteBuffer buffer, Vector3D v) {
        buffer.putFloat((float) v.getX());
        buffer.putFloat((float) v.getY());
        buffer.putFloat((float) v.getZ());
    }

    private void matchOps(final RandomAccessibleInterval<BitType> matchingView) {
        //TODO match with suitable mocked "NilTypes" (conforms == true)
        marchingCubesOp = Functions.unary(ops, Ops.Geometric.MarchingCubes.class, Mesh.class, matchingView);
    }

    private void processViews(List<SpatialView<BitType>> views) {
        final String name = inputImage.getName();
        final Map<String, String> failedMeshes = new HashMap<>();

        String path = "";
        String extension = "";
        if (exportSTL) {
            path = choosePath();
            if (path == null) {
                return;
            }

            final String fileName = path.substring(path.lastIndexOf(File.separator) + 1);
            final int dot = fileName.lastIndexOf(".");
            if (dot >= 0) {
                extension = fileName.substring(dot);
                //TODO add check for bad extension when DialogPrompt YES/NO options work correctly
                path = path.substring(0, path.length() - extension.length());
            } else {
                extension = ".stl";
            }
        }

        for (SpatialView<?> view : views) {
            final String label = name + view.hyperPosition;
            final Mesh mesh = marchingCubesOp.calculate(view.view);
            final double area = mesh.getSurfaceArea();
            showArea(label, area);

            if (exportSTL) {
                try {
                    writeBinarySTLFile(path + view.hyperPosition + extension, mesh);
                } catch (Exception e) {
                    failedMeshes.put(label, e.getMessage());
                }
            }
        }

        if (failedMeshes.size() > 0) {
            StringBuilder msgBuilder = new StringBuilder(STL_WRITE_ERROR);
            failedMeshes.forEach((k, v) -> msgBuilder.append(k).append(": ").append(v));
            uiService.showDialog(msgBuilder.toString(), ERROR_MESSAGE);
        }
    }

    @Nullable
    private String choosePath() {
        String initialName = stripFileExtension(inputImage.getName());

        // The file dialog won't allow empty filenames, and it prompts when file already exists
        File file = uiService.chooseFile(new File(initialName), FileWidget.SAVE_STYLE);
        if (file == null) {
            // User pressed cancel on file dialog
            return null;
        }

        return file.getAbsolutePath();
    }

    //TODO make into a utility method
    private static String stripFileExtension(String path) {
        final int dot = path.lastIndexOf('.');

        return dot == -1 ? path : path.substring(0, dot);
    }

    private void showArea(final String label, double area) {
        final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, '²');

        if (unitHeader.isEmpty() && !calibrationWarned) {
            uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
            calibrationWarned = true;
        }

        if (!isAxesMatchingSpatialCalibration(inputImage) && !scalingWarned) {
            uiService.showDialog(BAD_SCALING, WARNING_MESSAGE);
            scalingWarned = true;
        } else {
            final double scale = inputImage.axis(0).averageScale(0.0, 1.0);
            final double areaCalibration = scale * scale;
            area *= areaCalibration;
        }

        final ResultsInserter resultsInserter = ResultsInserter.getInstance();
        resultsInserter.setMeasurementInFirstFreeRow(label, "Surface area " + unitHeader, area);
        resultsInserter.updateResults();
    }

    @SuppressWarnings("unused")
    private void validateImage() {
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

    @SuppressWarnings("unused")
    private void openHelpPage() {
        Help.openHelpPage("http://bonej.org/isosurface", platformService, uiService, logService);
    }

    // -- Utility methods --

    /**
	 * Writes the surface mesh as a binary, little endian STL file
	 * <p>
	 * <p>
	 * NB: Public and static for testing purposes
	 * </p>
	 *
	 * @param path The absolute path to the save location of the STL file
	 * @param mesh A mesh consisting of triangular facets
	 */
    // TODO: Remove when SciJava PR goes through
    public static void writeBinarySTLFile(final String path, final Mesh mesh)
            throws IllegalArgumentException, IOException, NullPointerException {
        checkNotNull(mesh, "Mesh cannot be null");
        checkArgument(!Strings.isNullOrEmpty(path), "Filename cannot be null or empty");

        final List<Facet> facets = mesh.getFacets();
        final int numFacets = facets.size();

        checkArgument(mesh.triangularFacets(), "Cannot write STL file: invalid surface mesh");

        try (FileOutputStream writer = new FileOutputStream(path)) {
            final byte[] header = STL_HEADER.getBytes();
            writer.write(header);

            byte[] facetBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(numFacets).array();
            writer.write(facetBytes);

            final ByteBuffer buffer = ByteBuffer.allocate(50);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (Facet facet : facets) {
                final TriangularFacet triangularFacet = (TriangularFacet) facet;
                writeSTLFacet(buffer, triangularFacet);
                writer.write(buffer.array());
                buffer.clear();
            }
        }
    }

    /**
     * Check if all the spatial axes have a matching calibration, e.g. same unit, same scaling
     * <p>
     * NB: Public and static for testing purposes
     * </p>
     */
    // TODO make into a utility method or remove if mesh area considers calibration in the future
    public static <T extends AnnotatedSpace<CalibratedAxis>> boolean isAxesMatchingSpatialCalibration(T space) {
        final boolean noUnits = spatialAxisStream(space).map(CalibratedAxis::unit).allMatch(Strings::isNullOrEmpty);
        final boolean matchingUnit = spatialAxisStream(space).map(CalibratedAxis::unit).distinct().count() == 1;
        final boolean matchingScale = spatialAxisStream(space).map(a -> a.averageScale(0, 1)).distinct().count() == 1;

        return (matchingUnit || noUnits) && matchingScale;
    }
}
