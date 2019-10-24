package org.bonej.ops.ellipsoid;

/**
 * A class wrapping the optimisation parameters for {@link EllipsoidOptimisationStrategy}.
 *
 * @author Alessandro Felder
 */
public class OptimisationParameters {
    double vectorIncrement;
    int nVectors;
    int contactSensitivity;
    int maxIterations;
    double maxDrift;
    double minimumSemiAxis;

    public OptimisationParameters(double inc, int n, int cs, int maxIt, double maxDr, double minSemiAxis){
        vectorIncrement = inc;
        nVectors = n;
        contactSensitivity = cs;
        maxIterations = maxIt;
        maxDrift = maxDr;
        minimumSemiAxis = minSemiAxis;
    }
}