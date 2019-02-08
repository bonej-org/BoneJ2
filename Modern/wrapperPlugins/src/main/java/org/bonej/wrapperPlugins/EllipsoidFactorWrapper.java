package org.bonej.wrapperPlugins;

import ij.ImagePlus;
import ij.ImageStack;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;
import net.imagej.patcher.LegacyInjector;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.command.ContextCommand;
import org.scijava.convert.ConvertService;
import org.scijava.convert.DefaultConvertService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;
import static org.bonej.utilities.AxisUtils.isSpatialCalibrationsIsotropic;
import static org.bonej.wrapperPlugins.CommonMessages.*;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;
import static org.scijava.ui.DialogPrompt.OptionType.OK_CANCEL_OPTION;
import static org.scijava.ui.DialogPrompt.Result.OK_OPTION;

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor 2")
public class EllipsoidFactorWrapper<T extends RealType<T> & NativeType<T>>
        extends ContextCommand
{

    static {
        // NB: Needed if you mix-and-match IJ1 and IJ2 classes.
        // And even then: do not use IJ1 classes in the API!
        LegacyInjector.preinit();
    }

    @Parameter(validater = "validateImage")
    private ImgPlus<T> inputImage;

    @Parameter
    UnitService unitService;

    @Parameter
    private UIService uiService;
    private boolean calibrationWarned;

    @Parameter(visibility = ItemVisibility.MESSAGE)
    private String setup =
            "Setup";
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

    /** Safety value to prevent while() running forever */
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
    private String outputs =
            "Outputs";

    @Parameter(label = "EF_image")
    boolean showEFImage = true;

    @Parameter(label = "Ellipsoid_ID_image")
    boolean showEllipsoidIDImage = false;

    @Parameter(label = "Volume_image")
    boolean showVolumeImage = false;

    @Parameter(label = "EF_image")
    boolean showAxisRatioImages = false;

    @Parameter(label = "Flinn_peak_plot")
    boolean showFlinnPeakPlot = false;

    @Parameter(label = "Gaussian sigma (px)")
    double gaussianSigma = 0;

    @Parameter(label = "Flinn_plot")
    boolean showFlinnPlot = false;

    @Parameter(visibility = ItemVisibility.MESSAGE)
    private String note =
            "Ellipsoid Factor is beta software.\n" +
                "Please report your experiences to the user group:\n" +
                "http://forum.image.sc/tags/bonej";

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

    @Parameter(type = ItemIO.OUTPUT)
    Img<UnsignedIntType> skeletonPointsImage;

    @Override
    public void run() {
        regularVectors = getRegularVectors(nVectors);

        final List<Vector3dc> skeletonPoints = getSkeletonPoints();
        logService.info("Found "+skeletonPoints.size()+" skeleton points");

        long start = System.currentTimeMillis();
        final List<Ellipsoid> ellipsoids = findEllipsoids(inputImage, skeletonPoints);
        long stop = System.currentTimeMillis();

        logService.info("Found " + ellipsoids.size() + " ellipsoids in " + (stop - start) +
                " ms");


    }

    private List<Ellipsoid> findEllipsoids(ImgPlus<T> inputImage, List<Vector3dc> skeletonPoints) {
        statusService.showStatus("Optimising ellipsoids...");
        final List<Ellipsoid> ellipsoids = new ArrayList<>();
        skeletonPoints.stream().skip(skipRatio).forEach(sp -> ellipsoids.add(optimiseEllipsoid(inputImage, sp)));
        return ellipsoids.stream().filter(Objects::nonNull).sorted(Comparator.comparingDouble(e -> -e.getVolume())).collect(toList());
    }

    private Ellipsoid optimiseEllipsoid(ImgPlus<T> imp, Vector3dc skeletonPoint) {
        final long start = System.currentTimeMillis();

        //TODO deal with calibration and parallelisation

        final Img<T> stack = imp.getImg();
        final long w = stack.dimension(0);
        final long h = stack.dimension(1);
        final long d = stack.dimension(2);


        Ellipsoid ellipsoid = new Ellipsoid(vectorIncrement, vectorIncrement,
                vectorIncrement);
        ellipsoid.setCentroid(new Vector3d(skeletonPoint));

        final List<Double> volumeHistory = new ArrayList<>();
        volumeHistory.add(ellipsoid.getVolume());

        // dilate the sphere until it hits the background
        while (isContained(ellipsoid, pixels, pW, pH, pD, w, h, d)) {
            ellipsoid.setA(ellipsoid.getA()+vectorIncrement);
            ellipsoid.setB(ellipsoid.getB()+vectorIncrement);
            ellipsoid.setC(ellipsoid.getC()+vectorIncrement);
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
        final double[] xAxis = { 1, 0, 0 };
        double[] middleAxis = crossProduct(shortAxis, xAxis);
        middleAxis = norm(middleAxis);

        // find a mutually orthogonal axis by forming the cross product
        double[] longAxis = crossProduct(shortAxis, middleAxis);
        longAxis = norm(longAxis);

        // construct a rotation matrix
        double[][] rotation = { shortAxis, middleAxis, longAxis };
        rotation = Ellipsoid.transpose(rotation);

        // rotate ellipsoid to point this way...
        ellipsoid.setRotation(rotation);

        // shrink the ellipsoid slightly
        ellipsoid.contract(0.1);

        // dilate other two axes until number of contact points increases
        // by contactSensitivity number of contacts

        while (contactPoints.size() < contactSensitivity) {
            ellipsoid.dilate(0, vectorIncrement, vectorIncrement);
            contactPoints = findContactPoints(ellipsoid, contactPoints, pixels, pW,
                    pH, pD, w, h, d);
            if (isInvalid(ellipsoid, pW, pH, pD, w, h, d)) {
                IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz +
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
                noImprovementCount < maxIterations)
        {

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
                IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz +
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
            }
            else {
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
                IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz +
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
                IJ.log("Ellipsoid at (" + px + ", " + py + ", " + pz +
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
                " ms after " + totalIterations + " iterations (" + ((double) (stop -
                start) / totalIterations) + " ms/iteration)");

        return ellipsoid;
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
            final byte[] slicePixels = (byte[]) skeletonStack.getPixels(z+1);
            for(int x = 0; x<skeleton.getWidth();x++) {
                for (int y = 0; y < skeleton.getHeight(); y++) {
                    if(slicePixels[y*skeleton.getWidth()+x]!=0)
                    {
                        skeletonPoints.add(new Vector3d(x,y,z));
                    }
                }
            }
        }
        return skeletonPoints;
    }

    private boolean isContained(final Ellipsoid ellipsoid, final byte[][] pixels,
                                final double pW, final double pH, final double pD, final int w, final int h,
                                final int d)
    {
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
            final double[] vector = { x, y, z };
            vectors[k] = vector;
        }
        return vectors;
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
                !calibrationWarned)
        {
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
