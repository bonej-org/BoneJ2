package org.bonej.ops.connectivity;

import net.imagej.ImgPlus;
import net.imagej.ops.Contingent;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.RandomAccess;
import net.imglib2.type.BooleanType;
import net.imglib2.view.Views;
import org.bonej.utilities.AxisUtils;
import org.scijava.plugin.Plugin;

import java.util.Optional;
import java.util.stream.LongStream;

/**
 * An Op which calculates the edge correction needed for the Euler characteristic of the image to approximate its
 * contribution to the whole image. That is, it's assumed that the image is a small part cut from a larger image.
 * <p>
 * Based on the article
 * Odgaard A, Gundersen HJG (1993) Quantification of connectivity in cancellous bone,
 * with special emphasis on 3-D reconstructions.
 * Bone 14: 173-182.
 * <a href="http://dx.doi.org/10.1016/8756-3282(93)90245-6">doi:10.1016/8756-3282(93)90245-6</a>
 *
 * @author Michael Doube
 * @author Richard Domander
 * @implNote Methods are written statically to help testing
 */
@Plugin(type = Op.class, name = "eulerContribution")
public class EulerContribution<B extends BooleanType<B>> extends AbstractUnaryFunctionOp<ImgPlus<B>, Double> implements
        Contingent {
    /** The algorithm is only defined for 3D images  */
    @Override
    public boolean conforms() {
        return AxisUtils.countSpatialDimensions(in()) == 3;
    }

    @Override
    public Double compute1(final ImgPlus<B> imgPlus) {
        final Traverser<B> traverser = new Traverser<>(imgPlus);
        final long chiZero = stackCorners(traverser);
        final long e = stackEdges(traverser) + 3 * chiZero;
        final long d = voxelVertices(traverser) + chiZero;
        final long c = stackFaces(traverser) + 2 * e - 3 * chiZero;
        final long b = voxelEdges(traverser);
        final long a = voxelFaces(traverser);

        final long chiOne = d - e;
        final long chiTwo = a - b + c;

        return chiTwo / 2.0 + chiOne / 4.0 + chiZero / 8.0;
    }

    /**
     * Counts the foreground voxels in stack corners
     * <p>
     * Calculates χ_0 from Odgaard and Gundersen
     * @implNote Public and static for testing purposes
     */
    public static <B extends BooleanType<B>> int stackCorners(final Traverser<B> traverser) {
        int foregroundVoxels = 0;
        foregroundVoxels += getAtLocation(traverser, traverser.x0, traverser.y0, traverser.z0);
        foregroundVoxels += getAtLocation(traverser, traverser.x1, traverser.y0, traverser.z0);
        foregroundVoxels += getAtLocation(traverser, traverser.x1, traverser.y1, traverser.z0);
        foregroundVoxels += getAtLocation(traverser, traverser.x0, traverser.y1, traverser.z0);
        foregroundVoxels += getAtLocation(traverser, traverser.x0, traverser.y0, traverser.z1);
        foregroundVoxels += getAtLocation(traverser, traverser.x1, traverser.y0, traverser.z1);
        foregroundVoxels += getAtLocation(traverser, traverser.x1, traverser.y1, traverser.z1);
        foregroundVoxels += getAtLocation(traverser, traverser.x0, traverser.y1, traverser.z1);
        return foregroundVoxels;
    }

    /**
     * Count the foreground voxels on the edges lining the stack
     * <p>
     * Contributes to χ_1 from Odgaard and Gundersen
     * @implNote Public and static for testing purposes
     */
    public static <B extends BooleanType<B>> long stackEdges(final Traverser<B> traverser) {
        final long[] foregroundVoxels = {0};

        // left to right stack edges
        LongStream.of(traverser.z0, traverser.z1).forEach(z -> {
            LongStream.of(traverser.y0, traverser.y1).forEach(y -> {
                for (long x = 1; x < traverser.x1; x++) {
                    foregroundVoxels[0] += getAtLocation(traverser, x, y, z);
                }
            });
        });

        LongStream.of(traverser.z0, traverser.z1).forEach(z -> {
            LongStream.of(traverser.x0, traverser.x1).forEach(x -> {
                for (long y = 1; y < traverser.y1; y++) {
                    foregroundVoxels[0] += getAtLocation(traverser, x, y, z);
                }
            });
        });

        LongStream.of(traverser.y0, traverser.y1).forEach(y -> {
            LongStream.of(traverser.x0, traverser.x1).forEach(x -> {
                for (long z = 1; z < traverser.z1; z++) {
                    foregroundVoxels[0] += getAtLocation(traverser, x, y, z);
                }
            });
        });

        return foregroundVoxels[0];
    }

    /**
     * Count the foreground voxels on the faces that line the edges of the stack
     * <p>
     * Contributes to χ_2 from Odgaard and Gundersen
     * @implNote Public and static for testing purposes
     */
    public static <B extends BooleanType<B>> int stackFaces(final Traverser<B> traverser) {
        final int[] foregroundVoxels = {0};

        LongStream.of(traverser.z0, traverser.z1).forEach(z -> {
            for (int y = 1; y < traverser.y1; y++) {
                for (int x = 1; x < traverser.x1; x++) {
                    foregroundVoxels[0] += getAtLocation(traverser, x, y, z);
                }
            }
        });

        LongStream.of(traverser.y0, traverser.y1).forEach(y -> {
            for (int z = 1; z < traverser.z1; z++) {
                for (int x = 1; x < traverser.x1; x++) {
                    foregroundVoxels[0] += getAtLocation(traverser, x, y, z);
                }
            }
        });

        LongStream.of(traverser.x0, traverser.x1).forEach(x -> {
            for (int y = 1; y < traverser.y1; y++) {
                for (int z = 1; z < traverser.z1; z++) {
                    foregroundVoxels[0] += getAtLocation(traverser, x, y, z);
                }
            }
        });

        return foregroundVoxels[0];
    }

    public static <B extends BooleanType<B>> long voxelVertices(final Traverser<B> traverser) {
        final int[] voxelVertices = {0};

        LongStream.of(traverser.z0, traverser.z1).forEach(z -> {
            traverser.access.setPosition(z, traverser.zIndex);
            LongStream.of(traverser.y0, traverser.y1).forEach(y -> {
                traverser.access.setPosition(y, traverser.yIndex);
                for (long x = 1; x < traverser.xSize; x++) {
                    if (isTwoNeighborhoodForeground(traverser.access, x, traverser.xIndex)) {
                        voxelVertices[0]++;
                    }
                }
            });
        });

        LongStream.of(traverser.z0, traverser.z1).forEach(z -> {
            traverser.access.setPosition(z, traverser.zIndex);
            LongStream.of(traverser.x0, traverser.x1).forEach(x -> {
                traverser.access.setPosition(x, traverser.xIndex);
                for (long y = 1; y < traverser.ySize; y++) {
                    if (isTwoNeighborhoodForeground(traverser.access, y, traverser.yIndex)) {
                        voxelVertices[0]++;
                    }
                }
            });
        });

        LongStream.of(traverser.y0, traverser.y1).forEach(y -> {
            traverser.access.setPosition(y, traverser.yIndex);
            LongStream.of(traverser.x0, traverser.x1).forEach(x -> {
                traverser.access.setPosition(x, traverser.xIndex);
                for (long z = 1; z < traverser.zSize; z++) {
                    if (isTwoNeighborhoodForeground(traverser.access, z, traverser.zIndex)) {
                        voxelVertices[0]++;
                    }
                }
            });
        });

        return voxelVertices[0];
    }

    public static <B extends BooleanType<B>> long voxelEdges(final Traverser<B> traverser) {
        final long[] voxelEdges = {0};

        // Front and back faces (all 4 edges). Check 2 edges per voxel
        LongStream.of(traverser.z0, traverser.z1).forEach(z -> {
            traverser.access.setPosition(z, traverser.zIndex);
            for (int y = 0; y <= traverser.ySize; y++) {
                for (int x = 0; x <= traverser.xSize; x++) {
                    final int voxel = getAtLocation(traverser, x, y, z);
                    if (voxel > 0) {
                        voxelEdges[0] += 2;
                        continue;
                    }

                    voxelEdges[0] += getAtLocation(traverser, x, y - 1, z);
                    voxelEdges[0] += getAtLocation(traverser, x - 1, y, z);
                }
            }
        });

        // Top and bottom faces (horizontal edges)
        LongStream.of(traverser.y0, traverser.y1).forEach(y -> {
            traverser.access.setPosition(y, traverser.yIndex);
            for (int z = 1; z < traverser.zSize; z++) {
                for (int x = 0; x < traverser.xSize; x++) {
                    if (isTwoNeighborhoodForeground(traverser.access, z, traverser.zIndex)) {
                        voxelEdges[0]++;
                    }
                }
            }
        });

        // Top and bottom faces (vertical edges)
        LongStream.of(traverser.y0, traverser.y1).forEach(y -> {
            traverser.access.setPosition(y, traverser.yIndex);
            for (int z = 0; z < traverser.zSize; z++) {
                for (int x = 0; x <= traverser.xSize; x++) {
                    if (isTwoNeighborhoodForeground(traverser.access, x, traverser.xIndex)) {
                        voxelEdges[0]++;
                    }
                }
            }
        });

        // Left and right faces (horizontal edges)
        LongStream.of(traverser.x0, traverser.x1).forEach(x -> {
            traverser.access.setPosition(x, traverser.xIndex);
            for (int z = 1; z < traverser.zSize; z++) {
                for (int y = 0; y < traverser.ySize; y++) {
                    if (isTwoNeighborhoodForeground(traverser.access, z, traverser.zIndex)) {
                        voxelEdges[0]++;
                    }
                }
            }
        });

        // Left and right faces (vertical edges)
        LongStream.of(traverser.x0, traverser.x1).forEach(x -> {
            traverser.access.setPosition(x, traverser.xIndex);
            for (int z = 0; z < traverser.zSize; z++) {
                for (int y = 1; y < traverser.ySize; y++) {
                    if (isTwoNeighborhoodForeground(traverser.access, y, traverser.yIndex)) {
                        voxelEdges[0]++;
                    }
                }
            }
        });

        return voxelEdges[0];
    }

    public static <B extends BooleanType<B>> long voxelFaces(final Traverser<B> traverser) {
        final long[] voxelFaces = {0};

        LongStream.of(traverser.z0, traverser.z1).forEach(z -> {
            traverser.access.setPosition(z, traverser.zIndex);
            for (int y = 0; y <= traverser.ySize; y++) {
                for (int x = 0; x <= traverser.xSize; x++) {
                    if (isFourNeighborhoodForeground(traverser.access, x, traverser.xIndex, y, traverser.yIndex)) {
                        voxelFaces[0]++;
                    }
                }
            }
        });

        LongStream.of(traverser.x0, traverser.x1).forEach(x -> {
            traverser.access.setPosition(x, traverser.xIndex);
            for (int y = 0; y <= traverser.ySize; y++) {
                for (int z = 1; z < traverser.zSize; z++) {
                    if (isFourNeighborhoodForeground(traverser.access, y, traverser.yIndex, z, traverser.zIndex)) {
                        voxelFaces[0]++;
                    }
                }
            }
        });

        LongStream.of(traverser.y0, traverser.y1).forEach(y -> {
            traverser.access.setPosition(y, traverser.yIndex);
            for (int x = 1; x < traverser.ySize; x++) {
                for (int z = 1; z < traverser.zSize; z++) {
                    if (isFourNeighborhoodForeground(traverser.access, x, traverser.yIndex, z, traverser.zIndex)) {
                        voxelFaces[0]++;
                    }
                }
            }
        });

        return voxelFaces[0];
    }

    //region -- Helper methods --
    private static <B extends BooleanType<B>> int getAtLocation(final Traverser<B> traverser, final long x,
            final long y, final long z) {
        traverser.access.setPosition(x, traverser.xIndex);
        traverser.access.setPosition(y, traverser.yIndex);
        traverser.access.setPosition(z, traverser.zIndex);
        return (int) traverser.access.get().getRealDouble();
    }

    private static <B extends BooleanType<B>> boolean isTwoNeighborhoodForeground(final RandomAccess<B> access,
            final long pos, final int dim) {
        access.setPosition(pos, dim);
        final boolean voxelA = access.get().get();
        access.setPosition(pos - 1, dim);
        final boolean voxelB = access.get().get();

        return voxelA || voxelB;
    }

    private static <B extends BooleanType<B>> boolean isFourNeighborhoodForeground(final RandomAccess<B> access,
            final long pos1, final int dim1, final long pos2, final int dim2) {
        access.setPosition(pos1, dim1);
        access.setPosition(pos2, dim2);
        final boolean voxelA = access.get().get();
        access.setPosition(pos1 - 1, dim1);
        access.setPosition(pos2, dim2);
        final boolean voxelB = access.get().get();
        access.setPosition(pos1, dim1);
        access.setPosition(pos2 - 1, dim2);
        final boolean voxelC = access.get().get();
        access.setPosition(pos1 - 1, dim1);
        access.setPosition(pos2 - 1, dim2);
        final boolean voxelD = access.get().get();

        return voxelA || voxelB || voxelC || voxelD;
    }
    //endregion

    /** A convenience class for passing parameters */
    public static class Traverser<B extends BooleanType<B>> {
        public final long x0 = 0;
        public final long y0 = 0;
        public final long z0 = 0;
        public final long x1;
        public final long y1;
        public final long z1;
        public final int xIndex;
        public final int yIndex;
        public final int zIndex;
        public final long xSize;
        public final long ySize;
        public final long zSize;
        public final RandomAccess<B> access;

        public Traverser(ImgPlus<B> imgPlus) {
            final Optional<int[]> optional = AxisUtils.getXYZIndices(imgPlus);
            final int[] indices = optional.get();

            xIndex = indices[0];
            yIndex = indices[1];
            zIndex = indices[2];
            xSize = imgPlus.dimension(xIndex);
            ySize = imgPlus.dimension(yIndex);
            zSize = imgPlus.dimension(zIndex);
            x1 = xSize - 1;
            y1 = ySize - 1;
            z1 = zSize - 1;
            access = Views.extendZero(imgPlus).randomAccess();
        }
    }
}
