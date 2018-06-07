
package org.bonej.wrapperPlugins;

import static org.bonej.utilities.ImagePlusUtil.cleanDuplicate;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_CHANNEL_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.HAS_TIME_DIMENSIONS;
import static org.bonej.wrapperPlugins.CommonMessages.NOT_8_BIT_BINARY_IMAGE;
import static org.bonej.wrapperPlugins.CommonMessages.NO_IMAGE_OPEN;

import ij.plugin.filter.PlugInFilter;
import net.imagej.patcher.LegacyInjector;

import org.bonej.utilities.ImagePlusUtil;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
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

	@Override
	public void run() {
		skeleton = cleanDuplicate(inputImage);
		skeleton.setTitle("Skeleton of " + inputImage.getTitle());
		final PlugInFilter skeletoniser = new Skeletonize3D_();
		statusService.showStatus("Skeletonise: skeletonising");
		skeletoniser.setup("", skeleton);
		skeletoniser.run(null);
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
