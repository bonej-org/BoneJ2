package org.bonej.ops.ellipsoid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
    boolean showSecondaryImages = false;

    private List<ImgPlus> eFOutputs;

    @Override
    public List<ImgPlus> calculate(IterableInterval<IntType> idImage, List<QuickEllipsoid> ellipsoids) {
        eFOutputs = new ArrayList<>();
        calculatePrimaryOutputs(idImage, ellipsoids);

        if(showSecondaryImages)
        {
            calculateSecondaryOutputs(idImage, ellipsoids);
        }

        if(idImage.dimension(2)>1)
        {
            postProcessWeightedAveraging(idImage);
        }

        //divide EF-image by volume image
        final Cursor<? extends RealType> eFCursor = eFOutputs.get(0).cursor();
        final Cursor<? extends RealType> vCursor = eFOutputs.get(1).cursor();
        while(eFCursor.hasNext())
        {
            eFCursor.fwd();
            vCursor.fwd();

            eFCursor.get().setReal(eFCursor.get().getRealDouble()/vCursor.get().getRealDouble());
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
        eFOutputs.add(createVolumeImage(ellipsoids, idImage));
        eFOutputs.add(createIDImage(idImage, ellipsoids));
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

        //add to output list
        eFOutputs.add(createRadiusImage(as,idImage,"a"));
        eFOutputs.add(createRadiusImage(bs,idImage,"b"));
        eFOutputs.add(createRadiusImage(cs,idImage,"c"));

        eFOutputs.add(createAxisRatioImage(aBRatios, idImage, "a/b"));
        eFOutputs.add(createAxisRatioImage(bCRatios, idImage, "b/c"));

        eFOutputs.add(createFlinnPlotImage(aBRatios,bCRatios));
        eFOutputs.add(createFlinnPeakPlot(aBRatios,bCRatios,idImage));
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
        ImgPlus eIdImage = new ImgPlus<>(ints,"ID");
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
        final ImgPlus<FloatType> efImage = new ImgPlus<>(ellipsoidFactorImage, "EF");
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
        ImgPlus radiusImage = new ImgPlus<>(aImg, name);
        radiusImage.setChannelMaximum(0, radii[radii.length-1]);
        radiusImage.setChannelMinimum(0, 0.0f);
        return radiusImage;
    }

    private ImgPlus createAxisRatioImage(final double[] ratios, final IterableInterval idImage, String name) {
        final Img<FloatType> axisRatioImage = createNaNImg(idImage);
        mapValuesToImage(ratios, idImage, axisRatioImage);
        ImgPlus aToBAxisRatioImage = new ImgPlus<>(axisRatioImage, name);
        aToBAxisRatioImage.setChannelMaximum(0, 1.0f);
        aToBAxisRatioImage.setChannelMinimum(0, 0.0f);
        return aToBAxisRatioImage;
    }

    private ImgPlus createVolumeImage(final List<QuickEllipsoid> ellipsoids,
                                   final IterableInterval idImage) {
        final Img<FloatType> volumeImage = createNaNImg(idImage);
        final double[] volumes = ellipsoids.parallelStream().mapToDouble(QuickEllipsoid::getVolume).toArray();
        mapValuesToImage(volumes, idImage, volumeImage);
        ImgPlus vImage = new ImgPlus<>(volumeImage, "Volume");
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
            final long x = Math.round(aBRatios[localMaxEllipsoidID] * (FLINN_PLOT_DIMENSION - 1));
            final long y = Math.round(bCRatios[localMaxEllipsoidID] * (FLINN_PLOT_DIMENSION - 1));
            flinnPeakPlotRA.setPosition(new long[]{x, FLINN_PLOT_DIMENSION - y - 1});
            final float currentValue = flinnPeakPlotRA.get().getRealFloat();
            flinnPeakPlotRA.get().set(currentValue + 1.0f);
        }

        ImgPlus flinnPeakPlotImage = new ImgPlus<>(flinnPeakPlot, "Flinn Peak Plot");
        flinnPeakPlotImage.setChannelMaximum(0, 255.0f);
        flinnPeakPlotImage.setChannelMinimum(0, 0.0f);

        return flinnPeakPlotImage;
    }

    private ImgPlus createFlinnPlotImage(final double[] aBRatios, final double[] bCRatios) {
        final Img<BitType> flinnPlot = ArrayImgs.bits(FLINN_PLOT_DIMENSION, FLINN_PLOT_DIMENSION);
        final RandomAccess<BitType> flinnRA = flinnPlot.randomAccess();
        for (int i = 0; i < aBRatios.length; i++) {
            final long x = Math.round(aBRatios[i] * (FLINN_PLOT_DIMENSION - 1));
            final long y = FLINN_PLOT_DIMENSION - Math.round(bCRatios[i] * (FLINN_PLOT_DIMENSION - 1)) - 1;
            flinnRA.setPosition(x, 0);
            flinnRA.setPosition(y, 1);
            flinnRA.get().setOne();
        }
        ImgPlus flinnPlotImage = new ImgPlus<>(flinnPlot, "Unweighted Flinn Plot");
        flinnPlotImage.setChannelMaximum(0, 255.0f);
        flinnPlotImage.setChannelMinimum(0, 0.0f);
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
