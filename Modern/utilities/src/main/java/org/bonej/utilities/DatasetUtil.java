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

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.scijava.Context;
import org.scijava.convert.ConvertService;

/**
 * Utility class for loading raw binary 3D image data into ImageJ2 Datasets.
 * <p>
 * This class constructs Datasets by wrapping native ImageJ1 ImagePlus objects.
 * This ensures that when legacy plugins convert these Datasets back to ImagePlus,
 * they receive the original native byte[]/short[]/float[] arrays, avoiding the
 * performance overhead of imglib2 wrappers.
 */
public final class DatasetUtil {

	private DatasetUtil() {
		// Prevent instantiation
	}

	/**
	 * Loads a raw binary file as a 3D Dataset with spatial calibration.
	 *
	 * @param filePath      Path to the raw file on disk
	 * @param headerOffset  Number of bytes to skip at the start of the file
	 * @param width         Image width (X dimension)
	 * @param height        Image height (Y dimension)
	 * @param depth         Image depth (Z dimension)
	 * @param pixelType     One of: "uint8", "uint16", "int16", "float32"
	 * @param zeroOneBinary true if the raw data is (0,1) binary and we want (0,255) binary. Ignored if image is not 8-bit.
	 * @param byteOrder     One of: "Little-endian", "Big-endian"
	 * @param spacingX      Voxel spacing in X (mm)
	 * @param spacingY      Voxel spacing in Y (mm)
	 * @param spacingZ      Voxel spacing in Z (mm)
	 * @param datasetService The DatasetService for creating the Dataset
	 * @return A calibrated 3D Dataset backed by a native ImagePlus
	 * @throws IOException if the file cannot be read or is too small
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public static Dataset loadRaw3D(
			File filePath,
			long headerOffset,
			int width,
			int height,
			int depth,
			String pixelType,
			boolean zeroOneBinary,
			String byteOrder,
			double spacingX,
			double spacingY,
			double spacingZ,
			DatasetService datasetService) throws IOException {

		// --- Validate ---
		if (width <= 0 || height <= 0 || depth <= 0) {
			throw new IllegalArgumentException("Dimensions must be positive integers.");
		}
		if (filePath == null || !filePath.exists()) {
			throw new IllegalArgumentException("File does not exist: " + filePath);
		}

		String pt = pixelType.toLowerCase();
		int bytesPerPixel;
		switch (pt) {
			case "uint8":   bytesPerPixel = 1; break;
			case "uint16":  bytesPerPixel = 2; break;
			case "int16":   bytesPerPixel = 2; break;
			case "float32": bytesPerPixel = 4; break;
			default:
				throw new IllegalArgumentException("Unsupported pixel type: " + pixelType);
		}

		long totalPixels = (long) width * height * depth;
		long expectedDataBytes = totalPixels * bytesPerPixel;
		long expectedTotalBytes = headerOffset + expectedDataBytes;

		long actualSize = filePath.length();
		if (actualSize < expectedTotalBytes) {
			throw new IOException(String.format(
					"File too small: %d bytes available, but %d needed (offset=%d + data=%d).",
					actualSize, expectedTotalBytes, headerOffset, expectedDataBytes));
		}

		// --- Read raw bytes, skipping header ---
		byte[] rawData = readRawBytes(filePath, headerOffset, (int) expectedDataBytes);

		// Convert 0/1 binary to 0/255 if requested (for 8-bit only)
		if (zeroOneBinary && bytesPerPixel == 1) {
			for (int i = 0; i < rawData.length; i++) {
				byte byteValue = rawData[i];
				if ((byteValue & ~1) != 0) {
					throw new IllegalArgumentException(
							String.format("Non-binary value detected at index %d: %d (0x%02X)", 
									i, byteValue, byteValue & 0xFF));
				}
				// Convert: 0 -> 0, 1 -> 255 (stored as -1 in signed byte)
				rawData[i] = (byte) ((byteValue * 255) & 0xFF);
			}
		}

		// --- Determine byte order ---
		ByteOrder order = "Big-endian".equalsIgnoreCase(byteOrder)
				? ByteOrder.BIG_ENDIAN
						: ByteOrder.LITTLE_ENDIAN;

		// --- Build Dataset (via Native ImagePlus) ---
		Dataset dataset = buildDataset(rawData, width, height, depth, pt, order, datasetService);

		// --- Apply metadata ---
		dataset.setName(filePath.getName());
		dataset.setAxis(new DefaultLinearAxis(Axes.X, "mm", spacingX), 0);
		dataset.setAxis(new DefaultLinearAxis(Axes.Y, "mm", spacingY), 1);
		dataset.setAxis(new DefaultLinearAxis(Axes.Z, "mm", spacingZ), 2);

		return dataset;
	}

	/**
	 * Reads raw pixel bytes from a file, skipping the header offset.
	 *
	 * @param file        The file to read from
	 * @param headerOffset Number of bytes to skip at the start
	 * @param dataLength  The number of bytes to read after the header
	 * @return The raw byte array containing pixel data
	 * @throws IOException if the file cannot be read or is too small
	 */
	static byte[] readRawBytes(File file, long headerOffset, int dataLength) throws IOException {
		byte[] rawData = new byte[dataLength];
		try (FileInputStream fis = new FileInputStream(file)) {
			long skipped = 0;
			while (skipped < headerOffset) {
				long n = fis.skip(headerOffset - skipped);
				if (n <= 0) {
					throw new IOException("Unexpected end of stream while skipping header.");
				}
				skipped += n;
			}

			int offset = 0;
			while (offset < rawData.length) {
				int nRead = fis.read(rawData, offset, rawData.length - offset);
				if (nRead == -1) {
					throw new IOException(String.format(
							"Unexpected EOF: read %d of %d expected pixel bytes.",
							offset, rawData.length));
				}
				offset += nRead;
			}
		}
		return rawData;
	}

	/**
	 * Overloaded version of {@link #loadRaw3D(File, long, int, int, int, String, boolean, String, double, double, double, DatasetService)}
	 * that does not perform (0,1) to (0,255) binary conversion.
	 *
	 * @param filePath      Path to the raw file on disk
	 * @param headerOffset  Number of bytes to skip at the start of the file
	 * @param width         Image width (X dimension)
	 * @param height        Image height (Y dimension)
	 * @param depth         Image depth (Z dimension)
	 * @param pixelType     One of: "uint8", "uint16", "int16", "float32"
	 * @param byteOrder     One of: "Little-endian", "Big-endian"
	 * @param spacingX      Voxel spacing in X (mm)
	 * @param spacingY      Voxel spacing in Y (mm)
	 * @param spacingZ      Voxel spacing in Z (mm)
	 * @param datasetService The DatasetService for creating the Dataset
	 * @return A calibrated 3D Dataset backed by a native ImagePlus
	 * @throws IOException if the file cannot be read or is too small
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public static Dataset loadRaw3D(
			File filePath,
			long headerOffset,
			int width,
			int height,
			int depth,
			String pixelType,
			String byteOrder,
			double spacingX,
			double spacingY,
			double spacingZ,
			DatasetService datasetService) throws IOException {
		return loadRaw3D(filePath, headerOffset, width, height, depth, pixelType, false, byteOrder, spacingX, spacingY, spacingZ, datasetService);
	}

	/**
	 * Constructs a Dataset by creating a native ImagePlus first, then wrapping it.
	 * This ensures fast access for legacy plugins that expect native byte[] arrays.
	 *
	 * @param rawData       The raw pixel data array
	 * @param width         Image width
	 * @param height        Image height
	 * @param depth         Image depth
	 * @param pixelType     Type string ("uint8", "uint16", "int16", "float32")
	 * @param byteOrder     Byte order for multi-byte types
	 * @param datasetService The DatasetService used for conversion
	 * @return A Dataset backed by a native ImagePlus
	 */
	static Dataset buildDataset(
			byte[] rawData,
			int width, int height, int depth,
			String pixelType,
			ByteOrder byteOrder,
			DatasetService datasetService) {

		ImagePlus imp;

		if ("uint8".equals(pixelType)) {
			// Create native ImageStack with byte[] slices
			ImageStack stack = new ImageStack(width, height);
			int sliceSize = width * height;
			for (int z = 0; z < depth; z++) {
				byte[] slicePixels = new byte[sliceSize];
				System.arraycopy(rawData, z * sliceSize, slicePixels, 0, sliceSize);
				ByteProcessor bp = new ByteProcessor(width, height, slicePixels);
				stack.addSlice("", bp);
			}
			imp = new ImagePlus("", stack);

		} else if ("uint16".equals(pixelType)) {
			// Unpack to short array first
			short[] pixels = new short[rawData.length / 2];
			ByteBuffer.wrap(rawData).order(byteOrder).asShortBuffer().get(pixels);

			ImageStack stack = new ImageStack(width, height);
			int sliceSize = width * height;
			for (int z = 0; z < depth; z++) {
				short[] slicePixels = new short[sliceSize];
				System.arraycopy(pixels, z * sliceSize, slicePixels, 0, sliceSize);
				ShortProcessor sp = new ShortProcessor(width, height, slicePixels, null);
				stack.addSlice("", sp);
			}
			imp = new ImagePlus("", stack);

		} else if ("int16".equals(pixelType)) {
			// Unpack to short array (signed)
			short[] pixels = new short[rawData.length / 2];
			ByteBuffer.wrap(rawData).order(byteOrder).asShortBuffer().get(pixels);

			ImageStack stack = new ImageStack(width, height);
			int sliceSize = width * height;
			for (int z = 0; z < depth; z++) {
				short[] slicePixels = new short[sliceSize];
				System.arraycopy(pixels, z * sliceSize, slicePixels, 0, sliceSize);
				ShortProcessor sp = new ShortProcessor(width, height, slicePixels, null);
				stack.addSlice("", sp);
			}
			imp = new ImagePlus("", stack);

		} else { // float32
			float[] pixels = new float[rawData.length / 4];
			ByteBuffer.wrap(rawData).order(byteOrder).asFloatBuffer().get(pixels);

			ImageStack stack = new ImageStack(width, height);
			int sliceSize = width * height;
			for (int z = 0; z < depth; z++) {
				float[] slicePixels = new float[sliceSize];
				System.arraycopy(pixels, z * sliceSize, slicePixels, 0, sliceSize);
				FloatProcessor fp = new FloatProcessor(width, height, slicePixels);
				stack.addSlice("", fp);
			}
			imp = new ImagePlus("", stack);
		}

		// Convert the native ImagePlus to a Dataset.
		// This preserves the underlying native arrays, enabling fast round-trips.
		ConvertService convertService = datasetService.getContext().getService(ConvertService.class);
		return convertService.convert(imp, Dataset.class);
	}

	/**
	 * Writes the pixel data of a 3D Dataset to a raw binary file.
	 * Uses ArrayDataAccess to get the backing array efficiently (no reflection).
	 * If the Dataset is backed by a native ImagePlus (not an ArrayImg), it falls back
	 * to cursor iteration to write the data correctly.
	 *
	 * @param dataset       The 3D Dataset to save
	 * @param outputFile    The output file path
	 * @param littleEndian  True for little-endian byte order, false for big-endian
	 * @param zeroOneBinary True if writing (0,1) binary data where 1 should be written as 1 byte (not 255)
	 * @throws IOException if the file cannot be written
	 * @throws IllegalArgumentException if the dataset is not 3D
	 * @throws UnsupportedOperationException if the pixel type is unsupported
	 */
	public static void saveAsRaw(Dataset dataset, File outputFile, boolean littleEndian, boolean zeroOneBinary) throws IOException {
		if (dataset.numDimensions() != 3) {
			throw new IllegalArgumentException("Expected 3D Dataset, got " + dataset.numDimensions() + "D");
		}

		final var img = dataset.getImgPlus().getImg();
		final var type = img.firstElement();
		final int bytesPerPixel;

		// Determine bytes per pixel
		if (type instanceof UnsignedByteType || type instanceof ByteType) {
			bytesPerPixel = 1;
		} else if (type instanceof UnsignedShortType || type instanceof ShortType) {
			bytesPerPixel = 2;
		} else if (type instanceof FloatType) {
			bytesPerPixel = 4;
		} else {
			throw new UnsupportedOperationException("Unsupported pixel type: " + type.getClass().getSimpleName());
		}

		boolean fastPathUsed = false;

		System.out.println("img class = " + img.getClass().getName());
		long start = System.nanoTime();
		long end = start;
		
		if (img instanceof ArrayImg) {
			System.out.println("Using fast path for Dataset-> RAW");
			try (FileOutputStream fos = new FileOutputStream(outputFile);
					BufferedOutputStream bos = new BufferedOutputStream(fos)) {

				ArrayImg<?, ?> arrayImg = (ArrayImg<?, ?>) img;
				Object access = arrayImg.update(null);
				System.out.println("access class = " + access.getClass().getName());

				if (access instanceof ByteArray) {
					byte[] storageArray = ((ByteArray) access).getCurrentStorageArray();

					if (zeroOneBinary) {
						for (byte b : storageArray) {
							if (b != 0 && b != -1) {
								throw new IllegalArgumentException("Invalid binary value: " + (b & 0xFF));
							}
							// Write transformed byte immediately
							bos.write(b & 1); 
						}

					} else {
						bos.write(storageArray);
					}
					fastPathUsed = true;
				}
				else if (access instanceof ShortArray) {

					short[] shorts =
							((ShortArray) access).getCurrentStorageArray();

					ByteBuffer buffer =
							ByteBuffer.allocate(shorts.length * 2)
							.order(littleEndian
									? ByteOrder.LITTLE_ENDIAN
											: ByteOrder.BIG_ENDIAN);

					buffer.asShortBuffer().put(shorts);

					fos.write(buffer.array());
					fastPathUsed = true;
				}
				else if (access instanceof FloatArray) {

					float[] floats =
							((FloatArray) access).getCurrentStorageArray();

					ByteBuffer buffer =
							ByteBuffer.allocate(floats.length * 4)
							.order(littleEndian
									? ByteOrder.LITTLE_ENDIAN
											: ByteOrder.BIG_ENDIAN);

					buffer.asFloatBuffer().put(floats);

					fos.write(buffer.array());
					fastPathUsed = true;
				}
			}
			end = System.nanoTime();
		}

		// --- Fallback: Cursor Iteration (if not an ArrayImg or access failed) ---
		if (!fastPathUsed) {
			System.out.println("Writing Dataset to RAW using the slow path");
			try (FileOutputStream fos = new FileOutputStream(outputFile)) {
				fallbackCursorWrite(fos, img, bytesPerPixel, littleEndian, zeroOneBinary);
			}
			end = System.nanoTime();
		}
		System.out.println("RAW file written in "+(long)((end - start) / 1E6)+" ms");
	}

	/**
	 * Fallback method for non-array images.
	 * Iterates pixels and writes them sequentially.
	 * Type checking is performed once before iteration for efficiency.
	 *
	 * @param fos           Output stream to write to
	 * @param img           The image to iterate
	 * @param bytesPerPixel Number of bytes per pixel
	 * @param littleEndian  Endianness flag
	 * @param zeroOneBinary Binary conversion flag
	 * @throws IOException if writing fails
	 */
	private static void fallbackCursorWrite(OutputStream fos,
			RandomAccessibleInterval<?> img,
			int bytesPerPixel,
			boolean littleEndian,
			boolean zeroOneBinary) throws IOException {

		int batchSize = 65536 / bytesPerPixel;
		if (batchSize < 1) batchSize = 1;
		byte[] batchBuffer = new byte[batchSize * bytesPerPixel];

		// Determine the pixel type ONCE before iterating
		Object typeSample = img.firstElement();

		if (typeSample instanceof UnsignedByteType || typeSample instanceof ByteType) {
			write8Bit(fos, img, batchBuffer, zeroOneBinary);
		} else if (typeSample instanceof UnsignedShortType) {
			write16BitUnsigned(fos, img, batchBuffer, littleEndian);
		} else if (typeSample instanceof ShortType) {
			write16BitSigned(fos, img, batchBuffer, littleEndian);
		} else if (typeSample instanceof FloatType) {
			write32BitFloat(fos, img, batchBuffer, littleEndian);
		} else {
			throw new UnsupportedOperationException(
					"Unsupported pixel type in cursor fallback: " + typeSample.getClass().getSimpleName());
		}
	}

	/**
	 * Writes 8-bit pixel data using a cursor.
	 *
	 * @param fos           Output stream
	 * @param img           Image to read
	 * @param batchBuffer   Buffer for batching writes
	 * @param zeroOneBinary Flag for 0/1 binary conversion
	 * @throws IOException if writing fails
	 */
	private static void write8Bit(OutputStream fos, RandomAccessibleInterval<?> img,
			byte[] batchBuffer, boolean zeroOneBinary) throws IOException {

		@SuppressWarnings("unchecked")
		var cursor = ((RandomAccessibleInterval<IntegerType<?>>) img).cursor();
		int batchCount = 0;

		if (zeroOneBinary) {
			while (cursor.hasNext()) {
				cursor.fwd();
				byte b = (byte) (cursor.get().getIntegerLong() & 0xFF);
				if (b != 0 && b != -1) {
					throw new IllegalArgumentException("Invalid binary value: " + (b & 0xFF));
				}
				batchBuffer[batchCount++] = (byte) (b & 1);
				if (batchCount >= batchBuffer.length) {
					fos.write(batchBuffer, 0, batchCount);
					batchCount = 0;
				}
			}
		} else {
			while (cursor.hasNext()) {
				cursor.fwd();
				batchBuffer[batchCount++] = (byte) (cursor.get().getIntegerLong() & 0xFF);
				if (batchCount >= batchBuffer.length) {
					fos.write(batchBuffer, 0, batchCount);
					batchCount = 0;
				}
			}
		}

		if (batchCount > 0) {
			fos.write(batchBuffer, 0, batchCount);
		}
	}

	/**
	 * Writes 16-bit unsigned pixel data.
	 *
	 * @param fos           Output stream
	 * @param img           Image to read
	 * @param batchBuffer   Buffer for batching writes
	 * @param littleEndian  Endianness flag
	 * @throws IOException if writing fails
	 */
	private static void write16BitUnsigned(OutputStream fos, RandomAccessibleInterval<?> img,
			byte[] batchBuffer, boolean littleEndian) throws IOException {

		@SuppressWarnings("unchecked")
		var cursor = ((RandomAccessibleInterval<UnsignedShortType>) img).cursor();
		int batchCount = 0;

		while (cursor.hasNext()) {
			cursor.fwd();
			int val = cursor.get().get();

			if (littleEndian) {
				batchBuffer[batchCount++] = (byte) (val & 0xFF);
				batchBuffer[batchCount++] = (byte) ((val >> 8) & 0xFF);
			} else {
				batchBuffer[batchCount++] = (byte) ((val >> 8) & 0xFF);
				batchBuffer[batchCount++] = (byte) (val & 0xFF);
			}

			if (batchCount >= batchBuffer.length) {
				fos.write(batchBuffer, 0, batchCount);
				batchCount = 0;
			}
		}
		if (batchCount > 0) {
			fos.write(batchBuffer, 0, batchCount);
		}
	}

	/**
	 * Writes 16-bit signed pixel data.
	 *
	 * @param fos           Output stream
	 * @param img           Image to read
	 * @param batchBuffer   Buffer for batching writes
	 * @param littleEndian  Endianness flag
	 * @throws IOException if writing fails
	 */
	private static void write16BitSigned(OutputStream fos, RandomAccessibleInterval<?> img,
			byte[] batchBuffer, boolean littleEndian) throws IOException {

		@SuppressWarnings("unchecked")
		var cursor = ((RandomAccessibleInterval<ShortType>) img).cursor();
		int batchCount = 0;

		while (cursor.hasNext()) {
			cursor.fwd();
			int val = cursor.get().get() & 0xFFFF; // Preserve bit pattern

			if (littleEndian) {
				batchBuffer[batchCount++] = (byte) (val & 0xFF);
				batchBuffer[batchCount++] = (byte) ((val >> 8) & 0xFF);
			} else {
				batchBuffer[batchCount++] = (byte) ((val >> 8) & 0xFF);
				batchBuffer[batchCount++] = (byte) (val & 0xFF);
			}

			if (batchCount >= batchBuffer.length) {
				fos.write(batchBuffer, 0, batchCount);
				batchCount = 0;
			}
		}
		if (batchCount > 0) {
			fos.write(batchBuffer, 0, batchCount);
		}
	}

	/**
	 * Writes 32-bit float pixel data.
	 *
	 * @param fos           Output stream
	 * @param img           Image to read
	 * @param batchBuffer   Buffer for batching writes
	 * @param littleEndian  Endianness flag
	 * @throws IOException if writing fails
	 */
	private static void write32BitFloat(OutputStream fos, RandomAccessibleInterval<?> img,
			byte[] batchBuffer, boolean littleEndian) throws IOException {

		@SuppressWarnings("unchecked")
		var cursor = ((RandomAccessibleInterval<FloatType>) img).cursor();
		int batchCount = 0;

		while (cursor.hasNext()) {
			cursor.fwd();
			int bits = Float.floatToIntBits(cursor.get().get());

			if (littleEndian) {
				batchBuffer[batchCount++] = (byte) (bits);
				batchBuffer[batchCount++] = (byte) (bits >> 8);
				batchBuffer[batchCount++] = (byte) (bits >> 16);
				batchBuffer[batchCount++] = (byte) (bits >> 24);
			} else {
				batchBuffer[batchCount++] = (byte) (bits >> 24);
				batchBuffer[batchCount++] = (byte) (bits >> 16);
				batchBuffer[batchCount++] = (byte) (bits >> 8);
				batchBuffer[batchCount++] = (byte) (bits);
			}

			if (batchCount >= batchBuffer.length) {
				fos.write(batchBuffer, 0, batchCount);
				batchCount = 0;
			}
		}
		if (batchCount > 0) {
			fos.write(batchBuffer, 0, batchCount);
		}
	}

	/**
	 * Convenience method to save a Dataset to a raw file with default little-endian byte order.
	 *
	 * @param dataset     The 3D Dataset to save
	 * @param outputFile  The output file path
	 * @param littleEndian Byte order flag
	 * @throws IOException if the file cannot be written
	 */
	public static void saveAsRaw(Dataset dataset, File outputFile, boolean littleEndian) throws IOException {
		saveAsRaw(dataset, outputFile, littleEndian, false);
	}
	
	/**
	 * Convenience method to save a Dataset to a raw file with default little-endian byte order and no binary conversion.
	 *
	 * @param dataset     The 3D Dataset to save
	 * @param outputFile  The output file path
	 * @throws IOException if the file cannot be written
	 */
	public static void saveAsRaw(Dataset dataset, File outputFile) throws IOException {
		saveAsRaw(dataset, outputFile, true, false);
	}
	
	/**
	 * Converts a Dataset to a native ImagePlus backed by in-memory primitive arrays.
	 *
	 * @param dataset       The Dataset to convert
	 * @param convertService The SciJava ConvertService to perform the conversion
	 * @return A legacy ImagePlus backed by in-memory ImageStack primitive arrays.
	 */
	public static ImagePlus toImagePlus(Dataset dataset, ConvertService convertService) {
		ImagePlus imp = convertService.convert(dataset, ImagePlus.class);
		if (!ImagePlusUtil.isNativeStack(imp))
			imp = imp.duplicate();
		imp.setTitle(dataset.getName());
		return imp;
	}
	
	/**
	 * Converts a Dataset to a native ImagePlus using a Context to retrieve the ConvertService.
	 *
	 * @param dataset   The Dataset to convert
	 * @param context   The SciJava Context
	 * @return A legacy ImagePlus backed by in-memory ImageStack primitive arrays.
	 */
	public static ImagePlus toImagePlus(Dataset dataset, Context context) {
		return toImagePlus(dataset, context.getService(ConvertService.class));
	}
	
	/**
	 * Converts a Dataset to a native ImagePlus using a DatasetService to retrieve the Context and ConvertService.
	 *
	 * @param dataset       The Dataset to convert
	 * @param datasetService The DatasetService providing the Context
	 * @return A legacy ImagePlus backed by in-memory ImageStack primitive arrays.
	 */
	public static ImagePlus toImagePlus(Dataset dataset, DatasetService datasetService) {
		return toImagePlus(dataset, datasetService.getContext());
	}
	
}