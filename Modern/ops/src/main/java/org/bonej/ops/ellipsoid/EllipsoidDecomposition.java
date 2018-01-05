package org.bonej.ops.ellipsoid;

import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractUnaryFunctionOp;
import net.imglib2.util.ValuePair;
import org.scijava.plugin.Plugin;
import org.scijava.vecmath.Vector3d;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Tries to create an {@link Ellipsoid} decomposition from a list of vertex point with inward pointing normals
 * <p>
 * The ellipsoid decomposition is drawn from the paper of Bischoff and Kobbelt, 2002
 * </p>
 *
 * @author Alessandro Felder
 */
@Plugin(type = Op.class)
public class EllipsoidDecomposition extends AbstractUnaryFunctionOp<List<ValuePair<Vector3d,Vector3d>>,List<Ellipsoid>> {

    @Override
    public List<Ellipsoid> calculate(final List<ValuePair<Vector3d, Vector3d>> vertexNormalPairs) {

        final List<Vector3d> vertices = vertexNormalPairs.stream().map(v -> v.getA()).collect(Collectors.toList());
        final List<Double> optimalRadii = vertexNormalPairs.stream().map(vnp -> calculateOptimalRadius(vertices, vnp)).filter(r -> !r.isNaN()).collect(Collectors.toList());

        return optimalRadii.stream().map(r -> new Ellipsoid(r,r,r)).collect(Collectors.toList());
    }

    private double calculateOptimalRadius(final List<Vector3d> vertices, final ValuePair<Vector3d, Vector3d> vnp) {
        final List<Double> positiveRadii = vertices.stream().map(v -> calculateRadius(v, vnp)).filter(r -> r > 0).collect(Collectors.toList());
        positiveRadii.sort(Double::compareTo);
        if(positiveRadii.size() > 0)
            return positiveRadii.get(0);
        else
            return Double.NaN;
    }

    private double calculateRadius(Vector3d x, ValuePair<Vector3d, Vector3d> vertexWithNormal)
    {
        Vector3d xMinusP = new Vector3d(x.getX()-vertexWithNormal.getA().getX(), x.getY()-vertexWithNormal.getA().getY(), x.getZ()-vertexWithNormal.getA().getZ());
        double distanceSquared = xMinusP.lengthSquared();
        double scalarProduct = xMinusP.dot(vertexWithNormal.getB());
        if(scalarProduct > 0)
            return distanceSquared/(2*scalarProduct);
        else
            return -1.0;
    }
}