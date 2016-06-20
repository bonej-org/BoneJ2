package org.bonej.ops.connectivity;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.type.logic.BitType;
import org.bonej.testImages.Cuboid;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the {@link EulerContribution EulerContribution} Op
 *
 * @author Richard Domander 
 */
public class EulerContributionTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    /** Regression test EulerCharacteristic with a solid cuboid that never touches the edges of the stack */
    @Test
    public void testCompute1CuboidFreeFloat() throws Exception {
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(Cuboid.class, null, 10, 10, 10, 1, 1, 5);
        final EulerContribution.Traverser<BitType> traverser = new EulerContribution.Traverser<>(cuboid);

        final int vertices = EulerContribution.stackCorners(traverser);
        assertEquals("Number of stack vertices is incorrect", 0, vertices);

        final long edges = EulerContribution.stackEdges(traverser);
        assertEquals("Number stack edge voxels is incorrect", 0, edges);

        final int faces = EulerContribution.stackFaces(traverser);
        assertEquals("Number stack face voxels is incorrect", 0, faces);

        final long voxelVertices = EulerContribution.voxelVertices(traverser);
        assertEquals("Number of voxel vertices is incorrect", 0, voxelVertices);

        final long voxelFaces = EulerContribution.voxelFaces(traverser);
        assertEquals("Number of voxel faces is incorrect", 0, voxelFaces);

        final long voxelEdges = EulerContribution.voxelEdges(traverser);
        assertEquals("Number of voxel edges is incorrect", 0, voxelEdges);

        final Double result = (Double) IMAGE_J.op().run(EulerContribution.class, cuboid);
        assertEquals("Euler contribution is incorrect", 0, result.intValue());
    }

    /**
     * Regression test EulerCharacteristic with a solid cuboid that's the same size as the image,
     * i.e. all faces touch the edges
     */
    @Test
    public void testCompute1CuboidStackSize() throws Exception {
        final int edges = 12;
        final int cubeSize = 3;
        final int edgeSize = cubeSize - 2;
        final ImgPlus<BitType> cuboid =
                (ImgPlus<BitType>) IMAGE_J.op().run(Cuboid.class, null, cubeSize, cubeSize, cubeSize, 1, 1, 0);
        final EulerContribution.Traverser<BitType> traverser = new EulerContribution.Traverser<>(cuboid);

        final int vertices = EulerContribution.stackCorners(traverser);
        assertEquals("Number of stack vertices is incorrect", 8, vertices);

        final long stackEdges = EulerContribution.stackEdges(traverser);
        assertEquals("Number stack edge voxels is incorrect", edges * edgeSize, stackEdges);

        final int faces = EulerContribution.stackFaces(traverser);
        assertEquals("Number stack face voxels is incorrect", 6 * edgeSize * edgeSize, faces);

        final long voxelVertices = EulerContribution.voxelVertices(traverser);
        assertEquals("Number of voxel vertices is incorrect", edges * (cubeSize - 1), voxelVertices);

        final long xyFace = (cubeSize + 1) * (cubeSize + 1);
        final long yzFace = (cubeSize - 1) * (cubeSize + 1);
        final long xzFace = (cubeSize - 1) * (cubeSize - 1);
        final long expectedVoxelFaces = xyFace * 2 + yzFace * 2 + xzFace * 2;
        final long voxelFaces = EulerContribution.voxelFaces(traverser);
        assertEquals("Number of voxel faces is incorrect", expectedVoxelFaces, voxelFaces);

        final long voxelEdges = EulerContribution.voxelEdges(traverser);
        assertEquals("Number of voxel edges is incorrect", 108, voxelEdges);

        final Double result = (Double) IMAGE_J.op().run(EulerContribution.class, cuboid);
        assertEquals("Euler contribution is incorrect", 1, result.intValue());
    }
}