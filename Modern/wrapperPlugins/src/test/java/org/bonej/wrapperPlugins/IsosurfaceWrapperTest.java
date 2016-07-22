package org.bonej.wrapperPlugins;

import ij.measure.ResultsTable;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.ops.geom.geom3d.mesh.DefaultMesh;
import net.imagej.ops.geom.geom3d.mesh.Facet;
import net.imagej.ops.geom.geom3d.mesh.TriangularFacet;
import net.imagej.ops.geom.geom3d.mesh.Vertex;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.bonej.utilities.ResultsInserter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.bonej.wrapperPlugins.IsosurfaceWrapper.STL_WRITE_WARNING;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.ERROR_MESSAGE;

/**
 * Tests for {@link IsosurfaceWrapper}
 *
 * @author Richard Domander
 */
public class IsosurfaceWrapperTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @BeforeClass
    public static void oneTimeSetup() {
        ResultsInserter.getInstance().setHeadless(true);
    }

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @After
    public void tearDown() {
        ResultsInserter.getInstance().getResultsTable().reset();
    }

    @Test
    public void testNullImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void test2DImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void testNonBinaryImageCancelsIsosurface() throws Exception {
        CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J, IsosurfaceWrapper.class);
    }

    @Test
    public void testNoCalibrationShowsWarning() throws Exception {
        CommonWrapperTests.testNoCalibrationShowsWarning(IMAGE_J, IsosurfaceWrapper.class, "exportSTL", false);
    }

    @Test
    public void testResults() throws Exception {
        // Marching cubes creates an octahedron out of a unit cube
        // Calculate the length of the side of the octahedron with Pythagoras' theorem
        final double side = Math.sqrt(0.5 * 0.5 + 0.5 * 0.5);
        final double height = 0.5;
        final double pyramidFaces = 2 * side * Math.sqrt(side * side / 4.0 + height * height);
        final double expectedArea = pyramidFaces * 2;
        final String unit = "mm";
        final double[] expectedValues = {0, expectedArea, expectedArea, 0};

        /*
         * Create a hyperstack with two channels and two frames.
         * Two of the 3D subspaces are empty, and two of them contain a unit cube (single voxel)
         */
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit);
        final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
        final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
        final Img<BitType> img = ArrayImgs.bits(1, 1, 1, 2, 2);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, cAxis, tAxis);
        final RandomAccess<BitType> access = imgPlus.randomAccess();
        // Add a voxel to Channel 1, Frame 0
        access.setPosition(new long[]{0, 0, 0, 1, 0});
        access.get().setOne();
        // Add a voxel to Channel 0, Frame 1
        access.setPosition(new long[]{0, 0, 0, 0, 1});
        access.get().setOne();

        // Run command and get results
        IMAGE_J.command().run(IsosurfaceWrapper.class, true, "inputImage", imgPlus, "exportSTL", false).get();
        final ResultsTable resultsTable = ResultsInserter.getInstance().getResultsTable();
        final String[] headings = resultsTable.getHeadings();

        // Assert table size
        assertEquals("Wrong number of columns", 2, headings.length);
        assertEquals("Wrong number of rows", expectedValues.length, resultsTable.size());

        // Assert column headers
        assertEquals("Column header is incorrect", "Surface area (" + unit + "²)", headings[1]);

        // Assert results
        for (int row = 0; row < expectedValues.length; row++) {
            for (int column = 1; column < headings.length; column++) {
                final double value = resultsTable.getValue(headings[column], row);
                assertEquals("Incorrect surface area", expectedValues[row], value, 1e-12);
            }
        }
    }

    @Test(expected = NullPointerException.class)
    public void testWriteBinarySTLFileNullMeshThrowsNPE() throws Exception {
        IsosurfaceWrapper.writeBinarySTLFile("Mesh", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteBinarySTLFileNullNameThrowsIAE() throws Exception {
        final DefaultMesh mesh = new DefaultMesh();

        IsosurfaceWrapper.writeBinarySTLFile(null, mesh);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteBinarySTLFileEmptyNameThrowsIAE() throws Exception {
        final DefaultMesh mesh = new DefaultMesh();

        IsosurfaceWrapper.writeBinarySTLFile("", mesh);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteBinarySTLFileNonTriangularMeshThrowsIAE() throws Exception {
        final DefaultMesh mesh = mock(DefaultMesh.class);
        when(mesh.triangularFacets()).thenReturn(false);

        IsosurfaceWrapper.writeBinarySTLFile("Mesh", mesh);
    }

    @Test
    public void testWriteBinarySTLFile() throws Exception {
        // Create test mesh
        final DefaultMesh mesh = new DefaultMesh();
        mesh.addFace(
                new TriangularFacet(new Vertex(1.0, 0.0, 0.0), new Vertex(0.0, 1.0, 0.0), new Vertex(0.0, 0.0, 0.0)));
        mesh.addFace(
                new TriangularFacet(new Vertex(0.0, 0.0, 1.0), new Vertex(0.0, 1.0, 0.0), new Vertex(0.0, 0.0, 0.0)));
        final int expectedLength = 80 + 4 + mesh.getFacets().size() * 50;

        // Write test mesh to a file
        final String filePath = "./test_file.stl";
        IsosurfaceWrapper.writeBinarySTLFile(filePath, mesh);

        // Read and delete the test file
        final Path path = Paths.get(filePath);
        final byte[] bytes = Files.readAllBytes(path);
        Files.delete(path);

        // Assert that the STL file is valid and matches the mesh
        assertEquals("Size of STL file is incorrect", expectedLength, bytes.length);

        final String header = new String(Arrays.copyOfRange(bytes, 0, 80));
        assertEquals("File header is incorrect", IsosurfaceWrapper.STL_HEADER, header);

        final int numFacets = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertEquals("Wrong number of facets in the file", 2, numFacets);

        final List<Facet> facets = mesh.getFacets();
        int offset = 84;
        for (Facet facet : facets) {
            final TriangularFacet triangularFacet = (TriangularFacet) facet;
            assertVector3DEquals("Normal is incorrect", triangularFacet.getNormal(), readVector3D(bytes, offset));
            assertVector3DEquals("Vertex is incorrect", triangularFacet.getP0(), readVector3D(bytes, offset + 12));
            assertVector3DEquals("Vertex is incorrect", triangularFacet.getP1(), readVector3D(bytes, offset + 24));
            assertVector3DEquals("Vertex is incorrect", triangularFacet.getP2(), readVector3D(bytes, offset + 36));
            final short attrByteCount =
                    ByteBuffer.wrap(bytes, offset + 48, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
            assertEquals("Attribute byte count is incorrect", 0, attrByteCount);
            offset += 50;
        }
    }

    @Test
    public void testFileWritingExceptionsShowErrorDialog() throws Exception {
        // Mock a bad file
        final File badFile = mock(File.class);
        // Giving the file a path of a directory causes an exception to be thrown
        when(badFile.getAbsolutePath()).thenReturn("/.stl/");

        // Create a test image
        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "mm");
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "mm");
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "mm");
        final Img<BitType> img = ArrayImgs.bits(1, 1, 1);
        final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis);

        // Mock UI
        final UserInterface mockUI = mock(UserInterface.class);
        final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
        when(mockUI.dialogPrompt(startsWith(STL_WRITE_WARNING), anyString(), eq(ERROR_MESSAGE), any()))
                .thenReturn(mockPrompt);
        when(mockUI.chooseFile(any(File.class), anyString())).thenReturn(badFile);
        IMAGE_J.ui().setDefaultUI(mockUI);

        // Run plugin
        IMAGE_J.command().run(IsosurfaceWrapper.class, true, "inputImage", imgPlus, "exportSTL", true).get();

        // Verify that correct dialog got shown
        verify(mockUI, after(100).times(1))
                .dialogPrompt(startsWith(STL_WRITE_WARNING), anyString(), eq(ERROR_MESSAGE), any());
    }

    // -- Helper methods --
    private void assertVector3DEquals(final String message, final Vector3D expected, final Vector3D result)
            throws AssertionError {
        if (Double.compare(expected.getX(), result.getX()) != 0 ||
                Double.compare(expected.getY(), result.getY()) != 0 ||
                Double.compare(expected.getZ(), result.getZ()) != 0) {
            throw new AssertionError(message);
        }
    }

    private Vector3D readVector3D(byte[] bytes, int offset) {
        final float x = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        final float y = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        final float z = ByteBuffer.wrap(bytes, offset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();

        return new Vector3D(x, y, z);
    }
}