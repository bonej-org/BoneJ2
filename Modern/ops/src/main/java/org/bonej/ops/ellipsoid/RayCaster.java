package org.bonej.ops.ellipsoid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.IntStream;

/**
 * Find the pixels visible from seed points for further use in fitting
 * ellipsoids.
 * 
 * The only pixels relevant for fitting an ellipsoid at a given seed point
 * are the points visible from that seed point.
 * 
 * @author Michael Doube
 *
 */
public class RayCaster {

	/** Foreground value */
	public static final int FORE = -1;
	/** Background value */
	public static final int BACK = 0;

	/** Nyquist sampling of the edge length */
	private static final double STEP_SIZE = 1 / 2.3;
	
	/** Number of seed points to check to determine starting size of bounding box */
	private static final int BOX_SAMPLE_SIZE = 1000;

	/**
	 * Iterate over all background surface points, checking whether each seed point is
	 * visible. Checking for background surface points because these are the ones that will be excluded by the 
	 * ellipsoid-fitting contains() method. will be very slow brute force.
	 * 
	 * Multithreaded over z slices using new parallel streams -> lambda pattern
	 * 
	 * TODO improve efficiency by searching slices up and down (- and + in z) from the boundary pixel's slice.
	 * If there are no visible seed points in a slice then don't search in any more distant slices.
	 * Instead, return and continue the iteration on the next boundary pixel. Could expand this idea to x and y with
	 * + and - directions, a bounding box, or exclusion sphere. Distance between surface point and seed point
	 * is already calculated so just need an efficient way to set the exclusion criterion: 
	 * if (distance > tooFarAway) continue; But how to set tooFarAway?
	 * 
	 * At the moment this is threaded over slices in the foreground image, but would need a reorganisation to sort out the seed points
	 * into a list of lists, with the 0th index being the z-slice. Would need to think about how to do the threading model,
	 * either each surface pixel in its own thread, or each surface pixel spawns a team of threads that does the sampling vectors,
	 * bearing in mind the overhead involved in setting up a thread.
	 * 
	 * @param pixels
	 * @return
	 */
	public static HashMap<int[], ArrayList<int[]>> getVisibleClouds(final int[][] seedPoints,
		final byte[][] pixels, final int w, final int h, final int d) {

		final int nSeedPoints = seedPoints.length;

		//HashMap with boundaryPoint int[3] as key and seedPoints ArrayList<int[3]> as the value
		//this is the opposite of what we want in the end
		//list of these, one per slice to avoid contention / synchronisation.
		ArrayList<HashMap<int[], ArrayList<int[]>>> seedPointsPerBoundaryPointList = new ArrayList<>();
		for (int i = 0; i < d; i++){
			seedPointsPerBoundaryPointList.add(null);
		}
		
		
		//iterate in parallel over the stack slices
		IntStream.range(0, d).parallel().forEach( z -> {
			final byte[] slice = pixels[z];
			final HashMap<int[], ArrayList<int[]>> seedPointsPerBoundaryPoint = new HashMap<>();
			for (int y = 0; y < h; y++) {
				final int offset = y * w;
				for (int x = 0; x < w; x++) {
					final int value = slice[offset + x];
					if (value == BACK) {
						if (isSurface(pixels, x, y, z, w, h, d)) {
							//we found a surface point, now check all the seed points

							//need this because sometimes there may be fewer seed points than box sampling points.
							final int boxSampling = Math.min(BOX_SAMPLE_SIZE, nSeedPoints);

							ArrayList<int[]> visibleSeedPoints = new ArrayList<>();
							ArrayList<int[]> occludedSeedPoints = new ArrayList<>();
							
							for (int i = 0; i < boxSampling; i++) {
								final int[] p = seedPoints[i];
								if (p == null) {
									throw new NullPointerException("Found a null seedPoint!");
								}
								final int qx = p[0];
								final int qy = p[1];
								final int qz = p[2];
								final boolean isAVisiblePoint = isVisible(x, y, z, qx, qy, qz, w, STEP_SIZE,
									pixels);
								if (isAVisiblePoint) {
									visibleSeedPoints.add(p);
								} else {
									occludedSeedPoints.add(p);
								}
							}
							
							int[] boundingBox = calculateBoundingBox(x, y, z, visibleSeedPoints, occludedSeedPoints);
							 
							int xMin = boundingBox[0];
							int yMin = boundingBox[1];
							int zMin = boundingBox[2];
							int xMax = boundingBox[3];
							int yMax = boundingBox[4];
							int zMax = boundingBox[5];
							
							for (int i = boxSampling; i < nSeedPoints; i++) {
								
								//periodically tune the bounding box
								if (i > boxSampling && i % BOX_SAMPLE_SIZE == 0) {
									boundingBox = calculateBoundingBox(x, y, z, visibleSeedPoints, occludedSeedPoints);

									xMin = boundingBox[0];
									yMin = boundingBox[1];
									zMin = boundingBox[2];
									xMax = boundingBox[3];
									yMax = boundingBox[4];
									zMax = boundingBox[5];

								}
								final int[] p = seedPoints[i];
								final int qx = p[0];
								final int qy = p[1];
								final int qz = p[2];
								//don't check visibility of seed points outside the bounding box
								if (qx < xMin || qy < yMin || qz < zMin || qx > xMax || qy > yMax || qz > zMax)
									continue;
								final boolean isAVisiblePoint = isVisible(x, y, z, qx, qy, qz, w, STEP_SIZE,
									pixels);
								if (isAVisiblePoint) {									
									visibleSeedPoints.add(p);
								} else {
									occludedSeedPoints.add(p);
								}
							}
							//enter the full list of visible seed points to the hashmap keyed to the boundary point
							int[] boundaryPoint = new int[] {x, y, z};
							seedPointsPerBoundaryPoint.put(boundaryPoint, visibleSeedPoints);
//							System.out.println("RayTracer found "+visibleSeedPoints.size()+" seed points for boundary point ("+x+", "+y+", "+z+")");
						}
					}
				}
			}
			seedPointsPerBoundaryPointList.set(z, seedPointsPerBoundaryPoint);
		});
		
		//HashMap with seedPoint int[3] as key and boundary point ArrayList<int[3]> as the value
		HashMap<int[], ArrayList<int[]>> boundaryPointsPerSeedPoint = new HashMap<>();
		
		IntStream.range(0, nSeedPoints).forEach(i -> {
			boundaryPointsPerSeedPoint.put(seedPoints[i], new ArrayList<int[]>());
		});

		//re-arrange the list to get list of boundaryPoints for each seedPoint
		seedPointsPerBoundaryPointList.forEach(seedPointsPerBoundaryPoint -> {
			seedPointsPerBoundaryPoint.entrySet().stream().forEach(entry -> {
				int[] boundaryPoint = entry.getKey();
				ArrayList<int[]> seedPointList = entry.getValue();
				seedPointList.forEach(seedPoint -> {
					boundaryPointsPerSeedPoint.get(seedPoint).add(boundaryPoint);
				});
			});
		});
		
		boundaryPointsPerSeedPoint.entrySet().stream().forEach(entry -> {
			final int[] seedPoint = entry.getKey();
			final ArrayList<int[]> boundaryPoints = entry.getValue();
			System.out.println("RayCaster found "+boundaryPoints.size()+" boundary points for seed point ("+seedPoint[0]+", "+seedPoint[1]+", "+seedPoint[2]+")");
		});
		
		return boundaryPointsPerSeedPoint;
	}

	/**
	 * @param x
	 * @param y
	 * @param z
	 * @param visibleSkeletonPoints
	 * @param occludedSkeletonPoints
	 * @return
	 */
	private static int[] calculateBoundingBox(
		final int x, final int y, final int z,
		ArrayList<int[]> visibleSkeletonPoints,
		ArrayList<int[]> occludedSkeletonPoints)
	{
		//+- x, y, z limits of visible points
		//start with a tiny box centred on the surface point
		int xMinVis = x;
		int yMinVis = y;
		int zMinVis = z;
		int xMaxVis = x;
		int yMaxVis = y;
		int zMaxVis = z;

		final int nVis = visibleSkeletonPoints.size();
		
		//expand the box to contain all the visible seed points
		//by calculating the extreme (x, y, z)  values
		for (int i = 0; i < nVis; i++) {
			int[] v = visibleSkeletonPoints.get(i);
			final int qx = v[0];
			final int qy = v[1];
			final int qz = v[2];
			
			xMinVis = Math.min(xMinVis, qx);
			yMinVis = Math.min(yMinVis, qy);
			zMinVis = Math.min(zMinVis, qz);
			
			xMaxVis = Math.max(xMaxVis, qx);
			yMaxVis = Math.max(yMaxVis, qy);
			zMaxVis = Math.max(zMaxVis, qz);
		}
		
		//now find the nearest occluded point outside the visible point box
		
		//+- x, y, z limits of occluded points
		int xMinOcc = 0;
		int yMinOcc = 0;
		int zMinOcc = 0;
		int xMaxOcc = Integer.MAX_VALUE;
		int yMaxOcc = Integer.MAX_VALUE;
		int zMaxOcc = Integer.MAX_VALUE;

		final int nOcc = occludedSkeletonPoints.size();
		
		for (int i = 0; i < nOcc; i++) {
			int[] v = occludedSkeletonPoints.get(i);
			final int qx = v[0];
			final int qy = v[1];
			final int qz = v[2];
			
			//q needs to be more extreme than the vis box
			//and the least extreme occluded value

			if (qx < xMinVis && qx > xMinOcc) xMinOcc = qx;
			if (qy < yMinVis && qy > yMinOcc)	yMinOcc = qy;
			if (qz < zMinVis && qz > zMinOcc)	zMinOcc = qz;
			if (qx > xMaxVis && qx < xMaxOcc)	xMaxOcc = qx;
			if (qy > yMaxVis && qy < yMaxOcc)	yMaxOcc = qy;
			if (qz > zMaxVis && qz < zMaxOcc)	zMaxOcc = qz;
			
		}
		
		final int[] boundingBox = {
			xMinOcc,
			yMinOcc,
			zMinOcc,
			xMaxOcc,
			yMaxOcc,
			zMaxOcc
		};
		
		return boundingBox;
	}

	/**
	 * Check whether this pixel (x, y, z) has any foreground neighbours (26
	 * neighbourhood)
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @param w
	 * @param h
	 * @param d
	 * @return true if a foreground neighbour pixel is found, false otherwise
	 */
	private static boolean isSurface(final byte[][] image, final int x, final int y, final int z, final int w,
			final int h, final int d) {

		if (isForegoundNeighbour(image, x - 1, y - 1, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x, y - 1, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y - 1, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x - 1, y, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x, y, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x - 1, y + 1, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x, y + 1, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y + 1, z - 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x - 1, y - 1, z, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x, y - 1, z, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y - 1, z, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x - 1, y, z, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y, z, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x - 1, y + 1, z, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x, y + 1, z, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y + 1, z, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x - 1, y - 1, z + 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x, y - 1, z + 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y - 1, z + 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x - 1, y, z + 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x, y, z + 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y, z + 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x - 1, y + 1, z + 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x, y + 1, z + 1, w, h, d))
			return true;
		if (isForegoundNeighbour(image, x + 1, y + 1, z + 1, w, h, d))
			return true;

		// no foreground neighbours were found, x, y, z is not a surface pixel
		return false;
	}

	/**
	 * 
	 * @param image
	 * @param x
	 * @param y
	 * @param z
	 * @param w
	 * @param h
	 * @param d
	 * @return
	 */
	private static boolean isForegoundNeighbour(final byte[][] image, final int x, final int y, final int z,
			final int w, final int h, final int d) {
		// if neighbour pixel is within bounds and has foreground value it is a foreground
		// neighbour
		if (withinBounds(x, y, z, w, h, d))
			if (getPixel(image, x, y, z, w) == FORE)
				return true;

		return false;
	}

	/**
	 * Get pixel in 3D image - no bounds checking
	 *
	 * @param image 3D image
	 * @param x     x- coordinate
	 * @param y     y- coordinate
	 * @param z     z- coordinate (in image stacks the indexes start at 1)
	 * @param w     width of the image.
	 * @return corresponding pixel
	 */
	private static byte getPixel(final byte[][] image, final int x, final int y, final int z, final int w) {
		return image[z][x + y * w];
	}

	/**
	 * checks whether a pixel at (m, n, o) is within the image boundaries
	 * 
	 * 26- and 6-neighbourhood version
	 * 
	 * @param m x coordinate
	 * @param n y coordinate
	 * @param o z coordinate
	 * @param w image width
	 * @param h image height
	 * @param d image depth
	 * @return true if the pixel is within the image bounds
	 */
	private static boolean withinBounds(final int m, final int n, final int o, final int w, final int h, final int d) {
		return (m >= 0 && m < w && n >= 0 && n < h && o >= 0 && o < d);
	}

	/**
	 * Check whether a straight line can be drawn between pixels p and q, which is
	 * not occluded by any background pixel
	 * 
	 * Note all units are in pixels, so need to decalibrate back to pixel integers.
	 * 
	 * @param px       starting point x
	 * @param py
	 * @param pz
	 * @param qx
	 * @param qy
	 * @param qz
	 * @param w        image width in pixels
	 * @param stepSize increment distance along vector in pixels
	 * @param pixels
	 * @return true if it is possible to pass in a straight line from p to q without
	 *         hitting an occluding pixel.
	 */
	private static boolean isVisible(final int px, final int py, final int pz, final int qx, final int qy, final int qz,
			final int w, final double stepSize, byte[][] pixels) {

		// calculate unit vector between the two points
		final double dx = qx - px;
		final double dy = qy - py;
		final double dz = qz - pz;

		final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		
		// unit vector
		final double ux = dx / distance;
		final double uy = dy / distance;
		final double uz = dz / distance;

		// unit vector * step size = increment
		final double stepX = ux * stepSize;
		final double stepY = uy * stepSize;
		final double stepZ = uz * stepSize;

		// starting at p, step along the vector until we arrive at q
		// need to bump out of the starting pixel by a unit vector
		// otherwise starting pixel may trigger a (wrong) background hit
		double cursorX = px + ux;
		double cursorY = py + uy;
		double cursorZ = pz + uz;

		// number of steps required
		final int nSteps = (int) (distance / stepSize);

		for (int n = 0; n < nSteps; n++) {
			// calculate precise position along ray
			cursorX += stepX;
			cursorY += stepY;
			cursorZ += stepZ;

			// round it to integer discrete space
			final int x = (int) Math.round(cursorX);
			final int y = (int) Math.round(cursorY);
			final int z = (int) Math.round(cursorZ);

			// check if the pixel is background
			// if we hit background then the seed point is not visible
			if (pixels[z][y * w + x] == BACK)
				return false;
			// TODO make sure logic excludes the possibility
			// of accidentally counting p or q as occluding pixels.
			// easy to do if out by 1.
		}
		// if we got to the end of the loop without hitting something else
		// p and q are visible to each other
		return true;
	}
}
