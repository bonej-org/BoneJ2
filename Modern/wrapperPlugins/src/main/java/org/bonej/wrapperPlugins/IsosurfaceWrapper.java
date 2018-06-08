
package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.CommonMessages.BAD_CALIBRATION;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops.Geometric.MarchingCubes;
import net.imagej.ops.geom.geom3d.mesh.Facet;
import net.imagej.ops.geom.geom3d.mesh.Mesh;
import net.imagej.ops.geom.geom3d.mesh.TriangularFacet;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.StringUtils;
import org.scijava.widget.FileWidget;

/**
 * A wrapper command to calculate mesh surface area
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Surface area")
public class IsosurfaceWrapper<T extends RealType<T> & NativeType<T>> extends
	ContextCommand
{

	static final String STL_WRITE_ERROR =
		"Failed to write the following STL files:\n\n";
	static final String STL_HEADER = StringUtils.padEnd(
		"Binary STL created by BoneJ", 80, '.');
	static final String BAD_SCALING =
		"Cannot scale result because axis calibrations don't match";

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	/**
	 * The surface area results in a {@link Table}
	 * <p>
	 * Null if there are no results
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<String>, String> resultsTable;

	@Parameter(label = "Export STL file(s)",
		description = "Create a binary STL file from the surface mesh",
		required = false)
	private boolean exportSTL;

	@Parameter
	private OpService ops;

	@Parameter
	private UIService uiService;

	@Parameter
	private UnitService unitService;

	@Parameter
	private StatusService statusService;

	private String path = "";
	private String extension = "";
	private UnaryFunctionOp<RandomAccessibleInterval, Mesh> marchingCubesOp;
	private double areaScale;
	private String unitHeader = "";

	@Override
	public void run() {
		statusService.showStatus("Surface area: initialising");
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(ops,
			inputImage);
		final List<Subspace<BitType>> subspaces = HyperstackUtils.split3DSubspaces(
			bitImgPlus).collect(Collectors.toList());
		matchOps(subspaces.get(0).interval);
		prepareResults();
		statusService.showStatus("Surface area: creating meshes");
		final Map<String, Mesh> meshes = processViews(subspaces);
		if (exportSTL) {
			getFileName();
			statusService.showStatus("Surface area: saving files");
			saveMeshes(meshes);
		}
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	private void addResult(final String label, final double area) {
		SharedTable.add(label, "Surface area " + unitHeader, area * areaScale);
	}

	private String choosePath() {
		final String initialName = stripFileExtension(inputImage.getName());

		// The file dialog won't allow empty filenames, and it prompts when file
		// already exists
		final File file = uiService.chooseFile(new File(initialName),
			FileWidget.SAVE_STYLE);
		if (file == null) {
			// User pressed cancel on file dialog
			return null;
		}

		return file.getAbsolutePath();
	}

	private void getFileName() {
		path = choosePath();
		if (path == null) {
			return;
		}

		final String fileName = path.substring(path.lastIndexOf(File.separator) +
			1);
		final int dot = fileName.lastIndexOf(".");
		if (dot >= 0) {
			extension = fileName.substring(dot);
			// TODO Verify extension if not .stl, when DialogPrompt YES/NO options
			// work correctly
			path = stripFileExtension(path);
		}
		else {
			extension = ".stl";
		}
	}

	private void matchOps(final RandomAccessibleInterval<BitType> interval) {
		marchingCubesOp = Functions.unary(ops, MarchingCubes.class, Mesh.class,
			interval);
	}

	private void prepareResults() {
		unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, '²');
		if (unitHeader.isEmpty()) {
			uiService.showDialog(BAD_CALIBRATION, WARNING_MESSAGE);
		}

		if (AxisUtils.isAxesMatchingSpatialCalibration(inputImage)) {
			final double scale = inputImage.axis(0).averageScale(0.0, 1.0);
			areaScale = scale * scale;
		}
		else {
			uiService.showDialog(BAD_SCALING, WARNING_MESSAGE);
			areaScale = 1.0;
		}
	}

	private Map<String, Mesh> processViews(
		final Iterable<Subspace<BitType>> subspaces)
	{
		final String name = inputImage.getName();
		final Map<String, Mesh> meshes = new HashMap<>();
		for (final Subspace<BitType> subspace : subspaces) {
			final Mesh mesh = marchingCubesOp.calculate(subspace.interval);
			final double area = mesh.getSurfaceArea();
			final String suffix = subspace.toString();
			final String label = suffix.isEmpty() ? name : name + " " + suffix;
			addResult(label, area);
			meshes.put(subspace.toString(), mesh);
		}
		return meshes;
	}

	private void saveMeshes(final Map<String, Mesh> meshes) {
		final Map<String, String> savingErrors = new HashMap<>();
		meshes.forEach((key, subspaceMesh) -> {
			final String subspaceId = key.replace(' ', '_');
			final String filePath = path + "_" + subspaceId + extension;
			try {
				writeBinarySTLFile(filePath, subspaceMesh);
			}
			catch (final IOException e) {
				savingErrors.put(filePath, e.getMessage());
			}
		});
		if (!savingErrors.isEmpty()) {
			showSavingErrorsDialog(savingErrors);
		}
	}

	private void showSavingErrorsDialog(final Map<String, String> savingErrors) {
		final StringBuilder msgBuilder = new StringBuilder(STL_WRITE_ERROR);
		savingErrors.forEach((k, v) -> msgBuilder.append(k).append(": ").append(v));
		uiService.showDialog(msgBuilder.toString(), ERROR_MESSAGE);
	}

	// TODO make into a utility method
	private static String stripFileExtension(final String path) {
		final int dot = path.lastIndexOf('.');

		return dot == -1 ? path : path.substring(0, dot);
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

	/**
	 * Writes the surface mesh as a binary, little endian STL file
	 * <p>
	 * NB: Public and static for testing purposes
	 * </p>
	 *
	 * @param path The absolute path to the save location of the STL file
	 * @param mesh A mesh consisting of triangular facets
	 * @throws NullPointerException if mesh is null
	 * @throws IllegalArgumentException if path is null or empty, or mesh doesn't
	 *           have triangular facets
	 * @throws IOException if there's an error while writing the file
	 */
	// TODO: Remove when imagej-mesh / ThreeDViewer supports STL
	static void writeBinarySTLFile(final String path, final Mesh mesh)
		throws IllegalArgumentException, IOException, NullPointerException
	{
		if (mesh == null) {
			throw new NullPointerException("Mesh cannot be null");
		}

		if (StringUtils.isNullOrEmpty(path)) {
			throw new IllegalArgumentException("Filename cannot be null or empty");
		}
		if (!mesh.triangularFacets()) {
			throw new IllegalArgumentException(
				"Cannot write STL file: invalid surface mesh");
		}

		final List<Facet> facets = mesh.getFacets();
		final int numFacets = facets.size();
		try (final FileOutputStream writer = new FileOutputStream(path)) {
			final byte[] header = STL_HEADER.getBytes();
			writer.write(header);
			final byte[] facetBytes = ByteBuffer.allocate(4).order(
				ByteOrder.LITTLE_ENDIAN).putInt(numFacets).array();
			writer.write(facetBytes);
			final ByteBuffer buffer = ByteBuffer.allocate(50);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			for (final Facet facet : facets) {
				final TriangularFacet triangularFacet = (TriangularFacet) facet;
				writeSTLFacet(buffer, triangularFacet);
				writer.write(buffer.array());
				buffer.clear();
			}
		}
	}

	// -- Utility methods --

	// -- Helper methods --
	private static void writeSTLFacet(final ByteBuffer buffer,
		final TriangularFacet facet)
	{
		writeSTLVector(buffer, facet.getNormal());
		writeSTLVector(buffer, facet.getP0());
		writeSTLVector(buffer, facet.getP1());
		writeSTLVector(buffer, facet.getP2());
		buffer.putShort((short) 0); // Attribute byte count
	}

	private static void writeSTLVector(final ByteBuffer buffer,
		final Vector3D v)
	{
		buffer.putFloat((float) v.getX());
		buffer.putFloat((float) v.getY());
		buffer.putFloat((float) v.getZ());
	}
}
