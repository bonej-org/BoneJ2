
package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.axis.TypedAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;

import org.junit.Test;

/**
 * Unit tests for {@link Streamers}
 *
 * @author Richard Domander
 */
public class StreamersTest {

	@Test
	public void testRealDoubleStreamReturnsEmptyIfSpaceNull() throws Exception {
		final DoubleStream doubleStream = Streamers.realDoubleStream(null);

		assertEquals("Stream should be empty", doubleStream.count(), 0);
	}

	@Test
	public void testRealDoubleStream() throws Exception {
		final List<DoubleType> list = Arrays.asList(new DoubleType(2),
			new DoubleType(3), new DoubleType(11));

		final List<Double> result = Streamers.realDoubleStream(list).boxed()
			.collect(Collectors.toList());

		assertEquals("Stream had wrong number of elements", list.size(), result
			.size());

		for (int i = 0; i < list.size(); i++) {
			assertEquals("Stream had wrong values", list.get(i).getRealDouble(),
				result.get(i), 1e-12);
		}
	}

	@Test
	public void testSpatialAxisStreamReturnsEmptyIfSpaceNull() throws Exception {
		final Stream<TypedAxis> result = Streamers.spatialAxisStream(null);

		assertNotNull("Stream should not be null", result);
		assertFalse("Stream should be empty", result.findAny().isPresent());
	}

	@Test
	public void testSpatialAxisStream() throws Exception {
		// Create a test image that has spatial axes
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y);
		final long[] dimensions = { 10, 3, 10 };
		final Img<DoubleType> img = ArrayImgs.doubles(dimensions);
		final ImgPlus<DoubleType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			tAxis, yAxis);

		final List<AxisType> result = Streamers.spatialAxisStream(imgPlus).map(
			TypedAxis::type).collect(Collectors.toList());

		assertNotNull("Stream should not be null", result);
		assertEquals("Wrong number of axes in stream", 2, result.size());
		assertEquals("Axes in the stream are in wrong order", Axes.X, result.get(
			0));
		assertEquals("Axes in the stream are in wrong order", Axes.Y, result.get(
			1));
	}
}
