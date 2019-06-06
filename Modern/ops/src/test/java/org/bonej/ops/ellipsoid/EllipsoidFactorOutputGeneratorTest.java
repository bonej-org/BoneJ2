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
        final List<ImgPlus> primaryEfOutputs = (List<ImgPlus>) ops.run(EllipsoidFactorOutputGenerator.class,idImage,ellipsoids,false);
        final List<ImgPlus> allEfOutputs = (List<ImgPlus>) ops.run(EllipsoidFactorOutputGenerator.class,idImage,ellipsoids,true);

        //VERIFY
        assertEquals("Expect 3 primary outputs", 3,primaryEfOutputs.size());
        assertEquals("Expect 10 outputs overall", 10,allEfOutputs.size());
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