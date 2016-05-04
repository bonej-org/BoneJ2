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
	 * Tries to convert the given Dataset into an ImagePlus
	 *
	 * @param convertService
	 *            The convert service of the context
	 * @return An Optional of ImagePlus, or empty if could not convert
	 * @throws NullPointerException
	 *             if convertService == null or dataset == null
	 */
	public static Optional<ImagePlus> toImagePlus(final ConvertService convertService, final Dataset dataset)
			throws NullPointerException {
		checkNotNull(convertService, "ConvertService is null");
		checkNotNull(dataset, "Dataset is null");

		if (!convertService.supports(dataset, ImagePlus.class)) {
			return Optional.empty();
		}

		final ImagePlus imagePlus = convertService.convert(dataset, ImagePlus.class);
		return Optional.of(imagePlus);
	}
}
