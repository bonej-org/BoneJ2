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

import static org.bonej.utilities.ImagePlusUtil.cleanDuplicate;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_CHANNEL_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_TIME_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;

import net.imagej.patcher.LegacyInjector;

import org.bonej.utilities.ImagePlusUtil;
import org.bonej.wrapperPlugins.wrapperUtils.UsageReporter;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import org.scijava.prefs.PrefService;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * A wrapper plugin to bundle Skeletonize3D into BoneJ2
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Skeletonise")
public class SkeletoniseWrapper extends ContextCommand {

	static {
		LegacyInjector.preinit();
	}

	/**
	 * Use ImagePlus because of conversion issues of composite images.
	 */
	@Parameter(validater = "validateImage")
	private ImagePlus inputImage;

	/**
	 * Use ImagePlus because a (converted) Dataset has display issues with a
	 * composite image.
	 */
	@Parameter(type = ItemIO.OUTPUT)
	private ImagePlus skeleton;

	@Parameter
	private StatusService statusService;
	@Parameter
	private PrefService prefs;
	@Parameter
	private LogService logService;
	private static UsageReporter reporter;

	@Override
	public void run() {
		skeleton = cleanDuplicate(inputImage);
		skeleton.setTitle("Skeleton of " + inputImage.getTitle());
		final PlugInFilter skeletoniser = new Skeletonize3D_();
		statusService.showStatus("Skeletonise: skeletonising");
		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);
		if (reporter == null) {
			reporter = UsageReporter.getInstance(prefs);
		}
		reporter.reportEvent(getClass().getName());
	}

	static void setReporter(final UsageReporter reporter) {
		if (reporter == null) {
			throw new NullPointerException("Reporter cannot be null");
		}
		SkeletoniseWrapper.reporter = reporter;
	}

	@SuppressWarnings("unused")
	private void validateImage() {
		if (inputImage == null) {
			cancel(NO_IMAGE_OPEN);
			return;
		}
		if (!ImagePlusUtil.isBinaryColour(inputImage) || inputImage
			.getBitDepth() != 8)
		{
			cancel(NOT_8_BIT_BINARY_IMAGE);
			return;
		}
		if (inputImage.getNChannels() > 1) {
			cancel(HAS_CHANNEL_DIMENSIONS + ". Please split the channels.");
			return;
		}
		if (inputImage.getNFrames() > 1) {
			cancel(HAS_TIME_DIMENSIONS + ". Please split the hyperstack.");
		}
	}
}
