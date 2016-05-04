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
