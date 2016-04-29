package org.bonej.utilities;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;

import net.imagej.Dataset;

import org.scijava.convert.ConvertService;

import ij.ImagePlus;

/**
 * A helper class to avoid mixing IJ1 (ImagePlus) and IJ2 code
 *
 * @author Richard Domander
 */
public class ImagePlusHelper {
	private ImagePlusHelper() {
	}

	/**
	 * This is a HACKy method to check if a dataset is a greyscale image.
	 * Needed, because the Dataset class doesn't contain the necessary metadata
	 * yet
	 *
	 * @throws NullPointerException
	 *             if convertService == null or dataset == null
	 * @param convertService
	 *            The service needed to convert the Dataset
	 * @param dataset
	 *            The Dataset to inspect
	 * @return true if an ImagePlus converted from the dataset has the type
	 *         ImagePlus.GRAY8
	 */
	public static boolean is8BitGreyScale(final ConvertService convertService, final Dataset dataset)
			throws NullPointerException {
		final Optional<ImagePlus> imagePlus = toImagePlus(convertService, dataset);
		return imagePlus.isPresent() && imagePlus.get().getType() == ImagePlus.GRAY8;
	}

	public static boolean isImagePlusCompatible(final ConvertService convertService, final Dataset dataset)
			throws NullPointerException {
		checkNotNull(convertService, "ConvertService is null");
		checkNotNull(dataset, "Dataset is null");

		return convertService.supports(dataset, ImagePlus.class);
	}

	public static Optional<ImagePlus> toImagePlus(final ConvertService convertService, final Dataset dataset)
			throws NullPointerException {
		if (!isImagePlusCompatible(convertService, dataset)) {
			return Optional.empty();
		}

		final ImagePlus imagePlus = convertService.convert(dataset, ImagePlus.class);
		return Optional.of(imagePlus);
	}
}
