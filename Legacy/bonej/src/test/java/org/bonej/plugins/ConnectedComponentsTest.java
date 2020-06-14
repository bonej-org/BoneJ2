package org.bonej.plugins;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

public class ConnectedComponentsTest {

	/**
	 * Check that the correct number of particles is returned regardless of
	 * position of the particle. One particle (ID = 0) is background
	 * and we draw one particle so the correct number of particles is two.
	 */
	@Test
	public void testGetNParticles() {
		for (int z = 1; z <= 57; z += 8) {
			for (int y = 0; y < 56; y += 8) {
				for (int x = 0; x < 56; x += 8) {
					final ImagePlus imp = brick(64, 64, 64, 8, 8, 8, x, y, z);
					ConnectedComponents cc = new ConnectedComponents();
					cc.run(imp, ConnectedComponents.FORE);
					assertEquals(2, cc.getNParticles());			
				}
			}
		}
	}

	/**
	 * 
	 * @param width image width
	 * @param height image height
	 * @param depth image depth
	 * @param brickWidth brick width
	 * @param brickHeight brick height
	 * @param brickDepth brick depth
	 * @param x top left corner x coordinate
	 * @param y top left corner y coordinate
	 * @param z top left corner z coordinate
	 * @return Image stack containing 0 background and a cuboid of foreground
	 */
	private static ImagePlus brick(final int width, final int height, final int depth,
			final int brickWidth, final int brickHeight, final int brickDepth,
			final int x, final int y, final int z) {
		final ImageStack stack = new ImageStack(width, height);
		
		for (int i = 0; i < depth; i++) {
			final ByteProcessor bp = new ByteProcessor(width, height);
			stack.addSlice(bp);
		}
		
		for (int i = z; i < z + brickDepth; i++) {
			final ImageProcessor ip = stack.getProcessor(z);
			ip.setColor(255);
			ip.setRoi(x, y, brickWidth, brickHeight);
			ip.fill();
			stack.setProcessor(ip, z);
		}
		final ImagePlus imp = new ImagePlus("brick", stack);
		return imp;
	}
}
