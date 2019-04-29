package org.bonej.ops.ellipsoid;

public class OptimisationParameters {
    double vectorIncrement;
    int nVectors;
    int contactSensitivity;
    int maxIterations;
    double maxDrift;

    public OptimisationParameters(double inc, int n, int cs, int maxIt, double maxDr){
        vectorIncrement = inc;
        nVectors = n;
        contactSensitivity = cs;
        maxIterations = maxIt;
        maxDrift = maxDr;
    }
}