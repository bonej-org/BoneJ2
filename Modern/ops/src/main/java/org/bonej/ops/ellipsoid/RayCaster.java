package org.bonej.ops.ellipsoid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bonej.util.Multithreader;
import org.joml.Vector3d;

/**
 * Find the pixels visible from skeleton points for further use in fitting ellipsoids.
 * 
 * The only pixels relevant for fitting an ellipsoid at a given skeleton point are the 
 * points visible from that skeleton point.
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
	private static final double STEP_SIZE = 1/2.3;
	
	/**
	 * Iterate over all skeleton points, checking whether each foreground point is visible.
	 * will be very slow brute force.
	 * 
	 * Multithreaded over z slices (usual pattern)
	 * 
	 * @param pixels
	 * @return
	 */
	public static ArrayList<ArrayList<int[]>> getVisibleClouds(final List<Vector3d> skeletonPoints,
			final byte[][] pixels, final int w, final int h, final int d){
		final Thread[] threads = Multithreader.newThreads();
		final int nThreads = threads.length;
		@SuppressWarnings("unchecked")
		final ArrayList<ArrayList<int[]>>[] visibleCloudPointsArray = (ArrayList<ArrayList<int[]>>[]) new Object[nThreads];

		//probably same solution as usual, give each thread a copy of the below structure, then merge them at the end.
		final int nSkeletonPoints = skeletonPoints.size();
		
		//give each thread its own list of lists, will be merged later
		for (int i = 0; i < nThreads; i++) {
		//List<ArrayList<int[]>> visibleCloudPoints = new ArrayList<ArrayList<int[]>>(nSkeletonPoints);
			visibleCloudPointsArray[i] = new ArrayList<ArrayList<int[]>>(nSkeletonPoints);
		}
		
		
		//multithread this as a stream or as a Multithreader pattern.
		//have to handle potential write collisions on visibleCloudPoints
		//if multiple threads wanted to write a new visible point at the same time.
		AtomicInteger ai = new AtomicInteger(0);
		for (int thread = 0; thread < nThreads; thread++) {
			final ArrayList<ArrayList<int[]>> threadVisibleCloudPoints = visibleCloudPointsArray[thread];
			threads[thread] = new Thread(() -> {
		
//		for (int z = 0; z < d; z++) {
		for (int z = ai.getAndIncrement(); z < d; z = ai.getAndIncrement()) {
			final byte[] slice = pixels[z];
			for (int y = 0; y < h; y++) {
				final int offset = y * w;
				for (int x = 0; x < w; x++) {
					final int value = slice[offset + x];
					if (value == FORE) {
						//continue if not a surface pixel because all 'middle' pixels are occluded.
						if (!isSurface(pixels, x, y, z, w, h, d))
							continue;
						//look for fastest List iterator
						for (int i = 0; i < nSkeletonPoints; i++) {
							Vector3d v = skeletonPoints.get(i);
							final int qx = (int) v.x;
							final int qy = (int) v.y;
							final int qz = (int) v.z;
							final boolean isAVisiblePoint = isVisible(x, y, z, qx, qy, qz, w, h, d, STEP_SIZE, pixels);
							if (isAVisiblePoint) {
								//add this pixel to the list of points visible from this skeleton point
								threadVisibleCloudPoints.get(i).add(new int[] {x, y, z});
							}
						}
					}
				}
			}
		}
	});
}
		Multithreader.startAndJoin(threads);
		
		//merge all the lists into one
		//could be done in a parallel stream I guess
		ArrayList<ArrayList<int[]>> visibleCloudPoints = new ArrayList<ArrayList<int[]>>(nSkeletonPoints);
		for (int i = 0; i < nThreads; i++) {
			ArrayList<ArrayList<int[]>> threadVisibleCloudPoints = visibleCloudPointsArray[i];
			for (int s = 0; s < nSkeletonPoints; s++) {
				visibleCloudPoints.get(s).addAll(threadVisibleCloudPoints.get(s));
			}
		}
		
		return visibleCloudPoints;
	}
	
	/**
	 * Check whether this pixel (x, y, z) has any background neighbours (26 neighbourhood)
	 * @param x
	 * @param y
	 * @param z
	 * @param w
	 * @param h
	 * @param d
	 * @return true if a background neighbour pixel is found, false otherwise
	 */
	private static boolean isSurface(final byte[][] image, final int x, final int y, final int z,
			final int w, final int h, final int d) {
		
		if (isBackgroundNeighbour(image, x - 1, y - 1, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x, y - 1, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y - 1, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x - 1, y, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x, y, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x - 1, y + 1, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x, y + 1, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y + 1, z - 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x - 1, y - 1, z, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x, y - 1, z, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y - 1, z, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x - 1, y, z, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y, z, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x - 1, y + 1, z, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x, y + 1, z, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y + 1, z, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x - 1, y - 1, z + 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x, y - 1, z + 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y - 1, z + 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x - 1, y, z + 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x, y, z + 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y, z + 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x - 1, y + 1, z + 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x, y + 1, z + 1, w, h, d))
			return true;
		if (isBackgroundNeighbour(image, x + 1, y + 1, z + 1, w, h, d))
			return true;
		
		//no background neighbours were found, x, y, z is not a surface pixel
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
	private static boolean isBackgroundNeighbour(final byte[][] image,
			final int x, final int y, final int z, final int w, final int h, final int d) {
		//if pixel is within bounds and has background value it is a background neighbour
		if (withinBounds(x, y, z, w, h, d))
			if (getPixel(image, x, y, z, w) == BACK)
				return true;
		
		return false;
	}
	
	/**
	 * Get pixel in 3D image  - no bounds checking
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
	 * Check whether a straight line can be drawn between pixels p and q, which is not occluded by any foreground pixel
	 * 
	 * Note all units are in pixels, so need to decalibrate back to pixel integers.
	 * 
	 * @param px starting point x
	 * @param py
	 * @param pz
	 * @param qx
	 * @param qy
	 * @param qz
	 * @param w image width in pixels
	 * @param stepSize increment distance along vector in pixels
	 * @param image
	 * @return true if it is possible to pass in a straight line from p to q without hitting an occluding pixel.
	 */
	private static boolean isVisible(final int px, final int py, final int pz, 
			final int qx, final int qy, final int qz,
			final int w, final int h, final int d, 
			final double stepSize, final byte[][] pixels) {
		
		//calculate unit vector between the two points
		final double dx = qx - px;
		final double dy = qy - py;
		final double dz = qz - pz;
		
		final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		
		//unit vector
		final double ux = dx / distance;
		final double uy = dy / distance;
		final double uz = dz / distance;
		
		//unit vector * step size = increment
		final double stepX = ux * stepSize;
		final double stepY = uy * stepSize;
		final double stepZ = uz * stepSize;
		
		//starting at p, step along the vector until we arrive at q
		//need to bump out of the starting pixel by a unit vector 
		//otherwise starting pixel may trigger a (wrong) foreground hit
		double cursorX = px + ux;
		double cursorY = py + uy;
		double cursorZ = pz + uz;
		
		//number of steps required
		final int nSteps = (int) (distance / stepSize);
		
		for (int n = 0; n < nSteps; n++) {
			//calculate precise position along ray
			cursorX += stepX;
			cursorY += stepY;
			cursorZ += stepZ;
			
			//round it to integer discrete space
			final int x = (int) Math.round(cursorX);
			final int y = (int) Math.round(cursorY);
			final int z = (int) Math.round(cursorZ);
			
			//check if the pixel is foreground
			if (pixels[z][y * w + x] == FORE)
				return false;
			//TODO make sure logic excludes the possibility
			//of accidentally counting p or q as occluding pixels.
			//easy to do if out by 1.
		}
		//if we got to the end of the loop without hitting something else
		//p and q are visible to each other
		return true;
	}
}
