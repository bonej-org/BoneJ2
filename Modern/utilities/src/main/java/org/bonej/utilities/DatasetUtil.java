package org.bonej.utilities;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.numeric.RealType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for loading raw binary 3D image data into ImageJ2 Datasets.
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
	 * @param byteOrder     One of: "Little-endian", "Big-endian"
	 * @param spacingX      Voxel spacing in X (mm)
	 * @param spacingY      Voxel spacing in Y (mm)
	 * @param spacingZ      Voxel spacing in Z (mm)
	 * @param datasetService The DatasetService for creating the Dataset
	 * @return A calibrated 3D Dataset
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

		// --- Determine byte order ---
		ByteOrder order = "Big-endian".equalsIgnoreCase(byteOrder)
				? ByteOrder.BIG_ENDIAN
						: ByteOrder.LITTLE_ENDIAN;

		// --- Build Dataset ---
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
	 * Constructs a Dataset from raw bytes based on pixel type.
	 * Each branch uses the concrete type so Java infers generics correctly.
	 */
	static Dataset buildDataset(
			byte[] rawData,
			int width, int height, int depth,
			String pixelType,
			ByteOrder byteOrder,
			DatasetService datasetService) {

		Dataset dataset;

		if ("uint8".equals(pixelType)) {
			var img = ArrayImgs.unsignedBytes(rawData, width, height, depth);
			dataset = datasetService.create(img);

		} else if ("uint16".equals(pixelType)) {
			short[] pixels = new short[rawData.length / 2];
			ByteBuffer.wrap(rawData).order(byteOrder).asShortBuffer().get(pixels);
			var img = ArrayImgs.unsignedShorts(pixels, width, height, depth);
			dataset = datasetService.create(img);

		} else if ("int16".equals(pixelType)) {
			short[] pixels = new short[rawData.length / 2];
			ByteBuffer.wrap(rawData).order(byteOrder).asShortBuffer().get(pixels);
			var img = ArrayImgs.shorts(pixels, width, height, depth);
			dataset = datasetService.create(img);

		} else { // float32
			float[] pixels = new float[rawData.length / 4];
			ByteBuffer.wrap(rawData).order(byteOrder).asFloatBuffer().get(pixels);
			var img = ArrayImgs.floats(pixels, width, height, depth);
			dataset = datasetService.create(img);
		}

		return dataset;
	}

	/**
	 * Writes the pixel data of a 3D Dataset to a raw binary file.
	 * Uses ArrayDataAccess to get the backing array efficiently (no reflection).
	 *
	 * @param dataset      The source 3D Dataset.
	 * @param outputFile   The destination file path.
	 * @param littleEndian If true, writes as Little-Endian (default). If false, writes Big-Endian.
	 */
	public static void saveAsRaw(Dataset dataset, File outputFile, boolean littleEndian) throws IOException {
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

		// --- Fast Path: Use ArrayDataAccess to get the backing array ---
		if (img instanceof ArrayImg && img instanceof ArrayDataAccess) {
			try (FileOutputStream fos = new FileOutputStream(outputFile)) {
				ArrayDataAccess<?> access = (ArrayDataAccess<?>) img;
				Object storage = access.getCurrentStorageArray();

				if (storage instanceof byte[]) {
					fos.write((byte[]) storage);
					fastPathUsed = true;
				} else if (storage instanceof short[]) {
					short[] shorts = (short[]) storage;
					ByteBuffer buffer = ByteBuffer.allocate(shorts.length * 2)
							.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
					buffer.asShortBuffer().put(shorts);
					fos.write(buffer.array());
					fastPathUsed = true;
				} else if (storage instanceof float[]) {
					float[] floats = (float[]) storage;
					ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4)
							.order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
					buffer.asFloatBuffer().put(floats);
					fos.write(buffer.array());
					fastPathUsed = true;
				}
			}
		}

		// --- Fallback: Cursor Iteration (if not an ArrayImg or access failed) ---
		if (!fastPathUsed) {
			try (FileOutputStream fos = new FileOutputStream(outputFile)) {
				fallbackCursorWrite(fos, img, bytesPerPixel, littleEndian);
			}
		}
	}

	/**
	 * Fallback method for non-array images.
	 * Iterates pixels and writes them sequentially.
	 */
	private static void fallbackCursorWrite(OutputStream fos, 
			RandomAccessibleInterval<?> img, 
			int bytesPerPixel, 
			boolean littleEndian) throws IOException {

		int batchSize = 65536 / bytesPerPixel; 
		if (batchSize < 1) batchSize = 1;
		byte[] batchBuffer = new byte[batchSize * bytesPerPixel];
		int batchCount = 0;

		// Cursor does NOT implement AutoCloseable
		var cursor = img.cursor();

		try {
			while (cursor.hasNext()) {
				cursor.fwd();
				Object pixelObj = cursor.get();

				if (bytesPerPixel == 1) {
					// For 8-bit, we need an integer value between 0-255 (or -128 to 127)
					// Use getLong() from IntegerType which handles both signed/unsigned
					long val = -1;

					if (pixelObj instanceof IntegerType<?>) {
						val = ((IntegerType<?>) pixelObj).getIntegerLong();
						// Mask to ensure we get the lower 8 bits correctly for unsigned display if needed
						// But usually raw writes just want the bits.
						batchBuffer[batchCount++] = (byte) (val & 0xFF);
					} else if (pixelObj instanceof RealType<?>) {
						double dVal = ((RealType<?>) pixelObj).getRealDouble();
						batchBuffer[batchCount++] = (byte) ((int) dVal & 0xFF);
					} else {
						throw new IllegalStateException("Unknown pixel type for 8-bit conversion");
					}

				} else if (bytesPerPixel == 2) {
					// 16-bit short
					long val = -1;
					if (pixelObj instanceof IntegerType<?>) {
						val = ((IntegerType<?>) pixelObj).getIntegerLong();
					} else if (pixelObj instanceof RealType<?>) {
						double dVal = ((RealType<?>) pixelObj).getRealDouble();
						val = (long) dVal;
					} else {
						throw new IllegalStateException("Unknown pixel type for 16-bit conversion");
					}

					// Ensure we treat it as unsigned 16-bit for writing? 
					// Actually, we just write the bits.
					int iv = (int) (val & 0xFFFF);

					if (littleEndian) {
						batchBuffer[batchCount++] = (byte) (iv & 0xFF);
						batchBuffer[batchCount++] = (byte) ((iv >> 8) & 0xFF);
					} else {
						batchBuffer[batchCount++] = (byte) ((iv >> 8) & 0xFF);
						batchBuffer[batchCount++] = (byte) (iv & 0xFF);
					}

				} else if (bytesPerPixel == 4) {
					// 32-bit float
					float fVal = 0f;
					if (pixelObj instanceof FloatType) {
						fVal = ((FloatType) pixelObj).get();
					} else if (pixelObj instanceof RealType<?>) {
						fVal = (float) ((RealType<?>) pixelObj).getRealDouble();
					} else if (pixelObj instanceof IntegerType<?>) {
						fVal = ((IntegerType<?>) pixelObj).getIntegerLong();
					} else {
						throw new IllegalStateException("Unknown pixel type for 32-bit conversion");
					}

					int bits = Float.floatToIntBits(fVal);
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
				}

				if (batchCount >= batchBuffer.length) {
					fos.write(batchBuffer, 0, batchCount);
					batchCount = 0;
				}
			}

			if (batchCount > 0) {
				fos.write(batchBuffer, 0, batchCount);
			}
		} finally {
			// No explicit close needed for Cursor
			cursor = null;
		}
	}

	public static void saveAsRaw(Dataset dataset, File outputFile) throws IOException {
		saveAsRaw(dataset, outputFile, true);
	}
}