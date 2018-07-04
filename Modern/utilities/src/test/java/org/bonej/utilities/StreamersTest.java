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
	public void testRealDoubleStream() {
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
	public void testRealDoubleStreamReturnsEmptyIfSpaceNull() {
		final DoubleStream doubleStream = Streamers.realDoubleStream(null);

		assertEquals("Stream should be empty", doubleStream.count(), 0);
	}

	@Test
	public void testSpatialAxisStream() {
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

	@Test
	public void testSpatialAxisStreamReturnsEmptyIfSpaceNull() {
		final Stream<TypedAxis> result = Streamers.spatialAxisStream(null);

		assertNotNull("Stream should not be null", result);
		assertFalse("Stream should be empty", result.findAny().isPresent());
	}
}
