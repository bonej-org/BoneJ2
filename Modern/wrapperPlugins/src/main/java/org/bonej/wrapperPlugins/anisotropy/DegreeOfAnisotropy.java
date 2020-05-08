package org.bonej.wrapperPlugins.anisotropy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.imagej.ops.OpService;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imagej.ops.stats.regression.leastSquares.Quadric;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.logic.BitType;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.QuadricToEllipsoid;
import org.bonej.ops.mil.ParallelLineGenerator;
import org.bonej.ops.mil.ParallelLineMIL;
import org.bonej.ops.mil.PlaneParallelLineGenerator;
import org.joml.Matrix3dc;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.Context;
import org.scijava.plugin.Parameter;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.generate;
import static org.bonej.ops.mil.PlaneParallelLineGenerator.createFromInterval;

class DegreeOfAnisotropy {
    final static double MINIMUM_SAMPLING_DISTANCE = Math.sqrt(3.0);
    // Default directions is 2_000 since that's roughly the number of points in
    // Poisson distributed sampling that'd give points about 5 degrees apart).
    static final int DEFAULT_DIRECTIONS = 2_000;
    // The default number of lines was found to be sensible after experimenting
    // with data at hand. Other data may need a different number.
    static final int DEFAULT_LINES = 10_000;
    private AnisotropyWrapper<?> progressObserver;
    private double samplingPointDistance = MINIMUM_SAMPLING_DISTANCE;
    private long samplingDirections = DEFAULT_DIRECTIONS;
    private int linesPerDirection = DEFAULT_LINES;
    private long planeSections;
    @Parameter
    private OpService opService;
    private BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;
    private BinaryFunctionOp<RandomAccessibleInterval<BitType>, ParallelLineGenerator, Vector3d>
            milOp;
    private UnitSphereRandomVectorGenerator rotationGenerator;
    private Long seed;

    DegreeOfAnisotropy(final Context context) { context.inject(this); }

    void setProgressObserver(final AnisotropyWrapper<?> observer) { progressObserver = observer; }

    void setSamplingPointDistance(final double distance) { samplingPointDistance = distance; }

    void setSamplingDirections(final long directions) { samplingDirections = directions; }

    void setLinesPerDirection(final int lines) { linesPerDirection = lines; }

    void setSeed(final long seed) {
        this.seed = seed;
        ParallelLineMIL.setSeed(seed);
    }

    Results calculate(final RandomAccessibleInterval<BitType> image)
            throws EllipsoidFittingFailedException, ExecutionException, InterruptedException {
        initialise(image);
        final List<Vector3dc> mILVectors = drawMILVectors(image);
        final Ellipsoid ellipsoid = solveBestFittingEllipsoid(mILVectors);
        return writeResults(ellipsoid, mILVectors);
    }

    private void initialise(final RandomAccessibleInterval<BitType> image) {
        planeSections = (long) Math.sqrt(linesPerDirection);
        createDirectionGenerator();
        rotateOp = Hybrids.binaryCFI1(opService, Rotate3d.class, Vector3d.class,
                new Vector3d(), new Quaterniond());
        initialiseMILOp(image);
    }

    private void createDirectionGenerator() {
        if (seed == null) {
            rotationGenerator = new UnitSphereRandomVectorGenerator(4);
        } else {
            rotationGenerator = new UnitSphereRandomVectorGenerator(4,
                    new MersenneTwister(seed));
        }
    }

    private void initialiseMILOp(final RandomAccessibleInterval<BitType> image) {
        final ParallelLineGenerator generator = createLineGenerator(image);
        final double baseLength = calculateMILVectorBaseLength(image);
        // Op matching requires a non-null generator
        milOp = Functions.binary(opService, ParallelLineMIL.class, Vector3d.class,
                image, generator, baseLength, samplingPointDistance);
    }

    private double calculateMILVectorBaseLength(final RandomAccessibleInterval<BitType> image) {
        final double diagonal = ParallelLineMIL.calculateLongestDiagonal(image);
        return diagonal * linesPerDirection;
    }

    private List<Vector3dc> drawMILVectors(final RandomAccessibleInterval<BitType> image)
            throws ExecutionException, InterruptedException {
        final List<Callable<Vector3dc>> tasks = createMILTasks(image);
        final List<Future<Vector3dc>> futures = submit(tasks);
        return finishAll(futures);
    }

    private List<Callable<Vector3dc>> createMILTasks(
            final RandomAccessibleInterval<BitType> image) {
        return generate(() -> createMILTask(image)).limit(samplingDirections).collect(toList());
    }

    private Callable<Vector3dc> createMILTask(final RandomAccessibleInterval<BitType> image) {
        final PlaneParallelLineGenerator generator = createLineGenerator(image);
        return () -> {
            final Vector3dc mILVector = milOp.calculate(image, generator);
            updateProgress();
            return mILVector;
        };
    }

    private void updateProgress() {
        if (progressObserver != null) {
            progressObserver.directionFinished();
        }
    }

    private PlaneParallelLineGenerator createLineGenerator(
            final RandomAccessibleInterval<BitType> image) {
        final Quaterniondc rotation = nextRandomRotation();
        final PlaneParallelLineGenerator generator =
                createFromInterval(image, rotation, rotateOp, planeSections);
        if (seed != null) { generator.setSeed(seed); }
        return generator;
    }

    private Quaterniondc nextRandomRotation() {
        final double[] v = rotationGenerator.nextVector();
        return new Quaterniond(v[0], v[1], v[2], v[3]);
    }


    private List<Future<Vector3dc>> submit(final List<Callable<Vector3dc>> tasks)
            throws InterruptedException {
        final ExecutorService executor = createExecutorService();
        return executor.invokeAll(tasks);
    }

    private ExecutorService createExecutorService() {
        final int cores = Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(cores);
    }

    private static List<Vector3dc> finishAll(final List<Future<Vector3dc>> futures)
            throws ExecutionException, InterruptedException {
        final List<Vector3dc> mILVectors = new ArrayList<>();
        for (final Future<Vector3dc> future : futures) {
            mILVectors.add(future.get());
        }
        return mILVectors;
    }

    private Ellipsoid solveBestFittingEllipsoid(final List<Vector3dc> mILVectors)
            throws EllipsoidFittingFailedException {
        final Matrix4dc quadric = (Matrix4dc) opService.run(Quadric.class, mILVectors);
        final Optional<?> solution = (Optional<?>) opService.run(QuadricToEllipsoid.class, quadric);
        if (!solution.isPresent()) {
            throw new EllipsoidFittingFailedException();
        }
        return (Ellipsoid) solution.get();
    }

    private Results writeResults(final Ellipsoid ellipsoid, final List<Vector3dc> mILVectors) {
        final double dA = calculateDegreeOfAnisotropy(ellipsoid);
        final double[] radii = ellipsoid.getRadii();
        final Matrix3dc eigenMatrix = ellipsoid.getEigenMatrix();
        final double[] eigenValues = ellipsoid.getEigenValues();
        return new Results(dA, radii, eigenMatrix, eigenValues, mILVectors);
    }

    private double calculateDegreeOfAnisotropy(final Ellipsoid ellipsoid) {
        final double cSq = ellipsoid.getC() * ellipsoid.getC();
        final double aSq = ellipsoid.getA() * ellipsoid.getA();
        return 1.0 - (1.0 / cSq) / (1.0 / aSq);
    }

    static class Results {
        final double degreeOfAnisotropy;
        final double[] radii;
        final Matrix3dc eigenVectors;
        final double[] eigenValues;
        final List<Vector3dc> mILVectors;

        Results(final double degreeOfAnisotropy, final double[] radii, final Matrix3dc eigenVectors,
                final double[] eigenValues, final List<Vector3dc> mILVectors) {
            this.degreeOfAnisotropy = degreeOfAnisotropy;
            this.radii = radii;
            this.eigenVectors = eigenVectors;
            this.eigenValues = eigenValues;
            this.mILVectors = mILVectors;
        }
    }
}
