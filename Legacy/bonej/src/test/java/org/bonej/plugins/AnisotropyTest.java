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

package org.bonej.plugins;

import static org.junit.Assert.assertEquals;

import ij.ImageStack;
import ij.process.ByteProcessor;
import org.junit.Test;

import ij.ImagePlus;

import java.util.Random;

public class AnisotropyTest {
	private final Anisotropy anisotropy = new Anisotropy();

	@Test
	public void testRunToStableResultIsotropy() {
		final Random random = new Random(1234);
		final ImagePlus imp = binaryNoise(random);
		final Object[] result = anisotropy.runToStableResult(imp, 100, 2000, 50000, 256 / 4, 2.3, 0.005, false);
		final double da = ((double[]) result[0])[0];
		assertEquals(0, da, 1e-2);
	}

	@Test
	public void testRunToStableResultAnisotropy() {
		final ImagePlus imp = plates();
		final Object[] result = anisotropy.runToStableResult(imp, 100, 2000, 50000, 256 / 4, 2.3, 0.005, false);
		final double da = ((double[]) result[0])[0];
		assertEquals(1, da, 1e-12);
	}

	@Test
	public void testCalculateSingleSphereIsotropy() {
		final Random random = new Random(12345);
		final ImagePlus imp = binaryNoise(random);
		final double[] centroid = { 128, 128, 128 };
		final Object[] result = anisotropy.calculateSingleSphere(imp, centroid, 127, 2.3, 50000, false);
		final double da = ((double[]) result[0])[0];
		assertEquals(0, da, 1e-1);
	}

	@Test
	public void testCalculateSingleSphereAnisotropy() {
		final ImagePlus imp = plates();
		final double[] centroid = { 128, 128, 128 };
		final Object[] result = anisotropy.calculateSingleSphere(imp, centroid, 127, 2.3, 50000, false);
		final double da = ((double[]) result[0])[0];
		assertEquals(1, da, 1e-2);
	}

	/**
	 * Creates an ImagePlus with black (0x00) &amp; white (0xFF) noise
	 *
	 * @param generator A random generator for the noise. Using a generator with
	 *          predetermined {@link Random#Random(long)} seed} makes the result
	 *          of this method repeatable
	 * @return an image with binary noise.
	 */
	private static ImagePlus binaryNoise(final Random generator) {
		final int size = 256;
		final int npixels = size * size;
		final ImageStack stack = new ImageStack(size, size);
		for (int i = 0; i < size; i++) {
			final ByteProcessor bp = new ByteProcessor(size, size);
			for (int index = 0; index < npixels; index++) {
				final double random = generator.nextDouble();
				if (random > 0.25) bp.set(index, 255);
			}
			stack.addSlice(bp);
		}
		return new ImagePlus("binary-noise", stack);
	}

	/**
	 * Creates and image with "plates" or "sheets".
	 *
	 * @return an image with xy-plates.
	 */
	private static ImagePlus plates() {
		final int size = 256;
		final int spacing = 8;
		final ImageStack stack = new ImageStack(size, size);
		for (int i = 0; i < size; i++)
			stack.addSlice(new ByteProcessor(size, size));

		for (int i = 1; i <= size; i += spacing) {
			final ByteProcessor bp = (ByteProcessor) stack.getProcessor(i);
			bp.add(255);
		}
		return new ImagePlus("plates", stack);
	}
}
