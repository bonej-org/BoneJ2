/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.bonej.ops.ellipsoid;

/**
 * A class wrapping the optimisation parameters for {@link EllipsoidOptimisationStrategy}.
 *
 * @author Alessandro Felder
 */
public class OptimisationParameters {
    public final int contactSensitivity;
    public final int maxIterations;
    public final double maxDrift;
		public final double vectorIncrement;

    /**
     * 
     * @param contactSensitivity how many collisions between boundary points and the ellipsoid are
     * tolerated before doing the next move.
     * @param maximumIterations Number of times to try changing the ellipsoid before giving up
     * @param maximumDrift Maximum distance the ellipsoid's centroid is allowed to drift from its seed point
     * @param vectorIncrement Step size when moving through discrete space (in pixels)
     */
    public OptimisationParameters(int contactSensitivity, int maximumIterations,
    	double maximumDrift, double vectorIncrement){
    	
        this.contactSensitivity = contactSensitivity;
        this.maxIterations = maximumIterations;
        this.maxDrift = maximumDrift;
        this.vectorIncrement = vectorIncrement;
    }
}
