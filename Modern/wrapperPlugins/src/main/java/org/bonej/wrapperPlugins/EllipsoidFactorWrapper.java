package org.bonej.wrapperPlugins;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
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

import net.imglib2.view.composite.RealComposite;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.FindLocalEllipsoidOp;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.UIService;
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

    @SuppressWarnings("unused")
    @Parameter
    private OpService opService;

    @SuppressWarnings("unused")
    @Parameter
    private StatusService statusService;

    @SuppressWarnings("unused")
    @Parameter
    private UIService uiService;

    @SuppressWarnings("unused")
    @Parameter
    private PrefService prefService;

    @Override
    public void run() {
        Img<DoubleType> inputAsDoubles = opService.convert().float64(inputImage);
        CompositeIntervalView<DoubleType, RealComposite<DoubleType>> derivatives = opService.filter().allPartialDerivatives(inputAsDoubles);
        CompositeView<DoubleType, RealComposite<DoubleType>>.CompositeRandomAccess derivativeRandomAccess = derivatives.randomAccess();

        final List<ValuePair<Vector3d, Vector3d>> seedPoints = new ArrayList<>();
        final Random random = new Random();
        double chanceOfBeingSeeded = 0.1;

        final List<Vector3d> boundaryPoints = new ArrayList<>();

        Cursor<IntegerType> inputCursor = inputImage.localizingCursor();
        while(inputCursor.hasNext())
        {
            inputCursor.fwd();
            long[] position = new long[3];
            inputCursor.localize(position);
            if(inputCursor.get().getInteger()!=0) {
                derivativeRandomAccess.setPosition(position);
                Vector3d derivative = new Vector3d(derivativeRandomAccess.get().get(0).getRealDouble(),derivativeRandomAccess.get().get(1).getRealDouble(),derivativeRandomAccess.get().get(2).getRealDouble());
                if(derivative.length()!=0.0){
                    Vector3d point = new Vector3d(position[0]-0.5,position[1]-0.5,position[2]-0.5);//watch out for calibration issues later on here!!
                    boundaryPoints.add(point);
                    if(random.nextDouble()<=chanceOfBeingSeeded){
                        seedPoints.add(new ValuePair<>(point,derivative));
                    }
                }
            }
        }

        List<Ellipsoid> ellipsoids = getLocalEllipsoids(seedPoints, boundaryPoints);
        ellipsoids.sort(Comparator.comparingDouble(e -> -e.getVolume()));

        //find EF values
        final Img<DoubleType> ellipsoidFactorImage = ArrayImgs.doubles(inputImage.dimension(0),inputImage.dimension(1),inputImage.dimension(2));
        inputCursor.reset();

        while (inputCursor.hasNext()) {
            inputCursor.fwd();

            //find current position
            long[] coordinates = new long[3];
            inputCursor.localize(coordinates);

            //find largest ellipsoid containing current position
            int currentEllipsoidCounter = 0;
            while(currentEllipsoidCounter<ellipsoids.size()&&!insideEllipsoid(coordinates, ellipsoids.get(currentEllipsoidCounter)))
            {
                currentEllipsoidCounter++;
            }

            //ignore background voxels and voxels not contained in any ellipsoid
            if(inputCursor.get().getInteger()==0 || currentEllipsoidCounter==ellipsoids.size())
            {
                RandomAccess<DoubleType> randomAccess = ellipsoidFactorImage.randomAccess();
                randomAccess.setPosition(coordinates);
                randomAccess.get().set(Double.NaN);
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

