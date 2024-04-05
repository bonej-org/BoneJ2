/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */


package org.bonej.wrapperPlugins;

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

import net.imagej.ImgPlus;
import net.imagej.mesh.Mesh;
import net.imagej.mesh.Triangle;
import net.imagej.mesh.naive.NaiveFloatMesh;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops.Geometric.BoundarySize;
import net.imagej.ops.Ops.Geometric.MarchingCubes;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.StringUtils;

/**
 * A wrapper command to calculate mesh surface area
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Surface area")
public class SurfaceAreaWrapper<T extends RealType<T> & NativeType<T>> extends BoneJCommand
{

	static final String STL_WRITE_ERROR =
		"Failed to write the following STL files:\n\n";
	static final String STL_HEADER = StringUtils.padEnd(
		"Binary STL created by BoneJ", 80, '.');
	static final String BAD_SCALING =
		"Cannot scale result because axis calibrations don't match";

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;
	
	@Parameter(label = "Export STL",
		description = "Create a binary STL file from the surface mesh",
		required = false)
	private boolean exportSTL;
	
	@Parameter(label = "STL directory",
			description = "Set the output directory for the binary STL",
			required = false,
			style = "directory")
	private File stlDirectory;
	
	@Parameter
	private OpService opService;
	@Parameter
	private LogService logService;
	@Parameter
	private UIService uiService;
	@Parameter
	private UnitService unitService;
	@Parameter
	private StatusService statusService;

	private UnaryFunctionOp<RandomAccessibleInterval<?>, Mesh> marchingCubesOp;
	private UnaryFunctionOp<Mesh, DoubleType> areaOp;
	private double areaScale;
	private String unitHeader = "";

	@Override
	public void run() {
		statusService.showStatus("Surface area: initialising");
		if (exportSTL && stlDirectory == null) {
			logService.log(logService.getLevel(), "You asked to save the STL file but no path was given.");
			return;
		}
		subspaces = find3DSubspaces(inputImage);
		matchOps(subspaces.get(0).interval);
		prepareResults();
		final Map<String, Mesh> meshes = createMeshes(subspaces);
		if (exportSTL) {
			saveMeshes(meshes);
		}
		calculateAreas(meshes);
		resultsTable = SharedTable.getTable();
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

	private void matchOps(final RandomAccessibleInterval<BitType> interval) {
		marchingCubesOp = Functions.unary(opService, MarchingCubes.class, Mesh.class,
			interval);
		areaOp = Functions.unary(opService, BoundarySize.class, DoubleType.class,
			new NaiveFloatMesh());
	}

	private void prepareResults() {
		unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, "²");

		if (AxisUtils.isSpatialCalibrationsIsotropic(inputImage, 0.001, unitService)) {
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
			final String filePath = stlDirectory.getAbsolutePath() + 
					stripFileExtension(inputImage.getName()) + "_" + subspaceId + ".stl";
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
