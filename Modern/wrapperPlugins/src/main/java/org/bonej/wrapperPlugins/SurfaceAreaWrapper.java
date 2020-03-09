/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.wrapperPlugins;

import static org.bonej.utilities.Streamers.spatialAxisStream;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.mesh.Mesh;
import net.imagej.mesh.Triangle;
import net.imagej.mesh.naive.NaiveFloatMesh;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops.Geometric.BoundarySize;
import net.imagej.ops.Ops.Geometric.MarchingCubes;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.space.AnnotatedSpace;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.bonej.wrapperPlugins.wrapperUtils.UsageReporter;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginService;
import org.scijava.prefs.PrefService;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;
import org.scijava.ui.UIService;
import org.scijava.util.StringUtils;
import org.scijava.widget.FileWidget;

/**
 * A wrapper command to calculate mesh surface area
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Surface area")
public class SurfaceAreaWrapper<T extends RealType<T> & NativeType<T>> extends
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
	private Table<DefaultColumn<Double>, Double> resultsTable;

	@Parameter(label = "Export STL file(s)",
		description = "Create a binary STL file from the surface mesh",
		required = false)
	private boolean exportSTL;

	@Parameter
	private OpService ops;
	@Parameter
	private LogService logService;
	@Parameter
	private UIService uiService;
	@Parameter
	private UnitService unitService;
	@Parameter
	private StatusService statusService;
	@Parameter
	private PrefService prefs;
	@Parameter
	private PluginService pluginService;
	@Parameter
	private CommandService commandService;

	private String path = "";
	private String extension = "";
	private UnaryFunctionOp<RandomAccessibleInterval, Mesh> marchingCubesOp;
	private UnaryFunctionOp<Mesh, DoubleType> areaOp;
	private double areaScale;
	private String unitHeader = "";
	private static UsageReporter reporter;

	@Override
	public void run() {
		statusService.showStatus("Surface area: initialising");
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(ops,
			inputImage);
		final List<Subspace<BitType>> subspaces = HyperstackUtils.split3DSubspaces(
			bitImgPlus).collect(Collectors.toList());
		matchOps(subspaces.get(0).interval);
		prepareResults();
		final Map<String, Mesh> meshes = createMeshes(subspaces);
		if (exportSTL) {
			if (!getFileName()) {
				return;
			}
			saveMeshes(meshes);
		}
		calculateAreas(meshes);
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
		if (reporter == null) {
			reporter = UsageReporter.getInstance(prefs, pluginService, commandService);
		}
		reporter.reportEvent(getClass().getName());
	}

	static void setReporter(final UsageReporter reporter) {
		if (reporter == null) {
			throw new NullPointerException("Reporter cannot be null");
		}
		SurfaceAreaWrapper.reporter = reporter;
	}

	/**
	 * Check if all the spatial axes have a matching calibration, e.g. same unit,
	 * same scaling.
	 * <p>
	 * NB: Public and static for testing purposes.
	 * </p>
	 *
	 * @param space an N-dimensional space.
	 * @param <T> type of the space
	 * @return true if all spatial axes have matching calibration. Also returns
	 *         true if none of them have a unit
	 */
	// TODO make into a utility method or remove if mesh area considers
	// calibration in the future
	static <T extends AnnotatedSpace<CalibratedAxis>> boolean
		isAxesMatchingSpatialCalibration(final T space)
	{
		final boolean noUnits = spatialAxisStream(space).map(CalibratedAxis::unit)
			.allMatch(StringUtils::isNullOrEmpty);
		final boolean matchingUnit = spatialAxisStream(space).map(
			CalibratedAxis::unit).distinct().count() == 1;
		final boolean matchingScale = spatialAxisStream(space).map(a -> a
			.averageScale(0, 1)).distinct().count() == 1;

		return (matchingUnit || noUnits) && matchingScale;
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

		final Iterator<Triangle> triangles = mesh.triangles().iterator();
		final int numTriangles = (int) mesh.triangles().size();
		try (final FileOutputStream writer = new FileOutputStream(path)) {
			final byte[] header = STL_HEADER.getBytes();
			writer.write(header);
			final byte[] facetBytes = ByteBuffer.allocate(4).order(
				ByteOrder.LITTLE_ENDIAN).putInt(numTriangles).array();
			writer.write(facetBytes);
			final ByteBuffer buffer = ByteBuffer.allocate(50);
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			while (triangles.hasNext()) {
				final Triangle triangle = triangles.next();
				writeSTLFacet(buffer, triangle);
				writer.write(buffer.array());
				buffer.clear();
			}
		}
	}

	private void addResult(final String label, final double area) {
		SharedTable.add(label, "Surface area " + unitHeader, area * areaScale);
	}

	private void calculateAreas(final Map<String, Mesh> meshes) {
		statusService.showStatus("Surface area: calculating areas");
		final String name = inputImage.getName();
		meshes.forEach((suffix, mesh) -> {
			final double area = areaOp.calculate(mesh).get();
			final String label = suffix.isEmpty() ? name : name + " " + suffix;
			addResult(label, area);
		});
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

	private Map<String, Mesh> createMeshes(
		final List<Subspace<BitType>> subspaces)
	{
		final Map<String, Mesh> meshes = new HashMap<>();
		for (int i = 0; i < subspaces.size(); i++) {
			statusService.showStatus("Surface area: creating mesh for subspace " + (i + 1));
			final Subspace<BitType> subspace = subspaces.get(i);
			final Mesh mesh = marchingCubesOp.calculate(subspace.interval);
			meshes.put(subspace.toString(), mesh);
			statusService.showProgress(i, subspaces.size());
		}
		statusService.showProgress(subspaces.size(), subspaces.size());
		return meshes;
	}

	private boolean getFileName() {
		path = choosePath();
		if (path == null) {
			return false;
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
		return true;
	}

	private void matchOps(final RandomAccessibleInterval<BitType> interval) {
		marchingCubesOp = Functions.unary(ops, MarchingCubes.class, Mesh.class,
			interval);
		areaOp = Functions.unary(ops, BoundarySize.class, DoubleType.class,
			new NaiveFloatMesh());
	}

	private void prepareResults() {
		unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, '²');

		if (isAxesMatchingSpatialCalibration(inputImage)) {
			final double scale = inputImage.axis(0).averageScale(0.0, 1.0);
			areaScale = scale * scale;
		}
		else {
			uiService.showDialog(BAD_SCALING, WARNING_MESSAGE);
			areaScale = 1.0;
		}
	}

	private void saveMeshes(final Map<String, Mesh> meshes) {
		statusService.showStatus("Surface area: saving files");
		final Map<String, String> savingErrors = new HashMap<>();
		meshes.forEach((key, subspaceMesh) -> {
			final String subspaceId = key.replace(' ', '_').replaceAll("[,:]", "");
			final String filePath = path + "_" + subspaceId + extension;
			try {
				writeBinarySTLFile(filePath, subspaceMesh);
			}
			catch (final IOException e) {
				savingErrors.put(filePath, e.getMessage());
				logService.trace(e);
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
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}

		if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
			cancelMacroSafe(this, NOT_3D_IMAGE);
		}

		if (!ElementUtil.isBinary(inputImage)) {
			cancelMacroSafe(this, NOT_BINARY);
		}
	}

	// -- Helper methods --
	private static void writeSTLFacet(final ByteBuffer buffer,
		final Triangle triangle)
	{
		// Write normal
		buffer.putFloat(triangle.nxf());
		buffer.putFloat(triangle.nyf());
		buffer.putFloat(triangle.nzf());
		// Write vertex 0
		buffer.putFloat(triangle.v0xf());
		buffer.putFloat(triangle.v0yf());
		buffer.putFloat(triangle.v0zf());
		// Write vertex 1
		buffer.putFloat(triangle.v1xf());
		buffer.putFloat(triangle.v1yf());
		buffer.putFloat(triangle.v1zf());
		// Write vertex 2
		buffer.putFloat(triangle.v2xf());
		buffer.putFloat(triangle.v2yf());
		buffer.putFloat(triangle.v2zf());
		// Attribute byte count
		buffer.putShort((short) 0);
	}
}
