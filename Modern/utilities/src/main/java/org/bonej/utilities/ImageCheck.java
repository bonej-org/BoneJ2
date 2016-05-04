package org.bonej.utilities;

import static com.google.common.base.Preconditions.checkNotNull;

import net.imagej.axis.CalibratedAxis;
import net.imagej.space.AnnotatedSpace;

/**
 * Various utility methods for inspecting image properties
 *
 * @author Richard Domander
 */
public class ImageCheck {
	private ImageCheck() {
	}

	/**
	 * Counts the number of spatial dimensions in the given space
	 * @throws NullPointerException if space == null
	 */
	public static <T extends AnnotatedSpace<CalibratedAxis>> long countSpatialDimensions(final T space)
			throws NullPointerException {
		checkNotNull(space, "Cannot count axes of a null space");

		long spatialDimensions = 0;

		final CalibratedAxis[] axes = new CalibratedAxis[space.numDimensions()];
		space.axes(axes);

		for (CalibratedAxis axis : axes) {
			if (axis.type().isSpatial()) {
				spatialDimensions++;
			}
		}

		return spatialDimensions;
	}
}
