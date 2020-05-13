package org.bonej.wrapperPlugins;


import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.Random;

@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Test images>Create EF test images")
public class CreateEFTestImage extends ContextCommand {
    @Parameter
    double shortestEllipsoidDimension = 5;

    @Parameter
    int imageDimension = 200;

    @Parameter
    int nSpheres = 2;

    @Parameter
    int nRods = 2;

    @Parameter
    int nPlates = 2;

    @Parameter
    int nSurfboards = 2;

    @Parameter
    int randomSeed = 23;


    @Parameter(type=ItemIO.OUTPUT)
    ImgPlus<UnsignedIntType> testImgPlus;
    Random rng;

    @Override
    public void run() {
        //create image
        final ArrayImg testImage = ArrayImgs.unsignedBytes(imageDimension, imageDimension, imageDimension);

        //create seed points
        rng = new Random();
        rng.setSeed(randomSeed);

        drawSpheres(testImage);
        drawRods(testImage);
        drawPlates(testImage);
        drawSurfboards(testImage);

        final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "", 1.0);
        final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "", 1.0);
        final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "", 1.0);
        testImgPlus = new ImgPlus<UnsignedIntType>(testImage,"Test EF image", xAxis, yAxis, zAxis);
    }

    private void drawSpheres(ArrayImg testImage) {
        final ArrayLocalizingCursor<UnsignedByteType> localizingCursor = testImage.localizingCursor();
        for(int i = 0; i<nSpheres; i++) {
            final int x = rng.nextInt(imageDimension-1);
            final int y = rng.nextInt(imageDimension-1);
            final int z = rng.nextInt(imageDimension-1);

            localizingCursor.reset();
            while (localizingCursor.hasNext()) {
                localizingCursor.fwd();
                if(localizingCursor.get().get()==0) {
                    final long[] position = new long[3];
                    localizingCursor.localize(position);

                    //figure out centre squared
                    double xc2 = position[0] - x + 0.5;
                    xc2 = xc2 * xc2;

                    double yc2 = position[1] - y + 0.5;
                    yc2 = yc2 * yc2;

                    double zc2 = position[2] - z + 0.5;
                    zc2 = zc2 * zc2;

                    double r2 = shortestEllipsoidDimension * shortestEllipsoidDimension;

                    //sphere
                    if (xc2 + yc2 + zc2 <= 100 * r2) {
                        localizingCursor.get().set(255);
                    }
                }
            }
        }
    }

    private void drawRods(ArrayImg testImage) {
        final ArrayLocalizingCursor<UnsignedByteType> localizingCursor = testImage.localizingCursor();
        for(int i = 0; i<nRods; i++) {
            final int x = rng.nextInt(imageDimension-1);
            final int y = rng.nextInt(imageDimension-1);
            final int z = rng.nextInt(imageDimension-1);

            localizingCursor.reset();
            while (localizingCursor.hasNext()) {
                localizingCursor.fwd();
                if(localizingCursor.get().get()==0) {
                    final long[] position = new long[3];
                    localizingCursor.localize(position);

                    //figure out centre squared
                    double xc2 = position[0] - x + 0.5;
                    xc2 = xc2 * xc2;

                    double yc2 = position[1] - y + 0.5;
                    yc2 = yc2 * yc2;

                    double zc2 = position[2] - z + 0.5;
                    zc2 = zc2 * zc2;

                    double r2 = shortestEllipsoidDimension * shortestEllipsoidDimension;

                    //rod
                    if (xc2 / (1000 * 1000 * r2) + yc2 / (r2) + zc2 / (r2) <= 1) {
                        localizingCursor.get().set(255);
                    }
                }
            }
        }
    }

    private void drawPlates(ArrayImg testImage) {
        final ArrayLocalizingCursor<UnsignedByteType> localizingCursor = testImage.localizingCursor();
        for(int i = 0; i<nPlates; i++) {
            final int x = rng.nextInt(imageDimension-1);
            final int y = rng.nextInt(imageDimension-1);
            final int z = rng.nextInt(imageDimension-1);

            localizingCursor.reset();
            while (localizingCursor.hasNext()) {
                localizingCursor.fwd();
                if(localizingCursor.get().get()==0) {
                    final long[] position = new long[3];
                    localizingCursor.localize(position);

                    //figure out centre squared
                    double xc2 = position[0] - x + 0.5;
                    xc2 = xc2 * xc2;

                    double yc2 = position[1] - y + 0.5;
                    yc2 = yc2 * yc2;

                    double zc2 = position[2] - z + 0.5;
                    zc2 = zc2 * zc2;

                    double r2 = shortestEllipsoidDimension * shortestEllipsoidDimension;

                    //plate
                    if (xc2 / (r2) + yc2 / (1000 * r2) + zc2 / (1000 * r2) <= 1) {
                        localizingCursor.get().set(255);
                    }
                }
            }
        }
    }

    private void drawSurfboards(ArrayImg testImage) {
        final ArrayLocalizingCursor<UnsignedByteType> localizingCursor = testImage.localizingCursor();
        for(int i = 0; i<nSurfboards; i++) {
            final int x = rng.nextInt(imageDimension-1);
            final int y = rng.nextInt(imageDimension-1);
            final int z = rng.nextInt(imageDimension-1);

            localizingCursor.reset();
            while (localizingCursor.hasNext()) {
                localizingCursor.fwd();
                if(localizingCursor.get().get()==0) {
                    final long[] position = new long[3];
                    localizingCursor.localize(position);

                    //figure out centre squared
                    double xc2 = position[0] - x + 0.5;
                    xc2 = xc2 * xc2;

                    double yc2 = position[1] - y + 0.5;
                    yc2 = yc2 * yc2;

                    double zc2 = position[2] - z + 0.5;
                    zc2 = zc2 * zc2;

                    double r2 = shortestEllipsoidDimension * shortestEllipsoidDimension;

                    //plate
                    if (xc2 / (100 * 100 * r2) + yc2 / (100 * r2) + zc2 / (r2) <= 1) {
                        localizingCursor.get().set(255);
                    }
                }
            }
        }
    }
}
