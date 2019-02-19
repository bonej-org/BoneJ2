package org.bonej.ops.skeletonize;

import net.imagej.ImgPlus;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imagej.ops.stats.IterableMax;
import net.imglib2.*;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

@Plugin(name = "Find ridge points of a binary image", type = Op.class)
public class FindRidgePoints<R extends RealType<R> & NativeType<R>> extends AbstractUnaryFunctionOp<RandomAccessibleInterval<BitType>,List<Vector3dc>> {

    @Parameter(persist = false, required = false)
    private long approximateMaximumNumberOfSeeds = 20000;

    @Parameter(persist = false, required = false)
    private DoubleType thresholdForBeingARidgePoint = new DoubleType(0.6);

    @Parameter(label = "Seed point image", type = ItemIO.OUTPUT)
    private ImgPlus<UnsignedByteType> seedPointsImage;

    private Random rng = new Random();

    @Override
    public List<Vector3dc> calculate(RandomAccessibleInterval<BitType> bitImage) {
        final Img<R> ridge = (Img<R>) createRidge(bitImage);

        IterableMax<R> iterableMax = new IterableMax<>();
        final double threshold = thresholdForBeingARidgePoint.getRealFloat() *
                iterableMax.calculate(ridge).getRealFloat();

        final List<Vector3dc> seeds = new ArrayList<>();
        final Cursor<R> ridgeCursor = ridge.cursor();
        final long[] position = new long[3];
        final long[] dimensionsMinusOne = new long[]{bitImage.dimension(0)-1, bitImage.dimension(1)-1, bitImage.dimension(2)-1};
        final Dimensions innerImageDimensions = new FinalDimensions(dimensionsMinusOne);
        while (ridgeCursor.hasNext()) {
            ridgeCursor.fwd();
            final double localValue = ridgeCursor.get().getRealFloat();
            if (localValue <= threshold) {
                continue;
            }
            ridgeCursor.localize(position);
            if(outOfBounds(innerImageDimensions, position))
            {
                //	continue;
            }
            final Vector3d seed = new Vector3d(position[0], position[1], position[2]);
            seed.sub(0.5, 0.5, 0.5);//add 0.5 to centre of pixel, and subtract 1.0 because of ridge calculated on 1-expanded image!
            seeds.add(seed);
        }
        if (seeds.size() > approximateMaximumNumberOfSeeds) {
            reduceSeedPoints(seeds);
        }
        createSeedPointImage(ridge, seeds);
        return seeds;
    }

    private IterableInterval<R> createRidge(
            final RandomAccessibleInterval<BitType> image)
    {
        final long[] borderExpansion = new long[]{1,1,1};
        final long[] offset = new long[]{-1,-1,-1};
        final IntervalView<BitType> offsetImage = Views.offset(image, offset);
        final IntervalView<BitType> expandedImage = Views.expandZero(offsetImage, borderExpansion);

        final RandomAccessibleInterval<R> distanceMap = ops().image().distancetransform(expandedImage);

        final List<Shape> shapes = new ArrayList<>();
        shapes.add(new HyperSphereShape(2));

        final IterableInterval<R> open =  ops().morphology().open(distanceMap, shapes);
        final IterableInterval<R> close =  ops().morphology().close(distanceMap, shapes);
        final IterableInterval<R> ridge = ops().math().subtract(close, open);

        final Cursor<R> ridgeCursor = ridge.localizingCursor();
        final Img openImg = (Img) open;
        final RandomAccess<R> openedRA = openImg.randomAccess();
        final long[] position = new long[3];
        while (ridgeCursor.hasNext()) {
            ridgeCursor.fwd();
            ridgeCursor.localize(position);
            openedRA.setPosition(position);
            if (openedRA.get().getRealDouble()<1.0+1e-12)//avoids false ridge points on edge of FG
            {
                ridgeCursor.get().setReal(0.0f);
            }
        }
        return ridge;
    }

    private void createSeedPointImage(final Img<R> seedPointImage,
                                      final List<Vector3dc> seedPoints)
    {
        seedPointImage.cursor().forEachRemaining( c -> c.setZero());
        RandomAccess<R> randomAccess = seedPointImage.randomAccess();
        seedPoints.forEach(seed ->
                {
                    long[] seedPixel = {(long) seed.get(0), (long) seed.get(1), (long) seed.get(2)};
                    randomAccess.setPosition(seedPixel);
                    randomAccess.get().setReal(255);
                }
        );
        seedPointsImage = new ImgPlus<>(ops().convert().uint8(seedPointImage), "Seeding Points");
    }

    private void reduceSeedPoints(final Collection<Vector3dc> seeds) {
        final double probabilityOfAcceptingSeed =
                ((double) approximateMaximumNumberOfSeeds / seeds.size());
        seeds.removeIf(i -> rng.nextDouble() > probabilityOfAcceptingSeed);
    }

    private static boolean outOfBounds(final Dimensions dimensions, final long[] currentPixelPosition) {
        for (int i = 0; i < currentPixelPosition.length; i++) {
            final long position = currentPixelPosition[i];
            if (position < 0 || position >= dimensions.dimension(i)) {
                return true;
            }
        }
        return false;
    }
}
