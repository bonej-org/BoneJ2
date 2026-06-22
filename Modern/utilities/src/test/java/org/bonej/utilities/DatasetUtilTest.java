/*-
 * #%L
 * Utility methods for BoneJ2
 * %%
 * Copyright (C) 2015 - 2026 Michael Doube, BoneJ developers
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
package org.bonej.utilities;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.scijava.Context;
import org.scijava.convert.ConvertService;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DatasetUtil.
 */
public class DatasetUtilTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private DatasetService mockDatasetService;
	private Context mockContext;
	private ConvertService mockConvertService;

	@Before
	public void setUp() {
		mockDatasetService = mock(DatasetService.class);
		mockContext = mock(Context.class);
		mockConvertService = mock(ConvertService.class);

		// Wire up: datasetService.getContext() → mockContext
		when(mockDatasetService.getContext()).thenReturn(mockContext);

		// Wire up: context.getService(ConvertService.class) → mockConvertService
		when(mockContext.getService(ConvertService.class)).thenReturn(mockConvertService);
	}

	@After
	public void tearDown() {
		mockDatasetService = null;
		mockContext = null;
		mockConvertService = null;
	}

	// ========== Validation Tests ==========

	@Test(expected = IllegalArgumentException.class)
	public void testLoadRaw3D_InvalidWidth() throws Exception {
		File dummy = tempFolder.newFile("dummy.raw");
		DatasetUtil.loadRaw3D(
				dummy, 0, -5, 10, 10, "uint8", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLoadRaw3D_InvalidHeight() throws Exception {
		File dummy = tempFolder.newFile("dummy.raw");
		DatasetUtil.loadRaw3D(
				dummy, 0, 10, 0, 10, "uint8", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLoadRaw3D_InvalidDepth() throws Exception {
		File dummy = tempFolder.newFile("dummy.raw");
		DatasetUtil.loadRaw3D(
				dummy, 0, 10, 10, -1, "uint8", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLoadRaw3D_NonExistentFile() throws Exception {
		File nonexistent = new File("/nonexistent/dummy.raw");
		DatasetUtil.loadRaw3D(
				nonexistent, 0, 10, 10, 10, "uint8", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLoadRaw3D_NullFile() throws Exception {
		DatasetUtil.loadRaw3D(null, 0, 10, 10, 10, "uint8",
				"Little-endian", 1.0, 1.0, 1.0, mockDatasetService);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testLoadRaw3D_UnsupportedPixelType() throws Exception {
		File file = tempFolder.newFile("test.raw");
		Files.write(file.toPath(), new byte[100]);
		DatasetUtil.loadRaw3D(
				file, 0, 5, 5, 4, "invalid_type", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
		);
	}

	@Test(expected = IOException.class)
	public void testLoadRaw3D_FileTooSmall() throws Exception {
		byte[] smallData = new byte[10];
		File file = writeDataToFile(smallData, "small.raw");
		DatasetUtil.loadRaw3D(
				file, 0, 10, 10, 10, "uint8", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
		);
	}

	@Test(expected = IOException.class)
	public void testLoadRaw3D_HeaderOffsetExceedsFile() throws Exception {
		File file = writeDataToFile(new byte[10], "tiny.raw");
		DatasetUtil.loadRaw3D(file, 100, 2, 2, 2, "uint8",
				"Little-endian", 1.0, 1.0, 1.0, mockDatasetService);
	}

	// ========== readRawBytes Tests ==========

	@Test
	public void testReadRawBytes_NoOffset() throws Exception {
		byte[] expected = {1, 2, 3, 4, 5};
		File file = writeDataToFile(expected, "read_test.raw");
		byte[] result = DatasetUtil.readRawBytes(file, 0, 5);
		assertArrayEquals("Data should match exactly", expected, result);
	}

	@Test
	public void testReadRawBytes_WithHeaderOffset() throws Exception {
		byte[] header = {-1, -2, -3, 4, 5};
		byte[] payload = {10, 20, 30};
		byte[] combined = new byte[header.length + payload.length];
		System.arraycopy(header, 0, combined, 0, header.length);
		System.arraycopy(payload, 0, combined, header.length, payload.length);

		File file = writeDataToFile(combined, "offset_test.raw");
		byte[] result = DatasetUtil.readRawBytes(file, header.length, payload.length);

		assertArrayEquals("Payload should be read after skipping header", payload, result);
		assertNotEquals("Header content should not be in result", header[0], result[0]);
	}

	@Test
	public void testReadRawBytes_LargeBlock() throws Exception {
		int size = 1024;
		byte[] data = new byte[size];
		for (int i = 0; i < size; i++) data[i] = (byte) (i % 256);

		File file = writeDataToFile(data, "large_block.raw");
		byte[] result = DatasetUtil.readRawBytes(file, 0, size);

		assertArrayEquals("Large block read failed", data, result);
	}

	@Test
	public void testReadRawBytes_ZeroLengthRead() throws Exception {
		byte[] data = {1, 2, 3};
		File file = writeDataToFile(data, "zero_read.raw");
		byte[] result = DatasetUtil.readRawBytes(file, 0, 0);
		assertEquals("Zero-length read should return empty array", 0, result.length);
	}

	@Test(expected = IOException.class)
	public void testReadRawBytes_TruncatedFile() throws Exception {
		File file = writeDataToFile(new byte[50], "truncated.raw");
		DatasetUtil.readRawBytes(file, 0, 100);
	}

	// ========== buildDataset Tests ==========

	/**
	 * Helper that creates a mock Dataset to return from ConvertService.convert(),
	 * wrapping the converted ImagePlus metadata where possible.
	 */
	private Dataset stubConvertAndReturnMock() {
		Dataset mockDs = mock(Dataset.class);
		when(mockConvertService.convert(any(ij.ImagePlus.class), eq(Dataset.class)))
				.thenReturn(mockDs);
		return mockDs;
	}

	@Test
	public void testBuildDataset_Uint8() throws Exception {
		Dataset expectedDs = stubConvertAndReturnMock();

		int w = 4, h = 3, d = 2;
		byte[] rawData = new byte[w * h * d];
		for (int i = 0; i < rawData.length; i++) rawData[i] = (byte) i;

		Dataset result = DatasetUtil.buildDataset(
				rawData, w, h, d, "uint8", ByteOrder.LITTLE_ENDIAN, mockDatasetService
		);

		assertSame("buildDataset should return the converted Dataset", expectedDs, result);
		verify(mockConvertService).convert(any(ij.ImagePlus.class), eq(Dataset.class));
		verify(mockDatasetService).getContext();
	}

	@Test
	public void testBuildDataset_Uint16() throws Exception {
		Dataset expectedDs = stubConvertAndReturnMock();

		int w = 2, h = 2, d = 2;
		short[] values = {100, 2000, 30000, 500, 999, 1234, 32000, 0};
		ByteBuffer bb = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		bb.asShortBuffer().put(values);

		Dataset result = DatasetUtil.buildDataset(
				bb.array(), w, h, d, "uint16", ByteOrder.LITTLE_ENDIAN, mockDatasetService
		);

		assertSame(expectedDs, result);
		verify(mockConvertService).convert(any(ij.ImagePlus.class), eq(Dataset.class));
	}

	@Test
	public void testBuildDataset_Int16() throws Exception {
		Dataset expectedDs = stubConvertAndReturnMock();

		int w = 2, h = 2, d = 2;
		short[] values = {-100, 2000, -30000, 500, -999, 1234, -1, 0};
		ByteBuffer bb = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		bb.asShortBuffer().put(values);

		Dataset result = DatasetUtil.buildDataset(
				bb.array(), w, h, d, "int16", ByteOrder.LITTLE_ENDIAN, mockDatasetService
		);

		assertSame(expectedDs, result);
		verify(mockConvertService).convert(any(ij.ImagePlus.class), eq(Dataset.class));
	}

	@Test
	public void testBuildDataset_Float32() throws Exception {
		Dataset expectedDs = stubConvertAndReturnMock();

		int w = 2, h = 2, d = 2;
		float[] values = {1.0f, -2.5f, 3.14f, 0.0f, 100.0f, -0.001f, 1e10f, Float.NaN};
		ByteBuffer bb = ByteBuffer.allocate(values.length * 4).order(ByteOrder.BIG_ENDIAN);
		bb.asFloatBuffer().put(values);

		Dataset result = DatasetUtil.buildDataset(
				bb.array(), w, h, d, "float32", ByteOrder.BIG_ENDIAN, mockDatasetService
		);

		assertSame(expectedDs, result);
		verify(mockConvertService).convert(any(ij.ImagePlus.class), eq(Dataset.class));
	}

	// ========== saveAsRaw Method Existence ==========

	@Test
	public void testSaveAsRaw_MethodExists() throws Exception {
		DatasetUtil.class.getMethod("saveAsRaw", Dataset.class, File.class, boolean.class);
		DatasetUtil.class.getMethod("saveAsRaw", Dataset.class, File.class);
	}

	// ========== saveAsRaw Round-Trip Tests ==========

	@Test
	public void testSaveAsRaw_Uint8ArrayImg() throws Exception {
		int w = 4, h = 3, d = 2;
		ArrayImg<UnsignedByteType, ByteArray> img = ArrayImgs.unsignedBytes(w, h, d);

		byte[] expectedBytes = new byte[w * h * d];
		int idx = 0;
		var cursor = img.cursor();
		while (cursor.hasNext()) {
			cursor.fwd();
			byte val = (byte) idx;
			cursor.get().set(val);
			expectedBytes[idx++] = val;
		}

		Dataset ds = wrapInMockDataset(img, w, h, d);
		File outFile = tempFolder.newFile("out_uint8.raw");

		DatasetUtil.saveAsRaw(ds, outFile, true);

		byte[] written = Files.readAllBytes(outFile.toPath());
		assertArrayEquals("uint8 round-trip failed", expectedBytes, written);
	}

	@Test
	public void testSaveAsRaw_Uint16ArrayImg_LittleEndian() throws Exception {
		int w = 3, h = 2, d = 2;
		ArrayImg<UnsignedShortType, ShortArray> img = ArrayImgs.unsignedShorts(w, h, d);

		short[] expectedValues = new short[w * h * d];
		var cursor = img.cursor();
		for (int i = 0; i < expectedValues.length; i++) {
			cursor.fwd();
			short val = (short) (i * 1000);
			cursor.get().set(val & 0xFFFF);
			expectedValues[i] = val;
		}

		Dataset ds = wrapInMockDataset(img, w, h, d);
		File outFile = tempFolder.newFile("out_uint16_le.raw");

		DatasetUtil.saveAsRaw(ds, outFile, true);

		byte[] written = Files.readAllBytes(outFile.toPath());
		ByteBuffer bb = ByteBuffer.wrap(written).order(ByteOrder.LITTLE_ENDIAN);
		short[] result = new short[expectedValues.length];
		bb.asShortBuffer().get(result);

		assertArrayEquals("uint16 LE round-trip failed", expectedValues, result);
	}

	@Test
	public void testSaveAsRaw_Uint16ArrayImg_BigEndian() throws Exception {
		int w = 2, h = 2, d = 2;
		ArrayImg<UnsignedShortType, ShortArray> img = ArrayImgs.unsignedShorts(w, h, d);

		short[] expectedValues = new short[w * h * d];
		var cursor = img.cursor();
		for (int i = 0; i < expectedValues.length; i++) {
			cursor.fwd();
			short val = (short) (i * 500 + 100);
			cursor.get().set(val & 0xFFFF);
			expectedValues[i] = val;
		}

		Dataset ds = wrapInMockDataset(img, w, h, d);
		File outFile = tempFolder.newFile("out_uint16_be.raw");

		DatasetUtil.saveAsRaw(ds, outFile, false);

		byte[] written = Files.readAllBytes(outFile.toPath());
		ByteBuffer bb = ByteBuffer.wrap(written).order(ByteOrder.BIG_ENDIAN);
		short[] result = new short[expectedValues.length];
		bb.asShortBuffer().get(result);

		assertArrayEquals("uint16 BE round-trip failed", expectedValues, result);
	}

	@Test
	public void testSaveAsRaw_Float32ArrayImg_LittleEndian() throws Exception {
		int w = 2, h = 3, d = 2;
		ArrayImg<FloatType, FloatArray> img = ArrayImgs.floats(w, h, d);

		float[] expectedValues = new float[w * h * d];
		var cursor = img.cursor();
		for (int i = 0; i < expectedValues.length; i++) {
			cursor.fwd();
			float val = i * 1.5f - 3.0f;
			cursor.get().set(val);
			expectedValues[i] = val;
		}

		Dataset ds = wrapInMockDataset(img, w, h, d);
		File outFile = tempFolder.newFile("out_float32_le.raw");

		DatasetUtil.saveAsRaw(ds, outFile, true);

		byte[] written = Files.readAllBytes(outFile.toPath());
		ByteBuffer bb = ByteBuffer.wrap(written).order(ByteOrder.LITTLE_ENDIAN);
		float[] result = new float[expectedValues.length];
		bb.asFloatBuffer().get(result);

		assertArrayEquals("float32 LE round-trip failed", expectedValues, result, 0.0f);
	}

	/**
	 * Round-trip test: Write float32 ArrayImg to raw file (Big-Endian) and read back.
	 */
	@Test
	public void testSaveAsRaw_Float32ArrayImg_BigEndian() throws Exception {
		int w = 2, h = 2, d = 2;
		ArrayImg<FloatType, FloatArray> img = ArrayImgs.floats(w, h, d);

		float[] expectedValues = new float[w * h * d];
		var cursor = img.cursor();
		for (int i = 0; i < expectedValues.length; i++) {
			cursor.fwd();
			float val = (float) Math.sqrt(i) - 1.0f;
			cursor.get().set(val);
			expectedValues[i] = val;
		}

		Dataset ds = wrapInMockDataset(img, w, h, d);
		File outFile = tempFolder.newFile("out_float32_be.raw");

		DatasetUtil.saveAsRaw(ds, outFile, false);

		byte[] written = Files.readAllBytes(outFile.toPath());
		ByteBuffer bb = ByteBuffer.wrap(written).order(ByteOrder.BIG_ENDIAN);
		float[] result = new float[expectedValues.length];
		bb.asFloatBuffer().get(result);

		assertArrayEquals("float32 BE round-trip failed", expectedValues, result, 0.0f);
	}

	/**
	 * Test that the no-arg saveAsRaw defaults to little-endian.
	 */
	@Test
	public void testSaveAsRaw_DefaultIsLittleEndian() throws Exception {
		int w = 2, h = 2, d = 1;
		ArrayImg<UnsignedShortType, ShortArray> img = ArrayImgs.unsignedShorts(w, h, d);

		short val = 0x0102;
		var cursor = img.cursor();
		while (cursor.hasNext()) {
			cursor.fwd();
			cursor.get().set(val);
		}

		Dataset ds = wrapInMockDataset(img, w, h, d);
		File outFile = tempFolder.newFile("out_default_endian.raw");

		DatasetUtil.saveAsRaw(ds, outFile);

		byte[] written = Files.readAllBytes(outFile.toPath());
		// Little-endian: low byte first → 0x02, 0x01
		assertEquals("Default should be little-endian (low byte first)",
				(byte) 0x02, written[0]);
		assertEquals((byte) 0x01, written[1]);
	}

	/**
	 * Ensure saveAsRaw rejects non-3D datasets.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testSaveAsRaw_Non3DDataset() throws Exception {
		ArrayImg<UnsignedByteType, ByteArray> img = ArrayImgs.unsignedBytes(4, 3);
		Dataset ds = wrapInMockDataset(img, 4, 3);

		File outFile = tempFolder.newFile("out_2d.raw");
		DatasetUtil.saveAsRaw(ds, outFile);
	}

	// ========== Endianness Tests ==========

	@Test
	public void testEndiannessConversion() {
		short[] shorts = {1, 258, 513};
		ByteBuffer leBuffer = ByteBuffer.allocate(shorts.length * 2)
				.order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer beBuffer = ByteBuffer.allocate(shorts.length * 2)
				.order(ByteOrder.BIG_ENDIAN);

		leBuffer.asShortBuffer().put(shorts);
		beBuffer.asShortBuffer().put(shorts);

		assertFalse("Short byte representations should differ by endianness",
				Arrays.equals(leBuffer.array(), beBuffer.array()));

		float[] floats = {1.0f, 2.0f};
		ByteBuffer leF = ByteBuffer.allocate(floats.length * 4)
				.order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer beF = ByteBuffer.allocate(floats.length * 4)
				.order(ByteOrder.BIG_ENDIAN);

		leF.asFloatBuffer().put(floats);
		beF.asFloatBuffer().put(floats);

		assertFalse("Float byte representations should differ by endianness",
				Arrays.equals(leF.array(), beF.array()));
	}

	// ========== Helpers ==========

	private File writeDataToFile(byte[] data, String filename) throws Exception {
		File file = tempFolder.newFile(filename);
		java.nio.file.Files.write(file.toPath(), data);
		return file;
	}

	@SuppressWarnings("unchecked")
	private Dataset wrapInMockDataset(Object imgObj, long... dims) {
		Img img = (Img) imgObj;
		Dataset ds = mock(Dataset.class);
		ImgPlus imgPlus = mock(ImgPlus.class);

		when(ds.numDimensions()).thenReturn(dims.length);
		when(ds.getImgPlus()).thenReturn(imgPlus);
		when(imgPlus.getImg()).thenReturn(img);

		return ds;
	}
}