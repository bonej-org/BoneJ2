package org.bonej.wrapperPlugins;

import com.google.common.base.Strings;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.ops.geom.geom3d.mesh.Facet;
import net.imagej.ops.geom.geom3d.mesh.Mesh;
import net.imagej.ops.geom.geom3d.mesh.TriangularFacet;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
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
import org.scijava.widget.FileWidget;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

/**
 * A wrapper command to calculate mesh surface area
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Isosurface", headless = true)
public class IsosurfaceWrapper<T extends RealType<T> & NativeType<T>> extends ContextCommand {
    public static final String STL_WRITE_WARNING = "Failed to write the following STL files:\n\n";
    public static final String STL_HEADER = Strings.padEnd("Binary STL created by BoneJ", 80, '.');

    @Parameter(initializer = "initializeImage")
    private ImgPlus<T> inputImage;

    @Parameter(label = "Export STL file", description = "Create a STL file from the surface mesh", required = false)
    private boolean exportSTL;

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
        final Thresholds<BitType> thresholds = new Thresholds<>(matchingView, 1, 1);
        maskOp = Functions.binary(ops, SurfaceMask.class, RandomAccessibleInterval.class, matchingView, thresholds);
        marchingCubesOp = Functions.unary(ops, Ops.Geometric.MarchingCubes.class, Mesh.class, matchingView);
    }

    private void processViews(List<SpatialView<BitType>> views) {
        final String name = inputImage.getName();
        final Map<String, String> failedMeshes = new HashMap<>();

        String path = "";
        if (exportSTL) {
            path = choosePath();
            if (path == null) {
                return;
            }
        }

        for (SpatialView<?> view : views) {
            final String label = name + view.hyperPosition;
            final RandomAccessibleInterval mask = maskOp.compute1(view.view);
            final Mesh mesh = marchingCubesOp.compute1(mask);
            final double area = mesh.getSurfaceArea();
            showArea(label, area);

            if (exportSTL) {
                try {
                    writeBinarySTLFile(path + view.hyperPosition, mesh);
                } catch (Exception e) {
                    failedMeshes.put(label, e.getMessage());
                }
            }
        }

        if (failedMeshes.size() > 0) {
            //TODO test when user can give filename (empty name)
            StringBuilder msgBuilder = new StringBuilder(STL_WRITE_WARNING);
            failedMeshes.forEach((k, v) -> msgBuilder.append(k).append(": ").append(v));
            uiService.showDialog(msgBuilder.toString(), ERROR_MESSAGE);
        }
    }

    private String choosePath() {
        String initialName = stripFileExtension(inputImage.getName());

        // The file dialog won't allow empty filenames, and it prompts when file already exists
        File file = uiService.chooseFile(new File(initialName), FileWidget.SAVE_STYLE);
        if (file == null) {
            // User pressed cancel on file dialog
            return null;
        }


        final String path = file.getAbsolutePath();

        // Add a file extension if needed
        return path.lastIndexOf('.') != -1 ? path : path + ".stl";
    }

    //TODO make into a utility method
    private static String stripFileExtension(String path) {
        final int dot = path.lastIndexOf('.');

        return dot == -1 ? path : path.substring(0, dot);
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

    // -- Utility methods --

    /**
     * Writes the surface mesh as a binary, little endian STL file
     *
     * <p>NB: Public and static for testing purposes</p>
     *
     * @param path  The absolute path to the save location of the STL file
     * @param mesh  A mesh consisting of triangular facets
     */
    // TODO: Create an IOPlugin to save STL files from Meshes
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
}
