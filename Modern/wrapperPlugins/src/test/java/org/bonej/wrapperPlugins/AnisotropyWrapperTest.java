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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.scijava.ui.DialogPrompt.MessageType.WARNING_MESSAGE;

import java.util.concurrent.ExecutionException;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.DefaultLinearAxis;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.Views;

import org.bonej.utilities.SharedTable;
import org.bonej.wrapperPlugins.wrapperUtils.UsageReporter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.scijava.command.CommandModule;
import org.scijava.ui.DialogPrompt.Result;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.sdi.SwingDialogPrompt;

/**
 * Tests for {@link AnisotropyWrapper}.
 * <p>
 * These tests complement the manual tests in
 * ../IntegrationTestLogs/AnisotropyWrapper.txt
 * </p>
 *
 * @author Richard Domander
 */
@Category(SlowWrapperTest.class)
public class AnisotropyWrapperTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static ImgPlus<BitType> hyperSheets;

	@Before
	public void setup() {
		UsageReporter mockReporter = mock(UsageReporter.class);
		doNothing().when(mockReporter).reportEvent(anyString());
		AnisotropyWrapper.setReporter(mockReporter);
	}

	@After
	public void tearDown() {
		Mockito.reset(IMAGE_J.ui().getDefaultUI());
		SharedTable.reset();
	}

	@Test
	public void test2DImageCancelsWrapper() throws Exception {
		CommonWrapperTests.test2DImageCancelsPlugin(IMAGE_J,
			AnisotropyWrapper.class);
	}

	@Test
	public void testAnisotropicCalibrationWithinToleranceDoesNotShowWarningDialog()
			throws ExecutionException, InterruptedException
	{
		// SETUP
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "", 1.01);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "", 1.0);
		final Img<BitType> img = ArrayImgs.bits(5, 5, 5);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
				yAxis, zAxis);
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockPrompt.prompt()).thenReturn(Result.CANCEL_OPTION);
		final String expectedStart = "The voxels in the image are anisotropic";
		when(mockUI.dialogPrompt(startsWith(expectedStart), anyString(), eq(
				WARNING_MESSAGE), any())).thenReturn(mockPrompt);
		IMAGE_J.ui().setDefaultUI(mockUI);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(AnisotropyWrapper.class,
				true, "inputImage", imgPlus, "lines", 10, "directions", 10, "displayMILVectors", false).get();

		// VERIFY
		assertFalse(module.isCanceled());
		verify(mockUI, timeout(1000).times(0)).dialogPrompt(startsWith(
				expectedStart), anyString(), eq(WARNING_MESSAGE), any());
	}

	@Test
	public void testAnisotropicCalibrationShowsWarningDialog()
		throws ExecutionException, InterruptedException
	{
		// SETUP
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, "", 1.0);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, "", 5.0);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, "", 1.0);
		final Img<BitType> img = ArrayImgs.bits(5, 5, 5);
		final ImgPlus<BitType> imgPlus = new ImgPlus<>(img, "Test image", xAxis,
			yAxis, zAxis);
		final UserInterface mockUI = mock(UserInterface.class);
		final SwingDialogPrompt mockPrompt = mock(SwingDialogPrompt.class);
		when(mockPrompt.prompt()).thenReturn(Result.CANCEL_OPTION);
		final String expectedStart = "The voxels in the image are anisotropic";
		when(mockUI.dialogPrompt(startsWith(expectedStart), anyString(), eq(
			WARNING_MESSAGE), any())).thenReturn(mockPrompt);
		IMAGE_J.ui().setDefaultUI(mockUI);

		// EXECUTE
		final CommandModule module = IMAGE_J.command().run(AnisotropyWrapper.class,
			true, "inputImage", imgPlus, "lines", 10, "directions", 10).get();

		// VERIFY
		assertTrue(module.isCanceled());
		verify(mockUI, timeout(1000).times(1)).dialogPrompt(startsWith(
			expectedStart), anyString(), eq(WARNING_MESSAGE), any());
	}

	/**
	 * Tests that plugin cancels if an ellipsoid could not be fit into the point
	 * cloud.
	 * <p>
	 * The test differs from {@link #testTooFewPointsCancelsPlugin()} because here
	 * we get at least 9 points - which is the minimum for fitting - but the
	 * quadric solution to them is not an ellipsoid.
	 * </p>
	 */
	@Test
	public void testEllipsoidFittingFailingCancelsPlugins() throws Exception {
		final UserInterface mockUI = mock(UserInterface.class);
		IMAGE_J.ui().setDefaultUI(mockUI);

		final CommandModule module = IMAGE_J.command().run(AnisotropyWrapper.class,
			true, "inputImage", hyperSheets, "lines", 4, "directions", 9).get();

		assertTrue(module.isCanceled());
		assertEquals(
			"Anisotropy could not be calculated - ellipsoid fitting failed", module
				.getCancelReason());
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
		final UserInterface mockUI = mock(UserInterface.class);
		IMAGE_J.ui().setDefaultUI(mockUI);

		final CommandModule module = IMAGE_J.command().run(AnisotropyWrapper.class,
			true, "inputImage", hyperSheets, "lines", 1, "directions", 1).get();

		assertTrue(module.isCanceled());
		assertEquals("Anisotropy could not be calculated - too few points", module
			.getCancelReason());
	}

	@BeforeClass
	public static void oneTimeSetup() {
		final UsageReporter mockReporter = mock(UsageReporter.class);
		doNothing().when(mockReporter).reportEvent(anyString());
		AnisotropyWrapper.setReporter(mockReporter);

		final String unit = "mm";
		final double scale = 1.0;
		final DefaultLinearAxis xAxis = new DefaultLinearAxis(Axes.X, unit, scale);
		final DefaultLinearAxis yAxis = new DefaultLinearAxis(Axes.Y, unit, scale);
		final DefaultLinearAxis zAxis = new DefaultLinearAxis(Axes.Z, unit, scale);
		final DefaultLinearAxis cAxis = new DefaultLinearAxis(Axes.CHANNEL);
		final DefaultLinearAxis tAxis = new DefaultLinearAxis(Axes.TIME);
		final Img<BitType> img = ArrayImgs.bits(100, 100, 100, 2, 2);
		hyperSheets = new ImgPlus<>(img, "Test image", xAxis, yAxis, zAxis, cAxis,
			tAxis);
		// Draw xy-sheets to subspaces (channel 0, time 0) and (channel 1, time 1)
		for (int z = 0; z < 100; z += 2) {
			Views.interval(hyperSheets, new long[] { 0, 0, z, 0, 0 }, new long[] { 99,
				99, z, 0, 0 }).forEach(BitType::setOne);
			Views.interval(hyperSheets, new long[] { 0, 0, z, 1, 1 }, new long[] { 99,
				99, z, 0, 0 }).forEach(BitType::setOne);
		}
		AnisotropyWrapper.setSeed(0xc0ff33);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
