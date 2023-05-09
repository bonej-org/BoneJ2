/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
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


package org.bonej.wrapperPlugins;

import static org.bonej.wrapperPlugins.SurfaceAreaWrapper.STL_WRITE_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.mesh.Mesh;
import net.imagej.mesh.Triangle;
import net.imagej.mesh.Triangles;
import net.imagej.mesh.naive.NaiveFloatMesh;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

/**
 * Tests for {@link SurfaceAreaWrapper}
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class SurfaceAreaWrapperTest extends AbstractWrapperTest {

	@Test
	public void test2DImageCancelsIsosurface() {
		CommonWrapperTests.test2DImageCancelsPlugin(imageJ(),
			SurfaceAreaWrapper.class);
	}

	@Test
	public void testFileWritingExceptionsShowErrorDialog() throws Exception {
		// Mock a File that will cause an exception
		final File exceptionsThrowingFile = mock(File.class);
		// Directory path throws an exception
		when(exceptionsThrowingFile.getAbsolutePath()).thenReturn("/.stl/");

		// Create a test image
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm");
		final Img<BitType> img = ArrayImgs.bits(1, 1, 1);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis);

		// Mock UI
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(MOCK_UI.chooseFile(any(File.class), anyString())).thenReturn(
			exceptionsThrowingFile);
		when(MOCK_UI.dialogPrompt(startsWith(STL_WRITE_ERROR), anyString(), eq(
			ERROR_MESSAGE), any())).thenReturn(mockPrompt);

		// Run plugin
		command().run(SurfaceAreaWrapper.class, true, "inputImage", imgPlus,
			"exportSTL", true).get();

		// Verify that write error dialog got shown
		verify(MOCK_UI, timeout(1000).times(1)).dialogPrompt(startsWith(
			STL_WRITE_ERROR), anyString(), eq(ERROR_MESSAGE), any());
	}

	@Test
	public void testNonBinaryImageCancelsIsosurface() {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(imageJ(),
			SurfaceAreaWrapper.class);
	}

	@Test
	public void testNullImageCancelsIsosurface() {
		CommonWrapperTests.testNullImageCancelsPlugin(imageJ(),
			SurfaceAreaWrapper.class);
	}

	@Test
	public void testResults() throws Exception {
		// SETUP
		final double scale = 0.1;
		final String unit = "mm";
		final int width = 3;
		final int height = 3;
		final int depth = 3;
		// The mesh resulting from marching cubes is effectively one voxel smaller
		// in each dimension
		final double expectedArea = ((width - 1) * (height - 1) * 2 + (width - 1) *
			(depth - 1) * 2 + (height - 1) * (depth - 1) * 2) * (scale * scale);
		final String[] expectedHeaders = { ("Surface area (" + unit + "²)") };
		final double[] expectedValues = { 0, expectedArea, expectedArea, 0 };
		/*
		 * Create a calibrated hyperstack with two channels and two frames.
		 * Two of the 3D subspaces are empty, and two of them contain a 3x3 cuboids
		 * The cuboids have one voxel of empty space around them
		 */
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, scale);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit, scale);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<BitType> img = ArrayImgs.bits(width + 2, height + 2, depth + 2, 2,
			2);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis, cAxis, tAxis);
		final RandomAccess<BitType> access = imgPlus.randomAccess();
		for (int z = 1; z <= depth; z++) {
			for (int y = 1; y <= height; y++) {
				for (int x = 1; x <= width; x++) {
					// Add a voxel to Channel 1, Frame 0
					access.setPosition(new long[] { x, y, z, 1, 0 });
					access.get().setOne();
					// Add a voxel to Channel 0, Frame 1
					access.setPosition(new long[] { x, y, z, 0, 1 });
					access.get().setOne();
				}
			}
		}

		// EXECUTE
		final CommandModule module = command().run(SurfaceAreaWrapper.class,
			true, "inputImage", imgPlus, "exportSTL", false).get();

		// VERIFY
		@SuppressWarnings("unchecked")
		final List<DefaultColumn<Double>> table =
			(List<DefaultColumn<Double>>) module.getOutput("resultsTable");
		assertNotNull(table);
		assertEquals("Wrong number of columns", 1, table.size());
		for (int i = 0; i < 1; i++) {
			final DefaultColumn<Double> column = table.get(i);
			assertEquals("A column has wrong number of rows", 4, column.size());
			assertEquals("A column has an incorrect header", expectedHeaders[i],
				column.getHeader());
			for (int j = 0; j < column.size(); j++) {
				assertEquals("Column has an incorrect value", expectedValues[j], column.get(j), 1e-12);
			}
		}
	}

	@Test
	public void testWriteBinarySTLFile() throws Exception {
		final int headerSize = 84;
		final int bytesPerFacet = 50;
		// Create test mesh
		final Mesh mesh = new NaiveFloatMesh();
		final Triangles triangles = mesh.triangles();
		// @formatter:off
		triangles.addf(
				1.0f, 0.0f, 0.0f,
				0.0f, 1.0f, 0.0f,
				0.0f, 0.0f, 0.0f,
				0.0f, 0.0f, 1.0f
		);
		triangles.addf(
				0.0f, 0.0f, 1.0f,
				0.0f, 1.0f, 0.0f,
				0.0f, 0.0f, 0.0f,
				1.0f, 0.0f, 0.0f
		);
		// @formatter:on

		final int expectedLength = headerSize + 2 * bytesPerFacet;

		// Write test mesh to a file
		final String filePath = "./test_file.stl";
		SurfaceAreaWrapper.writeBinarySTLFile(filePath, mesh);

		// Read and delete the test file
		final Path path = Paths.get(filePath);
		final byte[] bytes = Files.readAllBytes(path);
		Files.delete(path);

		// Assert that the STL file is valid and matches the mesh
		assertEquals("Size of STL file is incorrect", expectedLength, bytes.length);

		final String header = new String(Arrays.copyOfRange(bytes, 0, 80));
		assertEquals("File header is incorrect", SurfaceAreaWrapper.STL_HEADER,
			header);

		final int numFacets = ByteBuffer.wrap(bytes, 80, 4).order(
			ByteOrder.LITTLE_ENDIAN).getInt();
		assertEquals("Wrong number of facets in the file", 2, numFacets);

		final Iterator<Triangle> iterator = mesh.triangles().iterator();
		final ByteBuffer buffer = ByteBuffer.wrap(bytes, headerSize, 2 *
			bytesPerFacet).order(ByteOrder.LITTLE_ENDIAN);
		while (iterator.hasNext()) {
			final Triangle triangle = iterator.next();
			assertEquals(triangle.nxf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.nyf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.nzf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v0xf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v0yf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v0zf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v1xf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v1yf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v1zf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v2xf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v2yf(), buffer.getFloat(), 1e-12);
			assertEquals(triangle.v2zf(), buffer.getFloat(), 1e-12);
			// Skip attribute bytes
			buffer.getShort();
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testWriteBinarySTLFileEmptyNameThrowsIAE() throws Exception {
		final Mesh mesh = new NaiveFloatMesh();

		SurfaceAreaWrapper.writeBinarySTLFile("", mesh);
	}

	@Test(expected = NullPointerException.class)
	public void testWriteBinarySTLFileNullMeshThrowsNPE() throws Exception {
		SurfaceAreaWrapper.writeBinarySTLFile("Mesh", null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testWriteBinarySTLFileNullNameThrowsIAE() throws Exception {
		final Mesh mesh = new NaiveFloatMesh();

		SurfaceAreaWrapper.writeBinarySTLFile(null, mesh);
	}
}
