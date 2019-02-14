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

import ij.ImagePlus;
import ij.ImageStack;
import net.imagej.ImgPlus;
import net.imagej.display.ColorTables;
import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imagej.units.UnitService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.joml.*;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.lang.Math;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.imglib2.roi.Regions.countTrue;
import static org.bonej.utilities.AxisUtils.isSpatialCalibrationsIsotropic;
import static org.bonej.utilities.ImageBoundsUtil.outOfBounds;
import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

/**
 * Ellipsoid Factor
 * <p>
 * Ellipsoid
 * </p>
 *
 * @author Alessandro Felder
 * @author Michael Doube
 * @author Richard Domander
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor 2")
public class EllipsoidFactorWrapper<T extends RealType<T> & NativeType<T>>
        extends ContextCommand {

    // Several ellipsoids may fall in same bin if this is too small a number!
    // This will be ignored!
    private static final long FLINN_PLOT_DIMENSION = 501;
    private static final String NO_ELLIPSOIDS_FOUND = "No ellipsoids were found - try allowing more sampling directions and/or more seedpoints.";

    static {
        // NB: Needed if you mix-and-match IJ1 and IJ2 classes.
        // And even then: do not use IJ1 classes in the API!
        LegacyInjector.preinit();
    }

    @Parameter
    UnitService unitService;
    @Parameter(label = "Show secondary images")
    boolean showSecondaryImages = false;
    @Parameter
    CommandService cs;
    @Parameter
    OpService opService;
    @Parameter
    LogService logService;
    @Parameter
    StatusService statusService;
    @Parameter(type = ItemIO.OUTPUT)
    ImagePlus skeletonization;
    @Parameter(label = "Gaussian sigma (px)")
    double sigma = 0;
    @Parameter(validater = "validateImage")
    private ImgPlus<T> inputImage;
    @Parameter
    private UIService uiService;
    private boolean calibrationWarned;
    @Parameter(visibility = ItemVisibility.MESSAGE)
    private String setup = "Setup";
    @Parameter(label = "Vectors")
    private int nVectors = 100;
    /**
     * increment for vector searching in real units. Defaults to ~Nyquist sampling
     * of a unit pixel
     */
    @Parameter(label = "Sampling_increment")
    private double vectorIncrement = 1 / 2.3;
    /**
     * Number of skeleton points per ellipsoid. Sets the granularity of the
     * ellipsoid fields.
     */
    @Parameter(label = "Skeleton_points per ellipsoid")
    private int skipRatio = 50;
    @Parameter(label = "Contact sensitivity")
    private int contactSensitivity = 1;
    /**
     * Safety value to prevent while() running forever
     */
    @Parameter(label = "Maximum_iterations")
    private int maxIterations = 100;
    /**
     * maximum distance ellipsoid may drift from seed point. Defaults to voxel
     * diagonal length
     */
    @Parameter(label = "Maximum_drift")
    private double maxDrift = Math.sqrt(3);
    private double stackVolume;
    private double[][] regularVectors;
    @Parameter(visibility = ItemVisibility.MESSAGE)
    private String outputs = "Outputs";

    @Parameter(visibility = ItemVisibility.MESSAGE)
    private String note =
            "Ellipsoid Factor is beta software.\n" +
                    "Please report your experiences to the user group:\n" +
                    "http://forum.image.sc/tags/bonej";
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

    /**
     * Calculate the torque of unit normals acting at the contact points
     *
     * @param ellipsoid
     * @param contactPoints
     * @return
     */
    private static Vector3d calculateTorque(final Ellipsoid ellipsoid,
                                            final Iterable<double[]> contactPoints) {

        final Vector3d pc = ellipsoid.getCentroid();
        final double cx = pc.get(0);
        final double cy = pc.get(1);
        final double cz = pc.get(2);
        ;
        final double a = ellipsoid.getA();
        final double b = ellipsoid.getB();
        final double c = ellipsoid.getC();

        final double s = 2 / (a * a);
        final double t = 2 / (b * b);
        final double u = 2 / (c * c);

        final Matrix4d rot = ellipsoid.getOrientation();
        final Matrix4d inv = new Matrix4d();
        rot.transpose(inv);

        double t0 = 0;
        double t1 = 0;
        double t2 = 0;

        for (final double[] p : contactPoints) {
            // translate point to centre on origin
            final double px = p[0] - cx;
            final double py = p[1] - cy;
            final double pz = p[2] - cz;
            Vector4dc point = new Vector4d(px, py, pz, 0);

            // derotate the point
            Vector4d x = new Vector4d();
            inv.transform(point, x);

            // calculate the unit normal on the centred and derotated ellipsoid
            final Vector3d un = new Vector3d(s * x.get(0), t * x.get(1), u * x.get(2));
            un.normalize();

            // rotate the normal back to the original ellipsoid
            Vector4d un4 = new Vector4d(un, 0);
            rot.transform(un4);

            Vector3d point3 = new Vector3d(point.get(0), point.get(1), point.get(2));
            final Vector3d torqueVector = point3.cross(new Vector3d(un4.get(0), un4.get(1), un4.get(2)), new Vector3d());

            t0 += torqueVector.get(0);
            t1 += torqueVector.get(1);
            t2 += torqueVector.get(2);

        }
        return new Vector3d(-t0, -t1, -t2);
    }

    /**
     * Calculate the mean unit vector between the ellipsoid's centroid and contact
     * points
     *
     * @param ellipsoid
     * @param contactPoints
     * @return
     */
    private static Vector3d contactPointUnitVector(final Ellipsoid ellipsoid,
                                                   final Collection<double[]> contactPoints) {

        final int nPoints = contactPoints.size();

        if (nPoints < 1) throw new IllegalArgumentException(
                "Need at least one contact point");

        final Vector3d c = ellipsoid.getCentroid();
        final double cx = c.get(0);
        final double cy = c.get(1);
        final double cz = c.get(2);
        double xSum = 0;
        double ySum = 0;
        double zSum = 0;
        for (final double[] p : contactPoints) {
            final double x = p[0] - cx;
            final double y = p[1] - cy;
            final double z = p[2] - cz;
            final Vector3d lv = new Vector3d(x, y, z);
            final double l = lv.length();

            xSum += x / l;
            ySum += y / l;
            zSum += z / l;
        }

        final double x = xSum / nPoints;
        final double y = ySum / nPoints;
        final double z = zSum / nPoints;
        final Vector3d lv = new Vector3d(x, y, z);
        final double l = lv.length();

        return new Vector3d(x / l, y / l, z / l);
    }

    /**
     * Generate an array of regularly-spaced 3D unit vectors. The vectors aren't
     * equally spaced in all directions, but there is no clustering around the
     * sphere's poles.
     *
     * @param nVectors number of vectors to generate
     * @return 2D array (nVectors x 3) containing unit vectors
     */
    public static double[][] getRegularVectors(final int nVectors) {

        final double[][] vectors = new double[nVectors][];
        final double inc = Math.PI * (3 - Math.sqrt(5));
        final double off = 2 / (double) nVectors;

        for (int k = 0; k < nVectors; k++) {
            final double y = k * off - 1 + (off / 2);
            final double r = Math.sqrt(1 - y * y);
            final double phi = k * inc;
            final double x = Math.cos(phi) * r;
            final double z = Math.sin(phi) * r;
            final double[] vector = {x, y, z};
            vectors[k] = vector;
        }
        return vectors;
    }

    /**
     * Generate a single randomly-oriented vector on the unit sphere
     *
     * @return 3-element double array containing [x y z]^T
     */
    public static Vector3d randomVector() {
        final double z = 2 * Math.random() - 1;
        final double rho = Math.sqrt(1 - z * z);
        final double phi = Math.PI * (2 * Math.random() - 1);
        final double x = rho * Math.cos(phi);
        final double y = rho * Math.sin(phi);
        return new Vector3d(x, y, z);
    }

    private static double[] threeWayShuffle() {
        final double[] a = {0, 0, 0};
        final double rand = Math.random();
        if (rand < 1.0 / 3.0) a[0] = 1;
        else if (rand >= 2.0 / 3.0) a[2] = 1;
        else a[1] = 1;
        return a;
    }

    private static void colourSlice(final RandomAccessible<IntType> idImage,
                                    final Cursor<BitType> mask, final Collection<Ellipsoid> localEllipsoids, final Map<Ellipsoid, Integer> iDs) {
        while (mask.hasNext()) {
            mask.fwd();
            if (!mask.get().get()) {
                continue;
            }
            final long[] coordinates = new long[3];
            mask.localize(coordinates);
            final Vector3d point = new Vector3d(coordinates[0], coordinates[1],
                    coordinates[2]);
            point.add(0.5, 0.5, 0.5);
            colourID(localEllipsoids, idImage, point, iDs);
        }
    }

    private static void colourID(final Collection<Ellipsoid> localEllipsoids,
                                 final RandomAccessible<IntType> ellipsoidIdentityImage,
                                 final Vector3dc point, final Map<Ellipsoid, Integer> iDs) {
        final Optional<Ellipsoid> candidate = localEllipsoids.stream().filter(e -> e.inside(point)).findFirst();
        if (!candidate.isPresent()) {
            return;
        }
        final RandomAccess<IntType> eIDRandomAccess = ellipsoidIdentityImage
                .randomAccess();
        eIDRandomAccess.setPosition(toPixelGrid(point));
        final Ellipsoid ellipsoid = candidate.get();
        eIDRandomAccess.get().set(iDs.get(ellipsoid));
    }

    private static long[] toPixelGrid(Vector3dc point) {
        long[] position = {(long) point.get(0), (long) point.get(1), (long) point.get(2)};
        return position;
    }

    private static float computeEllipsoidFactor(final Ellipsoid ellipsoid) {
        return (float) (ellipsoid.getA() / ellipsoid.getB() - ellipsoid.getB() / ellipsoid.getC());
    }

    private static Img<IntType> assignEllipsoidIDs(final Img<BitType> mask,
                                                   final List<Ellipsoid> ellipsoids) {
        final Img<IntType> idImage = ArrayImgs.ints(mask.dimension(0), mask
                .dimension(1), mask.dimension(2));
        idImage.forEach(c -> c.setInteger(-1));
        final Map<Ellipsoid, Integer> iDs =
                IntStream.range(0, ellipsoids.size()).boxed().collect(toMap(ellipsoids::get, Function.identity()));
        final LongStream zRange = LongStream.range(0, mask.dimension(2));
        zRange.parallel().forEach(z -> {
            // multiply by image unit? make more intelligent bounding box?
            final List<Ellipsoid> localEllipsoids = ellipsoids.stream().filter(
                    e -> Math.abs(e.getCentroid().z() - z) < e.getC()).collect(
                    toList());
            final long[] mins = {0, 0, z};
            final long[] maxs = {mask.dimension(0) - 1, mask.dimension(1) - 1,
                    z};
            final Cursor<BitType> maskSlice = Views.interval(mask, mins, maxs)
                    .localizingCursor();
            colourSlice(idImage, maskSlice, localEllipsoids, iDs);
        });
        return idImage;
    }

    @Override
    public void run() {

        regularVectors = getRegularVectors(nVectors);

        final List<Vector3dc> skeletonPoints = getSkeletonPoints();
        logService.info("Found " + skeletonPoints.size() + " skeleton points");


        long start = System.currentTimeMillis();
        stackVolume = inputImage.dimension(0) * inputImage.dimension(1) * inputImage.dimension(2);
        final List<Ellipsoid> ellipsoids = findEllipsoids(inputImage, skeletonPoints);
        long stop = System.currentTimeMillis();

        if (ellipsoids.isEmpty()) {
            cancel(NO_ELLIPSOIDS_FOUND);
            return;
        }

        logService.info("Found and sorted " + ellipsoids.size() + " ellipsoids in " + (stop - start) +
                " ms");

        statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
        final ImgPlus<BitType> bitImage = Common.toBitTypeImgPlus(opService, inputImage);
        final Img<IntType> ellipsoidIdentityImage = assignEllipsoidIDs(bitImage, ellipsoids);
        createPrimaryOutputImages(ellipsoids, ellipsoidIdentityImage);
        if (showSecondaryImages) createSecondaryOutputImages(ellipsoids, ellipsoidIdentityImage);

        final double numberOfForegroundVoxels = countTrue(bitImage);
        final double numberOfAssignedVoxels = countAssignedVoxels(ellipsoidIdentityImage);
        final double fillingPercentage = 100.0 * (numberOfAssignedVoxels / numberOfForegroundVoxels);
        addResults(ellipsoids, fillingPercentage);

        if (logService.isDebug()) {
            /*logService.debug("initial sampling directions = " + nSphere);
            logService.debug("threshold for ridge point inclusions = " + thresholdForBeingARidgePoint);*/
            logService.debug("assigned voxels = " + numberOfAssignedVoxels);
            logService.debug("foreground voxels = " + numberOfForegroundVoxels);
            for (int i = 0; i < Math.min(100, ellipsoids.size()); i++) {
                logService.debug("ellipsoid(" + i + "):\n" + ellipsoids.get(i).toString());
            }
        }

    }

    private List<Ellipsoid> findEllipsoids(ImgPlus<T> inputImage, List<Vector3dc> skeletonPoints) {
        statusService.showStatus("Optimising ellipsoids...");
        if (skipRatio > 1) {
            int limit = skeletonPoints.size() / skipRatio + Math.min(skeletonPoints.size() % skipRatio, 1);
            skeletonPoints = Stream.iterate(0, i -> i + skipRatio)
                    .limit(limit)
                    .map(skeletonPoints::get).collect(toList());
        }

        final List<Ellipsoid> ellipsoids = new ArrayList<>(skeletonPoints.size());
        skeletonPoints.parallelStream().forEach(sp -> ellipsoids.add(optimiseEllipsoid(inputImage, sp)));
        return ellipsoids.stream().filter(Objects::nonNull).sorted(Comparator.comparingDouble(e -> -e.getVolume())).collect(toList());
    }

    private Ellipsoid optimiseEllipsoid(ImgPlus<T> imp, Vector3dc skeletonPoint) {
        final long start = System.currentTimeMillis();

        //TODO deal with calibration and parallelisation

        Ellipsoid ellipsoid = new Ellipsoid(vectorIncrement, vectorIncrement, vectorIncrement);
        ellipsoid.setCentroid(new Vector3d(skeletonPoint));

        final List<Double> volumeHistory = new ArrayList<>();
        volumeHistory.add(ellipsoid.getVolume());


        // dilate the sphere until it hits the background
        while (isContained(ellipsoid)) {
            dilateEllipsoidIsotropic(ellipsoid, vectorIncrement);
        }

        volumeHistory.add(ellipsoid.getVolume());

        // instantiate the ArrayList
        ArrayList<double[]> contactPoints = new ArrayList<>();

        // get the points of contact
        contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));

        // find the mean unit vector pointing to the points of contact from the
        // centre
        final Vector3d shortAxis = contactPointUnitVector(ellipsoid, contactPoints);

        // find an orthogonal axis TODO this is a bug if shortAxis parallel to xAxis
        final Vector3d xAxis = new Vector3d(1, 0, 0);
        Vector3d middleAxis = shortAxis.cross(xAxis, new Vector3d());
        middleAxis.normalize();

        // find a mutually orthogonal axis by forming the cross product
        Vector3d longAxis = shortAxis.cross(middleAxis, new Vector3d());
        longAxis.normalize();

        // construct a rotation matrix
        final Matrix3d rotation = new Matrix3d();
        rotation.setColumn(0, shortAxis);
        rotation.setColumn(1, middleAxis);
        rotation.setColumn(2, longAxis);

        // rotate ellipsoid to point this way...
        ellipsoid.setOrientation(rotation);

        // shrink the ellipsoid slightly
        double contraction = 0.1;
        ellipsoid.setA(ellipsoid.getA() - contraction);
        ellipsoid.setB(ellipsoid.getB() - contraction);
        ellipsoid.setC(ellipsoid.getC() - contraction);

        // dilate other two axes until number of contact points increases
        // by contactSensitivity number of contacts

        while (contactPoints.size() < contactSensitivity) {
            ellipsoid.setB(ellipsoid.getB() + vectorIncrement);
            ellipsoid.setC(ellipsoid.getC() + vectorIncrement);
            contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));
            if (isInvalid(ellipsoid)) {
                logService.info("Ellipsoid at (" + ellipsoid.getCentroid().get(0) + ", " + ellipsoid.getCentroid().get(1) + ", " + ellipsoid.getCentroid().get(2) +
                        ") is invalid, nullifying at initial oblation");
                return null;
            }
        }

        volumeHistory.add(ellipsoid.getVolume());

        // until ellipsoid is totally jammed within the structure, go through
        // cycles of contraction, wiggling, dilation
        // goal is maximal inscribed ellipsoid, maximal being defined by volume

        // store a copy of the 'best ellipsoid so far'
        Ellipsoid maximal = copyEllipsoid(ellipsoid);

        // alternately try each axis
        int totalIterations = 0;
        int noImprovementCount = 0;
        final int absoluteMaxIterations = maxIterations * 10;
        while (totalIterations < absoluteMaxIterations && noImprovementCount < maxIterations) {

            // rotate a little bit
            ellipsoid = wiggle(ellipsoid);

            // contract until no contact
            ellipsoid = shrinkToFit(ellipsoid, contactPoints);

            // dilate an axis
            double[] abc = threeWayShuffle();
            ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2]);

            if (isInvalid(ellipsoid)) {
                reportInvalidEllipsoid(ellipsoid, totalIterations, ") is invalid, nullifying after ");
                return null;
            }

            if (ellipsoid.getVolume() > maximal.getVolume()) {
                maximal = copyEllipsoid(ellipsoid);
            }

            // bump a little away from the sides
            contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));
            // if can't bump then do a wiggle
            if (contactPoints.isEmpty()) {
                ellipsoid = wiggle(ellipsoid);
            } else {
                bump(ellipsoid, contactPoints);
            }

            // contract
            ellipsoid = shrinkToFit(ellipsoid, contactPoints);

            // dilate an axis
            abc = threeWayShuffle();
            ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2]);

            if (isInvalid(ellipsoid)) {
                reportInvalidEllipsoid(ellipsoid, totalIterations, ") is invalid, nullifying after ");
                return null;
            }

            if (ellipsoid.getVolume() > maximal.getVolume()) {
                maximal = copyEllipsoid(ellipsoid);
            }

            // rotate a little bit
            Ellipsoid test = copyEllipsoid(ellipsoid);
            ellipsoid = turn(ellipsoid, contactPoints);

            // contract until no contact
            ellipsoid = shrinkToFit(ellipsoid, contactPoints);

            // dilate an axis
            abc = threeWayShuffle();
            ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2]);

            if (isInvalid(ellipsoid)) {
                reportInvalidEllipsoid(ellipsoid, totalIterations, ") is invalid, nullifying after ");
                return null;
            }

            if (ellipsoid.getVolume() > maximal.getVolume()) {
                maximal = copyEllipsoid(ellipsoid);
            }

            // keep the maximal ellipsoid found
            ellipsoid = copyEllipsoid(maximal);
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
            reportInvalidEllipsoid(ellipsoid, totalIterations, ") seems to be out of control, nullifying after ");
            return null;
        }

        final long stop = System.currentTimeMillis();

        if (logService.isDebug()) logService.info("Optimised ellipsoid in " + (stop - start) +
                " ms after " + totalIterations + " iterations (" + ((double) (stop -
                start) / totalIterations) + " ms/iteration)");

        return ellipsoid;
    }

    private Ellipsoid turn(Ellipsoid ellipsoid, ArrayList<double[]> contactPoints) {
        contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));
        if (!contactPoints.isEmpty()) {
            final Vector3d torque = calculateTorque(ellipsoid, contactPoints);
            if (torque.length() == 0.0) {
                logService.info("Warning: zero torque vector - no turn performed");
            } else {
                torque.normalize();
                ellipsoid = rotateAboutAxis(ellipsoid, torque);
            }
        }
        return ellipsoid;
    }

    private Ellipsoid rotateAboutAxis(Ellipsoid ellipsoid, Vector3d axis) {
        final double theta = 0.1;
        final double sin = Math.sin(theta);
        final double cos = Math.cos(theta);
        final double cos1 = 1 - cos;
        final double x = axis.get(0);
        final double y = axis.get(1);
        final double z = axis.get(2);
        final double xy = x * y;
        final double xz = x * z;
        final double yz = y * z;
        final double xsin = x * sin;
        final double ysin = y * sin;
        final double zsin = z * sin;
        final double xycos1 = xy * cos1;
        final double xzcos1 = xz * cos1;
        final double yzcos1 = yz * cos1;
        final Matrix4d rotation = new Matrix4d();
        rotation.setRow(0, new Vector4d(cos + x * x * cos1, xycos1 - zsin, xzcos1 + ysin, 0));
        rotation.setRow(1, new Vector4d(xycos1 + zsin, cos + y * y * cos1, yzcos1 - xsin, 0));
        rotation.setRow(2, new Vector4d(xzcos1 - ysin, yzcos1 + xsin, cos + z * z * cos1, 0));

        Matrix4d orientation = ellipsoid.getOrientation();
        orientation = orientation.mul(rotation);
        Matrix3d rotated = new Matrix3d();
        orientation.get3x3(rotated);
        ellipsoid.setOrientation(rotated);

        return ellipsoid;
    }

    private void reportInvalidEllipsoid(Ellipsoid ellipsoid, int totalIterations, String s) {
        logService.info("Ellipsoid at (" + ellipsoid.getCentroid().get(0) + ", " + ellipsoid.getCentroid().get(1) + ", " + ellipsoid.getCentroid().get(2) +
                s + totalIterations + " iterations");
    }

    private Ellipsoid copyEllipsoid(Ellipsoid ellipsoid) {
        Ellipsoid maximal;
        maximal = new Ellipsoid(ellipsoid.getA(), ellipsoid.getB(), ellipsoid.getC());
        maximal.setCentroid(ellipsoid.getCentroid());
        Matrix3d orientation = new Matrix3d();
        ellipsoid.getOrientation().get3x3(orientation);
        maximal.setOrientation(orientation);
        return maximal;
    }

    private void bump(Ellipsoid ellipsoid, ArrayList<double[]> contactPoints) {

        final double displacement = vectorIncrement / 2;

        final Vector3d c = ellipsoid.getCentroid();
        final Vector3d vector = contactPointUnitVector(ellipsoid, contactPoints);
        vector.mul(displacement);

        Vector3d newCentroid = new Vector3d(ellipsoid.getCentroid());
        newCentroid.add(vector);

        if (vector.length() < maxDrift) ellipsoid.setCentroid(newCentroid);
    }

    private Ellipsoid inflateToFit(Ellipsoid ellipsoid, ArrayList<double[]> contactPoints, double a, double b, double c) {
        contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));

        List<Double> scaledIncrements = Stream.of(a, b, c).map(d -> d * vectorIncrement).collect(toList());
        final double av = scaledIncrements.get(0);
        final double bv = scaledIncrements.get(1);
        final double cv = scaledIncrements.get(2);

        int safety = 0;
        while (contactPoints.size() < contactSensitivity &&
                safety < maxIterations) {
            stretchEllipsoidAnisotropic(ellipsoid, av, bv, cv);
            contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));
            safety++;
        }

        return ellipsoid;
    }

    private void stretchEllipsoidAnisotropic(Ellipsoid ellipsoid, double av, double bv, double cv) {
        final List<Vector3d> semiAxes = ellipsoid.getSemiAxes();
        stretchSemiAxis(av, semiAxes.get(0));//1.2391304347826084
        stretchSemiAxis(bv, semiAxes.get(1));//1.2391304347826082
        stretchSemiAxis(cv, semiAxes.get(2));
        ellipsoid.setSemiAxes(semiAxes.get(0), semiAxes.get(1), semiAxes.get(2));
    }

    private void stretchSemiAxis(double av, Vector3d semiAxis) {
        double lengtha = semiAxis.length() + av;
        semiAxis.normalize();
        semiAxis.mul(lengtha);
    }

    private void dilateEllipsoidIsotropic(Ellipsoid ellipsoid, double increment) {
        ellipsoid.setC(ellipsoid.getC() + increment);
        ellipsoid.setB(ellipsoid.getB() + increment);
        ellipsoid.setA(ellipsoid.getA() + increment);
    }

    private Ellipsoid shrinkToFit(Ellipsoid ellipsoid, ArrayList<double[]> contactPoints) {
        // get the contact points
        contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));

        // get the unit vectors to the contact points
        final List<double[]> unitVectorList = contactPoints.stream().map(c ->
        {
            Vector3d cp = new Vector3d(c[0], c[1], c[2]);
            cp.sub(ellipsoid.getCentroid());
            cp.normalize();
            return new double[]{cp.get(0), cp.get(1), cp.get(2)};
        }).collect(toList());

        double[][] unitVectors = new double[unitVectorList.size()][3];
        for (int i = 0; i < unitVectorList.size(); i++) {
            unitVectors[i] = unitVectorList.get(i);
        }

        // contract until no contact
        int safety = 0;
        while (!contactPoints.isEmpty() && safety < maxIterations) {
            contract(ellipsoid, 0.05);
            contactPoints = findContactPoints(ellipsoid, contactPoints, unitVectors);
            safety++;
        }

        contract(ellipsoid, 0.05);

        return ellipsoid;
    }

    private void contract(Ellipsoid ellipsoid, double contraction) {
        if (contraction < 0.5 * ellipsoid.getA()) {
            ellipsoid.setA(ellipsoid.getA() - contraction);
            ellipsoid.setB(ellipsoid.getB() - contraction);
            ellipsoid.setC(ellipsoid.getC() - contraction);
        } else {
            logService.info("Warning: too much contraction!");
        }
    }

    private Ellipsoid wiggle(Ellipsoid ellipsoid) {
        final double b = Math.random() * 0.2 - 0.1;
        final double c = Math.random() * 0.2 - 0.1;
        final double a = Math.sqrt(1 - b * b - c * c);

        // zeroth column, should be very close to [1, 0, 0]^T (mostly x)
        final Vector3d zerothColumn = new Vector3d(a, b, c);

        // form triangle in random plane
        final Vector3d vector = randomVector();

        // first column, should be very close to [0, 1, 0]^T
        final Vector3d firstColumn = zerothColumn.cross(vector, new Vector3d());
        firstColumn.normalize();

        // second column, should be very close to [0, 0, 1]^T
        final Vector3d secondColumn = zerothColumn.cross(firstColumn, new Vector3d());
        secondColumn.normalize();

        ellipsoid.setOrientation(new Matrix3d(zerothColumn, firstColumn, secondColumn));
        return ellipsoid;
    }

    private boolean isInvalid(Ellipsoid ellipsoid) {
        final double[][] surfacePoints = getSurfacePoints(ellipsoid, nVectors);
        int outOfBoundsCount = 0;
        final int half = nVectors / 2;
        for (final double[] p : surfacePoints) {
            long[] position = {Math.round(p[0]), Math.round(p[1]), Math.round(p[2])};
            if (outOfBounds(inputImage, position)) outOfBoundsCount++;
            if (outOfBoundsCount > half) return true;
        }

        final double volume = ellipsoid.getVolume();
        return volume > stackVolume;

    }

    private List<Vector3dc> getSkeletonPoints() {
        ImagePlus skeleton = null;
        try {
            final CommandModule skeletonizationModule = cs.run("org.bonej.wrapperPlugins.SkeletoniseWrapper", true, inputImage).get();
            skeleton = (ImagePlus) skeletonizationModule.getOutput("skeleton");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        skeletonization = skeleton;

        final ImageStack skeletonStack = skeleton.getImageStack();
        final List<Vector3dc> skeletonPoints = new ArrayList<>();
        for (int z = 0; z < skeleton.getStackSize(); z++) {
            final byte[] slicePixels = (byte[]) skeletonStack.getPixels(z + 1);
            for (int x = 0; x < skeleton.getWidth(); x++) {
                for (int y = 0; y < skeleton.getHeight(); y++) {
                    if (slicePixels[y * skeleton.getWidth() + x] != 0) {
                        skeletonPoints.add(new Vector3d(x, y, z));
                    }
                }
            }
        }
        return skeletonPoints;
    }

    private ArrayList<double[]> findContactPoints(
            final Ellipsoid ellipsoid, final ArrayList<double[]> contactPoints,
            final double[][] unitVectors) {
        contactPoints.clear();
        final double[][] points = getSurfacePoints(ellipsoid, unitVectors);
        final RandomAccess<T> access = inputImage.randomAccess();
        for (final double[] p : points) {
            final int x = (int) Math.floor(p[0]);
            final int y = (int) Math.floor(p[1]);
            final int z = (int) Math.floor(p[2]);
            long[] position = {x, y, z};
            if (outOfBounds(inputImage, position)) {
                continue;
            }
            access.setPosition(position);
            if (access.get().getRealFloat() == 0) {
                contactPoints.add(p);
            }
        }
        return contactPoints;
    }

    private boolean isContained(final Ellipsoid ellipsoid) {
        final double[][] points = getSurfacePoints(ellipsoid, nVectors);
        final RandomAccess<T> access = inputImage.randomAccess();
        for (final double[] p : points) {
            final int x = (int) Math.floor(p[0]);
            final int y = (int) Math.floor(p[1]);
            final int z = (int) Math.floor(p[2]);
            long[] position = {x, y, z};
            if (outOfBounds(inputImage, position)) continue;
            access.setPosition(position);
            if (access.get().getRealFloat() == 0) return false;
        }
        return true;
    }

    public double[][] getSurfacePoints(Ellipsoid e, final int nPoints) {

        // get regularly-spaced points on the unit sphere
        final double[][] vectors = getRegularVectors(nPoints);
        return getSurfacePoints(e, vectors);

    }

    public double[][] getSurfacePoints(Ellipsoid e, final double[][] vectors) {
        final int nPoints = vectors.length;
        for (int p = 0; p < nPoints; p++) {
            final double[] v = vectors[p];

            // stretch the unit sphere into an ellipsoid
            final double x = e.getA() * v[0];
            final double y = e.getB() * v[1];
            final double z = e.getC() * v[2];
            // rotate and translate the ellipsoid into position
            final List<Vector3d> semiAxes = e.getSemiAxes();
            final Vector3d c = e.getCentroid();
            final double vx = x * semiAxes.get(0).get(0) + y * semiAxes.get(0).get(1) + z * semiAxes.get(0).get(2) + c.get(0);
            final double vy = x * semiAxes.get(1).get(0) + y * semiAxes.get(1).get(1) + z * semiAxes.get(1).get(2) + c.get(1);
            final double vz = x * semiAxes.get(2).get(0) + y * semiAxes.get(2).get(1) + z * semiAxes.get(2).get(2) + c.get(2);

            vectors[p] = new double[]{vx, vy, vz};
        }
        return vectors;
    }

    private void createAToBImage(final double[] aBRatios,
                                 final IterableInterval<IntType> ellipsoidIDs) {
        final Img<FloatType> aToBImage = createNaNCopy();
        mapValuesToImage(aBRatios, ellipsoidIDs, aToBImage);
        aToBAxisRatioImage = new ImgPlus<>(aToBImage, "a/b");
        aToBAxisRatioImage.setChannelMaximum(0, 1.0f);
        aToBAxisRatioImage.setChannelMinimum(0, 0.0f);
    }

    private void createBToCImage(final double[] bCRatios,
                                 final IterableInterval<IntType> ellipsoidIDs) {
        final Img<FloatType> bToCImage = createNaNCopy();
        mapValuesToImage(bCRatios, ellipsoidIDs, bToCImage);
        bToCAxisRatioImage = new ImgPlus<>(bToCImage, "b/c");
        bToCAxisRatioImage.setChannelMaximum(0, 1.0f);
        bToCAxisRatioImage.setChannelMinimum(0, 0.0f);
    }

    private void createEFImage(final Collection<Ellipsoid> ellipsoids,
                               final IterableInterval<IntType> ellipsoidIDs) {
        final Img<FloatType> ellipsoidFactorImage = createNaNCopy();
        final double[] ellipsoidFactors = ellipsoids.parallelStream().mapToDouble(
                EllipsoidFactorWrapper::computeEllipsoidFactor).toArray();
        mapValuesToImage(ellipsoidFactors, ellipsoidIDs, ellipsoidFactorImage);
        efImage = new ImgPlus<>(ellipsoidFactorImage, "EF");
        efImage.setChannelMaximum(0, 1);
        efImage.setChannelMinimum(0, -1);
        efImage.initializeColorTables(1);
        efImage.setColorTable(ColorTables.FIRE, 0);
    }

    private Img<FloatType> createNaNCopy() {
        final ArrayImg<FloatType, FloatArray> copy = ArrayImgs.floats(inputImage
                .dimension(0), inputImage.dimension(1), inputImage.dimension(2));
        copy.forEach(e -> e.setReal(Float.NaN));
        return copy;
    }

    private void createFlinnPeakPlot(final double[] aBRatios,
                                     final double[] bCRatios, final Img<IntType> ellipsoidIDs) {
        Img<FloatType> flinnPeakPlot = ArrayImgs.floats(FLINN_PLOT_DIMENSION,
                FLINN_PLOT_DIMENSION);
        final RandomAccess<FloatType> flinnPeakPlotRA = flinnPeakPlot
                .randomAccess();
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
            final long x = Math.round(aBRatios[localMaxEllipsoidID] *
                    (FLINN_PLOT_DIMENSION - 1));
            final long y = Math.round(bCRatios[localMaxEllipsoidID] *
                    (FLINN_PLOT_DIMENSION - 1));
            flinnPeakPlotRA.setPosition(new long[]{x, FLINN_PLOT_DIMENSION - y -
                    1});
            final float currentValue = flinnPeakPlotRA.get().getRealFloat();
            flinnPeakPlotRA.get().set(currentValue + 1.0f);
        }
        if (sigma > 0.0) {
            flinnPeakPlot = (Img<FloatType>) opService.filter().gauss(flinnPeakPlot,
                    sigma);
        }
        flinnPeakPlotImage = new ImgPlus<>(flinnPeakPlot, "Flinn Peak Plot");
        flinnPeakPlotImage.setChannelMaximum(0, 255.0f);
        flinnPeakPlotImage.setChannelMinimum(0, 0.0f);
    }

    private void createFlinnPlotImage(final double[] aBRatios,
                                      final double[] bCRatios) {
        final Img<BitType> flinnPlot = ArrayImgs.bits(FLINN_PLOT_DIMENSION,
                FLINN_PLOT_DIMENSION);
        final RandomAccess<BitType> flinnRA = flinnPlot.randomAccess();
        for (int i = 0; i < aBRatios.length; i++) {
            final long x = Math.round(aBRatios[i] * (FLINN_PLOT_DIMENSION - 1));
            final long y = FLINN_PLOT_DIMENSION - Math.round(bCRatios[i] *
                    (FLINN_PLOT_DIMENSION - 1)) - 1;
            flinnRA.setPosition(x, 0);
            flinnRA.setPosition(y, 1);
            flinnRA.get().setOne();
        }
        flinnPlotImage = new ImgPlus<>(flinnPlot, "Unweighted Flinn Plot");
        flinnPlotImage.setChannelMaximum(0, 255.0f);
        flinnPlotImage.setChannelMinimum(0, 0.0f);
    }

    private void createPrimaryOutputImages(final List<Ellipsoid> ellipsoids,
                                           final Img<IntType> ellipsoidIDs) {
        createEFImage(ellipsoids, ellipsoidIDs);
        createVolumeImage(ellipsoids, ellipsoidIDs);
    }

    private void createSecondaryOutputImages(final List<Ellipsoid> ellipsoids, final Img<IntType> ellipsoidIDs) {
        final double[] aBRatios = ellipsoids.parallelStream().mapToDouble(e -> e
                .getA() / e.getB()).toArray();
        createAToBImage(aBRatios, ellipsoidIDs);
        final double[] bCRatios = ellipsoids.parallelStream().mapToDouble(e -> e
                .getB() / e.getC()).toArray();
        createBToCImage(bCRatios, ellipsoidIDs);
        createFlinnPlotImage(aBRatios, bCRatios);
        createFlinnPeakPlot(aBRatios, bCRatios, ellipsoidIDs);
        eIdImage = new ImgPlus<>(ellipsoidIDs, "ID");
        eIdImage.setChannelMaximum(0, ellipsoids.size() / 10.0f);
        eIdImage.setChannelMinimum(0, -1.0f);
    }

    private void createVolumeImage(final List<Ellipsoid> ellipsoids,
                                   final IterableInterval<IntType> ellipsoidIDs) {
        final Img<FloatType> volumeImage = createNaNCopy();
        final double[] volumes = ellipsoids.parallelStream().mapToDouble(
                Ellipsoid::getVolume).toArray();
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

    private void addResults(final List<Ellipsoid> ellipsoids, double fillingPercentage) {
        final String label = inputImage.getName();
        SharedTable.add(label, "filling percentage", fillingPercentage);
        SharedTable.add(label, "number of ellipsoids found", ellipsoids.size());
        if (SharedTable.hasData()) {
            resultsTable = SharedTable.getTable();
        } else {
            cancel(NO_ELLIPSOIDS_FOUND);
        }
    }

    private void mapValuesToImage(final double[] values, final IterableInterval<IntType> ellipsoidIdentityImage, final RandomAccessible<FloatType> ellipsoidFactorImage) {
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

    @SuppressWarnings("unused")
    private void validateImage() {
        if (inputImage == null) {
            cancel(NO_IMAGE_OPEN);
            return;
        }
        if (AxisUtils.countSpatialDimensions(inputImage) != 3) {
            cancel(NOT_3D_IMAGE);
            return;
        }
        if (!ElementUtil.isColorsBinary(inputImage)) {
            cancel(NOT_BINARY);
            return;
        }
        if (!isSpatialCalibrationsIsotropic(inputImage, 0.01, unitService) &&
                !calibrationWarned) {
            final DialogPrompt.Result result = uiService.showDialog(
                    "The voxels in the image are anisotropic, which may affect results. Continue anyway?",
                    WARNING_MESSAGE, OK_CANCEL_OPTION);
            // Avoid showing warning more than once (validator gets called before and
            // after dialog pops up..?)
            calibrationWarned = true;
            if (result != OK_OPTION) {
                cancel(null);
            }
        }
    }
    // endregion

}
