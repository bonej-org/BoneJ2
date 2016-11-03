
package org.bonej.ops.thresholdFraction;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;
import java.util.stream.LongStream;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.LongType;

import org.bonej.ops.thresholdFraction.ElementFraction.Results;
import org.bonej.ops.thresholdFraction.ElementFraction.Settings;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Unit tests for the {@link ElementFraction ElementFraction} class
 *
 * @author Richard Domander
 */
public class ElementFractionTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test
	public void testCompute2() {
		// Create a 2x2x2x2x2 test image with values from 0 to 7
		final Img<LongType> img = ArrayImgs.longs(2, 2, 2, 2, 2);
		final ImgPlus<LongType> imgPlus = new ImgPlus<>(img);
		final Settings<LongType> settings = new Settings<>(new LongType(2L),
			new LongType(5L));
		final Iterator<Long> longIterator = LongStream.iterate(0, i -> (i + 1) % 8)
			.iterator();
		imgPlus.cursor().forEachRemaining(e -> e.set(longIterator.next()));

		final Results result = (Results) IMAGE_J.op().run(ElementFraction.class,
			imgPlus, settings);

		assertEquals("Number of elements within thresholds is incorrect", 16,
			result.thresholdElements);
		assertEquals("Total number of elements is incorrect", 32, result.elements);
		assertEquals("Ratio of elements is incorrect", 0.5, result.ratio, 1e-12);
	}
}
