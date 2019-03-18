/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.wrapperPlugins;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.imglib2.roi.Regions.countTrue;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.bonej.ops.skeletonize.FindRidgePoints;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;
import org.scijava.ui.UIService;

import net.imagej.ImgPlus;
import net.imagej.display.ColorTables;
import net.imagej.ops.OpService;
import net.imagej.units.UnitService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * ImageJ plugin to describe the local geometry of a binary image in an
 * oblate/prolate spheroid space. Uses Skeletonize3D to generate a 3D skeleton,
 * the points of which are used as centres for maximally inscribed ellipsoids.
 * The ellipsoid factor (EF) is a method for the local determination of the rod-
 * or plate-like nature of porous or spongy continua. EF at a point within a 3D
 * structure is defined as the difference in axis ratios of the greatest
 * ellipsoid that fits inside the structure and that contains the point of
 * interest, and ranges from −1 for strongly oblate (discus-shaped) ellipsoids,
 * to +1 for strongly prolate (javelin-shaped) ellipsoids. For an ellipsoid with
 * axes a ≤ b ≤ c, EF = a/b − b/c.
 *
 * @author Michael Doube
 * @author Alessandro Felder
 * @see <a href="http://dx.doi.org/10.3389/fendo.2015.00015">
 *      "The ellipsoid factor for quantification of rods, plates, and intermediate forms in 3D geometries"
 *      Frontiers in Endocrinology (2015)</a>
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor 2")
public class EllipsoidFactorWrapper extends ContextCommand {

	// Several ellipsoids may fall in same bin if this is too small a number!
	// This will be ignored!
	private static final long FLINN_PLOT_DIMENSION = 501;

	private static final String NO_ELLIPSOIDS_FOUND = "No ellipsoids were found - try modifying input parameters.";
	private static Random rng = new Random();
	@SuppressWarnings("unused")
	@Parameter
	private UnitService unitService;
	@SuppressWarnings("unused")
	@Parameter
	private CommandService commandService;
	@SuppressWarnings("unused")
	@Parameter
	private OpService opService;
	@SuppressWarnings("unused")
	@Parameter
	private LogService logService;
	@SuppressWarnings("unused")
	@Parameter
	private StatusService statusService;
	@SuppressWarnings("unused")
	@Parameter
	private UIService uiService;
	// private boolean calibrationWarned;
	@SuppressWarnings("unused")
	@Parameter
	private ImgPlus<UnsignedIntType> inputImgPlus;
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String setup = "Setup";
	@Parameter(label = "Vectors")
	private int nVectors = 100;
	@Parameter(label = "Sampling_increment", description = "Increment for vector searching in real units. Default is ~Nyquist sampling of a unit pixel.")
	private double vectorIncrement = 1 / 2.3;
	@Parameter(label = "Skeleton_points per ellipsoid", description = "Number of skeleton points per ellipsoid. Sets the granularity of the ellipsoid fields.")
	private int skipRatio = 50;
	@Parameter(label = "Contact sensitivity")
	private int contactSensitivity = 1;
	@Parameter(label = "Maximum_iterations", description = "Maximum iterations to try improving ellipsoid fit before stopping.")
	private int maxIterations = 100;
	@Parameter(label = "Maximum_drift", description = "maximum distance ellipsoid may drift from seed point. Defaults to unit voxel diagonal length")
	private double maxDrift = Math.sqrt(3);
	private double stackVolume;
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String outputs = "Outputs";
	@Parameter(label = "Show secondary images")
	private boolean showSecondaryImages = false;
	@Parameter(label = "Gaussian_sigma")
	private double sigma = 2;

	@Parameter(label = "EF image", type = ItemIO.OUTPUT)
	private ImgPlus<FloatType> efImage;

	@Parameter(label = "ID image", type = ItemIO.OUTPUT)
	private ImgPlus<IntType> eIdImage;

	@Parameter(label = "Volume Image", type = ItemIO.OUTPUT)
	private ImgPlus<FloatType> vImage;

	@Parameter(label = "a/b Image", type = ItemIO.OUTPUT)
	private ImgPlus<FloatType> aToBAxisRatioImage;

	@Parameter(label = "b/c Image", type = ItemIO.OUTPUT)
	private ImgPlus<FloatType> bToCAxisRatioImage;

	@Parameter(label = "Unweighted Flinn Plot", type = ItemIO.OUTPUT)
	private ImgPlus<BitType> flinnPlotImage;

	@Parameter(label = "Flinn Peak Plot", type = ItemIO.OUTPUT)
	private ImgPlus<FloatType> flinnPeakPlotImage;

	/**
	 * The EF results in a {@link Table}, null if there are no results
	 */
	@Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
	private Table<DefaultColumn<Double>, Double> resultsTable;
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String note = "Ellipsoid Factor is beta software.\n" + "Please report your experiences to the user group:\n"
			+ "http://forum.image.sc/tags/bonej";

	/**
	 * Method to numerically approximate equidistantly spaced points on the surface
	 * of a sphere
	 * <p>
	 * The implementation follows the description of the theoretical work by
	 * Rakhmanov et al., 1994 in Saff and Kuijlaars, 1997
	 * (<a href="doi:10.1007/BF03024331">dx.doi.org/10.1007/BF03024331</a>), but k
	 * is shifted by one to the left for more convenient indexing.
	 *
	 * @param n
	 *            : number of points required (has to be > 2)
	 *            <p>
	 *            This could be its own op in the future.
	 *            </p>
	 */
	static double[][] getGeneralizedSpiralSetOnSphere(final int n) {
		final Stream.Builder<double[]> spiralSet = Stream.builder();
		final List<Double> phi = new ArrayList<>();
		phi.add(0.0);
		for (int k = 1; k < n - 1; k++) {
			final double h = -1.0 + 2.0 * k / (n - 1);
			phi.add(getPhiByRecursion(n, phi.get(k - 1), h));
		}
		phi.add(0.0);

		for (int k = 0; k < n; k++) {
			final double h = -1.0 + 2.0 * k / (n - 1);
			final double theta = Math.acos(h);
			spiralSet.add(new double[]{Math.sin(theta) * Math.cos(phi.get(k)), Math.sin(theta) * Math.sin(phi.get(k)),
					Math.cos(theta)});

		}
		List<double[]> list = spiralSet.build().collect(toList());
		return list.toArray(new double[n][]);
	}

	private static double getPhiByRecursion(final double n, final double phiKMinus1, final double hk) {
		final double phiK = phiKMinus1 + 3.6 / Math.sqrt(n) * 1.0 / Math.sqrt(1 - hk * hk);
		// modulo 2pi calculation works for positive numbers only, which is not a
		// problem in this case.
		return phiK - Math.floor(phiK / (2 * Math.PI)) * 2 * Math.PI;
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
	static double[] calculateTorque(final QuickEllipsoid ellipsoid, final Iterable<double[]> contactPoints) {

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
		final double[][] inv = QuickEllipsoid.transpose(rot);

		double t0 = 0;
		double t1 = 0;
		double t2 = 0;

		for (final double[] p : contactPoints) {
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
			final double length = new Vector3d(nx, ny, nz).length();
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

	private static float computeEllipsoidFactor(final QuickEllipsoid ellipsoid) {
		final double[] sortedRadii = ellipsoid.getSortedRadii();
		return (float) (sortedRadii[0] / sortedRadii[1] - sortedRadii[1] / sortedRadii[2]);
	}

	/**
	 * Calculate the mean unit vector between the ellipsoid's centroid and contact
	 * points
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param contactPoints
	 *            the contact points
	 * @return a double array that is the mean unit vector
	 */
	private static double[] contactPointUnitVector(final QuickEllipsoid ellipsoid,
			final Collection<double[]> contactPoints) {

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
		for (final double[] p : contactPoints) {
			final double x = p[0] - cx;
			final double y = p[1] - cy;
			final double z = p[2] - cz;
			final double l = new Vector3d(x, y, z).length();

			xSum += x / l;
			ySum += y / l;
			zSum += z / l;
		}

		final double x = xSum / nPoints;
		final double y = ySum / nPoints;
		final double z = zSum / nPoints;
		final double l = new Vector3d(x, y, z).length();

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

	static void findContactPointsForGivenDirections(final QuickEllipsoid ellipsoid, final ArrayList<double[]> contactPoints,
													final double[][] unitVectors, final byte[][] pixels, final double pW, final double pH, final double pD,
													final int w, final int h, final int d) {
		contactPoints.clear();
		final double[][] points = ellipsoid.getSurfacePoints(unitVectors);
		for (final double[] p : points) {
			final int x = (int) Math.floor(p[0] / pW);
			final int y = (int) Math.floor(p[1] / pH);
			final int z = (int) Math.floor(p[2] / pD);
			if (isOutOfBounds(x, y, z, w, h, d)) {
				continue;
			}
			if (pixels[z][y * w + x] != -1) {
				contactPoints.add(p);
			}
		}
	}

	private static double[][] findContactUnitVectors(final QuickEllipsoid ellipsoid,
			final ArrayList<double[]> contactPoints) {
		final double[][] unitVectors = new double[contactPoints.size()][3];
		final double[] c = ellipsoid.getCentre();
		final double cx = c[0];
		final double cy = c[1];
		final double cz = c[2];

		for (int i = 0; i < contactPoints.size(); i++) {
			final double[] p = contactPoints.get(i);
			final double px = p[0];
			final double py = p[1];
			final double pz = p[2];

			Vector3d distance = new Vector3d(px, py, pz);
			distance.sub(new Vector3d(cx, cy, cz));
			final double l = distance.length();
			final double x = (px - cx) / l;
			final double y = (py - cy) / l;
			final double z = (pz - cz) / l;
			final double[] u = {x, y, z};
			unitVectors[i] = u;
		}
		return unitVectors;
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

	/**
	 * Rotate the ellipsoid 0.1 radians around an arbitrary unit vector
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param axis
	 *            the rotation axis
	 * @see <a href=
	 *      "http://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_and_angle">Rotation
	 *      matrix from axis and angle</a>
	 */
	private static void rotateAboutAxis(final QuickEllipsoid ellipsoid, final double[] axis) {
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
	 * Rotate the ellipsoid by a small random amount
	 *
	 * @param ellipsoid
	 *            the ellipsoid to rotate
	 */
	private static void wiggle(final QuickEllipsoid ellipsoid) {

		final double b = Math.random() * 0.2 - 0.1;
		final double c = Math.random() * 0.2 - 0.1;
		final double a = Math.sqrt(1 - b * b - c * c);

		// zeroth column, should be very close to [1, 0, 0]^T (mostly x)
		final double[] zerothColumn = {a, b, c};

		// form triangle in random plane
		final double[] vector = new double[]{rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian()};

		// first column, should be very close to [0, 1, 0]^T
		final double[] firstColumn = norm(crossProduct(zerothColumn, vector));

		// second column, should be very close to [0, 0, 1]^T
		final double[] secondColumn = norm(crossProduct(zerothColumn, firstColumn));

		double[][] rotation = {zerothColumn, firstColumn, secondColumn};

		// array has subarrays as rows, need them as columns
		rotation = QuickEllipsoid.transpose(rotation);

		ellipsoid.rotate(rotation);
	}

	private void createAToBImage(final double[] aBRatios, final IterableInterval<IntType> ellipsoidIDs) {
		final Img<FloatType> aToBImage = createNaNCopy();
		mapValuesToImage(aBRatios, ellipsoidIDs, aToBImage);
		aToBAxisRatioImage = new ImgPlus<>(aToBImage, "a/b");
		aToBAxisRatioImage.setChannelMaximum(0, 1.0f);
		aToBAxisRatioImage.setChannelMinimum(0, 0.0f);
	}

	private void createBToCImage(final double[] bCRatios, final IterableInterval<IntType> ellipsoidIDs) {
		final Img<FloatType> bToCImage = createNaNCopy();
		mapValuesToImage(bCRatios, ellipsoidIDs, bToCImage);
		bToCAxisRatioImage = new ImgPlus<>(bToCImage, "b/c");
		bToCAxisRatioImage.setChannelMaximum(0, 1.0f);
		bToCAxisRatioImage.setChannelMinimum(0, 0.0f);
	}

	private void mapValuesToImage(final double[] values, final IterableInterval<IntType> ellipsoidIdentityImage,
			final RandomAccessible<FloatType> ellipsoidFactorImage) {
		final RandomAccess<FloatType> ef = ellipsoidFactorImage.randomAccess();
		final Cursor<IntType> id = ellipsoidIdentityImage.localizingCursor();
		final long[] position = new long[3];
		while (id.hasNext()) {
			id.fwd();
			if (id.get().getInteger() < 0) {
				continue;
			}
			id.localize(position);
			final double value = values[id.get().getInteger()];
			ef.setPosition(position);
			ef.get().setReal(value);
		}
	}

	private void createEFImage(final Collection<QuickEllipsoid> ellipsoids, final IterableInterval<IntType> ellipsoidIDs) {
		final Img<FloatType> ellipsoidFactorImage = createNaNCopy();
		final double[] ellipsoidFactors = ellipsoids.parallelStream()
				.mapToDouble(EllipsoidFactorWrapper::computeEllipsoidFactor).toArray();
		mapValuesToImage(ellipsoidFactors, ellipsoidIDs, ellipsoidFactorImage);
		efImage = new ImgPlus<>(ellipsoidFactorImage, "EF");
		efImage.setChannelMaximum(0, 1);
		efImage.setChannelMinimum(0, -1);
		efImage.initializeColorTables(1);
		efImage.setColorTable(ColorTables.FIRE, 0);
	}

	private void createFlinnPeakPlot(final double[] aBRatios, final double[] bCRatios,
			final Img<IntType> ellipsoidIDs) {
		Img<FloatType> flinnPeakPlot = ArrayImgs.floats(FLINN_PLOT_DIMENSION, FLINN_PLOT_DIMENSION);
		final RandomAccess<FloatType> flinnPeakPlotRA = flinnPeakPlot.randomAccess();
		final RandomAccess<IntType> idAccess = ellipsoidIDs.randomAccess();
		final Cursor<IntType> idCursor = ellipsoidIDs.localizingCursor();
		final long[] position = new long[3];
		while (idCursor.hasNext()) {
			idCursor.fwd();
			if (idCursor.get().getInteger() < 0) {
				continue;
			}
			idCursor.localize(position);
			idAccess.setPosition(position);
			final int localMaxEllipsoidID = idAccess.get().getInteger();
			final long x = Math.round(aBRatios[localMaxEllipsoidID] * (FLINN_PLOT_DIMENSION - 1));
			final long y = Math.round(bCRatios[localMaxEllipsoidID] * (FLINN_PLOT_DIMENSION - 1));
			flinnPeakPlotRA.setPosition(new long[]{x, FLINN_PLOT_DIMENSION - y - 1});
			final float currentValue = flinnPeakPlotRA.get().getRealFloat();
			flinnPeakPlotRA.get().set(currentValue + 1.0f);
		}
		if (sigma > 0.0) {
			flinnPeakPlot = (Img<FloatType>) opService.filter().gauss(flinnPeakPlot, sigma);
		}
		flinnPeakPlotImage = new ImgPlus<>(flinnPeakPlot, "Flinn Peak Plot");
		flinnPeakPlotImage.setChannelMaximum(0, 255.0f);
		flinnPeakPlotImage.setChannelMinimum(0, 0.0f);
	}

	private void createFlinnPlotImage(final double[] aBRatios, final double[] bCRatios) {
		final Img<BitType> flinnPlot = ArrayImgs.bits(FLINN_PLOT_DIMENSION, FLINN_PLOT_DIMENSION);
		final RandomAccess<BitType> flinnRA = flinnPlot.randomAccess();
		for (int i = 0; i < aBRatios.length; i++) {
			final long x = Math.round(aBRatios[i] * (FLINN_PLOT_DIMENSION - 1));
			final long y = FLINN_PLOT_DIMENSION - Math.round(bCRatios[i] * (FLINN_PLOT_DIMENSION - 1)) - 1;
			flinnRA.setPosition(x, 0);
			flinnRA.setPosition(y, 1);
			flinnRA.get().setOne();
		}
		flinnPlotImage = new ImgPlus<>(flinnPlot, "Unweighted Flinn Plot");
		flinnPlotImage.setChannelMaximum(0, 255.0f);
		flinnPlotImage.setChannelMinimum(0, 0.0f);
	}

	private Img<FloatType> createNaNCopy() {
		final ArrayImg<FloatType, FloatArray> copy = ArrayImgs.floats(inputImgPlus.dimension(0),
				inputImgPlus.dimension(1), inputImgPlus.dimension(2));
		copy.forEach(e -> e.setReal(Float.NaN));
		return copy;
	}

	private void createPrimaryOutputImages(final List<QuickEllipsoid> ellipsoids, final Img<IntType> ellipsoidIDs) {
		createEFImage(ellipsoids, ellipsoidIDs);
		createVolumeImage(ellipsoids, ellipsoidIDs);
	}

	private void createSecondaryOutputImages(final List<QuickEllipsoid> ellipsoids, final Img<IntType> ellipsoidIDs) {
		final double[] aBRatios = ellipsoids.parallelStream()
				.mapToDouble(e -> e.getSortedRadii()[0] / e.getSortedRadii()[1]).toArray();
		createAToBImage(aBRatios, ellipsoidIDs);
		final double[] bCRatios = ellipsoids.parallelStream()
				.mapToDouble(e -> e.getSortedRadii()[1] / e.getSortedRadii()[2]).toArray();
		createBToCImage(bCRatios, ellipsoidIDs);
		createFlinnPlotImage(aBRatios, bCRatios);
		createFlinnPeakPlot(aBRatios, bCRatios, ellipsoidIDs);
		eIdImage = new ImgPlus<>(ellipsoidIDs, "ID");
		eIdImage.setChannelMaximum(0, ellipsoids.size() / 10.0f);
		eIdImage.setChannelMinimum(0, -1.0f);
	}

	private void createVolumeImage(final List<QuickEllipsoid> ellipsoids, final IterableInterval<IntType> ellipsoidIDs) {
		final Img<FloatType> volumeImage = createNaNCopy();
		final double[] volumes = ellipsoids.parallelStream().mapToDouble(QuickEllipsoid::getVolume).toArray();
		mapValuesToImage(volumes, ellipsoidIDs, volumeImage);
		vImage = new ImgPlus<>(volumeImage, "Volume");
		vImage.setChannelMaximum(0, ellipsoids.get(0).getVolume());
		vImage.setChannelMinimum(0, -1.0f);
	}

	private long countAssignedVoxels(final Iterable<IntType> ellipsoidIdentityImage) {
		final LongType assignedVoxels = new LongType();
		ellipsoidIdentityImage.forEach(e -> {
			if (e.get() >= 0) {
				assignedVoxels.inc();
			}
		});
		return assignedVoxels.get();
	}

	private Img<IntType> assignEllipsoidIDs(final Img<BitType> mask, final List<QuickEllipsoid> ellipsoids) {
		final Img<IntType> idImage = ArrayImgs.ints(mask.dimension(0), mask.dimension(1), mask.dimension(2));
		idImage.forEach(c -> c.setInteger(-1));
		final Map<QuickEllipsoid, Integer> iDs = IntStream.range(0, ellipsoids.size()).boxed()
				.collect(toMap(ellipsoids::get, Function.identity()));
		final LongStream zRange = LongStream.range(0, mask.dimension(2));
		zRange.parallel().forEach(z -> {
			// multiply by image unit? make more intelligent bounding box?
			final List<QuickEllipsoid> localEllipsoids = ellipsoids.stream()
					.filter(e -> Math.abs(e.getCentre()[2] - z * inputImgPlus.averageScale(2)) < e.getSortedRadii()[2])
					.collect(toList());
			final long[] mins = {0, 0, z};
			final long[] maxs = {mask.dimension(0) - 1, mask.dimension(1) - 1, z};
			final Cursor<BitType> maskSlice = Views.interval(mask, mins, maxs).localizingCursor();
			colourSlice(idImage, maskSlice, localEllipsoids, iDs);
		});
		return idImage;
	}

	private void colourSlice(final RandomAccessible<IntType> idImage, final Cursor<BitType> mask,
							 final Collection<QuickEllipsoid> localEllipsoids, final Map<QuickEllipsoid, Integer> iDs) {
		while (mask.hasNext()) {
			mask.fwd();
			if (!mask.get().get()) {
				continue;
			}
			final long[] coordinates = new long[3];
			mask.localize(coordinates);
			final Vector3d point = new Vector3d(coordinates[0], coordinates[1], coordinates[2]);
			colourID(localEllipsoids, idImage, point, iDs);
		}
	}

	private void colourID(final Collection<QuickEllipsoid> localEllipsoids,
			final RandomAccessible<IntType> ellipsoidIdentityImage, final Vector3dc point,
			final Map<QuickEllipsoid, Integer> iDs) {
		// point.add(0.5, 0.5, 0.5);//this need to be scaled before
		final Optional<QuickEllipsoid> candidate = localEllipsoids.stream()
				.filter(e -> e.contains(point.x() * inputImgPlus.averageScale(0),
						point.y() * inputImgPlus.averageScale(1), point.z() * inputImgPlus.averageScale(2)))
				.findFirst();
		if (!candidate.isPresent()) {
			return;
		}
		final RandomAccess<IntType> eIDRandomAccess = ellipsoidIdentityImage.randomAccess();
		eIDRandomAccess.setPosition(new long[]{(long) point.x(), (long) point.y(), (long) point.z()});
		final QuickEllipsoid ellipsoid = candidate.get();
		eIDRandomAccess.get().set(iDs.get(ellipsoid));
	}

	@Override
	public void run() {
		final double pW = inputImgPlus.averageScale(0);
		final double pH = inputImgPlus.averageScale(1);
		final double pD = inputImgPlus.averageScale(2);
		vectorIncrement *= Math.min(pD, Math.min(pH, pW));
		maxDrift *= Math.sqrt(pW * pW + pH * pH + pD * pD) / Math.sqrt(3);

		stackVolume = pW * pH * pD * inputImgPlus.dimension(0) * inputImgPlus.dimension(1) * inputImgPlus.dimension(2);

		// noinspection unchecked
		final List<Vector3dc> vector3dSkeletonPoints = (List<Vector3dc>) ((List) opService.run(FindRidgePoints.class,
				Common.toBitTypeImgPlus(opService, inputImgPlus))).get(0);
		final List<int[]> listSkeletonPoints = vector3dSkeletonPoints.stream()
				.map(v -> new int[]{(int) v.get(0), (int) v.get(1), (int) v.get(2)}).collect(toList());
		final int[][] skeletonPoints = listSkeletonPoints.toArray(new int[vector3dSkeletonPoints.size()][]);
		logService.info("Found " + skeletonPoints.length + " skeleton points");

		long start = System.currentTimeMillis();
		final QuickEllipsoid[] ellipsoids = findEllipsoids(inputImgPlus, skeletonPoints);
		long stop = System.currentTimeMillis();
		logService.info("Found " + ellipsoids.length + " ellipsoids in " + (stop - start) + " ms");
		if (ellipsoids.length == 0) {
			cancel(NO_ELLIPSOIDS_FOUND);
			return;
		}

		statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
		start = System.currentTimeMillis();
		final Img<IntType> ellipsoidIdentityImage = assignEllipsoidIDs(Common.toBitTypeImgPlus(opService, inputImgPlus),
				Arrays.asList(ellipsoids));
		stop = System.currentTimeMillis();
		logService.info("Found maximal ellipsoids in " + (stop - start) + " ms");

		createPrimaryOutputImages(Arrays.asList(ellipsoids), ellipsoidIdentityImage);
		if (showSecondaryImages)
			createSecondaryOutputImages(Arrays.asList(ellipsoids), ellipsoidIdentityImage);

		final double numberOfForegroundVoxels = countTrue(Common.toBitTypeImgPlus(opService, inputImgPlus));
		final double numberOfAssignedVoxels = countAssignedVoxels(ellipsoidIdentityImage);
		final double fillingPercentage = 100.0 * (numberOfAssignedVoxels / numberOfForegroundVoxels);
		addResults(Arrays.asList(ellipsoids), fillingPercentage);

		statusService.showStatus("Ellipsoid Factor completed");
	}

	private void addResults(final List<QuickEllipsoid> ellipsoids, double fillingPercentage) {
		final String label = inputImgPlus.getName();
		SharedTable.add(label, "filling percentage", fillingPercentage);
		SharedTable.add(label, "number of ellipsoids found", ellipsoids.size());
		if (SharedTable.hasData()) {
			resultsTable = SharedTable.getTable();
		} else {
			cancel(NO_ELLIPSOIDS_FOUND);
		}
	}

	private void bump(final QuickEllipsoid ellipsoid, final Collection<double[]> contactPoints, final double px,
					  final double py, final double pz) {

		final double displacement = vectorIncrement / 2;

		final double[] c = ellipsoid.getCentre();
		final double[] vector = contactPointUnitVector(ellipsoid, contactPoints);
		final double x = c[0] + vector[0] * displacement;
		final double y = c[1] + vector[1] * displacement;
		final double z = c[2] + vector[2] * displacement;

		Vector3d distance = new Vector3d(px, py, pz);
		distance.sub(new Vector3d(x, y, z));
		if (distance.length() < maxDrift)
			ellipsoid.setCentroid(x, y, z);
	}

	private void findContactPoints(final QuickEllipsoid ellipsoid, final ArrayList<double[]> contactPoints,
								   final byte[][] pixels, final double pW, final double pH, final double pD, final int w, final int h,
								   final int d) {
		findContactPointsForGivenDirections(ellipsoid, contactPoints, ellipsoid.getAxisAlignRandomlyDistributedSurfacePoints(nVectors), pixels, pW, pH, pD, w, h,
				d);
	}

	/**
	 * Using skeleton points as seeds, propagate along each vector until a boundary
	 * is hit. Use the resulting cloud of boundary points as input into an ellipsoid
	 * fit.
	 *
	 * @param imp
	 *            input image
	 * @param skeletonPoints
	 *            input skeleton points
	 * @return array of fitted ellipsoids
	 */
	private QuickEllipsoid[] findEllipsoids(final ImgPlus imp, int[][] skeletonPoints) {
		final int nPoints = skeletonPoints.length;

		final int w = (int) imp.dimension(0);
		final int h = (int) imp.dimension(1);
		final int d = (int) imp.dimension(2);

		final byte[][] pixels = new byte[d][w * h];
		final IntStream zRange = IntStream.range(0, (int) imp.dimension(2));
		zRange.forEach(z -> {
			final long[] mins = {0, 0, z};
			final long[] maxs = {imp.dimension(0) - 1, imp.dimension(1) - 1, z};
			final Cursor<UnsignedByteType> sliceCursor = (Cursor<UnsignedByteType>) Views.interval(imp, mins, maxs)
					.localizingCursor();
			while (sliceCursor.hasNext()) {
				sliceCursor.fwd();
				int[] position = new int[3];
				sliceCursor.localize(position);
				pixels[position[2]][position[1] * w + position[0]] = sliceCursor.get().getByte();
			}
		});

		List<int[]> skeletonPointList = Arrays.asList(skeletonPoints);
		if (skipRatio > 1) {
			int limit = skeletonPoints.length / skipRatio + Math.min(skeletonPoints.length % skipRatio, 1);
			skeletonPointList = Stream.iterate(0, i -> i + skipRatio).limit(limit).map(skeletonPointList::get)
					.collect(toList());
			skeletonPoints = skeletonPointList.toArray(new int[limit][]);
		}

		final List<QuickEllipsoid> ellipsoidList = new ArrayList<>(skeletonPoints.length);
		statusService.showStatus("Optimising ellipsoids...");
		skeletonPointList.parallelStream().forEach(sp -> ellipsoidList.add(optimiseEllipsoid(imp, pixels, sp)));
		return ellipsoidList.stream().filter(Objects::nonNull)
				.sorted((a, b) -> Double.compare(b.getVolume(), a.getVolume())).toArray(QuickEllipsoid[]::new);
	}

	private void inflateToFit(final QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints, final double a,
							  final double b, final double c, final byte[][] pixels, final double pW, final double pH, final double pD,
							  final int w, final int h, final int d) {

		findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

		final double av = a * vectorIncrement;
		final double bv = b * vectorIncrement;
		final double cv = c * vectorIncrement;

		int safety = 0;
		while (contactPoints.size() < contactSensitivity && safety < maxIterations) {
			ellipsoid.dilate(av, bv, cv);
			findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
			safety++;
		}
	}

	private boolean isContained(final QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints, final byte[][] pixels, final double pW, final double pH,
								final double pD, final int w, final int h, final int d) {
		final double[][] points = ellipsoid.getSurfacePoints(ellipsoid.getAxisAlignRandomlyDistributedSurfacePoints(nVectors));
		for (final double[] p : points) {
			final int x = (int) Math.floor(p[0] / pW);
			final int y = (int) Math.floor(p[1] / pH);
			final int z = (int) Math.floor(p[2] / pD);
			if (isOutOfBounds(x, y, z, w, h, d))
				continue;
			if (pixels[z][y * w + x] != -1) {
				contactPoints.clear();
				contactPoints.addAll(Arrays.asList(points));
				return false;
			}
		}
		return true;
	}

	/**
	 * Check whether this ellipsoid is sensible
	 *
	 * @param ellipsoid
	 *            ellipsoids
	 * @param pW
	 *            scale in x
	 * @param pH
	 *            scale in y
	 * @param pD
	 *            scale in z
	 * @param w
	 *            image dimension in x
	 * @param h
	 *            image dimension in y
	 * @param d
	 *            image dimension in z
	 * @return true if half or more of the surface points are outside the image
	 *         stack, or if the volume of the ellipsoid exceeds that of the image
	 *         stack
	 */
	private boolean isInvalid(final QuickEllipsoid ellipsoid, final ArrayList<double []> surfacePoints, final double pW, final double pH, final double pD, final int w,
							  final int h, final int d) {
		int outOfBoundsCount = 0;
		final int half = nVectors / 2;
		for (final double[] p : surfacePoints) {
			if (isOutOfBounds((int) (p[0] / pW), (int) (p[1] / pD), (int) (p[2] / pH), w, h, d))
				outOfBoundsCount++;
			if (outOfBoundsCount > half)
				return true;
		}

		final double volume = ellipsoid.getVolume();
		return volume > stackVolume;

	}

	/**
	 * given a seed point, find the ellipsoid which best fits the binarised
	 * structure
	 *
	 * @param imp
	 *            input image
	 * @return ellipsoid fitting the point cloud of boundaries lying at the end of
	 *         vectors surrounding the seed point. If ellipsoid fitting fails,
	 *         returns null
	 */
	private QuickEllipsoid optimiseEllipsoid(final ImgPlus imp, byte[][] pixels, final int[] skeletonPoint) {

		final long start = System.currentTimeMillis();
		// cache slices into an array
		final double pW = imp.averageScale(0);
		final double pH = imp.averageScale(1);
		final double pD = imp.averageScale(2);

		final int w = (int) imp.dimension(0);
		final int h = (int) imp.dimension(1);
		final int d = (int) imp.dimension(2);

		// centre point of vector field
		final double px = skeletonPoint[0] * pW;
		final double py = skeletonPoint[1] * pH;
		final double pz = skeletonPoint[2] * pD;

		// Instantiate a small spherical ellipsoid
		final double[][] orthogonalVectors = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

		QuickEllipsoid ellipsoid = new QuickEllipsoid(vectorIncrement, vectorIncrement, vectorIncrement, px, py, pz,
				orthogonalVectors);

		final List<Double> volumeHistory = new ArrayList<>();
		volumeHistory.add(ellipsoid.getVolume());

		// instantiate the ArrayList
		ArrayList<double[]> contactPoints = new ArrayList<>();

		// dilate the sphere until it hits the background
		while (isContained(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d)) {
			ellipsoid.dilate(vectorIncrement, vectorIncrement, vectorIncrement);
		}

		volumeHistory.add(ellipsoid.getVolume());

		// find the mean unit vector pointing to the points of contact from the
		// centre
		final double[] shortAxis = contactPointUnitVector(ellipsoid, contactPoints);

		// find an orthogonal axis
		final double[] xAxis = {1, 0, 0};
		double[] middleAxis = crossProduct(shortAxis, xAxis);
		middleAxis = norm(middleAxis);

		// find a mutually orthogonal axis by forming the cross product
		double[] longAxis = crossProduct(shortAxis, middleAxis);
		longAxis = norm(longAxis);

		// construct a rotation matrix
		double[][] rotation = {shortAxis, middleAxis, longAxis};
		rotation = QuickEllipsoid.transpose(rotation);

		// rotate ellipsoid to point this way...
		ellipsoid.setRotation(rotation);

		// shrink the ellipsoid slightly
		shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
		ellipsoid.contract(0.1);

		// dilate other two axes until number of contact points increases
		// by contactSensitivity number of contacts

		while (contactPoints.size() < contactSensitivity) {
			ellipsoid.dilate(0, vectorIncrement, vectorIncrement);
			findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
			if (isInvalid(ellipsoid, contactPoints, pW, pH, pD, w, h, d)) {
				logService.debug(
						"Ellipsoid at (" + px + ", " + py + ", " + pz + ") is invalid, nullifying at initial oblation");
				return null;
			}
		}

		volumeHistory.add(ellipsoid.getVolume());

		// until ellipsoid is totally jammed within the structure, go through
		// cycles of contraction, wiggling, dilation
		// goal is maximal inscribed ellipsoid, maximal being defined by volume

		// store a copy of the 'best ellipsoid so far'
		QuickEllipsoid maximal = ellipsoid.copy();

		// alternately try each axis
		int totalIterations = 0;
		int noImprovementCount = 0;
		final int absoluteMaxIterations = maxIterations * 10;
		while (totalIterations < absoluteMaxIterations && noImprovementCount < maxIterations) {

			// rotate a little bit
			wiggle(ellipsoid);

			// contract until no contact
			shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

			// dilate an axis
			double[] abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, pW, pH, pD, w, h, d);

			if (isInvalid(ellipsoid, contactPoints, pW, pH, pD, w, h, d)) {
				logService.debug("Ellipsoid at (" + px + ", " + py + ", " + pz + ") is invalid, nullifying after "
						+ totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// bump a little away from the sides
			findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
			// if can't bump then do a wiggle
			if (contactPoints.isEmpty()) {
				wiggle(ellipsoid);
			} else {
				bump(ellipsoid, contactPoints, px, py, pz);
			}

			// contract
			shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

			// dilate an axis
			abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, pW, pH, pD, w, h, d);

			if (isInvalid(ellipsoid, contactPoints, pW, pH, pD, w, h, d)) {
				logService.debug("Ellipsoid at (" + px + ", " + py + ", " + pz + ") is invalid, nullifying after "
						+ totalIterations + " iterations");
				return null;
			}

			if (ellipsoid.getVolume() > maximal.getVolume())
				maximal = ellipsoid.copy();

			// rotate a little bit
			turn(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

			// contract until no contact
			shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

			// dilate an axis
			abc = threeWayShuffle();
			inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2], pixels, pW, pH, pD, w, h, d);

			if (isInvalid(ellipsoid, contactPoints, pW, pH, pD, w, h, d)) {
				logService.debug("Ellipsoid at (" + px + ", " + py + ", " + pz + ") is invalid, nullifying after "
						+ totalIterations + " iterations");
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
			logService.debug("Ellipsoid at (" + px + ", " + py + ", " + pz
					+ ") seems to be out of control, nullifying after " + totalIterations + " iterations");
			return null;
		}

		final long stop = System.currentTimeMillis();

		logService.debug("Optimised ellipsoid in " + (stop - start) + " ms after " + totalIterations + " iterations ("
				+ (double) (stop - start) / totalIterations + " ms/iteration)");

		return ellipsoid;
	}

	private void shrinkToFit(final QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints, final byte[][] pixels,
							 final double pW, final double pH, final double pD, final int w, final int h, final int d) {

		// get the contact points
		findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

		// get the unit vectors to the contact points
		final double[][] unitVectors = findContactUnitVectors(ellipsoid, contactPoints);

		// contract until no contact
		int safety = 0;
		while (!contactPoints.isEmpty() && safety < maxIterations) {
			ellipsoid.contract(0.01);
			findContactPointsForGivenDirections(ellipsoid, contactPoints, unitVectors, pixels, pW, pH, pD, w, h, d);
			safety++;
		}

		ellipsoid.contract(0.05);
	}

	/**
	 * Rotate the ellipsoid theta radians around the unit vector formed by the sum
	 * of torques effected by unit normals acting on the surface of the ellipsoid
	 *
	 * @param ellipsoid
	 *            the ellipsoid
	 * @param pW
	 *            the scale in x
	 * @param pH
	 *            the scale in y
	 * @param pD
	 *            the scale in z
	 * @param w
	 *            the image dimension in x
	 * @param h
	 *            the image dimension in y
	 * @param d
	 *            the image dimension in z
	 */
	private void turn(QuickEllipsoid ellipsoid, ArrayList<double[]> contactPoints, final byte[][] pixels, final double pW,
					  final double pH, final double pD, final int w, final int h, final int d) {

		findContactPoints(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
		if (!contactPoints.isEmpty()) {
			final double[] torque = calculateTorque(ellipsoid, contactPoints);
			rotateAboutAxis(ellipsoid, norm(torque));
		}
	}

}

