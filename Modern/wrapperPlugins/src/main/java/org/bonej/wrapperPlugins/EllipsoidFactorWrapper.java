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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.units.UnitService;
import net.imglib2.Cursor;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.view.Views;
import org.bonej.ops.skeletonize.FindRidgePoints;
import org.bonej.util.Multithreader;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.DoubleStream.of;

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
 * "The ellipsoid factor for quantification of rods, plates, and intermediate forms in 3D geometries"
 * Frontiers in Endocrinology (2015)</a>
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor 2")
public class EllipsoidFactorWrapper extends ContextCommand {

    private static final String NO_ELLIPSOIDS_FOUND = "No ellipsoids were found - try modifying input parameters.";
    private static Random rng = new Random();
    double[][] regularVectors;
    @Parameter
    private UnitService unitService;
    @Parameter
    private CommandService cs;
    @Parameter
    private OpService opService;
    @Parameter
    private LogService logService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private UIService uiService;
    private boolean calibrationWarned;
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
    /**
     * The EF results in a {@link Table}, null if there are no results
     */
    @Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
    private Table<DefaultColumn<Double>, Double> resultsTable;
    @Parameter(visibility = ItemVisibility.MESSAGE)
    private String note =
            "Ellipsoid Factor is beta software.\n" +
                    "Please report your experiences to the user group:\n" +
                    "http://forum.image.sc/tags/bonej";

    /**
     * Method to numerically approximate equidistantly spaced points on the
     * surface of a sphere
     * <p>
     * The implementation follows the description of the theoretical work by
     * Rakhmanov et al., 1994 in Saff and Kuijlaars, 1997
     * (<a href="doi:10.1007/BF03024331">dx.doi.org/10.1007/BF03024331</a>), but k
     * is shifted by one to the left for more convenient indexing.
     *
     * @param n : number of points required (has to be > 2)
     *          <p>
     *          This could be its own op in the future.
     *          </p>
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
            spiralSet.add(new double[]{Math.sin(theta) * Math.cos(phi.get(k)), Math
                    .sin(theta) * Math.sin(phi.get(k)), Math.cos(theta)});

        }
        List<double[]> list = spiralSet.build().collect(toList());
        return list.toArray(new double[n][]);
    }

    private static double getPhiByRecursion(final double n, final double phiKMinus1,
                                            final double hk) {
        final double phiK = phiKMinus1 + 3.6 / Math.sqrt(n) * 1.0 / Math.sqrt(1 - hk *
                hk);
        // modulo 2pi calculation works for positive numbers only, which is not a
        // problem in this case.
        return phiK - Math.floor(phiK / (2 * Math.PI)) * 2 * Math.PI;
    }

    /**
     * Search the list of ellipsoids and return the index of the largest ellipsoid
     * which contains the point x, y, z
     *
     * @param ellipsoids sorted in order of descending size and with id set to the
     *                   sort position of the whole set. This means that subsets may be
     *                   searched in sorted order and the ID which is returned is the index
     *                   of the ellipsoid in the full array of ellipsoids rather than its
     *                   index in the subset. The advantage is much faster searching.
     * @param x
     * @param y
     * @param z
     * @return the index of the largest ellipsoid which contains this point, -1 if
     * none of the ellipsoids contain the point
     */
    private static int biggestEllipsoid(final Ellipsoid[] ellipsoids,
                                        final double x, final double y, final double z) {
        for (final Ellipsoid ellipsoid : ellipsoids) {
            if (ellipsoid.contains(x, y, z)) {
                return ellipsoid.id;
            }
        }
        return -1;
    }

    /**
     * Calculate the torque of unit normals acting at the contact points
     *
     * @param ellipsoid
     * @param contactPoints
     * @return
     */
    private static double[] calculateTorque(final Ellipsoid ellipsoid,
                                            final Iterable<double[]> contactPoints) {

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

    /**
     * Calculate the mean unit vector between the ellipsoid's centroid and contact
     * points
     *
     * @param ellipsoid
     * @param contactPoints
     * @return
     */
    private static double[] contactPointUnitVector(final Ellipsoid ellipsoid,
                                                   final Collection<double[]> contactPoints) {

        final int nPoints = contactPoints.size();

        if (nPoints < 1) throw new IllegalArgumentException(
                "Need at least one contact point");

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
     * @param a first vector
     * @param b second vector
     * @return resulting vector in double[3] format
     */
    private static double[] crossProduct(final double[] a, final double[] b) {
        return crossProduct(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    /**
     * Calculate the cross product of two vectors (x1, y1, z1) and (x2, y2, z2)
     *
     * @param x1 x-coordinate of the 1st vector.
     * @param y1 y-coordinate of the 1st vector.
     * @param z1 z-coordinate of the 1st vector.
     * @param x2 x-coordinate of the 2nd vector.
     * @param y2 y-coordinate of the 2nd vector.
     * @param z2 z-coordinate of the 2nd vector.
     * @return cross product in {x, y, z} format
     */
    private static double[] crossProduct(final double x1, final double y1,
                                         final double z1, final double x2, final double y2, final double z2) {
        final double x = y1 * z2 - z1 * y2;
        final double y = z1 * x2 - x1 * z2;
        final double z = x1 * y2 - y1 * x2;
        return new double[]{x, y, z};
    }

    private static ImagePlus displayEllipsoidFactor(final ImagePlus imp,
                                                    final int[][] maxIDs, final Ellipsoid[] ellipsoids) {
        final ImageStack stack = imp.getImageStack();
        final int w = stack.getWidth();
        final int h = stack.getHeight();
        final int d = stack.getSize();

        final ImageStack efStack = new ImageStack(imp.getWidth(), imp.getHeight());

        final float[][] stackPixels = new float[d + 1][w * h];

        final AtomicInteger ai = new AtomicInteger(1);
        final Thread[] threads = Multithreader.newThreads();
        for (int thread = 0; thread < threads.length; thread++) {
            threads[thread] = new Thread(() -> {
                for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
                    IJ.showStatus("Generating EF image");
                    IJ.showProgress(z, d);
                    final int[] idSlice = maxIDs[z];
                    final float[] pixels = stackPixels[z];

                    for (int y = 0; y < h; y++) {
                        final int offset = y * w;
                        for (int x = 0; x < w; x++) {
                            final int i = offset + x;
                            final int id = idSlice[i];
                            if (id >= 0) pixels[i] = (float) ellipsoidFactor(ellipsoids[id]);
                            else pixels[i] = Float.NaN;
                        }
                    }
                }
            });
        }
        Multithreader.startAndJoin(threads);

        for (int z = 1; z <= d; z++)
            efStack.addSlice("" + z, stackPixels[z]);

        final ImagePlus ef = new ImagePlus("EF-" + imp.getTitle(), efStack);
        ef.setCalibration(imp.getCalibration());
        return ef;
    }

    private static ImagePlus displayMiddleOverLong(final ImagePlus imp,
                                                   final int[][] maxIDs, final Ellipsoid[] ellipsoids) {
        final ImageStack stack = imp.getImageStack();
        final int w = stack.getWidth();
        final int h = stack.getHeight();
        final int d = stack.getSize();

        final ImageStack mlStack = new ImageStack(imp.getWidth(), imp.getHeight());

        final float[][] stackPixels = new float[d + 1][w * h];

        final AtomicInteger ai = new AtomicInteger(1);
        final Thread[] threads = Multithreader.newThreads();
        for (int thread = 0; thread < threads.length; thread++) {
            threads[thread] = new Thread(() -> {
                for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
                    IJ.showStatus("Generating volume image");
                    IJ.showProgress(z, d);
                    final int[] idSlice = maxIDs[z];
                    final float[] pixels = stackPixels[z];
                    for (int y = 0; y < h; y++) {
                        final int offset = y * w;
                        for (int x = 0; x < w; x++) {
                            final int i = offset + x;
                            final int id = idSlice[i];
                            if (id >= 0) {
                                final double[] radii = ellipsoids[id].getSortedRadii();
                                pixels[i] = (float) (radii[1] / radii[2]);
                            } else pixels[i] = Float.NaN;
                        }
                    }
                }
            });
        }
        Multithreader.startAndJoin(threads);

        for (int z = 1; z <= d; z++)
            mlStack.addSlice("" + z, stackPixels[z]);

        final ImagePlus midLong = new ImagePlus("Mid_Long-" + imp.getTitle(),
                mlStack);
        midLong.setCalibration(imp.getCalibration());
        return midLong;
    }

    private static ImagePlus displayShortOverMiddle(final ImagePlus imp,
                                                    final int[][] maxIDs, final Ellipsoid[] ellipsoids) {
        final ImageStack stack = imp.getImageStack();
        final int w = stack.getWidth();
        final int h = stack.getHeight();
        final int d = stack.getSize();

        final ImageStack smStack = new ImageStack(imp.getWidth(), imp.getHeight());

        final float[][] stackPixels = new float[d + 1][w * h];

        final AtomicInteger ai = new AtomicInteger(1);
        final Thread[] threads = Multithreader.newThreads();
        for (int thread = 0; thread < threads.length; thread++) {
            threads[thread] = new Thread(() -> {
                for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
                    IJ.showStatus("Generating short/middle axis image");
                    IJ.showProgress(z, d);
                    final int[] idSlice = maxIDs[z];
                    final float[] pixels = stackPixels[z];
                    for (int y = 0; y < h; y++) {
                        final int offset = y * w;
                        for (int x = 0; x < w; x++) {
                            final int i = offset + x;
                            final int id = idSlice[i];
                            if (id >= 0) {
                                final double[] radii = ellipsoids[id].getSortedRadii();
                                pixels[i] = (float) (radii[0] / radii[1]);
                            } else pixels[i] = Float.NaN;
                        }
                    }
                }
            });
        }
        Multithreader.startAndJoin(threads);

        for (int z = 1; z <= d; z++)
            smStack.addSlice("" + z, stackPixels[z]);

        final ImagePlus shortmid = new ImagePlus("Short_Mid-" + imp.getTitle(),
                smStack);
        shortmid.setCalibration(imp.getCalibration());
        return shortmid;
    }

    private static ImagePlus displayVolumes(final ImagePlus imp,
                                            final int[][] maxIDs, final Ellipsoid[] ellipsoids) {
        final ImageStack stack = imp.getImageStack();
        final int w = stack.getWidth();
        final int h = stack.getHeight();
        final int d = stack.getSize();

        final ImageStack volStack = new ImageStack(imp.getWidth(), imp.getHeight());

        final float[][] stackPixels = new float[d + 1][w * h];

        final AtomicInteger ai = new AtomicInteger(1);
        final Thread[] threads = Multithreader.newThreads();
        for (int thread = 0; thread < threads.length; thread++) {
            threads[thread] = new Thread(() -> {
                for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
                    IJ.showStatus("Generating volume image");
                    IJ.showProgress(z, d);
                    final int[] idSlice = maxIDs[z];
                    final float[] pixels = stackPixels[z];
                    for (int y = 0; y < h; y++) {
                        final int offset = y * w;
                        for (int x = 0; x < w; x++) {
                            final int i = offset + x;
                            final int id = idSlice[i];
                            if (id >= 0) {
                                pixels[i] = (float) ellipsoids[id].getVolume();
                            } else pixels[i] = Float.NaN;
                        }
                    }
                }
            });
        }
        Multithreader.startAndJoin(threads);

        for (int z = 1; z <= d; z++)
            volStack.addSlice("" + z, stackPixels[z]);

        final ImagePlus volImp = new ImagePlus("Volume-" + imp.getTitle(),
                volStack);
        volImp.setCalibration(imp.getCalibration());
        return volImp;
    }

    /**
     * Draw a Flinn diagram with each point given an intensity proportional to the
     * volume of the structure with that axis ratio
     *
     * @param title
     * @param imp
     * @param maxIDs
     * @param ellipsoids
     * @param sigma
     * @return
     */
    private static ImagePlus drawFlinnPeakPlot(final String title,
                                               final ImagePlus imp, final int[][] maxIDs, final Ellipsoid[] ellipsoids,
                                               final double sigma) {

        final ImageStack stack = imp.getImageStack();
        final int w = stack.getWidth();
        final int h = stack.getHeight();
        final int d = stack.getSize();

        final float[][] ab = new float[d][];
        final float[][] bc = new float[d][];

        final AtomicInteger ai = new AtomicInteger(1);
        final Thread[] threads = Multithreader.newThreads();
        for (int thread = 0; thread < threads.length; thread++) {
            threads[thread] = new Thread(() -> {
                for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
                    IJ.showStatus("Generating Flinn Diagram");
                    IJ.showProgress(z, d);
                    final int[] idSlice = maxIDs[z];
                    int l = 0;
                    for (int y = 0; y < h; y++) {
                        final int offset = y * w;
                        for (int x = 0; x < w; x++)
                            if (idSlice[offset + x] >= 0) l++;
                    }
                    final float[] abl = new float[l];
                    final float[] bcl = new float[l];
                    int j = 0;
                    for (int y = 0; y < h; y++) {
                        final int offset = y * w;
                        for (int x = 0; x < w; x++) {
                            final int i = offset + x;
                            final int id = idSlice[i];
                            if (id >= 0) {
                                final double[] radii = ellipsoids[id].getSortedRadii();
                                abl[j] = (float) (radii[0] / radii[1]);
                                bcl[j] = (float) (radii[1] / radii[2]);
                                j++;
                            }
                        }
                    }
                    ab[z - 1] = abl;
                    bc[z - 1] = bcl;
                }
            });
        }
        Multithreader.startAndJoin(threads);

        int l = 0;
        for (final float[] f : ab)
            l += f.length;

        final float[] aOverB = new float[l];
        final float[] bOverC = new float[l];

        int i = 0;
        for (final float[] fl : ab) {
            for (final float f : fl) {
                aOverB[i] = f;
                i++;
            }
        }
        i = 0;
        for (final float[] fl : bc) {
            for (final float f : fl) {
                bOverC[i] = f;
                i++;
            }
        }

        final int size = 512;
        final float[][] pixels = new float[size][size];

        for (int j = 0; j < l; j++) {
            final int x = (int) Math.floor((size - 1) * bOverC[j]);
            final int y = (int) Math.floor((size - 1) * (1 - aOverB[j]));
            pixels[x][y] += 1;
        }

        final FloatProcessor fp = new FloatProcessor(pixels);
        if (sigma > 0) fp.blurGaussian(sigma);

        final Calibration cal = new Calibration();
        cal.setXUnit("b/c");
        cal.setYUnit("a/b");
        cal.pixelWidth = 1.0 / size;
        cal.pixelHeight = 1.0 / size;
        cal.setInvertY(true);
        final ImagePlus plot = new ImagePlus(title, fp);
        plot.setCalibration(cal);
        return plot;
    }

    /**
     * Display each ellipsoid's axis ratios in a scatter plot
     *
     * @param title
     * @param ellipsoids
     * @return
     */
    private static ImagePlus drawFlinnPlot(final String title,
                                           final Ellipsoid[] ellipsoids) {

        final int l = ellipsoids.length;
        final double[] aOverB = new double[l];
        final double[] bOverC = new double[l];

        for (int i = 0; i < l; i++) {
            final double[] radii = ellipsoids[i].getSortedRadii();
            aOverB[i] = radii[0] / radii[1];
            bOverC[i] = radii[1] / radii[2];
        }

        final Plot plot = new Plot("Flinn Diagram of " + title, "b/c", "a/b");
        plot.setLimits(0, 1, 0, 1);
        plot.setSize(1024, 1024);
        plot.addPoints(bOverC, aOverB, Plot.CIRCLE);
        final ImageProcessor plotIp = plot.getProcessor();
        return new ImagePlus("Flinn Diagram of " + title, plotIp);
    }

    /**
     * Calculate the ellipsoid factor of this ellipsoid as a / b - b / c where a <
     * b < c and a, b and c are the ellipsoid semi axis lengths (radii). This
     * formulation places more rod-like ellipsoids towards 1 and plate-like
     * ellipsoids towards -1. Ellipsoids of EF = 0 have equal a:b and b:c ratios
     * so are midway between plate and rod. Spheres are a special case of EF = 0.
     *
     * @param ellipsoid
     * @return the ellipsoid factor
     */
    private static double ellipsoidFactor(final Ellipsoid ellipsoid) {
        final double[] radii = ellipsoid.getSortedRadii();
        final double a = radii[0];
        final double b = radii[1];
        final double c = radii[2];
        return a / b - b / c;
    }

    private static ArrayList<double[]> findContactPoints(
            final Ellipsoid ellipsoid, final ArrayList<double[]> contactPoints,
            final double[][] unitVectors, final byte[][] pixels, final double pW,
            final double pH, final double pD, final int w, final int h, final int d) {
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
        return contactPoints;
    }

    private static double[][] findContactUnitVectors(final Ellipsoid ellipsoid,
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
     * For each foreground pixel of the input image, find the ellipsoid of
     * greatest volume
     *
     * @param imp
     * @param ellipsoids
     * @return array containing the indexes of the biggest ellipsoids which
     * contain each point
     */
    private static int[][] findMaxID(final ImagePlus imp,
                                     final Ellipsoid[] ellipsoids) {

        final ImageStack stack = imp.getImageStack();
        final int w = stack.getWidth();
        final int h = stack.getHeight();
        final int d = stack.getSize();

        final Calibration cal = imp.getCalibration();
        final double vW = cal.pixelWidth;
        final double vH = cal.pixelHeight;
        final double vD = cal.pixelDepth;

        final int[][] biggest = new int[d + 1][w * h];

        final AtomicInteger ai = new AtomicInteger(1);
        final Thread[] threads = Multithreader.newThreads();
        for (int thread = 0; thread < threads.length; thread++) {
            threads[thread] = new Thread(() -> {
                for (int z = ai.getAndIncrement(); z <= d; z = ai.getAndIncrement()) {
                    IJ.showStatus("Finding biggest ellipsoid");
                    IJ.showProgress(z, d);
                    final byte[] slicePixels = (byte[]) stack.getPixels(z);
                    final int[] bigSlice = biggest[z];
                    Arrays.fill(bigSlice, -ellipsoids.length);
                    final double zvD = z * vD;

                    // find the subset of ellipsoids whose bounding box
                    // intersects with z
                    final List<Ellipsoid> nearEllipsoids = new ArrayList<>();
                    final int n = ellipsoids.length;
                    for (int i = 0; i < n; i++) {
                        final Ellipsoid e = ellipsoids[i];
                        final double[] zMinMax = e.getZMinAndMax();
                        if (zvD >= zMinMax[0] && zvD <= zMinMax[1]) {
                            final Ellipsoid f = e.copy();
                            f.id = i;
                            nearEllipsoids.add(f);
                        }
                    }
                    final int o = nearEllipsoids.size();
                    final Ellipsoid[] ellipsoidSubSet = new Ellipsoid[o];
                    for (int i = 0; i < o; i++) {
                        ellipsoidSubSet[i] = nearEllipsoids.get(i);
                    }

                    for (int y = 0; y < h; y++) {
                        final double yvH = y * vH;
                        // find the subset of ellipsoids whose bounding box
                        // intersects with y
                        final List<Ellipsoid> yEllipsoids = new ArrayList<>();
                        for (final Ellipsoid e : ellipsoidSubSet) {
                            final double[] yMinMax = e.getYMinAndMax();
                            if (yvH >= yMinMax[0] && yvH <= yMinMax[1]) {
                                yEllipsoids.add(e);
                            }
                        }

                        final int r = yEllipsoids.size();
                        final Ellipsoid[] ellipsoidSubSubSet = new Ellipsoid[r];
                        for (int i = 0; i < r; i++) {
                            ellipsoidSubSubSet[i] = yEllipsoids.get(i);
                        }

                        final int offset = y * w;
                        for (int x = 0; x < w; x++) {
                            if (slicePixels[offset + x] == -1) {
                                bigSlice[offset + x] = biggestEllipsoid(ellipsoidSubSubSet, x *
                                        vW, yvH, zvD);
                            }
                        }
                    }

                }
            });
        }
        Multithreader.startAndJoin(threads);
        return biggest;
    }

    /**
     * return true if pixel coordinate is out of image bounds
     *
     * @param x
     * @param y
     * @param z
     * @param w
     * @param h
     * @param d
     * @return
     */
    private static boolean isOutOfBounds(final int x, final int y, final int z,
                                         final int w, final int h, final int d) {
        return x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= d;
    }

    /**
     * Normalise a vector to have a length of 1 and the same orientation as the
     * input vector a
     *
     * @param a a 3D vector.
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
     * Rotate the ellipsoid theta radians around an arbitrary unit vector
     *
     * @param ellipsoid
     * @param axis
     * @return
     * @see <a href=
     * "http://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_and_angle">Rotation
     * matrix from axis and angle</a>
     */
    private static Ellipsoid rotateAboutAxis(final Ellipsoid ellipsoid,
                                             final double[] axis) {
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
        final double[][] rotation = {{cos + x * x * cos1, xycos1 - zsin, xzcos1 +
                ysin}, {xycos1 + zsin, cos + y * y * cos1, yzcos1 - xsin}, {xzcos1 -
                ysin, yzcos1 + xsin, cos + z * z * cos1},};

        ellipsoid.rotate(rotation);

        return ellipsoid;
    }

    private static double[] threeWayShuffle() {
        final double[] a = {0, 0, 0};
        final double rand = Math.random();
        if (rand < 1.0 / 3.0) a[0] = 1;
        else if (rand >= 2.0 / 3.0) a[2] = 1;
        else a[1] = 1;
        return a;
    }

    /**
     * Rotate the ellipsoid by a small random amount
     *
     * @param ellipsoid
     */
    private static Ellipsoid wiggle(final Ellipsoid ellipsoid) {

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
        rotation = Ellipsoid.transpose(rotation);

        ellipsoid.rotate(rotation);

        return ellipsoid;
    }

    @Override
    public void run() {
        final double pW = inputImgPlus.averageScale(0);
        final double pH = inputImgPlus.averageScale(1);
        final double pD = inputImgPlus.averageScale(2);
        vectorIncrement *= Math.min(pD, Math.min(pH, pW));
        maxDrift *= Math.sqrt(pW * pW + pH * pH + pD * pD) / Math.sqrt(3);

        stackVolume = pW * pH * pD * inputImgPlus.dimension(0) * inputImgPlus.dimension(1) * inputImgPlus.dimension(2);

        final List<Vector3dc> vector3dSkeletonPoints = (List<Vector3dc>) ((List) opService.run(FindRidgePoints.class, Common.toBitTypeImgPlus(opService, inputImgPlus))).get(0);
        final List<int[]> listSkeletonPoints = vector3dSkeletonPoints.stream().map(v -> new int[]{(int) v.get(0), (int) v.get(1), (int) v.get(2)}).collect(toList());
        final int[][] skeletonPoints = listSkeletonPoints.toArray(new int[vector3dSkeletonPoints.size()][]);
        logService.info("Found " + skeletonPoints.length + " skeleton points");

        long start = System.currentTimeMillis();
        final Ellipsoid[] ellipsoids = findEllipsoids(inputImgPlus, skeletonPoints);
        long stop = System.currentTimeMillis();
        logService.info("Found " + ellipsoids.length + " ellipsoids in " + (stop - start) +
                " ms");
        if (ellipsoids.length == 0) {
            cancel(NO_ELLIPSOIDS_FOUND);
            return;
        }

        start = System.currentTimeMillis();
        final ImagePlus imp = IJ.getImage();
        final int[][] maxIDs = findMaxID(imp, ellipsoids);
        stop = System.currentTimeMillis();

        logService.info("Found maximal ellipsoids in " + (stop - start) + " ms");

        final double fractionFilled = calculateFillingEfficiency(maxIDs);
        addResults(Arrays.asList(ellipsoids), fractionFilled);

        if (showSecondaryImages) {
            final ImagePlus volumes = displayVolumes(imp, maxIDs, ellipsoids);
            volumes.show();
            volumes.setDisplayRange(0, ellipsoids[(int) (0.05 * ellipsoids.length)]
                    .getVolume());
            IJ.run("Fire");
        }

        if (showSecondaryImages) {
            final ImagePlus middleOverLong = displayMiddleOverLong(imp, maxIDs,
                    ellipsoids);
            middleOverLong.show();
            middleOverLong.setDisplayRange(0, 1);
            IJ.run("Fire");

            final ImagePlus shortOverMiddle = displayShortOverMiddle(imp, maxIDs,
                    ellipsoids);
            shortOverMiddle.show();
            shortOverMiddle.setDisplayRange(0, 1);
            IJ.run("Fire");
        }

        if (showSecondaryImages) {
            final ImagePlus eF = displayEllipsoidFactor(imp, maxIDs, ellipsoids);
            eF.show();
            eF.setDisplayRange(-1, 1);
            IJ.run("Fire");
        }

        if (showSecondaryImages) {
            final ImagePlus maxID = displayMaximumIDs(maxIDs, imp);
            maxID.show();
            maxID.setDisplayRange(-ellipsoids.length / 2.0, ellipsoids.length);
        }

        if (showSecondaryImages) {
            final ImagePlus flinnPlot = drawFlinnPlot("Weighted-flinn-plot-" + imp
                    .getTitle(), ellipsoids);
            flinnPlot.show();
        }

        if (showSecondaryImages) {
            final ImagePlus flinnPeaks = drawFlinnPeakPlot("FlinnPeaks_" + imp
                    .getTitle(), imp, maxIDs, ellipsoids, sigma);
            flinnPeaks.show();
        }

        //UsageReporter.reportEvent(this).send();
        IJ.showStatus("Ellipsoid Factor completed");
    }

    private void addResults(final List<Ellipsoid> ellipsoids, double fillingPercentage) {
        final String label = inputImgPlus.getName();
        SharedTable.add(label, "filling percentage", fillingPercentage);
        SharedTable.add(label, "number of ellipsoids found", ellipsoids.size());
        if (SharedTable.hasData()) {
            resultsTable = SharedTable.getTable();
        } else {
            cancel(NO_ELLIPSOIDS_FOUND);
        }
    }

    private Ellipsoid bump(final Ellipsoid ellipsoid,
                           final Collection<double[]> contactPoints, final double px, final double py,
                           final double pz) {

        final double displacement = vectorIncrement / 2;

        final double[] c = ellipsoid.getCentre();
        final double[] vector = contactPointUnitVector(ellipsoid, contactPoints);
        final double x = c[0] + vector[0] * displacement;
        final double y = c[1] + vector[1] * displacement;
        final double z = c[2] + vector[2] * displacement;

        Vector3d distance = new Vector3d(px, py, pz);
        distance.sub(new Vector3d(x, y, z));
        if (distance.length() < maxDrift) ellipsoid.setCentroid(
                x, y, z);

        return ellipsoid;
    }

    private double calculateFillingEfficiency(final int[][] maxIDs) {
        final int l = maxIDs.length;
        final long[] foregroundCount = new long[l];
        final long[] filledCount = new long[l];

        final AtomicInteger ai = new AtomicInteger(0);
        final Thread[] threads = Multithreader.newThreads();
        for (int thread = 0; thread < threads.length; thread++) {
            threads[thread] = new Thread(() -> {
                for (int i = ai.getAndIncrement(); i < l; i = ai.getAndIncrement()) {
                    IJ.showStatus("Calculating filling efficiency...");
                    IJ.showProgress(i, l);
                    final int[] idSlice = maxIDs[i];
                    for (final int val : idSlice) {
                        if (val >= -1) {
                            foregroundCount[i]++;
                        }
                        if (val >= 0) {
                            filledCount[i]++;
                        }
                    }
                }
            });
        }
        Multithreader.startAndJoin(threads);

        long sumForegroundCount = 0;
        long sumFilledCount = 0;

        for (int i = 0; i < l; i++) {
            sumForegroundCount += foregroundCount[i];
            sumFilledCount += filledCount[i];
        }

        final long unfilled = sumForegroundCount - sumFilledCount;
        logService.info(unfilled + " pixels unfilled with ellipsoids out of " +
                sumForegroundCount + " total foreground pixels");

        return sumFilledCount / (double) sumForegroundCount;
    }

    private ImagePlus displayMaximumIDs(final int[][] biggestEllipsoid,
                                        final ImagePlus imp) {

        final ImageStack bigStack = new ImageStack(imp.getWidth(), imp.getHeight());
        for (int i = 1; i < biggestEllipsoid.length; i++) {
            final int[] maxIDs = biggestEllipsoid[i];
            final int l = maxIDs.length;
            final float[] pixels = new float[l];
            for (int j = 0; j < l; j++) {
                pixels[j] = maxIDs[j];
            }
            bigStack.addSlice("" + i, pixels);
        }
        final ImagePlus bigImp = new ImagePlus("Max-ID-" + imp.getTitle(),
                bigStack);
        bigImp.setCalibration(imp.getCalibration());
        return bigImp;
    }

    private ArrayList<double[]> findContactPoints(final Ellipsoid ellipsoid,
                                                  final ArrayList<double[]> contactPoints, final byte[][] pixels,
                                                  final double pW, final double pH, final double pD, final int w, final int h,
                                                  final int d) {
        return findContactPoints(ellipsoid, contactPoints, getGeneralizedSpiralSetOnSphere(nVectors),
                pixels, pW, pH, pD, w, h, d);
    }

    /**
     * Using skeleton points as seeds, propagate along each vector until a
     * boundary is hit. Use the resulting cloud of boundary points as input into
     * an ellipsoid fit.
     *
     * @param imp
     * @param skeletonPoints
     * @return
     */
    private Ellipsoid[] findEllipsoids(final ImgPlus imp,
                                       final int[][] skeletonPoints) {
        final int nPoints = skeletonPoints.length;
        final Ellipsoid[] ellipsoids = new Ellipsoid[nPoints];

        // make sure array contains null in the non-calculated elements
        Arrays.fill(ellipsoids, null);

        final int w = (int) imp.dimension(0);
        final int h = (int) imp.dimension(1);
        final int d = (int) imp.dimension(2);

        final byte[][] pixels = new byte[d][w * h];
        final LongStream zRange = LongStream.range(0, imp.dimension(2));
        zRange.forEach(z -> {
            final long[] mins = {0, 0, z};
            final long[] maxs = {imp.dimension(0) - 1, imp.dimension(1) - 1, z};
            final Cursor<UnsignedByteType> sliceCursor = Views.interval(imp, mins, maxs).localizingCursor();
            while (sliceCursor.hasNext()) {
                sliceCursor.fwd();
                int[] position = new int[3];
                sliceCursor.localize(position);
                pixels[position[2]][position[1] * w + position[0]] = sliceCursor.get().getByte();
            }
        });


        final AtomicInteger ai = new AtomicInteger(0);
        final AtomicInteger counter = new AtomicInteger(0);
        final Thread[] threads = Multithreader.newThreads();
        for (int thread = 0; thread < threads.length; thread++) {
            threads[thread] = new Thread(() -> {
                for (int i = ai.getAndAdd(skipRatio); i < nPoints; i = ai.getAndAdd(
                        skipRatio)) {
                    ellipsoids[i] = optimiseEllipsoid(imp, pixels, skeletonPoints[i]);
                    IJ.showProgress(counter.getAndAdd(skipRatio), nPoints);
                    IJ.showStatus("Optimising ellipsoids...");
                }
            });
        }
        Multithreader.startAndJoin(threads);
        return Arrays.stream(ellipsoids).filter(Objects::nonNull).sorted((a,
                                                                          b) -> Double.compare(b.getVolume(), a.getVolume())).toArray(
                Ellipsoid[]::new);
    }

    private Ellipsoid inflateToFit(final Ellipsoid ellipsoid,
                                   ArrayList<double[]> contactPoints, final double a, final double b,
                                   final double c, final byte[][] pixels, final double pW, final double pH,
                                   final double pD, final int w, final int h, final int d) {

        contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH,
                pD, w, h, d);

        final double av = a * vectorIncrement;
        final double bv = b * vectorIncrement;
        final double cv = c * vectorIncrement;

        int safety = 0;
        while (contactPoints.size() < contactSensitivity &&
                safety < maxIterations) {
            ellipsoid.dilate(av, bv, cv);
            contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW,
                    pH, pD, w, h, d);
            safety++;
        }

        return ellipsoid;
    }

    private boolean isContained(final Ellipsoid ellipsoid, final byte[][] pixels,
                                final double pW, final double pH, final double pD, final int w, final int h,
                                final int d) {
        final double[][] points = ellipsoid.getSurfacePoints(nVectors);
        for (final double[] p : points) {
            final int x = (int) Math.floor(p[0] / pW);
            final int y = (int) Math.floor(p[1] / pH);
            final int z = (int) Math.floor(p[2] / pD);
            if (isOutOfBounds(x, y, z, w, h, d)) continue;
            if (pixels[z][y * w + x] != -1) return false;
        }
        return true;
    }

    /**
     * Check whether this ellipsoid is sensible
     *
     * @param ellipsoid
     * @param pW
     * @param pH
     * @param pD
     * @param w
     * @param h
     * @param d
     * @return true if half or more of the surface points are outside the image
     * stack, or if the volume of the ellipsoid exceeds that of the image
     * stack
     */
    private boolean isInvalid(final Ellipsoid ellipsoid, final double pW,
                              final double pH, final double pD, final int w, final int h, final int d) {

        final double[][] surfacePoints = ellipsoid.getSurfacePoints(nVectors);
        int outOfBoundsCount = 0;
        final int half = nVectors / 2;
        for (final double[] p : surfacePoints) {
            if (isOutOfBounds((int) (p[0] / pW), (int) (p[1] / pD), (int) (p[2] / pH),
                    w, h, d)) outOfBoundsCount++;
            if (outOfBoundsCount > half) return true;
        }

        final double volume = ellipsoid.getVolume();
        return volume > stackVolume;

    }

    /**
     * given a seed point, find the ellipsoid which best fits the binarised
     * structure
     *
     * @param imp
     * @return ellipsoid fitting the point cloud of boundaries lying at the end of
     * vectors surrounding the seed point. If ellipsoid fitting fails,
     * returns null
     */
    private Ellipsoid optimiseEllipsoid(final ImgPlus imp, byte[][] pixels,
                                        final int[] skeletonPoint) {

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

        Ellipsoid ellipsoid = new Ellipsoid(vectorIncrement, vectorIncrement,
                vectorIncrement, px, py, pz, orthogonalVectors);

        final List<Double> volumeHistory = new ArrayList<>();
        volumeHistory.add(ellipsoid.getVolume());

        // dilate the sphere until it hits the background
        while (isContained(ellipsoid, pixels, pW, pH, pD, w, h, d)) {
            ellipsoid.dilate(vectorIncrement, vectorIncrement, vectorIncrement);
        }

        volumeHistory.add(ellipsoid.getVolume());

        // instantiate the ArrayList
        ArrayList<double[]> contactPoints = new ArrayList<>();

        // get the points of contact
        contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH,
                pD, w, h, d);

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
        rotation = Ellipsoid.transpose(rotation);

        // rotate ellipsoid to point this way...
        ellipsoid.setRotation(rotation);

        // shrink the ellipsoid slightly
        shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);
        ellipsoid.contract(0.1);

        // dilate other two axes until number of contact points increases
        // by contactSensitivity number of contacts

        while (contactPoints.size() < contactSensitivity) {
            ellipsoid.dilate(0, vectorIncrement, vectorIncrement);
            contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW,
                    pH, pD, w, h, d);
            if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
                logService.info("Ellipsoid at (" + px + ", " + py + ", " + pz +
                        ") is invalid, nullifying at initial oblation");
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
        final int absoluteMaxIterations = maxIterations * 10;
        while (totalIterations < absoluteMaxIterations &&
                noImprovementCount < maxIterations) {

            // rotate a little bit
            ellipsoid = wiggle(ellipsoid);

            // contract until no contact
            ellipsoid = shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w,
                    h, d);

            // dilate an axis
            double[] abc = threeWayShuffle();
            ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2],
                    pixels, pW, pH, pD, w, h, d);

            if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
                logService.info("Ellipsoid at (" + px + ", " + py + ", " + pz +
                        ") is invalid, nullifying after " + totalIterations + " iterations");
                return null;
            }

            if (ellipsoid.getVolume() > maximal.getVolume()) maximal = ellipsoid
                    .copy();

            // bump a little away from the sides
            contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW,
                    pH, pD, w, h, d);
            // if can't bump then do a wiggle
            if (contactPoints.isEmpty()) {
                ellipsoid = wiggle(ellipsoid);
            } else {
                ellipsoid = bump(ellipsoid, contactPoints, px, py, pz);
            }

            // contract
            ellipsoid = shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w,
                    h, d);

            // dilate an axis
            abc = threeWayShuffle();
            ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2],
                    pixels, pW, pH, pD, w, h, d);

            if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
                logService.info("Ellipsoid at (" + px + ", " + py + ", " + pz +
                        ") is invalid, nullifying after " + totalIterations + " iterations");
                return null;
            }

            if (ellipsoid.getVolume() > maximal.getVolume()) maximal = ellipsoid
                    .copy();

            // rotate a little bit
            ellipsoid = turn(ellipsoid, contactPoints, pixels, pW, pH, pD, w, h, d);

            // contract until no contact
            ellipsoid = shrinkToFit(ellipsoid, contactPoints, pixels, pW, pH, pD, w,
                    h, d);

            // dilate an axis
            abc = threeWayShuffle();
            ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2],
                    pixels, pW, pH, pD, w, h, d);

            if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
                logService.info("Ellipsoid at (" + px + ", " + py + ", " + pz +
                        ") is invalid, nullifying after " + totalIterations + " iterations");
                return null;
            }

            if (ellipsoid.getVolume() > maximal.getVolume()) maximal = ellipsoid
                    .copy();

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
            if (volumeHistory.get(i) > volumeHistory.get(i - 1)) noImprovementCount =
                    0;
            else noImprovementCount++;

            totalIterations++;
        }

        // this usually indicates that the ellipsoid
        // grew out of control for some reason
        if (totalIterations == absoluteMaxIterations) {
            logService.info("Ellipsoid at (" + px + ", " + py + ", " + pz +
                    ") seems to be out of control, nullifying after " + totalIterations +
                    " iterations");
            return null;
        }

        final long stop = System.currentTimeMillis();

        if (logService.isDebug()) logService.info("Optimised ellipsoid in " + (stop - start) +
                " ms after " + totalIterations + " iterations (" + IJ.d2s((double) (stop -
                start) / totalIterations, 3) + " ms/iteration)");

        return ellipsoid;
    }

    private Ellipsoid shrinkToFit(final Ellipsoid ellipsoid,
                                  ArrayList<double[]> contactPoints, final byte[][] pixels, final double pW,
                                  final double pH, final double pD, final int w, final int h, final int d) {

        // get the contact points
        contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH,
                pD, w, h, d);

        // get the unit vectors to the contact points
        final double[][] unitVectors = findContactUnitVectors(ellipsoid,
                contactPoints);

        // contract until no contact
        int safety = 0;
        while (!contactPoints.isEmpty() && safety < maxIterations) {
            ellipsoid.contract(0.01);
            contactPoints = findContactPoints(ellipsoid, contactPoints, unitVectors,
                    pixels, pW, pH, pD, w, h, d);
            safety++;
        }

        ellipsoid.contract(0.05);

        return ellipsoid;
    }

    /**
     * Rotate the ellipsoid theta radians around the unit vector formed by the sum
     * of torques effected by unit normals acting on the surface of the ellipsoid
     *
     * @param ellipsoid
     * @param pW
     * @param pH
     * @param pD
     * @param w
     * @param h
     * @param d
     * @return
     */
    private Ellipsoid turn(Ellipsoid ellipsoid, ArrayList<double[]> contactPoints,
                           final byte[][] pixels, final double pW, final double pH, final double pD,
                           final int w, final int h, final int d) {

        contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW, pH,
                pD, w, h, d);
        if (!contactPoints.isEmpty()) {
            final double[] torque = calculateTorque(ellipsoid, contactPoints);
            ellipsoid = rotateAboutAxis(ellipsoid, norm(torque));
        }
        return ellipsoid;
    }

}


/**
 * <p>
 * Represents an ellipsoid defined by its centroid, eigenvalues and 3x3
 * eigenvector matrix. Semiaxis lengths (radii) are calculated as the inverse
 * square root of the eigenvalues.
 * </p>
 *
 * @author Michael Doube
 */
class Ellipsoid {

    /**
     * Eigenvalue matrix. Size-based ordering is not performed. They are in the
     * same order as the eigenvectors.
     */
    private final double[][] ed;
    /**
     * ID field for tracking this particular ellipsoid
     */
    public int id;
    /**
     * Centroid of ellipsoid (cx, cy, cz)
     */
    private double cx;
    private double cy;
    private double cz;
    /**
     * Radii (semiaxis lengths) of ellipsoid. Size-based ordering (e.g. a > b > c)
     * is not performed. They are in the same order as the eigenvalues and
     * eigenvectors.
     */
    private double ra;
    private double rb;
    private double rc;
    /**
     * Volume of ellipsoid, calculated as 4 * PI * ra * rb * rc / 3
     */
    private double volume;
    /**
     * Eigenvector matrix Size-based ordering is not performed. They are in the
     * same order as the eigenvalues.
     */
    private double[][] ev;
    /**
     * 3x3 matrix describing shape of ellipsoid
     */
    private double[][] eh;

    /**
     * Instantiate an ellipsoid from the result of FitEllipsoid
     *
     * @param ellipsoid the properties of an ellipsoid.
     */
    public Ellipsoid(final Object[] ellipsoid) {
        final double[] centre = (double[]) ellipsoid[0];
        cx = centre[0];
        cy = centre[1];
        cz = centre[2];

        final double[] radii = (double[]) ellipsoid[1];
        ra = radii[0];
        rb = radii[1];
        rc = radii[2];

        if (Double.isNaN(ra) || Double.isNaN(rb) || Double.isNaN(rc))
            throw new IllegalArgumentException("Radius is NaN");

        if (ra <= 0 || rb <= 0 || rc <= 0) throw new IllegalArgumentException(
                "Radius cannot be <= 0");

        ev = new double[3][3];
        ed = new double[3][3];
        eh = new double[3][3];
        setRotation((double[][]) ellipsoid[2]);
        setEigenvalues();
        setVolume();
    }

    /**
     * Construct an Ellipsoid from the radii (a,b,c), centroid (cx, cy, cz) and
     * Eigenvectors.
     *
     * @param a            1st radius.
     * @param b            2nd radius.
     * @param c            3rd radius.
     * @param cx           centroid x-coordinate.
     * @param cy           centroid y-coordinate.
     * @param cz           centroid z-coordinate.
     * @param eigenVectors the orientation of the ellipsoid.
     */
    public Ellipsoid(final double a, final double b, final double c,
                     final double cx, final double cy, final double cz,
                     final double[][] eigenVectors) {

        ra = a;
        rb = b;
        rc = c;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        ev = new double[3][3];
        ed = new double[3][3];
        eh = new double[3][3];
        setRotation(eigenVectors);
        setEigenvalues();
        setVolume();
    }

    /**
     * Transpose a 3x3 matrix in double[][] format. Does no error checking.
     *
     * @param a a matrix.
     * @return new transposed matrix.
     */
    public static double[][] transpose(final double[][] a) {
        final double[][] t = new double[3][3];
        t[0][0] = a[0][0];
        t[0][1] = a[1][0];
        t[0][2] = a[2][0];
        t[1][0] = a[0][1];
        t[1][1] = a[1][1];
        t[1][2] = a[2][1];
        t[2][0] = a[0][2];
        t[2][1] = a[1][2];
        t[2][2] = a[2][2];
        return t;
    }

    /**
     * High performance 3x3 matrix multiplier with no bounds or error checking
     *
     * @param a 3x3 matrix
     * @param b 3x3 matrix
     * @return result of matrix multiplication, c = ab
     */
    private static double[][] times(final double[][] a, final double[][] b) {
        final double a00 = a[0][0];
        final double a01 = a[0][1];
        final double a02 = a[0][2];
        final double a10 = a[1][0];
        final double a11 = a[1][1];
        final double a12 = a[1][2];
        final double a20 = a[2][0];
        final double a21 = a[2][1];
        final double a22 = a[2][2];
        final double b00 = b[0][0];
        final double b01 = b[0][1];
        final double b02 = b[0][2];
        final double b10 = b[1][0];
        final double b11 = b[1][1];
        final double b12 = b[1][2];
        final double b20 = b[2][0];
        final double b21 = b[2][1];
        final double b22 = b[2][2];
        return new double[][]{{a00 * b00 + a01 * b10 + a02 * b20, a00 * b01 +
                a01 * b11 + a02 * b21, a00 * b02 + a01 * b12 + a02 * b22}, {a10 * b00 +
                a11 * b10 + a12 * b20, a10 * b01 + a11 * b11 + a12 * b21, a10 * b02 +
                a11 * b12 + a12 * b22}, {a20 * b00 + a21 * b10 + a22 * b20, a20 *
                b01 + a21 * b11 + a22 * b21, a20 * b02 + a21 * b12 + a22 * b22},};
    }

    /**
     * Method based on the inequality (X-X0)^T H (X-X0) &le; 1 Where X is the test
     * point, X0 is the centroid, H is the ellipsoid's 3x3 matrix
     *
     * @param x x-coordinate of the point.
     * @param y y-coordinate of the point.
     * @param z z-coordinate of the point.
     * @return true if the point (x,y,z) lies inside or on the ellipsoid, false
     * otherwise
     */
    public boolean contains(final double x, final double y, final double z) {
        // calculate vector between point and centroid
        final double vx = x - cx;
        final double vy = y - cy;
        final double vz = z - cz;

        final double[] radii = getSortedRadii();
        final double maxRadius = radii[2];

        // if further than maximal sphere's bounding box, must be outside
        if (Math.abs(vx) > maxRadius || Math.abs(vy) > maxRadius || Math.abs(
                vz) > maxRadius) return false;

        // calculate distance from centroid
        final double length = Math.sqrt(vx * vx + vy * vy + vz * vz);

        // if further from centroid than major semiaxis length
        // must be outside
        if (length > maxRadius) return false;

        // if length closer than minor semiaxis length
        // must be inside
        if (length <= radii[0]) return true;

        final double[][] h = eh;

        final double dot0 = vx * h[0][0] + vy * h[1][0] + vz * h[2][0];
        final double dot1 = vx * h[0][1] + vy * h[1][1] + vz * h[2][1];
        final double dot2 = vx * h[0][2] + vy * h[1][2] + vz * h[2][2];

        final double dot = dot0 * vx + dot1 * vy + dot2 * vz;

        return dot <= 1;
    }

    /**
     * Constrict all three axes by a fractional increment
     *
     * @param increment scaling factor.
     */
    public void contract(final double increment) {
        dilate(-increment);
    }

    /**
     * Perform a deep copy of this Ellipsoid
     *
     * @return a copy of the instance.
     */
    public Ellipsoid copy() {
        final double[][] clone = new double[ev.length][];
        for (int i = 0; i < ev.length; i++) {
            clone[i] = ev[i].clone();
        }
        return new Ellipsoid(ra, rb, rc, cx, cy, cz, clone);
    }

    /**
     * Dilate the ellipsoid semiaxes by independent absolute amounts
     *
     * @param da value added to the 1st radius.
     * @param db value added to the 2nd radius.
     * @param dc value added to the 3rd radius.
     * @throws IllegalArgumentException if new semi-axes are non-positive.
     */
    public void dilate(final double da, final double db, final double dc)
            throws IllegalArgumentException {

        final double a = ra + da;
        final double b = rb + db;
        final double c = rc + dc;
        if (a <= 0 || b <= 0 || c <= 0) {
            throw new IllegalArgumentException("Ellipsoid cannot have semiaxis <= 0");
        }
        setRadii(a, b, c);
    }

    /**
     * Calculate the minimal axis-aligned bounding box of this ellipsoid Thanks to
     * Tavian Barnes for the simplification of the maths
     * http://tavianator.com/2014/06/exact-bounding-boxes-for-spheres-ellipsoids
     *
     * @return 6-element array containing x min, x max, y min, y max, z min, z max
     */
    public double[] getAxisAlignedBoundingBox() {
        final double[] x = getXMinAndMax();
        final double[] y = getYMinAndMax();
        final double[] z = getZMinAndMax();
        return new double[]{x[0], x[1], y[0], y[1], z[0], z[1]};
    }

    public double[] getCentre() {
        return new double[]{cx, cy, cz};
    }

    /**
     * Gets a copy of the radii.
     *
     * @return the semiaxis lengths a, b and c. Note these are not ordered by
     * size, but the order does relate to the 0th, 1st and 2nd columns of
     * the rotation matrix respectively.
     */
    public double[] getRadii() {
        return new double[]{ra, rb, rc};
    }

    /**
     * Return a copy of the ellipsoid's eigenvector matrix
     *
     * @return a 3x3 rotation matrix
     */
    public double[][] getRotation() {
        return ev.clone();
    }

    /**
     * Set rotation to the supplied rotation matrix. Does no error checking.
     *
     * @param rotation a 3x3 rotation matrix
     */
    public void setRotation(final double[][] rotation) {
        ev = rotation.clone();
        update3x3Matrix();
    }

    /**
     * Get the radii sorted in ascending order. Note that there is no guarantee
     * that this ordering relates at all to the eigenvectors or eigenvalues.
     *
     * @return radii in ascending order
     */
    public double[] getSortedRadii() {
        return of(ra, rb, rc).sorted().toArray();
    }

    public double[][] getSurfacePoints(final int nPoints) {

        // get regularly-spaced points on the unit sphere
        final double[][] vectors = EllipsoidFactorWrapper.getGeneralizedSpiralSetOnSphere(nPoints);
        return getSurfacePoints(vectors);

    }

    public double[][] getSurfacePoints(final double[][] vectors) {
        final int nPoints = vectors.length;
        for (int p = 0; p < nPoints; p++) {
            final double[] v = vectors[p];

            // stretch the unit sphere into an ellipsoid
            final double x = ra * v[0];
            final double y = rb * v[1];
            final double z = rc * v[2];
            // rotate and translate the ellipsoid into position
            final double vx = x * ev[0][0] + y * ev[0][1] + z * ev[0][2] + cx;
            final double vy = x * ev[1][0] + y * ev[1][1] + z * ev[1][2] + cy;
            final double vz = x * ev[2][0] + y * ev[2][1] + z * ev[2][2] + cz;

            vectors[p] = new double[]{vx, vy, vz};
        }
        return vectors;
    }

    /**
     * Gets the volume of this ellipsoid, calculated as PI * a * b * c * 4 / 3
     *
     * @return copy of the stored volume value.
     */
    public double getVolume() {
        return volume;
    }

    /**
     * Calculate the minimal and maximal y values bounding this ellipsoid
     *
     * @return array containing minimal and maximal y values
     */
    public double[] getYMinAndMax() {
        final double m21 = ev[1][0] * ra;
        final double m22 = ev[1][1] * rb;
        final double m23 = ev[1][2] * rc;
        final double d = Math.sqrt(m21 * m21 + m22 * m22 + m23 * m23);
        return new double[]{cy - d, cy + d};
    }

    /**
     * Calculate the minimal and maximal z values bounding this ellipsoid
     *
     * @return array containing minimal and maximal z values
     */
    public double[] getZMinAndMax() {
        final double m31 = ev[2][0] * ra;
        final double m32 = ev[2][1] * rb;
        final double m33 = ev[2][2] * rc;
        final double d = Math.sqrt(m31 * m31 + m32 * m32 + m33 * m33);
        return new double[]{cz - d, cz + d};
    }

    /**
     * Rotate the ellipsoid by the given 3x3 Matrix
     *
     * @param rotation a 3x3 rotation matrix
     */
    public void rotate(final double[][] rotation) {
        setRotation(times(ev, rotation));
    }

    /**
     * Translate the ellipsoid to a given new centroid
     *
     * @param x new centroid x-coordinate
     * @param y new centroid y-coordinate
     * @param z new centroid z-coordinate
     */
    public void setCentroid(final double x, final double y, final double z) {
        cx = x;
        cy = y;
        cz = z;
    }

    /**
     * Calculate the minimal and maximal x values bounding this ellipsoid
     *
     * @return array containing minimal and maximal x values
     */
    private double[] getXMinAndMax() {
        final double m11 = ev[0][0] * ra;
        final double m12 = ev[0][1] * rb;
        final double m13 = ev[0][2] * rc;
        final double d = Math.sqrt(m11 * m11 + m12 * m12 + m13 * m13);
        return new double[]{cx - d, cx + d};
    }

    /**
     * Calculates eigenvalues from current radii
     */
    private void setEigenvalues() {
        ed[0][0] = 1 / (ra * ra);
        ed[1][1] = 1 / (rb * rb);
        ed[2][2] = 1 / (rc * rc);
        update3x3Matrix();
    }

    /**
     * Set the radii (semiaxes). No ordering is assumed, except with regard to the
     * columns of the eigenvector rotation matrix (i.e. a relates to the 0th
     * eigenvector column, b to the 1st and c to the 2nd)
     *
     * @param a 1st radius of the ellipsoid.
     * @param b 2nd radius of the ellipsoid.
     * @param c 3rd radius of the ellipsoid.
     * @throws IllegalArgumentException if radii are non-positive.
     */
    private void setRadii(final double a, final double b, final double c)
            throws IllegalArgumentException {
        ra = a;
        rb = b;
        rc = c;
        setEigenvalues();
        setVolume();
    }

    private void setVolume() {
        volume = Math.PI * ra * rb * rc * 4 / 3;
    }

    /**
     * Needs to be run any time the eigenvalues or eigenvectors change
     */
    private void update3x3Matrix() {
        eh = times(times(ev, ed), transpose(ev));
    }

    /**
     * Dilate all three axes by a fractional increment
     *
     * @param increment scaling factor.
     */
    void dilate(final double increment) {
        dilate(ra * increment, rb * increment, rc * increment);
    }
}
