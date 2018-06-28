
package org.bonej.plugins;

import static org.bonej.plugins.Moments.getEmptyPixels;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the {@link Moments} class.
 *
 * @author Richard Domander
 */
public class MomentsTest {

	@Test
	public void testGetEmptyPixels16bit() {
		final Object pixels = getEmptyPixels(1, 1, 16);
		assertTrue(pixels instanceof short[]);
	}

	@Test
	public void testGetEmptyPixels24bit() {
		final Object pixels = getEmptyPixels(1, 1, 24);
		assertTrue(pixels instanceof int[]);
	}

	@Test
	public void testGetEmptyPixels32bit() {
		final Object pixels = getEmptyPixels(1, 1, 32);
		assertTrue(pixels instanceof float[]);
	}

	@Test
	public void testGetEmptyPixels8bit() {
		final Object pixels = getEmptyPixels(1, 1, 8);
		assertTrue(pixels instanceof byte[]);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetEmptyPixelsBadBitDepth() {
		getEmptyPixels(1, 1, 64);
	}
}
