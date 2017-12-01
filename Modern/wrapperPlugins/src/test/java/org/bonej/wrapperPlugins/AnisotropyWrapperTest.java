
package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertFalse;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.Views;

import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;

/**
 * Tests for {@link AnisotropyWrapper}.
 *
 * @author Richard Domander
 */
// TODO Complete tests when implementation stabilises
@Ignore
@Category(SlowWrapperTest.class)
public class AnisotropyWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@Test
	public void test2DImageCancelsWrapper() throws Exception {
		CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J,
			AnisotropyWrapper.class);
	}

	@Test
	public void testAnisotropicCalibrationShowsWarningDialog() throws Exception {

	}

	@Test
	public void testCalibrationAffectsResults() throws Exception {

	}

	// TODO How to test this?
	@Test
	public void testEllipsoidFittingFailingCancelsPlugins() throws Exception {

	}

	@Test
	public void testHyperStackResultsTable() throws Exception {
		// SETUP
		final String unit = "mm";
		final double scale = 1.0;
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, scale);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit, scale);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<BitType> img = ArrayImgs.bits(100, 100, 100, 2, 2);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis, cAxis, tAxis);
		// TODO draw sheets
		// Draw cubes to subspaces (channel 0, time 0) and (channel 1, time 1)
		Views.interval(imgPlus, new long[] { 1, 1, 1, 0, 0 }, new long[] { 98, 98,
			98, 0, 0 }).forEach(BitType::setOne);
		Views.interval(imgPlus, new long[] { 1, 1, 1, 1, 1 }, new long[] { 98, 98,
			98, 1, 1 }).forEach(BitType::setOne);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(AnisotropyWrapper.class,
			true, "inputImage", imgPlus, "rotations", 100).get();

		// VERIFY
		assertFalse(module.isCanceled());
		// TODO assert that results in two subspaces are different than the two
		// other
	}

	@Test
	public void testNonBinaryImageCancelsWrapper() throws Exception {
		CommonWrapperTests.testNonBinaryImageCancelsPlugin(IMAGE_J,
			AnisotropyWrapper.class);
	}

	@Test
	public void testNullImageCancelsWrapper() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			AnisotropyWrapper.class);
	}

	@Test
	public void testTooFewPointsCancelsPlugin() throws Exception {

	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
