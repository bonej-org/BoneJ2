package org.bonej.ops;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.algorithm.neighborhood.HyperSphereNeighborhood;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.stats.Max;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Vector3d;

import java.util.HashSet;
import java.util.Set;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;

@Plugin(type = Op.class)
public class DistanceRidge <R extends RealType<R>> extends AbstractUnaryFunctionOp<Img<R>,Set<Vector3d>> {

    @Override
    public Set<Vector3d> calculate(Img<R> distanceMap) {

        final Set<Vector3d> distanceRidgeSet = new HashSet<>();

        final Cursor<R> overallMaximum = Max.findMax(distanceMap);

        //slow!!!
        final long v = (long) ceil(overallMaximum.get().getRealDouble());
        long[] expandedRange = {v,v,v};


        final IntervalView<R> distanceMapInteriorView = Views.expandZero(distanceMap, expandedRange);

        final HyperSphereShape sphereShape = new HyperSphereShape((long) floor(overallMaximum.get().getRealDouble()));
        final HyperSphereShape.NeighborhoodsIterableInterval<R> sphereNeighborhoods = sphereShape.neighborhoods(distanceMapInteriorView);

        sphereNeighborhoods.forEach(n -> {
            final Cursor<R> localValue = n.cursor();
            if(localValue.get().getRealDouble()!=0.0){
                final Cursor<R> localMax = Max.findMax(n);
                Vector3d largestSphereCenter = new Vector3d(localMax.getDoublePosition(0),localMax.getDoublePosition(1),localMax.getDoublePosition(2));
                distanceRidgeSet.add(largestSphereCenter);
            }
        });

        return distanceRidgeSet;
    }
}
