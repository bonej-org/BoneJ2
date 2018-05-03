package org.bonej.wrapperPlugins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.imagej.ImgPlus;
import net.imagej.ops.filter.derivative.PartialDerivativesRAI;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.edge.Edgel;
import net.imglib2.algorithm.edge.SubpixelEdgelDetection;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.CompositeView;

import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.FindLocalEllipsoidOp;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Vector3d;


/**
 * Ellipsoid Factor
 * <p>
 * Ellipsoid
 * </p>
 *
 * @author Alessandro Felder
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Decomposition")
public class EllipsoidFactorWrapper extends ContextCommand {

    private final FindLocalEllipsoidOp findLocalEllipsoidOp = new FindLocalEllipsoidOp();
    @Parameter(validater = "imageValidater")
    private ImgPlus<IntegerType> inputImage;

    @Parameter(label = "EF image", type = ItemIO.OUTPUT)
    private ImgPlus<DoubleType> efImage;

    @Override
    public void run() {
        Img<DoubleType> inputAsDoubles = ArrayImgs.doubles(inputImage.getImg().dimension(0), inputImage.getImg().dimension(1), inputImage.getImg().dimension(2));
        final Cursor<IntegerType> inputImageCursor = inputImage.localizingCursor();

        while (inputImageCursor.hasNext()) {
            inputImageCursor.fwd();

            //find current position
            long[] coordinates = new long[3];
            inputImageCursor.localize(coordinates);
            double castedInt = (double) inputImageCursor.get().getInteger();

            RandomAccess<DoubleType> randomAccess = inputAsDoubles.randomAccess();
            randomAccess.setPosition(coordinates);
            randomAccess.get().set(castedInt);
        }

        PartialDerivativesRAI<DoubleType> gradientCalculatorOp = new PartialDerivativesRAI<>();
        gradientCalculatorOp.initialize();
        final CompositeIntervalView gradients = gradientCalculatorOp.calculate(inputAsDoubles);

        final CompositeView.CompositeRandomAccess gradientRandomAccess = gradients.randomAccess();
        gradientRandomAccess.get().get(0);
        gradientRandomAccess.get().get(1);

        ArrayList<Edgel> edgels = SubpixelEdgelDetection.getEdgels(inputImage.getImg(), inputImage.getImg().factory(), 0);

        final List<Vector3d> boundaryPoints = new ArrayList<>();
        edgels.forEach(e -> boundaryPoints.add(new Vector3d(e.getDoublePosition(0), e.getDoublePosition(1), e.getDoublePosition(2))));

        final List<ValuePair<Vector3d, Vector3d>> seedPoints = new ArrayList<>();

        edgels.forEach(edgel -> {
                    Vector3d start = new Vector3d(edgel.getDoublePosition(0), edgel.getDoublePosition(1), edgel.getDoublePosition(2));

                    Vector3d sum = new Vector3d();
                    IntStream.range(0, edgels.size()).forEach(i -> {
                        double d = distanceBetweenEdgels(edgels.get(i), edgel);
                        if (d < 1.74 && d > 1.0e-12) {
                            Vector3d g = new Vector3d(edgels.get(i).getGradient());
                            g.scale(1.0 / d);
                            sum.add(g);
                        }
                    });
                    sum.add(new Vector3d(edgel.getGradient()));
                    sum.normalize();
                    seedPoints.add(new ValuePair<>(start, new Vector3d(sum)));
                });

        List<Ellipsoid> ellipsoids = getLocalEllipsoids(seedPoints, boundaryPoints);
        ellipsoids.sort(Comparator.comparingDouble(e -> -e.getVolume()));

        //find EF values
        final Img<DoubleType> ellipsoidFactorImage = ArrayImgs.doubles(inputImage.dimension(0),inputImage.dimension(1),inputImage.dimension(2));
        inputImageCursor.reset();

        while (inputImageCursor.hasNext()) {
            inputImageCursor.fwd();

            //find current position
            long[] coordinates = new long[3];
            inputImageCursor.localize(coordinates);
            int currentEllipsoidCounter = 0;

            //ignore background voxels
            if(inputImageCursor.get().getInteger()==0)
            {
                RandomAccess<DoubleType> randomAccess = ellipsoidFactorImage.randomAccess();
                randomAccess.setPosition(coordinates);
                randomAccess.get().set(Double.NaN);
                continue;
            }

            //find largest ellipsoid containing current position
            while(currentEllipsoidCounter<ellipsoids.size()&&!insideEllipsoid(coordinates, ellipsoids.get(currentEllipsoidCounter)))
            {
                currentEllipsoidCounter++;
            }

            //current voxel not contained in any ellipsoid
            if(currentEllipsoidCounter==ellipsoids.size())
            {
                continue;
            }

            //make an ellipsoid factor image with EF value at those coordinates
            RandomAccess<DoubleType> randomAccess = ellipsoidFactorImage.randomAccess();
            randomAccess.setPosition(coordinates);
            randomAccess.get().set(computeEllipsoidFactor(ellipsoids.get(currentEllipsoidCounter)));
        }
        efImage = new ImgPlus<>(ellipsoidFactorImage, "EF");
    }

    private static Double computeEllipsoidFactor(final Ellipsoid ellipsoid) {
        return (ellipsoid.getA()/ellipsoid.getB() - ellipsoid.getB()/ellipsoid.getC());
    }

    static boolean insideEllipsoid(final long[] coordinates, final Ellipsoid ellipsoid) {
        Vector3d x = new Vector3d(coordinates[0],coordinates[1],coordinates[2]);
        Vector3d centroid = ellipsoid.getCentroid();
        x.sub(centroid);

        Matrix3d orientation = new Matrix3d();
        ellipsoid.getOrientation().getRotationScale(orientation);

        Matrix3d eigenMatrix = new Matrix3d();
        eigenMatrix.setM00(1.0/(ellipsoid.getA()*ellipsoid.getA()));
        eigenMatrix.setM11(1.0/(ellipsoid.getB()*ellipsoid.getB()));
        eigenMatrix.setM22(1.0/(ellipsoid.getC()*ellipsoid.getC()));

        eigenMatrix.mul(eigenMatrix,orientation);
        eigenMatrix.mulTransposeLeft(orientation, eigenMatrix);

        Vector3d Ax = new Vector3d();
        eigenMatrix.transform(x,Ax);

        return x.dot(Ax)<1;
    }

    private List<Ellipsoid> getLocalEllipsoids(final List<ValuePair<Vector3d, Vector3d>> seedPoints, final List<Vector3d> boundaryPoints){
        return seedPoints.stream().map(s -> findLocalEllipsoidOp.calculate(new ArrayList<Vector3d>(boundaryPoints), s)).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
    }

    private static double distanceBetweenEdgels(Edgel e1, Edgel e2)
    {
        Vector3d distance = new Vector3d(e1.getDoublePosition(0), e1.getDoublePosition(1), e1.getDoublePosition(2));
        distance.sub(new Vector3d(e2.getDoublePosition(0), e2.getDoublePosition(1), e2.getDoublePosition(2)));
        return distance.length();
    }
}

