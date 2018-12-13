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

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.real.FloatType;
import org.bonej.utilities.SharedTable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.Gateway;
import org.scijava.command.CommandModule;
import org.scijava.table.DefaultColumn;

import java.util.List;

import static org.bonej.wrapperPlugins.EllipsoidFactorWrapper.vectorToPixelGrid;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link EllipsoidFactorWrapper}.
 *
 * @author Alessandro Felder
 */

@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class EllipsoidFactorWrapperTest {
    private static final Gateway IMAGE_J = new ImageJ();

    @After
    public void tearDown() {
        SharedTable.reset();
    }

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void testNullImageCancelsPlugin() throws Exception {
        CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
                EllipsoidFactorWrapper.class);
    }

    @Test
    public void testNonBinaryImgCancelsPlugin() throws Exception {
        CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
                EllipsoidFactorWrapper.class);
    }

    @Test
    public void test2DImgCancelsPlugin() throws Exception {
        CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J, EllipsoidFactorWrapper.class);
    }

    @Test
    public void testAnisotropicImageShowsWarningDialog() throws Exception {
        CommonWrapperTests.testAnisotropyWarning(IMAGE_J,
                IntertrabecularAngleWrapper.class);
    }

    @Test
    public void testResultsTable() throws Exception {
        // SETUP
        final ImgPlus<BitType> sphereImgPlus = getSphereImage();

        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", sphereImgPlus, "nSphere", 10, "approximateMaximumNumberOfSeeds", 10).get();

        // VERIFY
        final List<DefaultColumn<Double>> table =
                (List<DefaultColumn<Double>>) module.getOutput("resultsTable");
        assertEquals("Filling percentage header wrong!","filling percentage",table.get(0).getHeader());
        assertEquals("Filling percentage wrong", 100.0, table.get(0).getValue(0).doubleValue(), 1e-12);
        assertEquals("Number of ellipsoids header wrong!","number of ellipsoids found",table.get(1).getHeader());
        assertEquals("Number of ellipsoids wrong", 87.0, table.get(1).getValue(0).doubleValue(), 1e-12);

    }

    @Test
    public void testSphereVoxelsHaveEFZero() throws Exception {
        // SETUP
        final ImgPlus<BitType> sphereImgPlus = getSphereImage();

        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", sphereImgPlus).get();

        // VERIFY
        @SuppressWarnings("unchecked")
        final ImgPlus<FloatType> efImage = (ImgPlus<FloatType>) module.getOutput("efImage");
        final double expectedValue = 0.0;
        assertFiniteImageEntriesMatchValue(efImage, expectedValue, 1e-5);
    }

    @Test
    public void testSphereVoxelsHaveCorrectVolume() throws Exception {
        // SETUP
        final ImgPlus<BitType> sphereImgPlus = getSphereImage();

        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", sphereImgPlus).get();

        // VERIFY
        @SuppressWarnings("unchecked")
        final ImgPlus<FloatType> volumeImage = (ImgPlus<FloatType>) module.getOutput("vImage");
        final double expectedValue = 4.0*Math.PI/3.0*11*11*11;
        assertFiniteImageEntriesMatchValue(volumeImage,expectedValue,0.2);
    }


    @Test
    public void testSphereVoxelsHaveCorrectAxisRatios() throws Exception {
        // SETUP
        final ImgPlus<BitType> sphereImgPlus = getSphereImage();
        final double expectedRatio = 1.0;

        // EXECUTE
        final CommandModule module = IMAGE_J.command().run(
                EllipsoidFactorWrapper.class, true, "inputImage", sphereImgPlus, "showSecondaryImages", true).get();

        // VERIFY

        @SuppressWarnings("unchecked")
        final ImgPlus<FloatType> aToB = (ImgPlus<FloatType>) module.getOutput("aToBAxisRatioImage");
        assertFiniteImageEntriesMatchValue(aToB,expectedRatio,1e-4);
        @SuppressWarnings("unchecked")
        final ImgPlus<FloatType> bToC = (ImgPlus<FloatType>) module.getOutput("bToCAxisRatioImage");
        assertFiniteImageEntriesMatchValue(bToC,expectedRatio,1e-4);
    }

    @Test
    public void testVectorToPixelGrid() throws Exception {
        final Vector3dc position = new Vector3d(3.25, 3.5, 3.75);

        final long[] expected = {3,3,3};
        final long[] actual = vectorToPixelGrid(position);

        assertEquals("Conversion to pixel grid failed", expected[0], actual[0]);
        assertEquals("Conversion to pixel grid failed", expected[1], actual[1]);
        assertEquals("Conversion to pixel grid failed", expected[2], actual[2]);
    }

	private void assertFiniteImageEntriesMatchValue(
		final ImgPlus<FloatType> efImage, final double expectedValue,
		final double tolerance)
	{
		efImage.forEach(e -> {
			final double value = e.getRealDouble();
			if (Double.isFinite(value)) {
				assertEquals(expectedValue, value, tolerance);
			}
		});
	}

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