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
    private Img<FloatType> currentAverage;
    private Img<FloatType> previousAverage;

    public EllipsoidFactorErrorTracking(OpService opService)
    {
        this.setEnvironment(opService);
    }
    @Override
    public Map<String,Double> calculate(Img<FloatType> currentEllipsoidFactorImage) {
        if(currentIteration==0)
        {
            previousAverage = ArrayImgs.floats(currentEllipsoidFactorImage.dimension(0),currentEllipsoidFactorImage.dimension(1),currentEllipsoidFactorImage.dimension(2));
            currentAverage =  currentEllipsoidFactorImage;
        }
        else
        {
            previousAverage = currentAverage;

            currentAverage = (Img) ops().math().multiply(previousAverage,new FloatType(currentIteration));
            currentAverage = (Img) ops().math().add(currentEllipsoidFactorImage, (IterableInterval<FloatType>) currentAverage);
            currentAverage = (Img) ops().math().divide(currentAverage, new FloatType(currentIteration+1));
        }
        currentIteration++;

        final Img<FloatType> error = (Img) ops().math().subtract(previousAverage, (IterableInterval<FloatType>) currentAverage);
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
        double mean = nonNanValues.stream().reduce((a,b) -> a+b).get()/((double) count);

        final Map<String, Double> stats = new HashMap<>();
        stats.put("Mean",mean);
        stats.put("Max",max);
        stats.put("Min",min);
        stats.put("Median",median);
        return stats;
    }
}
