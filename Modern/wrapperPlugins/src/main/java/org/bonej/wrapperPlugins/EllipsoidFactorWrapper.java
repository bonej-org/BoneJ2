package org.bonej.wrapperPlugins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.edge.Edgel;
import net.imglib2.algorithm.edge.SubpixelEdgelDetection;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.ValuePair;

import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.FindLocalEllipsoidOp;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Vector3d;

import ij.ImagePlus;

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
        final List<ValuePair<Vector3d, Vector3d>> verticesAndNormals = new ArrayList<>();

        edgels.forEach(e -> verticesAndNormals.add(new ValuePair<>(new Vector3d(e.getDoublePosition(0), e.getDoublePosition(1), e.getDoublePosition(2)), new Vector3d(e.getGradient()))));

        List<Ellipsoid> ellipsoids = getLocalEllipsoids(verticesAndNormals);
        ellipsoids.sort(Comparator.comparingDouble(e -> e.getVolume()));

        //find EF values

    }

    private List<Ellipsoid> getLocalEllipsoids(final List<ValuePair<Vector3d,Vector3d>> verticesAndNormals){
        final List<Vector3d> vertices = verticesAndNormals.stream().map(v->v.getA()).collect(Collectors.toList());
        return verticesAndNormals.stream().map(v -> findLocalEllipsoidOp.calculate(vertices, v)).filter(e -> e.isPresent()).map(e -> e.get()).collect(Collectors.toList());
    }
}

