/*
 * #%L
 * BoneJ: open source tools for trabecular geometry and whole bone shape analysis.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers. See also individual class @authors.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.doube.util;

import ij.ImagePlus;
import ij.ImageStack;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 * Utility methods for working with the {@link Skeletonize3D_} plugin
 *
 * @author Michael Doube
 * @author Mark Hiner
 */
public class SkeletonUtils {

	/**
	 * Gets a medial axis skeleton from a binary imp using a topology-preserving
	 * iterative algorithm
	 *
	 * @param imp
	 *            input image
	 * @return skeletonised image
	 */

	public static ImagePlus getSkeleton(final ImagePlus imp) {
		final ImagePlus imp2 = imp.duplicate();
		final ImageStack stack2 = imp2.getStack();
		final Skeletonize3D_ sk = new Skeletonize3D_();

		// Prepare data
		sk.prepareData(stack2);

		// Compute Thinning
		sk.computeThinImage(stack2);

		// Convert image to binary 0-255
		for (int i = 1; i <= stack2.getSize(); i++)
			stack2.getProcessor(i).multiply(255);

		imp2.setCalibration(imp.getCalibration());
		imp2.setTitle("Skeleton of " + imp.getTitle());
		return imp2;
	}
}
