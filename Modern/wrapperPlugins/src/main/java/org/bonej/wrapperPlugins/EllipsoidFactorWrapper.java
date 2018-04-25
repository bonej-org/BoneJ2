package org.bonej.wrapperPlugins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.edge.Edgel;
import net.imglib2.algorithm.edge.SubpixelEdgelDetection;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.ValuePair;

import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.FindLocalEllipsoidOp;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;

/**
 * Ellipsoid Factor
 * <p>
 * Ellipsoid
 * </p>
 *
 * @author Alessandro Felder
 */

@Plugin(type = Command.class, menuPath = "Plugins>Ellipsoid Decomposition")
public class EllipsoidFactorWrapper extends ContextCommand {

    FindLocalEllipsoidOp findLocalEllipsoidOp = new FindLocalEllipsoidOp();
    @Parameter(validater = "imageValidater")
    private ImgPlus inputImage;

    @Override
    public void run() {
        //understand input
        ArrayList<Edgel> edgels = SubpixelEdgelDetection.getEdgels(inputImage.getImg(), inputImage.getImg().factory(), 0);

        final List<Vector3d> boundaryPoints = new ArrayList<>();
        edgels.forEach(e ->{
            boundaryPoints.add(new Vector3d(e.getDoublePosition(0), e.getDoublePosition(1), e.getDoublePosition(2)));
        });

        final List<ValuePair<Vector3d, Vector3d>> seedPoints = new ArrayList<>();

        edgels.stream().forEachOrdered( edgel -> {
                    Vector3d start = new Vector3d(edgel.getDoublePosition(0), edgel.getDoublePosition(1), edgel.getDoublePosition(2));

                    Vector3d sum = new Vector3d();
                    for (int i = 0; i < edgels.size(); i++) {
                        double d = distanceBetweenEdgels(edgels.get(i), edgel);
                        if (d < 1.74 && d > 1.0e-12) {
                            Vector3d g = new Vector3d(edgels.get(i).getGradient());
                            g.scale(1.0 / d);
                            sum.add(g);
                        }
                    }
                    sum.add(new Vector3d(edgel.getGradient()));
                    sum.normalize();
                    seedPoints.add(new ValuePair<>(start, new Vector3d(sum)));
                });

        List<Ellipsoid> ellipsoids = getLocalEllipsoids(seedPoints, boundaryPoints);
        ellipsoids.sort(Comparator.comparingDouble(e -> -e.getVolume()));

        //find EF values
        final Img<DoubleType> ellipsoidFactorImage = ArrayImgs.doubles(inputImage.dimension(0),inputImage.dimension(1),inputImage.dimension(2));
        final Cursor<IntegerType> cursor = inputImage.localizingCursor();

        while (cursor.hasNext()) {
            cursor.fwd();

            //find current position
            long[] coordinates = new long[3];
            cursor.localize(coordinates);
            int currentEllipsoidCounter = 0;

            //ignore background voxels
            if(cursor.get().getInteger()==0) continue;

            //find largest ellipsoid containing current position
            while(!insideEllipsoid(coordinates, ellipsoids.get(currentEllipsoidCounter)))
            {
                currentEllipsoidCounter++;
            }

            //make an ellipsoid factor image with EF value at those coordinates
            RandomAccess<DoubleType> randomAccess = ellipsoidFactorImage.randomAccess();
            randomAccess.setPosition(coordinates);
            randomAccess.get().set(computeEllipsoidFactor(ellipsoids.get(currentEllipsoidCounter)));
        }

        ImgPlus<DoubleType> ellipsoidFactorImagePlus = new ImgPlus<DoubleType>(ellipsoidFactorImage, "EF");

    }

    private static Double computeEllipsoidFactor(final Ellipsoid ellipsoid) {
        return (ellipsoid.getA()/ellipsoid.getB() - ellipsoid.getB()/ellipsoid.getC());
    }

    public static boolean insideEllipsoid(final long[] coordinates, final Ellipsoid ellipsoid) {
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
        return seedPoints.stream().map(s -> findLocalEllipsoidOp.calculate(boundaryPoints.stream().collect(Collectors.toList()), s)).filter(opt -> opt.isPresent()).map(e -> e.get()).collect(Collectors.toList());
    }

    private static double distanceBetweenEdgels(Edgel e1, Edgel e2)
    {
        Vector3d distance = new Vector3d(e1.getDoublePosition(0), e1.getDoublePosition(1), e1.getDoublePosition(2));
        distance.sub(new Vector3d(e2.getDoublePosition(0), e2.getDoublePosition(1), e2.getDoublePosition(2)));
        return distance.length();
    }
}

