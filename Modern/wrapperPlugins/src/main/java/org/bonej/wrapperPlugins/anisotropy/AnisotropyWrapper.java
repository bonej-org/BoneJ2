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

package org.bonej.wrapperPlugins.anisotropy;

import static java.util.stream.Collectors.toList;

import static org.bonej.utilities.AxisUtils.isSpatialCalibrationsIsotropic;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_BINARY;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.anisotropy.DegreeOfAnisotropy.DEFAULT_DIRECTIONS;
import static org.bonej.wrapperPlugins.anisotropy.DegreeOfAnisotropy.DEFAULT_LINES;
import static org.bonej.wrapperPlugins.anisotropy.DegreeOfAnisotropy.MINIMUM_SAMPLING_DISTANCE;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

import java.util.List;
import java.util.concurrent.ExecutionException;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.stats.regression.leastSquares.Quadric;
import net.imagej.units.UnitService;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;

import org.apache.commons.math3.util.Precision;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.utilities.Visualiser;
import org.bonej.wrapperPlugins.anisotropy.DegreeOfAnisotropy.Results;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.UsageReporter;
import org.joml.Matrix3dc;
import org.joml.Vector3dc;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
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
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

/**
 * A command that analyses the degree of anisotropy in an image.
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Anisotropy")
public class AnisotropyWrapper<T extends RealType<T> & NativeType<T>> extends
	ContextCommand
{
	private static final double MIN_INCREMENT = Precision.round(MINIMUM_SAMPLING_DISTANCE, 2);

	@SuppressWarnings("unused")
	@Parameter(validater = "validateImage")
	private ImgPlus<T> inputImage;

	@Parameter(label = "Directions",
		description = "The number of times sampling is performed from different directions",
		min = "9", style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "forceRecommendedMinimums", validater = "validateDirections")
	private Integer directions = DEFAULT_DIRECTIONS;

	@Parameter(label = "Lines per direction",
		description = "How many lines are sampled per direction",
		min = "1", style = NumberWidget.SPINNER_STYLE, required = false,
		callback = "forceRecommendedMinimums")
	private Integer lines = DEFAULT_LINES;

	@Parameter(label = "Sampling increment", persist = false,
		description = "Distance between sampling points (in voxels)",
		style = NumberWidget.SPINNER_STYLE, required = false, stepSize = "0.1",
		callback = "incrementChanged", initializer = "forceAboveMinimumIncrement")
	private Double samplingIncrement;

	@Parameter(label = "Recommended minimums",
		description = "Apply minimum recommended values to directions, lines, and increment",
		persist = false, required = false, callback = "forceRecommendedMinimums")
	private boolean recommendedMin;

	@Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
	private String instruction = "NB parameter values can affect results significantly";

	@Parameter(label = "Show radii",
		description = "Show the radii of the fitted ellipsoid in the results",
		required = false)
	private boolean printRadii;

	@Parameter(label = "Show Eigens",
		description = "Show the eigenvectors and eigenvalues of the fitted ellipsoid in the results",
		required = false)
	private boolean printEigens;

	@Parameter(label = "Display MIL vectors",
			description = "Show the vectors of the mean intercept lengths",
			required = false)
	private boolean displayMILVectors;

	/**
	 * The anisotropy results in a {@link Table}.
	 * <p>
	 * Null if there are no results.
	 * </p>
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<Double>, Double> resultsTable;

	@Parameter
	private LogService logService;
	@Parameter
	private StatusService statusService;
	@Parameter
	private OpService opService;
	@Parameter
	private UIService uiService;
	@Parameter
	private UnitService unitService;
	@Parameter
	private PrefService prefService;
	@Parameter
	private PluginService pluginService;
	@Parameter
	private CommandService commandService;

	private boolean calibrationWarned;
	private static UsageReporter reporter;
	private DegreeOfAnisotropy degreeOfAnisotropy;
	private int directionProgress;
	private List<Subspace<BitType>> subspaces;

	@Override
	public void run() {
		initialise();
		calculateHyperStackDAs();
		outputResultsTable();
		reportUsage();
	}

	static void setReporter(final UsageReporter reporter) {
		if (reporter == null) {
			throw new NullPointerException("Reporter cannot be null");
		}
		AnisotropyWrapper.reporter = reporter;
	}

	// Called from multiple threads, but don't add "synchronized" - that has caused dead lock
	// It's enough to show some progress instead of 100 % accurate progress
	void directionFinished() {
		directionProgress++;
		statusService.showProgress(directionProgress, directions);
	}

	// region -- Helper methods --

	private void initialise() {
		statusService.showStatus("Anisotropy: initialising");
		initialiseDegreeOfAnisotropy();
		find3DSubspaces();
	}

	private void initialiseDegreeOfAnisotropy() {
		degreeOfAnisotropy = new DegreeOfAnisotropy(getContext());
		degreeOfAnisotropy.setSamplingDirections(directions);
		degreeOfAnisotropy.setLinesPerDirection(lines);
		degreeOfAnisotropy.setSamplingPointDistance(samplingIncrement);
		degreeOfAnisotropy.setProgressObserver(this);
	}

	private void find3DSubspaces() {
		final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService, inputImage);
		subspaces = HyperstackUtils.split3DSubspaces(bitImgPlus).collect(toList());
	}

	private void calculateHyperStackDAs() {
		for (int i = 0; i < subspaces.size(); i++) {
			statusService.showStatus("Anisotropy: sampling 3D subspace #" + (i + 1));
			calculate3DSubspaceDA(subspaces.get(i));
		}
	}

	private void calculate3DSubspaceDA(final Subspace<BitType> subspace) {
		try {
			final Results results = degreeOfAnisotropy.calculate(subspace.interval);
			addResults(subspace, results);
		} catch (final EllipsoidFittingFailedException e) {
			cancelMacroSafe(this, "Anisotropy could not be calculated - ellipsoid fitting failed");
		} catch (final ExecutionException | InterruptedException e) {
			logService.trace(e.getMessage());
			cancelMacroSafe(this, "The plug-in was interrupted");
		}
	}

	private void addResults(final Subspace<BitType> subspace, final Results results) {
		final String name = getSubspaceName(subspace);
		writeToTable(name, results);
		visualise(name, results.mILVectors);
	}

	private String getSubspaceName(final Subspace<BitType> subspace) {
		final String suffix = subspace.toString();
		return suffix.isEmpty() ? inputImage.getName() : inputImage.getName() + " " + suffix;
	}

	private void writeToTable(final String label, final Results results)
	{
		SharedTable.add(label, "Degree of anisotropy", results.degreeOfAnisotropy);
		if (printRadii) {
			final double[] radii = results.radii;
			SharedTable.add(label, "Radius a", radii[0]);
			SharedTable.add(label, "Radius b", radii[1]);
			SharedTable.add(label, "Radius c", radii[2]);
		}
		if (printEigens) {
			final Matrix3dc eigenVectors = results.eigenVectors;
			SharedTable.add(label, "m00", eigenVectors.m00());
			SharedTable.add(label, "m01", eigenVectors.m01());
			SharedTable.add(label, "m02", eigenVectors.m02());
			SharedTable.add(label, "m10", eigenVectors.m10());
			SharedTable.add(label, "m11", eigenVectors.m11());
			SharedTable.add(label, "m12", eigenVectors.m12());
			SharedTable.add(label, "m20", eigenVectors.m20());
			SharedTable.add(label, "m21", eigenVectors.m21());
			SharedTable.add(label, "m22", eigenVectors.m22());
			final double[] eigenValues = results.eigenValues;
			SharedTable.add(label, "D1", eigenValues[0]);
			SharedTable.add(label, "D2", eigenValues[1]);
			SharedTable.add(label, "D3", eigenValues[2]);
		}
	}

	private void visualise(final String title, final List<Vector3dc> mILVectors) {
		if (displayMILVectors) {
			Visualiser.display3DPoints(mILVectors, title);
		}
	}

	private void outputResultsTable() {
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		}
	}

	private void reportUsage() {
		if (reporter == null) {
			reporter = UsageReporter.getInstance(prefService, pluginService, commandService);
		}
		reporter.reportEvent(getClass().getName());
	}

	@SuppressWarnings("unused")
	private void forceAboveMinimumIncrement() {
	    samplingIncrement = Math.max(MIN_INCREMENT, samplingIncrement);
	}

	@SuppressWarnings("unused")
	private void incrementChanged() {
		if (recommendedMin) {
			samplingIncrement = MIN_INCREMENT;
		} else {
			forceAboveMinimumIncrement();
		}
	}

	@SuppressWarnings("unused")
	private void forceRecommendedMinimums() {
		if (recommendedMin) {
			lines = DEFAULT_LINES;
			directions = DEFAULT_DIRECTIONS;
			samplingIncrement = MIN_INCREMENT;
		}
	}

	// A "min" in a @Parameter only applies to UI, not e.g. a script
	@SuppressWarnings("unused")
	private void validateDirections() {
		if (directions < Quadric.MIN_DATA) {
			cancelMacroSafe(this,
					"Anisotropy cannot be calculated - minimum directions = " + Quadric.MIN_DATA);
		}
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
			return;
		}
		if (!isSpatialCalibrationsIsotropic(inputImage, 0.01, unitService) &&
				!isAnisotropicCalibrationOK()) {
			cancel(null);
		}
	}

	private boolean isAnisotropicCalibrationOK() {
		if (calibrationWarned) {
			return true;
		}
		final Result result = uiService.showDialog(
				"The voxels in the image are anisotropic, which may affect results. Continue anyway?",
				WARNING_MESSAGE, OK_CANCEL_OPTION);
		// Avoid showing warning more than once (validator gets called before and
		// after dialog pops up..?)
		calibrationWarned = true;
		return result == OK_OPTION;
	}
	// endregion
}
