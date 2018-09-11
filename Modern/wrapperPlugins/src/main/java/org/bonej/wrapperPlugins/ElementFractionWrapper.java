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

import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.CommonMessages.WEIRD_SPATIAL;

import java.util.List;
import java.util.stream.Collectors;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.table.DefaultColumn;
import net.imagej.table.Table;
import net.imagej.units.UnitService;
import net.imglib2.IterableInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

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

/**
 * This command estimates the size of the given sample by counting its
 * foreground elements, and the whole stack by counting all the elements (bone)
 * and the whole image stack. In the case of a 2D image "size" refers to areas,
 * and in 3D volumes. The plugin displays the sizes and their ratio in the
 * results table. Results are shown in calibrated units, if possible.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class,
	menuPath = "Plugins>BoneJ>Fraction>Area/Volume fraction", headless = true)
public class ElementFractionWrapper<T extends RealType<T> & NativeType<T>>
	extends ContextCommand
{

	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	/**
	 * The element faction results in a {@link Table}
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

	/** Header of the foreground (bone) volume column in the results table */
	private String boneSizeHeader;
	/** Header of the total volume column in the results table */
	private String totalSizeHeader;
	/** Header of the size ratio column in the results table */
	private String ratioHeader;
	/** The calibrated size of an element in the image */
	private double elementSize;

	@Override
	public void run() {
		statusService.showStatus("Element fraction: initializing");
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService,
			inputImage);
		final List<Subspace<BitType>> subspaces = HyperstackUtils.split3DSubspaces(
			bitImgPlus).collect(Collectors.toList());
		prepareResultDisplay();
		final String name = inputImage.getName();
		for (int i = 0; i < subspaces.size(); i++) {
			final Subspace<BitType> subspace = subspaces.get(0);
			statusService.showStatus("Element fraction: calculating subspace #" + (i +
				1));
			statusService.showProgress(i, subspaces.size());
			// The value of each foreground element in a bit type image is 1, so we
			// can count their number just by summing
			final IterableInterval<BitType> interval = Views.flatIterable(
				subspace.interval);
			final double foregroundSize = opService.stats().sum(interval)
				.getRealDouble() * elementSize;
			final double totalSize = interval.size() * elementSize;
			final double ratio = foregroundSize / totalSize;
			final String suffix = subspace.toString();
			final String label = suffix.isEmpty() ? name : name + " " + suffix;
			addResults(label, foregroundSize, totalSize, ratio);
		}
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	private void addResults(final String label, final double foregroundSize,
		final double totalSize, final double ratio)
	{
		SharedTable.add(label, boneSizeHeader, foregroundSize);
		SharedTable.add(label, totalSizeHeader, totalSize);
		SharedTable.add(label, ratioHeader, ratio);
	}

	// region -- Helper methods --
	private void prepareResultDisplay() {
		final char exponent = ResultUtils.getExponent(inputImage);
		final String unitHeader = ResultUtils.getUnitHeader(inputImage, unitService,
			exponent);
		final String sizeDescription = ResultUtils.getSizeDescription(inputImage);

		boneSizeHeader = "Bone " + sizeDescription.toLowerCase() + " " + unitHeader;
		totalSizeHeader = "Total " + sizeDescription.toLowerCase() + " " +
			unitHeader;
		ratioHeader = sizeDescription + " ratio";
		elementSize = ElementUtil.calibratedSpatialElementSize(inputImage,
			unitService);

	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}

		if (!ElementUtil.isColorsBinary(inputImage)) {
			cancel(NOT_BINARY);
		}

		final long spatialDimensions = AxisUtils.countSpatialDimensions(inputImage);
		if (spatialDimensions < 2 || spatialDimensions > 3) {
			cancel(WEIRD_SPATIAL);
		}
	}
	// endregion
}
