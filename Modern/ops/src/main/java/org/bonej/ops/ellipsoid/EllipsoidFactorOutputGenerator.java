package org.bonej.ops.ellipsoid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.imagej.display.ColorTables;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import net.imagej.ImgPlus;
import net.imagej.ops.Op;
import net.imagej.ops.special.function.AbstractBinaryFunctionOp;
import net.imglib2.img.Img;

@Plugin(name = "Generate Ellipsoid Factor Output", type = Op.class)
public class EllipsoidFactorOutputGenerator extends
        AbstractBinaryFunctionOp<Img, List<QuickEllipsoid>, List<ImgPlus>>{
    // Several ellipsoids may fall in same bin if this is too small a number!
    // This will be ignored!
    private static final long FLINN_PLOT_DIMENSION = 501;

    @Parameter(required = false)
    boolean showSecondaryImages = false;

    private List<ImgPlus> eFOutputs;

    @Override
    public List<ImgPlus> calculate(Img idImage, List<QuickEllipsoid> ellipsoids) {
        eFOutputs = new ArrayList<>();

        calculatePrimaryOutputs(idImage, ellipsoids);
        if(showSecondaryImages)
        {
            calculateSecondaryOutputs(idImage, ellipsoids);
        }
        return eFOutputs;
    }


    private void calculatePrimaryOutputs(Img idImage, List<QuickEllipsoid> ellipsoids) {
        eFOutputs.add(createEFImage(ellipsoids, idImage));
        eFOutputs.add(createVolumeImage(ellipsoids, idImage));
        eFOutputs.add(createIDImage(idImage, ellipsoids));
    }


    private void calculateSecondaryOutputs(Img idImage, List<QuickEllipsoid> ellipsoids) {

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
    private ImgPlus createIDImage(Img idImage, List<QuickEllipsoid> ellipsoids) {
        ImgPlus eIdImage = new ImgPlus<>(idImage,"ID");
        eIdImage.setChannelMaximum(0, ellipsoids.size() / 10.0f);
        eIdImage.setChannelMinimum(0, -1.0f);
        return eIdImage;
    }

    private ImgPlus<FloatType> createEFImage(final Collection<QuickEllipsoid> ellipsoids,
                                             final Img idImage) {
        final Img<FloatType> ellipsoidFactorImage = createNaNCopy(idImage);
        final double[] ellipsoidFactors = ellipsoids.parallelStream()
                .mapToDouble(EllipsoidFactorOutputGenerator::computeEllipsoidFactor).toArray();
        mapValuesToImage(ellipsoidFactors, idImage, ellipsoidFactorImage);
        final ImgPlus<FloatType> efImage = new ImgPlus<>(ellipsoidFactorImage, "EF");
        efImage.setChannelMaximum(0,1);
        efImage.setChannelMinimum(0, -1);
        efImage.initializeColorTables(1);
        efImage.setColorTable(ColorTables.FIRE,0);
        return efImage;
    }

    private ImgPlus createRadiusImage(double[] as, Img<IntType> idImage, String name) {
        final Img<FloatType> aImg = createNaNCopy(idImage);
        mapValuesToImage(as, idImage, aImg);
        Arrays.sort(as);
        ImgPlus radiusImage = new ImgPlus<>(aImg, name);
        radiusImage.setChannelMaximum(0, as[as.length-1]);
        radiusImage.setChannelMinimum(0, 0.0f);
        return radiusImage;
    }

    private ImgPlus createAxisRatioImage(final double[] ratios, final Img idImage, String name) {
        final Img<FloatType> axisRatioImage = createNaNCopy(idImage);
        mapValuesToImage(ratios, idImage, axisRatioImage);
        ImgPlus aToBAxisRatioImage = new ImgPlus<>(axisRatioImage, name);
        aToBAxisRatioImage.setChannelMaximum(0, 1.0f);
        aToBAxisRatioImage.setChannelMinimum(0, 0.0f);
        return aToBAxisRatioImage;
    }

    private ImgPlus createVolumeImage(final List<QuickEllipsoid> ellipsoids,
                                   final Img idImage) {
        final Img<FloatType> volumeImage = createNaNCopy(idImage);
        final double[] volumes = ellipsoids.parallelStream().mapToDouble(QuickEllipsoid::getVolume).toArray();
        mapValuesToImage(volumes, idImage, volumeImage);
        ImgPlus vImage = new ImgPlus<>(volumeImage, "Volume");
        vImage.setChannelMaximum(0, ellipsoids.get(0).getVolume());
        vImage.setChannelMinimum(0, -1.0f);
        return vImage;
    }

    private ImgPlus createFlinnPeakPlot(final double[] aBRatios, final double[] bCRatios,
                                        final Img<IntType> ellipsoidIDs) {
        Img<FloatType> flinnPeakPlot = ArrayImgs.floats(FLINN_PLOT_DIMENSION, FLINN_PLOT_DIMENSION);
        final RandomAccess<FloatType> flinnPeakPlotRA = flinnPeakPlot.randomAccess();
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
    private static float computeEllipsoidFactor(final QuickEllipsoid ellipsoid) {
        final double[] sortedRadii = ellipsoid.getSortedRadii();
        return (float) (sortedRadii[0] / sortedRadii[1] - sortedRadii[1] / sortedRadii[2]);
    }

    //TODO avoid use of RandomAccess if cursor iterates in same order
    private void mapValuesToImage(final double[] values, final IterableInterval<IntType> ellipsoidIdentityImage,
                                  final RandomAccessible<FloatType> ellipsoidFactorImage) {
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

    private Img<FloatType> createNaNCopy(Img idImage) {
        final ArrayImg<FloatType, FloatArray> copy = ArrayImgs.floats(idImage.dimension(0),
                idImage.dimension(1), idImage.dimension(2));
        copy.forEach(e -> e.setReal(Float.NaN));
        return copy;
    }
    //endregion
}
