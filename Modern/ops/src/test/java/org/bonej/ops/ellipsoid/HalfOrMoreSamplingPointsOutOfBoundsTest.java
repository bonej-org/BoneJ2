package org.bonej.ops.ellipsoid;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HalfOrMoreSamplingPointsOutOfBoundsTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @SuppressWarnings("unchecked")
    private final BinaryFunctionOp<Img,Ellipsoid,Boolean> samplingOp =
            (BinaryFunctionOp) Functions.binary(IMAGE_J.op(),
                    HalfOrMoreSamplingPointsOutOfBounds.class, Boolean.class, Img.class, Ellipsoid.class);

    @Test
    public void testSamplingOutsideImage() {
        //SETUP
        Ellipsoid partiallyOutside = new Ellipsoid(5,5,5);
        Ellipsoid completelyInside = new Ellipsoid(5,5,5);
        completelyInside.setCentroid(new Vector3d(50,50,50));

        final ImgPlus<BitType> sphereImage = getSphereImage();

        //EXECUTE
        final Boolean shouldBeTrue = samplingOp.calculate(sphereImage.getImg(), partiallyOutside);
        final Boolean shouldBeFalse = samplingOp.calculate(sphereImage.getImg(), completelyInside);

        //VERIFY
        assertTrue(shouldBeTrue);
        assertFalse(shouldBeFalse);
    }

    //TODO make test image util method
    private static ImgPlus<BitType> getSphereImage() {
        final long[] imageDimensions = { 101, 101, 101 };
        final Vector3dc centre = new Vector3d(Math.floor(imageDimensions[0] / 2.0),
                Math.floor(imageDimensions[1] / 2.0), Math.floor(imageDimensions[2] /
                2.0));
        final int radius = 10;
        final Img<BitType> sphereImg = ArrayImgs.bits(imageDimensions[0],
                imageDimensions[1], imageDimensions[2]);
        final ImgPlus<BitType> sphereImgPlus = new ImgPlus<>(sphereImg,
                "Sphere test image", new AxisType[] { Axes.X, Axes.Y, Axes.Z },
                new double[] { 1.0, 1.0, 1.0 }, new String[] { "", "", "" });
        final Cursor<BitType> cursor = sphereImgPlus.localizingCursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            final long[] coordinates = new long[3];
            cursor.localize(coordinates);
            final Vector3d v = centre.add(-coordinates[0], -coordinates[1],
                    -coordinates[2], new Vector3d());
            if (v.lengthSquared() <= radius * radius) {
                cursor.get().setOne();
            }
        }
        return sphereImgPlus;
    }
}
