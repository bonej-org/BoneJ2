/*-
 * #%L
 * Ops created for BoneJ2
 * %%
 * Copyright (C) 2015 - 2023 Michael Doube, BoneJ developers
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import net.imagej.ImgPlus;
import net.imagej.ops.AbstractOpTest;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;

public class EllipsoidFactorOutputGeneratorTest extends AbstractOpTest {

    @Test
    public void testWeightedAverage(){
        IterableInterval<IntType> idImage = getSimpleIDImg();

        List<QuickEllipsoid> ellipsoids = getEllipsoids();

        final List<ImgPlus> efOutputs = (List<ImgPlus>) ops.run(EllipsoidFactorOutputGenerator.class,idImage,ellipsoids);
        final Img<? extends RealType> efImg = efOutputs.get(0).getImg();
        final RandomAccess<? extends RealType> access = efImg.randomAccess();
        access.setPosition(new long[]{2,2,2});
        double efValue = access.get().getRealDouble();

        //ellipsoids have axis lengths (1,1,10) and (1,5,5), and therefore EF 0.9 and -0.8, respectively, which gives 0.05 EF on average.
        assertEquals("Unexpected EF value", -11.0/35,efValue,1e-6);

    }

    @Test
    public void testOutput()
    {
        //SET-UP
        final IterableInterval<IntType> idImage = Views.interval(getSimpleIDImg(),new long[]{0,0,0,0}, new long[]{4,4,0,4});
        final List<QuickEllipsoid> ellipsoids = getEllipsoids();

        //EXECUTE
        final List<ImgPlus> primaryEfOutputs = (List<ImgPlus>) ops.run(EllipsoidFactorOutputGenerator.class,idImage,ellipsoids,false,false,"test_image");
        final List<ImgPlus> allEfOutputs = (List<ImgPlus>) ops.run(EllipsoidFactorOutputGenerator.class,idImage,ellipsoids,true,true,"test_image");

        //VERIFY
        assertEquals("Wrong number of primary outputs", 1,primaryEfOutputs.size());
        assertEquals("Wrong overall number of outputs", 10,allEfOutputs.size());
        allEfOutputs.forEach(out -> assertNotNull("No null outputs expected.", out));
    }

    private Img<IntType> getSimpleIDImg() {
        Img<IntType> idImage = ArrayImgs.ints(5,5,2,5);
        final Cursor<IntType> cursor = idImage.localizingCursor();
        while(cursor.hasNext())
        {
            cursor.fwd();
            long[] position = new long[4];
            cursor.localize(position);
            if (position[0]==2 && position[1]==2 && position[3]==2) {
                cursor.get().setInteger(position[2]);
            }
            else{
                cursor.get().setInteger(-1);
            }
        }
        return idImage;
    }

    private List<QuickEllipsoid> getEllipsoids() {
        List<QuickEllipsoid> ellipsoids = new ArrayList<>();
        double[][] frame = new double[][]{{1,0,0},{0,1,0},{0,0,1}};
        ellipsoids.add(new QuickEllipsoid(new double[]{10.0,1.0,1.0}, new double[3],frame));
        ellipsoids.add(new QuickEllipsoid(new double[]{1.0,5.0,5.0}, new double[3],frame));
        return ellipsoids;
    }

}
