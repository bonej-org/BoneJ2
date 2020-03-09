package org.bonej.ops.ellipsoid;

/**
 * A class wrapping the optimisation parameters for {@link EllipsoidOptimisationStrategy}.
 *
 * @author Alessandro Felder
 */
public class OptimisationParameters {
    public final double vectorIncrement;
    public final int nVectors;
    public final int contactSensitivity;
    public final int maxIterations;
    public final double maxDrift;
    public final double minimumSemiAxis;

    public OptimisationParameters(double inc, int n, int cs, int maxIt, double maxDr, double minSemiAxis){
        vectorIncrement = inc;
        nVectors = n;
        contactSensitivity = cs;
        maxIterations = maxIt;
        maxDrift = maxDr;
        minimumSemiAxis = minSemiAxis;
    }
}