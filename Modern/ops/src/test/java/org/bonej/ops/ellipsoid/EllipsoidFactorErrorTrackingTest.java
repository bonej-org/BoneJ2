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
        final IterableInterval<? extends RealType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking();
        errorTracking.setEnvironment(ops);
        Map<String,Double> errorStats = errorTracking.calculate((Img<FloatType>) toyImg);
        Double median = errorStats.get("Median");
        assertEquals("Median wrong", 3.0, median, 1e-12);
    }

    @Test
    public void testIteration(){
        final IterableInterval<? extends RealType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking();
        errorTracking.setEnvironment(ops);
        Map<String,Double> errorStats = errorTracking.calculate((Img<FloatType>) toyImg);
        errorStats = errorTracking.calculate((Img<FloatType>) toyImg);
        Double median = errorStats.get("Median");
        assertEquals("Iteration get median wrong", 0.0, median, 1e-12);
    }

    @Test
    public void testMean(){
        final IterableInterval<? extends RealType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking();
        errorTracking.setEnvironment(ops);
        Map<String,Double> errorStats = errorTracking.calculate((Img<FloatType>) toyImg);
        Double mean = errorStats.get("Mean");
        assertEquals("Mean wrong", 2.5, mean, 1e-12);
    }

    @Test
    public void testMax(){
        final IterableInterval<? extends RealType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking();
        errorTracking.setEnvironment(ops);
        Map<String,Double> errorStats = errorTracking.calculate((Img<FloatType>) toyImg);
        Double max = errorStats.get("Max");
        assertEquals("Max wrong", 3.5, max, 1e-12);
    }

    @Test
    public void testMin(){
        final IterableInterval<? extends RealType> toyImg = getToyImg();
        final EllipsoidFactorErrorTracking errorTracking = new EllipsoidFactorErrorTracking();
        errorTracking.setEnvironment(ops);
        Map<String,Double> errorStats = errorTracking.calculate((Img<FloatType>) toyImg);
        Double min = errorStats.get("Min");
        assertEquals("Min wrong", 1.0, min, 1e-12);
    }

    private IterableInterval<? extends RealType> getToyImg()
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