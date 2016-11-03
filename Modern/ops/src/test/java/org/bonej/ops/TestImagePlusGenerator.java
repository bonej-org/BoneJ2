
package org.bonej.ops;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * A simple class to help with the tests of TriplePointAngles. Separates IJ1
 * code from IJ2 code to avoid Legacy incompatibility issues.
 *
 * @author Richard Domander
 */
public class TestImagePlusGenerator {

	private TestImagePlusGenerator() {}

	/** Creates an 8-bit, binary colour image of a wire-frame cuboid */
	public static ImagePlus wireFrameCuboid(final int width, final int height,
		final int depth, final int padding)
	{
		final int totalPadding = 2 * padding;
		final int paddedWidth = width + totalPadding;
		final int paddedHeight = height + totalPadding;
		final int paddedDepth = depth + totalPadding;
		final int boxColor = 0xFF;

		final ImagePlus imagePlus = IJ.createImage("Wire-frame cuboid", "8black",
			paddedWidth, paddedHeight, paddedDepth);
		final ImageStack stack = imagePlus.getStack();

		// Draw edges in the xy-plane
		ImageProcessor ip = new ByteProcessor(paddedWidth, paddedHeight);
		ip.setColor(boxColor);
		ip.drawRect(padding, padding, width, height);
		final int firstCuboidSlice = padding + 1;
		final int lastCuboidSlice = padding + depth;
		stack.setProcessor(ip.duplicate(), firstCuboidSlice);
		stack.setProcessor(ip.duplicate(), lastCuboidSlice);

		// Draw edges in the xz-plane
		for (int s = firstCuboidSlice + 1; s < lastCuboidSlice; s++) {
			ip = stack.getProcessor(s);
			ip.setColor(boxColor);
			ip.drawPixel(padding, padding);
			ip.drawPixel(padding, padding + height - 1);
			ip.drawPixel(padding + width - 1, padding);
			ip.drawPixel(padding + width - 1, padding + height - 1);
		}

		return imagePlus;
	}
}
