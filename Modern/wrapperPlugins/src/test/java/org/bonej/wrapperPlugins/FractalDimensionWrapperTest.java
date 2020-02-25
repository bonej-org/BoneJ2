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

package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.table.Column;
import org.scijava.table.DefaultColumn;
import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericTable;
import org.scijava.table.Table;

/**
 * Tests for {@link FractalDimensionWrapper}
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class FractalDimensionWrapperTest extends AbstractWrapperTest {

	@BeforeClass
	public static void oneTimeSetup() {
		FractalDimensionWrapper.setReporter(MOCK_REPORTER);
	}

	@Test
	public void testNonBinaryImageCancelsFractalDimension() {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(imageJ(),
			FractalDimensionWrapper.class);
	}

	@Test
	public void testNullImageCancelsFractalDimension() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
			FractalDimensionWrapper.class);
	}

	@Test
	public void testPointResults() throws Exception {
		// SETUP
		final int expectedRows = 2;
		final double[] expectedSizes = Stream.of(4.0, 2.0).mapToDouble(i -> -Math
			.log(i)).toArray();
		final double[] emptyCounts = Stream.of(0.0, 0.0).mapToDouble(Math::log)
			.toArray();
		final double[] cubeCounts = Stream.of(1.0, 8.0).mapToDouble(Math::log)
			.toArray();
		final double[][] expectedCounts = { emptyCounts, cubeCounts, cubeCounts,
			emptyCounts };
		final ImgPlus<BitType> imgPlus = createTestHyperStack("Test");

		// EXECUTE
		final CommandModule module = command().run(
			FractalDimensionWrapper.class, true, "inputImage", imgPlus,
			"startBoxSize", 4, "smallestBoxSize", 2, "scaleFactor", 2.0,
			"translations", 0L, "showPoints", true).get();

		// VERIFY
		final Collection<?> tables = (Collection<?>) module.getOutput(
			"subspaceTables");
		assertNotNull("Output not found!", tables);
		assertEquals("Wrong number of tables", 4, tables.size());
		final List<GenericTable> pointTables = tables.stream().map(
			t -> (GenericTable) t).collect(Collectors.toList());
		for (int i = 0; i < pointTables.size(); i++) {
			final GenericTable table = pointTables.get(i);
			assertEquals("Table has wrong number of columns", 2, table.size());
			final DoubleColumn sizes = (DoubleColumn) table.get("-log(size)");
			assertEquals("Size column has wrong number of rows", expectedRows, sizes
				.size());
			final DoubleColumn counts = (DoubleColumn) table.get("log(count)");
			assertEquals("Count column has wrong number of rows", expectedRows, counts
				.size());
			for (int j = 0; j < expectedRows; j++) {
				assertEquals("Incorrect log(count) value", expectedCounts[i][j], counts
					.get(j), 1e-12);
				assertEquals("Incorrect -log(box size) value", expectedSizes[j], sizes
					.get(j), 1e-12);
			}
		}
	}

	@Test
	public void testResults() throws Exception {
		// SETUP
		final Iterator<String> expectedHeaders = 
				Stream.of("Fractal dimension", "R²").iterator();
		final String imageName = "Image";
		final Iterator<Double> expectedDimensions = Stream.of(0.0,
			1.4999999999999998, 1.4999999999999998, 0.0).iterator();
		final Iterator<Double> expectedRSquares = Stream.of(Double.NaN,
			0.7500000000000002, 0.7500000000000002, Double.NaN).iterator();
		final ImgPlus<BitType> imgPlus = createTestHyperStack(imageName);

		// EXECUTE
		final CommandModule module = command().run(
			FractalDimensionWrapper.class, true, "inputImage", imgPlus,
			"startBoxSize", 4, "smallestBoxSize", 1, "scaleFactor", 2.0,
			"translations", 0L).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final Table<DefaultColumn<Double>, Double> table =
			(Table<DefaultColumn<Double>, Double>) module.getOutput("resultsTable");
		assertEquals("Wrong number of columns", 2, table.size());
		table.forEach(column -> assertEquals(
			"Column has an incorrect number of rows", 4, column.size()));
		table.stream().map(Column::getHeader).forEach(header -> assertEquals(
			"Column has an incorrect header", header, expectedHeaders.next()));
		table.get("Fractal dimension").forEach(dimension -> assertEquals(
			"Fractal dimension column has a wrong value", expectedDimensions.next(),
			dimension, 1e-12));
		table.get("R²").forEach(r2 -> assertEquals("R² column has a wrong value",
			expectedRSquares.next(), r2, 1e-12));
	}

	/** Create a hyperstack with a cuboid in two subspaces */
	private ImgPlus<BitType> createTestHyperStack(final String imageName) {
		final Img<BitType> img = ArrayImgs.bits(4, 4, 4, 2, 2);
		IntervalView<BitType> cubeView = Views.offsetInterval(img, new long[] { 1,
			1, 1, 1, 0 }, new long[] { 2, 2, 2, 1, 1 });
		cubeView.forEach(BitType::setOne);
		cubeView = Views.offsetInterval(img, new long[] { 1, 1, 1, 0, 1 },
			new long[] { 2, 2, 2, 1, 1 });
		cubeView.forEach(BitType::setOne);
		return new ImgPlus<>(img, imageName, new DefaultLinearAxis(Axes.X),
			new DefaultLinearAxis(Axes.Y), new DefaultLinearAxis(Axes.Z),
			new DefaultLinearAxis(Axes.CHANNEL), new DefaultLinearAxis(Axes.TIME));
	}
}
