package org.bonej.ops.ellipsoid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Vector3d;

/**
 * Find the pixels visible from skeleton points for further use in fitting
 * ellipsoids.
 * 
 * The only pixels relevant for fitting an ellipsoid at a given skeleton point
 * are the points visible from that skeleton point.
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
	
	/** Number of skeleton points to check to determine starting size of bounding box */
	private static final int BOX_SAMPLE_SIZE = 1000;

	/**
	 * Iterate over all background surface points, checking whether each skeleton point is
	 * visible. Checking for background surface points because these are the ones that will be excluded by the 
	 * ellipsoid-fitting contains() method. will be very slow brute force.
	 * 
	 * Multithreaded over z slices using new parallel streams -> lambda pattern
	 * 
	 * TODO improve efficiency by searching slices up and down (- and + in z) from the boundary pixel's slice.
	 * If there are no visible skeleton points in a slice then don't search in any more distant slices.
	 * Instead, return and continue the iteration on the next boundary pixel. Could expand this idea to x and y with
	 * + and - directions, a bounding box, or exclusion sphere. Distance between surface point and skeleton point
	 * is already calculated so just need an efficient way to set the exclusion criterion: 
	 * if (distance > tooFarAway) continue; But how to set tooFarAway?
	 * 
	 * At the moment this is threaded over slices in the foreground image, but would need a reorganisation to sort out the skeleton points
	 * into a list of lists, with the 0th index being the z-slice. Would need to think about how to do the threading model,
	 * either each surface pixel in its own thread, or each surface pixel spawns a team of threads that does the sampling vectors,
	 * bearing in mind the overhead involved in setting up a thread.
	 * 
	 * @param pixels
	 * @return
	 */
	public static ArrayList<ArrayList<int[]>> getVisibleClouds(final List<Vector3d> skeletonPoints,
		final byte[][] pixels, final int w, final int h, final int d) {

		//need to randomise skeleton points' order in the List so that bounding box isn't biased
		//by the ordered way they were added to the list
		Collections.shuffle(skeletonPoints);
		
		final int nSkeletonPoints = skeletonPoints.size();

		ArrayList<Integer> sliceNumbers = new ArrayList<>();
		@SuppressWarnings("unchecked")
		final ArrayList<ArrayList<int[]>>[] visibleCloudPointsArray = (ArrayList<ArrayList<int[]>>[]) new Object[d];

		for (int z = 0; z < d; z++) {
			sliceNumbers.add(z);
			visibleCloudPointsArray[z] = new ArrayList<>(nSkeletonPoints);
		}

		//iterate in parallel over the stack slices
		sliceNumbers.parallelStream().forEach(z -> {
			final ArrayList<ArrayList<int[]>> threadVisibleCloudPoints = visibleCloudPointsArray[z];
			final byte[] slice = pixels[z];
			for (int y = 0; y < h; y++) {
				final int offset = y * w;
				for (int x = 0; x < w; x++) {
					final int value = slice[offset + x];
					if (value == BACK) {
						if (isSurface(pixels, x, y, z, w, h, d)) {
							//we found a surface point, now check all the skeleton points

							//need this because sometimes there may be fewer skeleton points than box sampling points.
							final int boxSampling = Math.min(BOX_SAMPLE_SIZE, nSkeletonPoints);

							ArrayList<Vector3d> visibleSkeletonPoints = new ArrayList<>();
							ArrayList<Vector3d> occludedSkeletonPoints = new ArrayList<>();
							
							for (int i = 0; i < boxSampling; i++) {
								Vector3d v = skeletonPoints.get(i);
								final int qx = (int) v.x;
								final int qy = (int) v.y;
								final int qz = (int) v.z;
								final boolean isAVisiblePoint = isVisible(x, y, z, qx, qy, qz, w, h, d, STEP_SIZE,
									pixels);
								if (isAVisiblePoint) {
									visibleSkeletonPoints.add(v);
								} else {
									occludedSkeletonPoints.add(v);
								}
							}
							
							int[] boundingBox = calculateBoundingBox(x, y, z, visibleSkeletonPoints, occludedSkeletonPoints);
							 
							int xMin = boundingBox[0];
							int yMin = boundingBox[1];
							int zMin = boundingBox[2];
							int xMax = boundingBox[3];
							int yMax = boundingBox[4];
							int zMax = boundingBox[5];
							
							for (int i = boxSampling; i < nSkeletonPoints; i++) {
								
								//periodically tune the bounding box
								if (i > boxSampling && i % BOX_SAMPLE_SIZE == 0) {
									boundingBox = calculateBoundingBox(x, y, z, visibleSkeletonPoints, occludedSkeletonPoints);

									xMin = boundingBox[0];
									yMin = boundingBox[1];
									zMin = boundingBox[2];
									xMax = boundingBox[3];
									yMax = boundingBox[4];
									zMax = boundingBox[5];

								}
								Vector3d v = skeletonPoints.get(i);
								final int qx = (int) v.x;
								final int qy = (int) v.y;
								final int qz = (int) v.z;
								//don't check visibility of skeleton points outside the bounding box
								if (qx < xMin || qy < yMin || qz < zMin || qx > xMax || qy > yMax || qz > zMax)
									continue;
								final boolean isAVisiblePoint = isVisible(x, y, z, qx, qy, qz, w, h, d, STEP_SIZE,
									pixels);
								if (isAVisiblePoint) {
									// add this surface pixel (x, y, z) to the list of points visible from this skeleton point (i)
									//which is what we need to feed to Ellipsoid.contains() later.
									threadVisibleCloudPoints.get(i).add(new int[] { x, y, z });
									visibleSkeletonPoints.add(v);
								} else {
									occludedSkeletonPoints.add(v);
								}
							}
						}
					}
				}
			}
		});

		// merge all the lists into one
		// could be done in a parallel stream I guess
		ArrayList<ArrayList<int[]>> visibleCloudPoints = new ArrayList<>(nSkeletonPoints);
		for (int z = 0; z < d; z++) {
			ArrayList<ArrayList<int[]>> threadVisibleCloudPoints = visibleCloudPointsArray[z];
			for (int s = 0; s < nSkeletonPoints; s++) {
				visibleCloudPoints.get(s).addAll(threadVisibleCloudPoints.get(s));
			}
		}

		return visibleCloudPoints;
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
		ArrayList<Vector3d> visibleSkeletonPoints,
		ArrayList<Vector3d> occludedSkeletonPoints)
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
		
		//expand the box to contain all the visible skeleton points
		//by calculating the extreme (x, y, z)  values
		for (int i = 0; i < nVis; i++) {
			Vector3d v = visibleSkeletonPoints.get(i);
			final int qx = (int) v.x;
			final int qy = (int) v.y;
			final int qz = (int) v.z;
			
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
			Vector3d v = occludedSkeletonPoints.get(i);
			final int qx = (int) v.x;
			final int qy = (int) v.y;
			final int qz = (int) v.z;
			
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
			final int w, final int h, final int d, final double stepSize, byte[][] pixels) {

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
			// if we hit background then the skeleton point is not visible
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
