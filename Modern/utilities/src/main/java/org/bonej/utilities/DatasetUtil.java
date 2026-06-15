package org.bonej.utilities;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.array.ArrayImgs;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import io.scif.services.DatasetIOService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Load Raw 3D Image")
public class DatasetUtil implements Command {

    @Parameter(label = "File Path", required = true, style = "file")
    private File filePath;
    
    @Parameter(type = ItemIO.OUTPUT)
    private Dataset dataset;

    @Parameter(label = "Header Offset (bytes)", min = "0")
    private long headerOffset = 0;

    @Parameter(label = "Width (X)", required = true, min = "1")
    private int width;

    @Parameter(label = "Height (Y)", required = true, min = "1")
    private int height;

    @Parameter(label = "Depth (Z)", required = true, min = "1")
    private int depth;

    @Parameter(label = "Pixel Type", choices = {"uint8", "uint16", "int16", "float32"}, required = true)
    private String pixelTypeStr;

    // NEW: Explicit byte order selection
    @Parameter(label = "Byte Order", choices = {"Little-endian", "Big-endian"}, required = true)
    private String byteOrderStr;

    @Parameter(label = "Spacing X (mm)", stepSize = "0.00001")
    private double spacingX = 1.0;

    @Parameter(label = "Spacing Y (mm)", stepSize = "0.00001")
    private double spacingY = 1.0;

    @Parameter(label = "Spacing Z (mm)", stepSize = "0.00001")
    private double spacingZ = 1.0;

    @Parameter
    private DatasetService datasetService;
    
    @Parameter
    private DatasetIOService datasetIOService;
    
    @Parameter
    private UIService uiService;

    @Override
    public void run() {
        try {
            // --- Validate ---
            if (width <= 0 || height <= 0 || depth <= 0) {
                throw new IllegalArgumentException("Dimensions must be positive integers.");
            }

            int bytesPerPixel;
            switch (pixelTypeStr.toLowerCase()) {
                case "uint8":   bytesPerPixel = 1; break;
                case "uint16":  bytesPerPixel = 2; break;
                case "int16":   bytesPerPixel = 2; break;
                case "float32": bytesPerPixel = 4; break;
                default:
                    throw new IllegalArgumentException("Unsupported pixel type: " + pixelTypeStr);
            }

            long totalPixels = (long) width * height * depth;
            long expectedDataBytes = totalPixels * bytesPerPixel;
            long expectedTotalBytes = headerOffset + expectedDataBytes;

            File f = filePath;
            long actualSize = f.length();
            if (actualSize < expectedTotalBytes) {
                throw new IOException(String.format(
                    "File too small: %d bytes available, but %d needed (offset=%d + data=%d).",
                    actualSize, expectedTotalBytes, headerOffset, expectedDataBytes));
            }

            // --- Read raw bytes, skipping header ---
            byte[] rawData = new byte[(int) expectedDataBytes];
            try (FileInputStream fis = new FileInputStream(f)) {
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

            // Determine ByteOrder enum from string
            ByteOrder order = "Big-endian".equals(byteOrderStr) 
                ? ByteOrder.BIG_ENDIAN 
                : ByteOrder.LITTLE_ENDIAN;

            // --- Build ImgLib2 image AND create Dataset in the same branch ---
            String typeName = pixelTypeStr.toUpperCase();

            if ("uint8".equals(pixelTypeStr.toLowerCase())) {
                var img = ArrayImgs.unsignedBytes(rawData, width, height, depth);
                dataset = datasetService.create(img);
            } else if ("uint16".equals(pixelTypeStr.toLowerCase())) {
                short[] pixels = new short[rawData.length / 2];
                ByteBuffer.wrap(rawData)
                    .order(order) 
                    .asShortBuffer()
                    .get(pixels);
                var img = ArrayImgs.unsignedShorts(pixels, width, height, depth);
                dataset = datasetService.create(img);
            } else if ("int16".equals(pixelTypeStr.toLowerCase())) {
                short[] pixels = new short[rawData.length / 2];
                ByteBuffer.wrap(rawData)
                    .order(order)
                    .asShortBuffer()
                    .get(pixels);
                var img = ArrayImgs.shorts(pixels, width, height, depth);
                dataset = datasetService.create(img);
            } else { // float32
                float[] pixels = new float[rawData.length / 4];
                ByteBuffer.wrap(rawData)
                    .order(order)
                    .asFloatBuffer()
                    .get(pixels);
                var img = ArrayImgs.floats(pixels, width, height, depth);
                dataset = datasetService.create(img);
            }

            dataset.setName(filePath.getName());
            
            // --- Apply spatial calibration ---
            dataset.setAxis(new DefaultLinearAxis(Axes.X, "mm", spacingX), 0);
            dataset.setAxis(new DefaultLinearAxis(Axes.Y, "mm", spacingY), 1);
            dataset.setAxis(new DefaultLinearAxis(Axes.Z, "mm", spacingZ), 2);
            
            if (uiService != null && uiService.isVisible()) {
                uiService.show(dataset);
            }

            System.out.println("Loaded 3D image: " + width + "×" + height + "×" + depth
                + " (" + typeName + ")");
            System.out.println("  Header offset: " + headerOffset + " bytes");
            System.out.println("  Byte order: " + byteOrderStr);
            System.out.printf("  Spacing: x=%.5f, y=%.5f, z=%.5f mm%n", spacingX, spacingY, spacingZ);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load raw 3D image: " + e.getMessage(), e);
        }
    }
}