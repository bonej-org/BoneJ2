/*-
 * #%L
 * Mavenized version of the BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2022 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.bonej.plugins;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * @author mdoube
 *
 */
public class PurifyTest {

	/**
	 * Check that no exception is thrown when an empty stack is provided to Purify
	 */
	@Test
	public void testEmptyStack() {
		for (int z = 1; z <= 57; z += 8) {
			for (int y = 0; y < 56; y += 8) {
				for (int x = 0; x < 56; x += 8) {
					final ImageStack stack = new ImageStack(64, 64);
					
					for (int i = 0; i < 64; i++) {
						final ByteProcessor bp = new ByteProcessor(64, 64);
						stack.addSlice(bp);
					}
					final ImagePlus imp = new ImagePlus("empty stack", stack);
					Purify.purify(imp);
				}
			}
		}
	}
	
	/**
	 * Check that the correct number of particles (1) is returned regardless of
	 * position of the particle. One particle (ID = 0) is background
	 * and we draw one particle so the correct number of particles is two.
	 */
	@Test
	public void testOneParticle() {
		for (int z = 1; z <= 57; z += 8) {
			for (int y = 0; y < 56; y += 8) {
				for (int x = 0; x < 56; x += 8) {
					final ImagePlus imp = brick(64, 64, 64, 8, 8, 8, x, y, z);
					ImagePlus purifiedImp = Purify.purify(imp);
					ConnectedComponents cc = new ConnectedComponents();
					cc.run(purifiedImp, ConnectedComponents.FORE);
					assertEquals(2, cc.getNParticles());
				}
			}
		}
	}
	
	/**
	 * Check that the correct number of particles is returned regardless of
	 * number of particles added. One particle (ID = 0) is background
	 * and we draw one particle so the correct number of particles is two.
	 */
	@Test
	public void testRemoveslAllButOneParticle() {
		final ImagePlus imp = brick(64, 64, 64, 8, 8, 8, 24, 24, 24);
		for (int i = 1; i <= 10; i++) {
			for (int j = 1; j < i; j++) {
				addCuboid(imp.getImageStack(), 4, 4, 4, 4 * j, 4 * j, 4 * j);
			}
			ImagePlus purifiedImp = Purify.purify(imp);
			ConnectedComponents cc = new ConnectedComponents();
			cc.run(purifiedImp, ConnectedComponents.FORE);
			assertEquals("Found wrong number of particles when i = " +i, 2, cc.getNParticles());
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
		
		addCuboid(stack, brickWidth, brickHeight, brickDepth, x, y, z);
		
		final ImagePlus imp = new ImagePlus("brick", stack);
		return imp;
	}
	
	/**
	 * @param stack Image stack
	 * @param brickWidth brick width
	 * @param brickHeight brick height
	 * @param brickDepth brick depth
	 * @param x top left corner x coordinate
	 * @param y top left corner y coordinate
	 * @param z top left corner z coordinate
	 */
	private static void addCuboid(ImageStack stack,
			final int brickWidth, final int brickHeight, final int brickDepth,
			final int x, final int y, final int z ) {
		
		for (int i = z; i < z + brickDepth; i++) {
			final ImageProcessor ip = stack.getProcessor(z);
			ip.setColor(255);
			ip.setRoi(x, y, brickWidth, brickHeight);
			ip.fill();
			stack.setProcessor(ip, z);
		}
	}
}
