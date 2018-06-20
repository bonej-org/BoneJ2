package org.bonej.wrapperPlugins;

import net.imagej.ImgPlus;
import net.imagej.display.ColorTables;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.binary.Thresholder;
import net.imglib2.algorithm.edge.Edgel;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.FindLocalEllipsoidOp;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.UIService;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static net.imglib2.roi.Regions.countTrue;


/**
 * Ellipsoid Factor
 * <p>
 * Ellipsoid
 * </p>
 *
 * @author Alessandro Felder
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor")
public class EllipsoidFactorWrapper<R extends RealType<R>> extends ContextCommand {

    private final FindLocalEllipsoidOp findLocalEllipsoidOp = new FindLocalEllipsoidOp();

    @Parameter(validater = "imageValidater")
    private ImgPlus<IntegerType> inputImage;

    @Parameter(persist = false, required = false)
    private DoubleType estimatedCharacteristicLength = new DoubleType(2.5);

    @Parameter(persist = false, required = false)
    private DoubleType percentageOfRidgePoints = new DoubleType(0.8);

    @Parameter(label = "Ridge image", type = ItemIO.OUTPUT)
    private ImgPlus<UnsignedByteType> ridgePointsImage;

    @Parameter(label = "EF image", type = ItemIO.OUTPUT)
    private ImgPlus<FloatType> efImage;

    @Parameter(label = "ID image", type = ItemIO.OUTPUT)
    private ImgPlus<IntType> eIdImage;

    @SuppressWarnings("unused")
    @Parameter
    private OpService opService;

    @SuppressWarnings("unused")
    @Parameter
    private StatusService statusService;

    @SuppressWarnings("unused")
    @Parameter
    private UIService uiService;

    @SuppressWarnings("unused")
    @Parameter
    private PrefService prefService;

    @Override
    public void run() {
        statusService.showStatus("Ellipsoid Factor: initialising...");
        Img<BitType> inputAsBit = opService.convert().bit(inputImage);
        final RandomAccess<BitType> inputBitRA = inputAsBit.randomAccess();
        DoubleType minimumAxisLength = new DoubleType(2.5);

        //find clever seed and boundary points
        final Img<R> distanceTransform = (Img<R>) opService.image().distancetransform(inputAsBit);
        double samplingWidth = 1.0/2.3;
        int nSphere = estimateNSpiralPointsRequired(estimatedCharacteristicLength.get(), samplingWidth);
        List<Vector3d> sphereSamplingDirections = getGeneralizedSpiralSetOnSphere(nSphere);

        List<Shape> shapes = new ArrayList<>();
        shapes.add(new HyperSphereShape(2));
        final IterableInterval<R> open = opService.morphology().open(distanceTransform, shapes);
        final IterableInterval<R> close = opService.morphology().close(distanceTransform, shapes);
        final IterableInterval<R> ridge = opService.math().subtract(close, open);
        final Cursor<R> ridgeCursor = ridge.localizingCursor();
        //remove ridgepoints in BG - how does this make a difference?
        while(ridgeCursor.hasNext()){
            ridgeCursor.fwd();
            long[] position = new long[3];
            ridgeCursor.localize(position);
            inputBitRA.setPosition(position);
            if(!inputBitRA.get().get())
            {
                ridgeCursor.get().setReal(0.0f);
            }
        }

        final double ridgePointCutOff = percentageOfRidgePoints.getRealFloat()*opService.stats().max(ridge).getRealFloat();
        final Img<R> ridgeImg = (Img) ridge;
        final Img<BitType> thresholdedRidge = Thresholder.threshold(ridgeImg, (R) new FloatType((float) ridgePointCutOff), true, 1);
        ridgePointsImage = new ImgPlus<>(opService.convert().uint8(thresholdedRidge), "Seeding Points");

        final List<ValuePair<List<Vector3d>, List<ValuePair<Vector3d, Vector3d>>>> starVolumes = new ArrayList<>();
        final List<Vector3d> internalSeedPoints = new ArrayList<>();

        ridgeCursor.reset();
        while (ridgeCursor.hasNext()) {
            ridgeCursor.fwd();
            final double localValue = ridgeCursor.get().getRealFloat();
            if (localValue > ridgePointCutOff) {
                final List<ValuePair<Vector3d, Vector3d>> seedPoints = new ArrayList<>();
                final List<Vector3d> boundaryPoints = new ArrayList<>();
                long[] position = new long[3];
                ridgeCursor.localize(position);
                final Vector3d internalSeedPoint = new Vector3d(position[0]+0.5, position[1]+0.5, position[2]+0.5);
                internalSeedPoints.add(internalSeedPoint);
                List<Vector3d> contactPoints = sphereSamplingDirections.stream().map(d -> {
                    final Vector3d direction = new Vector3d(d);
                    return findFirstPointInBGAlongRay(direction, internalSeedPoint);
                }).collect(toList());

                contactPoints.forEach(c -> {
                    boundaryPoints.add(c);
                    Vector3d inwardDirection = new Vector3d(internalSeedPoint);
                    inwardDirection.sub(c);
                    inwardDirection.normalize();
                    seedPoints.add(new ValuePair<>(c, inwardDirection));
                });
                starVolumes.add(new ValuePair<>(boundaryPoints, seedPoints));
            }
        }
        statusService.showStatus("Ellipsoid Factor: finding ellipsoids...");

        final List<Ellipsoid> ellipsoids = starVolumes.parallelStream().map(s -> getLocalEllipsoids(s.getB(), s.getA())).flatMap(l -> l.stream()).filter(e -> e.getA()>minimumAxisLength.get() && whollyContainedInForeground(e,sphereSamplingDirections)).collect(Collectors.toList());
        ellipsoids.sort(Comparator.comparingDouble(e -> -e.getVolume()));

        //find EF values
        statusService.showStatus("Ellipsoid Factor: preparing assignment...");
        final Img<FloatType> ellipsoidFactorImage = ArrayImgs.floats(inputImage.dimension(0), inputImage.dimension(1), inputImage.dimension(2));
        ellipsoidFactorImage.cursor().forEachRemaining(c -> c.setReal(Double.NaN));
        final Img<IntType> ellipsoidIdentityImage = ArrayImgs.ints(inputImage.dimension(0), inputImage.dimension(1), inputImage.dimension(2));
        ellipsoidIdentityImage.cursor().forEachRemaining(c -> c.setInteger(-1));

        final Cursor<BitType> inputCursor = inputAsBit.localizingCursor();
        long numberOfForegroundVoxels = countTrue(inputAsBit);
        List<Vector3d> voxelCentrePoints = new ArrayList<>();

        while (inputCursor.hasNext()) {
            inputCursor.fwd();
            if(inputCursor.get().get()) {
                long [] coordinates = new long[3];
                inputCursor.localize(coordinates);
                voxelCentrePoints.add(new Vector3d(coordinates[0]+0.5, coordinates[1]+0.5, coordinates[2]+0.5));
            }
        }

        statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
        long numberOfAssignedVoxels = voxelCentrePoints.parallelStream().filter(centrePoint -> assignEllipsoidFactor(ellipsoids,ellipsoidFactorImage,ellipsoidIdentityImage,centrePoint)).count();


        final LogService log = uiService.log();
        log.initialize();
        log.info("found "+ellipsoids.size()+" ellipsoids");
        log.info("assigned voxels = "+numberOfAssignedVoxels);
        log.info("foreground voxels = "+numberOfForegroundVoxels);
        double fillingPercentage = 100.0*((double) numberOfAssignedVoxels)/((double) numberOfForegroundVoxels);
        log.info("filling percentage = "+fillingPercentage+"%");

        efImage = new ImgPlus<FloatType>(ellipsoidFactorImage, "EF");
        efImage.setChannelMaximum(0,1);
        efImage.setChannelMinimum(  0,-1);
        efImage.initializeColorTables(1);
        efImage.setColorTable(ColorTables.FIRE, 0);

        eIdImage = new ImgPlus<>(ellipsoidIdentityImage, "ID");

    }

    private boolean assignEllipsoidFactor(List<Ellipsoid> ellipsoids, Img<FloatType> ellipsoidFactorImage, Img<IntType> ellipsoidIdentityImage, Vector3d point) {
        boolean assigned = false;

        //find largest ellipsoid containing current position
        int currentEllipsoidCounter = 0;
        while (currentEllipsoidCounter < ellipsoids.size() && !insideEllipsoid(point, ellipsoids.get(currentEllipsoidCounter))) {
            currentEllipsoidCounter++;
        }

        RandomAccess<FloatType> efRandomAccess = ellipsoidFactorImage.randomAccess();
        RandomAccess<IntType> eIDRandomAccess = ellipsoidIdentityImage.randomAccess();

        efRandomAccess.setPosition(vectorToPixelGrid(point));
        eIDRandomAccess.setPosition(vectorToPixelGrid(point));

        //ignore background voxels and voxels not contained in any ellipsoid
        if (currentEllipsoidCounter == ellipsoids.size()) {
            efRandomAccess.get().setReal(Double.NaN);
            eIDRandomAccess.get().set(-1);
        } else {
            efRandomAccess.get().set(computeEllipsoidFactor(ellipsoids.get(currentEllipsoidCounter)));
            eIDRandomAccess.get().set(currentEllipsoidCounter);
            assigned = true;
        }
        return assigned;
    }

    private boolean whollyContainedInForeground(Ellipsoid e, List<Vector3d> sphereSamplingDirections) {
        if(!isInBounds(vectorToPixelGrid(e.getCentroid())))
        {
            return false;
        }

        List<Vector3d> axisSamplingDirections = new ArrayList<>();
        Matrix3d ellipsoidOrientation = new Matrix3d();
        e.getOrientation().getRotationScale(ellipsoidOrientation);
        axisSamplingDirections.add(new Vector3d(ellipsoidOrientation.getM00(),ellipsoidOrientation.getM01(),ellipsoidOrientation.getM02()));
        axisSamplingDirections.add(new Vector3d(-ellipsoidOrientation.getM00(),-ellipsoidOrientation.getM01(),-ellipsoidOrientation.getM02()));

        axisSamplingDirections.add(new Vector3d(ellipsoidOrientation.getM10(),ellipsoidOrientation.getM11(),ellipsoidOrientation.getM12()));
        axisSamplingDirections.add(new Vector3d(-ellipsoidOrientation.getM10(),-ellipsoidOrientation.getM11(),-ellipsoidOrientation.getM12()));

        axisSamplingDirections.add(new Vector3d(ellipsoidOrientation.getM20(),ellipsoidOrientation.getM21(),ellipsoidOrientation.getM22()));
        axisSamplingDirections.add(new Vector3d(-ellipsoidOrientation.getM20(),-ellipsoidOrientation.getM21(),-ellipsoidOrientation.getM22()));

        boolean ellipsoidsExtentsInForeground = !axisSamplingDirections.stream().anyMatch(dir -> ellipsoidIntersectionIsBackground(e,dir));
        return ellipsoidsExtentsInForeground && !sphereSamplingDirections.stream().anyMatch(dir -> ellipsoidIntersectionIsBackground(e,dir));
    }

    private boolean ellipsoidIntersectionIsBackground(Ellipsoid e, Vector3d dir) {
        double axisReduction = 1.0;
        final Matrix3d Q = new Matrix3d();
        e.getOrientation().getRotationScale(Q);
        final Matrix3d lambda = new Matrix3d();
        double a = e.getA()-axisReduction;
        double b = e.getB()-axisReduction;
        double c = e.getC()-axisReduction;
        lambda.setM00(1.0/(a*a));
        lambda.setM11(1.0/(b*b));
        lambda.setM22(1.0/(c*c));
        lambda.mul(lambda, Q);
        lambda.mulTransposeLeft(Q, lambda);
        Vector3d ATimesDir = new Vector3d();
        lambda.transform(dir,ATimesDir);
        double surfaceIntersectionParameter = Math.sqrt(1.0/dir.dot(ATimesDir));
        Vector3d intersectionPoint = new Vector3d(dir);
        intersectionPoint.scaleAdd(surfaceIntersectionParameter,e.getCentroid());
        final long[] pixel = vectorToPixelGrid(intersectionPoint);
        if (isInBounds(pixel)) {
            final RandomAccess<IntegerType> inputRA = inputImage.getImg().randomAccess();
            inputRA.setPosition(pixel);
            return inputRA.get().getInteger() == 0;
        }
        else {
            return false;
        }
    }

    private static float computeEllipsoidFactor(final Ellipsoid ellipsoid) {
        return (float) (ellipsoid.getA() / ellipsoid.getB() - ellipsoid.getB() / ellipsoid.getC());
    }

    static boolean insideEllipsoid(final Vector3d coordinates, final Ellipsoid ellipsoid) {
        Vector3d x = new Vector3d(coordinates);
        Vector3d centroid = ellipsoid.getCentroid();
        x.sub(centroid);

        if(x.length()>ellipsoid.getC()) return false;

        Matrix3d orientation = new Matrix3d();
        ellipsoid.getOrientation().getRotationScale(orientation);

        Matrix3d eigenMatrix = new Matrix3d();
        eigenMatrix.setM00(1.0 / (ellipsoid.getA() * ellipsoid.getA()));
        eigenMatrix.setM11(1.0 / (ellipsoid.getB() * ellipsoid.getB()));
        eigenMatrix.setM22(1.0 / (ellipsoid.getC() * ellipsoid.getC()));

        eigenMatrix.mul(eigenMatrix, orientation);
        eigenMatrix.mulTransposeLeft(orientation, eigenMatrix);

        Vector3d Ax = new Vector3d();
        eigenMatrix.transform(x, Ax);

        return x.dot(Ax) < 1;
    }

    private List<Ellipsoid> getLocalEllipsoids(final List<ValuePair<Vector3d, Vector3d>> seedPoints, final List<Vector3d> boundaryPoints) {
        return seedPoints.stream().map(s -> findLocalEllipsoidOp.calculate(new ArrayList<Vector3d>(boundaryPoints), s)).flatMap(Collection::stream).collect(toList());
    }

    private static double distanceBetweenEdgels(Edgel e1, Edgel e2) {
        Vector3d distance = new Vector3d(e1.getDoublePosition(0), e1.getDoublePosition(1), e1.getDoublePosition(2));
        distance.sub(new Vector3d(e2.getDoublePosition(0), e2.getDoublePosition(1), e2.getDoublePosition(2)));
        return distance.length();
    }

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
     * </p>
     */
    static List<Vector3d> getGeneralizedSpiralSetOnSphere(int n) {
        List<Vector3d> spiralSet = new ArrayList<>();

        List<Double> phi = new ArrayList<>();
        phi.add(0.0);
        for (int k = 1; k < n - 1; k++) {
            double h = -1.0 + 2.0 * ((double) k) / (n - 1);
            phi.add(getPhiByRecursion(n, phi.get(k - 1), h));
        }
        phi.add(0.0);

        for (int k = 0; k < n; k++) {
            double h = -1.0 + 2.0 * ((double) k) / (n - 1);
            double theta = Math.acos(h);
            spiralSet.add(new Vector3d(Math.sin(theta) * Math.cos(phi.get(k)), Math
                    .sin(theta) * Math.sin(phi.get(k)), Math.cos(theta)));

        }

        return spiralSet;
    }

    private static double getPhiByRecursion(double n, double phiKMinus1,
                                            double hk) {
        double phiK = phiKMinus1 + 3.6 / Math.sqrt(n) * 1.0 / Math.sqrt(1 - hk *
                hk);
        // modulo 2pi calculation works for positive numbers only, which is not a
        // problem in this case.
        return phiK - Math.floor(phiK / (2 * Math.PI)) * 2 * Math.PI;
    }

    private static int estimateNSpiralPointsRequired(double searchRadius,
                                                     double pixelWidth) {
        return (int) Math.ceil(Math.pow(searchRadius * 3.809 / pixelWidth, 2));
    }

    Vector3d findFirstPointInBGAlongRay(final Vector3d rayIncrement,
                                        final Vector3d start) {
        RandomAccess<IntegerType> randomAccess = inputImage.randomAccess();

        Vector3d currentRealPosition = new Vector3d(start);
        long[] currentPixelPosition = vectorToPixelGrid(start);
        randomAccess.setPosition(currentPixelPosition);

        while (randomAccess.get().getInteger() > 0) {
            currentRealPosition.add(rayIncrement);
            currentPixelPosition = vectorToPixelGrid(currentRealPosition);
            if (!isInBounds(currentPixelPosition)) break;
            randomAccess.setPosition(currentPixelPosition);
        }
        return currentRealPosition;
    }


    private boolean isInBounds(long[] currentPixelPosition) {
        long width = inputImage.dimension(0);
        long height = inputImage.dimension(1);
        long depth = inputImage.dimension(2);
        return !(currentPixelPosition[0] < 0 || currentPixelPosition[0] >= width ||
                currentPixelPosition[1] < 0 || currentPixelPosition[1] >= height ||
                currentPixelPosition[2] < 0 || currentPixelPosition[2] >= depth);
    }

    private long[] vectorToPixelGrid(Vector3d currentPosition) {
        return Stream.of(currentPosition.getX(), currentPosition.getY(),
                currentPosition.getZ()).mapToLong(x -> (long) x.doubleValue()).toArray();
    }
}
