/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.util;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Static methods to generate images for testing
 *
 * @author Michael Doube
 */
public final class TestDataMaker {

	private TestDataMaker() {}

	/**
	 * Create a binary brick of arbitrary width, height and depth, padded with 1
	 * voxel of background on all faces.
	 *
	 * @param width width of the brick.
	 * @param height height of the brick.
	 * @param depth depth of the brick.
	 * @return image with brick in foreground
	 */
	public static ImagePlus brick(final int width, final int height,
		final int depth)
	{
		final ImageStack stack = new ImageStack(width + 2, height + 2);
		final ImageProcessor ip = new ByteProcessor(width + 2, height + 2);
		stack.addSlice("", ip);
		for (int i = 0; i < depth; i++) {
			final ImageProcessor ip2 = new ByteProcessor(width + 2, height + 2);
			ip2.setColor(255);
			ip2.setRoi(1, 1, width, height);
			ip2.fill();
			stack.addSlice("", ip2);
		}
		final ImageProcessor ip3 = new ByteProcessor(width + 2, height + 2);
		stack.addSlice("", ip3);
		return new ImagePlus("brick", stack);
	}
}
