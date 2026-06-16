package org.bonej.utilities;

import net.imagej.Dataset;
import net.imagej.DatasetService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

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
 * Note: Full integration tests require a real ImageJ context.
 * These tests focus on validation, file I/O logic, and byte manipulation.
 */
public class DatasetUtilTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private DatasetService mockDatasetService;

    @Before
    public void setUp() {
        // Mock the service to avoid needing a full ImageJ context for basic tests
        mockDatasetService = mock(DatasetService.class);
        
        // Since we can't easily create a real Dataset without an ImageJ instance,
        // we will test the static helper methods that don't strictly require it
        // or use mocks where necessary.
        // For buildDataset, we expect it to fail if called without a working service,
        // so we'll primarily test readRawBytes and validation logic here.
    }

    @After
    public void tearDown() {
        mockDatasetService = null;
    }

    /**
     * Test that loadRaw3D throws IllegalArgumentException for invalid dimensions.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLoadRaw3D_InvalidWidth() throws Exception {
        File dummy = tempFolder.newFile("dummy.raw");
        dummy.deleteOnExit(); // Just needs to exist
        
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

    /**
     * Test that loadRaw3D throws IllegalArgumentException for non-existent file.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLoadRaw3D_NonExistentFile() throws Exception {
        File nonexistent = new File("/nonexistent/dummy.raw");
        DatasetUtil.loadRaw3D(
            nonexistent, 0, 10, 10, 10, "uint8", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
        );
    }

    /**
     * Test that loadRaw3D throws IllegalArgumentException for unsupported pixel type.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testLoadRaw3D_UnsupportedPixelType() throws Exception {
        File file = tempFolder.newFile("test.raw");
        Files.write(file.toPath(), new byte[100]);
        
        DatasetUtil.loadRaw3D(
            file, 0, 5, 5, 4, "invalid_type", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
        );
    }

    /**
     * Test that loadRaw3D throws IOException when file is too small.
     */
    @Test(expected = IOException.class)
    public void testLoadRaw3D_FileTooSmall() throws Exception {
        // Need 10x10x10 uint8 = 1000 bytes. File has only 10.
        byte[] smallData = new byte[10];
        File file = writeDataToFile(smallData, "small.raw");
        
        DatasetUtil.loadRaw3D(
            file, 0, 10, 10, 10, "uint8", "Little-endian", 1.0, 1.0, 1.0, mockDatasetService
        );
    }

    /**
     * Test readRawBytes reads correctly with no offset.
     */
    @Test
    public void testReadRawBytes_NoOffset() throws Exception {
        byte[] expected = {1, 2, 3, 4, 5};
        File file = writeDataToFile(expected, "read_test.raw");
        
        byte[] result = DatasetUtil.readRawBytes(file, 0, 5);
        
        assertArrayEquals("Data should match exactly", expected, result);
    }

    /**
     * Test readRawBytes skips header correctly.
     */
    @Test
    public void testReadRawBytes_WithHeaderOffset() throws Exception {
        byte[] header = {-1, -2, -3, 4, 5}; // Header data
        byte[] payload = {10, 20, 30};      // Data to read
        
        byte[] combined = new byte[header.length + payload.length];
        System.arraycopy(header, 0, combined, 0, header.length);
        System.arraycopy(payload, 0, combined, header.length, payload.length);
        
        File file = writeDataToFile(combined, "offset_test.raw");
        
        byte[] result = DatasetUtil.readRawBytes(file, header.length, payload.length);
        
        assertArrayEquals("Payload should be read after skipping header", payload, result);
        
        // Verify header was skipped
        assertNotEquals("Header content should not be in result", 
                        header[0], result[0]);
    }

    /**
     * Test readRawBytes handles partial reads correctly (simulated by large buffer).
     */
    @Test
    public void testReadRawBytes_LargeBlock() throws Exception {
        int size = 1024;
        byte[] data = new byte[size];
        for(int i=0; i<size; i++) data[i] = (byte)(i % 256);
        
        File file = writeDataToFile(data, "large_block.raw");
        
        byte[] result = DatasetUtil.readRawBytes(file, 0, size);
        
        assertArrayEquals("Large block read failed", data, result);
    }

    /**
     * Test that endianness conversion works as expected in a standalone scenario.
     * This verifies the ByteBuffer logic used in buildDataset.
     */
    @Test
    public void testEndiannessConversion() {
        // Test Short (uint16/int16)
        short[] shorts = {1, 258, 513}; // 0x0001, 0x0102, 0x0201
        ByteBuffer leBuffer = ByteBuffer.allocate(shorts.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer beBuffer = ByteBuffer.allocate(shorts.length * 2).order(ByteOrder.BIG_ENDIAN);

        leBuffer.asShortBuffer().put(shorts);
        beBuffer.asShortBuffer().put(shorts);

        byte[] leBytes = leBuffer.array();
        byte[] beBytes = beBuffer.array();

        // Value 1 (0x0001): LE=[0x01, 0x00], BE=[0x00, 0x01]
        assertEquals((byte) 1, leBytes[0]);
        assertEquals((byte) 0, beBytes[0]);

        // Value 258 (0x0102): LE=[0x02, 0x01], BE=[0x01, 0x02]
        assertEquals((byte) 2, leBytes[2]);
        assertEquals((byte) 1, leBytes[3]);
        assertEquals((byte) 1, beBytes[2]);
        assertEquals((byte) 2, beBytes[3]);

        // Test Float (float32)
        float[] floats = {1.0f, 2.0f};
        ByteBuffer leF = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer beF = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.BIG_ENDIAN);

        leF.asFloatBuffer().put(floats);
        beF.asFloatBuffer().put(floats);

        byte[] leFBytes = leF.array();
        byte[] beFBytes = beF.array();

        assertNotEquals("Floats should have different byte orders",
                Arrays.hashCode(leFBytes), Arrays.hashCode(beFBytes));
    }

    /**
     * Test saveAsRaw method existence and basic parameter handling.
     * Actual functionality requires a populated Dataset which is hard to mock without ImageJ.
     */
    @Test
    public void testSaveAsRaw_MethodExists() throws Exception {
        // Verify the method signature exists
        DatasetUtil.class.getMethod("saveAsRaw", Dataset.class, File.class, boolean.class);
        DatasetUtil.class.getMethod("saveAsRaw", Dataset.class, File.class);
    }

    // Helper to create a test file with data
    private File writeDataToFile(byte[] data, String filename) throws Exception {
        File file = tempFolder.newFile(filename);
        java.nio.file.Files.write(file.toPath(), data);
        return file;
    }
}