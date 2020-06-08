/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
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
package org.bonej.ops.ellipsoid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.scijava.plugin.Plugin;

import net.imagej.ops.Op;
import net.imagej.ops.OpService;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Class for tracking descriptive statistics of the change in EF from iteration to iteration
 */
@Plugin(name = "Track Ellipsoid Factor Error", type = Op.class)
public class EllipsoidFactorErrorTracking extends AbstractUnaryFunctionOp<Img<FloatType>, Map<String, Double>> {


    private int currentIteration;
    private IterableInterval<FloatType> currentAverage;

    public EllipsoidFactorErrorTracking(OpService opService)
    {
        this.setEnvironment(opService);
    }
    @Override
    public Map<String,Double> calculate(Img<FloatType> currentEllipsoidFactorImage) {
        final IterableInterval<FloatType> previousAverage;
        if(currentIteration==0)
        {
            previousAverage = ArrayImgs.floats(currentEllipsoidFactorImage.dimension(0),currentEllipsoidFactorImage.dimension(1),currentEllipsoidFactorImage.dimension(2));
            currentAverage =  currentEllipsoidFactorImage;
        }
        else
        {
            previousAverage = currentAverage;
            currentAverage = ops().math().multiply(previousAverage, new FloatType(currentIteration));
            currentAverage = ops().math().add(currentEllipsoidFactorImage, currentAverage);
            currentAverage = ops().math().divide(currentAverage, new FloatType(currentIteration+1));
        }
        currentIteration++;

        final IterableInterval<FloatType> error = ops().math().subtract(previousAverage, currentAverage);
        final Cursor<FloatType> cursor = error.cursor();
        while (cursor.hasNext()) {
            final float next = cursor.next().get();
            if (next < 0) {
                cursor.get().setReal(-next);
            }
        }
        cursor.reset();

        List<Double> nonNanValues = new ArrayList<>();
        while (cursor.hasNext()) {
            final double next = cursor.next().getRealDouble();
            if (!Double.isNaN(next)) {
                nonNanValues.add(next);
            }
        }
        nonNanValues.sort(Double::compare);

        int count = nonNanValues.size();
        double max = nonNanValues.get(count-1);
        double min = nonNanValues.get(0);
        double median = nonNanValues.get(count/2);
        double mean = nonNanValues.stream().reduce(Double::sum).get()/((double) count);

        final Map<String, Double> stats = new HashMap<>();
        stats.put("Mean",mean);
        stats.put("Max",max);
        stats.put("Min",min);
        stats.put("Median",median);
        return stats;
    }
}
