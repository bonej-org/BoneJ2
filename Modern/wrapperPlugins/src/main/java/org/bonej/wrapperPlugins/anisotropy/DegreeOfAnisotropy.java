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
import org.scijava.plugin.Parameter;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.generate;

class DegreeOfAnisotropy {
    final static double MINIMUM_SAMPLING_DISTANCE = Math.sqrt(3.0);
    // Default directions is 2_000 since that's roughly the number of points in
    // Poisson distributed sampling that'd give points about 5 degrees apart).
    static final int DEFAULT_DIRECTIONS = 2_000;
    // The default number of lines was found to be sensible after experimenting
    // with data at hand. Other data may need a different number.
    static final int DEFAULT_LINES = 10_000;
    private final AnisotropyWrapper<?> progressObserver;
    private RandomAccessibleInterval<BitType> interval;
    private double samplingPointDistance = MINIMUM_SAMPLING_DISTANCE;
    private double mILVectorBaseLength;
    private long samplingDirections = DEFAULT_DIRECTIONS;
    private int linesPerDirection = DEFAULT_LINES;
    private long planeSections;
    @Parameter
    private OpService opService;
    private BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;
    private BinaryFunctionOp<RandomAccessibleInterval<BitType>, ParallelLineGenerator, Vector3d>
            milOp;
    private UnitSphereRandomVectorGenerator quaternionGenerator;
    private Ellipsoid ellipsoid;
    private double degreeOfAnisotropy;
    private ExecutorService executor;
    private List<Vector3dc> pointCloud;
    private Long seed;

    DegreeOfAnisotropy(final AnisotropyWrapper<?> wrapper) {
        wrapper.context().inject(this);
        progressObserver = wrapper;
    }

    double getDegreeOfAnisotropy() { return degreeOfAnisotropy; }

    Matrix3dc getEigenMatrix() { return ellipsoid.getEigenMatrix(); }

    double[] getEigenValues() { return ellipsoid.getEigenValues(); }

    List<Vector3dc> getMILVectors() { return pointCloud; }

    double[] getRadii() { return ellipsoid.getRadii(); }

    void setSamplingPointDistance(final double distance) { samplingPointDistance = distance; }

    void setSamplingDirections(final long directions) { samplingDirections = directions; }

    void setLinesPerDirection(final int lines) { linesPerDirection = lines; }

    void setSeed(final long seed) {
        this.seed = seed;
        ParallelLineMIL.setSeed(seed);
    }

    void calculate(final RandomAccessibleInterval<BitType> interval)
            throws EllipsoidFittingFailedException, ExecutionException, InterruptedException {
        this.interval = interval;
        initialise();
        drawMILVectors();
        solveBestFittingEllipsoid();
        calculateDegreeOfAnisotropy();
    }

    private void initialise() {
        calculateMILVectorBaseLength();
        planeSections = (long) Math.sqrt(linesPerDirection);
        createQuaternionGenerator();
        matchOps();
        createExecutorService();
    }

    private void calculateMILVectorBaseLength() {
        final double diagonal = ParallelLineMIL.calculateLongestDiagonal(interval);
        mILVectorBaseLength = diagonal * linesPerDirection;
    }

    private void createQuaternionGenerator() {
        if (seed == null) {
            quaternionGenerator = new UnitSphereRandomVectorGenerator(4);
        } else {
            quaternionGenerator = new UnitSphereRandomVectorGenerator(4,
                    new MersenneTwister(seed));
        }
    }

    private void matchOps() {
        rotateOp = Hybrids.binaryCFI1(opService, Rotate3d.class, Vector3d.class,
                new Vector3d(), new Quaterniond());
        // Op matching requires any non-null ParallelLineGenerator
        final ParallelLineGenerator generator = createLineGenerator();
        milOp = Functions.binary(opService, ParallelLineMIL.class, Vector3d.class,
                interval, generator, mILVectorBaseLength, samplingPointDistance);
    }

    private void createExecutorService() {
        final int cores = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(cores);
    }

    private void drawMILVectors() throws ExecutionException, InterruptedException {
        final List<Callable<Vector3dc>> tasks = createMILTasks();
        final List<Future<Vector3dc>> futures = executor.invokeAll(tasks);
        pointCloud = finishAll(futures);
    }

    private List<Callable<Vector3dc>> createMILTasks() {
        return generate(this::createMILTask).limit(samplingDirections).collect(toList());
    }

    private Callable<Vector3dc> createMILTask() {
        final PlaneParallelLineGenerator generator = createLineGenerator();
        return () -> {
            final Vector3dc mILVector = milOp.calculate(interval, generator);
            progressObserver.directionFinished();
            return mILVector;
        };
    }

    private PlaneParallelLineGenerator createLineGenerator() {
        final Quaterniondc rotation = nextRandomRotation();
        final PlaneParallelLineGenerator generator =
                new PlaneParallelLineGenerator(interval, rotation, rotateOp, planeSections);
        if (seed != null) { generator.setSeed(seed); }
        return generator;
    }

    private Quaterniondc nextRandomRotation() {
        final double[] v = quaternionGenerator.nextVector();
        return new Quaterniond(v[0], v[1], v[2], v[3]);
    }

    private static List<Vector3dc> finishAll(final List<Future<Vector3dc>> futures)
            throws ExecutionException, InterruptedException {
        final List<Vector3dc> mILVectors = new ArrayList<>();
        for (final Future<Vector3dc> future : futures) {
            mILVectors.add(future.get());
        }
        return mILVectors;
    }

    private void solveBestFittingEllipsoid() throws EllipsoidFittingFailedException {
        final Matrix4dc quadric = (Matrix4dc) opService.run(Quadric.class, pointCloud);
        final Optional<?> solution = (Optional<?>) opService.run(QuadricToEllipsoid.class, quadric);
        if (!solution.isPresent()) {
            throw new EllipsoidFittingFailedException();
        }
        ellipsoid = (Ellipsoid) solution.get();
    }

    private void calculateDegreeOfAnisotropy() {
        final double cSq = ellipsoid.getC() * ellipsoid.getC();
        final double aSq = ellipsoid.getA() * ellipsoid.getA();
        degreeOfAnisotropy = 1.0 - (1.0 / cSq) / (1.0 / aSq);
    }
}
