package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.bonej.ops.ellipsoid.QuickEllipsoid;
import org.joml.Vector3d;
import org.junit.Test;

import net.imagej.ImgPlus;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayLocalizingCursor;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class EllipsoidFactorWrapperTest {

    /**
     * test for {@link EllipsoidFactorWrapper#getAnchors(QuickEllipsoid[], ImgPlus)}
     *
     * tests the method on a simple 3x3x3 foreground cube
     * where one side of the cube is completely contained in an ellipsoid.
     */
    @Test
    public void testGetAnchors(){
        //SET-UP
        final ImgPlus<UnsignedByteType> cube3x3x3 = new ImgPlus<>(get3x3x3CubeIn5x5x5Img());
        double[] radii = new double[]{0.5, 3.0 / 2.0 * Math.sqrt(2.0), 3.0 / 2.0 * Math.sqrt(2.0)};
        double[] centre = new double[]{1.5, 2.5, 2.5};
        final QuickEllipsoid[] ellipsoids = {new QuickEllipsoid(radii, centre, new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0,
                1}})};
        //EXECUTE
        List<Vector3d> anchors = EllipsoidFactorWrapper.getAnchors(ellipsoids, cube3x3x3);

        //VERIFY
        assertTrue("Cube centre should not be an anchor.",anchors.stream().noneMatch(a -> a.x==2.5 && a.y==2.5 && a.z==2.5));
        assertTrue("Pixels with x==1.5 should be within ellipsoid, and therefore not anchors", anchors.stream().noneMatch(a -> a.x==1.5));
        assertEquals("Unexpected number of anchors found.", 17, anchors.size());
    }

    @Test
    public void testImgToByteArray(){
        final ArrayImg<UnsignedByteType, ByteArray> unsignedIntTypes = ArrayImgs.unsignedBytes(3, 3, 3);
        final ArrayRandomAccess<UnsignedByteType> access = unsignedIntTypes.randomAccess();
        access.setPosition(new int[]{1,1,1});
        access.get().setInteger(255);
        final byte[][] bytes = EllipsoidFactorWrapper.imgPlusToByteArray(new ImgPlus<>(unsignedIntTypes));

        assertTrue("Central pixel should be FG",bytes[1][4]==-1);
        assertTrue("Pixel at (0,0,0) should be BG",bytes[0][0]==0);
    }

    /**
     * @return a 5x5x5 ArrayImg which contains a 3x3x3 foreground cube at its centre.
     */
    private ArrayImg<UnsignedByteType, ByteArray> get3x3x3CubeIn5x5x5Img() {
        final ArrayImg<UnsignedByteType, ByteArray> cube3x3x3 = ArrayImgs.unsignedBytes(5, 5, 5);
        final ArrayLocalizingCursor<UnsignedByteType> cursor = cube3x3x3.localizingCursor();
        while(cursor.hasNext()){
            cursor.fwd();

            long[] position = new long[3];
            cursor.localize(position);
            Arrays.sort(position);
            if(position[0]>0 && position[2]<4){
                cursor.get().setInteger(255);
            }
        }
        return cube3x3x3;
    }
}
