package org.bonej.wrapperPlugins;

import ij.ImagePlus;
import ij.ImageStack;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imagej.units.UnitService;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
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
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.lang.Math;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.bonej.utilities.AxisUtils.isSpatialCalibrationsIsotropic;
import static org.bonej.utilities.ImageBoundsUtil.outOfBounds;
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
        stackVolume = inputImage.dimension(0)*inputImage.dimension(1)*inputImage.dimension(2);
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
        while (isContained(ellipsoid)) {
            ellipsoid.setC(ellipsoid.getC()+vectorIncrement);
            ellipsoid.setB(ellipsoid.getB()+vectorIncrement);
            ellipsoid.setA(ellipsoid.getA()+vectorIncrement);
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
        final Vector3d xAxis = new Vector3d(1,0,0);
        Vector3d middleAxis = shortAxis.cross(xAxis, new Vector3d());
        middleAxis.normalize();

        // find a mutually orthogonal axis by forming the cross product
        Vector3d longAxis = shortAxis.cross(middleAxis, new Vector3d());
        longAxis.normalize();

        // construct a rotation matrix
        final Matrix3d rotation = new Matrix3d();
        rotation.setColumn(0,shortAxis);
        rotation.setColumn(1,middleAxis);
        rotation.setColumn(2,longAxis);

        // rotate ellipsoid to point this way...
        ellipsoid.setOrientation(rotation);

        // shrink the ellipsoid slightly
        double contraction = 0.1;
        ellipsoid.setA(ellipsoid.getA()-contraction);
        ellipsoid.setB(ellipsoid.getB()-contraction);
        ellipsoid.setC(ellipsoid.getC()-contraction);

        // dilate other two axes until number of contact points increases
        // by contactSensitivity number of contacts

        while (contactPoints.size() < contactSensitivity) {
            ellipsoid.setB(ellipsoid.getB()+vectorIncrement);
            ellipsoid.setC(ellipsoid.getC()+vectorIncrement);
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
        Ellipsoid maximal = updateMaximalEllipsoid(ellipsoid);

        // alternately try each axis
        int totalIterations = 0;
        int noImprovementCount = 0;
        final int absoluteMaxIterations = maxIterations * 10;
        while (totalIterations < absoluteMaxIterations && noImprovementCount < maxIterations)
        {

            // rotate a little bit
            ellipsoid = wiggle(ellipsoid);

            // contract until no contact
            ellipsoid = shrinkToFit(ellipsoid, contactPoints);

            // dilate an axis
            double[] abc = threeWayShuffle();
            ellipsoid = inflateToFit(ellipsoid, contactPoints, abc[0], abc[1], abc[2]);

            if (isInvalid(ellipsoid)) {
                reportInvalidEllipsoid(ellipsoid,totalIterations,") is invalid, nullifying after ");
                return null;
            }

            if (ellipsoid.getVolume() > maximal.getVolume())
            {
                maximal = updateMaximalEllipsoid(ellipsoid);
            }

            // bump a little away from the sides
            contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));
            // if can't bump then do a wiggle
            if (contactPoints.isEmpty()) {
                ellipsoid = wiggle(ellipsoid);
            }
            else {
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

            if (ellipsoid.getVolume() > maximal.getVolume())
            {
                maximal = updateMaximalEllipsoid(ellipsoid);
            }

            // rotate a little bit
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

            if (ellipsoid.getVolume() > maximal.getVolume())
            {
                maximal = updateMaximalEllipsoid(ellipsoid);
            }

            // keep the maximal ellipsoid found
            ellipsoid = updateMaximalEllipsoid(ellipsoid);
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
        contactPoints = findContactPoints(ellipsoid, contactPoints,getRegularVectors(nVectors));
        if (!contactPoints.isEmpty()) {
            final Vector3d torque = calculateTorque(ellipsoid, contactPoints);
            torque.normalize();
            ellipsoid = rotateAboutAxis(ellipsoid, torque);
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
        rotation.setRow(0, new Vector4d(cos + x * x * cos1, xycos1 - zsin, xzcos1 + ysin,0));
        rotation.setRow(1, new Vector4d(xycos1 + zsin, cos + y * y * cos1, yzcos1 - xsin,0));
        rotation.setRow(2, new Vector4d(xzcos1 - ysin, yzcos1 + xsin, cos + z * z * cos1,0));

        Matrix4d orientation = ellipsoid.getOrientation();
        orientation = orientation.mul(rotation);
        Matrix3d rotated = new Matrix3d();
        orientation.get3x3(rotated);
        ellipsoid.setOrientation(rotated);

        return ellipsoid;
    }

    /**
     * Calculate the torque of unit normals acting at the contact points
     *
     * @param ellipsoid
     * @param contactPoints
     * @return
     */
    private static Vector3d calculateTorque(final Ellipsoid ellipsoid,
                                            final Iterable<double[]> contactPoints)
    {

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
            Vector4dc point = new Vector4d(px,py,pz,0);

            // derotate the point
            Vector4d x = new Vector4d();
            inv.transform(point,x);

            // calculate the unit normal on the centred and derotated ellipsoid
            final Vector3d un = new Vector3d(s*x.get(0),t*x.get(1),u*x.get(2));
            un.normalize();

            // rotate the normal back to the original ellipsoid
            Vector4d un4 = new Vector4d(un,0);
            rot.transform(un4);

            Vector3d point3 = new Vector3d(point.get(0),point.get(1), point.get(2));
            final Vector3d torqueVector = point3.cross(new Vector3d(un4.get(0),un4.get(1), un4.get(2)), new Vector3d());

            t0 += torqueVector.get(0);
            t1 += torqueVector.get(1);
            t2 += torqueVector.get(2);

        }
        return new Vector3d(-t0, -t1, -t2);
    }

    private void reportInvalidEllipsoid(Ellipsoid ellipsoid, int totalIterations, String s) {
        logService.info("Ellipsoid at (" + ellipsoid.getCentroid().get(0) + ", " + ellipsoid.getCentroid().get(1) + ", " + ellipsoid.getCentroid().get(2) +
                s + totalIterations + " iterations");
    }

    private Ellipsoid updateMaximalEllipsoid(Ellipsoid ellipsoid) {
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

        Vector3d newCentroid =  new Vector3d(ellipsoid.getCentroid());
        newCentroid.add(vector);

        if (vector.length() < maxDrift) ellipsoid.setCentroid(newCentroid);
    }

    private Ellipsoid inflateToFit(Ellipsoid ellipsoid, ArrayList<double[]> contactPoints, double a, double b, double c) {
        contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));

        List<Double> scaledIncrements = Stream.of(a,b,c).sorted().map(d -> d*vectorIncrement).collect(toList());//TODO sort columns!
        final double av = scaledIncrements.get(0);
        final double bv = scaledIncrements.get(1);
        final double cv = scaledIncrements.get(2);

        int safety = 0;
        while (contactPoints.size() < contactSensitivity &&
                safety < maxIterations)
        {
            ellipsoid.setC(ellipsoid.getC()+cv);
            ellipsoid.setB(ellipsoid.getB()+bv);
            ellipsoid.setA(ellipsoid.getA()+av);
            contactPoints = findContactPoints(ellipsoid, contactPoints, getRegularVectors(nVectors));
            safety++;
        }

        return ellipsoid;
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
        for(int i = 0; i<unitVectorList.size(); i++)
        {
            unitVectors[i] = unitVectorList.get(i);
        }

        // contract until no contact
        int safety = 0;
        while (!contactPoints.isEmpty() && safety < maxIterations) {
            contract(ellipsoid, 0.01);
            contactPoints = findContactPoints(ellipsoid, contactPoints, unitVectors);
            safety++;
        }

        contract(ellipsoid,0.05);

        return ellipsoid;
    }

    private void contract(Ellipsoid ellipsoid, double contraction) {
        if(contraction<ellipsoid.getA()) {
            ellipsoid.setA(ellipsoid.getA() - contraction);
            ellipsoid.setB(ellipsoid.getB() - contraction);
            ellipsoid.setC(ellipsoid.getC() - contraction);
        }
        //TODO else?
    }

    private Ellipsoid wiggle(Ellipsoid ellipsoid) {
        final double b = Math.random() * 0.2 - 0.1;
        final double c = Math.random() * 0.2 - 0.1;
        final double a = Math.sqrt(1 - b * b - c * c);

        // zeroth column, should be very close to [1, 0, 0]^T (mostly x)
        final Vector3d zerothColumn = new Vector3d(a,b,c);

        // form triangle in random plane
        final Vector3d vector = randomVector();

        // first column, should be very close to [0, 1, 0]^T
        final Vector3d firstColumn = zerothColumn.cross(vector, new Vector3d());
        firstColumn.normalize();

        // second column, should be very close to [0, 0, 1]^T
        final Vector3d secondColumn = zerothColumn.cross(firstColumn, new Vector3d());
        secondColumn.normalize();

        ellipsoid.setOrientation(new Matrix3d(zerothColumn,firstColumn,secondColumn));
        return ellipsoid;
    }

    private boolean isInvalid(Ellipsoid ellipsoid) {
        final double[][] surfacePoints = getSurfacePoints(ellipsoid,nVectors);
        int outOfBoundsCount = 0;
        final int half = nVectors / 2;
        for (final double[] p : surfacePoints) {
            long[] position = {Math.round(p[0]),Math.round(p[1]),Math.round(p[2])};
            if (outOfBounds(inputImage, position)) outOfBoundsCount++;
            if (outOfBoundsCount > half) return true;
        }

        final double volume = ellipsoid.getVolume();
        return volume > stackVolume;

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
                                                   final Collection<double[]> contactPoints)
    {

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
            final Vector3d lv = new Vector3d(x,y,z);
            final double l = lv.length();

            xSum += x / l;
            ySum += y / l;
            zSum += z / l;
        }

        final double x = xSum / nPoints;
        final double y = ySum / nPoints;
        final double z = zSum / nPoints;
        final Vector3d lv = new Vector3d(x,y,z);
        final double l = lv.length();

        return new Vector3d(x / l, y / l, z / l);
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

    private ArrayList<double[]> findContactPoints(
            final Ellipsoid ellipsoid, final ArrayList<double[]> contactPoints,
            final double[][] unitVectors)
    {
        contactPoints.clear();
        final double[][] points = getSurfacePoints(ellipsoid,unitVectors);
        final RandomAccess<T> access = inputImage.randomAccess();
        for (final double[] p : points) {
            final int x = (int) Math.floor(p[0]);
            final int y = (int) Math.floor(p[1]);
            final int z = (int) Math.floor(p[2]);
            long[] position = {x,y,z};
            if (outOfBounds(inputImage, position)) {
                continue;
            }
            access.setPosition(position);
            if (access.get().getRealFloat()==0) {
                contactPoints.add(p);
            }
        }
        return contactPoints;
    }

    private boolean isContained(final Ellipsoid ellipsoid)
    {
        final double[][] points = getSurfacePoints(ellipsoid, nVectors);
        final RandomAccess<T> access = inputImage.randomAccess();
        for (final double[] p : points) {
            final int x = (int) Math.floor(p[0]);
            final int y = (int) Math.floor(p[1]);
            final int z = (int) Math.floor(p[2]);
            long[] position = {x,y,z};
            if (outOfBounds(inputImage,position)) continue;
            access.setPosition(position);
            if (access.get().getRealFloat()==0) return false;
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

            vectors[p] = new double[] { vx, vy, vz };
        }
        return vectors;
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
        final double[] a = { 0, 0, 0 };
        final double rand = Math.random();
        if (rand < 1.0 / 3.0) a[0] = 1;
        else if (rand >= 2.0 / 3.0) a[2] = 1;
        else a[1] = 1;
        return a;
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
