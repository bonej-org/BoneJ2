package org.bonej.ops.ellipsoids;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.math3.complex.Quaternion;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;

public class Ellipsoid {

    private final double a;
    private final double b;
    private final double c;

    private final static UnitSphereRandomVectorGenerator rvg = new UnitSphereRandomVectorGenerator(4);
    private final static Random rng = new Random();

    public Ellipsoid(final double a, final double b, final double c) throws IllegalArgumentException {

        double[] radii = {a,b,c};
        if(Arrays.stream(radii).anyMatch(r -> r<=0 || !Double.isFinite(r)))
        {
            throw new IllegalArgumentException("All radii must be positive and finite numbers.");
        }
        Arrays.sort(radii);

        this.a = radii[0];
        this.b = radii[1];
        this.c = radii[2];
    }

    public List<Vector3D> sampleOnEllipsoid(int nVectors)
    {
        return sampleOnAxisAlignedEllipsoid(nVectors);
    }

    private List<Vector3D> sampleOnAxisAlignedEllipsoid(int nVectors)
    {
        final Vector3D sampleVector = new Vector3D(1, 0, 0);
        final double muMax = b * c;

        final Predicate<Vector3D> pKeepCandidate = v -> rng.nextDouble() <= mu(v) / muMax;

        // Mapping function from sphere to ellipsoid
        final Function<Vector3D, Vector3D> toEllipsoid = v -> new Vector3D(a * v
                .getX(), b * v.getY(), c * v.getZ());

        return Stream.generate(() -> randomRotation(sampleVector)).filter(pKeepCandidate).limit(nVectors).map(
                toEllipsoid).collect(toList());
    }

    private Vector3D randomRotation(Vector3D sampleVector) {
        double[] v = rvg.nextVector();
        Quaternion q = new Quaternion(v[0],v[1],v[2],v[3]);
        return rotate(sampleVector, q);

    }

    private Vector3D rotate(Vector3D v, Quaternion q)
    {
        Quaternion p = new Quaternion(v.toArray());
        final double[] rotatedQ = q.multiply(p).multiply(q.getInverse()).getVectorPart();
        return new Vector3D(rotatedQ);
    }

    private double mu(Vector3D v)
    {
        return Math.sqrt(a*a*c*c*v.getY()*v.getY()+a*a*b*b*v.getZ()*v.getZ()+b*b*c*c*v.getX()*v.getX());
    }

    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }

    public double getC() {
        return c;
    }
}

