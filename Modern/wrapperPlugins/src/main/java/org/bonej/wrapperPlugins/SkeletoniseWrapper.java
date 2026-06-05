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
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;
import static org.bonej.wrapperPlugins.wrapperUtils.Common.cancelMacroSafe;

import net.imagej.Dataset;
import net.imagej.patcher.LegacyInjector;

import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * A wrapper plugin to bundle Skeletonize3D into BoneJ2
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Skeletonise")
public class SkeletoniseWrapper extends BoneJCommand {

	static {
		LegacyInjector.preinit();
	}

	@Parameter(type = ItemIO.INPUT, validater = "validateImage")
	private Dataset inputDataset;

	@Parameter(type = ItemIO.OUTPUT)
	private Dataset skeletonDataset;

	@Parameter
	private StatusService statusService;
	
	@Parameter
	private ConvertService convertService;
	
	@Parameter
	private UIService uiService;

	@Override
	public void run() {
		ImagePlus skeleton = convertService.convert(inputDataset, ImagePlus.class).duplicate();
		skeleton.setTitle("skeleton_" + inputDataset.getName());
		final PlugInFilter skeletoniser = new Skeletonize3D_();
		statusService.showStatus("Skeletonise: skeletonising");
		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);
		skeletonDataset = convertService.convert(skeleton, Dataset.class);
		skeleton.close();
        if (uiService != null && uiService.isVisible())
			uiService.show(skeletonDataset);
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputDataset == null) {
			cancelMacroSafe(this, NO_IMAGE_OPEN);
			return;
		}
		if (!ElementUtil.isIJ1Binary(inputDataset, 1000000))
		{
			cancelMacroSafe(this, NOT_8_BIT_BINARY_IMAGE);
			return;
		}
		if (AxisUtils.hasChannelDimensions(inputDataset)) {
			cancelMacroSafe(this, HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
			return;
		}
		if (AxisUtils.hasTimeDimensions(inputDataset)) {
			cancelMacroSafe(this, HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
		}
	}
}
