/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2026 Michael Doube, BoneJ developers
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

import static org.bonej.wrapperPlugins.CommonMessages.HAS_CHANNEL_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_TIME_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_3D_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import ij.ImagePlus;
import ij.process.StackStatistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.imagej.Dataset;
import net.imagej.display.ColorTables;
import net.imagej.patcher.LegacyInjector;
import net.imagej.units.UnitService;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.ResultUtils;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import sc.fiji.localThickness.LocalThicknessWrapper;

/**
 * An ImageJ2 command that wraps the sc.fiji.localThickness plugin
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Thickness")
public class ThicknessWrapper extends BoneJCommand {

	static {
		LegacyInjector.preinit();
	}

	@Parameter(validater = "validateImage", type = ItemIO.INPUT)
	private Dataset inputDataset;

	@Parameter(label = "Calculate:",
		description = "Which thickness measures to calculate",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = {
			"Trabecular thickness", "Trabecular separation", "Both" })
	private String mapChoice = "Trabecular thickness";

	@Parameter(label = "Show thickness maps",
		description = "Show resulting map images after calculations",
		required = false)
	private boolean showMaps = true;

	@Parameter(label = "Mask thickness maps",
		description = "Remove pixel artifacts from the thickness maps",
		persist = false, required = false)
	private boolean maskArtefacts = true;

	@Parameter(label = "Trabecular thickness", type = ItemIO.OUTPUT)
	private Dataset trabecularMap;

	@Parameter(label = "Trabecular separation", type = ItemIO.OUTPUT)
	private Dataset separationMap;

	@Parameter
	private UIService uiService;
	@Parameter
	private LogService logService;
	@Parameter
	private StatusService statusService;
	@Parameter
	private ConvertService convertService;
	@Parameter
	private UnitService unitService;

	private boolean foreground;
	private LocalThicknessWrapper localThickness;
	private boolean anisotropyWarned;

	@Override
	public void run() {
		final List<Boolean> mapOptions = getMapOptions();
		createLocalThickness();
		final Map<Boolean, ImagePlus> thicknessMaps = new HashMap<>();
		mapOptions.forEach(foreground -> {
			prepareRun(foreground);
			statusService.showStatus("Thickness: creating thickness map");
			final ImagePlus map = createMap();
			statusService.showStatus("Thickness: calculating results");
			addMapResults(map);
			thicknessMaps.put(foreground, map);
		});
		resultsTable = SharedTable.getTable();
		if (showMaps) {
			trabecularMap = convertService.convert(thicknessMaps.get(true), Dataset.class);
			if (trabecularMap != null) {
				final StackStatistics trabecularStats = new StackStatistics(thicknessMaps.get(true));
				trabecularMap.getImgPlus().setChannelMinimum(0, 0.0);
				trabecularMap.getImgPlus().setChannelMaximum(0, trabecularStats.max);
				trabecularMap.getImgPlus().setColorTable(ColorTables.FIRE, 0);
				if (uiService != null && uiService.isVisible())
        			uiService.show(trabecularMap);
			}
			separationMap = convertService.convert(thicknessMaps.get(false), Dataset.class);
			if (separationMap != null) {
				final StackStatistics separationStats = new StackStatistics(thicknessMaps.get(false));
				separationMap.getImgPlus().setChannelMinimum(0, 0.0);
				separationMap.getImgPlus().setChannelMaximum(0, separationStats.max);
				separationMap.getImgPlus().setColorTable(ColorTables.FIRE, 0);
				if (uiService != null && uiService.isVisible())
        			uiService.show(separationMap);
			}
		}
	}

	private void addMapResults(final ImagePlus map) {
		if (map == null) {
			return;
		}
		final String label = inputDataset.getName();
		final String unitHeader = ResultUtils.getUnitHeader(map);
		final String prefix = foreground ? "Tb.Th" : "Tb.Sp";
		final StackStatistics resultStats = new StackStatistics(map);
		double mean = resultStats.mean;
		double stdDev = resultStats.stdDev;
		double max = resultStats.max;

		if (resultStats.pixelCount == 0) {
			// All pixels are background (NaN), stats not applicable
			mean = Double.NaN;
			stdDev = Double.NaN;
			max = Double.NaN;
		}

		SharedTable.add(label, prefix + " Mean " + unitHeader, mean);
		SharedTable.add(label, prefix + " Std Dev " + unitHeader, stdDev);
		SharedTable.add(label, prefix + " Max " + unitHeader, max);
	}

	private void createLocalThickness() {
		localThickness = new LocalThicknessWrapper();
		localThickness.setSilence(true);
		localThickness.setShowOptions(false);
		localThickness.maskThicknessMap = maskArtefacts;
		localThickness.calibratePixels = true;
	}

	private ImagePlus createMap() {
		final ImagePlus image;

		image = convertService.convert(inputDataset, ImagePlus.class).duplicate();
		
		return localThickness.processImage(image);
	}

	// region -- Helper methods --
	private List<Boolean> getMapOptions() {
		final List<Boolean> mapOptions = new ArrayList<>();
		if ("Trabecular thickness".equals(mapChoice)) {
			mapOptions.add(true);
		}
		else if ("Trabecular separation".equals(mapChoice)) {
			mapOptions.add(false);
		}
		else if ("Both".equals(mapChoice)) {
			mapOptions.add(true);
			mapOptions.add(false);
		}
		else {
			throw new IllegalArgumentException("Unexpected map choice");
		}
		return mapOptions;
	}

	private void prepareRun(final boolean foreground) {
		this.foreground = foreground;
		final String suffix = foreground ? "_Tb.Th" : "_Tb.Sp";
		localThickness.setTitleSuffix(suffix);
		localThickness.inverse = !foreground;
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputDataset == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}

		if (!AxisUtils.has3SpatialDimensions(inputDataset)) {
			cancelMacroSafe(this, NOT_3D_IMAGE);
			return;
		}

		if (AxisUtils.hasChannelDimensions(inputDataset)) {
			cancelMacroSafe(this, HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
			return;
		}
		if (AxisUtils.hasTimeDimensions(inputDataset)) {
			cancelMacroSafe(this, HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
			return;
		}

		if (!ElementUtil.isIJ1Binary(inputDataset, 1000000))
		{
			cancelMacroSafe(this, NOT_8_BIT_BINARY_IMAGE);
			return;
		}

		if (!anisotropyWarned) {
			if (!AxisUtils.isSpatialCalibrationsIsotropic(inputDataset, 0.01, unitService)) {
				if (!Common.warnAnisotropy(AxisUtils.getAnisotropy(inputDataset, unitService), uiService, logService))
					cancel(null);
			}
			anisotropyWarned = true;
		}
	}

	// endregion
}
