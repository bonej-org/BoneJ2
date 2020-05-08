package org.bonej.wrapperPlugins.anisotropy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import net.imagej.ops.OpService;
import net.imagej.ops.stats.regression.leastSquares.Quadric;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.logic.BitType;

import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.QuadricToEllipsoid;
import org.joml.Matrix3dc;
import org.joml.Matrix4dc;
import org.joml.Vector3dc;

class DegreeOfAnisotropy {
    private final MILVectorSampler mILVectorSampler;
    private final OpService opService;

    DegreeOfAnisotropy(final OpService opService, final MILVectorSampler sampler) {
        this.opService = opService;
        mILVectorSampler = sampler;
    }

    Results calculate(final RandomAccessibleInterval<BitType> image)
            throws EllipsoidFittingFailedException, ExecutionException, InterruptedException {
        final List<Vector3dc> mILVectors = mILVectorSampler.sample(image);
        final Ellipsoid ellipsoid = solveBestFittingEllipsoid(mILVectors);
        return writeResults(ellipsoid, mILVectors);
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
