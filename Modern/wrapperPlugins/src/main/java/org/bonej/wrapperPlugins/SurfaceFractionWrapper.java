/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2025 Michael Doube, BoneJ developers
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

import net.imagej.ImgPlus;
import net.imagej.mesh.Mesh;
import net.imagej.mesh.naive.NaiveFloatMesh;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops.Copy.RAI;
import net.imagej.ops.Ops.Geometric.MarchingCubes;
import net.imagej.ops.Ops.Geometric.Size;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.function.UnaryFunctionOp;
import net.imagej.units.UnitService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * First this command creates a surface mesh from both all foreground voxels
 * (bone) and the whole image stack. Then it calculates the surfaces' volumes,
 * their ratio, and shows the results. Results are shown in calibrated units, if
 * possible.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BoneJ>Fraction>Surface fraction")
public class SurfaceFractionWrapper<T extends RealType<T> & NativeType<T>> extends BoneJCommand
{

	/** Header of ratio column in the results table */
	private static final String ratioHeader = "BV/TV";
	private static UnaryFunctionOp<RandomAccessibleInterval<?>, Mesh> marchingCubes;
	private static UnaryFunctionOp<Mesh, DoubleType> meshVolume;
	private static UnaryFunctionOp<RandomAccessibleInterval<?>, RandomAccessibleInterval> raiCopy;

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;
	@Parameter
	private OpService opService;
	@Parameter
	private UnitService unitService;
	@Parameter
	private StatusService statusService;
	/** Header of the thresholded volume column in the results table */
	private String bVHeader;
	/** Header of the total volume column in the results table */
	private String tVHeader;
	/** The calibrated size of an element in the image */
	private double elementSize;

	@Override
	public void run() {
		statusService.showStatus("Surface fraction: initializing");
		subspaces = find3DSubspaces(inputImage);
		matchOps(subspaces.get(0).interval);
		prepareResultDisplay();
		for (int i = 0; i < subspaces.size(); i++) {
			calculateSubspaceVolumes(subspaces.get(i), i + 1);
			statusService.showProgress(i, subspaces.size());
		}
		resultsTable = SharedTable.getTable();
	}

	// region -- Helper methods --

	private void addResults(final String label, final double[] results) {
		SharedTable.add(label, bVHeader, results[0]);
		SharedTable.add(label, tVHeader, results[1]);
		SharedTable.add(label, ratioHeader, results[2]);
	}

	private double[] calculateMeshVolumes(final Mesh foregroundMesh,
		final Mesh totalMesh)
	{
		final double foregroundVolume = meshVolume.calculate(foregroundMesh).get() *
			elementSize;
		final double totalVolume = meshVolume.calculate(totalMesh).get() *
			elementSize;
		final double ratio = foregroundVolume / totalVolume;
		return new double[] { foregroundVolume, totalVolume, ratio };
	}

	private void calculateSubspaceVolumes(final Subspace<BitType> subspace,
		final int subspaceNumber)
	{
		statusService.showStatus(
			"Surface fraction: creating surfaces for subspace #" + subspaceNumber);
		final Mesh foregroundMesh = marchingCubes.calculate(subspace.interval);
		final Mesh totalMesh = createTotalMesh(subspace.interval);
		statusService.showStatus(
			"Surface fraction: calculating volumes for subspace #" + subspaceNumber);
		final double[] results = calculateMeshVolumes(foregroundMesh, totalMesh);
		final String suffix = subspace.toString();
		final String name = inputImage.getName();
		final String label = suffix.isEmpty() ? name : name + " " + suffix;
		addResults(label, results);
	}

	private Mesh createTotalMesh(
		final RandomAccessibleInterval<BitType> subspace)
	{
		@SuppressWarnings("unchecked")
		final RandomAccessibleInterval<BitType> totalMask = raiCopy.calculate(
			subspace);
		// Because we want to create a surface from the whole image, set everything
		// in the mask to foreground
		final IterableInterval<BitType> iterable = Views.flatIterable(totalMask);
		iterable.forEach(BitType::setOne);
		return marchingCubes.calculate(totalMask);
	}

	private void matchOps(final RandomAccessibleInterval<BitType> subspace) {
		raiCopy = Functions.unary(opService, RAI.class,
			RandomAccessibleInterval.class, subspace);
		marchingCubes = Functions.unary(opService, MarchingCubes.class, Mesh.class,
			subspace);
		// Create a dummy object to make op matching happy
		meshVolume = Functions.unary(opService, Size.class, DoubleType.class,
			new NaiveFloatMesh());
	}

	private void prepareResultDisplay() {
		final char exponent = ResultUtils.getExponent(inputImage);
		final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService,
			String.valueOf(exponent));
		bVHeader = "BV " + unitHeader;
		tVHeader = "TV " + unitHeader;
		elementSize = ElementUtil.calibratedSpatialElementSize(inputImage,
			unitService);
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}

		if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
			cancelMacroSafe(this, NOT_3D_IMAGE);
            return;
		}

		if (!ElementUtil.isBinary(inputImage)) {
			cancelMacroSafe(this, NOT_BINARY);
		}
	}
	// endregion
}
