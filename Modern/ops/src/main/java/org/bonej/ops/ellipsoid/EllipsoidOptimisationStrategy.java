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

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_DEVICE_TYPE_ALL;
import static org.jocl.CL.CL_MEM_COPY_HOST_PTR;
import static org.jocl.CL.CL_MEM_READ_ONLY;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clReleaseCommandQueue;
import static org.jocl.CL.clReleaseContext;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clReleaseProgram;
import static org.jocl.CL.clSetKernelArg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import org.bonej.geometry.Vectors;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;

/**
 * Handles the stochastic optimisation of local ellipsoids for the Ellipsoid Factor algorithm
 * <p>
 *     Main inputs are an int[][] array representing the boundary points and a seeding point.
 *     The optimisation consists of a custom order of stochastically "bumping","wiggling" and "turning"
 *     the ellipsoid until it achieves a locally maximum volume.
 *     Returns a locally maximal ellipsoid.
 * </p>
 *
 * @author Alessandro Felder
 * @author Michael Doube
 */

public class EllipsoidOptimisationStrategy {
	private static Object deviceName;
	private static cl_context context;
	private static cl_command_queue commandQueue;
	private static cl_program program;
	private long[] imageDimensions;
	private LogService logService;
	private StatusService statusService;
	private OptimisationParameters params;// = new OptimisationParameters(1, 100, 1.73, 0.435);
	double stackVolume;
	
	/** unit vectors needed for testing whether ellipsoid is outside the volume */
	private final double[][] unitVectors = Vectors.randomVectors(20);
	
	public EllipsoidOptimisationStrategy(
		long[] imageDimensions, LogService logService,
		StatusService statusService, OptimisationParameters params, boolean useGPU) {
		this.imageDimensions = imageDimensions;
		this.logService = logService;
		this.statusService = statusService;
		this.params = params;
		if (useGPU)
			initialiseCL();
	}

	private static double[] threeWayShuffle() {
		final double[] a = {0, 0, 0};
		final double rand = Math.random();
		if (rand < 1.0 / 3.0)
			a[0] = 1;
		else if (rand >= 2.0 / 3.0)
			a[2] = 1;
		else
			a[1] = 1;
		return a;
	}

	/**
	 * return true if pixel coordinate is out of image bounds
	 *
	 * @param x
	 *            pixel x-coordinate
	 * @param y
	 *            pixel y-coordinate
	 * @param z
	 *            pixel z-coordinate
	 * @param w
	 *            image dimension in x
	 * @param h
	 *            image dimension in y
	 * @param d
	 *            image dimension in z
	 * @return true if pixel is outside image bounds, false otherwise
	 */
	private static boolean isOutOfBounds(final int x, final int y, final int z, final int w, final int h, final int d) {
		return x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= d;
	}

	/**
	 * Calculate the torque of unit normals acting at the contact points
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param contactPoints
	 *            the contact points of the ellipsoid
	 * @return the torque vector
	 */
	static double[] calculateTorque(final Ellipsoid ellipsoid, final ArrayList<int[]> contactPoints) {

		final double[] pc = ellipsoid.getCentre();
		final double cx = pc[0];
		final double cy = pc[1];
		final double cz = pc[2];

		final double[] r = ellipsoid.getRadii();
		final double a = r[0];
		final double b = r[1];
		final double c = r[2];

		final double s = 2 / (a * a);
		final double t = 2 / (b * b);
		final double u = 2 / (c * c);

		final double[][] rot = ellipsoid.getRotation();
		final double[][] inv = Ellipsoid.transpose(rot);

		double t0 = 0;
		double t1 = 0;
		double t2 = 0;

		for (final int[] p : contactPoints) {
			// translate point to centre on origin
			final double px = p[0] - cx;
			final double py = p[1] - cy;
			final double pz = p[2] - cz;

			// derotate the point
			final double x = inv[0][0] * px + inv[0][1] * py + inv[0][2] * pz;
			final double y = inv[1][0] * px + inv[1][1] * py + inv[1][2] * pz;
			final double z = inv[2][0] * px + inv[2][1] * py + inv[2][2] * pz;

			// calculate the unit normal on the centred and derotated ellipsoid
			final double nx = s * x;
			final double ny = t * y;
			final double nz = u * z;
			final double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
			final double unx = nx / length;
			final double uny = ny / length;
			final double unz = nz / length;

			// rotate the normal back to the original ellipsoid
			final double ex = rot[0][0] * unx + rot[0][1] * uny + rot[0][2] * unz;
			final double ey = rot[1][0] * unx + rot[1][1] * uny + rot[1][2] * unz;
			final double ez = rot[2][0] * unx + rot[2][1] * uny + rot[2][2] * unz;

			final double[] torqueVector = crossProduct(px, py, pz, ex, ey, ez);

			t0 += torqueVector[0];
			t1 += torqueVector[1];
			t2 += torqueVector[2];

		}
		return new double[]{-t0, -t1, -t2};
	}

	/**
	 * Calculate the mean unit vector from the ellipsoid's centroid to the contact
	 * points
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param contactPoints
	 *            the contact points
	 * @return a double array that is the mean unit vector
	 */
	private static double[] contactPointUnitVector(final Ellipsoid ellipsoid,
			final ArrayList<int[]> contactPoints) {

		final int nPoints = contactPoints.size();

		if (nPoints < 1)
			throw new IllegalArgumentException("Need at least one contact point");

		final double[] c = ellipsoid.getCentre();
		final double cx = c[0];
		final double cy = c[1];
		final double cz = c[2];
		double xSum = 0;
		double ySum = 0;
		double zSum = 0;
		for (int i = 0; i < nPoints; i++) {
			int[] p = contactPoints.get(i);
			final double x = p[0] - cx;
			final double y = p[1] - cy;
			final double z = p[2] - cz;
			final double l = Math.sqrt(x * x + y * y + z * z);

			xSum += x / l;
			ySum += y / l;
			zSum += z / l;
		}

		final double x = xSum / nPoints;
		final double y = ySum / nPoints;
		final double z = zSum / nPoints;
		final double l = Math.sqrt(x * x + y * y + z * z);

		return new double[]{x / l, y / l, z / l};
	}

	/**
	 * Calculate the cross product of 2 vectors, both in double[3] format
	 *
	 * @param a
	 *            first vector
	 * @param b
	 *            second vector
	 * @return resulting vector in double[3] format
	 */
	private static double[] crossProduct(final double[] a, final double[] b) {
		return crossProduct(a[0], a[1], a[2], b[0], b[1], b[2]);
	}

	/**
	 * Calculate the cross product of two vectors (x1, y1, z1) and (x2, y2, z2)
	 *
	 * @param x1
	 *            x-coordinate of the 1st vector.
	 * @param y1
	 *            y-coordinate of the 1st vector.
	 * @param z1
	 *            z-coordinate of the 1st vector.
	 * @param x2
	 *            x-coordinate of the 2nd vector.
	 * @param y2
	 *            y-coordinate of the 2nd vector.
	 * @param z2
	 *            z-coordinate of the 2nd vector.
	 * @return cross product in {x, y, z} format
	 */
	private static double[] crossProduct(final double x1, final double y1, final double z1, final double x2,
			final double y2, final double z2) {
		final double x = y1 * z2 - z1 * y2;
		final double y = z1 * x2 - x1 * z2;
		final double z = x1 * y2 - y1 * x2;
		return new double[]{x, y, z};
	}

	/**
	 * Rotate the ellipsoid 0.1 radians around an arbitrary unit vector
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param axis
	 *            the rotation axis
	 * @see <a href=
	 *      "https://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_and_angle">Rotation
	 *      matrix from axis and angle</a>
	 */
	static void rotateAboutAxis(final Ellipsoid ellipsoid, final double[] axis) {
		final double theta = 0.1;
		final double sin = Math.sin(theta);
		final double cos = Math.cos(theta);
		final double cos1 = 1 - cos;
		final double x = axis[0];
		final double y = axis[1];
		final double z = axis[2];
		final double xy = x * y;
		final double xz = x * z;
		final double yz = y * z;
		final double xsin = x * sin;
		final double ysin = y * sin;
		final double zsin = z * sin;
		final double xycos1 = xy * cos1;
		final double xzcos1 = xz * cos1;
		final double yzcos1 = yz * cos1;
		final double[][] rotation = {{cos + x * x * cos1, xycos1 - zsin, xzcos1 + ysin},
				{xycos1 + zsin, cos + y * y * cos1, yzcos1 - xsin},
				{xzcos1 - ysin, yzcos1 + xsin, cos + z * z * cos1},};

		ellipsoid.rotate(rotation);
	}

	/**
	 * Normalise a vector to have a length of 1 and the same orientation as the
	 * input vector a
	 *
	 * @param a
	 *            a 3D vector.
	 * @return Unit vector in direction of a
	 */
	private static double[] norm(final double[] a) {
		final double a0 = a[0];
		final double a1 = a[1];
		final double a2 = a[2];
		final double length = Math.sqrt(a0 * a0 + a1 * a1 + a2 * a2);

		final double[] normed = new double[3];
		normed[0] = a0 / length;
		normed[1] = a1 / length;
		normed[2] = a2 / length;
		return normed;
	}

	static void wiggle(Ellipsoid ellipsoid) {
		//random angles between -0.05 and +0.05 radians (range is 0.1 radians, 5.73°)
		final double wiggleRange = 0.1;
		final double a = Math.random() * wiggleRange - wiggleRange / 2;
		final double b = Math.random() * wiggleRange - wiggleRange / 2;
		final double g = Math.random() * wiggleRange - wiggleRange / 2;

		final double sina = Math.sin(a);
		final double sinb = Math.sin(b);
		final double sing = Math.sin(g);
		
		final double cosa = Math.cos(a);
		final double cosb = Math.cos(b);
		final double cosg = Math.cos(g);
	
		double[][] rotation = {
			{cosa * cosb, sina * cosb, -sinb},
			{cosa * sinb * sing - sina * cosg, sina * sinb * sing + cosa * cosg, cosb * sing},
			{cosa * sinb * cosg + sina * sing, sina * sinb * cosg - cosa * sing, cosb * cosg}
		};

		ellipsoid.rotate(rotation);
	}

	void inflateToFit(final Ellipsoid ellipsoid, ArrayList<int[]> contactPoints, final double a,
			final double b, final double c, final int[][] boundaryPoints,
			cl_kernel kernel, long[] global_work_size, cl_mem dotProductMem, Pointer dotProductsPointer, float[] dotProducts) {

		findContactPoints(ellipsoid, contactPoints, boundaryPoints, 
			kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);

		final double av = a * params.vectorIncrement;
		final double bv = b * params.vectorIncrement;
		final double cv = c * params.vectorIncrement;

		int safety = 0;
		while (contactPoints.size() < params.contactSensitivity && safety < params.maxIterations) {
			ellipsoid.dilate(av, bv, cv);
			findContactPoints(ellipsoid, contactPoints, boundaryPoints, 
				kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);
			safety++;
		}
	}

	public Ellipsoid calculate(int[][] boundaryPoints, int[] seedPoint) {
		
		final long start = System.currentTimeMillis();
		
		final int nBoundaryPoints = boundaryPoints.length;
		if (nBoundaryPoints < 6) {
			logService.info("Too few boundary points to optimise this ellipsoid, nullifying");
			return null;
		}
		
		//----set up OpenCL
		//set up flat array for OpenCL (array of float3 vectors)
		final float[] boundaryPointsAsFlatFloatArray = new float[nBoundaryPoints * 4];
		for (int p = 0; p < nBoundaryPoints; p++) {
			final int[] bp = boundaryPoints[p];
			boundaryPointsAsFlatFloatArray[p * 4] = bp[0];
			boundaryPointsAsFlatFloatArray[p * 4 + 1] = bp[1];
			boundaryPointsAsFlatFloatArray[p * 4 + 2] = bp[2];
		}
		
		//set up another array to hold a dot product for each boundary point
    float[] dotProducts = new float[nBoundaryPoints];
    
    //Make pointers that will be passed to the kernel
    Pointer boundaryPointsPointer = Pointer.to(boundaryPointsAsFlatFloatArray);
    Pointer dotProductsPointer = Pointer.to(dotProducts);
    
    cl_mem boundaryPointVectorsMem = clCreateBuffer(context, 
    	CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
        Sizeof.cl_float3 * nBoundaryPoints, boundaryPointsPointer, null);

    cl_mem dotProductMem = clCreateBuffer(context, 
        CL_MEM_READ_WRITE, 
        Sizeof.cl_float * nBoundaryPoints, null, null);
    
    // Set the work-item dimensions
    final long global_work_size[] = new long[]{nBoundaryPoints};
    
    //create the kernel
    cl_kernel kernel = clCreateKernel(program, "ellipsoid_contains", null);
    
    //pass large working data input and output
    clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(boundaryPointVectorsMem));
    clSetKernelArg(kernel, 5, Sizeof.cl_mem, Pointer.to(dotProductMem));
    
    // OpenCL setup complete ---------------------------
        
		final int w = (int) imageDimensions[0];
		final int h = (int) imageDimensions[1];
		final int d = (int) imageDimensions[2];
		stackVolume = w * h * d;

		// instantiate the contact point ArrayList
		ArrayList<int[]> contactPoints = new ArrayList<>();
		
		// Instantiate a small spherical ellipsoid
		final double[] centre = {seedPoint[0], seedPoint[1], seedPoint[2]};
		double r = getInitialRadius(centre, boundaryPoints, contactPoints);
		final double[] radii = {r, r, r};
		final double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

		Ellipsoid ellipsoid = new Ellipsoid(radii, centre, axes);
		
		final List<Double> volumeHistory = new ArrayList<>();
		volumeHistory.add(ellipsoid.getVolume());

		orientAxes(ellipsoid, contactPoints);

		// shrink the ellipsoid slightly
		shrinkToFit(ellipsoid, contactPoints, boundaryPoints, kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);
		ellipsoid.contract(0.1);
		
		// dilate other two axes until number of contact points increases
		// by contactSensitivity number of contacts

		while (contactPoints.size() < params.contactSensitivity) {
			ellipsoid.dilate(0, params.vectorIncrement, params.vectorIncrement);
			findContactPoints(ellipsoid, contactPoints, boundaryPoints, 
				kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);
			if (isInvalid(ellipsoid, w, h, d)) {
				logService.info("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
						+ ") is invalid, nullifying at initial oblation");
				return null;
			}
		}

		volumeHistory.add(ellipsoid.getVolume());

		// until ellipsoid is totally jammed within the structure, go through
		// cycles of contraction, wiggling, dilation
		// goal is maximal inscribed ellipsoid, maximal being defined by volume

		// store a copy of the 'best ellipsoid so far'
		Ellipsoid maximal = ellipsoid.copy();

		// alternately try each axis
		int totalIterations = 0;
		int noImprovementCount = 0;
		final int absoluteMaxIterations = params.maxIterations * 10;
		while (totalIterations < absoluteMaxIterations && noImprovementCount < params.maxIterations) {

			// rotate a little bit
			wiggle(ellipsoid);

			// contract until no contact
			shrinkToFit(ellipsoid, contactPoints, boundaryPoints, kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);

			// dilate an axis
			double[] abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], boundaryPoints, kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);
			
			if (isInvalid(ellipsoid, w, h, d)) {
				logService.info("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
						+ ") is invalid, nullifying after " + totalIterations + " iterations");
				return null;
			}
			
			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// find any boundary points touching the ellipsoid
			findContactPoints(ellipsoid, contactPoints, boundaryPoints, 
				kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);

			// if can't bump then do a wiggle
			if (contactPoints.isEmpty()) {
				wiggle(ellipsoid);
			} else {
				bump(ellipsoid, contactPoints, seedPoint);				
			}

			// contract
			shrinkToFit(ellipsoid, contactPoints, boundaryPoints, kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);

			// dilate an axis
			abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], boundaryPoints, kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);

			if (isInvalid(ellipsoid, w, h, d)) {
				logService.info("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
						+ ") is invalid, nullifying after " + totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// rotate a little bit
			turn(ellipsoid, contactPoints, boundaryPoints, kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);

			// contract until no contact
			shrinkToFit(ellipsoid, contactPoints, boundaryPoints, kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);

			// dilate an axis
			abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], boundaryPoints, kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);

			if (isInvalid(ellipsoid, w, h, d)) {
				logService.info("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
						+ ") is invalid, nullifying after " + totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// keep the maximal ellipsoid found
			ellipsoid = maximal.copy();

			// log its volume
			volumeHistory.add(ellipsoid.getVolume());

			// if the last value is bigger than the second-to-last value
			// reset the noImprovementCount
			// otherwise, increment it by 1.
			// if noImprovementCount exceeds a preset value the while() is
			// broken
			final int i = volumeHistory.size() - 1;
			if (volumeHistory.get(i) > volumeHistory.get(i - 1))
				noImprovementCount = 0;
			else
				noImprovementCount++;

			totalIterations++;
		}

		// this usually indicates that the ellipsoid
		// grew out of control for some reason
		if (totalIterations == absoluteMaxIterations) {
			logService.info("Ellipsoid at (" + centre[0] + ", " + centre[1] + ", " + centre[2]
					+ ") seems to be out of control, nullifying after " + totalIterations + " iterations");
			return null;
		}

		//cleanup the OpenCL resources
		clReleaseMemObject(boundaryPointVectorsMem);
		clReleaseMemObject(dotProductMem);
    clReleaseKernel(kernel);
		
		final long stop = System.currentTimeMillis();

		logService.info("Optimised ellipsoid in " + (stop - start) + " ms after " + totalIterations + " iterations ("
				+ (double) (stop - start) / totalIterations + " ms/iteration)");

		if(statusService!=null) {
			statusService.showStatus("Ellipsoid optimised at " + centreString(centre));//non-null check needed for tests
		}

		return ellipsoid;
	}

	private String centreString(double[] centre) {
		return centreString(new int[] {(int) centre[0], (int) centre[1], (int) centre[2]});
	}
	
	private String centreString(int[] centre) {
		return "("+centre[0]+", "+ centre[1]+", "+centre[2]+")";
	}

	/**
	 * Get an initial radius for an ellipsoid, which is a sphere that just fits in the boundary.
	 * Also set an initial contact point to help orient the axes for the first time
	 * 
	 * @param centre
	 * @param boundaryPoints
	 * @param contactPoints
	 * @return radius of the starting ellipsoid 
	 */
	double getInitialRadius(final double[] centre,
		final int[][] boundaryPoints, ArrayList<int[]> contactPoints){
		
		double cx = centre[0];
		double cy = centre[1];
		double cz = centre[2];
		
		double minD = Double.MAX_VALUE;
		final int n = boundaryPoints.length;
		for (int i = 0; i < n; i++) {
			final int[] p = boundaryPoints[i];
			final double dx = p[0] - cx;
			final double dy = p[1] - cy;
			final double dz = p[2] - cz;
			final double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < minD) {
				minD = d;
				contactPoints.clear();
				contactPoints.add(p);
			}
			
		}
		return minD - params.vectorIncrement;
	}

	void orientAxes(Ellipsoid ellipsoid, ArrayList<int[]> contactPoints) {
		// find the mean unit vector pointing to the points of contact from the
		// centre
		final double[] shortAxis = contactPointUnitVector(ellipsoid, contactPoints);

		// find an orthogonal axis
		final double[] xAxis = {1, 0, 0};
		final double[] yAxis = {0, 1, 0};
		double[] middleAxis = new double[3];
		if (shortAxis[0] == 1 || shortAxis[0] == -1)
			middleAxis = crossProduct(shortAxis, yAxis);
		else
			middleAxis = crossProduct(shortAxis, xAxis);
		middleAxis = norm(middleAxis);

		// find a mutually orthogonal axis by forming the cross product
		double[] longAxis = crossProduct(shortAxis, middleAxis);
		longAxis = norm(longAxis);

		// construct a rotation matrix
		double[][] rotation = {shortAxis, middleAxis, longAxis};
		rotation = Ellipsoid.transpose(rotation);

		// rotate ellipsoid to point this way...
		ellipsoid.setRotation(rotation);
	}

	void shrinkToFit(final Ellipsoid ellipsoid, ArrayList<int[]> contactPoints, final int[][] boundaryPoints,
		cl_kernel kernel, long[] global_work_size, cl_mem dotProductMem, Pointer dotProductsPointer, float[] dotProducts) {
		
		// get the contact points
		findContactPoints(ellipsoid, contactPoints, boundaryPoints, 
			kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);
		
		final int nContactPoints = contactPoints.size();
		final int[][] contactPointArray = new int[nContactPoints][3];
		for (int i = 0; i < nContactPoints; i++)
			contactPointArray[i] = contactPoints.get(i);			
		
		// contract until no contact
		int safety = 0;
		while (!contactPoints.isEmpty() && safety < params.maxIterations) {
			ellipsoid.contract(0.01);
			findContactPoints(ellipsoid, contactPoints, contactPointArray);
//			findContactPoints(ellipsoid, contactPoints, boundaryPoints, 
//				kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);
			safety++;
		}

		ellipsoid.contract(0.05);
	}

	/**
	 * Rotate the ellipsoid theta radians around the unit vector formed by the sum
	 * of torques effected by unit normals acting on the surface of the ellipsoid
	 * (if the ellipsoid surface is in contact with the foreground boundary)
	 * 
	 * @param ellipsoid
	 * @param contactPoints
	 * @param boundaryPoints
	 * @param kernel 
	 * @param global_work_size 
	 * @param dotProductMem 
	 * @param dotProductsPointer 
	 * @param dotProducts 
	 */
	void turn(Ellipsoid ellipsoid, ArrayList<int[]> contactPoints, final int[][] boundaryPoints,
		cl_kernel kernel, long[] global_work_size, cl_mem dotProductMem, Pointer dotProductsPointer, float[] dotProducts) {
		
		findContactPoints(ellipsoid, contactPoints, boundaryPoints, 
			kernel, global_work_size, dotProductMem, dotProductsPointer, dotProducts);
		
		if (!contactPoints.isEmpty()) {
			final double[] torque = calculateTorque(ellipsoid, contactPoints);
			rotateAboutAxis(ellipsoid, norm(torque));
		}
	}

	/**
	 * Check whether this ellipsoid is sensible
	 *
	 * @param ellipsoid
	 *            ellipsoids
	 * @param w
	 *            image dimension in x
	 * @param h
	 *            image dimension in y
	 * @param d
	 *            image dimension in z
	 * @return true if half or more of the surface points are outside the image
	 *         stack, if the smallest radius is less than half a pixel length, or if
	 *         the volume of the ellipsoid exceeds that of the image stack
	 */
	boolean isInvalid(final Ellipsoid ellipsoid, final int w, final int h, final int d) {
		double[][] surfacePoints = ellipsoid.getSurfacePoints(unitVectors);
		final int nSurfacePoints = surfacePoints.length;

		final double minRadius = ellipsoid.getSortedRadii()[0];
		if (minRadius < 0.5) {
			return true;
		}

		if (ellipsoid.getVolume() > stackVolume) {
			return true;
		}

		int outOfBoundsCount = 0;
		final int half = nSurfacePoints / 2;

		for (int i = 0 ; i < nSurfacePoints; i++) {
			final double[] p = surfacePoints[i];
			if (isOutOfBounds((int) (p[0]), (int) (p[1]), (int) (p[2]), w, h, d)) {
				outOfBoundsCount++;
			}
			if (outOfBoundsCount > half) {
				return true;
			}
		}		
		return false;
	}
	
	/**
	 * CPU version - better for small numbers of boundary points
	 * 
	 * @param ellipsoid
	 * @param contactPoints
	 * @param boundaryPoints
	 */
	void findContactPoints(final Ellipsoid ellipsoid, final ArrayList<int[]> contactPoints,
		final int[][] boundaryPoints) {

		contactPoints.clear();

		final int n = boundaryPoints.length;

		for (int i = 0; i < n; i++) {
			final int[] p = boundaryPoints[i]; 
			if (ellipsoid.contains(p[0], p[1], p[2]))
				contactPoints.add(p);
		}
	}

	/**
	 * GPU version, using OpenCL via JOCL. Better for large numbers (thousands) of boundary points.
	 * 
	 * @param ellipsoid
	 * @param contactPoints
	 * @param boundaryPoints
	 * @param kernel
	 * @param global_work_size
	 * @param dotProductMem
	 * @param dotProductsPointer
	 * @param dotProducts
	 */
	void findContactPoints(final Ellipsoid ellipsoid, final ArrayList<int[]> contactPoints, final int[][] boundaryPoints,
		final cl_kernel kernel, long[] global_work_size, cl_mem dotProductMem, Pointer dotProductsPointer, float[] dotProducts) {
		
		final int n = dotProducts.length;
		
		contactPoints.clear();
		
  	//update the tensor and centre in the kernel
  	setCentreAndTensor(kernel, ellipsoid.getCentre(), ellipsoid.getEllipsoidTensor());

  	// Execute the kernel
  	clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
  		global_work_size, null, 0, null, null);

  	// Read the output data
  	clEnqueueReadBuffer(commandQueue, dotProductMem, CL_TRUE, 0,
  		n * Sizeof.cl_float, dotProductsPointer, 0, null, null);

  	//check all the dot products and if any are <= 1 they are inside the ellipsoid and contact points.
  	for (int p = 0; p < n; p++) {
  		if (dotProducts[p] <= 1) {
  			contactPoints.add(boundaryPoints[p]);
  		}
  	}
  		
  	Arrays.fill(dotProducts, 0);
		
//		final int n = boundaryPoints.length;
//		long start = System.nanoTime();
//		for (int i = 0; i < n; i++) {
//			final int[] p = boundaryPoints[i]; 
//			if (ellipsoid.contains(p[0], p[1], p[2]))
//				contactPoints.add(p);
//		}
//		long end = System.nanoTime();
//		System.out.println("Checked contains() on "+n+" boundary points in "+(end-start)/1E6+" ms: "+(end-start)/(double) n+" ns per contains()");
	}

	/**
	 * Changed this to subtract along the contact vector which (I think)
	 * moves the centroid AWAY from the contact point.
	 * 
	 * If the bump would move the centroid further from the seed point
	 * than the maximum drift allowed, then it is ignored.
	 * 
	 * @param ellipsoid
	 * @param contactPoints
	 * @param seedPoint
	 */
	void bump(final Ellipsoid ellipsoid, final ArrayList<int[]> contactPoints, final int[] seedPoint) {
		
		final double displacement = params.vectorIncrement / 10;

		final double[] c = ellipsoid.getCentre();
		final double[] u = contactPointUnitVector(ellipsoid, contactPoints);
		
		final double x = c[0] - u[0] * displacement;
		final double y = c[1] - u[1] * displacement;
		final double z = c[2] - u[2] * displacement;

		final double xD = x - seedPoint[0];
		final double yD = y - seedPoint[1];
		final double zD = z - seedPoint[2];
		final double drift = Math.sqrt(xD * xD + yD * yD + zD * zD); 
				
		if (drift < params.maxDrift)
			ellipsoid.setCentroid(x, y, z);
	}

	
	//GPU handling -----------------------------
	
  /**
   * Initialize a default OpenCL context, command queue, program and kernel
   */
  private void initialiseCL()
  {
      // The platform, device type and device number
      // that will be used
      final int platformIndex = 0;
      final long deviceType = CL_DEVICE_TYPE_ALL;
      final int deviceIndex = 0;

      // Enable exceptions and subsequently omit error checks in this sample
      CL.setExceptionsEnabled(true);

      // Obtain the number of platforms
      int numPlatformsArray[] = new int[1];
      clGetPlatformIDs(0, null, numPlatformsArray);
      int numPlatforms = numPlatformsArray[0];

      // Obtain a platform ID
      cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
      clGetPlatformIDs(platforms.length, platforms, null);
      cl_platform_id platform = platforms[platformIndex];

      // Initialize the context properties
      cl_context_properties contextProperties = new cl_context_properties();
      contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
      
      // Obtain the number of devices for the platform
      int numDevicesArray[] = new int[1];
      clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
      int numDevices = numDevicesArray[0];
      
      // Obtain a device ID 
      cl_device_id devices[] = new cl_device_id[numDevices];
      clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
      cl_device_id device = devices[deviceIndex];

      // CL_DEVICE_NAME
      deviceName = getString(device, CL_DEVICE_NAME);
      logService.info("Using OpenCL program extensions on "+deviceName);
      
      // Create a context for the selected device
      context = clCreateContext(
          contextProperties, 1, new cl_device_id[]{device}, 
          null, null, null);
      
      // Create a command-queue for the selected device
      cl_queue_properties properties = new cl_queue_properties();
      commandQueue = clCreateCommandQueueWithProperties(
          context, device, properties, null);

      String programSource = "";
      try (InputStream inputStream = getClass().getResourceAsStream("/ellipsoid_contains.cl")) {
      	 ByteArrayOutputStream result = new ByteArrayOutputStream();
      	 byte[] buffer = new byte[1024];
      	 for (int length; (length = inputStream.read(buffer)) != -1; ) {
      	     result.write(buffer, 0, length);
      	 }
      	 // StandardCharsets.UTF_8.name() > JDK 7
      	 programSource = result.toString("UTF-8");
			}
			catch (IOException exc) {
				exc.printStackTrace();
			}
      
      // Create the program from the source code
      program = clCreateProgramWithSource(context,
          1, new String[]{ programSource }, null, null);
      
      // Build the program
      clBuildProgram(program, 0, null, null, null, null);
      
      // Create the kernel - do kernel creation once per call to calculate()
      //so that each ellipsoid gets optimised in its own kernel
      //kernel = clCreateKernel(program, "ellipsoidContainsKernel", null);
  }
  
  public void shutdownCL() {
    clReleaseProgram(program);
    clReleaseCommandQueue(commandQueue);
    clReleaseContext(context);
  }
  
  /**
   * Returns the value of the device info parameter with the given name
   *
   * @param device The device
   * @param paramName The parameter name
   * @return The value
   */
  private static String getString(cl_device_id device, int paramName)
  {
      // Obtain the length of the string that will be queried
      long size[] = new long[1];
      clGetDeviceInfo(device, paramName, 0, null, size);

      // Create a buffer of the appropriate size and fill it with the info
      byte buffer[] = new byte[(int)size[0]];
      clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

      // Create a string from the buffer (excluding the trailing \0 byte)
      return new String(buffer, 0, buffer.length-1);
  }
	
  /**
   * 
   * @param kernel OpenCL kernel to update
   * @param centre x,y,z centre coordinates
   * @param h ellipsoid tensor
   */
  private static void setCentreAndTensor(cl_kernel kernel, double[] centre, double[][] h) {
    //centre
    final float[] C = new float[] {(float) centre[0], (float) centre[1], (float) centre[2], 0};
    clSetKernelArg(kernel, 0, Sizeof.cl_float3, Pointer.to(C));
          
    //tensor
    final float[] Ha = new float[] {(float)h[0][0], (float)h[1][0], (float)h[2][0], 0};
    final float[] Hb = new float[] {(float)h[0][1], (float)h[1][1], (float)h[2][1], 0};
    final float[] Hc = new float[] {(float)h[0][2], (float)h[1][2], (float)h[2][2], 0};
    clSetKernelArg(kernel, 1, Sizeof.cl_float3, Pointer.to(Ha));
    clSetKernelArg(kernel, 2, Sizeof.cl_float3, Pointer.to(Hb));
    clSetKernelArg(kernel, 3, Sizeof.cl_float3, Pointer.to(Hc));
  }
  
	
}
