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

import java.util.Iterator;
import java.util.stream.Stream;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

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
		final double[] expectedSizes = new double[] { -Math.log(4), -Math.log(2) };
		final double[] emptyCounts = new double[] { Math.log(0), Math.log(0) };
		final double[] cubeCounts = new double[] { Math.log(1), Math.log(8) };
		final ImgPlus<BitType> imgPlus = createTestHyperStack();

		// EXECUTE
		final CommandModule module = command().run(
			FractalDimensionWrapper.class, true, "inputImage", imgPlus,
			"startBoxSize", 4, "smallestBoxSize", 2, "scaleFactor", 2.0,
			"translations", 0L, "showPoints", true).get();

		// VERIFY
		final GenericTable pointsTable = (GenericTable) module.getOutput("pointsTable");
		assertNotNull("Command should have returned a point table", pointsTable);
		assertEquals("Table has wrong number of columns", 8, pointsTable.getColumnCount());
		testColumn(pointsTable, "-log(size) Channel: 1, Time: 1", expectedSizes);
		testColumn(pointsTable, "log(count) Channel: 1, Time: 1", emptyCounts);
		testColumn(pointsTable, "-log(size) Channel: 1, Time: 2", expectedSizes);
		testColumn(pointsTable, "log(count) Channel: 1, Time: 2", cubeCounts);
		testColumn(pointsTable, "-log(size) Channel: 2, Time: 1", expectedSizes);
		testColumn(pointsTable, "log(count) Channel: 2, Time: 1", cubeCounts);
		testColumn(pointsTable, "-log(size) Channel: 2, Time: 2", expectedSizes);
		testColumn(pointsTable, "log(count) Channel: 2, Time: 2", emptyCounts);
	}

	private void testColumn(final GenericTable table, final String header,
							final double[] expectedValues) throws IllegalArgumentException {
		final DoubleColumn column = (DoubleColumn) table.get(header);
		assertEquals("Column has wrong number of rows", expectedValues.length, column.size());
		assertEquals(expectedValues[0], column.get(0), 1e-12);
		assertEquals(expectedValues[1], column.get(1), 1e-12);
	}

	@Test
	public void testResults() throws Exception {
		// SETUP
		final Iterator<String> expectedHeaders = 
				Stream.of("Fractal dimension", "R²").iterator();
		final Iterator<Double> expectedDimensions = Stream.of(0.0,
			1.4999999999999998, 1.4999999999999998, 0.0).iterator();
		final Iterator<Double> expectedRSquares = Stream.of(Double.NaN,
			0.7500000000000002, 0.7500000000000002, Double.NaN).iterator();
		final ImgPlus<BitType> imgPlus = createTestHyperStack();

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

	/** Create a hyperstack with 2 channels and 2 frames
	 * <p>
	 * Subspaces (C0, F0) and (C1, F1) are empty. The other two have a cube in them.
	 * </p>
	 */
	private ImgPlus<BitType> createTestHyperStack() {
		final Img<BitType> img = ArrayImgs.bits(4, 4, 4, 2, 2);
		IntervalView<BitType> cubeView = Views.offsetInterval(img, new long[] { 1,
			1, 1, 1, 0 }, new long[] { 2, 2, 2, 1, 1 });
		cubeView.forEach(BitType::setOne);
		cubeView = Views.offsetInterval(img, new long[] { 1, 1, 1, 0, 1 },
			new long[] { 2, 2, 2, 1, 1 });
		cubeView.forEach(BitType::setOne);
		return new ImgPlus<>(img, "Test image", new DefaultLinearAxis(Axes.X),
			new DefaultLinearAxis(Axes.Y), new DefaultLinearAxis(Axes.Z),
			new DefaultLinearAxis(Axes.CHANNEL), new DefaultLinearAxis(Axes.TIME));
	}
}
