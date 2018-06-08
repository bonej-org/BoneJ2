
package org.bonej.wrapperPlugins.wrapperUtils;

import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpEnvironment;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ComplexType;

import org.bonej.utilities.ImagePlusUtil;
import org.scijava.ui.UIService;

import ij.ImagePlus;

/**
 * Miscellaneous utility methods.
 *
 * @author Richard Domander
 */
public final class Common {

	/**
	 * Converts the {@link ImgPlus} to a new ImgPlus with {@link BitType}
	 * elements.
	 * <p>
	 * Also copies ImgPlus metadata.
	 * </p>
	 * 
	 * @param ops an {@link OpService} to find the necessary ops for conversion.
	 * @param imgPlus an image.
	 * @param <C> type of the elements in the input image.
	 * @return the image converted to bit type.
	 */
	public static <C extends ComplexType<C>> ImgPlus<BitType> toBitTypeImgPlus(
		final OpEnvironment ops, final ImgPlus<C> imgPlus)
	{
		final Img<BitType> convertedImg = ops.convert().bit(imgPlus.getImg());
		final ImgPlus<BitType> convertedImgPlus = new ImgPlus<>(convertedImg);
		copyMetadata(imgPlus, convertedImgPlus);

		return convertedImgPlus;
	}

	/**
	 * Shows a warning dialog about image anisotropy, and asks if the user wants
	 * to continue.
	 *
	 * @param image the current image open in ImageJ.
	 * @param uiService used to display the warning dialog.
	 * @return true if user chose OK_OPTION, or image is not anisotropic. False if
	 *         user chose 'cancel' or they closed the dialog.
	 */
	public static boolean warnAnisotropy(final ImagePlus image,
		final UIService uiService)
	{
		final double anisotropy = ImagePlusUtil.anisotropy(image);
		if (anisotropy < 1E-3) {
			return true;
		}
		final String anisotropyPercent = String.format("(%.1f %%)", anisotropy *
			100.0);
		return uiService.showDialog("The image is anisotropic " +
			anisotropyPercent + ". Continue anyway?", WARNING_MESSAGE,
			OK_CANCEL_OPTION) == OK_OPTION;
	}

	/**
	 * Copies image metadata such as name, axis types and calibrations from source
	 * to target.
	 *
	 * @param source source of metadata.
	 * @param target target of metadata.
	 */
	private static void copyMetadata(final ImgPlus<?> source,
		final ImgPlus<?> target)
	{
		target.setName(source.getName());

		final int dimensions = source.numDimensions();
		for (int d = 0; d < dimensions; d++) {
			final CalibratedAxis axis = source.axis(d);
			target.setAxis(axis, d);
		}
	}
}
