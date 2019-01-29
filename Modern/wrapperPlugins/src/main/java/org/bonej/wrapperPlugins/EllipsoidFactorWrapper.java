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
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.bonej.ops.ellipsoid.Ellipsoid;
import org.bonej.ops.ellipsoid.FindEllipsoidFromBoundaryPoints;
import org.bonej.ops.skeletonize.FindRidgePoints;
import org.bonej.utilities.AxisUtils;
import org.bonej.utilities.ElementUtil;
import org.bonej.utilities.SharedTable;
import org.bonej.utilities.VectorUtil;
import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static net.imglib2.roi.Regions.countTrue;
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
 */

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Ellipsoid Factor 2")
public class EllipsoidFactorWrapper<R extends RealType<R> & NativeType<R>> extends ContextCommand {

    // Several ellipsoids may fall in same bin if this is too small a number!
    // This will be ignored!
    private static final long FLINN_PLOT_DIMENSION = 501;
    private static final String NO_ELLIPSOIDS_FOUND = "No ellipsoids were found - try allowing more sampling directions and/or more seedpoints.";
    @SuppressWarnings("unused")
    @Parameter(validater = "validateImage")
    private ImgPlus<R> inputImage;

    @Parameter(persist = false, required = false)
    private DoubleType sigma = new DoubleType(0);

    @Parameter(label = "Maximum internal seeds", min = "0", stepSize = "1",
            description = "Approximate maximum of internal seed points allowed. If more seeds are found, they are filtered with probability 1-Maximum internal seeds/total internal seeds found.",
            style = NumberWidget.SPINNER_STYLE)
    private long approximateMaximumNumberOfSeeds = 20000;

    @Parameter(label = "Sampling directions", min = "0", stepSize = "1",
            description = "Number of directions (evenly spaced on the surface of a sphere) that internal seed points will search for contact points.",
            style = NumberWidget.SPINNER_STYLE)
    private int nSphere = 20;

    @Parameter(persist = false, required = false)
    private DoubleType thresholdForBeingARidgePoint = new DoubleType(0.6);

    @Parameter(persist = false, required = false)
    private boolean showSecondaryImages = false;

    @Parameter(label = "Seed point image", type = ItemIO.OUTPUT)
    private ImgPlus<UnsignedByteType> seedPointsImage;

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
    private UnitService unitService;

    @SuppressWarnings("unused")
    @Parameter
    private LogService logService;

    private boolean calibrationWarned;

    private Random rng;

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
        eIDRandomAccess.setPosition(VectorUtil.toPixelGrid(point));
        final Ellipsoid ellipsoid = candidate.get();
        eIDRandomAccess.get().set(iDs.get(ellipsoid));
    }

    private static float computeEllipsoidFactor(final Ellipsoid ellipsoid) {
        return (float) (ellipsoid.getA() / ellipsoid.getB() - ellipsoid.getB() / ellipsoid.getC());
    }

    @Override
    public void run() {
        statusService.showStatus("Ellipsoid Factor: initialising...");
        rng = new Random(23);
        final ImgPlus<BitType> bitImage = Common.toBitTypeImgPlus(opService, inputImage);
        final List<Vector3dc> internalSeedPoints = (List<Vector3dc>) ((List) opService.run(FindRidgePoints.class, bitImage)).get(0);

        statusService.showStatus("Ellipsoid Factor: finding ellipsoids...");
        final List<Ellipsoid> ellipsoids = findEllipsoids(bitImage, internalSeedPoints);
        if (ellipsoids.isEmpty()) {
            cancel(NO_ELLIPSOIDS_FOUND);
            return;
        }
        ellipsoids.sort(Comparator.comparingDouble(e -> -e.getVolume()));

        statusService.showStatus("Ellipsoid Factor: assigning EF to foreground voxels...");
        final Img<IntType> ellipsoidIdentityImage = assignEllipsoidIDs(bitImage, ellipsoids);
        createPrimaryOutputImages(ellipsoids, ellipsoidIdentityImage);
        if (showSecondaryImages) createSecondaryOutputImages(ellipsoids, ellipsoidIdentityImage);

        final double numberOfForegroundVoxels = countTrue(bitImage);
        final double numberOfAssignedVoxels = countAssignedVoxels(ellipsoidIdentityImage);
        final double fillingPercentage = 100.0 * (numberOfAssignedVoxels / numberOfForegroundVoxels);
        addResults(ellipsoids, fillingPercentage);

        if (logService.isDebug()) {
            logService.debug("initial sampling directions = " + nSphere);
            logService.debug("threshold for ridge point inclusions = " + thresholdForBeingARidgePoint);
            logService.debug("assigned voxels = " + numberOfAssignedVoxels);
            logService.debug("foreground voxels = " + numberOfForegroundVoxels);
            for (int i = 0; i < Math.min(100, ellipsoids.size()); i++) {
                logService.debug("ellipsoid(" + i + "):\n" + ellipsoids.get(i).toString());
            }
        }
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
        if (sigma.getRealDouble() > 0.0) {
            flinnPeakPlot = (Img<FloatType>) opService.filter().gauss(flinnPeakPlot,
                    sigma.get());
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

    private Img<FloatType> createNaNCopy() {
        final ArrayImg<FloatType, FloatArray> copy = ArrayImgs.floats(inputImage
                .dimension(0), inputImage.dimension(1), inputImage.dimension(2));
        copy.forEach(e -> e.setReal(Float.NaN));
        return copy;
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

    private List<Ellipsoid> findEllipsoids(ImgPlus<BitType> bitImage, final List<Vector3dc> seeds) {
        if (seeds.size() >= approximateMaximumNumberOfSeeds) {
            reduceSeedPoints(seeds);
        }
        createSeedPointImage(seeds);

        return seeds.parallelStream().flatMap(
                seed -> (Stream<Ellipsoid>) opService.run(
                        FindEllipsoidFromBoundaryPoints.class, seed, bitImage.getImg(), nSphere))
                .collect(toList());
    }

    private void createSeedPointImage(List<Vector3dc> seeds) {
        final ArrayImg<IntType, IntArray> seedImg = ArrayImgs.ints(inputImage.dimension(0), inputImage.dimension(1), inputImage.dimension(2));
        final ArrayRandomAccess<IntType> seedRandomAccess = seedImg.randomAccess();
        seeds.forEach(s -> {
            final long[] position = VectorUtil.toPixelGrid(s);
            seedRandomAccess.setPosition(position);
            seedRandomAccess.get().setInt(255);
        });
        seedPointsImage = new ImgPlus(seedImg, "Seed Points");
        seedPointsImage.setChannelMaximum(0,255d);
        seedPointsImage.setChannelMinimum(0,0d);
    }

    private void reduceSeedPoints(final List<Vector3dc> seeds) {
        Set<Vector3dc> seedsToKeep = new HashSet<>();
        while (seedsToKeep.size() < approximateMaximumNumberOfSeeds) {
            seedsToKeep.add(seeds.get(rng.nextInt(seeds.size() - 1)));
        }
        seeds.clear();
        seeds.addAll(seedsToKeep);
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
        if (!AxisUtils.isSpatialCalibrationsIsotropic(inputImage, 0.003,
                unitService) && !calibrationWarned) {
            final Result result = uiService.showDialog(
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

    @SuppressWarnings("unused")
    private void enforceValidRange() {
        if (approximateMaximumNumberOfSeeds > inputImage.dimension(0) * inputImage.dimension(1) * inputImage.dimension(2)) {
            approximateMaximumNumberOfSeeds = inputImage.dimension(0) * inputImage.dimension(1) * inputImage.dimension(3);
        }
    }
    // endregion
}


