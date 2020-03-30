package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.imagej.ImgPlus;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.array.ArrayRandomAccess;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class EllipsoidFactorWrapperTest {

    @Test
    public void testImgToByteArray(){
        final int fg = 0xFF;
        final ArrayImg<UnsignedByteType, ByteArray> unsignedIntTypes = ArrayImgs.unsignedBytes(3, 3, 3);
        final ArrayRandomAccess<UnsignedByteType> access = unsignedIntTypes.randomAccess();
        access.setPosition(new int[]{1,1,1});
        access.get().setInteger(fg);
        final byte[][] bytes = EllipsoidFactorWrapper.imgPlusToByteArray(new ImgPlus<>(unsignedIntTypes));

        assertEquals("Central pixel should be FG", (byte) fg, bytes[1][4]);
        assertEquals("Pixel at (0,0,0) should be BG", 0, bytes[0][0]);
    }
}
