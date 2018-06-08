
package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.table.Column;
import net.imagej.table.DefaultColumn;
import net.imagej.table.DoubleColumn;
import net.imagej.table.GenericTable;
import net.imagej.table.Table;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.Gateway;
import org.scijava.command.CommandModule;

/**
 * Tests for {@link FractalDimensionWrapper}
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class FractalDimensionWrapperTest {

	private static final Gateway IMAGE_J = new ImageJ();

	@After
	public void tearDown() {
		SharedTable.reset();
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test
	public void testNonBinaryImageCancelsFractalDimension() throws Exception {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
			FractalDimensionWrapper.class);
	}

	@Test
	public void testNullImageCancelsFractalDimension() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			FractalDimensionWrapper.class);
	}

	@Test
	public void testResults() throws Exception {
		// SETUP
		final Iterator<String> expectedHeaders = Stream.of(SharedTable.LABEL_HEADER,
			"Fractal dimension", "R²").iterator();
		final String imageName = "Image";
		final Iterator<String> expectedLabels = Stream.of(" Channel: 1, Time: 1",
			" Channel: 1, Time: 2", " Channel: 2, Time: 1", " Channel: 2, Time: 2")
			.map(imageName::concat).iterator();
		final Iterator<Double> expectedDimensions = Stream.of(0.0,
			1.4999999999999998, 1.4999999999999998, 0.0).iterator();
		final Iterator<Double> expectedRSquares = Stream.of(Double.NaN,
			0.7500000000000002, 0.7500000000000002, Double.NaN).iterator();
		final ImgPlus<BitType> imgPlus = createTestHyperStack(imageName);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			FractalDimensionWrapper.class, true, "inputImage", imgPlus,
			"startBoxSize", 4, "smallestBoxSize", 1, "scaleFactor", 2.0,
			"translations", 0L).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final Table<DefaultColumn<String>, String> table =
			(Table<DefaultColumn<String>, String>) module.getOutput("resultsTable");
		assertEquals("Wrong number of columns", 3, table.size());
		table.forEach(column -> assertEquals(
			"Column has an incorrect number of rows", 4, column.size()));
		table.stream().map(Column::getHeader).forEach(header -> assertEquals(
			"Column has an incorrect header", header, expectedHeaders.next()));
		table.get("Label").forEach(s -> assertEquals(
			"Label column has a wrong value", expectedLabels.next(), s));
		table.get("Fractal dimension").forEach(dimension -> assertEquals(
			"Fractal dimension column has a wrong value", expectedDimensions.next(),
			Double.parseDouble(dimension), 1e-12));
		table.get("R²").forEach(r2 -> assertEquals("R² column has a wrong value",
			expectedRSquares.next(), Double.parseDouble(r2), 1e-12));
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
		final double[][] expectedCounts = { emptyCounts, cubeCounts,
			cubeCounts, emptyCounts };
		final String[] expectedLabels = { "Test Channel: 1, Time: 1",
			"Test Channel: 2, Time: 1", "Test Channel: 1, Time: 2",
			"Test Channel: 2, Time: 2" };
		final ImgPlus<BitType> imgPlus = createTestHyperStack("Test");

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			FractalDimensionWrapper.class, true, "inputImage", imgPlus,
			"startBoxSize", 4, "smallestBoxSize", 2, "scaleFactor", 2.0,
			"translations", 0L, "showPoints", true).get();

		// VERIFY
		final Collection<?> tables = (Collection<?>) module.getOutput("subspaceTables");
		assertNotNull("Output not found!", tables);
		assertEquals("Wrong number of tables", 4, tables.size());
		final List<GenericTable> pointTables = tables.stream().map(
			t -> (GenericTable) t).collect(Collectors.toList());
		for (int i = 0; i < pointTables.size(); i++) {
			final GenericTable table = pointTables.get(i);
			assertEquals("Table has wrong number of columns", 3, table.size());
			final Column<String> label = (Column<String>) table.get("Label");
			assertEquals("Label column has wrong number of rows", expectedRows, label
				.size());
			final DoubleColumn sizes = (DoubleColumn) table.get("-log(size)");
			assertEquals("Size column has wrong number of rows", expectedRows, sizes
				.size());
			final DoubleColumn counts = (DoubleColumn) table.get("log(count)");
			assertEquals("Count column has wrong number of rows", expectedRows, counts
				.size());
			for (int j = 0; j < expectedRows; j++) {
				assertEquals("Incorrect label", expectedLabels[i], label.get(j));
				assertEquals("Incorrect log(count) value", expectedCounts[i][j], counts
					.get(j), 1e-12);
				assertEquals("Incorrect -log(box size) value", expectedSizes[j], sizes
					.get(j), 1e-12);
			}
		}
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
		return new ImgPlus<>(img, imageName,
			new DefaultLinearAxis(Axes.X), new DefaultLinearAxis(Axes.Y),
			new DefaultLinearAxis(Axes.Z), new DefaultLinearAxis(Axes.CHANNEL),
			new DefaultLinearAxis(Axes.TIME));
	}
}
