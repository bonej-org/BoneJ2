
package org.bonej.wrapperPlugins.wrapperUtils;

import net.imagej.ImgPlus;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ComplexType;

import ij.ImagePlus;

/**
 * Miscellaneous utility methods
 *
 * @author Richard Domander
 */
public class Common {

	/**
	 * Duplicates the image without changing the title of the copy, or cropping it
	 * to the ROI.
	 * <p>
	 * Circumvents the default behaviour of {@link ImagePlus#duplicate()}.
	 * </p>
	 * 
	 * @param image an ImageJ1 style ImagePlus.
	 * @return an unchanged copy of the image.
	 */
	public static ImagePlus cleanDuplicate(final ImagePlus image) {
		image.killRoi();
		final ImagePlus copy = image.duplicate();
		image.restoreRoi();
		copy.setTitle(image.getTitle());
		return copy;
	}

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
		OpService ops, final ImgPlus<C> imgPlus)
	{
		final Img<BitType> convertedImg = ops.convert().bit(imgPlus.getImg());
		final ImgPlus<BitType> convertedImgPlus = new ImgPlus<>(convertedImg);
		copyMetadata(imgPlus, convertedImgPlus);

		return convertedImgPlus;
	}

	/**
	 * Copies image metadata such as name, axis types and calibrations from source
	 * to target.
     *
     * @param source source of metadata.
     * @param target target of metadata.
	 */
	private static void copyMetadata(ImgPlus<?> source, ImgPlus<?> target) {
		target.setName(source.getName());

		final int dimensions = source.numDimensions();
		for (int d = 0; d < dimensions; d++) {
			final CalibratedAxis axis = source.axis(d);
			target.setAxis(axis, d);
		}
	}
}
