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

import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;
import static org.scijava.ui.DialogPrompt.MessageType.INFORMATION_MESSAGE;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops.Topology.EulerCharacteristic26NFloating;
import net.imagej.ops.Ops.Topology.EulerCorrection;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imagej.ops.special.hybrid.UnaryHybridCF;
import net.imagej.units.UnitService;
import net.imglib2.IterableRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;
import org.scijava.ui.UIService;

/**
 * A wrapper UI class for the Connectivity Ops
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Connectivity>Connectivity (Modern)")
public class ConnectivityWrapper<T extends RealType<T> & NativeType<T>> extends BoneJCommand {

	static final String NEGATIVE_CONNECTIVITY =
		"Connectivity is negative.\nThis usually happens if there are multiple particles or enclosed cavities.\n" +
			"Try running Purify prior to Connectivity.\n";

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	/**
	 * The connectivity results in a {@link Table}
	 * <p>
	 * Null if there are no results
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<Double>, Double> resultsTable;

	@Parameter
	private OpService opService;
	@Parameter
	private UIService uiService;
	@Parameter
	private UnitService unitService;
	@Parameter
	private StatusService statusService;

	private UnaryHybridCF<RandomAccessibleInterval<BitType>, DoubleType> eulerCharacteristicOp;
	private UnaryHybridCF<RandomAccessibleInterval<BitType>, DoubleType> eulerCorrectionOp;

	/** A flag to avoid showing the same warning repeatedly */
	private boolean negativityWarned;
	/** The unit displayed in the results */
	private String unitHeader;
	private int progress;
	private static final int PROGRESS_STEPS = 3;

	@Override
	public void run() {
		statusService.showStatus("Connectivity: initialising");
		final String name = inputImage.getName();
		subspaces = find3DSubspaces(inputImage);

		determineResultUnit();
		matchOps(subspaces.get(0).interval);
		subspaces.forEach(subspace -> {
			progress = 0;
			statusService.showProgress(progress, PROGRESS_STEPS);
			progress++;
			final String suffix = subspace.toString();
			final String label = suffix.isEmpty() ? name : name + " " + suffix;
			subspaceConnectivity(label, subspace.interval);
		});
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
		reportUsage();
	}

	private void addResults(final String label, final double eulerCharacteristic,
		final double deltaEuler, final double connectivity,
		final double connectivityDensity)
	{
		if (connectivity < 0 && !negativityWarned) {
			uiService.showDialog(NEGATIVE_CONNECTIVITY, INFORMATION_MESSAGE);
			negativityWarned = true;
		}

		SharedTable.add(label, "Euler char. (χ)", eulerCharacteristic);
		SharedTable.add(label, "Corrected Euler (χ + Δχ)", deltaEuler);
		SharedTable.add(label, "Connectivity", connectivity);
		SharedTable.add(label, "Conn. density " + unitHeader, connectivityDensity);
	}

	private double calculateConnectivityDensity(
		final RandomAccessibleInterval subspace, final double connectivity)
	{
		final double elements = ((IterableRealInterval) subspace).size();
		final double elementSize = ElementUtil.calibratedSpatialElementSize(
			inputImage, unitService);
		return connectivity / (elements * elementSize);
	}

	private void determineResultUnit() {
		unitHeader = ResultUtils.getUnitHeader(inputImage, unitService, '³');
	}

	// region -- Helper methods --
	private void matchOps(final RandomAccessibleInterval<BitType> interval) {
		eulerCharacteristicOp = Hybrids.unaryCF(opService,
			EulerCharacteristic26NFloating.class, DoubleType.class, interval);
		eulerCorrectionOp = Hybrids.unaryCF(opService, EulerCorrection.class,
			DoubleType.class, interval);
	}

	/** Process connectivity for one 3D subspace */
	private void subspaceConnectivity(final String label,
		final RandomAccessibleInterval<BitType> subspace)
	{
		statusService.showStatus("Connectivity: calculating connectivity");
		statusService.showProgress(progress, PROGRESS_STEPS);
		progress++;
		final double eulerCharacteristic = eulerCharacteristicOp.calculate(subspace)
			.get();
		statusService.showStatus("Connectivity: calculating euler correction");
		statusService.showProgress(progress, PROGRESS_STEPS);
		progress++;
		final double edgeCorrection = eulerCorrectionOp.calculate(subspace).get();
		final double correctedEuler = eulerCharacteristic - edgeCorrection;
		final double connectivity = 1 - correctedEuler;
		final double connectivityDensity = calculateConnectivityDensity(subspace,
			connectivity);

		addResults(label, eulerCharacteristic, correctedEuler, connectivity,
			connectivityDensity);
		statusService.showProgress(progress, PROGRESS_STEPS);
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
