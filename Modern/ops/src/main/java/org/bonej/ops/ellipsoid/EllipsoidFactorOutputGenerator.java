/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.bonej.ops.ellipsoid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ImgPlus;
import net.imagej.display.ColorTables;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;

@Plugin(name = "Generate Ellipsoid Factor Output", type = Op.class)
public class EllipsoidFactorOutputGenerator extends
        AbstractBinaryFunctionOp<IterableInterval<IntType>, List<QuickEllipsoid>, List<ImgPlus>>{
    // Several ellipsoids may fall in same bin if this is too small a number!
    // This will be ignored!
    private static final long FLINN_PLOT_DIMENSION = 501;

    @Parameter(required = false)
    boolean showFlinnPlots = false;

    @Parameter(required = false)
    boolean showSecondaryImages = false;

    @Parameter(required = false)
    String inputName = "";

    private List<ImgPlus> eFOutputs;

    @Override
    public List<ImgPlus> calculate(IterableInterval<IntType> idImage, List<QuickEllipsoid> ellipsoids) {
        eFOutputs = new ArrayList<>();
        calculatePrimaryOutputs(idImage, ellipsoids);

        //volume image must always be calculated
        eFOutputs.add(createVolumeImage(ellipsoids, idImage));
        if(showFlinnPlots || showSecondaryImages)
        {
            calculateSecondaryOutputs(idImage, ellipsoids);
        }

        if(idImage.dimension(2)>1)
        {
            postProcessWeightedAveraging(idImage);
        }

        //divide EF-image by volume image (which now contains sum of volumes for each ID)
        final Cursor<? extends RealType> eFCursor = eFOutputs.get(0).cursor();
        final Cursor<? extends RealType> vCursor = eFOutputs.get(1).cursor();
        while(eFCursor.hasNext())
        {
            eFCursor.fwd();
            vCursor.fwd();

            eFCursor.get().setReal(eFCursor.get().getRealDouble()/vCursor.get().getRealDouble());
        }

        //remove volume image if we don't want it
        if(!showSecondaryImages)
        {
            eFOutputs.remove(1);
        }

        return eFOutputs;
    }

    private void postProcessWeightedAveraging(IterableInterval<IntType> idImage) {
        Img<IntType> validCounts = ArrayImgs.ints(idImage.dimension(0), idImage.dimension(1), idImage.dimension(3));
        RandomAccess<IntType> validRandomAccess = validCounts.randomAccess();
        final Cursor<IntType> cursor = idImage.localizingCursor();

        while(cursor.hasNext())
        {
            cursor.fwd();
            if(cursor.get().getInteger()>=0) {
                long[] position = new long[4];
                cursor.localize(position);
                validRandomAccess.setPosition(new long[]{position[0],position[1],position[3]});
                validRandomAccess.get().inc();
            }
        }

        eFOutputs.forEach(o -> {
            if(o.numDimensions()==3) {
                divideByValidCounts(o, validRandomAccess);
            }
        });
    }

    private <T extends RealType<T>> void divideByValidCounts(ImgPlus o, RandomAccess<IntType> validRandomAccess) {
        final Cursor<T> cursor = o.localizingCursor();
        while(cursor.hasNext())
        {
            cursor.fwd();
            long[] position = new long[3];
            cursor.localize(position);

            validRandomAccess.setPosition(position);
            int denominator = validRandomAccess.get().getInteger();
            if(denominator>0) cursor.get().setReal(cursor.get().getRealDouble()/denominator);
        }

     }


    private void calculatePrimaryOutputs(IterableInterval idImage, List<QuickEllipsoid> ellipsoids) {
        eFOutputs.add(createEFImage(ellipsoids, idImage));
    }


    private void calculateSecondaryOutputs(IterableInterval idImage, List<QuickEllipsoid> ellipsoids) {

        //radii
        final double[] as = ellipsoids.parallelStream().mapToDouble(e -> e.getSortedRadii()[0]).toArray();
        final double[] bs = ellipsoids.parallelStream().mapToDouble(e -> e.getSortedRadii()[1]).toArray();
        final double[] cs = ellipsoids.parallelStream().mapToDouble(e -> e.getSortedRadii()[2]).toArray();

        //radii ratios
        final double[] aBRatios = ellipsoids.parallelStream()
                .mapToDouble(e -> e.getSortedRadii()[0] / e.getSortedRadii()[1]).toArray();
        final double[] bCRatios = ellipsoids.parallelStream()
                .mapToDouble(e -> e.getSortedRadii()[1] / e.getSortedRadii()[2]).toArray();

        if (showFlinnPlots) {
            eFOutputs.add(createFlinnPlotImage(aBRatios, bCRatios));
            eFOutputs.add(createFlinnPeakPlot(aBRatios, bCRatios, idImage));
        }

        if (showSecondaryImages) {
            eFOutputs.add(createIDImage(idImage, ellipsoids));

            //add to output list
            eFOutputs.add(createRadiusImage(as, idImage, inputName+"_a"));
            eFOutputs.add(createRadiusImage(bs, idImage, inputName+"_b"));
            eFOutputs.add(createRadiusImage(cs, idImage, inputName+"_c"));

            eFOutputs.add(createAxisRatioImage(aBRatios, idImage, inputName+"_a/b"));
            eFOutputs.add(createAxisRatioImage(bCRatios, idImage, inputName+"_b/c"));
        }
    }

    //region: create outputs
    private ImgPlus createIDImage(IterableInterval idImage, List<QuickEllipsoid> ellipsoids) {
        final ArrayImg<IntType, IntArray> ints = ArrayImgs.ints(idImage.dimension(0), idImage.dimension(1), idImage.dimension(2),idImage.dimension(3));
        final Cursor<IntType> cursor = idImage.localizingCursor();
        final Cursor<IntType> cursor1 = ints.localizingCursor();
        while(cursor.hasNext())
        {
            cursor.fwd();
            cursor1.fwd();
            final long[] position = new long[4];
            cursor.localize(position);
            final int integer = cursor.get().getInteger();
            cursor1.get().set(integer);
        }
        ImgPlus eIdImage = new ImgPlus<>(ints,inputName+"_ID");
        eIdImage.setChannelMaximum(0, ellipsoids.size() / 10.0f);
        eIdImage.setChannelMinimum(0, -1.0f);
        return eIdImage;
    }

    private ImgPlus<FloatType> createEFImage(final Collection<QuickEllipsoid> ellipsoids,
                                             final IterableInterval idImage) {
        final Img<FloatType> ellipsoidFactorImage = createNaNImg(idImage);
        final double[] ellipsoidFactors = ellipsoids.parallelStream()
                .mapToDouble(EllipsoidFactorOutputGenerator::computeWeightedEllipsoidFactor).toArray();
        mapValuesToImage(ellipsoidFactors, idImage, ellipsoidFactorImage);
        final ImgPlus<FloatType> efImage = new ImgPlus<>(ellipsoidFactorImage, inputName+"_EF");
        efImage.setChannelMaximum(0,1);
        efImage.setChannelMinimum(0, -1);
        efImage.initializeColorTables(1);
        efImage.setColorTable(ColorTables.FIRE,0);
        return efImage;
    }

    private ImgPlus createRadiusImage(double[] radii, IterableInterval idImage, String name) {
        final Img<FloatType> aImg = createNaNImg(idImage);
        mapValuesToImage(radii, idImage, aImg);
        Arrays.sort(radii);
        ImgPlus radiusImage = new ImgPlus(aImg,name);
        radiusImage.setChannelMaximum(0, radii[radii.length-1]);
        radiusImage.setChannelMinimum(0, 0.0f);
        return radiusImage;
    }

    private ImgPlus createAxisRatioImage(final double[] ratios, final IterableInterval idImage, String name) {
        final Img<FloatType> axisRatioImage = createNaNImg(idImage);
        mapValuesToImage(ratios, idImage, axisRatioImage);
        ImgPlus aToBAxisRatioImage = new ImgPlus(axisRatioImage,name);
        aToBAxisRatioImage.setChannelMaximum(0, 1.0f);
        aToBAxisRatioImage.setChannelMinimum(0, 0.0f);
        return aToBAxisRatioImage;
    }

    private ImgPlus createVolumeImage(final List<QuickEllipsoid> ellipsoids,
                                   final IterableInterval idImage) {
        final Img<FloatType> volumeImage = createNaNImg(idImage);
        final double[] volumes = ellipsoids.parallelStream().mapToDouble(QuickEllipsoid::getVolume).toArray();
        mapValuesToImage(volumes, idImage, volumeImage);
        ImgPlus vImage = new ImgPlus(volumeImage,inputName+"_volume");
        vImage.setChannelMaximum(0, ellipsoids.get(0).getVolume());
        vImage.setChannelMinimum(0, -1.0f);
        return vImage;
    }

    private ImgPlus createFlinnPeakPlot(final double[] aBRatios, final double[] bCRatios,
                                        final IterableInterval ellipsoidIDs) {
        Img<FloatType> flinnPeakPlot = ArrayImgs.floats(FLINN_PLOT_DIMENSION, FLINN_PLOT_DIMENSION);
        final RandomAccess<FloatType> flinnPeakPlotRA = flinnPeakPlot.randomAccess();
        final Cursor<IntType> idCursor = ellipsoidIDs.localizingCursor();
        final long[] position = new long[4];
        while (idCursor.hasNext()) {
            idCursor.fwd();
            if (idCursor.get().getInteger() < 0) {
                continue;
            }
            idCursor.localize(position);
            if(position[2]>0) break;

            final int localMaxEllipsoidID = idCursor.get().getInteger();
            final long x = Math.round(bCRatios[localMaxEllipsoidID] * (FLINN_PLOT_DIMENSION - 1));
            final long y = Math.round(aBRatios[localMaxEllipsoidID] * (FLINN_PLOT_DIMENSION - 1));
            flinnPeakPlotRA.setPosition(new long[]{x, FLINN_PLOT_DIMENSION - y - 1});
            final float currentValue = flinnPeakPlotRA.get().getRealFloat();
            flinnPeakPlotRA.get().set(currentValue + 1.0f);
        }

        ImgPlus flinnPeakPlotImage = new ImgPlus<>(flinnPeakPlot, inputName+"_Flinn_peak_plot");

        flinnPeakPlotImage.setChannelMaximum(0, 255.0f);
        flinnPeakPlotImage.setChannelMinimum(0, 0.0f);
        DefaultLinearAxis xFlinnAxis = new DefaultLinearAxis(Axes.get("b/c",true),1.0/FLINN_PLOT_DIMENSION);
        xFlinnAxis.setUnit("");
        DefaultLinearAxis yFlinnAxis = new DefaultLinearAxis(Axes.get("a/b",true),-1.0/FLINN_PLOT_DIMENSION);
        yFlinnAxis.setOrigin(FLINN_PLOT_DIMENSION);
        yFlinnAxis.setUnit("");

        flinnPeakPlotImage.setAxis(xFlinnAxis,0);
        flinnPeakPlotImage.setAxis(yFlinnAxis,1);

        return flinnPeakPlotImage;
    }

    private ImgPlus createFlinnPlotImage(final double[] aBRatios, final double[] bCRatios) {
        final Img<BitType> flinnPlot = ArrayImgs.bits(FLINN_PLOT_DIMENSION, FLINN_PLOT_DIMENSION);
        final RandomAccess<BitType> flinnRA = flinnPlot.randomAccess();
        for (int i = 0; i < aBRatios.length; i++) {
            final long x = Math.round(bCRatios[i] * (FLINN_PLOT_DIMENSION - 1));
            final long y = FLINN_PLOT_DIMENSION - Math.round(aBRatios[i] * (FLINN_PLOT_DIMENSION - 1)) - 1;
            flinnRA.setPosition(x, 0);
            flinnRA.setPosition(y, 1);
            flinnRA.get().setOne();
        }
        ImgPlus flinnPlotImage = new ImgPlus<>(flinnPlot, inputName+"_unweighted_Flinn_plot");
        flinnPlotImage.setChannelMaximum(0, 255.0f);
        flinnPlotImage.setChannelMinimum(0, 0.0f);

        flinnPlotImage.setChannelMaximum(0, 255.0f);
        flinnPlotImage.setChannelMinimum(0, 0.0f);
        DefaultLinearAxis xFlinnAxis = new DefaultLinearAxis(Axes.get("b/c",true),1.0/FLINN_PLOT_DIMENSION);
        xFlinnAxis.setUnit("");
        DefaultLinearAxis yFlinnAxis = new DefaultLinearAxis(Axes.get("a/b",true),-1.0/FLINN_PLOT_DIMENSION);
        yFlinnAxis.setOrigin(FLINN_PLOT_DIMENSION);
        yFlinnAxis.setUnit("");

        flinnPlotImage.setAxis(xFlinnAxis,0);
        flinnPlotImage.setAxis(yFlinnAxis,1);

        return flinnPlotImage;
    }

    //endregion

    //region: helper methods
    private static double computeWeightedEllipsoidFactor(final QuickEllipsoid ellipsoid) {
        final double[] sortedRadii = ellipsoid.getSortedRadii();
        return (sortedRadii[0] / sortedRadii[1] - sortedRadii[1] / sortedRadii[2])*ellipsoid.getVolume();
    }

    //TODO avoid use of RandomAccess if cursor iterates in same order
    private void mapValuesToImage(final double[] values, final IterableInterval<IntType> idImage,
                                  final Img<FloatType> image) {
        final RandomAccess<FloatType> randomAccess = image.randomAccess();
        idImage.localizingCursor();
        final Cursor<IntType> id = idImage.localizingCursor();
        final long[] position = new long[4];
        while (id.hasNext()) {
            id.fwd();
            if (id.get().getInteger() < 0) {
                continue;
            }
            id.localize(position);
            double value = values[id.get().getInteger()];
            randomAccess.setPosition(new long[]{position[0],position[1],position[3]});
            if(!Double.isNaN(randomAccess.get().getRealDouble())) {
                value += randomAccess.get().getRealDouble();
            }
            randomAccess.get().setReal(value);
        }
    }

    private Img<FloatType> createNaNImg(IterableInterval idImage) {
        final ArrayImg<FloatType, FloatArray> img = ArrayImgs.floats(idImage.dimension(0),
                idImage.dimension(1), idImage.dimension(3));
        img.forEach(e -> e.setReal(Float.NaN));
        return img;
    }
    //endregion
}
