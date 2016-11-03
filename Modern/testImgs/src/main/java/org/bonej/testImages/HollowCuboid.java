
package org.bonej.testImages;

import static org.bonej.testImages.IJ1ImgPlus.*;

import java.util.stream.LongStream;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractNullaryFunctionOp;
import net.imglib2.RandomAccess;
import net.imglib2.type.logic.BitType;

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Creates an ImgPlus<BitType> of a hollow cuboid.
 *
 * @author Richard Domander
 */
@Plugin(type = Op.class, name = "hollowCuboid",
	menuPath = "Plugins>Test Images>Hollow cuboid")
public class HollowCuboid extends AbstractNullaryFunctionOp<ImgPlus<BitType>> {

	@Parameter(label = "X-size", description = "Cuboid width", min = "1")
	private long xSize = 50;

	@Parameter(label = "Y-size", description = "Cuboid height", min = "1")
	private long ySize = 50;

	@Parameter(label = "Z-size", description = "Cuboid depth", min = "1")
	private long zSize = 50;

	@Parameter(label = "Channels", description = "Colour channels", min = "1",
		max = "4", required = false)
	private long channels = 1;

	@Parameter(label = "Frames", description = "Size in time dimension",
		min = "1", required = false)
	private long frames = 1;

	@Parameter(label = "Padding", description = "Empty space around the cuboid",
		min = "0", required = false)
	private long padding = 5;

	@Parameter(label = "Scale", description = "The scale calibration",
		min = "0.0", required = false)
	private double scale = 1.0;

	@Parameter(label = "Unit", description = "The unit of calibration",
		required = false)
	private String unit = "";

	@Override
	public ImgPlus<BitType> compute0() {
		final ImgPlus<BitType> cuboid = createIJ1ImgPlus("Hollow cuboid", xSize,
			ySize, zSize, channels, frames, padding, scale, unit);
		final RandomAccess<BitType> access = cuboid.randomAccess();

		for (int t = 0; t < frames; t++) {
			access.setPosition(t, TIME_DIM);
			for (int c = 0; c < channels; c++) {
				access.setPosition(c, CHANNEL_DIM);
				drawFaces(access, X_DIM, Y_DIM, Z_DIM, xSize, ySize, zSize);
				drawFaces(access, X_DIM, Z_DIM, Y_DIM, xSize, zSize, ySize);
				drawFaces(access, Y_DIM, Z_DIM, X_DIM, ySize, zSize, xSize);
			}
		}

		return cuboid;
	}

	/**
	 * Draw two faces of the cuboid
	 *
	 * @param faceDim1 Index of the 1st spatial dimension of the faces in the axes
	 *          table
	 * @param faceDim2 Index of the 2nd spatial dimension of the faces in the axes
	 *          table
	 * @param orthogonalDim Index of the dimension orthogonal to the faces
	 * @param faceSize1 Size of the face in the 1st spatial dimension
	 * @param faceSize2 Size of the face in the 2nd spatial dimension
	 * @param orthogonalDistance Spacing between the faces in the orthogonal
	 *          dimension
	 */
	private void drawFaces(RandomAccess<BitType> access, final int faceDim1,
		final int faceDim2, final int orthogonalDim, final long faceSize1,
		final long faceSize2, final long orthogonalDistance)
	{
		LongStream.of(padding, padding + orthogonalDistance - 1).forEach(i -> {
			access.setPosition(i, orthogonalDim);
			for (long j = padding; j < padding + faceSize1; j++) {
				access.setPosition(j, faceDim1);
				for (long k = padding; k < padding + faceSize2; k++) {
					access.setPosition(k, faceDim2);
					access.get().setOne();
				}
			}
		});

		// Reset positions for next call
		access.setPosition(0, orthogonalDim);
		access.setPosition(0, faceDim1);
		access.setPosition(0, faceDim2);
	}

	public static void main(String... args) {
		final ImageJ ij = net.imagej.Main.launch(args);
		Object cuboid = ij.op().run(HollowCuboid.class, 100L, 100L, 10L, 3L, 5L,
			5L);
		ij.ui().show(cuboid);
	}
}
