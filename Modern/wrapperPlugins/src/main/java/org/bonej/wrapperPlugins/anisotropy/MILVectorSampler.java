package org.bonej.wrapperPlugins.anisotropy;

import net.imagej.ops.OpEnvironment;
import net.imagej.ops.OpService;
import net.imagej.ops.linalg.rotate.Rotate3d;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imagej.ops.special.hybrid.BinaryHybridCFI1;
import net.imagej.ops.special.hybrid.Hybrids;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.logic.BitType;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.UnitSphereRandomVectorGenerator;
import org.bonej.ops.mil.ParallelLineGenerator;
import org.bonej.ops.mil.ParallelLineMIL;
import org.bonej.ops.mil.PlaneParallelLineGenerator;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.generate;
import static org.bonej.ops.mil.PlaneParallelLineGenerator.createFromInterval;

public class MILVectorSampler {
    static final double MINIMUM_SAMPLING_DISTANCE = Math.sqrt(3.0);
    // Default directions is 2_000 since that's roughly the number of points in
    // Poisson distributed sampling that'd give points about 5 degrees apart).
    static final int DEFAULT_DIRECTIONS = 2_000;
    // The default number of lines was found to be sensible after experimenting
    // with data at hand. Other data may need a different number.
    static final int DEFAULT_LINES = 10_000;
    private final long planeSections;
    private final double linesPerDirection;
    private final BinaryHybridCFI1<Vector3d, Quaterniondc, Vector3d> rotateOp;
    private final OpEnvironment opService;
    private Long seed;
    private UnitSphereRandomVectorGenerator rotationGenerator;
    private final double samplingPointDistance;
    private BinaryFunctionOp<RandomAccessibleInterval<BitType>, ParallelLineGenerator, Vector3dc>
            milOp;
    private final long samplingDirections;
    private double mILVectorBaseLength;
    private ProgressObserver observer;
    private long directionsSampled;
    private final ExecutorService executorService;

    MILVectorSampler(final OpService opService, final long samplingDirections,
                     final long linesPerDirection) {
        this(opService, samplingDirections, linesPerDirection, MINIMUM_SAMPLING_DISTANCE);
    }

    MILVectorSampler(final OpService opService, final long samplingDirections,
                     final long linesPerDirection, final double samplingPointDistance) {
        this.opService = opService;
        this.samplingDirections = samplingDirections;
        this.linesPerDirection = linesPerDirection;
        this.samplingPointDistance = samplingPointDistance;
        planeSections = (long) Math.sqrt(linesPerDirection);
        rotationGenerator = new UnitSphereRandomVectorGenerator(4);
        rotateOp = Hybrids.binaryCFI1(opService, Rotate3d.class, Vector3d.class,
                new Vector3d(), new Quaterniond());
        executorService = createExecutorService();
    }

    private ExecutorService createExecutorService() {
        final int cores = Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(cores);
    }

    void setObserver(final ProgressObserver observer) { this.observer = observer; }

    List<Vector3dc> sample(final RandomAccessibleInterval<BitType> image)
            throws ExecutionException, InterruptedException {
        initialiseFor(image);
        final List<Callable<Vector3dc>> tasks = createMILTasks(image);
        final List<Future<Vector3dc>> futures = executorService.invokeAll(tasks);
        return finishAll(futures);
    }

    private void initialiseFor(final RandomAccessibleInterval<BitType> image) {
        directionsSampled = 0;
        matchMILOp(image);
    }

    private void matchMILOp(final RandomAccessibleInterval<BitType> image) {
        final double baseLength = calculateMILVectorBaseLength(image);
        if (milOp == null || baseLength != mILVectorBaseLength) {
            // Op matching requires any non-null generator
            final ParallelLineGenerator generator = createLineGenerator(image, new Quaterniond());
            milOp = Functions.binary(opService, ParallelLineMIL.class, Vector3dc.class,
                    image, generator, baseLength, samplingPointDistance);
        }
        mILVectorBaseLength = baseLength;
    }

    private double calculateMILVectorBaseLength(final RandomAccessibleInterval<BitType> image) {
        final double diagonal = ParallelLineMIL.calculateLongestDiagonal(image);
        return diagonal * linesPerDirection;
    }

    private List<Callable<Vector3dc>> createMILTasks(
            final RandomAccessibleInterval<BitType> image) {
        return generate(() -> createMILTask(image)).limit(samplingDirections).collect(toList());
    }

    private Callable<Vector3dc> createMILTask(final RandomAccessibleInterval<BitType> image) {
        final Quaterniondc rotation = nextRandomRotation();
        final PlaneParallelLineGenerator generator = createLineGenerator(image, rotation);
        return () -> {
            final Vector3dc mILVector = milOp.calculate(image, generator);
            notifyObserver();
            return mILVector;
        };
    }

    private PlaneParallelLineGenerator createLineGenerator(
            final RandomAccessibleInterval<BitType> image, final Quaterniondc rotation) {
        final PlaneParallelLineGenerator generator =
                createFromInterval(image, rotation, rotateOp, planeSections);
        if (seed != null) { generator.setSeed(seed); }
        return generator;
    }

    private Quaterniondc nextRandomRotation() {
        final double[] v = rotationGenerator.nextVector();
        return new Quaterniond(v[0], v[1], v[2], v[3]);
    }

    private synchronized void notifyObserver() {
        if (observer != null) {
            directionsSampled++;
            observer.updateProgress((int) directionsSampled, (int) samplingDirections);
        }
    }

    private static List<Vector3dc> finishAll(final List<Future<Vector3dc>> futures)
            throws ExecutionException, InterruptedException {
        final List<Vector3dc> mILVectors = new ArrayList<>();
        for (final Future<Vector3dc> future : futures) {
            mILVectors.add(future.get());
        }
        return mILVectors;
    }

    void setSeed(final long seed) {
        this.seed = seed;
        ParallelLineMIL.setSeed(seed);
        rotationGenerator = new UnitSphereRandomVectorGenerator(4, new MersenneTwister(seed));
    }
}
