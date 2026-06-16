package org.bonej.utilities;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

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
	 * @param zeroOneBinary true if the raw data is (0,1) binary and we want (0,255) binary
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

		if (bytesPerPixel != 1 && zeroOneBinary) {
			throw new IllegalArgumentException("Unexpected (0,1) binary image flag set for image of type " + pixelType);
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
		
		//multiply 1s by 255 so that we have an ImageJ convention (0,255) binary image
		if (zeroOneBinary) {
			for (int i = 0; i < expectedDataBytes; i++) {
				byte byteValue = rawData[i];
				if ((byteValue & ~1) != 0) {
		            // Provide context about the bad value
		            throw new IllegalArgumentException(
		                String.format("Non-binary value detected at index %d: %d (0x%02X)", 
		                              i, byteValue, byteValue & 0xFF));
		        }
		        
		        // Convert: 0 -> 0, 1 -> 255 (stored as -1)
		        rawData[i] = (byte) ((byteValue * 255) & 0xFF);
		    }
		}

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
	 * Overloaded version of loadRaw3D that does no (0,1) -> (0,255) binary conversion.
	 * 
	 * @param filePath
	 * @param headerOffset
	 * @param width
	 * @param height
	 * @param depth
	 * @param pixelType
	 * @param byteOrder
	 * @param spacingX
	 * @param spacingY
	 * @param spacingZ
	 * @param datasetService
	 * @return
	 * @throws IOException
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

		System.out.println("img class = " + img.getClass().getName());
		long start = System.nanoTime();
		long end = start;
		if (img instanceof ArrayImg) {
			System.out.println("Using fast path for Dataset-> RAW");
			try (FileOutputStream fos = new FileOutputStream(outputFile)) {

				ArrayImg<?, ?> arrayImg = (ArrayImg<?, ?>) img;
				Object access = arrayImg.update(null);
				System.out.println("access class = " + access.getClass().getName());

				if (access instanceof ByteArray) {
					fos.write(((ByteArray) access).getCurrentStorageArray());
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
				fallbackCursorWrite(fos, img, bytesPerPixel, littleEndian);
			}
			end = System.nanoTime();
		}
		System.out.println("RAW file written in "+(long)((end - start) / 1E6)+" ms");
	}

	/**
	 * Fallback method for non-array images.
	 * Iterates pixels and writes them sequentially.
	 * Type checking is performed once before iteration for efficiency.
	 */
	private static void fallbackCursorWrite(OutputStream fos,
			RandomAccessibleInterval<?> img,
			int bytesPerPixel,
			boolean littleEndian) throws IOException {

		int batchSize = 65536 / bytesPerPixel;
		if (batchSize < 1) batchSize = 1;
		byte[] batchBuffer = new byte[batchSize * bytesPerPixel];

		// Determine the pixel type ONCE before iterating
		Object typeSample = img.firstElement();

		if (typeSample instanceof UnsignedByteType || typeSample instanceof ByteType) {
			write8Bit(fos, img, batchBuffer);
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

	private static void write8Bit(OutputStream fos, RandomAccessibleInterval<?> img, byte[] batchBuffer)
			throws IOException {

		@SuppressWarnings("unchecked")
		var cursor = ((RandomAccessibleInterval<IntegerType<?>>) img).cursor();
		int batchCount = 0;

		while (cursor.hasNext()) {
			cursor.fwd();
			batchBuffer[batchCount++] = (byte) (cursor.get().getIntegerLong() & 0xFF);

			if (batchCount >= batchBuffer.length) {
				fos.write(batchBuffer, 0, batchCount);
				batchCount = 0;
			}
		}
		if (batchCount > 0) {
			fos.write(batchBuffer, 0, batchCount);
		}
	}

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

	public static void saveAsRaw(Dataset dataset, File outputFile) throws IOException {
		saveAsRaw(dataset, outputFile, true);
	}
}