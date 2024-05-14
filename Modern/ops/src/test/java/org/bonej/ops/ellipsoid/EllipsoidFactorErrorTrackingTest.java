/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
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

import java.util.Map;

import net.imglib2.img.Img;
import org.junit.Test;

import net.imagej.ops.AbstractOpTest;
import net.imglib2.IterableInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import static org.junit.Assert.assertEquals;

public class EllipsoidFactorErrorTrackingTest extends AbstractOpTest {

    @Test
    public void testMedian(){
        final Img<FloatType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking(ops);
        final Map<String,Double> errorStats = errorTracking.calculate(toyImg);
        Double median = errorStats.get("Median");
        assertEquals("Median wrong", 3.0, median, 1e-12);
    }

    @Test
    public void testIteration(){
        final Img<FloatType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking(ops);
        errorTracking.calculate(toyImg);
        Map<String,Double> errorStats = errorTracking.calculate((Img<FloatType>) toyImg);
        Double median = errorStats.get("Median");
        assertEquals("Iteration get median wrong", 0.0, median, 1e-12);
    }

    @Test
    public void testMean(){
        final Img<FloatType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking(ops);
        final Map<String,Double> errorStats = errorTracking.calculate(toyImg);
        Double mean = errorStats.get("Mean");
        assertEquals("Mean wrong", 2.5, mean, 1e-12);
    }

    @Test
    public void testMax(){
        final Img<FloatType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking(ops);
        Map<String,Double> errorStats = errorTracking.calculate(toyImg);
        Double max = errorStats.get("Max");
        assertEquals("Max wrong", 3.5, max, 1e-12);
    }

    @Test
    public void testMin(){
        final Img<FloatType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking(ops);
        Map<String,Double> errorStats = errorTracking.calculate(toyImg);
        Double min = errorStats.get("Min");
        assertEquals("Min wrong", 1.0, min, 1e-12);
    }

    private Img<FloatType> getToyImg()
    {
        final ArrayImg<FloatType, FloatArray> floats = ArrayImgs.floats(3, 3, 3);
        floats.forEach(f -> f.setReal(Float.NaN));

        final ArrayRandomAccess<FloatType> access = floats.randomAccess();
        access.setPosition(new int[]{1,1,1});
        access.get().setReal(1.0);

        access.setPosition(new int[]{0,1,1});
        access.get().setReal(3.0);

        access.setPosition(new int[]{2,1,1});
        access.get().setReal(3.5);

        return floats;
    }
}
