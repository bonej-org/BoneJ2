
package org.bonej.wrapperPlugins;

import static org.bonej.ops.SolveQuadricEq.QUADRIC_TERMS;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import net.imagej.ImageJ;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.scijava.command.CommandModule;
import org.scijava.ui.UserInterface;

import ij.ImagePlus;
import ij.gui.NewImage;

/**
 * Tests for {@link FitEllipsoidWrapper}.
 *
 * @author Richard Domander
 */
@Category(org.bonej.wrapperPlugins.SlowWrapperTest.class)
public class FitEllipsoidWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test
	public void testNullImageCancelsPlugin() throws Exception {
		CommonWrapperTests.testNullImageCancelsPlugin(IMAGE_J,
			FitEllipsoidWrapper.class);
	}

	@Test
	public void test2DImageCancelsPlugin() throws Exception {
		CommonWrapperTests.test2DImagePlusCancelsPlugin(IMAGE_J,
			FitEllipsoidWrapper.class);
	}

	@Test
	public void testAnisotropicImageShowsWarningDialog() throws Exception {
		CommonWrapperTests.testAnisotropyWarning(IMAGE_J,
			FitEllipsoidWrapper.class);
	}

	@Test
	public void testNullROIManagerCancelsPlugin() throws Exception {
		// SETUP
		final UserInterface mockUI = CommonWrapperTests.mockUIService(IMAGE_J);
		final ImagePlus imagePlus = NewImage.createImage("", 5, 5, 5, 8, 1);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(
			FitEllipsoidWrapper.class, true, "inputImage", imagePlus).get();

		// VERIFY
		assertTrue("No ROI Manager should have cancelled the plugin", module
			.isCanceled());
		assertTrue("Cancel reason is incorrect", module.getCancelReason()
			.startsWith("Please populate ROI Manager with at least " +
				QUADRIC_TERMS));
		verify(mockUI, timeout(1000)).dialogPrompt(anyString(), anyString(), any(),
			any());
    }
}
