/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
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
package org.bonej.ops.skeletonize;

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
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alessandro Felder
 * @author Michael Doube
 *
 * @param <R>
 */
@Plugin(name = "Find ridge points of a binary image", type = Op.class)
public class FindRidgePoints<R extends RealType<R> & NativeType<R>> extends AbstractUnaryFunctionOp<RandomAccessibleInterval<BitType>,List<int[]>> {

    @Parameter(persist = false, required = false)
    private DoubleType thresholdForBeingARidgePoint = new DoubleType(0.6);

    @Override
    public List<int[]> calculate(RandomAccessibleInterval<BitType> bitImage) {
        final Img<R> ridge = (Img<R>) createRidge(bitImage);

        IterableMax<R> iterableMax = new IterableMax<>();
        final double threshold = thresholdForBeingARidgePoint.getRealFloat() *
                iterableMax.calculate(ridge).getRealFloat();

        final List<int[]> seeds = new ArrayList<>();
        final Cursor<R> ridgeCursor = ridge.localizingCursor();
        final long[] position = new long[3];
        while (ridgeCursor.hasNext()) {
            ridgeCursor.fwd();
            final double localValue = ridgeCursor.get().getRealFloat();
            if (localValue <= threshold) {
                continue;
            }
            ridgeCursor.localize(position);
            //need to shift by -1 because the ridge image has been expanded (and shifted) by +1
            final int[] seed = new int[] {(int) position[0] - 1, (int) position[1] - 1, (int) position[2] - 1};
            seeds.add(seed);
        }
        return seeds;
    }

    private IterableInterval<R> createRidge(
            final RandomAccessibleInterval<BitType> image)
    {
        final long[] borderExpansion = new long[]{1,1,1};
        final long[] offset = new long[]{-1,-1,-1};
        final IntervalView<BitType> offsetImage = Views.translateInverse(image, offset);
        final IntervalView<BitType> expandedImage = Views.expandZero(offsetImage, borderExpansion);

        final RandomAccessibleInterval<R> distanceMap = ops().image().distancetransform(expandedImage);

        final List<Shape> shapes = new ArrayList<>();
        shapes.add(new HyperSphereShape(2));

        final IterableInterval<R> open =  ops().morphology().open(distanceMap, shapes);
        final IterableInterval<R> close =  ops().morphology().close(distanceMap, shapes);
        final IterableInterval<R> ridge = ops().math().subtract(close, open);

        final Cursor<R> ridgeCursor = ridge.localizingCursor();
        final Img<R> openImg = (Img<R>) open;
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
}
