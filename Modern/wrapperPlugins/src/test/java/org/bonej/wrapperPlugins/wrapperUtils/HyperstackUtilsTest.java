/*-
 * #%L
 * High-level BoneJ2 commands.
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

package org.bonej.wrapperPlugins.wrapperUtils;

import static org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.splitSubspaces;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.DefaultAxisType;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.junit.Test;

/**
 * Unit tests for the {@link HyperstackUtils ViewUtils} class
 *
 * @author Richard Domander
 */
public class HyperstackUtilsTest {

	private static final DefaultLinearAxis X_AXIS = new DefaultLinearAxis(Axes.X);
	private static final DefaultLinearAxis Y_AXIS = new DefaultLinearAxis(Axes.Y);
	private static final DefaultLinearAxis Z_AXIS = new DefaultLinearAxis(Axes.Z);
	private static final AxisType W_TYPE = new DefaultAxisType("W", true);
	private static final DefaultLinearAxis W_AXIS = new DefaultLinearAxis(W_TYPE);
	private static final DefaultLinearAxis C_AXIS = new DefaultLinearAxis(
		Axes.CHANNEL);
	private static final DefaultLinearAxis T_AXIS = new DefaultLinearAxis(
		Axes.TIME);
	private static final DefaultLinearAxis BAD_AXIS = new DefaultLinearAxis(Axes
		.unknown());

	@Test
	public void testMultipleAxesOfType() {
		// SETUP
		final Img<ByteType> img = ArrayImgs.bytes(2, 2, 2, 2);
		final Iterator<long[]> expectedSubscripts = Stream.generate(
			() -> new long[] { 1, 2 }).limit(4).iterator();
		final Iterator<String> expectedStrings = Stream.of("Time: 1, Time(2): 1",
			"Time: 2, Time(2): 1", "Time: 1, Time(2): 2", "Time: 2, Time(2): 2")
			.iterator();
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS,
			T_AXIS, T_AXIS);
		final List<AxisType> types = Arrays.asList(Axes.X, Axes.Y);

		// EXECUTE
		final Stream<Subspace<ByteType>> subspaces = splitSubspaces(imgPlus, types);

		// VERIFY
		subspaces.forEach(subspace -> {
			assertTrue(subspace.getAxisTypes().allMatch(t -> t == Axes.TIME));
			assertArrayEquals(
				"The subscripts of the multiple axes of the same type are incorrect",
				expectedSubscripts.next(), subspace.getSubScripts().toArray());
			assertEquals("The string describing the subspace position is incorrect",
				expectedStrings.next(), subspace.toString());
		});
	}

	@Test
	public void testSplit3DSubspacesWith2DImgPlus() {
		// SETUP
		final long height = 3;
		final Img<ByteType> img = ArrayImgs.bytes(2, height);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS);
		final List<AxisType> subspaceTypes = Arrays.asList(Axes.X, Axes.Z);

		// EXECUTE
		final List<Subspace<ByteType>> subspaces = splitSubspaces(imgPlus,
			subspaceTypes).collect(Collectors.toList());

		// VERIFY
		assertEquals(height, subspaces.size());
		subspaces.forEach(s -> {
			final List<AxisType> resultTypes = s.getAxisTypes().collect(Collectors
				.toList());
			assertEquals(1, resultTypes.size());
			assertEquals(Axes.Y, resultTypes.get(0));
		});
	}

	@Test
	public void testSplitSubspaces() {
		// SETUP
		final long tSize = 3;
		final long cSize = 3;
		final long wSize = 3;
		final long expectedSubspaces = tSize * cSize * wSize;
		final long depth = 2;
		final long height = 10;
		final long width = 15;
		final Img<ByteType> img = ArrayImgs.bytes(width, height, depth, wSize,
			cSize, tSize);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS,
			Z_AXIS, W_AXIS, C_AXIS, T_AXIS);

		// EXECUTE
		final List<Subspace<ByteType>> subspaces = HyperstackUtils.split3DSubspaces(
			imgPlus).collect(Collectors.toList());

		// VERIFY
		assertEquals("Wrong number of subspaces", expectedSubspaces, subspaces
			.size());
		subspaces.stream().map(s -> s.interval).forEach(v -> {
			assertEquals("Subspace has wrong number of dimensions", 3, v
				.numDimensions());
			assertEquals("Incorrect max X", width - 1, v.max(0));
			assertEquals("Incorrect min X", 0, v.min(0));
			assertEquals("Incorrect max Y", height - 1, v.max(1));
			assertEquals("Incorrect min Y", 0, v.min(1));
			assertEquals("Incorrect max Z", depth - 1, v.max(2));
			assertEquals("Incorrect min Z", 0, v.min(2));
		});
	}

	/**
	 * Test that subspaces is empty, if we try to split into subspaces that don't
	 * exist in the stack
	 */
	@Test
	public void testSplitSubspacesEmptySubspaces() {
		// SETUP
		final Img<ByteType> img = ArrayImgs.bytes(2, 2);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS);
		final List<AxisType> subspaceTypes = Collections.singletonList(
			Axes.CHANNEL);

		// EXECUTE
		final Stream<Subspace<ByteType>> subspaces = splitSubspaces(imgPlus,
			subspaceTypes);

		// VERIFY
		assertEquals(0, subspaces.count());
	}

	@Test
	public void testSplitSubspacesEmptyTypes() {
		// SETUP
		final Img<ByteType> img = ArrayImgs.bytes(2, 2);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS);
		final List<AxisType> emptyList = Collections.emptyList();

		// EXECUTE
		final Stream<Subspace<ByteType>> subspaces = splitSubspaces(imgPlus,
			emptyList);

		// VERIFY
		assertEquals(0, subspaces.count());
	}

	/**
	 * Test that the subspace stream is identical to the input hyperstack, if
	 * subspace dimensions are equal
	 */
	@Test
	public void testSplitSubspacesIdentical() {
		// SETUP
		final Img<ByteType> img = ArrayImgs.bytes(2, 3);
		final AxisType[] types = { Axes.X, Axes.Y };
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", types);

		// EXECUTE
		final List<Subspace<ByteType>> subspaces = splitSubspaces(imgPlus, Arrays
			.asList(types)).collect(Collectors.toList());

		// VERIFY
		assertEquals(1, subspaces.size());
		assertEquals(imgPlus, subspaces.get(0).interval);
	}

	/**
	 * Fill the 3D subspaces of a 5D hyperstack with random data, and check that
	 * it's split correctly. That is, each element in each 3D space split should
	 * correspond to the original image
	 */
	@Test
	public void testSplitSubspacesIntervalData() {
		// SETUP
		final long depth = 3;
		final long height = 3;
		final long width = 3;
		final long channels = 2;
		final long frames = 2;
		final Random random = new Random(0xC0FFEE);
		final ArrayImg<LongType, LongArray> img = ArrayImgs.longs(width, height,
			depth, channels, frames);
		final ImgPlus<LongType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS,
			Z_AXIS, C_AXIS, T_AXIS);
		final List<long[]> positions = Arrays.asList(new long[] { 0, 0, 0, 0, 0 },
			new long[] { 0, 0, 0, 1, 0 }, new long[] { 0, 0, 0, 0, 1 }, new long[] {
				0, 0, 0, 1, 1 });
		final long[] sizes = { width, height, depth, 1, 1 };
		final List<IntervalView<LongType>> expectedSubspaces = new ArrayList<>();
		positions.forEach(position -> {
			final IntervalView<LongType> subspace = Views.offsetInterval(imgPlus,
				position, sizes);
			subspace.forEach(e -> e.set(random.nextLong()));
			expectedSubspaces.add(subspace);
		});

		// EXECUTE
		final Stream<Subspace<LongType>> subspaces = HyperstackUtils
			.split3DSubspaces(imgPlus);

		// VERIFY
		subspaces.forEach(subspace -> {
			final Iterator<LongType> expected = Views.flatIterable(expectedSubspaces
				.remove(0)).iterator();
			final IterableInterval<LongType> resultIterable = Views.flatIterable(
				subspace.interval);
			resultIterable.forEach(result -> assertEquals(expected.next().get(),
				result.get()));
		});
	}

	/**
	 * Test that, for example, if you want a {X, Y, T} subspaces of a {X, Y, Z, T,
	 * T} hyperstack, the subspaces contain all the time axes. You should get n
	 * {X, Y, T, T} subspaces, where n is the size of the Z-dimension.
	 */
	@Test
	public void testSplitSubspacesMultipleSubspaceTypes() {
		// SETUP
		final long depth = 5;
		final Img<ByteType> img = ArrayImgs.bytes(2, 2, depth, 13, 14);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS,
			Z_AXIS, T_AXIS, T_AXIS);
		final List<AxisType> subspaceTypes = Arrays.asList(Axes.X, Axes.Y,
			Axes.TIME);

		// EXECUTE
		final Stream<Subspace<ByteType>> subspaces = splitSubspaces(imgPlus,
			subspaceTypes);

		// VERIFY
		assertEquals(depth, subspaces.count());
	}

	/**
	 * Test that subspaces is empty, if we ImgPlus has no metadata about axis
	 * types
	 */
	@Test
	public void testSplitSubspacesNoImgPlusMeta() {
		// SETUP
		final Img<ByteType> img = ArrayImgs.bytes(2, 2, 2);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", BAD_AXIS, BAD_AXIS,
			BAD_AXIS);
		final List<AxisType> subspaceTypes = Arrays.asList(Axes.X, Axes.Y);

		// EXECUTE
		final Stream<Subspace<ByteType>> subspaces = splitSubspaces(imgPlus,
			subspaceTypes);

		// VERIFY
		assertEquals(0, subspaces.count());
	}

	@Test(expected = NullPointerException.class)
	public void testSplitSubspacesThrowsNPEIfTypesNull() {
		// SETUP
		final Img<ByteType> img = ArrayImgs.bytes(2, 2);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS);

		// EXECUTE
		splitSubspaces(imgPlus, null);
	}

	@Test(expected = NullPointerException.class)
	public void testSplitSubspacesThrowsNPEIfImgPlusNull() {
		splitSubspaces(null, Arrays.asList(Axes.X, Axes.Y));
	}

	@Test
	public void testViewMeta() {
		// SETUP
		final AxisType[] expectedTypes = { W_TYPE, Axes.CHANNEL, Axes.TIME };
		final Iterator<long[]> expectedPositions = Stream.of(new long[] { 0, 0, 0 },
			new long[] { 1, 0, 0 }, new long[] { 0, 1, 0 }, new long[] { 1, 1, 0 },
			new long[] { 0, 0, 1 }, new long[] { 1, 0, 1 }, new long[] { 0, 1, 1 },
			new long[] { 1, 1, 1 }).iterator();
		final Iterator<String> expectedStrings = Stream.of(
			"W: 0, Channel: 1, Time: 1", "W: 1, Channel: 1, Time: 1",
			"W: 0, Channel: 2, Time: 1", "W: 1, Channel: 2, Time: 1",
			"W: 0, Channel: 1, Time: 2", "W: 1, Channel: 1, Time: 2",
			"W: 0, Channel: 2, Time: 2", "W: 1, Channel: 2, Time: 2").iterator();
		final Img<ByteType> img = ArrayImgs.bytes(2, 2, 2, 2, 2, 2);
		final ImgPlus<ByteType> imgPlus = new ImgPlus<>(img, "", X_AXIS, Y_AXIS,
			Z_AXIS, W_AXIS, C_AXIS, T_AXIS);

		// EXECUTE
		final List<Subspace<ByteType>> subspaces = HyperstackUtils.split3DSubspaces(
			imgPlus).collect(Collectors.toList());

		// VERIFY
		subspaces.forEach(subspace -> {
			assertArrayEquals(expectedTypes, subspace.getAxisTypes().toArray());
			assertArrayEquals(expectedPositions.next(), subspace.getPosition()
				.toArray());
			assertEquals(expectedStrings.next(), subspace.toString());
		});
	}
}
